import { useEffect, useState } from "react";
import "./DoctorHome.css";
import Icon from "../Shared_components/Icon";
import {
  getCourses,
  getEnrollments,
  getGroups,
  getOfferings,
  getRooms,
  getTeachingAssignments,
  getTerms,
} from "../../academicApi";
import {
  getAlerts,
  getAssignments,
  getGradeItems,
  getRecords,
  getSessions,
  getStudentGrades,
  getSubmissions,
} from "../../learningApi";
import {
  getCourseInfo,
  getDoctorOfferingIds,
  CourseInfo,
} from "../../courseHelpers";
import {
  AcademicTerm,
  Alert,
  AssignmentInfo,
  AttendanceRecord,
  AttendanceSession,
  Enrollment,
  GradeItem,
  Room,
  StudentGroup,
  StudentGrade,
  Submission,
  User,
} from "../../types";
import {
  hourText,
  lastName,
  termName,
  timeAgo,
  weekOfTerm,
} from "../../format";

type DoctorHomeProps = {
  user: User;
  onNavigate: (page: string) => void;
};

const dayNames = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];

function DoctorHome({ user, onNavigate }: DoctorHomeProps) {
  const [myCourses, setMyCourses] = useState<CourseInfo[]>([]);
  const [groups, setGroups] = useState<StudentGroup[]>([]);
  const [enrollments, setEnrollments] = useState<Enrollment[]>([]);
  const [rooms, setRooms] = useState<Room[]>([]);
  const [terms, setTerms] = useState<AcademicTerm[]>([]);
  const [assignments, setAssignments] = useState<AssignmentInfo[]>([]);
  const [submissions, setSubmissions] = useState<Submission[]>([]);
  const [gradeItems, setGradeItems] = useState<GradeItem[]>([]);
  const [grades, setGrades] = useState<StudentGrade[]>([]);
  const [sessions, setSessions] = useState<AttendanceSession[]>([]);
  const [teachingIds, setTeachingIds] = useState<number[]>([]);
  const [records, setRecords] = useState<AttendanceRecord[]>([]);
  const [alerts, setAlerts] = useState<Alert[]>([]);
  const [error, setError] = useState("");

  useEffect(() => {
    async function loadData() {
      try {
        const teaching = await getTeachingAssignments();
        setTeachingIds(
          teaching.filter((t) => t.doctor_user_id === user.id).map((t) => t.id),
        );
        const groupList = await getGroups();
        const ids = getDoctorOfferingIds(user.id, teaching, groupList);
        setMyCourses(
          getCourseInfo(ids, await getOfferings(), await getCourses()),
        );
        setGroups(groupList);
        setEnrollments(await getEnrollments());
        setRooms(await getRooms());
        setTerms(await getTerms());
        setAssignments(await getAssignments());
        setSubmissions(await getSubmissions());
        setGradeItems(await getGradeItems());
        setGrades(await getStudentGrades());
        setSessions(await getSessions());
        setRecords(await getRecords());
        setAlerts(await getAlerts(user.id));
      } catch (err) {
        setError("Can't load your dashboard. Did you run: npm run server ?");
      }
    }
    loadData();
  }, [user.id]);

  const myOfferingIds = myCourses.map((item) => item.offering.id);

  function countStudents(offeringId: number) {
    return enrollments.filter((e) => e.offering_id === offeringId).length;
  }

  function countPending(offeringId: number) {
    let count = 0;
    assignments.forEach((assignment) => {
      if (assignment.offering_id !== offeringId) {
        return;
      }
      const item = gradeItems.find((g) => g.id === assignment.id);
      submissions.forEach((submission) => {
        if (submission.grade_item_id !== assignment.id) {
          return;
        }
        const graded =
          item !== undefined &&
          grades.some(
            (g) =>
              g.grade_item_id === item.id &&
              g.enrollment_id === submission.enrollment_id,
          );
        if (!graded) {
          count = count + 1;
        }
      });
    });
    return count;
  }

  const studentIds: number[] = [];
  enrollments.forEach((e) => {
    if (
      myOfferingIds.includes(e.offering_id) &&
      !studentIds.includes(e.student_user_id)
    ) {
      studentIds.push(e.student_user_id);
    }
  });

  let pendingTotal = 0;
  myOfferingIds.forEach((id) => {
    pendingTotal = pendingTotal + countPending(id);
  });

  const myGroupIds = groups
    .filter((g) => myOfferingIds.includes(g.offering_id))
    .map((g) => g.id);
  const twoWeeksAgo = Date.now() - 14 * 24 * 60 * 60 * 1000;
  const recentSessionIds = sessions
    .filter(
      (s) =>
        myGroupIds.includes(s.group_id) &&
        new Date(s.starts_at).getTime() > twoWeeksAgo,
    )
    .map((s) => s.id);
  const recentRecords = records.filter((r) =>
    recentSessionIds.includes(r.session_id),
  );
  const presentCount = recentRecords.filter(
    (r) => r.status !== "absent",
  ).length;
  let attendance = 0;
  if (recentRecords.length > 0) {
    attendance = Math.round((presentCount / recentRecords.length) * 100);
  }

  const weekStart = Date.now() - new Date().getDay() * 24 * 60 * 60 * 1000;
  const sessionsThisWeek = sessions.filter(
    (s) =>
      teachingIds.includes(s.teaching_assignment_id) &&
      new Date(s.starts_at).getTime() > weekStart,
  ).length;

  const stats = [
    {
      label: "Total enrolled students",
      value: String(studentIds.length),
      note: "across " + myCourses.length + " courses",
    },
    {
      label: "Pending submissions to grade",
      value: String(pendingTotal),
      note: "from " + submissions.length + " submissions",
    },
    { label: "Avg. attendance", value: attendance + "%", note: "last 14 days" },
    {
      label: "Sessions this week",
      value: String(sessionsThisWeek),
      note: "QR attendance sessions",
    },
  ];

  let next: { info: CourseInfo; day: string; hour: number } | null = null;
  let bestDistance = 99999;
  const now = new Date();
  const nowValue = now.getDay() * 24 + now.getHours() + now.getMinutes() / 60;
  myCourses.forEach((info) => {
    info.offering.days.forEach((day) => {
      let value = dayNames.indexOf(day) * 24 + info.offering.start_hour;
      if (value < nowValue) {
        value = value + 7 * 24;
      }
      if (value - nowValue < bestDistance) {
        bestDistance = value - nowValue;
        next = { info: info, day: day, hour: info.offering.start_hour };
      }
    });
  });

  function getRoomName(roomId: number) {
    const room = rooms.find((item) => item.id === roomId);
    if (room === undefined) {
      return "";
    }
    return room.name;
  }

  const activeTerm = terms.find((term) => term.status === "Active");
  let dateLine = now.toLocaleDateString("en-US", {
    weekday: "long",
    month: "long",
    day: "numeric",
  });
  let termLabel = "";
  if (activeTerm !== undefined) {
    const week = weekOfTerm(activeTerm.start_date, activeTerm.end_date);
    termLabel = termName(activeTerm.term_type, activeTerm.academic_year);
    dateLine = dateLine + " · Week " + week.week + " of " + termLabel;
  }

  const nextSession = next as {
    info: CourseInfo;
    day: string;
    hour: number;
  } | null;

  return (
    <div className="dhome">
      <main className="dhome-main">
        <p className="dhome-date">{dateLine}</p>
        <h1 className="dhome-title">
          Welcome, Professor {lastName(user.name)}
        </h1>
        {nextSession !== null && (
          <p className="dhome-intro">
            Your next lecture is {nextSession.info.course.course_code} on{" "}
            {nextSession.day} at {hourText(nextSession.hour)} and {pendingTotal}{" "}
            submissions are waiting in your grading queue.
          </p>
        )}

        {error !== "" && <p className="msg-error">{error}</p>}

        <div className="dhome-stats">
          {stats.map((stat) => (
            <div key={stat.label} className="dhome-stat">
              <p className="dhome-stat-label">{stat.label}</p>
              <p className="dhome-stat-value">{stat.value}</p>
              <p className="dhome-stat-note">{stat.note}</p>
            </div>
          ))}
        </div>

        {nextSession !== null && (
          <section className="dhome-next">
            <p className="dhome-next-small">Next session</p>
            <p className="dhome-next-title">
              {nextSession.info.course.course_code} ·{" "}
              {nextSession.info.course.title}
            </p>
            <p className="dhome-next-sub">
              {getRoomName(nextSession.info.offering.room_id)} ·{" "}
              {nextSession.day} {hourText(nextSession.hour)} ·{" "}
              {countStudents(nextSession.info.offering.id)} students expected
            </p>
            <button
              className="dhome-next-btn"
              onClick={() => onNavigate("attendance")}
            >
              <Icon name="qr" size={20} />
              <span className="dhome-next-btn-text">
                Start Live QR Attendance
              </span>
            </button>
          </section>
        )}

        <div className="dhome-courses-head">
          <h2 className="dhome-h2">My Teaching Courses</h2>
          <span className="dhome-courses-count">
            {termLabel} · {myCourses.length} courses
          </span>
        </div>
        <div className="dhome-courses">
          {myCourses.map((info) => (
            <article key={info.offering.id} className="dhome-course">
              <span className="dhome-emoji">{info.offering.emoji}</span>
              <span className="dhome-code">{info.course.course_code}</span>
              <h3 className="dhome-course-title">{info.course.title}</h3>
              <p className="dhome-course-sub">
                {info.offering.days.join(" · ")}{" "}
                {hourText(info.offering.start_hour)} ·{" "}
                {getRoomName(info.offering.room_id)}
              </p>
              <p className="dhome-course-numbers">
                <span>
                  <b>{countStudents(info.offering.id)}</b> students
                </span>
                <span>
                  <b>{countPending(info.offering.id)}</b> to grade
                </span>
              </p>
              <p className="dhome-progress-label">
                Syllabus progress
                <span className="dhome-progress-value">
                  {info.offering.syllabus_progress}%
                </span>
              </p>
              <div className="dhome-progress">
                <div
                  className="dhome-progress-fill"
                  style={{ width: info.offering.syllabus_progress + "%" }}
                />
              </div>
              <div className="dhome-course-actions">
                <button
                  className="dhome-course-btn"
                  onClick={() => onNavigate("grades")}
                >
                  <Icon name="grade" size={14} /> Gradebook
                </button>
                <button
                  className="dhome-course-btn"
                  onClick={() => onNavigate("quizzes")}
                >
                  <Icon name="quiz" size={14} /> Manage Quizzes
                </button>
              </div>
            </article>
          ))}
        </div>
      </main>

      <aside className="dhome-alerts">
        <div className="dhome-alerts-head">
          <h2 className="dhome-alerts-title">Faculty Smart Alerts</h2>
          <span className="dhome-alerts-new">
            {alerts.filter((alert) => alert.unread).length} new
          </span>
        </div>
        <ul className="dhome-alert-list">
          {alerts.map((alert) => (
            <li key={alert.id} className="dhome-alert">
              <span className="dhome-alert-icon">
                <Icon name={alert.icon} />
              </span>
              <div className="dhome-alert-body">
                <p className="dhome-alert-title">
                  {alert.title}
                  {alert.unread && <span className="dhome-alert-dot" />}
                </p>
                <p className="dhome-alert-text">{alert.body}</p>
                <p className="dhome-alert-foot">
                  <button
                    className="dhome-alert-action"
                    onClick={() => onNavigate(alert.page)}
                  >
                    {alert.action} <Icon name="arrow" size={14} />
                  </button>
                  <span className="dhome-alert-time">
                    {timeAgo(alert.created_at)}
                  </span>
                </p>
              </div>
            </li>
          ))}
        </ul>
        <p className="dhome-alerts-note">
          Alerts are generated from attendance, quiz performance and
          AI-assistant activity across your courses.
        </p>
      </aside>
    </div>
  );
}

export default DoctorHome;
