window.NS_ADMIN_API = localStorage.getItem("ns_admin_api_base")
  || "http://localhost:8082/NarainsonsProject";

window.NS_ADMIN_TOKEN_KEY = "ns_admin_access_token";

function getAdminToken() {
  return localStorage.getItem(window.NS_ADMIN_TOKEN_KEY) || "";
}

function setAdminToken(token) {
  if (token) localStorage.setItem(window.NS_ADMIN_TOKEN_KEY, token);
  else localStorage.removeItem(window.NS_ADMIN_TOKEN_KEY);
}

async function adminApi(path, options = {}) {
  const headers = Object.assign({ "Content-Type": "application/json" }, options.headers || {});
  const token = getAdminToken();
  if (token) headers.Authorization = "Bearer " + token;
  const res = await fetch(window.NS_ADMIN_API + path, Object.assign({}, options, { headers }));
  const json = await res.json().catch(() => ({}));
  if (!res.ok || json.success === false) {
    throw new Error(json.message || "Request failed");
  }
  return json.data;
}
