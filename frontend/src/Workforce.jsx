import { useEffect, useState } from "react";
const API = import.meta.env.VITE_API_URL || "/api";
async function api(session, path, body, method = "POST") {
  const r = await fetch(`${API}/workforce${path}`, {method: body === undefined ? "GET" : method,
    headers: {Authorization: `Bearer ${session.access_token}`, "Content-Type": "application/json"},
    ...(body !== undefined ? {body: JSON.stringify(body)} : {})});
  if (!r.ok) { const error = await r.json().catch(() => ({})); throw new Error(error.detail || error.message || `Unable to complete request (${r.status}). Refresh the plan and retry.`); }
  const text = await r.text(); return text ? JSON.parse(text) : null;
}
export function WorkforceTasks({session}) {
  const [tasks,setTasks]=useState([]),[error,setError]=useState(""),[busy,setBusy]=useState(false);
  function load(){return api(session,"/tasks").then(setTasks).catch(e=>setError(e.message));}
  useEffect(()=>{load();},[session.access_token]);
  async function complete(id){setBusy(true);try{await api(session,`/tasks/${id}/complete`,{});await load();}catch(e){setError(e.message);}finally{setBusy(false);}}
  return <section className="assigned-tasks"><div><small>Accepted work</small><h4>Your assigned tasks</h4></div>{error && <p role="alert">{error}</p>}{!tasks.length && <p>No accepted tasks yet.</p>}<div className="assigned-task-grid">{tasks.map(t=><article key={t.id}><strong>{t.title}</strong><p>{t.project}</p><span>{t.skills}</span><button disabled={busy} onClick={()=>complete(t.id)}>Mark complete</button></article>)}</div></section>;
}
