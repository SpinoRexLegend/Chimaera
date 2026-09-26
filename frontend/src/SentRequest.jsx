export default function SentRequest({ item, onChat }) {
  return <article className="request-card">
    <div className={`inbox-status status-${item.status.toLowerCase()}`}><i /><span>{item.status === "SENT" ? "Awaiting response" : item.status}</span></div>
    <h4>{item.questTitle}</h4>
    <p>Sent to {item.receiverName || "a collaborator"}</p>
    <p>{item.message}</p>
    <div className="request-toolbar"><time>{item.receivedAt ? new Date(item.receivedAt).toLocaleString() : "Recently sent"}</time>
      {item.status === "ACCEPTED" && <button type="button" onClick={() => onChat(item)}>Open chat</button>}
    </div>
  </article>;
}
