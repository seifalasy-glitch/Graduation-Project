import { useEffect, useState } from "react";
import { Route, Routes, useLocation, useNavigate } from "react-router-dom";
import "../Shared_components/Pages.css";
import Sidebar from "../Shared_components/Sidebar";
import TopBar from "../Shared_components/TopBar";
import Settings from "../Shared_components/Settings";
import ComingSoon from "../Shared_components/ComingSoon";
import DoctorHome from "./DoctorHome";
import DoctorCourses from "./DoctorCourses";
import AttendanceLive from "./AttendanceLive";
import {
  getCourses,
  getGroups,
  getOfferings,
  getTeachingAssignments,
} from "../../academicApi";
import {
  getCourseInfo,
  getDoctorOfferingIds,
  CourseInfo,
} from "../../courseHelpers";
import { MenuItem, User } from "../../types";

const menuItems: MenuItem[] = [
  { id: "home", label: "Home", icon: "home" },
  { id: "courses", label: "Courses", icon: "book" },
  { id: "assignments", label: "Assignments", icon: "file" },
  { id: "quizzes", label: "Quizzes", icon: "quiz" },
  { id: "grades", label: "Grade Management", icon: "grade" },
  { id: "inbox", label: "Inbox", icon: "inbox" },
  { id: "students", label: "Students", icon: "users" },
  { id: "queue", label: "Grading queue", icon: "check" },
  { id: "analytics", label: "Analytics", icon: "chart" },
  { id: "settings", label: "Settings", icon: "settings" },
];

type DoctorDashboardProps = {
  user: User;
  onUserChange: (user: User) => void;
  onLogout: () => void;
};

function DoctorDashboard({
  user,
  onUserChange,
  onLogout,
}: DoctorDashboardProps) {
  const navigate = useNavigate();
  const location = useLocation();
  const [teachingCourses, setTeachingCourses] = useState<CourseInfo[]>([]);

  useEffect(() => {
    async function loadCourses() {
      try {
        const teaching = await getTeachingAssignments();
        const groups = await getGroups();
        const offerings = await getOfferings();
        const courses = await getCourses();
        const ids = getDoctorOfferingIds(user.id, teaching, groups);
        setTeachingCourses(getCourseInfo(ids, offerings, courses));
      } catch (err) {
        setTeachingCourses([]);
      }
    }
    loadCourses();
  }, [user.id]);

  let page = location.pathname.split("/")[2];
  if (page === undefined || page === "") {
    page = "home";
  }

  function goTo(id: string) {
    if (id === "home") {
      navigate("/doctor");
    } else {
      navigate("/doctor/" + id);
    }
  }

  const currentItem = menuItems.find((item) => item.id === page);
  let currentLabel = "Live Attendance";
  if (currentItem !== undefined) {
    currentLabel = currentItem.label;
  }

  if (page === "attendance") {
    return <AttendanceLive user={user} onExit={() => navigate("/doctor")} />;
  }

  return (
    <div>
      <Sidebar
        variant="doctor"
        badge="Faculty"
        items={menuItems}
        basePath="/doctor"
        userName={user.name}
        userSub={user.department}
        onLogout={onLogout}
      >
        <p className="side-extra-label">Teaching</p>
        {teachingCourses.map((item) => (
          <p
            key={item.offering.id}
            className="side-extra-item"
            onClick={() => navigate("/doctor/courses/" + item.offering.id)}
          >
            <span className="side-extra-emoji">{item.offering.emoji}</span>
            {item.course.title}
          </p>
        ))}
      </Sidebar>

      <div className="main">
        {page !== "settings" && (
          <TopBar
            path={["Faculty", currentLabel]}
            searchPlaceholder="Search students, courses…"
            onRootClick={() => navigate("/doctor")}
          />
        )}

        <Routes>
          <Route index element={<DoctorHome user={user} onNavigate={goTo} />} />
          <Route
            path="courses"
            element={<DoctorCourses user={user} onNavigate={goTo} />}
          />
          <Route
            path="courses/:offeringId"
            element={<DoctorCourses user={user} onNavigate={goTo} />}
          />
          <Route
            path="settings"
            element={<Settings user={user} onUserChange={onUserChange} />}
          />
          <Route
            path="*"
            element={
              <div className="page">
                <ComingSoon
                  title={currentLabel}
                  onBack={() => navigate("/doctor")}
                />
              </div>
            }
          />
        </Routes>
      </div>
    </div>
  );
}

export default DoctorDashboard;
