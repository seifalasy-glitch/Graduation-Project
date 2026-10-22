import { useState } from "react";
import "./Login.css";
import { loginRequest } from "../../api";

function Login({ onLogin }) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e) {
    e.preventDefault();
    setError("");

    if (email === "" || password === "") {
      setError("Please enter your email and password");
      return;
    }

    setLoading(true);

    try {
      const user = await loginRequest(email, password);
      onLogin(user);
    } catch (err) {
      if (err.message === "Network Error") {
        setError("Can't reach the server. Did you run: npm run server ?");
      } else {
        setError(err.message);
      }
      setLoading(false);
    }
  }

  return (
    <div className="login-page">
      <div className="login-card">
        <div className="login-logo">
          <span className="login-logo-box">E</span>EduSync
        </div>

        <h1 className="login-title">Welcome back</h1>
        <p className="login-subtitle">Log in to continue to your dashboard</p>

        <form onSubmit={handleSubmit}>
          <label className="login-label">Email</label>
          <input
            className="login-input"
            type="email"
            placeholder="you@edusync.com"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />

          <label className="login-label">Password</label>
          <input
            className="login-input"
            type="password"
            placeholder="••••••"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />

          {error !== "" && <p className="login-error">{error}</p>}

          <button className="login-btn" type="submit" disabled={loading}>
            {loading ? "Logging in..." : "Log in"}
          </button>
        </form>

        <p className="login-hint">
          Test accounts (password 123456):
          <br />
          maya@edusync.com · ahmed@edusync.com · admin@edusync.com
        </p>
      </div>
    </div>
  );
}

export default Login;
