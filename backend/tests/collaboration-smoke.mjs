// Isolated offer/chat integration proof using temporary JWT keys and H2.
import {createServer} from 'node:http';
import {generateKeyPairSync,sign} from 'node:crypto';
import {spawn,spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {resolve,dirname} from 'node:path';
import assert from 'node:assert/strict';
const project=resolve(dirname(fileURLToPath(import.meta.url)),'../..'),runtime=resolve(project,'../../work/runtime');
const {privateKey,publicKey}=generateKeyPairSync('rsa',{modulusLength:2048});
const jwk={...publicKey.export({format:'jwk'}),kid:'collaboration-test',use:'sig',alg:'RS256'};
const jwks=createServer((_req,res)=>{res.setHeader('Content-Type','application/json');res.end(JSON.stringify({keys:[jwk]}));});
await new Promise(r=>jwks.listen(0,'127.0.0.1',r));const issuer=`http://127.0.0.1:${jwks.address().port}`;
const reservation=createServer();await new Promise(r=>reservation.listen(0,'127.0.0.1',r));const port=reservation.address().port;await new Promise(r=>reservation.close(r));
const quote=s=>`'${s.replaceAll("'","''")}'`,base=`http://127.0.0.1:${port}`;
const command=`& ${quote(resolve(runtime,'maven-3.9.16/bin/mvn.cmd'))} ${[`-Dmaven.repo.local=${resolve(project,'backend/.m2')}`,'-Dmaven.main.skip=true','-Dmaven.test.skip=true','-f',resolve(project,'backend/pom.xml'),'spring-boot:run'].map(quote).join(' ')}`;
const child=spawn('powershell.exe',['-NoProfile','-Command',command],{windowsHide:true,env:{...process.env,JAVA_HOME:resolve(runtime,'jdk/jdk-17.0.20.1+1'),SERVER_PORT:String(port),SERVER_ADDRESS:'127.0.0.1',SPRING_DATASOURCE_URL:'jdbc:h2:mem:collab;MODE=MySQL;DB_CLOSE_DELAY=-1',SPRING_DATASOURCE_DRIVER_CLASS_NAME:'org.h2.Driver',SPRING_DATASOURCE_USERNAME:'sa',SPRING_DATASOURCE_PASSWORD:'',SPRING_JPA_HIBERNATE_DDL_AUTO:'create-drop',SPRING_FLYWAY_ENABLED:'false',AUTH_ENABLED:'true',SUPABASE_ISSUER_URI:issuer,SUPABASE_JWK_SET_URI:`${issuer}/jwks`,AI_SERVICE_URL:'http://127.0.0.1:8000'}});
let logs='',appPid;function capture(b){logs=(logs+b).slice(-12000);const m=logs.match(/Starting ChimaeraApplication[^\r\n]* with PID (\d+)/);if(m)appPid=Number(m[1]);}child.stdout.on('data',capture);child.stderr.on('data',capture);
function token(subject){const enc=x=>Buffer.from(JSON.stringify(x)).toString('base64url'),now=Math.floor(Date.now()/1000),input=`${enc({alg:'RS256',kid:jwk.kid})}.${enc({sub:subject,email:`${subject}@test.local`,iss:issuer,iat:now,exp:now+3600})}`;return `${input}.${sign('RSA-SHA256',Buffer.from(input),privateKey).toString('base64url')}`;}
async function api(user,path,method='GET',body,expected=200){const response=await fetch(`${base}/api${path}`,{method,headers:{Authorization:`Bearer ${token(user)}`,'Content-Type':'application/json'},...(body===undefined?{}:{body:JSON.stringify(body)})});const raw=await response.text();assert.equal(response.status,expected,`${method} ${path}: ${raw}`);return raw?JSON.parse(raw):null;}
async function profile(user,skills){return api(user,'/auth/sync','POST',{displayName:user,skills,interests:[],availabilityStatus:'AVAILABLE',profileVisibility:'MEMBERS',timezone:'UTC'});}
async function offer(){const quest=await api('sender','/quests','POST',{title:'Collaboration test',publicSummary:'Need React help',privateDescription:'Need React help',deadline:'2099-01-01',maxMembers:3,desiredSkills:['react']},201);const matches=await api('sender',`/quests/${quest.id}/match`,'POST');const worker=matches.find(x=>x.name==='receiver');assert(worker);const draft=await api('sender',`/quests/${quest.id}/proposals/draft`,'POST',{candidateId:worker.id,candidateName:worker.name});return api('sender',`/proposals/${draft.id}/send`,'POST',{approved:true});}
let checks=0,pass=name=>{checks++;console.log(`PASS ${name}`);};
try {
  for(let i=0;i<120;i++){try{if((await fetch(`${base}/actuator/health`)).ok)break;}catch{}if(i===119)throw Error('startup timeout');await new Promise(r=>setTimeout(r,1000));}
  await profile('sender',['planning']);await profile('receiver',['react']);await profile('stranger',['rust']);
  const declined=await offer();assert.equal((await api('sender','/inbox/sent'))[0].id,declined.id);pass('sender sees sent request');
  await api('receiver',`/inbox/proposals/${declined.id}/respond`,'POST',{decision:'DECLINED'});await api('receiver',`/proposals/${declined.id}/messages`,'GET',undefined,404);pass('decline does not open chat');
  const accepted=await offer();await api('receiver',`/inbox/proposals/${accepted.id}/respond`,'POST',{decision:'ACCEPTED'});
  await api('receiver',`/proposals/${accepted.id}/messages`,'POST',{body:'I can help.'},201);const chat=await api('sender',`/proposals/${accepted.id}/messages`);assert.equal(chat[0].body,'I can help.');await api('stranger',`/proposals/${accepted.id}/messages`,'GET',undefined,404);pass('accepted offer enables participant-only chat');
  await api('receiver',`/inbox/proposals/${accepted.id}`,'DELETE',undefined,204);assert(!(await api('receiver','/inbox/proposals')).some(x=>x.id===accepted.id));assert((await api('sender','/inbox/sent')).some(x=>x.id===accepted.id));pass('receiver delete hides offer but preserves sender history');
  console.log(`SUCCESS: ${checks} collaboration checks`);
} catch(e){console.error(e);console.error(logs);process.exitCode=1;} finally {if(appPid)spawnSync('powershell.exe',['-NoProfile','-Command',`Stop-Process -Id ${appPid} -ErrorAction SilentlyContinue`],{windowsHide:true,stdio:'ignore'});jwks.closeAllConnections();await new Promise(r=>jwks.close(r));}
