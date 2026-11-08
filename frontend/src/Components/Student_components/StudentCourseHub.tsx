import { useEffect, useState } from "react";
import "./StudentCourses.css";
import Icon from "../Shared_components/Icon";
import { PendingTask } from "./StudentCourses";
import { CourseInfo } from "../../courseHelpers";
import {
  getGradeItems,
  getMaterials,
  getRecords,
  getSessions,
  getStudentGrades,
} from "../../learningApi";
import {
  AttendanceRecord,
  AttendanceSession,
  CourseMaterial,
  Enrollment,
  GradeItem,
  StudentGrade,
} from "../../types";
import { hourText, letterGrade, shortDate, timeText } from "../../format";

type StudentCourseHubProps = {
  info: CourseInfo;
  enrollment: Enrollment;
  doctorName: string;
  roomName: string;
  week: number;
  tasks: PendingTask[];
  onBack: () => void;
  onOpenAI: () => void;
};

const statusLabels: Record<string, string> = {
  present: "Present",
  late: "Late",
  absent: "Absent",
};

function StudentCourseHub({
  info,
  enrollment,
  doctorName,
  roomName,
  week,
  tasks,
  onBack,
  onOpenAI,
}: StudentCourseHubProps) {
  const [tab, setTab] = useState("lectures");
  const [materials, setMaterials] = useState<CourseMaterial[]>([]);
  const [records, setRecords] = useState<AttendanceRecord[]>([]);
  const [sessions, setSessions] = useState<AttendanceSession[]>([]);
  const [gradeItems, setGradeItems] = useState<GradeItem[]>([]);
  const [grades, setGrades] = useState<StudentGrade[]>([]);
  const [error, setError] = useState("");

  useEffect(() => {
    async function loadData() {
      try {
        setMaterials(await getMaterials(info.offering.id));
        setRecords(
          (await getRecords()).filter((r) => r.enrollment_id === enrollment.id),
        );
        setSessions(await getSessions());
        setGradeItems(await getGradeItems());
        setGrades(
          (await getStudentGrades()).filter(
            (g) => g.enrollment_id === enrollment.id,
          ),
        );
      } catch (err) {
        setError("Can't load this course. Did you run: npm run server ?");
      }
    }
    loadData();
  }, [info.offering.id, enrollment.id]);

  const attended = records.filter((r) => r.status !== "absent").length;
  let attendance = 0;
  if (records.length > 0) {
    attendance = Math.round((attended / records.length) * 100);
  }

  let earned = 0;
  let possible = 0;
  grades.forEach((grade) => {
    const item = gradeItems.find((g) => g.id === grade.grade_item_id);
    if (item !== undefined) {
      earned = earned + grade.score;
      possible = possible + item.max_score;
    }
  });
  const letter = possible > 0 ? letterGrade((earned / possible) * 100) : "—";

  const weeks: number[] = [];
  materials.forEach((m) => {
    if (!weeks.includes(m.lecture_no)) {
      weeks.push(m.lecture_no);
    }
  });
  weeks.sort((a, b) => a - b);
  const doneWeeks = weeks.filter((w) => w < week).length;

  function sessionDate(sessionId: number) {
    const session = sessions.find((s) => s.id === sessionId);
    return session !== undefined ? shortDate(session.starts_at) : "";
  }

  const tabs = [
    {
      id: "lectures",
      label: "Lectures & Materials",
      icon: "book",
      count: weeks.length,
    },
    { id: "attendance", label: "Attendance Tracker", icon: "users", count: -1 },
    { id: "tasks", label: "Pending Tasks", icon: "file", count: tasks.length },
  ];

  return (
    <div className="scourses">
      <button className="scourses-back" onClick={onBack}>
        ← My Courses
      </button>
      <p className="hub-emoji">{info.offering.emoji}</p>
      <h1 className="hub-title">
        {info.course.course_code}: {info.course.title}
      </h1>
      <p className="hub-meta">
        <span>
          <Icon name="users" size={14} /> {doctorName}
        </span>
        <span>
          <Icon name="clock" size={14} /> {info.offering.days.join(" & ")} ·{" "}
          {hourText(info.offering.start_hour)}
        </span>
        <span>
          <Icon name="home" size={14} /> {roomName}
        </span>
      </p>

      {error !== "" && <p className="msg-error">{error}</p>}

      <div className="hub-stats">
        <div className="hub-stat">
          <p className="hub-stat-label">
            Syllabus Progress
            <span className="hub-stat-right">
              {doneWeeks}/{weeks.length} weeks
            </span>
          </p>
          <p className="hub-stat-value">{info.offering.syllabus_progress}%</p>
          <div className="progress">
            <div
              className="progress-fill"
              style={{ width: info.offering.syllabus_progress + "%" }}
            />
          </div>
        </div>
        <div className="hub-stat">
          <p className="hub-stat-label">Attendance</p>
          <p className="hub-stat-value">{attendance}%</p>
          <p className="hub-stat-label">
            {attended}/{records.length} sessions attended
          </p>
        </div>
        <div className="hub-stat">
          <p className="hub-stat-label">Current grade</p>
          <p className="hub-stat-value">{letter}</p>
          <p className="hub-stat-label">
            {tasks.length} pending task{tasks.length === 1 ? "" : "s"}
          </p>
        </div>
      </div>

      <button className="hub-ai" onClick={onOpenAI}>
        <span className="hub-ai-icon">
          <Icon name="spark" />
        </span>
        <span className="hub-ai-text">
          <b>Course AI Assistant</b>
          <span>
            Ask anything about {info.course.course_code}. Answers cite lecture
            sources, and you can escalate to {doctorName}.
          </span>
        </span>
        <span className="hub-ai-btn">
          Open Assistant <Icon name="arrow" size={14} />
        </span>
      </button>

      <div className="tabs hub-tabs">
        {tabs.map((item) => (
          <button
            key={item.id}
            className={tab === item.id ? "tab active" : "tab"}
            onClick={() => setTab(item.id)}
          >
            <Icon name={item.icon} size={14} /> {item.label}
            {item.count >= 0 && <span className="hub-count">{item.count}</span>}
          </button>
        ))}
      </div>

      {tab === "lectures" && (
        <ul className="hub-list">
          {weeks.map((w) => (
            <li key={w} className="hub-lecture">
              <span className={w < week ? "hub-week done" : "hub-week"}>
                {w < week ? "✓" : "W" + w}
              </span>
              <span className="hub-lecture-text">
                <b>{w === 0 ? "Course documents" : "Week " + w}</b>
                {materials
                  .filter((m) => m.lecture_no === w)
                  .map((m) => (
                    <span key={m.id} className="hub-file">
                      <Icon name="download" size={14} /> {m.title}{" "}
                      <span className="subtle">{m.size}</span>
                    </span>
                  ))}
              </span>
            </li>
          ))}
          {weeks.length === 0 && (
            <li className="empty">
              Materials are released after each lecture.
            </li>
          )}
        </ul>
      )}

      {tab === "attendance" && (
        <div className="hub-att">
          <div className="hub-att-left">
            <p className="hub-stat-label">Sessions attended</p>
            <p className="hub-att-big">
              {attended}
              <span className="subtle">/{records.length}</span>
            </p>
            <div className="progress">
              <div
                className="progress-fill"
                style={{ width: attendance + "%" }}
              />
            </div>
            <p className="hub-att-pct">{attendance}% attendance</p>
            <p
              className={
                attendance >= 80 ? "hub-att-note ok" : "hub-att-note low"
              }
            >
              {attendance >= 80
                ? "Above the 80% minimum requirement"
                : "Below the 80% minimum. Attend upcoming sessions to recover."}
            </p>
            {["present", "late", "absent"].map((status) => (
              <p key={status} className="hub-legend">
                <span className={"hub-legend-box " + status} />
                {statusLabels[status]}
                <b>{records.filter((r) => r.status === status).length}</b>
              </p>
            ))}
          </div>
          <div className="hub-att-right">
            <p className="hub-att-head">Session record</p>
            <p className="hub-stat-label">
              {records.length} sessions this term
            </p>
            <div className="hub-cells">
              {records.map((record, index) => (
                <span
                  key={record.id}
                  className={"hub-cell " + record.status}
                  title={
                    "Session " +
                    (index + 1) +
                    " · " +
                    sessionDate(record.session_id) +
                    ": " +
                    statusLabels[record.status]
                  }
                >
                  {index + 1}
                </span>
              ))}
            </div>
          </div>
        </div>
      )}

      {tab === "tasks" &&
        (tasks.length > 0 ? (
          <ul className="hub-list">
            {tasks.map((task) => (
              <li key={task.key} className="hub-task">
                <span className="hub-task-icon">
                  <Icon name={task.kind === "Quiz" ? "quiz" : "file"} />
                </span>
                <span className="hub-task-text">
                  <span className="hub-stat-label">
                    {task.kind} · {task.points} pts
                  </span>
                  <b>{task.title}</b>
                </span>
                <span className="hub-task-right">
                  <span className="muted">
                    <Icon name="clock" size={14} /> {shortDate(task.due)} ·{" "}
                    {timeText(task.due)}
                  </span>
                  <span className={task.daysLeft <= 1 ? "pill amber" : "pill"}>
                    {task.daysLeft <= 1
                      ? "Due soon"
                      : "In " + task.daysLeft + " days"}
                  </span>
                </span>
              </li>
            ))}
          </ul>
        ) : (
          <p className="empty">No pending tasks for this course.</p>
        ))}
    </div>
  );
}

export default StudentCourseHub;
