import { useEffect, useState } from "react";
import "./StudentEnroll.css";
import Icon from "../Shared_components/Icon";
import CourseDetails from "./CourseDetails";
import {
  getCourses,
  getEnrollments,
  getGroups,
  getOfferings,
  getRooms,
  getTeachingAssignments,
  getTerms,
  getUsers,
} from "../../academicApi";
import { getLectureDoctor } from "../../courseHelpers";
import {
  AcademicTerm,
  Course,
  CourseOffering,
  Enrollment,
  Room,
  StudentGroup,
  TeachingAssignment,
  User,
} from "../../types";
import { hourText, termName } from "../../format";

type StudentEnrollProps = {
  user: User;
};

export type CatalogItem = {
  offering: CourseOffering;
  course: Course;
  prof: string;
  room: string;
  seatsLeft: number;
};

const times = ["Any time", "Morning", "Afternoon", "Evening"];
const days = ["Mon", "Tue", "Wed", "Thu", "Fri"];
const hours = [8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19];

export function slotText(offering: CourseOffering) {
  return (
    offering.days.join("/") +
    " " +
    hourText(offering.start_hour) +
    " – " +
    hourText(offering.end_hour)
  );
}

function period(offering: CourseOffering) {
  if (offering.start_hour < 12) {
    return "Morning";
  }
  if (offering.start_hour < 17) {
    return "Afternoon";
  }
  return "Evening";
}

