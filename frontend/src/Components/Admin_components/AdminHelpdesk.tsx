import { useEffect, useState } from "react";
import "./AdminHelpdesk.css";
import Icon from "../Shared_components/Icon";
import TicketPanel from "./TicketPanel";
import { getUsers } from "../../academicApi";
import {
  getTicketCategories,
  getTickets,
  updateTicket,
} from "../../supportApi";
import { Ticket, TicketCategory, User } from "../../types";
import { shortDate, timeText } from "../../format";

const tabs = ["All", "Open", "Pending", "Resolved"];

function AdminHelpdesk() {
  const [tickets, setTickets] = useState<Ticket[]>([]);
  const [categories, setCategories] = useState<TicketCategory[]>([]);
  const [users, setUsers] = useState<User[]>([]);
  const [tab, setTab] = useState("All");
  const [search, setSearch] = useState("");
  const [openId, setOpenId] = useState(0);
  const [error, setError] = useState("");

  useEffect(() => {
    async function loadData() {
      try {
        setTickets(await getTickets());
        setCategories(await getTicketCategories());
        setUsers(await getUsers());
      } catch (err) {
        setError("Can't load tickets. Did you run: npm run server ?");
      }
    }
    loadData();
  }, []);

  function getUser(userId: number) {
    return users.find((item) => item.id === userId);
  }

  function getCategory(categoryId: number) {
    const category = categories.find((item) => item.id === categoryId);
    if (category === undefined) {
      return "";
    }
    return category.name;
  }

  async function handleStatus(ticket: Ticket, status: string) {
    try {
      const updated = await updateTicket(ticket.id, {
        status: status,
        resolved_at: status === "Resolved" ? new Date().toISOString() : "",
      });
      setTickets(
        tickets.map((item) => (item.id === ticket.id ? updated : item)),
      );
    } catch (err) {
      setError("Couldn't update the ticket, try again.");
    }
  }

  const text = search.toLowerCase();
  const rows = tickets.filter((ticket) => {
    const requester = getUser(ticket.requester_id);
    const words = (
      "TCK-" +
      ticket.id +
      " " +
      ticket.subject +
      " " +
      (requester ? requester.name : "") +
      " " +
      getCategory(ticket.category_id)
    ).toLowerCase();
    return (tab === "All" || ticket.status === tab) && words.includes(text);
  });

  const open = tickets.find((ticket) => ticket.id === openId);

  return (
    <div>
      <p className="desk-eyebrow">Support</p>
      <h1 className="desk-title">Helpdesk</h1>
      <p className="desk-desc">
        Issues and escalated AI threads from students and faculty, in one queue.
      </p>

      {error !== "" && <p className="msg-error">{error}</p>}

      <div className="desk-stats">
        {["Open", "Pending", "Resolved"].map((status) => (
          <div key={status} className="card desk-stat">
            <p className="subtle">{status}</p>
            <p
              className={
                status === "Open" ? "desk-stat-value open" : "desk-stat-value"
              }
            >
              {tickets.filter((ticket) => ticket.status === status).length}
            </p>
          </div>
        ))}
      </div>

      <div className="desk-filters">
        <div className="desk-tabs">
          {tabs.map((item) => (
            <button
              key={item}
              className={tab === item ? "desk-tab active" : "desk-tab"}
              onClick={() => setTab(item)}
            >
              {item}
            </button>
          ))}
        </div>
        <div className="desk-search">
          <span className="desk-search-icon">
            <Icon name="search" />
          </span>
          <input
            className="input desk-search-input"
            value={search}
            placeholder="Search tickets, requesters or categories"
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
      </div>

      <div className="table-wrap desk-table">
        <table className="table">
          <thead>
            <tr>
              <th>Ticket ID</th>
              <th>Requester</th>
              <th>Category</th>
              <th>Status</th>
              <th>Date Resolved</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((ticket) => {
              const requester = getUser(ticket.requester_id);
              const category = getCategory(ticket.category_id);
              return (
                <tr
                  key={ticket.id}
                  className="desk-row"
                  onClick={() => setOpenId(ticket.id)}
                >
                  <td>
                    <p className="desk-id">TCK-{ticket.id}</p>
                    <p className="desk-subject">{ticket.subject}</p>
                  </td>
                  <td>
                    <p className="desk-strong">
                      {requester ? requester.name : "Unknown"}
                    </p>
                    <p className="desk-small">
                      {requester && requester.role === "doctor"
                        ? "Faculty"
                        : "Student"}
                    </p>
                  </td>
                  <td className="muted">
                    {category === "Escalated AI Thread" && (
                      <span className="desk-ai-dot" />
                    )}
                    {category}
                  </td>
                  <td>
                    <span className={"desk-status " + ticket.status}>
                      {ticket.status}
                    </span>
                  </td>
                  <td className="muted">
                    {ticket.resolved_at !== "" ? (
                      shortDate(ticket.resolved_at) +
                      ", " +
                      timeText(ticket.resolved_at)
                    ) : (
                      <span className="desk-none">—</span>
                    )}
                  </td>
                </tr>
              );
            })}
            {rows.length === 0 && (
              <tr>
                <td colSpan={5} className="empty">
                  No tickets here.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {open !== undefined && (
        <TicketPanel
          ticket={open}
          users={users}
          category={getCategory(open.category_id)}
          onClose={() => setOpenId(0)}
          onStatus={(status) => handleStatus(open, status)}
        />
      )}
    </div>
  );
}

export default AdminHelpdesk;
