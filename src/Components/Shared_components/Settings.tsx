import { useState } from "react";
import "./Settings.css";
import "./Pages.css";
import Icon from "./Icon";
import Switch from "./Switch";
import { changePasswordRequest, updateUser } from "../../api";
import { User } from "../../types";

type SettingsProps = {
  user: User;
  onUserChange: (user: User) => void;
};

const sections = [
  { id: "profile", label: "Profile & Account", icon: "users" },
  { id: "security", label: "Security", icon: "shield" },
  { id: "preferences", label: "App Preferences", icon: "settings" },
];

function hasNumber(text: string) {
  return text.split("").some((ch) => ch >= "0" && ch <= "9");
}

function Settings({ user, onUserChange }: SettingsProps) {
  const [section, setSection] = useState("profile");

  const [name, setName] = useState(user.name);
  const [email, setEmail] = useState(user.email);
  const [photo, setPhoto] = useState("");
  const [profileMsg, setProfileMsg] = useState("");
  const [savingProfile, setSavingProfile] = useState(false);

  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const [show, setShow] = useState(false);
  const [passwordMsg, setPasswordMsg] = useState("");
  const [passwordError, setPasswordError] = useState("");
  const [savingPassword, setSavingPassword] = useState(false);

  const [dark, setDark] = useState(false);
  const [language, setLanguage] = useState("English");
  const [notify, setNotify] = useState(true);
  const [compact, setCompact] = useState(false);

  const emailBad = !(email.includes("@") && email.endsWith(".edu"));

  const rules = [
    { label: "8+ characters", pass: next.length >= 8 },
    { label: "A number", pass: hasNumber(next) },
    {
      label: "Upper & lowercase",
      pass: next !== next.toLowerCase() && next !== next.toUpperCase(),
    },
    { label: "Different from current", pass: next !== "" && next !== current },
  ];
  const score = rules.filter((rule) => rule.pass).length;

  let barColor = "#f79009";
  if (score >= 2) {
    barColor = "#7f56d9";
  }
  if (score === 4) {
    barColor = "#12b76a";
  }

  let initials = "";
  name.split(" ").forEach((word) => {
    if (initials.length < 2 && word !== "Dr." && word !== "Prof." && word) {
      initials = initials + word[0].toUpperCase();
    }
  });

  async function handleSaveProfile() {
    setSavingProfile(true);
    setProfileMsg("");
    try {
      const updated = await updateUser(user.id, { name: name, email: email });
      onUserChange(updated);
      setProfileMsg("Changes saved");
    } catch (err) {
      setProfileMsg("Couldn't save, try again");
    }
    setSavingProfile(false);
  }

  async function handleSavePassword() {
    setSavingPassword(true);
    setPasswordMsg("");
    setPasswordError("");
    try {
      await changePasswordRequest(user.id, current, next);
      setPasswordMsg("Password updated");
      setCurrent("");
      setNext("");
    } catch (err) {
      if (err instanceof Error) {
        setPasswordError(err.message);
      }
    }
    setSavingPassword(false);
  }

  function handlePhoto(e: React.ChangeEvent<HTMLInputElement>) {
    if (e.target.files && e.target.files.length > 0) {
      setPhoto(URL.createObjectURL(e.target.files[0]));
    }
  }

  let roleLabel = "Admin";
  if (user.role === "doctor") {
    roleLabel = "Faculty";
  }
  if (user.role === "student") {
    roleLabel = "Student";
  }

  return (
    <div className="settings">
      <h1 className="page-title">Settings</h1>
      <p className="page-desc">
        Manage your account, security, and preferences.
      </p>

      <div className="settings-body">
        <ul className="settings-nav">
          {sections.map((item) => (
            <li key={item.id}>
              <button
                className={
                  section === item.id ? "settings-link active" : "settings-link"
                }
                onClick={() => setSection(item.id)}
              >
                <Icon name={item.icon} />
                <span className="settings-link-text">{item.label}</span>
              </button>
            </li>
          ))}
        </ul>

        <div className="settings-content">
          {section === "profile" && (
            <section className="card">
              <div className="settings-card-head">
                <h2 className="settings-card-title">Profile & Account</h2>
                <p className="settings-card-desc">
                  How you appear to classmates, students, and staff across
                  EduSync.
                </p>
              </div>
              <div className="settings-card-body">
                <p className="field-label">Profile Picture</p>
                <div className="settings-photo-row">
                  <span className="settings-avatar">
                    {photo !== "" ? (
                      <img className="settings-avatar-img" src={photo} alt="" />
                    ) : (
                      initials
                    )}
                  </span>
                  <label className="settings-drop">
                    <span className="settings-drop-icon">
                      <Icon name="upload" />
                    </span>
                    <span className="settings-drop-text">
                      <span className="settings-drop-title">
                        {photo !== ""
                          ? "Photo ready — choose another to replace"
                          : "Drag & drop a new photo"}
                      </span>
                      <span className="settings-drop-sub">
                        or <b>browse</b> · PNG or JPG, square works best, up to
                        5 MB
                      </span>
                    </span>
                    <input
                      className="hidden-input"
                      type="file"
                      accept="image/*"
                      onChange={handlePhoto}
                    />
                  </label>
                </div>

                <div className="settings-two">
                  <div className="settings-col">
                    <label className="field-label">Full Name</label>
                    <input
                      className="input"
                      value={name}
                      onChange={(e) => setName(e.target.value)}
                    />
                  </div>
                  <div className="settings-col right">
                    <label className="field-label">University Email</label>
                    <input
                      className={emailBad ? "input bad" : "input"}
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                    />
                    {emailBad && (
                      <p className="error-text">
                        Use your university address ending in .edu
                      </p>
                    )}
                  </div>
                </div>

                <p className="settings-meta">
                  <span className="settings-role">{roleLabel}</span>
                  {user.department} · {user.university_id}
                </p>
              </div>
              <div className="settings-card-foot">
                {profileMsg !== "" && (
                  <span className="settings-saved">{profileMsg}</span>
                )}
                <button
                  className="btn-primary"
                  disabled={name.trim() === "" || emailBad || savingProfile}
                  onClick={handleSaveProfile}
                >
                  {savingProfile ? "Saving…" : "Save Changes"}
                </button>
              </div>
            </section>
          )}

          {section === "security" && (
            <section className="card">
              <div className="settings-card-head">
                <h2 className="settings-card-title">Security</h2>
                <p className="settings-card-desc">
                  Change the password you use to sign in to EduSync.
                </p>
              </div>
              <div className="settings-card-body settings-narrow">
                <label className="field-label">Current Password</label>
                <input
                  className={passwordError !== "" ? "input bad" : "input"}
                  type={show ? "text" : "password"}
                  value={current}
                  onChange={(e) => setCurrent(e.target.value)}
                />
                {passwordError !== "" && (
                  <p className="error-text">{passwordError}</p>
                )}

                <label className="field-label settings-gap">
                  New Password
                  <button
                    type="button"
                    className="settings-show"
                    onClick={() => setShow(!show)}
                  >
                    {show ? "Hide" : "Show"}
                  </button>
                </label>
                <input
                  className="input"
                  type={show ? "text" : "password"}
                  value={next}
                  onChange={(e) => setNext(e.target.value)}
                />

                <div className="settings-bars">
                  {[0, 1, 2, 3].map((index) => (
                    <span
                      key={index}
                      className="settings-bar"
                      style={{
                        backgroundColor: index < score ? barColor : "#f1f1ef",
                      }}
                    />
                  ))}
                </div>
                <ul className="settings-rules">
                  {rules.map((rule) => (
                    <li
                      key={rule.label}
                      className={
                        rule.pass ? "settings-rule pass" : "settings-rule"
                      }
                    >
                      {rule.pass ? "✓ " : "• "}
                      {rule.label}
                    </li>
                  ))}
                </ul>
              </div>
              <div className="settings-card-foot">
                {passwordMsg !== "" && (
                  <span className="settings-saved">{passwordMsg}</span>
                )}
                <button
                  className="btn-primary"
                  disabled={current === "" || score < 4 || savingPassword}
                  onClick={handleSavePassword}
                >
                  {savingPassword ? "Updating…" : "Update Password"}
                </button>
              </div>
            </section>
          )}

          {section === "preferences" && (
            <section className="card">
              <div className="settings-card-head">
                <h2 className="settings-card-title">App Preferences</h2>
                <p className="settings-card-desc">
                  Personalize how EduSync looks and behaves on this device.
                </p>
              </div>
              <div className="settings-card-body">
                <p className="settings-small-title">System preferences</p>
                <div className="settings-row">
                  <p className="settings-row-title">Enable Dark Mode</p>
                  <p className="settings-row-desc">
                    Switch to a darker interface for low-light environments.
                  </p>
                  <span className="settings-row-control">
                    <Switch on={dark} onChange={setDark} />
                  </span>
                </div>
                <div className="settings-row">
                  <p className="settings-row-title">Language Selection</p>
                  <p className="settings-row-desc">
                    Choose the display language for menus and notifications.
                  </p>
                  <span className="settings-row-control">
                    <select
                      className="settings-select"
                      value={language}
                      onChange={(e) => setLanguage(e.target.value)}
                    >
                      <option>English</option>
                      <option value="Arabic">Arabic — العربية</option>
                    </select>
                  </span>
                </div>
                <div className="settings-row">
                  <p className="settings-row-title">Email notifications</p>
                  <p className="settings-row-desc">
                    Deadlines, grade releases, and announcements.
                  </p>
                  <span className="settings-row-control">
                    <Switch on={notify} onChange={setNotify} />
                  </span>
                </div>
                <div className="settings-row last">
                  <p className="settings-row-title">Compact tables</p>
                  <p className="settings-row-desc">
                    Show more rows by reducing table spacing.
                  </p>
                  <span className="settings-row-control">
                    <Switch on={compact} onChange={setCompact} />
                  </span>
                </div>
                {(dark || language === "Arabic") && (
                  <p className="settings-note">
                    {dark ? "Dark mode" : "Arabic (RTL) layout"} will be
                    available in an upcoming release. Your preference has been
                    noted.
                  </p>
                )}
              </div>
            </section>
          )}
        </div>
      </div>
    </div>
  );
}

export default Settings;
