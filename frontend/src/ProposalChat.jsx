import { useEffect, useRef, useState } from "react";
const API = import.meta.env.VITE_API_URL || "/api";

export default function ProposalChat({ proposal, session, onClose }) {
  const [messages, setMessages] = useState([]);
  const [body, setBody] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const endRef = useRef(null);
  async function load(quiet = false) {
    try {
      const response = await fetch(`${API}/proposals/${proposal.id}/messages`, { headers: { Authorization: `Bearer ${session.access_token}` } });
      if (!response.ok) throw new Error("Unable to load this conversation.");
      setMessages(await response.json()); setError("");
    } catch (e) { if (!quiet) setError(e.message); }
  }
  useEffect(() => {
    load();
    const timer = setInterval(() => load(true), 4000);
    return () => clearInterval(timer);
  }, [proposal.id, session.access_token]);
  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages.length]);
  async function send(event) {
    event.preventDefault();
    if (!body.trim() || busy) return;
    setBusy(true); setError("");
    try {
      const response = await fetch(`${API}/proposals/${proposal.id}/messages`, {
        method: "POST", headers: { Authorization: `Bearer ${session.access_token}`, "Content-Type": "application/json" },
        body: JSON.stringify({ body: body.trim() }),
      });
      if (!response.ok) throw new Error("Message could not be sent.");
      const message = await response.json(); setMessages(current => [...current, message]); setBody("");
    } catch (e) { setError(e.message); } finally { setBusy(false); }
  }
  const other = proposal.chatPartner || proposal.senderName || proposal.receiverName || "Collaborator";
  return <aside className="proposal-chat" aria-label={`Chat with ${other}`}>
    <header><div><small>Accepted collaboration</small><h4>{proposal.questTitle}</h4><p>{other}</p></div><button type="button" onClick={onClose} aria-label="Close chat">×</button></header>
    <div className="chat-messages" aria-live="polite">
      {!messages.length && !error && <p className="chat-empty">Offer accepted. Start the conversation.</p>}
      {messages.map(message => <div key={message.id} className={`chat-bubble ${message.mine ? "mine" : "theirs"}`}><span>{message.mine ? "You" : message.senderName}</span><p>{message.body}</p><time>{new Date(message.createdAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}</time></div>)}
      <div ref={endRef} />
    </div>
    {error && <p className="auth-error" role="alert">{error}</p>}
    <form onSubmit={send}><textarea rows="2" maxLength="2000" value={body} onChange={e => setBody(e.target.value)} placeholder="Write a message…" /><button disabled={busy || !body.trim()}>{busy ? "Sending…" : "Send"}</button></form>
  </aside>;
}
