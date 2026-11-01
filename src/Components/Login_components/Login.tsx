import { useState } from "react";
import "./Login.css";
import "../Shared_components/Pages.css";
import { loginRequest } from "../../api";
import { User } from "../../types";
import SetPassword from "./SetPassword";

type LoginProps = {
  onLogin: (user: User) => void;
};

function Login({ onLogin }: LoginProps) {
  const [idOrEmail, setIdOrEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [firstTimeUser, setFirstTimeUser] = useState<User | null>(null);

  async function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setError("");

    if (idOrEmail === "" || password === "") {
      setError("Please enter your ID / email and password.");
      return;
    }

    setLoading(true);
    try {
      const user = await loginRequest(idOrEmail, password);
      if (user.must_change_password) {
        setFirstTimeUser(user);
      } else {
        onLogin(user);
      }
    } catch (err) {
      if (err instanceof Error && err.message === "Network Error") {
        setError("Can't reach the server. Did you run: npm run server ?");
      } else if (err instanceof Error) {
        setError(err.message);
      }
    }
    setLoading(false);
  }

  let inputClass = "input";
  if (error !== "") {
    inputClass = "input bad";
  }

  return (
    <div className="login-page">
      <header className="login-header">
        <span className="login-logo-box">E</span>
        <span className="login-logo-text">EduSync</span>
      </header>

      <main className="login-main">
        <div className="login-card">
          {firstTimeUser !== null ? (
            <SetPassword user={firstTimeUser} onDone={onLogin} />
          ) : (
            <form onSubmit={handleSubmit} noValidate>
              <h1 className="login-title">Sign in</h1>
              <p className="login-subtitle">Welcome back to EduSync.</p>

              {error !== "" && <p className="login-error">{error}</p>}

              <label className="login-label">ID / Email</label>
              <input
                className={inputClass}
                value={idOrEmail}
                placeholder="e.g. FAC-2041 or name@edusync.edu"
                onChange={(e) => setIdOrEmail(e.target.value)}
              />

              <div className="login-label-row">
                <label className="login-label-inline">Password</label>
                <button type="button" className="login-forgot">
                  Forgot password?
                </button>
              </div>
              <input
                className={inputClass}
                type="password"
                value={password}
                placeholder="••••••••"
                onChange={(e) => setPassword(e.target.value)}
              />

              <button className="login-btn" type="submit" disabled={loading}>
                {loading ? "Signing in..." : "Sign In"}
              </button>

              <p className="login-hint">
                Demo: admin@edusync.edu · alan.reyes@edusync.edu ·
                maya.chen@edusync.edu (password 123456). ID NEW-1001 triggers
                first-time setup.
              </p>
            </form>
          )}
        </div>
      </main>
    </div>
  );
}

export default Login;
