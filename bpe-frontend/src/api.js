const API_BASE = import.meta.env.VITE_API_BASE || "/api";

async function safeJson(response) {
  const text = await response.text();
  return text ? JSON.parse(text) : null;
}

async function request(path, options = {}) {
  const response = await fetch(`${API_BASE}${path}`, {
    credentials: "include",
    headers: { "Content-Type": "application/json", ...(options.headers || {}) },
    ...options,
  });
  return { ok: response.ok, status: response.status, data: await safeJson(response) };
}

export const me = () => request("/auth/me");
export const register = (username, password) =>
  request("/auth/register", { method: "POST", body: JSON.stringify({ username, password }) });
export const login = (username, password) =>
  request("/auth/login", { method: "POST", body: JSON.stringify({ username, password }) });
export const logout = () => request("/auth/logout", { method: "POST" });

export const searchProducts = (query) =>
  request(`/products?query=${encodeURIComponent(query)}`);

export const compareOffers = (params) =>
  request(`/compare?${new URLSearchParams(params).toString()}`);

export const requestBulkPricing = (offerId, requestedQuantity) =>
  request("/bulk-pricing-requests", {
    method: "POST",
    body: JSON.stringify({ offerId, requestedQuantity }),
  });

export const placeOrder = (payload) =>
  request("/orders", { method: "POST", body: JSON.stringify(payload) });

export const orderHistory = () => request("/orders");

export const placeWatch = (payload) =>
  request("/price-watch-orders", { method: "POST", body: JSON.stringify(payload) });
