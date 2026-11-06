import { useEffect, useState } from "react";
import "./StudentHome.css";
import Icon from "../Shared_components/Icon";
import ScanSheet from "./ScanSheet";
import {
  getCourses,
  getGroups,
  getMemberships,
  getOfferings,
  getStudentEnrollments,
  getTeachingAssignments,
  getTerms,
  getUsers,
} from "../../academicApi";
import {
  getAssignments,
  getGradeItems,
  getQuizzes,
  getRecords,
  getStudentGrades,
  getSubmissions,
} from "../../learningApi";
import {
  getCourseInfo,
  getLectureDoctor,
  CourseInfo,
} from "../../courseHelpers";
import {
  AcademicTerm,
  AssignmentInfo,
  AttendanceRecord,
  Enrollment,
  GradeItem,
  GroupMembership,
  Quiz,
  StudentGrade,
  StudentGroup,
  Submission,
  TeachingAssignment,
  User,
} from "../../types";
import {
  firstName,
  greetingText,
  termName,
  timeText,
  weekOfTerm,
} from "../../format";

type StudentHomeProps = {
  user: User;
  onNavigate: (page: string) => void;
};

type TimelineItem = {
  key: string;
  kind: string;
  title: string;
  course: string;
  time: string;
  tag: string;
  tone: string;
  days: number;
};

const MIN_ATTENDANCE = 80;
const dayMs = 24 * 60 * 60 * 1000;

