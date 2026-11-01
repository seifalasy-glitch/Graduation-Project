import { ReactNode } from "react";
import "./Sidebar.css";
import Icon from "./Icon";
import { MenuItem } from "../../types";

type SidebarProps = {
  variant: "admin" | "doctor" | "student";
  badge: string;
  sectionLabel?: string;
  items: MenuItem[];
  page: string;
  onSelect: (id: string) => void;
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
  page,
  onSelect,
  userName,
  userSub,
  onLogout,
  children,
}: SidebarProps) {
  function getItemClass(id: string) {
    if (id === page) {
      return "side-item active";
    }
    return "side-item";
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
            <button
              className={getItemClass(item.id)}
              onClick={() => onSelect(item.id)}
            >
              <Icon name={item.icon} />
              <span className="side-item-label">{item.label}</span>
            </button>
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
