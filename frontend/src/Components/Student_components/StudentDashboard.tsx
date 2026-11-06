import { useState } from "react";
import "../Shared_components/Pages.css";
import Sidebar from "../Shared_components/Sidebar";
import Settings from "../Shared_components/Settings";
import ComingSoon from "../Shared_components/ComingSoon";
import StudentHome from "./StudentHome";
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

const builtPages = ["home", "settings"];

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
  const [page, setPage] = useState("home");

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
        page={page}
        onSelect={setPage}
        userName={user.name}
        userSub={user.department}
        onLogout={onLogout}
      />

      <div className="main">
        {page === "home" && <StudentHome user={user} onNavigate={setPage} />}
        {page === "settings" && (
          <Settings user={user} onUserChange={onUserChange} />
        )}
        {!builtPages.includes(page) && (
          <div className="page">
            <ComingSoon title={currentLabel} onBack={() => setPage("home")} />
          </div>
        )}
      </div>
    </div>
  );
}

export default StudentDashboard;
