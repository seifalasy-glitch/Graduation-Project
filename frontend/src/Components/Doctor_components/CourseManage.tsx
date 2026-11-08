import { useEffect, useState } from "react";
import Icon from "../Shared_components/Icon";
import CourseRoster from "./CourseRoster";
import CourseMaterials from "./CourseMaterials";
import StudentGroups from "./StudentGroups";
import KnowledgeBase from "./KnowledgeBase";
import {
  getEnrollments,
  getGroups,
  getMemberships,
  getUsers,
} from "../../academicApi";
import {
  getGradeItems,
  getMaterials,
  getRecords,
  getSessions,
  getStudentGrades,
} from "../../learningApi";
import { CourseInfo } from "../../courseHelpers";
import {
  AttendanceRecord,
  AttendanceSession,
  CourseMaterial,
  Enrollment,
  GradeItem,
  GroupMembership,
  StudentGrade,
  StudentGroup,
  User,
} from "../../types";
import { nextClass, hourText } from "../../format";

type CourseManageProps = {
  info: CourseInfo;
  color: string;
  sections: string;
  roomName: string;
  attendance: number;
  onBack: () => void;
  onNavigate: (page: string) => void;
};

export type RosterRow = {
  enrollment: Enrollment;
  student: User;
  attended: number;
  total: number;
  percent: number;
};

function CourseManage({
  info,
  color,
  sections,
  roomName,
  attendance,
  onBack,
  onNavigate,
}: CourseManageProps) {
  const [tab, setTab] = useState("roster");
  const [enrollments, setEnrollments] = useState<Enrollment[]>([]);
  const [users, setUsers] = useState<User[]>([]);
  const [groups, setGroups] = useState<StudentGroup[]>([]);
  const [memberships, setMemberships] = useState<GroupMembership[]>([]);
  const [sessions, setSessions] = useState<AttendanceSession[]>([]);
  const [records, setRecords] = useState<AttendanceRecord[]>([]);
  const [gradeItems, setGradeItems] = useState<GradeItem[]>([]);
  const [grades, setGrades] = useState<StudentGrade[]>([]);
  const [materials, setMaterials] = useState<CourseMaterial[]>([]);
  const [error, setError] = useState("");

  useEffect(() => {
    async function loadData() {
      try {
        const all = await getEnrollments();
        setEnrollments(all.filter((e) => e.offering_id === info.offering.id));
        setUsers(await getUsers());
        setGroups(
          (await getGroups()).filter((g) => g.offering_id === info.offering.id),
        );
        setMemberships(await getMemberships());
        setSessions(await getSessions());
        setRecords(await getRecords());
        setGradeItems(
          (await getGradeItems()).filter(
            (g) => g.offering_id === info.offering.id,
          ),
        );
        setGrades(await getStudentGrades());
        setMaterials(await getMaterials(info.offering.id));
      } catch (err) {
        setError("Can't load this course. Did you run: npm run server ?");
      }
    }
    loadData();
  }, [info.offering.id]);

  const groupIds = groups.map((g) => g.id);
  const sessionIds = sessions
    .filter((s) => groupIds.includes(s.group_id))
    .map((s) => s.id);

  const rows: RosterRow[] = [];
  enrollments.forEach((enrollment) => {
    const student = users.find((u) => u.id === enrollment.student_user_id);
    if (student === undefined) {
      return;
    }
    const mine = records.filter(
      (r) =>
        r.enrollment_id === enrollment.id && sessionIds.includes(r.session_id),
    );
    const attended = mine.filter((r) => r.status !== "absent").length;
    const percent =
      mine.length > 0 ? Math.round((attended / mine.length) * 100) : 100;
    rows.push({
      enrollment: enrollment,
      student: student,
      attended: attended,
      total: mine.length,
      percent: percent,
    });
  });

  const atRisk = rows.filter((row) => row.percent < 75).length;

  const stats = [
    { label: "Enrolled", value: String(rows.length) },
    { label: "Avg. attendance", value: attendance + "%" },
    { label: "Below 75%", value: String(atRisk) },
    { label: "Materials", value: String(materials.length) },
  ];

  const tabs = [
    { id: "roster", label: "Course Roster · " + rows.length },
    { id: "materials", label: "Syllabus & Materials" },
    { id: "groups", label: "Student Groups" },
    { id: "knowledge", label: "AI Knowledge Base" },
  ];

  return (
    <div className="dcourses">
      <button className="dmanage-back" onClick={onBack}>
        ← All courses
      </button>
      <p className="dcourses-code dmanage-code">
        <span className="dcourses-dot" style={{ backgroundColor: color }} />
        <b>{info.course.course_code}</b> · {sections} · Fall 2026
      </p>
      <h1 className="dcourses-title">{info.course.title}</h1>
      <p className="dmanage-meta">
        {info.offering.days.join("/")} {hourText(info.offering.start_hour)} –{" "}
        {hourText(info.offering.end_hour)} · {roomName} · Next session{" "}
        {nextClass(info.offering.days, info.offering.start_hour)}
      </p>

      {error !== "" && <p className="msg-error">{error}</p>}

      <section className="dmanage-actions">
        <button
          className="dmanage-action primary"
          onClick={() => onNavigate("attendance")}
        >
          <span className="dmanage-action-icon">
            <Icon name="qr" />
          </span>
          <span className="dmanage-action-text">
            <b>Start QR Attendance</b>
            <span>Launch a live session for {roomName}</span>
          </span>
          <span className="dmanage-action-arrow">
            <Icon name="arrow" />
          </span>
        </button>
        <button
          className="dmanage-action"
          onClick={() => onNavigate("assignments")}
        >
          <span className="dmanage-action-icon light">
            <Icon name="file" />
          </span>
          <span className="dmanage-action-text">
            <b>Create Assignment</b>
            <span>Draft, attach files, and set a due date</span>
          </span>
          <span className="dmanage-action-arrow">
            <Icon name="arrow" />
          </span>
        </button>
      </section>

      <section className="dmanage-stats">
        {stats.map((stat) => (
          <div key={stat.label} className="dmanage-stat">
            <p className="dcourses-stat-label">{stat.label}</p>
            <p className="dmanage-stat-value">{stat.value}</p>
          </div>
        ))}
      </section>

      <div className="tabs dmanage-tabs">
        {tabs.map((item) => (
          <button
            key={item.id}
            className={tab === item.id ? "tab active" : "tab"}
            onClick={() => setTab(item.id)}
          >
            {item.label}
          </button>
        ))}
      </div>

      {tab === "roster" && (
        <CourseRoster rows={rows} gradeItems={gradeItems} grades={grades} />
      )}
      {tab === "materials" && (
        <CourseMaterials
          offeringId={info.offering.id}
          materials={materials}
          onChange={setMaterials}
        />
      )}
      {tab === "groups" && (
        <StudentGroups
          offeringId={info.offering.id}
          rows={rows}
          groups={groups}
          memberships={memberships}
          onGroupsChange={setGroups}
          onMembershipsChange={setMemberships}
        />
      )}
      {tab === "knowledge" && (
        <KnowledgeBase
          offeringId={info.offering.id}
          materials={materials}
          onChange={setMaterials}
        />
      )}
    </div>
  );
}

export default CourseManage;
