import { useEffect, useState } from "react";
import "./DoctorCourses.css";
import Icon from "../Shared_components/Icon";
import CourseManage from "./CourseManage";
import {
  getCourses,
  getEnrollments,
  getGroups,
  getOfferings,
  getRooms,
  getTeachingAssignments,
  getTerms,
} from "../../academicApi";
import { getRecords, getSessions } from "../../learningApi";
import {
  getCourseInfo,
  getDoctorOfferingIds,
  CourseInfo,
} from "../../courseHelpers";
import {
  AcademicTerm,
  AttendanceRecord,
  AttendanceSession,
  Enrollment,
  Room,
  StudentGroup,
  User,
} from "../../types";
import { hourText, nextClass, termName, weekOfTerm } from "../../format";

type DoctorCoursesProps = {
  user: User;
  onNavigate: (page: string) => void;
};

export const courseColors = ["#7f56d9", "#2e90fa", "#12b76a", "#f79009"];

function DoctorCourses({ user, onNavigate }: DoctorCoursesProps) {
  const [courses, setCourses] = useState<CourseInfo[]>([]);
  const [groups, setGroups] = useState<StudentGroup[]>([]);
  const [enrollments, setEnrollments] = useState<Enrollment[]>([]);
  const [rooms, setRooms] = useState<Room[]>([]);
  const [terms, setTerms] = useState<AcademicTerm[]>([]);
  const [sessions, setSessions] = useState<AttendanceSession[]>([]);
  const [records, setRecords] = useState<AttendanceRecord[]>([]);
  const [openId, setOpenId] = useState(0);
  const [error, setError] = useState("");

  useEffect(() => {
    async function loadData() {
      try {
        const teaching = await getTeachingAssignments();
        const groupList = await getGroups();
        const ids = getDoctorOfferingIds(user.id, teaching, groupList);
        setCourses(
          getCourseInfo(ids, await getOfferings(), await getCourses()),
        );
        setGroups(groupList);
        setEnrollments(await getEnrollments());
        setRooms(await getRooms());
        setTerms(await getTerms());
        setSessions(await getSessions());
        setRecords(await getRecords());
      } catch (err) {
        setError("Can't load your courses. Did you run: npm run server ?");
      }
    }
    loadData();
  }, [user.id]);

  function getRoomName(roomId: number) {
    const room = rooms.find((item) => item.id === roomId);
    return room !== undefined ? room.name : "";
  }

  function getSections(offeringId: number) {
    return groups
      .filter((g) => g.offering_id === offeringId && g.group_type === "lecture")
      .map((g) => g.label)
      .join(" & ");
  }

  function getAttendance(offeringId: number) {
    const groupIds = groups
      .filter((g) => g.offering_id === offeringId)
      .map((g) => g.id);
    const sessionIds = sessions
      .filter((s) => groupIds.includes(s.group_id))
      .map((s) => s.id);
    const list = records.filter((r) => sessionIds.includes(r.session_id));
    if (list.length === 0) {
      return 0;
    }
    return Math.round(
      (list.filter((r) => r.status !== "absent").length / list.length) * 100,
    );
  }

  const activeTerm = terms.find((term) => term.status === "Active");
  let termLine = "";
  if (activeTerm !== undefined) {
    const week = weekOfTerm(activeTerm.start_date, activeTerm.end_date);
    termLine =
      termName(activeTerm.term_type, activeTerm.academic_year) +
      " · Week " +
      week.week +
      " of " +
      week.total;
  }

  const opened = courses.find((info) => info.offering.id === openId);
  if (opened !== undefined) {
    return (
      <CourseManage
        info={opened}
        color={courseColors[courses.indexOf(opened) % 4]}
        sections={getSections(opened.offering.id)}
        roomName={getRoomName(opened.offering.room_id)}
        attendance={getAttendance(opened.offering.id)}
        onBack={() => setOpenId(0)}
        onNavigate={onNavigate}
      />
    );
  }

  let total = 0;
  courses.forEach((info) => {
    total =
      total +
      enrollments.filter((e) => e.offering_id === info.offering.id).length;
  });

  return (
    <div className="dcourses">
      <p className="dcourses-term">{termLine}</p>
      <h1 className="dcourses-title">Teaching Courses</h1>
      <p className="dcourses-sub">
        {courses.length} courses · {total} students enrolled this term
      </p>

      {error !== "" && <p className="msg-error">{error}</p>}

      <div className="dcourses-list">
        {courses.map((info, index) => {
          const enrolled = enrollments.filter(
            (e) => e.offering_id === info.offering.id,
          ).length;
          return (
            <div key={info.offering.id} className="dcourses-card">
              <div className="dcourses-card-top">
                <p className="dcourses-code">
                  <span
                    className="dcourses-dot"
                    style={{ backgroundColor: courseColors[index % 4] }}
                  />
                  <b>{info.course.course_code}</b> ·{" "}
                  {getSections(info.offering.id)}
                </p>
                <h2 className="dcourses-name">{info.course.title}</h2>
                <p className="dcourses-meta">
                  {info.offering.days.join("/")}{" "}
                  {hourText(info.offering.start_hour)} –{" "}
                  {hourText(info.offering.end_hour)} ·{" "}
                  {getRoomName(info.offering.room_id)}
                </p>
                <span className="dcourses-next">
                  {nextClass(info.offering.days, info.offering.start_hour)}
                </span>
              </div>
              <div className="dcourses-stats">
                <div className="dcourses-stat">
                  <p className="dcourses-stat-label">Total Enrolled Students</p>
                  <p className="dcourses-stat-value">
                    {enrolled}
                    <span className="dcourses-cap">
                      {" "}
                      / {info.offering.capacity}
                    </span>
                  </p>
                </div>
                <div className="dcourses-stat">
                  <p className="dcourses-stat-label">Avg. attendance</p>
                  <p className="dcourses-stat-value">
                    {getAttendance(info.offering.id)}%
                  </p>
                </div>
              </div>
              <button
                className="dcourses-manage"
                onClick={() => setOpenId(info.offering.id)}
              >
                Manage Course <Icon name="arrow" />
              </button>
            </div>
          );
        })}
      </div>
    </div>
  );
}

export default DoctorCourses;
