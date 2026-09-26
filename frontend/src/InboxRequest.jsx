import { useState } from "react";
import { ResumeButton } from "./ProfilePanel";
const API = import.meta.env.VITE_API_URL || "/api";

export default function InboxRequest({ item, session, onUpdated, onAccepted, onDeleted }) {
  const [review, setReview] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function request(decision) {
    setBusy(true); setError("");
    try {
      const response = await fetch(`${API}/inbox/proposals/${item.id}${decision ? "/respond" : ""}`, {
        method: decision ? "POST" : "GET",
        headers: { Authorization: `Bearer ${session.access_token}`, "Content-Type": "application/json" },
        ...(decision ? { body: JSON.stringify({ decision }) } : {}),
      });
      if (!response.ok) throw new Error(response.status === 409 ? "This request was already answered. Close and reopen it to refresh." : "Unable to update this request. Please retry.");
      const data = await response.json(); setReview(data); onUpdated(data);
      if (decision === "ACCEPTED") onAccepted(data);
    } catch (e) { setError(e.message); } finally { setBusy(false); }
  }
  async function remove() {
    setBusy(true); setError("");
    try {
      const response = await fetch(`${API}/inbox/proposals/${item.id}`, { method: "DELETE", headers: { Authorization: `Bearer ${session.access_token}` } });
      if (!response.ok) throw new Error("Unable to delete this offer.");
      onDeleted(item.id);
    } catch (e) { setError(e.message); } finally { setBusy(false); }
  }
  return <article className="request-card">
    <div className={`inbox-status status-${item.status.toLowerCase()}`}><i /><span>{item.status === "SENT" ? "Awaiting your response" : item.status}</span></div>
    <h4>{item.questTitle}</h4><p>From {item.senderName || "a collaborator"}</p>
    <p>{item.message}</p>
    <div className="request-toolbar"><time>{item.receivedAt ? new Date(item.receivedAt).toLocaleString() : "Recently received"}</time>
      <div className="request-actions"><button type="button" disabled={busy} aria-expanded={!!review} onClick={() => review ? setReview(null) : request()}>{review ? "Close review" : busy ? "Loading…" : "Review request"}</button><button type="button" disabled={busy} onClick={remove}>Delete</button></div>
    </div>
    {error && <p role="alert" className="auth-error">{error}</p>}
    {review && <section className="request-review" aria-label="Request review">
      <h5>About the request</h5><p>{review.summary}</p>
      <dl><div><dt>Sent by</dt><dd>{review.senderName}</dd></div><div><dt>Target date</dt><dd>{review.deadline || "Not specified"}</dd></div></dl>
      <div className="capabilities">{review.skills?.map(skill => <b key={skill}>{skill}</b>)}</div>
      <ResumeButton session={session} path={`/inbox/proposals/${item.id}/sender-resume`} label="Download sender’s resume" />
      {review.status === "SENT" ? <div className="decision-actions"><button type="button" disabled={busy} onClick={() => request("ACCEPTED")}>Accept request</button><button type="button" disabled={busy} onClick={() => request("DECLINED")}>Decline request</button></div> : <div><p role="status">You {review.status.toLowerCase()} this request{review.respondedAt ? ` on ${new Date(review.respondedAt).toLocaleString()}` : ""}.</p>{review.status === "ACCEPTED" && <button type="button" onClick={() => onAccepted(review)}>Open chat</button>}</div>}
    </section>}
  </article>;
}
