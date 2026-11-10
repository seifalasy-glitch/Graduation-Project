import { Route, Routes, useLocation, useNavigate } from "react-router-dom";
import "../Shared_components/Pages.css";
import Sidebar from "../Shared_components/Sidebar";
import TopBar from "../Shared_components/TopBar";
import Settings from "../Shared_components/Settings";
import ComingSoon from "../Shared_components/ComingSoon";
import AdminHome from "./AdminHome";
import AdminUsers from "./AdminUsers";
import AdminEnrollments from "./AdminEnrollments";
import AdminTerms from "./AdminTerms";
import AdminPrograms from "./AdminPrograms";
import AdminRooms from "./AdminRooms";
import AdminAuditLogs from "./AdminAuditLogs";
import AdminHelpdesk from "./AdminHelpdesk";
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

type AdminDashboardProps = {
  user: User;
  onUserChange: (user: User) => void;
  onLogout: () => void;
};

function AdminDashboard({ user, onUserChange, onLogout }: AdminDashboardProps) {
  const navigate = useNavigate();
  const location = useLocation();

  let page = location.pathname.split("/")[2];
  if (page === undefined || page === "") {
    page = "home";
  }

  const currentItem = menuItems.find((item) => item.id === page);
  let currentLabel = "";
  if (currentItem !== undefined) {
    currentLabel = currentItem.label;
  }

  function goTo(id: string) {
    if (id === "home") {
      navigate("/admin");
    } else {
      navigate("/admin/" + id);
    }
  }

  return (
    <div>
      <Sidebar
        variant="admin"
        badge="Admin"
        sectionLabel="Workspace"
        items={menuItems}
        basePath="/admin"
        userName={user.name}
        userSub={user.department}
        onLogout={onLogout}
      />

      <div className="main">
        {page !== "settings" && (
          <TopBar
            path={["Admin", currentLabel]}
            searchPlaceholder="Search users, courses, rooms…"
            onRootClick={() => navigate("/admin")}
          />
        )}

        <Routes>
          <Route
            index
            element={
              <div className="page">
                <AdminHome user={user} onNavigate={goTo} />
              </div>
            }
          />
          <Route
            path="users"
            element={
              <div className="page">
                <AdminUsers />
              </div>
            }
          />
          <Route
            path="enrollments"
            element={
              <div className="page">
                <AdminEnrollments />
              </div>
            }
          />
          <Route
            path="terms"
            element={
              <div className="page">
                <AdminTerms />
              </div>
            }
          />
          <Route
            path="programs"
            element={
              <div className="page">
                <AdminPrograms />
              </div>
            }
          />
          <Route
            path="rooms"
            element={
              <div className="page">
                <AdminRooms />
              </div>
            }
          />
          <Route
            path="audit"
            element={
              <div className="page">
                <AdminAuditLogs />
              </div>
            }
          />
          <Route
            path="helpdesk"
            element={
              <div className="page">
                <AdminHelpdesk />
              </div>
            }
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
                  onBack={() => navigate("/admin")}
                />
              </div>
            }
          />
        </Routes>
      </div>
    </div>
  );
}

export default AdminDashboard;
