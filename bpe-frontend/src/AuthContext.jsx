import { createContext, useContext, useEffect, useState } from "react";
import * as api from "./api";

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  // undefined = still checking session on load, null = logged out, string = username
  const [user, setUser] = useState(undefined);

  useEffect(() => {
    api.me().then((res) => setUser(res.ok ? res.data.username : null));
  }, []);

  async function login(username, password) {
    const res = await api.login(username, password);
    if (res.ok) setUser(res.data.username);
    return res;
  }

  async function register(username, password) {
    return api.register(username, password);
  }

  async function logout() {
    await api.logout();
    setUser(null);
  }

  return (
    <AuthContext.Provider value={{ user, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  return useContext(AuthContext);
}
