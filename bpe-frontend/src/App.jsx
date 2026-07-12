import { useState } from "react";
import { AuthProvider, useAuth } from "./AuthContext";
import NavBar from "./components/NavBar";
import ComparePage from "./components/ComparePage";
import OrderHistoryPage from "./components/OrderHistoryPage";
import LoginForm from "./components/LoginForm";
import RegisterForm from "./components/RegisterForm";
import "./App.css";

function Shell() {
  const { user } = useAuth();
  const [view, setView] = useState("compare");

  if (user === undefined) {
    return <div className="app-loading">Loading...</div>;
  }

  return (
    <>
      <NavBar view={view} setView={setView} />
      {view === "compare" && <ComparePage setView={setView} />}
      {view === "history" && user && <OrderHistoryPage />}
      {view === "login" && (
        <main className="auth-page">
          <LoginForm onSuccess={() => setView("compare")} setView={setView} />
        </main>
      )}
      {view === "register" && (
        <main className="auth-page">
          <RegisterForm setView={setView} />
        </main>
      )}
    </>
  );
}

export default function App() {
  return (
    <AuthProvider>
      <Shell />
    </AuthProvider>
  );
}
