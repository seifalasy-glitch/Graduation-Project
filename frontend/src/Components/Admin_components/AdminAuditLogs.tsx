import { useEffect, useState } from "react";
import "./AdminAuditLogs.css";
import Icon from "../Shared_components/Icon";
import AuditDetails from "./AuditDetails";
import { getAuditEvents, getUsers } from "../../academicApi";
import { AuditEvent, User } from "../../types";

const roleFilters = ["All roles", "Admin", "Faculty", "Student", "System"];

function AdminAuditLogs() {
  const [events, setEvents] = useState<AuditEvent[]>([]);
  const [users, setUsers] = useState<User[]>([]);
  const [search, setSearch] = useState("");
  const [role, setRole] = useState("All roles");
  const [open, setOpen] = useState<AuditEvent | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    async function loadData() {
      try {
        setEvents(await getAuditEvents());
        setUsers(await getUsers());
      } catch (err) {
        setError("Can't load audit logs. Did you run: npm run server ?");
      }
    }
    loadData();
  }, []);

  function getActor(actorId: number) {
    const actor = users.find((item) => item.id === actorId);
    if (actor === undefined) {
      return { name: "System", role: "System" };
    }
    let roleName = "Admin";
    if (actor.role === "doctor") {
      roleName = "Faculty";
    }
    if (actor.role === "student") {
      roleName = "Student";
    }
    return { name: actor.name, role: roleName };
  }

  function getInitials(name: string) {
    const parts = name.replace("Dr. ", "").split(" ");
    let text = "";
    parts.forEach((part) => {
      if (text.length < 2) {
        text = text + part[0];
      }
    });
    return text;
  }

  const today = new Date().toISOString().slice(0, 10);
  const todayCount = events.filter((event) =>
    event.created_at.startsWith(today),
  ).length;
  const securityCount = events.filter(
    (event) => event.kind === "security",
  ).length;

  const text = search.toLowerCase();
  const rows = events.filter((event) => {
    const actor = getActor(event.actor_user_id);
    const matchesRole = role === "All roles" || actor.role === role;
    const words = (
      actor.name +
      " " +
      event.action +
      " " +
      event.target_label +
      " EVT-" +
      event.id
    ).toLowerCase();
    return matchesRole && words.includes(text);
  });

  const stats = [
    { label: "Events today", value: String(todayCount) },
    { label: "Security events", value: String(securityCount) },
    { label: "Retention", value: "365 days" },
  ];

  return (
    <div>
      <p className="audit-eyebrow">Security</p>
      <div className="audit-head">
        <h1 className="audit-title">System Audit Logs</h1>
        <p className="audit-desc">
          An immutable record of every change made across EduSync. Open any
          event to compare the data before and after.
        </p>
        <div className="audit-stats">
          {stats.map((stat) => (
            <div key={stat.label} className="audit-stat">
              <p className="subtle">{stat.label}</p>
              <p className="audit-stat-value">{stat.value}</p>
            </div>
          ))}
        </div>
      </div>

      {error !== "" && <p className="msg-error">{error}</p>}

      <div className="audit-filters">
        <div className="audit-search">
          <span className="audit-search-icon">
            <Icon name="search" />
          </span>
          <input
            className="input audit-search-input"
            value={search}
            placeholder="Search actor, action, entity or event ID"
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
        <select
          className="input audit-role"
          value={role}
          onChange={(e) => setRole(e.target.value)}
        >
          {roleFilters.map((item) => (
            <option key={item}>{item}</option>
          ))}
        </select>
      </div>

      <div className="table-wrap audit-table">
        <table className="table">
          <thead>
            <tr>
              <th>Timestamp</th>
              <th>Actor</th>
              <th>Action Performed</th>
              <th>Target Entity</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {rows.map((event) => {
              const actor = getActor(event.actor_user_id);
              return (
                <tr key={event.id}>
                  <td>
                    <p>{event.created_at.slice(11, 19)}</p>
                    <p className="audit-small">
                      {event.created_at.slice(0, 10)}
                    </p>
                  </td>
                  <td>
                    <span className="audit-avatar">
                      {actor.role === "System" ? (
                        <Icon name="db" size={14} />
                      ) : (
                        getInitials(actor.name)
                      )}
                    </span>
                    <span className="audit-actor">
                      <b>{actor.name}</b>
                      <span className="audit-small">{actor.role}</span>
                    </span>
                  </td>
                  <td>
                    <span className={"audit-kind " + event.kind}>
                      {event.action}
                    </span>
                  </td>
                  <td>
                    <p>{event.target_label}</p>
                    <p className="audit-small">
                      {event.target_type} · EVT-{event.id}
                    </p>
                  </td>
                  <td className="audit-right">
                    <button
                      className="btn-ghost btn-small"
                      onClick={() => setOpen(event)}
                    >
                      View Details
                    </button>
                  </td>
                </tr>
              );
            })}
            {rows.length === 0 && (
              <tr>
                <td colSpan={5} className="empty">
                  No events match your filters.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      <p className="audit-count">
        Showing {rows.length} of {events.length} events
      </p>

      {open !== null && (
        <AuditDetails
          event={open}
          actor={getActor(open.actor_user_id)}
          onClose={() => setOpen(null)}
        />
      )}
    </div>
  );
}

export default AdminAuditLogs;