function StudentEnroll({ user }: StudentEnrollProps) {
  const [catalog, setCatalog] = useState<CatalogItem[]>([]);
  const [mine, setMine] = useState<Enrollment[]>([]);
  const [term, setTerm] = useState<AcademicTerm | null>(null);
  const [groups, setGroups] = useState<StudentGroup[]>([]);
  const [tab, setTab] = useState("catalog");
  const [search, setSearch] = useState("");
  const [dept, setDept] = useState("All departments");
  const [time, setTime] = useState("Any time");
  const [open, setOpen] = useState<CatalogItem | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    async function loadData() {
      try {
        const terms = await getTerms();
        const active = terms.find((t) => t.status === "Active");
        const offerings = await getOfferings();
        const courses = await getCourses();
        const users = await getUsers();
        const groupList = await getGroups();
        const teaching: TeachingAssignment[] = await getTeachingAssignments();
        const rooms: Room[] = await getRooms();
        const enrollments = await getEnrollments();
        const list: CatalogItem[] = [];
        offerings.forEach((offering) => {
          if (active === undefined || offering.term_id !== active.id) {
            return;
          }
          const course = courses.find((c) => c.id === offering.course_id);
          if (course === undefined) {
            return;
          }
          const doctor = getLectureDoctor(
            offering.id,
            groupList,
            teaching,
            users,
          );
          const room = rooms.find((r) => r.id === offering.room_id);
          const taken = enrollments.filter(
            (e) => e.offering_id === offering.id,
          ).length;
          list.push({
            offering: offering,
            course: course,
            prof: doctor !== undefined ? doctor.name : "To be announced",
            room: room !== undefined ? room.name : "",
            seatsLeft:
              offering.capacity - taken > 0 ? offering.capacity - taken : 0,
          });
        });
        setCatalog(list);
        setMine(enrollments.filter((e) => e.student_user_id === user.id));
        setGroups(groupList);
        setTerm(active !== undefined ? active : null);
      } catch (err) {
        setError("Can't load the catalog. Did you run: npm run server ?");
      }
    }
    loadData();
  }, [user.id]);

  const depts = ["All departments"];
  catalog.forEach((item) => {
    if (!depts.includes(item.course.dept)) {
      depts.push(item.course.dept);
    }
  });

  const held = catalog.filter((item) =>
    mine.some((e) => e.offering_id === item.offering.id),
  );
  let credits = 0;
  held.forEach((item) => {
    credits = credits + item.course.credit_hours;
  });

  const text = search.toLowerCase();
  const list = catalog.filter(
    (item) =>
      (item.course.course_code + item.course.title + item.prof)
        .toLowerCase()
        .includes(text) &&
      (dept === "All departments" || item.course.dept === dept) &&
      (time === "Any time" || period(item.offering) === time),
  );

  const termLabel =
    term !== null ? termName(term.term_type, term.academic_year) : "";

  return (
    <div className="enroll-page">
      <header className="enroll-top">
        <div className="enroll-tabs">
          <button
            className={tab === "catalog" ? "enroll-tab active" : "enroll-tab"}
            onClick={() => setTab("catalog")}
          >
            Catalog
          </button>
          <button
            className={tab === "schedule" ? "enroll-tab active" : "enroll-tab"}
            onClick={() => setTab("schedule")}
          >
            My Schedule
          </button>
        </div>
        <span className="enroll-credits">{credits} / 18 credits</span>
      </header>

      <div className="enroll-body">
        <p className="page-eyebrow">Registration open · {termLabel}</p>
        <h1 className="page-title">
          {tab === "catalog" ? "Enroll in Courses" : "My Schedule"}
        </h1>
        <p className="page-desc">
          {tab === "catalog"
            ? "Browse available sections and check seats, schedule, and prerequisites."
            : held.length + " courses · " + credits + " credits this term"}
        </p>

        {error !== "" && <p className="msg-error">{error}</p>}

        {tab === "catalog" && (
          <div>
            <div className="enroll-filters">
              <label className="enroll-search">
                <Icon name="search" />
                <input
                  className="enroll-search-input"
                  value={search}
                  placeholder="Search by code, title, or professor"
                  onChange={(e) => setSearch(e.target.value)}
                />
              </label>
              <select
                className="enroll-select"
                value={dept}
                onChange={(e) => setDept(e.target.value)}
              >
                {depts.map((item) => (
                  <option key={item}>{item}</option>
                ))}
              </select>
              <select
                className="enroll-select"
                value={time}
                onChange={(e) => setTime(e.target.value)}
              >
                {times.map((item) => (
                  <option key={item}>{item}</option>
                ))}
              </select>
            </div>
            <p className="enroll-count">{list.length} courses</p>
            <div className="enroll-grid">
              {list.map((item) => {
                const isMine = mine.some(
                  (e) => e.offering_id === item.offering.id,
                );
                const full = item.seatsLeft === 0;
                const low =
                  !full && item.seatsLeft / item.offering.capacity <= 0.2;
                const used =
                  ((item.offering.capacity - item.seatsLeft) /
                    item.offering.capacity) *
                  100;
                return (
                  <button
                    key={item.offering.id}
                    className="enroll-card"
                    onClick={() => setOpen(item)}
                  >
                    <span className="enroll-card-top">
                      <b>{item.course.course_code}</b>
                      {isMine ? (
                        <span className="enroll-chip">Enrolled</span>
                      ) : (
                        <span className="enroll-card-credits">
                          {item.course.credit_hours} credits
                        </span>
                      )}
                    </span>
                    <span className="enroll-card-title">
                      {item.course.title}
                    </span>
                    <span className="enroll-card-line">{item.prof}</span>
                    <span className="enroll-card-line">
                      {slotText(item.offering)}
                    </span>
                    <span className="enroll-card-seats">
                      <span
                        className={
                          full ? "enroll-full" : low ? "enroll-low" : ""
                        }
                      >
                        {full
                          ? "0/" + item.offering.capacity + " · Full"
                          : item.seatsLeft +
                            "/" +
                            item.offering.capacity +
                            " seats left"}
                      </span>
                      <span className="enroll-arrow">→</span>
                    </span>
                    <span className="enroll-bar">
                      <span
                        className={
                          full
                            ? "enroll-bar-fill full"
                            : low
                              ? "enroll-bar-fill low"
                              : "enroll-bar-fill"
                        }
                        style={{ width: used + "%" }}
                      />
                    </span>
                  </button>
                );
              })}
            </div>
            {list.length === 0 && (
              <p className="empty">No courses match your filters.</p>
            )}
          </div>
        )}

        {tab === "schedule" && (
          <div className="sched">
            <div className="sched-head">
              <span className="sched-time-col" />
              {days.map((day) => (
                <span key={day} className="sched-day-head">
                  {day}
                </span>
              ))}
            </div>
            <div className="sched-body">
              <div className="sched-time-col">
                {hours.map((hour) => (
                  <p key={hour} className="sched-hour">
                    {hourText(hour)}
                  </p>
                ))}
              </div>
              {days.map((day) => (
                <div key={day} className="sched-day">
                  {hours.map((hour) => (
                    <p key={hour} className="sched-slot" />
                  ))}
                  {held
                    .filter((item) => item.offering.days.includes(day))
                    .map((item) => (
                      <div
                        key={item.offering.id}
                        className="sched-block"
                        style={{
                          top: (item.offering.start_hour - 8) * 48 + 2,
                          height:
                            (item.offering.end_hour -
                              item.offering.start_hour) *
                              48 -
                            4,
                        }}
                      >
                        <b>{item.course.course_code}</b>
                        <span>{item.room}</span>
                      </div>
                    ))}
                </div>
              ))}
            </div>
          </div>
        )}
      </div>

      {open !== null && (
        <CourseDetails
          key={open.offering.id}
          user={user}
          item={open}
          held={held}
          groups={groups}
          mine={mine}
          onClose={() => setOpen(null)}
          onEnrolled={(enrollment) => setMine([...mine, enrollment])}
          onSchedule={() => {
            setOpen(null);
            setTab("schedule");
          }}
        />
      )}
    </div>
  );
}

export default StudentEnroll;
