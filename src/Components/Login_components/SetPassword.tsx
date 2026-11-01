import { useState } from "react";
import { setFirstPassword } from "../../api";
import { User } from "../../types";

type SetPasswordProps = {
  user: User;
  onDone: (user: User) => void;
};

function SetPassword({ user, onDone }: SetPasswordProps) {
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);

  const mismatch = confirm.length > 0 && password !== confirm;
  const valid = password.length >= 8 && password === confirm;

  let strength = password.length * 10;
  if (strength > 100) {
    strength = 100;
  }

  async function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!valid) {
      return;
    }
    setSaving(true);
    try {
      const updated = await setFirstPassword(user.id, password);
      onDone(updated);
    } catch (err) {
      setError("Couldn't save the new password, try again.");
      setSaving(false);
    }
  }

  return (
    <form onSubmit={handleSubmit}>
      <span className="login-chip">First-time login</span>
      <h1 className="login-title login-title-gap">Set New Password</h1>
      <p className="login-subtitle">
        Replace your temporary password to continue.
      </p>

      <label className="login-label">New Password</label>
      <input
        className="input"
        type="password"
        value={password}
        onChange={(e) => setPassword(e.target.value)}
      />
      <div className="login-strength">
        <div
          className="login-strength-fill"
          style={{ width: strength + "%" }}
        />
      </div>
      <p className="login-small">At least 8 characters</p>

      <label className="login-label login-label-gap">Confirm Password</label>
      <input
        className={mismatch ? "input bad" : "input"}
        type="password"
        value={confirm}
        onChange={(e) => setConfirm(e.target.value)}
      />
      {mismatch && <p className="login-small red">Passwords don't match.</p>}
      {error !== "" && <p className="login-error">{error}</p>}

      <button className="login-btn" type="submit" disabled={!valid || saving}>
        Update & Continue
      </button>
    </form>
  );
}

export default SetPassword;
