import { useState } from "react";
import { useAuth } from "../AuthContext";

export default function RegisterForm({ setView }) {
  const { register, login } = useAuth();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [success, setSuccess] = useState(false);

  async function handleSubmit(event) {
    event.preventDefault();
    setError("");
    const res = await register(username, password);
    if (!res.ok) {
      setError(res.data?.error || "Registration failed");
      return;
    }
    setSuccess(true);
    await login(username, password);
    setView("compare");
  }

  return (
    <div className="auth-card">
      <h2>Create an account</h2>
      <form id="register-form" onSubmit={handleSubmit}>
        <label>
          Username
          <input id="register-username" value={username} onChange={(e) => setUsername(e.target.value)} required />
        </label>
        <label>
          Password
          <input
            id="register-password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        </label>
        {error && <p className="form-error" id="register-error">{error}</p>}
        {success && <p className="form-success">Account created -- logging you in...</p>}
        <button type="submit" id="submit-register">Register</button>
      </form>
      <p className="auth-switch">
        Already have an account? <button className="link-button" onClick={() => setView("login")}>Log in</button>
      </p>
    </div>
  );
}
