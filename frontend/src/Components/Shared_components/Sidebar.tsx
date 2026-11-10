import { ReactNode } from "react";
import { NavLink } from "react-router-dom";
import "./Sidebar.css";
import Icon from "./Icon";
import { MenuItem } from "../../types";

type SidebarProps = {
  variant: "admin" | "doctor" | "student";
  badge: string;
  sectionLabel?: string;
  items: MenuItem[];
  basePath: string;
  userName: string;
  userSub: string;
  onLogout: () => void;
  children?: ReactNode;
};

function getInitials(name: string) {
  const parts = name.split(" ");
  let text = "";
  parts.forEach((part) => {
    if (text.length < 2 && part !== "Dr.") {
      text = text + part[0];
    }
  });
  return text.toUpperCase();
}

function Sidebar({
  variant,
  badge,
  sectionLabel,
  items,
  basePath,
  userName,
  userSub,
  onLogout,
  children,
}: SidebarProps) {
  function getItemPath(id: string) {
    if (id === "home") {
      return basePath;
    }
    return basePath + "/" + id;
  }

  return (
    <aside className={"sidebar " + variant}>
      <div className="side-logo">
        <span className="side-logo-box">E</span>
        <span className="side-logo-text">EduSync</span>
        <span className="side-badge">{badge}</span>
      </div>

      {sectionLabel && <p className="side-section">{sectionLabel}</p>}

      <ul className="side-menu">
        {items.map((item) => (
          <li key={item.id}>
            <NavLink
              to={getItemPath(item.id)}
              end={item.id === "home"}
              className={({ isActive }) =>
                isActive ? "side-item active" : "side-item"
              }
            >
              <Icon name={item.icon} />
              <span className="side-item-label">{item.label}</span>
            </NavLink>
          </li>
        ))}
      </ul>

      {children}

      <div className="side-footer">
        <span className="side-avatar">{getInitials(userName)}</span>
        <div className="side-user">
          <p className="side-user-name">{userName}</p>
          <p className="side-user-sub">{userSub}</p>
        </div>
        <button className="side-logout" onClick={onLogout}>
          <Icon name="logout" />
          <span className="side-item-label">Sign out</span>
        </button>
      </div>
    </aside>
  );
}

export default Sidebar;
