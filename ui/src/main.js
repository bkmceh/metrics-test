const messagesEl = document.getElementById("messages");
const sharedTokenEl = document.getElementById("shared-token");
const statsOutputEl = document.getElementById("stats-output");

let accessToken = "";

function logMessage(text) {
  const stamp = new Date().toISOString();
  messagesEl.textContent = `[${stamp}] ${text}\n` + messagesEl.textContent;
}

function switchTab(tabId) {
  document.querySelectorAll(".tab-btn").forEach((btn) => {
    btn.classList.toggle("active", btn.dataset.tab === tabId);
  });
  document.querySelectorAll(".tab-panel").forEach((panel) => {
    panel.classList.toggle("active", panel.id === tabId);
  });
}

function formatToApiDateTime(localDateTime) {
  const date = new Date(localDateTime);
  if (Number.isNaN(date.getTime())) {
    throw new Error("Некорректная дата/время");
  }
  const pad = (n) => String(n).padStart(2, "0");
  const year = date.getFullYear();
  const month = pad(date.getMonth() + 1);
  const day = pad(date.getDate());
  const hours = pad(date.getHours());
  const minutes = pad(date.getMinutes());
  const seconds = pad(date.getSeconds());
  const offsetMinutes = -date.getTimezoneOffset();
  const sign = offsetMinutes >= 0 ? "+" : "-";
  const absOffset = Math.abs(offsetMinutes);
  const offsetHours = pad(Math.floor(absOffset / 60));
  const offsetMins = pad(absOffset % 60);
  return `${year}-${month}-${day}T${hours}:${minutes}:${seconds}${sign}${offsetHours}:${offsetMins}`;
}

function getTokenStatus(token) {
  try {
    const parts = token.split(".");
    if (parts.length < 2) {
      return "invalid";
    }
    const payload = JSON.parse(atob(parts[1]));
    if (!payload.exp) {
      return "valid";
    }
    return Date.now() >= payload.exp * 1000 ? "expired" : "valid";
  } catch {
    return "invalid";
  }
}

async function request(path, options = {}) {
  const response = await fetch(`/api/${path}`, options);
  const text = await response.text();
  let data = {};
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = { raw: text };
    }
  }
  if (!response.ok) {
    const message = data.message || data.error || data.raw || `HTTP ${response.status}`;
    throw new Error(`${response.status}: ${message}`);
  }
  return data;
}

document.querySelectorAll(".tab-btn").forEach((btn) => {
  btn.addEventListener("click", () => switchTab(btn.dataset.tab));
});

sharedTokenEl.addEventListener("input", () => {
  accessToken = sharedTokenEl.value.trim();
});

document.getElementById("signup-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  const clientId = document.getElementById("signup-client-id").value.trim();

  if (!clientId) {
    logMessage("Ошибка: укажите clientId для sign-up.");
    return;
  }

  try {
    const data = await request("sign-up", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ clientId })
    });
    document.getElementById("auth-client-id").value = clientId;
    logMessage(`Sign-up успешен: ${JSON.stringify(data)}`);
  } catch (error) {
    logMessage(`Sign-up ошибка: ${error.message}`);
  }
});

document.getElementById("auth-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  const clientID = document.getElementById("auth-client-id").value.trim();

  if (!clientID) {
    logMessage("Ошибка: укажите clientID для auth.");
    return;
  }

  try {
    const data = await request("auth", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ clientID })
    });
    accessToken = data.jwt || "";
    sharedTokenEl.value = accessToken;
    document.getElementById("metric-token").value = accessToken;
    document.getElementById("stats-token").value = accessToken;
    logMessage("Auth успешен: JWT получен.");
  } catch (error) {
    accessToken = "";
    sharedTokenEl.value = "";
    logMessage(`Auth ошибка: ${error.message}`);
  }
});

document.getElementById("metric-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  const token = document.getElementById("metric-token").value.trim();

  if (!token) {
    logMessage("Ошибка: укажите token для отправки метрики.");
    return;
  }
  const tokenStatusForMetric = getTokenStatus(token);
  if (tokenStatusForMetric === "expired") {
    logMessage("Ошибка: токен истек. Выполните auth повторно.");
    return;
  }
  if (tokenStatusForMetric === "invalid") {
    logMessage("Ошибка: токен невалидный. Выполните auth повторно.");
    return;
  }

  let timestamp;
  try {
    timestamp = formatToApiDateTime(document.getElementById("metric-timestamp").value.trim());
  } catch (error) {
    logMessage(`Ошибка времени: ${error.message}`);
    return;
  }
  const value = Number(document.getElementById("metric-value").value);
  const payloadRaw = document.getElementById("metric-payload").value.trim();

  let payload;
  try {
    payload = JSON.parse(payloadRaw);
  } catch {
    logMessage("Ошибка: payload должен быть валидным JSON.");
    return;
  }

  try {
    const data = await request("metrics", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Authorization": `Bearer ${token}`
      },
      body: JSON.stringify({ timestamp, value, payload })
    });
    logMessage(`Метрика сохранена: ${JSON.stringify(data)}`);
  } catch (error) {
    logMessage(`Ошибка отправки метрики: ${error.message}`);
  }
});

document.getElementById("stats-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  const token = document.getElementById("stats-token").value.trim();

  if (!token) {
    logMessage("Ошибка: укажите token для получения метрик.");
    return;
  }
  const tokenStatusForStats = getTokenStatus(token);
  if (tokenStatusForStats === "expired") {
    logMessage("Ошибка: токен истек. Выполните auth повторно.");
    return;
  }
  if (tokenStatusForStats === "invalid") {
    logMessage("Ошибка: токен невалидный. Выполните auth повторно.");
    return;
  }

  let from;
  let to;
  try {
    from = encodeURIComponent(formatToApiDateTime(document.getElementById("stats-from").value.trim()));
    to = encodeURIComponent(formatToApiDateTime(document.getElementById("stats-to").value.trim()));
  } catch (error) {
    logMessage(`Ошибка времени: ${error.message}`);
    return;
  }

  try {
    const data = await request(`metrics?from=${from}&to=${to}`, {
      method: "GET",
      headers: {
        "Authorization": `Bearer ${token}`
      }
    });
    statsOutputEl.textContent = JSON.stringify(data, null, 2);
    logMessage("Агрегация получена.");
  } catch (error) {
    statsOutputEl.textContent = "";
    logMessage(`Ошибка получения агрегатов: ${error.message}`);
  }
});
