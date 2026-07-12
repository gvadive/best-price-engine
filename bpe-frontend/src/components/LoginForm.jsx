import { useState } from "react";
import { useAuth } from "../AuthContext";

export default function LoginForm({ onSuccess, setView }) {
  const { login } = useAuth();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");

  async function handleSubmit(event) {
    event.preventDefault();
    setError("");
    const res = await login(username, password);
    if (!res.ok) {
      setError(res.data?.error || "Login failed");
      return;
    }
    onSuccess();
  }

  return (
    <div className="auth-card">
      <h2>Log in</h2>
      <form id="login-form" onSubmit={handleSubmit}>
        <label>
          Username
          <input id="login-username" value={username} onChange={(e) => setUsername(e.target.value)} required />
        </label>
        <label>
          Password
          <input
            id="login-password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        </label>
        {error && <p className="form-error" id="login-error">{error}</p>}
        <button type="submit" id="submit-login">Log in</button>
      </form>
      <p className="auth-switch">
        No account? <button className="link-button" onClick={() => setView("register")}>Register</button>
      </p>
    </div>
  );
}
