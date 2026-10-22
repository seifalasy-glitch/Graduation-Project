import { useState } from "react";
import "./DoctorDashboard.css";
import Settings from "../Shared_components/Settings";
import DoctorCourses from "./DoctorCourses";

const menuItems = [
  { id: "home", label: "Home" },
  { id: "courses", label: "My Courses" },
  { id: "attendance", label: "Attendance" },
  { id: "assignments", label: "Assignments" },
  { id: "quizzes", label: "Quizzes" },
  { id: "grades", label: "Students Grades" },
  { id: "settings", label: "Settings" },
];

function DoctorDashboard({ user, onLogout }) {
  const [page, setPage] = useState("home");

  const hour = new Date().getHours();
  let greeting = "Good evening";
  if (hour < 12) {
    greeting = "Good morning";
  } else if (hour < 18) {
    greeting = "Good afternoon";
  }

  const today = new Date().toLocaleDateString("en-US", {
    weekday: "long",
    month: "long",
    day: "numeric",
  });

  const firstName = user.name.split(" ")[0];

  const currentItem = menuItems.find((item) => item.id === page);

  function getItemClass(id) {
    let className = "menu-item";
    if (id === page) {
      className = className + " active";
    }
    return className;
  }

  return (
    <div>
      <aside className="sidebar">
        <div className="sidebar-inner">
          <div className="logo">
            <span className="logo-box">E</span>EduSync
            <span className="role-badge doctor">Doctor</span>
          </div>

          <ul className="menu">
            {menuItems.map((item) => (
              <li
                key={item.id}
                className={getItemClass(item.id)}
                onClick={() => setPage(item.id)}
              >
                {item.label}
              </li>
            ))}
          </ul>

          <button className="logout-btn" onClick={onLogout}>
            Log out
          </button>
        </div>
      </aside>

      <main className="content">
        <div className="content-inner">
          {page === "home" && (
            <div>
              <p className="date-line">{today}</p>
              <h1 className="greeting">
                {greeting}, Dr. {firstName}
              </h1>
            </div>
          )}

          {page === "settings" && <Settings user={user} />}

          {page === "courses" && <DoctorCourses user={user} />}

          {page !== "home" && page !== "settings" && page !== "courses" && (
            <div>
              <h1 className="coming-soon">{currentItem.label}</h1>
              <p className="coming-soon-text">This page is coming soon.</p>
            </div>
          )}
        </div>
      </main>
    </div>
  );
}

export default DoctorDashboard;
