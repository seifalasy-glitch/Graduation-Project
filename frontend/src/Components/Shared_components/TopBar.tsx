import "./TopBar.css";
import Icon from "./Icon";

type TopBarProps = {
  path: string[];
  searchPlaceholder: string;
  onRootClick?: () => void;
};

function TopBar({ path, searchPlaceholder, onRootClick }: TopBarProps) {
  return (
    <header className="topbar">
      <p className="topbar-path">
        {path.map((part, index) => (
          <span key={part + index}>
            {index > 0 && <span className="topbar-sep">/</span>}
            {index === 0 && onRootClick ? (
              <button className="topbar-root" onClick={onRootClick}>
                {part}
              </button>
            ) : (
              <span
                className={index === path.length - 1 ? "topbar-current" : ""}
              >
                {part}
              </span>
            )}
          </span>
        ))}
      </p>
      <label className="topbar-search">
        <Icon name="search" size={14} />
        <input className="topbar-input" placeholder={searchPlaceholder} />
      </label>
    </header>
  );
}

export default TopBar;
