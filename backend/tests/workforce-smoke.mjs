// Isolated HTTP integration test. Uses temporary RSA identities and an H2 memory
// database; never reads the real .env or writes to the user's MySQL database.
import {createServer} from 'node:http';
import {generateKeyPairSync,sign} from 'node:crypto';
import {spawn,spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {resolve,dirname} from 'node:path';
import assert from 'node:assert/strict';
const project=resolve(dirname(fileURLToPath(import.meta.url)),'../..');
const runtime=resolve(project,'../../work/runtime');
const {privateKey,publicKey}=generateKeyPairSync('rsa',{modulusLength:2048});
const jwk={...publicKey.export({format:'jwk'}),kid:'isolated-workforce-test',use:'sig',alg:'RS256'};
const jwks=createServer((req,res)=>{res.setHeader('Content-Type','application/json');res.end(JSON.stringify({keys:[jwk]}));});
await new Promise(r=>jwks.listen(0,'127.0.0.1',r));
const issuer=`http://127.0.0.1:${jwks.address().port}`;
const reservation=createServer();await new Promise(r=>reservation.listen(0,'127.0.0.1',r));
const port=reservation.address().port;await new Promise(r=>reservation.close(r));
const base=`http://127.0.0.1:${port}`;
const quote=s=>`'${s.replaceAll("'","''")}'`;
const command=`& ${quote(resolve(runtime,'maven-3.9.16/bin/mvn.cmd'))} ${[
  `-Dmaven.repo.local=${resolve(project,'backend/.m2')}`,'-Dmaven.main.skip=true','-Dmaven.test.skip=true',
  '-f',resolve(project,'backend/pom.xml'),'spring-boot:run'].map(quote).join(' ')}`;
const child=spawn('powershell.exe',['-NoProfile','-Command',command],{windowsHide:true,env:{...process.env,
  JAVA_HOME:resolve(runtime,'jdk/jdk-17.0.20.1+1'), SERVER_PORT:String(port), SERVER_ADDRESS:'127.0.0.1',
  SPRING_DATASOURCE_URL:'jdbc:h2:mem:workforce_smoke;MODE=MySQL;DB_CLOSE_DELAY=-1',
  SPRING_DATASOURCE_DRIVER_CLASS_NAME:'org.h2.Driver',SPRING_DATASOURCE_USERNAME:'sa',SPRING_DATASOURCE_PASSWORD:'',
  SPRING_JPA_HIBERNATE_DDL_AUTO:'create-drop',SPRING_FLYWAY_ENABLED:'false',SPRING_PROFILES_ACTIVE:'',
  AUTH_ENABLED:'true',SUPABASE_ISSUER_URI:issuer,SUPABASE_JWK_SET_URI:`${issuer}/jwks`,AI_SERVICE_URL:'http://127.0.0.1:8000'}});
let logs='',appPid;
function capture(b){logs=(logs+b).slice(-15000);const match=logs.match(/Starting ChimaeraApplication[^\r\n]* with PID (\d+)/);if(match)appPid=Number(match[1]);}
child.stdout.on('data',capture);child.stderr.on('data',capture);
function token(subject){const encode=x=>Buffer.from(JSON.stringify(x)).toString('base64url');const now=Math.floor(Date.now()/1000);
  const input=`${encode({alg:'RS256',kid:jwk.kid})}.${encode({sub:subject,email:`${subject}@example.test`,iss:issuer,iat:now,exp:now+3600})}`;
  return `${input}.${sign('RSA-SHA256',Buffer.from(input),privateKey).toString('base64url')}`;}
async function api(user,path,method='GET',body,expected=200){const r=await fetch(`${base}/api${path}`,{
  method,headers:{Authorization:`Bearer ${token(user)}`,'Content-Type':'application/json'},
  ...(body===undefined?{}:{body:JSON.stringify(body)})});const raw=await r.text();assert.equal(r.status,expected,`${method} ${path}: ${raw}`);return raw?JSON.parse(raw):null;}
async function profile(user,skills){return api(user,'/auth/sync','POST',{displayName:user,skills,interests:[],availabilityStatus:'AVAILABLE',profileVisibility:'MEMBERS',timezone:'UTC'});}
async function projectFor(user){return api(user,'/quests','POST',{title:'Isolated workforce test',publicSummary:'Python API',privateDescription:'Build a Python API',deadline:'2099-01-01',maxMembers:3,desiredSkills:['python']},201);}
let checks=0;const pass=name=>{checks++;console.log(`PASS ${name}`);};
try {
  for(let i=0;i<120;i++){if(child.exitCode!==null)throw Error('Test backend exited');try{if((await fetch(`${base}/actuator/health`)).ok)break;}catch{}
    if(i===119)throw Error('Test backend startup timed out');await new Promise(r=>setTimeout(r,1000));}
  await profile('manager',['planning']);const worker=await profile('employee',['python']);await profile('stranger',['rust']);
  await api('employee','/workforce/profile','PUT',{experienceYears:5,weeklyHours:8,externalHours:2});
  const p=await projectFor('manager');const task={title:'Implement API',skills:['python'],hours:4};
  const planned=await api('manager',`/workforce/projects/${p.id}/plan`,'POST',{teamSize:2,tasks:[task]});
  assert.equal(planned.assignments[0].employeeId,worker.id);pass('real AI recommends eligible employee');
  await api('stranger',`/workforce/projects/${p.id}/tasks`,'GET',undefined,404);pass('non-owner project access denied');
  const allocation={approved:true,assignments:[{employeeId:worker.id,task}]};
  await api('manager',`/workforce/projects/${p.id}/allocate`,'POST',{...allocation,approved:false},400);pass('explicit approval enforced');
  const assigned=await api('manager',`/workforce/projects/${p.id}/allocate`,'POST',allocation);
  assert.equal((await api('employee','/workforce/tasks')).length,0);
  assert.equal((await api('employee','/workforce/profile')).assignedHours,4);pass('allocation persists and reserves employee workload');
  const invitation=(await api('employee','/inbox/proposals')).find(item=>item.questId===p.id);
  assert(invitation);await api('employee',`/inbox/proposals/${invitation.id}/respond`,'POST',{decision:'ACCEPTED'});
  assert.equal((await api('employee','/workforce/tasks'))[0].id,assigned[0].id);pass('tasks remain hidden until collaborator accepts');
  await api('manager',`/workforce/projects/${p.id}/allocate`,'POST',allocation,409);pass('duplicate allocation rejected');
  await api('stranger',`/workforce/tasks/${assigned[0].id}/complete`,'POST',{},404);pass('unrelated employee cannot complete task');
  await api('employee',`/workforce/tasks/${assigned[0].id}/complete`,'POST',{});
  assert.equal((await api('employee','/workforce/profile')).assignedHours,0);pass('completion releases workload');
  const p2=await projectFor('manager'),p3=await projectFor('manager');
  const results=await Promise.all([p2,p3].map(p=>fetch(`${base}/api/workforce/projects/${p.id}/allocate`,{
    method:'POST',headers:{Authorization:`Bearer ${token('manager')}`,'Content-Type':'application/json'},body:JSON.stringify(allocation)})));
  assert.deepEqual(results.map(r=>r.status).sort(),[200,409]);pass('concurrent project approvals cannot overbook employee');
  assert.equal((await api('employee','/workforce/profile')).assignedHours,4);
  console.log(`SUCCESS: ${checks} isolated workforce integration checks`);
} catch(e){console.error(e);console.error(logs);process.exitCode=1;}
finally {
  // Stop only the test JVM identified by its startup line. Its Maven parent exits
  // naturally; Windows taskkill /T may be denied inside a restricted runtime.
  if(appPid)spawnSync('powershell.exe',['-NoProfile','-Command',`Stop-Process -Id ${appPid} -ErrorAction SilentlyContinue`],{windowsHide:true,stdio:'ignore'});
  else if(child.pid)spawnSync('taskkill',['/PID',String(child.pid),'/T','/F'],{windowsHide:true,stdio:'ignore'});
  jwks.closeAllConnections();await new Promise(r=>jwks.close(r));
}
