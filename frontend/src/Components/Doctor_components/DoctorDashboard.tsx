import { useEffect, useState } from "react";
import "../Shared_components/Pages.css";
import Sidebar from "../Shared_components/Sidebar";
import TopBar from "../Shared_components/TopBar";
import Settings from "../Shared_components/Settings";
import ComingSoon from "../Shared_components/ComingSoon";
import DoctorHome from "./DoctorHome";
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

const builtPages = ["home", "settings"];

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
  const [page, setPage] = useState("home");
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

  const currentItem = menuItems.find((item) => item.id === page);
  let currentLabel = "Live Attendance";
  if (currentItem !== undefined) {
    currentLabel = currentItem.label;
  }

  return (
    <div>
      <Sidebar
        variant="doctor"
        badge="Faculty"
        items={menuItems}
        page={page}
        onSelect={setPage}
        userName={user.name}
        userSub={user.department}
        onLogout={onLogout}
      >
        <p className="side-extra-label">Teaching</p>
        {teachingCourses.map((item) => (
          <p
            key={item.offering.id}
            className="side-extra-item"
            onClick={() => setPage("courses")}
          >
            <span className="side-extra-emoji">{item.offering.emoji}</span>
            {item.course.title}
          </p>
        ))}
      </Sidebar>

      <div className="main">
        {page === "settings" && (
          <Settings user={user} onUserChange={onUserChange} />
        )}

        {page !== "settings" && (
          <TopBar
            path={["Faculty", currentLabel]}
            searchPlaceholder="Search students, courses…"
            onRootClick={() => setPage("home")}
          />
        )}

        {page === "home" && <DoctorHome user={user} onNavigate={setPage} />}
        {!builtPages.includes(page) && (
          <div className="page">
            <ComingSoon title={currentLabel} onBack={() => setPage("home")} />
          </div>
        )}
      </div>
    </div>
  );
}

export default DoctorDashboard;
