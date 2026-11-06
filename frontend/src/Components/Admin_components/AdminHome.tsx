import { useEffect, useState } from "react";
import "./AdminHome.css";
import Icon from "../Shared_components/Icon";
import {
  getAuditEvents,
  getEnrollments,
  getOfferings,
  getRooms,
  getTeachingAssignments,
  getTerms,
  getUsers,
  getGroups,
} from "../../academicApi";
import {
  AcademicTerm,
  AuditEvent,
  CourseOffering,
  Enrollment,
  Room,
  StudentGroup,
  TeachingAssignment,
  User,
} from "../../types";
import {
  greetingText,
  firstName,
  shortDate,
  termName,
  timeAgo,
  weekOfTerm,
} from "../../format";

type AdminHomeProps = {
  user: User;
  onNavigate: (page: string) => void;
};

type Stat = {
  label: string;
  value: number;
  delta: string;
  icon: string;
  breakdown: { label: string; value: number }[];
};

const barColors = ["#7f56d9", "#d6bbfb", "#ebe9fe"];

function AdminHome({ user, onNavigate }: AdminHomeProps) {
  const [users, setUsers] = useState<User[]>([]);
  const [terms, setTerms] = useState<AcademicTerm[]>([]);
  const [offerings, setOfferings] = useState<CourseOffering[]>([]);
  const [groups, setGroups] = useState<StudentGroup[]>([]);
  const [teaching, setTeaching] = useState<TeachingAssignment[]>([]);
  const [enrollments, setEnrollments] = useState<Enrollment[]>([]);
  const [rooms, setRooms] = useState<Room[]>([]);
  const [events, setEvents] = useState<AuditEvent[]>([]);
  const [error, setError] = useState("");

  useEffect(() => {
    async function loadData() {
      try {
        setUsers(await getUsers());
        setTerms(await getTerms());
        setOfferings(await getOfferings());
        setGroups(await getGroups());
        setTeaching(await getTeachingAssignments());
        setEnrollments(await getEnrollments());
        setRooms(await getRooms());
        setEvents(await getAuditEvents());
      } catch (err) {
        setError("Can't load the dashboard. Did you run: npm run server ?");
      }
    }
    loadData();
  }, []);

  const activeTerm = terms.find((term) => term.status === "Active");
  let termLine = "";
  let termEnd = "";
  let termOfferings: CourseOffering[] = [];
  if (activeTerm !== undefined) {
    const week = weekOfTerm(activeTerm.start_date, activeTerm.end_date);
    const name = termName(activeTerm.term_type, activeTerm.academic_year);
    termLine = name + " · Week " + week.week + " of " + week.total;
    termEnd = name + " active · ends " + shortDate(activeTerm.end_date);
    termOfferings = offerings.filter(
      (offering) => offering.term_id === activeTerm.id,
    );
  }

  const thisMonth = new Date().getMonth();
  const newUsers = users.filter(
    (item) => new Date(item.created_at).getMonth() === thisMonth,
  ).length;

  const assigned = termOfferings.filter((offering) =>
    groups.some(
      (group) =>
        group.offering_id === offering.id &&
        teaching.some((item) => item.group_id === group.id),
    ),
  ).length;

  const verifiedRooms = rooms.filter((room) => room.status === "Active");

  const students = users.filter((item) => item.role === "student");
  const unassigned = students.filter(
    (student) => !enrollments.some((e) => e.student_user_id === student.id),
  ).length;

  const lastImport = events.find((event) => event.action === "Users Imported");

  const stats: Stat[] = [
    {
      label: "Total Active Users",
      value: users.filter((item) => item.active).length,
      delta: "+" + newUsers + " this month",
      icon: "users",
      breakdown: [
        { label: "Students", value: students.length },
        {
          label: "Faculty",
          value: users.filter((item) => item.role === "doctor").length,
        },
        {
          label: "Admins",
          value: users.filter((item) => item.role === "admin").length,
        },
      ],
    },
    {
      label: "Current Term Courses",
      value: termOfferings.length,
      delta: termLine,
      icon: "book",
      breakdown: [
        { label: "Assigned", value: assigned },
        { label: "Unassigned", value: termOfferings.length - assigned },
      ],
    },
    {
      label: "Configured Rooms",
      value: rooms.length,
      delta: rooms.length + " with Wi-Fi geofence",
      icon: "door",
      breakdown: [
        { label: "Verified", value: verifiedRooms.length },
        { label: "Pending", value: rooms.length - verifiedRooms.length },
      ],
    },
  ];

  const actions = [
    {
      title: "Import Users",
      desc: "Bulk-create students and faculty from an Excel or CSV roster.",
      icon: "users",
      badge: "sheet",
      meta:
        lastImport !== undefined
          ? "Last import · " + timeAgo(lastImport.created_at)
          : "No imports yet",
      cta: "Upload file",
      page: "users",
    },
    {
      title: "Assign Enrollments",
      desc: "Link students to course sections and assign instructors.",
      icon: "link",
      badge: "users",
      meta: unassigned + " students unassigned",
      cta: "Start assigning",
      page: "enrollments",
    },
    {
      title: "Manage Academic Terms",
      desc: "Define term dates, add/drop windows, and exam periods.",
      icon: "calendar",
      badge: "calendar",
      meta: termEnd,
      cta: "Open terms",
      page: "terms",
    },
    {
      title: "Add New Room Profile",
      desc: "Register a room with its location and Wi-Fi network for attendance.",
      icon: "wifi",
      badge: "door",
      meta:
        rooms.length - verifiedRooms.length + " rooms awaiting verification",
      cta: "Add room",
      page: "rooms",
    },
  ];

  function getActorName(actorId: number) {
    if (actorId === user.id) {
      return "You";
    }
    const actor = users.find((item) => item.id === actorId);
    if (actor === undefined) {
      return "System";
    }
    return actor.name;
  }

  return (
    <div>
      <p className="page-eyebrow">{termLine}</p>
      <h1 className="home-title">
        {greetingText()}, {firstName(user.name)}
      </h1>
      <p className="page-desc">
        Here's the state of your institution at a glance.
      </p>

      {error !== "" && <p className="msg-error">{error}</p>}

      <section className="home-stats">
        {stats.map((stat) => {
          let total = 0;
          stat.breakdown.forEach((part) => {
            total = total + part.value;
          });
          return (
            <div key={stat.label} className="card home-stat">
              <p className="home-stat-label">
                {stat.label}
                <span className="home-stat-icon">
                  <Icon name={stat.icon} />
                </span>
              </p>
              <p className="home-stat-value">{stat.value}</p>
              <p className="home-stat-delta">{stat.delta}</p>
              <div className="home-stat-bar">
                {stat.breakdown.map((part, index) => (
                  <span
                    key={part.label}
                    className="home-stat-part"
                    style={{
                      width:
                        total > 0 ? (part.value / total) * 100 + "%" : "0%",
                      backgroundColor: barColors[index],
                    }}
                  />
                ))}
              </div>
              <p className="home-stat-legend">
                {stat.breakdown.map((part) => (
                  <span key={part.label} className="home-stat-legend-item">
                    {part.label} <b>{part.value}</b>
                  </span>
                ))}
              </p>
            </div>
          );
        })}
      </section>

      <section className="home-section">
        <h2 className="section-title">System Management</h2>
        <p className="section-desc">
          Entry points for the core administrative workflows.
        </p>
        <div className="home-actions">
          {actions.map((action) => (
            <button
              key={action.title}
              className="card home-action"
              onClick={() => onNavigate(action.page)}
            >
              <span className="home-action-tile">
                <Icon name={action.icon} size={24} />
                <span className="home-action-badge">
                  <Icon name={action.badge} size={14} />
                </span>
              </span>
              <span className="home-action-arrow">
                <Icon name="arrow" size={20} />
              </span>
              <span className="home-action-title">{action.title}</span>
              <span className="home-action-desc">{action.desc}</span>
              <span className="home-action-foot">
                <span className="home-action-meta">{action.meta}</span>
                <span className="home-action-cta">{action.cta}</span>
              </span>
            </button>
          ))}
        </div>
      </section>

      <section className="home-section">
        <h2 className="home-activity-title">Recent admin activity</h2>
        <ul className="card home-activity">
          {events.slice(0, 4).map((event) => (
            <li key={event.id} className="home-activity-row">
              <span className="home-activity-dot" />
              <b>{getActorName(event.actor_user_id)}</b>{" "}
              <span className="home-activity-what">
                {event.action.toLowerCase()} · {event.target_label}
              </span>
              <span className="home-activity-when">
                {timeAgo(event.created_at)}
              </span>
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}

export default AdminHome;
