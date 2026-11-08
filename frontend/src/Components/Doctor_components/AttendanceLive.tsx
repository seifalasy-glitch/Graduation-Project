import { useEffect, useState } from "react";
import "./AttendanceLive.css";
import Icon from "../Shared_components/Icon";
import {
  getCourses,
  getEnrollments,
  getGroups,
  getMemberships,
  getOfferings,
  getRooms,
  getTeachingAssignments,
  getUsers,
} from "../../academicApi";
import {
  addAttendanceChange,
  addQrChallenge,
  addRecord,
  addSession,
  getSessionRecords,
  updateSession,
} from "../../learningApi";
import {
  AttendanceRecord,
  AttendanceSession,
  Course,
  CourseOffering,
  Enrollment,
  GroupMembership,
  Room,
  StudentGroup,
  TeachingAssignment,
  User,
} from "../../types";

type AttendanceLiveProps = {
  user: User;
  onExit: () => void;
};

const ROTATE = 10;

const networks = [
  {
    id: "campus",
    name: "Campus Wi-Fi",
    note: "Students verify via eduroam. Recommended.",
  },
  {
    id: "router",
    name: "Local Router",
    note: "Offline mode via classroom hotspot. Syncs after session.",
  },
];

function twoDigits(value: number) {
  return value < 10 ? "0" + value : String(value);
}

function clock(seconds: number) {
  return twoDigits(Math.floor(seconds / 60)) + ":" + twoDigits(seconds % 60);
}

