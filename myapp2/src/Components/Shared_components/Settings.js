import { useState } from "react";
import "./Settings.css";
import { changePasswordRequest } from "../../api";

function Settings({ user }) {
  const [oldPassword, setOldPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [message, setMessage] = useState("");
  const [isError, setIsError] = useState(false);
  const [loading, setLoading] = useState(false);

  function showError(text) {
    setIsError(true);
    setMessage(text);
  }

  async function handleSubmit(e) {
    e.preventDefault();
    setMessage("");

    if (oldPassword === "" || newPassword === "" || confirmPassword === "") {
      showError("Please fill all fields");
      return;
    }
    if (newPassword.length < 6) {
      showError("New password must be at least 6 characters");
      return;
    }
    if (newPassword !== confirmPassword) {
      showError("New password and confirm password are not the same");
      return;
    }

    setLoading(true);
    try {
      await changePasswordRequest(user.id, oldPassword, newPassword);
      setIsError(false);
      setMessage("Password changed! Log out and log in with the new password.");
      setOldPassword("");
      setNewPassword("");
      setConfirmPassword("");
    } catch (err) {
      showError(err.message);
    }
    setLoading(false);
  }

  return (
    <div>
      <h1 className="coming-soon">Settings</h1>
      <p className="coming-soon-text">
        Logged in as {user.name} ({user.email})
      </p>

      <form className="settings-form" onSubmit={handleSubmit}>
        <h2 className="settings-title">Change password</h2>

        <label className="settings-label">Current password</label>
        <input
          className="settings-input"
          type="password"
          value={oldPassword}
          onChange={(e) => setOldPassword(e.target.value)}
        />

        <label className="settings-label">New password</label>
        <input
          className="settings-input"
          type="password"
          value={newPassword}
          onChange={(e) => setNewPassword(e.target.value)}
        />

        <label className="settings-label">Confirm new password</label>
        <input
          className="settings-input"
          type="password"
          value={confirmPassword}
          onChange={(e) => setConfirmPassword(e.target.value)}
        />

        {message !== "" && (
          <p className={isError ? "settings-msg error" : "settings-msg success"}>
            {message}
          </p>
        )}

        <button className="settings-btn" type="submit" disabled={loading}>
          {loading ? "Saving..." : "Save new password"}
        </button>
      </form>
    </div>
  );
}

export default Settings;
