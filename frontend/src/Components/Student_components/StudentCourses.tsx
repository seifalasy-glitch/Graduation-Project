import { useEffect, useState } from "react";
import "./StudentCourses.css";
import Icon from "../Shared_components/Icon";
import StudentCourseHub from "./StudentCourseHub";
import AiChat from "./AiChat";
import {
  getCourses,
  getGroups,
  getOfferings,
  getRooms,
  getStudentEnrollments,
  getTeachingAssignments,
  getTerms,
  getUsers,
} from "../../academicApi";
import { getAssignments, getQuizzes, getSubmissions } from "../../learningApi";
import {
  getCourseInfo,
  getLectureDoctor,
  CourseInfo,
} from "../../courseHelpers";
import {
  AcademicTerm,
  AssignmentInfo,
  Enrollment,
  Quiz,
  Room,
  StudentGroup,
  Submission,
  TeachingAssignment,
  User,
} from "../../types";
import { termName, weekOfTerm } from "../../format";

type StudentCoursesProps = {
  user: User;
};

export type PendingTask = {
  key: string;
  kind: string;
  title: string;
  due: string;
  daysLeft: number;
  points: number;
};

function StudentCourses({ user }: StudentCoursesProps) {
  const [enrollments, setEnrollments] = useState<Enrollment[]>([]);
  const [courses, setCourses] = useState<CourseInfo[]>([]);
  const [users, setUsers] = useState<User[]>([]);
  const [groups, setGroups] = useState<StudentGroup[]>([]);
  const [teaching, setTeaching] = useState<TeachingAssignment[]>([]);
  const [rooms, setRooms] = useState<Room[]>([]);
  const [terms, setTerms] = useState<AcademicTerm[]>([]);
  const [assignments, setAssignments] = useState<AssignmentInfo[]>([]);
  const [submissions, setSubmissions] = useState<Submission[]>([]);
  const [quizzes, setQuizzes] = useState<Quiz[]>([]);
  const [openId, setOpenId] = useState(0);
  const [chat, setChat] = useState(false);
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
        setRooms(await getRooms());
        setTerms(await getTerms());
        setAssignments(await getAssignments());
        setSubmissions(await getSubmissions());
        setQuizzes(await getQuizzes());
      } catch (err) {
        setError("Can't load your courses. Did you run: npm run server ?");
      }
    }
    loadData();
  }, [user.id]);

  function getTasks(offeringId: number) {
    const enrollment = enrollments.find((e) => e.offering_id === offeringId);
    const tasks: PendingTask[] = [];
    assignments.forEach((a) => {
      if (a.offering_id !== offeringId) {
        return;
      }
      const done = submissions.some(
        (s) =>
          s.grade_item_id === a.id &&
          enrollment !== undefined &&
          s.enrollment_id === enrollment.id,
      );
      const days = Math.ceil(
        (new Date(a.due_at).getTime() - Date.now()) / 86400000,
      );
      if (!done && days > -7) {
        tasks.push({
          key: "a" + a.id,
          kind: "Assignment",
          title: a.title,
          due: a.due_at,
          daysLeft: days,
          points: a.max_score,
        });
      }
    });
    quizzes.forEach((q) => {
      if (q.offering_id !== offeringId || q.status === "Closed") {
        return;
      }
      const days = Math.ceil(
        (new Date(q.closes_at).getTime() - Date.now()) / 86400000,
      );
      if (days > 0) {
        tasks.push({
          key: "q" + q.id,
          kind: "Quiz",
          title: q.title,
          due: q.closes_at,
          daysLeft: days,
          points: q.questions * 2,
        });
      }
    });
    tasks.sort((x, y) => x.daysLeft - y.daysLeft);
    return tasks;
  }

  function relative(days: number) {
    if (days < 0) {
      return -days + " days overdue";
    }
    if (days <= 1) {
      return "Due tomorrow";
    }
    return "In " + days + " days";
  }

  const activeTerm = terms.find((term) => term.status === "Active");
  let termLine = "";
  let week = 1;
  if (activeTerm !== undefined) {
    const info = weekOfTerm(activeTerm.start_date, activeTerm.end_date);
    week = info.week;
    termLine =
      termName(activeTerm.term_type, activeTerm.academic_year) +
      " · Week " +
      info.week +
      " of " +
      info.total;
  }

  const opened = courses.find((info) => info.offering.id === openId);
  const openedEnrollment = enrollments.find((e) => e.offering_id === openId);

  if (opened !== undefined && openedEnrollment !== undefined) {
    const doctor = getLectureDoctor(
      opened.offering.id,
      groups,
      teaching,
      users,
    );
    const doctorName = doctor !== undefined ? doctor.name : "your doctor";
    if (chat) {
      return (
        <AiChat
          user={user}
          info={opened}
          enrollment={openedEnrollment}
          doctorId={doctor !== undefined ? doctor.id : 0}
          doctorName={doctorName}
          onReturn={() => setChat(false)}
        />
      );
    }
    const room = rooms.find((r) => r.id === opened.offering.room_id);
    return (
      <StudentCourseHub
        info={opened}
        enrollment={openedEnrollment}
        doctorName={doctorName}
        roomName={room !== undefined ? room.name : ""}
        week={week}
        tasks={getTasks(opened.offering.id)}
        onBack={() => setOpenId(0)}
        onOpenAI={() => setChat(true)}
      />
    );
  }

  let pendingTotal = 0;
  courses.forEach((info) => {
    pendingTotal = pendingTotal + getTasks(info.offering.id).length;
  });

  return (
    <div className="scourses">
      <p className="scourses-term">{termLine}</p>
      <h1 className="scourses-title">My Courses</h1>
      <p className="scourses-sub">
        {courses.length} enrolled courses · {pendingTotal} pending tasks
      </p>

      {error !== "" && <p className="msg-error">{error}</p>}

      <div className="scourses-grid">
        {courses.map((info) => {
          const doctor = getLectureDoctor(
            info.offering.id,
            groups,
            teaching,
            users,
          );
          const tasks = getTasks(info.offering.id);
          return (
            <article key={info.offering.id} className="scourses-card">
              <span className="scourses-emoji">{info.offering.emoji}</span>
              <span className="scourses-code">{info.course.course_code}</span>
              <h2 className="scourses-name">
                {info.course.course_code}: {info.course.title}
              </h2>
              <p className="scourses-prof">
                <Icon name="users" size={14} />{" "}
                {doctor !== undefined ? doctor.name : "No doctor yet"}
              </p>
              <p className="scourses-progress-label">
                Overall Progress
                <span className="scourses-progress-value">
                  {info.offering.syllabus_progress}%
                </span>
              </p>
              <div className="progress">
                <div
                  className="progress-fill"
                  style={{ width: info.offering.syllabus_progress + "%" }}
                />
              </div>
              {tasks.length > 0 && (
                <p className="scourses-next">
                  <Icon name="clock" size={14} /> Next: <b>{tasks[0].title}</b>{" "}
                  · {relative(tasks[0].daysLeft)}
                </p>
              )}
              <button
                className="scourses-go"
                onClick={() => setOpenId(info.offering.id)}
              >
                Go to Course
              </button>
            </article>
          );
        })}
      </div>
    </div>
  );
}

export default StudentCourses;