function AttendanceLive({ user, onExit }: AttendanceLiveProps) {
  const [stage, setStage] = useState("setup");
  const [groups, setGroups] = useState<StudentGroup[]>([]);
  const [offerings, setOfferings] = useState<CourseOffering[]>([]);
  const [courses, setCourses] = useState<Course[]>([]);
  const [rooms, setRooms] = useState<Room[]>([]);
  const [users, setUsers] = useState<User[]>([]);
  const [enrollments, setEnrollments] = useState<Enrollment[]>([]);
  const [memberships, setMemberships] = useState<GroupMembership[]>([]);
  const [teaching, setTeaching] = useState<TeachingAssignment[]>([]);
  const [groupId, setGroupId] = useState(0);
  const [roomId, setRoomId] = useState(0);
  const [network, setNetwork] = useState("campus");
  const [session, setSession] = useState<AttendanceSession | null>(null);
  const [records, setRecords] = useState<AttendanceRecord[]>([]);
  const [elapsed, setElapsed] = useState(0);
  const [search, setSearch] = useState("");
  const [error, setError] = useState("");

  useEffect(() => {
    async function loadData() {
      try {
        const allTeaching = await getTeachingAssignments();
        const myTeaching = allTeaching.filter(
          (t) => t.doctor_user_id === user.id,
        );
        setTeaching(myTeaching);
        const mine = myTeaching.map((t) => t.group_id);
        const allGroups = await getGroups();
        const myGroups = allGroups.filter((g) => mine.includes(g.id));
        setGroups(myGroups);
        setOfferings(await getOfferings());
        setCourses(await getCourses());
        const roomList = await getRooms();
        setRooms(roomList);
        setUsers(await getUsers());
        setEnrollments(await getEnrollments());
        setMemberships(await getMemberships());
        if (myGroups.length > 0) {
          setGroupId(myGroups[0].id);
        }
        if (roomList.length > 0) {
          setRoomId(roomList[0].id);
        }
      } catch (err) {
        setError("Can't load your sections. Did you run: npm run server ?");
      }
    }
    loadData();
  }, [user.id]);

  useEffect(() => {
    if (stage !== "live" || session === null) {
      return;
    }
    const sessionId = session.id;
    const timer = setInterval(() => {
      setElapsed((value) => value + 1);
    }, 1000);
    const poll = setInterval(async () => {
      try {
        setRecords(await getSessionRecords(sessionId));
      } catch (err) {
        setError("Lost connection to the server.");
      }
    }, 3000);
    return () => {
      clearInterval(timer);
      clearInterval(poll);
    };
  }, [stage, session]);

  function getGroupTitle(id: number) {
    const group = groups.find((g) => g.id === id);
    if (group === undefined) {
      return "";
    }
    const offering = offerings.find((o) => o.id === group.offering_id);
    const course = courses.find(
      (c) => offering !== undefined && c.id === offering.course_id,
    );
    if (course === undefined) {
      return group.label;
    }
    return course.course_code + " · " + course.title;
  }

  const groupMembers = memberships.filter((m) => m.group_id === groupId);
  const total = groupMembers.length;
  const presentIds = records
    .filter((r) => r.status !== "absent")
    .map((r) => r.enrollment_id);
  const present = presentIds.length;
  const manualCount = records.filter((r) => r.method === "manual").length;
  const room = rooms.find((r) => r.id === roomId);
  const roomName = room !== undefined ? room.name : "";

  const notCheckedIn: { enrollmentId: number; student: User }[] = [];
  groupMembers.forEach((m) => {
    const enrollment = enrollments.find((e) => e.id === m.enrollment_id);
    const student = users.find(
      (u) => enrollment !== undefined && u.id === enrollment.student_user_id,
    );
    if (student !== undefined && !presentIds.includes(m.enrollment_id)) {
      notCheckedIn.push({ enrollmentId: m.enrollment_id, student: student });
    }
  });
  const text = search.trim().toLowerCase();
  const results = notCheckedIn.filter((item) =>
    (item.student.name + item.student.university_id)
      .toLowerCase()
      .includes(text),
  );

  async function handleLaunch() {
    setError("");
    try {
      const now = new Date();
      const assignment = teaching.find((t) => t.group_id === groupId);
      const saved = await addSession({
        group_id: groupId,
        room_id: roomId,
        teaching_assignment_id: assignment !== undefined ? assignment.id : 0,
        method: network,
        starts_at: now.toISOString(),
        ends_at: new Date(now.getTime() + 90 * 60000).toISOString(),
        state: "open",
      });
      await addQrChallenge({
        session_id: saved.id,
        token_hash: "mock-" + saved.id + "-0",
        expires_at: new Date(now.getTime() + (ROTATE + 5) * 1000).toISOString(),
      });
      setSession(saved);
      setRecords([]);
      setElapsed(0);
      setStage("live");
    } catch (err) {
      setError("Couldn't start the session, try again.");
    }
  }

  async function handleManual(enrollmentId: number) {
    if (session === null) {
      return;
    }
    try {
      const saved = await addRecord({
        session_id: session.id,
        enrollment_id: enrollmentId,
        status: "present",
        method: "manual",
        marked_at: new Date().toISOString(),
      });
      await addAttendanceChange({
        record_id: saved.id,
        doctor_user_id: user.id,
        old_status: "absent",
        new_status: "present",
        reason_code: "manual_override",
        changed_at: new Date().toISOString(),
      });
      setRecords([...records, saved]);
    } catch (err) {
      setError("Couldn't mark the student, try again.");
    }
  }

  async function handleEnd() {
    if (session === null) {
      return;
    }
    try {
      const latest = await getSessionRecords(session.id);
      const marked = latest.map((r) => r.enrollment_id);
      for (const member of groupMembers) {
        if (!marked.includes(member.enrollment_id)) {
          await addRecord({
            session_id: session.id,
            enrollment_id: member.enrollment_id,
            status: "absent",
            method: "",
            marked_at: "",
          });
        }
      }
      await updateSession(session.id, {
        state: "closed",
        ends_at: new Date().toISOString(),
      });
      setRecords(latest);
      setStage("done");
    } catch (err) {
      setError("Couldn't save the ledger, try again.");
    }
  }

  const seed = Math.floor(elapsed / ROTATE);

  useEffect(() => {
    if (stage !== "live" || session === null || seed === 0) {
      return;
    }
    const expires = new Date(Date.now() + (ROTATE + 5) * 1000);
    addQrChallenge({
      session_id: session.id,
      token_hash: "mock-" + session.id + "-" + seed,
      expires_at: expires.toISOString(),
    }).catch(() => setError("Lost connection to the server."));
  }, [seed, stage, session]);
  const secondsLeft = ROTATE - (elapsed % ROTATE);
  const cells: { r: number; c: number }[] = [];
  for (let r = 0; r < 25; r++) {
    for (let c = 0; c < 25; c++) {
      const inFinder =
        (r < 7 && c < 7) || (r < 7 && c > 17) || (r > 17 && c < 7);
      if (!inFinder && (r * 7 + c * 13 + seed * 5 + r * c) % 3 === 0) {
        cells.push({ r: r, c: c });
      }
    }
  }

  let rate = 0;
  if (total > 0) {
    rate = Math.round((present / total) * 100);
  }

  if (stage === "setup") {
    return (
      <div className="live-backdrop">
        <div className="live-dialog">
          <p className="live-small">{getGroupTitle(groupId)}</p>
          <h2 className="live-title">Start Live QR Attendance</h2>
          <button className="live-close" onClick={onExit}>
            ✕
          </button>

          {error !== "" && <p className="msg-error">{error}</p>}

          <p className="live-legend">Select Section</p>
          <select
            className="input"
            value={groupId}
            onChange={(e) => setGroupId(Number(e.target.value))}
          >
            {groups.map((group) => (
              <option key={group.id} value={group.id}>
                {getGroupTitle(group.id)} — {group.label}
              </option>
            ))}
          </select>

          <p className="live-legend">Select Room</p>
          {rooms.slice(0, 3).map((item) => (
            <button
              key={item.id}
              className={roomId === item.id ? "live-option on" : "live-option"}
              onClick={() => setRoomId(item.id)}
            >
              <span className="live-radio">
                {roomId === item.id && <span className="live-radio-dot" />}
              </span>
              <span className="live-option-text">
                <b>{item.name}</b>
                <span>
                  {item.building} · capacity {item.capacity}
                </span>
              </span>
            </button>
          ))}

          <p className="live-legend">Network Mode</p>
          {networks.map((item) => (
            <button
              key={item.id}
              className={network === item.id ? "live-option on" : "live-option"}
              onClick={() => setNetwork(item.id)}
            >
              <span className="live-radio">
                {network === item.id && <span className="live-radio-dot" />}
              </span>
              <span className="live-option-text">
                <b>{item.name}</b>
                <span>{item.note}</span>
              </span>
            </button>
          ))}

          <div className="live-actions">
            <button className="btn-ghost" onClick={onExit}>
              Cancel
            </button>
            <button
              className="btn-primary"
              disabled={groupId === 0}
              onClick={handleLaunch}
            >
              <Icon name="qr" />
              Launch Session
            </button>
          </div>
        </div>
      </div>
    );
  }

  if (stage === "live") {
    return (
      <div className="live-screen">
        <header className="live-header">
          <span className="live-pill">
            <span className="live-pill-dot" />
            LIVE
          </span>
          <b className="live-course">{getGroupTitle(groupId)}</b>
          <span className="muted">
            · {roomName} ·{" "}
            {network === "campus" ? "Campus Wi-Fi" : "Local Router"}
          </span>
          <span className="live-timer">{clock(elapsed)}</span>
          <button className="btn-ghost live-end" onClick={handleEnd}>
            End Session
          </button>
        </header>

        {error !== "" && <p className="msg-error">{error}</p>}

        <section className="live-projector">
          <p className="live-instruction">
            Open <b>EduSync</b> on your phone and scan to check in
          </p>
          <div className="live-qr-row">
            <svg
              className="live-qr"
              viewBox="-1 -1 27 27"
              shapeRendering="crispEdges"
            >
              {cells.map((cell) => (
                <rect
                  key={cell.r + "-" + cell.c}
                  x={cell.c}
                  y={cell.r}
                  width={1}
                  height={1}
                />
              ))}
              {[
                [0, 0],
                [0, 18],
                [18, 0],
              ].map((pos) => (
                <g key={pos[0] + "-" + pos[1]}>
                  <rect
                    x={pos[1] + 0.5}
                    y={pos[0] + 0.5}
                    width={6}
                    height={6}
                    rx={1.2}
                    fill="none"
                    stroke="#1f2328"
                    strokeWidth={1}
                  />
                  <rect
                    x={pos[1] + 2}
                    y={pos[0] + 2}
                    width={3}
                    height={3}
                    rx={0.6}
                  />
                </g>
              ))}
            </svg>
            <p className="live-rotate">
              Rotating QR · new code in <b>{secondsLeft}s</b>
            </p>
          </div>
          <div className="live-count">
            <p className="live-count-row">
              <span className="live-count-label">Present</span>
              <span className="live-count-value">
                {present}
                <span className="live-count-total"> / {total}</span>
              </span>
            </p>
            <div className="live-progress">
              <div
                className="live-progress-fill"
                style={{ width: rate + "%" }}
              />
            </div>
            <p className="live-count-pct">{rate}% checked in</p>
          </div>
        </section>

        <aside className="live-manual">
          <h2 className="live-manual-title">Manual Override</h2>
          <p className="live-manual-text">
            For students whose scan failed. Overrides are logged in the ledger.
          </p>
          <label className="live-search">
            <Icon name="search" />
            <input
              className="live-search-input"
              value={search}
              placeholder="Search Student by ID/Name"
              onChange={(e) => setSearch(e.target.value)}
            />
          </label>
          <p className="live-manual-label">Not yet checked in</p>
          <ul className="live-manual-list">
            {results.map((item) => (
              <li key={item.enrollmentId} className="live-manual-row">
                <span className="live-avatar">
                  {item.student.name
                    .split(" ")
                    .map((part) => part[0])
                    .join("")}
                </span>
                <span className="live-manual-name">
                  <b>{item.student.name}</b>
                  <span>{item.student.university_id}</span>
                </span>
                <button
                  className="live-mark"
                  onClick={() => handleManual(item.enrollmentId)}
                >
                  Mark Present (Manual)
                </button>
              </li>
            ))}
            {results.length === 0 && (
              <li className="live-manual-empty">No matching students</li>
            )}
          </ul>
        </aside>
      </div>
    );
  }

  const late = records.filter(
    (r) =>
      r.status !== "absent" &&
      session !== null &&
      new Date(r.marked_at).getTime() - new Date(session.starts_at).getTime() >
        10 * 60000,
  ).length;

  const summary = [
    { label: "Present", value: present + "/" + total },
    { label: "Attendance rate", value: rate + "%" },
    { label: "Manual overrides", value: String(manualCount) },
    { label: "Session length", value: clock(elapsed > 0 ? elapsed : 1) },
  ];

  return (
    <div className="live-screen live-done">
      <div className="live-done-box">
        <span className="live-done-icon">
          <Icon name="check" size={24} />
        </span>
        <h1 className="live-done-title">Attendance Ledger Saved</h1>
        <p className="live-done-sub">
          {getGroupTitle(groupId)} · {roomName} ·{" "}
          {new Date().toLocaleDateString("en-US", {
            weekday: "long",
            month: "long",
            day: "numeric",
          })}
        </p>
        <div className="live-done-grid">
          {summary.map((item) => (
            <div key={item.label} className="live-done-cell">
              <p className="live-small">{item.label}</p>
              <p className="live-done-value">{item.value}</p>
            </div>
          ))}
        </div>
        <ul className="live-done-list">
          <li>
            <span>Absent</span>
            <b>{total - present} students · notified automatically</b>
          </li>
          <li>
            <span>Late check-ins (after 10 min)</span>
            <b>{late}</b>
          </li>
          <li>
            <span>QR tokens issued</span>
            <b>
              {seed + 1} · rotated every {ROTATE}s
            </b>
          </li>
          <li>
            <span>Ledger ID</span>
            <b>
              ATT-{session !== null ? session.id : 0}-
              {new Date().toISOString().slice(0, 10)}
            </b>
          </li>
        </ul>
        <div className="live-done-actions">
          <button className="btn-primary live-return" onClick={onExit}>
            Return to Dashboard <Icon name="arrow" />
          </button>
          <button className="btn-ghost">
            <Icon name="download" />
            Export CSV
          </button>
        </div>
      </div>
    </div>
  );
}

export default AttendanceLive;
