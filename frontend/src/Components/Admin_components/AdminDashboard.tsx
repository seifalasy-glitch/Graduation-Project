import { useState } from "react";
import "../Shared_components/Pages.css";
import Sidebar from "../Shared_components/Sidebar";
import TopBar from "../Shared_components/TopBar";
import Settings from "../Shared_components/Settings";
import ComingSoon from "../Shared_components/ComingSoon";
import AdminHome from "./AdminHome";
import AdminUsers from "./AdminUsers";
import AdminTerms from "./AdminTerms";
import AdminPrograms from "./AdminPrograms";
import { MenuItem, User } from "../../types";

const menuItems: MenuItem[] = [
  { id: "home", label: "Dashboard", icon: "grid" },
  { id: "users", label: "User Management", icon: "users" },
  { id: "enrollments", label: "Enrollments", icon: "link" },
  { id: "terms", label: "Academic Terms", icon: "calendar" },
  { id: "programs", label: "Academic Programs", icon: "book" },
  { id: "rooms", label: "Rooms Setup", icon: "door" },
  { id: "audit", label: "Audit Logs", icon: "history" },
  { id: "helpdesk", label: "Helpdesk", icon: "help" },
  { id: "settings", label: "Settings", icon: "settings" },
];

const builtPages = ["home", "users", "terms", "programs", "settings"];

type AdminDashboardProps = {
  user: User;
  onUserChange: (user: User) => void;
  onLogout: () => void;
};

function AdminDashboard({ user, onUserChange, onLogout }: AdminDashboardProps) {
  const [page, setPage] = useState("home");

  const currentItem = menuItems.find((item) => item.id === page);
  let currentLabel = "";
  if (currentItem !== undefined) {
    currentLabel = currentItem.label;
  }

  return (
    <div>
      <Sidebar
        variant="admin"
        badge="Admin"
        sectionLabel="Workspace"
        items={menuItems}
        page={page}
        onSelect={setPage}
        userName={user.name}
        userSub={user.department}
        onLogout={onLogout}
      />

      <div className="main">
        {page === "settings" && (
          <Settings user={user} onUserChange={onUserChange} />
        )}

        {page !== "settings" && (
          <TopBar
            path={["Admin", currentLabel]}
            searchPlaceholder="Search users, courses, rooms…"
            onRootClick={() => setPage("home")}
          />
        )}

        {page !== "settings" && (
          <div className="page">
            {page === "home" && <AdminHome user={user} onNavigate={setPage} />}
            {page === "users" && <AdminUsers />}
            {page === "terms" && <AdminTerms />}
            {page === "programs" && <AdminPrograms />}
            {!builtPages.includes(page) && (
              <ComingSoon title={currentLabel} onBack={() => setPage("home")} />
            )}
          </div>
        )}
      </div>
    </div>
  );
}

export default AdminDashboard;
