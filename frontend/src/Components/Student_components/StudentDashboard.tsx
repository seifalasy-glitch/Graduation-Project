import { Route, Routes, useLocation, useNavigate } from "react-router-dom";
import "../Shared_components/Pages.css";
import Sidebar from "../Shared_components/Sidebar";
import Settings from "../Shared_components/Settings";
import ComingSoon from "../Shared_components/ComingSoon";
import StudentHome from "./StudentHome";
import StudentCourses from "./StudentCourses";
import StudentEnroll from "./StudentEnroll";
import { MenuItem, User } from "../../types";

const menuItems: MenuItem[] = [
  { id: "home", label: "Home", icon: "home" },
  { id: "courses", label: "My Courses", icon: "book" },
  { id: "enroll", label: "Enroll in Courses", icon: "plus" },
  { id: "assignments", label: "Assignments", icon: "file" },
  { id: "quizzes", label: "Quizzes", icon: "quiz" },
  { id: "grades", label: "My Grades", icon: "grade" },
  { id: "support", label: "Support", icon: "help" },
  { id: "settings", label: "Settings", icon: "settings" },
];

type StudentDashboardProps = {
  user: User;
  onUserChange: (user: User) => void;
  onLogout: () => void;
};

function StudentDashboard({
  user,
  onUserChange,
  onLogout,
}: StudentDashboardProps) {
  const navigate = useNavigate();
  const location = useLocation();

  let page = location.pathname.split("/")[2];
  if (page === undefined || page === "") {
    page = "home";
  }

  function goTo(id: string) {
    if (id === "home") {
      navigate("/student");
    } else {
      navigate("/student/" + id);
    }
  }

  const currentItem = menuItems.find((item) => item.id === page);
  let currentLabel = "";
  if (currentItem !== undefined) {
    currentLabel = currentItem.label;
  }

  return (
    <div>
      <Sidebar
        variant="student"
        badge="Student"
        items={menuItems}
        basePath="/student"
        userName={user.name}
        userSub={user.department}
        onLogout={onLogout}
      />

      <div className="main">
        <Routes>
          <Route
            index
            element={<StudentHome user={user} onNavigate={goTo} />}
          />
          <Route path="courses" element={<StudentCourses user={user} />} />
          <Route
            path="courses/:offeringId"
            element={<StudentCourses user={user} />}
          />
          <Route path="enroll" element={<StudentEnroll user={user} />} />
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
                  onBack={() => navigate("/student")}
                />
              </div>
            }
          />
        </Routes>
      </div>
    </div>
  );
}

export default StudentDashboard;
