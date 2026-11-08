import { useEffect, useState } from "react";
import "./StudentCourses.css";
import Icon from "../Shared_components/Icon";
import {
  addAiMessage,
  addConversation,
  addTicket,
  addTicketMessage,
  getAiMessages,
  getConversations,
} from "../../supportApi";
import { getMaterials } from "../../learningApi";
import { CourseInfo } from "../../courseHelpers";
import { AiConversation, AiMessage, Enrollment, User } from "../../types";
import { timeText } from "../../format";

type AiChatProps = {
  user: User;
  info: CourseInfo;
  enrollment: Enrollment;
  doctorId: number;
  doctorName: string;
  onReturn: () => void;
};

function AiChat({
  user,
  info,
  enrollment,
  doctorId,
  doctorName,
  onReturn,
}: AiChatProps) {
  const [conversation, setConversation] = useState<AiConversation | null>(null);
  const [messages, setMessages] = useState<AiMessage[]>([]);
  const [sources, setSources] = useState<string[]>([]);
  const [draft, setDraft] = useState("");
  const [thinking, setThinking] = useState(false);
  const [phase, setPhase] = useState("chat");
  const [error, setError] = useState("");

  useEffect(() => {
    async function loadChat() {
      try {
        const list = await getConversations(user.id, info.offering.id);
        if (list.length > 0) {
          setConversation(list[0]);
          setMessages(await getAiMessages(list[0].id));
        }
        const materials = await getMaterials(info.offering.id);
        setSources(
          materials
            .filter((m) => m.in_ai && m.ai_state === "parsed")
            .map((m) => m.title),
        );
      } catch (err) {
        setError("Can't load the assistant. Did you run: npm run server ?");
      }
    }
    loadChat();
  }, [user.id, info.offering.id]);

  async function handleSend(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const text = draft.trim();
    if (text === "" || thinking) {
      return;
    }
    setDraft("");
    setPhase("chat");
    setThinking(true);
    try {
      let current = conversation;
      if (current === null) {
        current = await addConversation({
          user_id: user.id,
          offering_id: info.offering.id,
          title: text.slice(0, 40),
          created_at: new Date().toISOString(),
          updated_at: new Date().toISOString(),
        });
        setConversation(current);
      }
      const question = await addAiMessage({
        conversation_id: current.id,
        sender: "user",
        content: text,
        citations: [],
        created_at: new Date().toISOString(),
      });
      // ASSUMPTION A19: there is no AI service yet, so the answer is a fixed text and the sources are the course materials marked "in AI".
      const answer = await addAiMessage({
        conversation_id: current.id,
        sender: "ai",
        content:
          "Based on the " +
          info.course.course_code +
          " materials: this topic is covered in the lectures listed below. Review the worked examples there, and ask a follow-up question if any step is unclear.",
        citations: sources.slice(0, 2),
        created_at: new Date().toISOString(),
      });
      setMessages([...messages, question, answer]);
    } catch (err) {
      setError("Couldn't send your question, try again.");
    }
    setThinking(false);
  }

  async function handleEscalate() {
    setPhase("sending");
    try {
      const firstQuestion = messages.find((m) => m.sender === "user");
      const ticket = await addTicket({
        enrollment_id: enrollment.id,
        requester_id: user.id,
        doctor_user_id: doctorId,
        category_id: 1,
        subject:
          firstQuestion !== undefined
            ? firstQuestion.content.slice(0, 60)
            : "AI thread",
        description: firstQuestion !== undefined ? firstQuestion.content : "",
        status: "Open",
        decision: "",
        priority: "Normal",
        created_at: new Date().toISOString(),
        resolved_at: "",
      });
      for (const message of messages) {
        await addTicketMessage({
          ticket_id: ticket.id,
          from_user_id: message.sender === "ai" ? 0 : user.id,
          text: message.content,
          note: false,
          created_at: message.created_at,
        });
      }
      setPhase("escalated");
    } catch (err) {
      setError("Couldn't create the thread, try again.");
      setPhase("chat");
    }
  }

  let lastAiId = 0;
  messages.forEach((m) => {
    if (m.sender === "ai") {
      lastAiId = m.id;
    }
  });

  let citationCount = 0;
  messages.forEach((m) => {
    citationCount = citationCount + m.citations.length;
  });

  return (
    <div className="chat">
      <header className="chat-head">
        <button className="chat-back" onClick={onReturn}>
          ←
        </button>
        <span className="chat-spark">
          <Icon name="spark" />
        </span>
        <span className="chat-head-text">
          <b>AI Assistant</b>
          <span>
            {info.course.course_code}: {info.course.title} · Answers from course
            materials
          </span>
        </span>
      </header>

      {error !== "" && <p className="msg-error">{error}</p>}

      {phase === "escalated" ? (
        <div className="chat-done">
          <span className="chat-done-icon">
            <Icon name="check" size={24} />
          </span>
          <p className="chat-done-title">Thread Created</p>
          <p className="chat-done-text">
            Your message and the AI context have been sent to the Doctor
            securely.
          </p>
          <div className="chat-done-card">
            <p className="chat-small">🔒 Private thread · {doctorName}</p>
            <p className="chat-done-quote">
              {messages.length > 0 ? messages[0].content : ""}
            </p>
            <p className="chat-small">
              {messages.length} messages · {citationCount} sources attached
            </p>
          </div>
          <button className="btn-primary" onClick={onReturn}>
            Return to Course
          </button>
        </div>
      ) : (
        <div>
          <div className="chat-messages">
            {messages.length === 0 && (
              <p className="empty">
                Ask your first question about {info.course.course_code}.
              </p>
            )}
            {messages.map((m) =>
              m.sender === "user" ? (
                <div key={m.id} className="chat-user">
                  <div className="chat-user-bubble">
                    {m.content}
                    <p className="chat-small">You · {timeText(m.created_at)}</p>
                  </div>
                </div>
              ) : (
                <div key={m.id} className="chat-ai">
                  <span className="chat-spark">
                    <Icon name="spark" />
                  </span>
                  <div className="chat-ai-body">
                    <p className="chat-ai-text">{m.content}</p>
                    {m.citations.length > 0 && (
                      <div className="chat-sources">
                        <p className="chat-small">Sources</p>
                        {m.citations.map((c) => (
                          <span key={c} className="chat-source">
                            <Icon name="file" size={12} /> Source: {c}
                          </span>
                        ))}
                      </div>
                    )}
                    <p className="chat-small">
                      AI Assistant · {timeText(m.created_at)}
                    </p>
                    {m.id === lastAiId && !thinking && phase === "closed" && (
                      <p className="chat-closed">
                        ✓ Marked as resolved. Chat closed.
                      </p>
                    )}
                    {m.id === lastAiId && !thinking && phase !== "closed" && (
                      <div className="chat-fork">
                        <p className="chat-fork-title">
                          Is the question resolved?
                        </p>
                        <button
                          className="btn-ghost btn-small"
                          disabled={phase === "sending"}
                          onClick={() => setPhase("closed")}
                        >
                          Yes, Close Chat
                        </button>
                        <button
                          className="btn-primary btn-small chat-fork-ask"
                          disabled={phase === "sending"}
                          onClick={handleEscalate}
                        >
                          {phase === "sending"
                            ? "Creating private thread…"
                            : "No, Ask Doctor"}
                        </button>
                        <p className="chat-small">
                          Asking the doctor converts this chat into a private
                          thread with {doctorName}.
                        </p>
                      </div>
                    )}
                  </div>
                </div>
              ),
            )}
            {thinking && (
              <p className="chat-thinking">AI Assistant is typing…</p>
            )}
          </div>
          <form className="chat-form" onSubmit={handleSend}>
            <textarea
              className="chat-input"
              rows={1}
              value={draft}
              placeholder={"Ask about " + info.course.course_code + "…"}
              onChange={(e) => setDraft(e.target.value)}
            />
            <button
              className="chat-send"
              disabled={draft.trim() === "" || thinking}
            >
              <Icon name="arrow" />
            </button>
            <p className="chat-small">
              AI answers are generated from {info.course.course_code} materials
              and may contain mistakes.
            </p>
          </form>
        </div>
      )}
    </div>
  );
}

export default AiChat;
