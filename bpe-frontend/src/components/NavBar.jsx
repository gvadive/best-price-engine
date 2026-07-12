import { useAuth } from "../AuthContext";

export default function NavBar({ view, setView }) {
  const { user, logout } = useAuth();

  return (
    <header className="navbar">
      <div className="navbar-brand" onClick={() => setView("compare")}>
        Best Price Engine
      </div>
      <nav className="navbar-links">
        <button
          className={view === "compare" ? "nav-link active" : "nav-link"}
          onClick={() => setView("compare")}
        >
          Compare
        </button>
        {user && (
          <button
            className={view === "history" ? "nav-link active" : "nav-link"}
            onClick={() => setView("history")}
            id="nav-history"
          >
            Order History
          </button>
        )}
        {user === null && (
          <>
            <button
              className={view === "login" ? "nav-link active" : "nav-link"}
              onClick={() => setView("login")}
              id="nav-login"
            >
              Log in
            </button>
            <button
              className={view === "register" ? "nav-link active" : "nav-link"}
              onClick={() => setView("register")}
              id="nav-register"
            >
              Register
            </button>
          </>
        )}
        {user && (
          <span className="navbar-user">
            <span className="navbar-username">{user}</span>
            <button className="nav-link" onClick={logout} id="nav-logout">
              Log out
            </button>
          </span>
        )}
      </nav>
    </header>
  );
}
