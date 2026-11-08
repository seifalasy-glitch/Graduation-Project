import { useEffect, useState } from "react";
import Icon from "../Shared_components/Icon";
import { addTicketMessage, getTicketMessages } from "../../supportApi";
import { Ticket, TicketMessage, User } from "../../types";
import { shortDate, timeText } from "../../format";

type TicketPanelProps = {
  ticket: Ticket;
  users: User[];
  category: string;
  onClose: () => void;
  onStatus: (status: string) => void;
};

function TicketPanel({
  ticket,
  users,
  category,
  onClose,
  onStatus,
}: TicketPanelProps) {
  const [messages, setMessages] = useState<TicketMessage[]>([]);
  const [note, setNote] = useState("");
  const [error, setError] = useState("");

  useEffect(() => {
    async function loadMessages() {
      try {
        setMessages(await getTicketMessages(ticket.id));
      } catch (err) {
        setError("Can't load the conversation.");
      }
    }
    loadMessages();
  }, [ticket.id]);

  function getSender(userId: number) {
    if (userId === 0) {
      return { name: "EduSync AI", role: "AI Assistant", initials: "AI" };
    }
    const user = users.find((item) => item.id === userId);
    if (user === undefined) {
      return { name: "Unknown", role: "", initials: "?" };
    }
    let role = "Student";
    if (user.role === "doctor") {
      role = "Faculty";
    }
    if (user.role === "admin") {
      role = "Admin";
    }
    let initials = "";
    user.name
      .replace("Dr. ", "")
      .split(" ")
      .forEach((part) => {
        initials = initials + part[0];
      });
    return { name: user.name, role: role, initials: initials };
  }

  async function handleNote(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (note.trim() === "") {
      return;
    }
    try {
      const saved = await addTicketMessage({
        ticket_id: ticket.id,
        from_user_id: 1,
        text: note.trim(),
        note: true,
        created_at: new Date().toISOString(),
      });
      setMessages([...messages, saved]);
      setNote("");
    } catch (err) {
      setError("Couldn't add the note.");
    }
  }

  const requester = getSender(ticket.requester_id);
  const done = ticket.status === "Resolved";

  return (
    <div>
      <div className="overlay" onClick={onClose} />
      <aside className="drawer desk-drawer">
        <div className="desk-drawer-head">
          <p className="desk-id">
            TCK-{ticket.id} · opened {shortDate(ticket.created_at)},{" "}
            {timeText(ticket.created_at)}
          </p>
          <h2 className="desk-drawer-title">{ticket.subject}</h2>
          <button className="modal-close" onClick={onClose}>
            <Icon name="x" />
          </button>
          <div className="desk-facts">
            <div className="desk-fact">
              <p className="subtle">Requester</p>
              <p className="desk-fact-value">{requester.name}</p>
            </div>
            <div className="desk-fact">
              <p className="subtle">Category</p>
              <p className="desk-fact-value">{category}</p>
            </div>
            <div className="desk-fact">
              <p className="subtle">Priority</p>
              <p className="desk-fact-value">{ticket.priority}</p>
            </div>
            <div className="desk-fact">
              <p className="subtle">Status</p>
              <select
                className="desk-status-select"
                value={ticket.status}
                onChange={(e) => onStatus(e.target.value)}
              >
                <option>Open</option>
                <option>Pending</option>
                <option>Resolved</option>
              </select>
            </div>
          </div>
        </div>

        <div className="desk-thread">
          {error !== "" && <p className="msg-error">{error}</p>}
          {messages.map((message) => {
            const sender = getSender(message.from_user_id);
            return (
              <div
                key={message.id}
                className={message.note ? "desk-msg note" : "desk-msg"}
              >
                <p className="desk-msg-head">
                  <span
                    className={
                      sender.role === "AI Assistant"
                        ? "desk-msg-avatar ai"
                        : "desk-msg-avatar"
                    }
                  >
                    {sender.initials}
                  </span>
                  <b>{sender.name}</b>
                  <span className="subtle">
                    {message.note ? "Internal note" : sender.role}
                  </span>
                  <span className="desk-msg-time">
                    {shortDate(message.created_at)},{" "}
                    {timeText(message.created_at)}
                  </span>
                </p>
                <p className="desk-msg-text">{message.text}</p>
              </div>
            );
          })}
          {done && ticket.decision !== "" && (
            <p className="desk-decision">
              <b>Decision:</b> {ticket.decision}
            </p>
          )}
          {done && (
            <p className="desk-resolved">
              ✓ Resolved {shortDate(ticket.resolved_at)},{" "}
              {timeText(ticket.resolved_at)}
            </p>
          )}
        </div>

        <div className="desk-drawer-foot">
          <form className="desk-note-form" onSubmit={handleNote}>
            <input
              className="input desk-note-input"
              value={note}
              placeholder="Add internal note — only admins can see this"
              onChange={(e) => setNote(e.target.value)}
            />
            <button
              className="btn-ghost desk-note-btn"
              disabled={note.trim() === ""}
            >
              Add note
            </button>
          </form>
          {done ? (
            <button
              className="btn-ghost desk-wide"
              onClick={() => onStatus("Open")}
            >
              Reopen ticket
            </button>
          ) : (
            <button
              className="btn-primary desk-wide"
              onClick={() => onStatus("Resolved")}
            >
              ✓ Mark as Resolved
            </button>
          )}
        </div>
      </aside>
    </div>
  );
}

export default TicketPanel;