function StudentHome({ user, onNavigate }: StudentHomeProps) {
  const [enrollments, setEnrollments] = useState<Enrollment[]>([]);
  const [courses, setCourses] = useState<CourseInfo[]>([]);
  const [users, setUsers] = useState<User[]>([]);
  const [groups, setGroups] = useState<StudentGroup[]>([]);
  const [teaching, setTeaching] = useState<TeachingAssignment[]>([]);
  const [memberships, setMemberships] = useState<GroupMembership[]>([]);
  const [terms, setTerms] = useState<AcademicTerm[]>([]);
  const [records, setRecords] = useState<AttendanceRecord[]>([]);
  const [assignments, setAssignments] = useState<AssignmentInfo[]>([]);
  const [submissions, setSubmissions] = useState<Submission[]>([]);
  const [quizzes, setQuizzes] = useState<Quiz[]>([]);
  const [gradeItems, setGradeItems] = useState<GradeItem[]>([]);
  const [grades, setGrades] = useState<StudentGrade[]>([]);
  const [filter, setFilter] = useState("All");
  const [scanning, setScanning] = useState(false);
  const [checkedIn, setCheckedIn] = useState("");
  const [error, setError] = useState("");

  useEffect(() => {
    async function loadData() {
      try {
        const mine = await getStudentEnrollments(user.id);
        setEnrollments(mine);
        setCourses(
          getCourseInfo(
            mine.map((e) => e.offering_id),
            await getOfferings(),
            await getCourses(),
          ),
        );
        setUsers(await getUsers());
        setGroups(await getGroups());
        setTeaching(await getTeachingAssignments());
        setMemberships(await getMemberships());
        setTerms(await getTerms());
        setRecords(await getRecords());
        setAssignments(await getAssignments());
        setSubmissions(await getSubmissions());
        setQuizzes(await getQuizzes());
        setGradeItems(await getGradeItems());
        setGrades(await getStudentGrades());
      } catch (err) {
        setError("Can't load your dashboard. Did you run: npm run server ?");
      }
    }
    loadData();
  }, [user.id]);

  const myEnrollmentIds = enrollments.map((e) => e.id);
  const myOfferingIds = enrollments.map((e) => e.offering_id);
  const myRecords = records.filter((r) =>
    myEnrollmentIds.includes(r.enrollment_id),
  );
  const myPresent = myRecords.filter((r) => r.status !== "absent").length;
  let attendance = 0;
  if (myRecords.length > 0) {
    attendance = Math.round((myPresent / myRecords.length) * 100);
  }

  const myAssignments = assignments.filter((a) =>
    myOfferingIds.includes(a.offering_id),
  );
  const mySubmissions = submissions.filter((s) =>
    myEnrollmentIds.includes(s.enrollment_id),
  );
  const dueSoFar = myAssignments.filter(
    (a) => new Date(a.due_at).getTime() < Date.now(),
  ).length;
  const dueThisWeek = myAssignments.filter((a) => {
    const left = new Date(a.due_at).getTime() - Date.now();
    return left > 0 && left < 7 * dayMs;
  }).length;

  const quizItemIds = gradeItems
    .filter((g) => g.item_type === "quiz")
    .map((g) => g.id);
  const myQuizGrades = grades.filter(
    (g) =>
      myEnrollmentIds.includes(g.enrollment_id) &&
      quizItemIds.includes(g.grade_item_id),
  );
  let quizTotal = 0;
  myQuizGrades.forEach((grade) => {
    const item = gradeItems.find((g) => g.id === grade.grade_item_id);
    if (item !== undefined) {
      quizTotal = quizTotal + (grade.score / item.max_score) * 100;
    }
  });
  let quizAverage = 0;
  if (myQuizGrades.length > 0) {
    quizAverage = Math.round(quizTotal / myQuizGrades.length);
  }

  let assignmentPct = 0;
  if (dueSoFar > 0) {
    assignmentPct = Math.round((mySubmissions.length / dueSoFar) * 100);
  }

  const performance = [
    {
      label: "Overall attendance",
      value: attendance + "%",
      pct: attendance,
      note: myPresent + " of " + myRecords.length + " sessions this term",
      icon: "users",
    },
    {
      label: "Assignments submitted",
      value: mySubmissions.length + "/" + dueSoFar,
      pct: assignmentPct > 100 ? 100 : assignmentPct,
      note: dueThisWeek + " due this week",
      icon: "file",
    },
    {
      label: "Avg. quiz score",
      value: quizAverage + "%",
      pct: quizAverage,
      note: "across " + myQuizGrades.length + " quizzes",
      icon: "quiz",
    },
  ];

  function getCourseLabel(offeringId: number) {
    const info = courses.find((c) => c.offering.id === offeringId);
    if (info === undefined) {
      return "";
    }
    return info.course.course_code + ": " + info.course.title;
  }

  function courseAttendance(enrollmentId: number) {
    const list = records.filter((r) => r.enrollment_id === enrollmentId);
    if (list.length === 0) {
      return 100;
    }
    const present = list.filter((r) => r.status !== "absent").length;
    return Math.round((present / list.length) * 100);
  }

  function dayLabel(date: string) {
    return new Date(date).toLocaleDateString("en-US", { weekday: "short" });
  }

  const timeline: TimelineItem[] = [];
  myAssignments.forEach((assignment) => {
    const left = (new Date(assignment.due_at).getTime() - Date.now()) / dayMs;
    const enrollment = enrollments.find(
      (e) => e.offering_id === assignment.offering_id,
    );
    const submission = mySubmissions.find(
      (s) =>
        s.grade_item_id === assignment.id &&
        enrollment !== undefined &&
        s.enrollment_id === enrollment.id,
    );
    if (submission !== undefined && left > -7) {
      timeline.push({
        key: "a" + assignment.id,
        kind: "Assignment",
        title: assignment.title,
        course: getCourseLabel(assignment.offering_id),
        time: "Submitted " + dayLabel(submission.submitted_at),
        tag: "Submitted",
        tone: "done",
        days: 99,
      });
    } else if (submission === undefined && left > 0) {
      const days = Math.ceil(left);
      timeline.push({
        key: "a" + assignment.id,
        kind: "Assignment",
        title: assignment.title,
        course: getCourseLabel(assignment.offering_id),
        time: dayLabel(assignment.due_at) + " · " + timeText(assignment.due_at),
        tag: days <= 1 ? "Due tomorrow" : "Due in " + days + " days",
        tone: days <= 1 ? "urgent" : days <= 7 ? "soon" : "later",
        days: days,
      });
    }
  });
  quizzes.forEach((quiz) => {
    if (!myOfferingIds.includes(quiz.offering_id)) {
      return;
    }
    const opens = (new Date(quiz.opens_at).getTime() - Date.now()) / dayMs;
    const closes = (new Date(quiz.closes_at).getTime() - Date.now()) / dayMs;
    if (opens > 0) {
      const days = Math.ceil(opens);
      timeline.push({
        key: "q" + quiz.id,
        kind: "Quiz",
        title: quiz.title + " opens",
        course: getCourseLabel(quiz.offering_id),
        time: dayLabel(quiz.opens_at) + " · " + timeText(quiz.opens_at),
        tag: "Opens in " + days + " days",
        tone: days <= 7 ? "soon" : "later",
        days: days,
      });
    } else if (closes > 0) {
      const days = Math.ceil(closes);
      timeline.push({
        key: "q" + quiz.id,
        kind: "Quiz",
        title: quiz.title + " closes",
        course: getCourseLabel(quiz.offering_id),
        time: dayLabel(quiz.closes_at) + " · " + timeText(quiz.closes_at),
        tag: "Closes in " + days + (days === 1 ? " day" : " days"),
        tone: days <= 1 ? "urgent" : "soon",
        days: days,
      });
    }
  });
  timeline.sort((a, b) => a.days - b.days);

  const shown = timeline.filter(
    (item) => filter === "All" || item.kind === filter,
  );
  const groupsOfTimeline = [
    { name: "Tomorrow", items: shown.filter((item) => item.days <= 1) },
    {
      name: "This week",
      items: shown.filter((item) => item.days > 1 && item.days <= 7),
    },
    { name: "Later", items: shown.filter((item) => item.days > 7) },
  ];
  const openCount = timeline.filter((item) => item.tone !== "done").length;

  const activeTerm = terms.find((term) => term.status === "Active");
  let dateLine = new Date().toLocaleDateString("en-US", {
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

  const tomorrowCount = timeline.filter(
    (item) => item.days <= 1 && item.tone !== "done",
  ).length;

  return (
    <div className="shome">
      {scanning && (
        <ScanSheet
          enrollments={enrollments}
          memberships={memberships}
          courses={courses}
          onClose={() => setScanning(false)}
          onSuccess={(course) => {
            setCheckedIn(course);
            setScanning(false);
          }}
        />
      )}

      <div className="shome-main">
        <p className="shome-date">{dateLine}</p>
        <p className="shome-title">
          {greetingText()}, {firstName(user.name)}
        </p>
        <p className="shome-intro">
          You have {tomorrowCount} deadlines in the next day and {openCount}{" "}
          open items this term.
        </p>

        {error !== "" && <p className="msg-error">{error}</p>}

        <div className="shome-perf">
          {performance.map((item) => (
            <div key={item.label} className="shome-perf-item">
              <p className="shome-perf-label">
                <Icon name={item.icon} size={14} /> {item.label}
              </p>
              <p className="shome-perf-value">{item.value}</p>
              <div className="shome-bar">
                <div
                  className="shome-bar-fill"
                  style={{ width: item.pct + "%" }}
                />
              </div>
              <p className="shome-perf-note">{item.note}</p>
            </div>
          ))}
        </div>

        <section className="shome-scan">
          <p className="shome-scan-title">
            In class right now?
            <span className="shome-scan-chip">
              Mobile app · Camera required
            </span>
          </p>
          <p className="shome-scan-text">
            {checkedIn !== "" ? (
              <>
                You're checked in to <b>{checkedIn}</b>.
              </>
            ) : (
              "Scan the QR code your instructor displays to mark yourself present."
            )}
          </p>
          <button className="shome-scan-btn" onClick={() => setScanning(true)}>
            <Icon name="qr" size={20} />
            <span className="shome-scan-btn-text">Scan Attendance QR</span>
          </button>
        </section>

        <section className="shome-courses">
          <p className="shome-h2">Enrolled courses</p>
          <p className="shome-h2-sub">
            {termLabel} · {courses.length} courses
          </p>
          <div className="shome-course-grid">
            {enrollments.map((enrollment) => {
              const info = courses.find(
                (c) => c.offering.id === enrollment.offering_id,
              );
              if (info === undefined) {
                return null;
              }
              const doctor = getLectureDoctor(
                info.offering.id,
                groups,
                teaching,
                users,
              );
              const percent = courseAttendance(enrollment.id);
              return (
                <article key={enrollment.id} className="shome-course">
                  <span className="shome-emoji">{info.offering.emoji}</span>
                  <p className="shome-course-title">
                    {info.course.course_code}: {info.course.title}
                  </p>
                  <p className="shome-course-prof">
                    {doctor !== undefined ? doctor.name : "No doctor yet"}
                  </p>
                  <p className="shome-att-label">
                    Attendance
                    <span className="shome-att-value">{percent}%</span>
                  </p>
                  <div className="shome-bar thick">
                    <div
                      className="shome-bar-fill"
                      style={{ width: percent + "%" }}
                    />
                  </div>
                  {percent < MIN_ATTENDANCE && (
                    <p className="shome-low">
                      Below the {MIN_ATTENDANCE}% requirement
                    </p>
                  )}
                  <button
                    className="shome-view"
                    onClick={() => onNavigate("courses")}
                  >
                    View Course <Icon name="arrow" size={14} />
                  </button>
                </article>
              );
            })}
          </div>
        </section>
      </div>

      <aside className="shome-rail">
        <p className="shome-rail-head">
          Upcoming
          <span className="shome-rail-open">{openCount} open</span>
        </p>
        <div className="shome-filter">
          {["All", "Assignment", "Quiz"].map((name) => (
            <button
              key={name}
              className={
                filter === name ? "shome-filter-btn active" : "shome-filter-btn"
              }
              onClick={() => setFilter(name)}
            >
              {name === "All" ? "All" : name + "s"}
            </button>
          ))}
        </div>
        {groupsOfTimeline.map((group) =>
          group.items.length === 0 ? null : (
            <div key={group.name} className="shome-group">
              <p className="shome-group-name">{group.name}</p>
              <ol className="shome-line">
                {group.items.map((item) => (
                  <li key={item.key} className="shome-line-item">
                    <span className={"shome-line-dot " + item.tone} />
                    <p
                      className={
                        item.tone === "done"
                          ? "shome-item-title done"
                          : "shome-item-title"
                      }
                    >
                      <Icon
                        name={item.kind === "Quiz" ? "quiz" : "file"}
                        size={14}
                      />{" "}
                      {item.title}
                    </p>
                    <p className="shome-item-course">{item.course}</p>
                    <p className="shome-item-foot">
                      <span className={"shome-tag " + item.tone}>
                        {item.tag}
                      </span>
                      <span className="shome-item-time">{item.time}</span>
                    </p>
                  </li>
                ))}
              </ol>
            </div>
          ),
        )}
      </aside>
    </div>
  );
}

export default StudentHome;
