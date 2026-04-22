const adminApiBase = resolveAdminApiBase();
const adminPasswordStorageKey = "database-mcp-admin-password";

const statusEl = document.getElementById("status");
const baseConfigTableBody = document.getElementById("baseConfigTableBody");
const datasourceTableBody = document.getElementById("datasourceTableBody");
const baseConfigForm = document.getElementById("baseConfigForm");
const datasourceForm = document.getElementById("datasourceForm");
const baseConfigSearchInput = document.getElementById("baseConfigSearch");
const datasourceSearchInput = document.getElementById("datasourceSearch");
const baseConfigSelect = document.getElementById("baseConfigSelect");
const datasourceSchemaHint = document.getElementById("datasourceSchemaHint");
const navItems = document.querySelectorAll(".nav-item");
const panels = document.querySelectorAll(".panel-view");

let currentBaseConfigs = [];
let currentDatasources = [];

document.getElementById("reloadButton").addEventListener("click", () => loadConfig("配置已刷新"));
document.getElementById("cancelBaseEditButton").addEventListener("click", resetBaseConfigForm);
document.getElementById("cancelDatasourceEditButton").addEventListener("click", resetDatasourceForm);
baseConfigSearchInput.addEventListener("input", () => renderBaseConfigs(currentBaseConfigs));
datasourceSearchInput.addEventListener("input", () => renderDatasources(currentDatasources, currentBaseConfigs));
baseConfigSelect.addEventListener("change", updateDatasourceSchemaHint);
navItems.forEach((item) => item.addEventListener("click", () => switchView(item.dataset.view)));

baseConfigForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  try {
    const form = new FormData(baseConfigForm);
    const id = String(form.get("id")).trim();
    await apiFetch(`/base-configs/${encodeURIComponent(id)}`, {
      method: "PUT",
      body: JSON.stringify({
        type: form.get("type"),
        host: form.get("host"),
        port: Number(form.get("port")),
        databaseName: form.get("databaseName"),
        sid: form.get("sid"),
        jdbcParams: form.get("jdbcParams")
      })
    });
    resetBaseConfigForm();
    await loadConfig("基础 JDBC 配置已保存");
  } catch (error) {
    setStatus(error.message, true);
  }
});

datasourceForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  try {
    const form = new FormData(datasourceForm);
    const id = String(form.get("id")).trim();
    await apiFetch(`/datasources/${encodeURIComponent(id)}`, {
      method: "PUT",
      body: JSON.stringify({
        baseConfigId: form.get("baseConfigId"),
        username: form.get("username"),
        password: form.get("password"),
        schema: form.get("schema")
      })
    });
    resetDatasourceForm();
    await loadConfig("数据源映射已保存");
  } catch (error) {
    setStatus(error.message, true);
  }
});

function ensureAdminPassword(forcePrompt = false) {
  let password = window.localStorage.getItem(adminPasswordStorageKey);
  if (!password || forcePrompt) {
    const today = new Date().toISOString().slice(0, 10);
    const input = window.prompt("请输入管理后台口令，默认是今天日期（yyyy-MM-dd）", password || today);
    if (input && input.trim()) {
      password = input.trim();
      window.localStorage.setItem(adminPasswordStorageKey, password);
      setStatus("管理口令已更新");
    }
  }
  return password || "";
}

async function loadConfig(successMessage) {
  try {
    const snapshot = await apiFetch("/config");
    currentBaseConfigs = snapshot.baseConfigs || [];
    currentDatasources = snapshot.datasources || [];
    renderBaseConfigOptions(currentBaseConfigs);
    renderBaseConfigs(currentBaseConfigs);
    renderDatasources(currentDatasources, currentBaseConfigs);
    updateDatasourceSchemaHint();
    setStatus(successMessage || `已加载 ${currentBaseConfigs.length} 个基础配置，${currentDatasources.length} 个数据源`);
  } catch (error) {
    setStatus(error.message, true);
  }
}

function renderBaseConfigOptions(baseConfigs) {
  const currentValue = datasourceForm.elements.baseConfigId.value;
  baseConfigSelect.innerHTML = '<option value="">请选择基础配置别名</option>';
  for (const item of baseConfigs) {
    const option = document.createElement("option");
    option.value = item.id;
    option.textContent = `${item.id} | ${normalizeType(item.type)} | ${item.host}:${item.port}`;
    baseConfigSelect.appendChild(option);
  }
  if (baseConfigs.some((item) => item.id === currentValue)) {
    datasourceForm.elements.baseConfigId.value = currentValue;
  }
}

function renderBaseConfigs(baseConfigs) {
  const keyword = baseConfigSearchInput.value.trim().toLowerCase();
  baseConfigTableBody.innerHTML = "";
  for (const item of baseConfigs.filter((config) => matchesBaseConfig(config, keyword))) {
    const target = normalizeType(item.type) === "postgres" ? (item.databaseName || "-") : (item.sid || "-");
    const row = document.createElement("tr");
    row.innerHTML = `
      <td>${escapeHtml(item.id)}</td>
      <td>${escapeHtml(normalizeType(item.type))}</td>
      <td>${escapeHtml(item.host)}</td>
      <td>${escapeHtml(item.port)}</td>
      <td>${escapeHtml(target)}</td>
      <td>${escapeHtml(item.jdbcParams || "-")}</td>
      <td>
        <div class="row-actions">
          <button class="button ghost" type="button" data-action="edit-base">编辑</button>
          <button class="button danger" type="button" data-action="delete-base">删除</button>
        </div>
      </td>
    `;

    row.querySelector("[data-action='edit-base']").addEventListener("click", () => {
      fillBaseConfigForm(item);
      setStatus(`正在编辑基础配置 ${item.id}`);
    });

    row.querySelector("[data-action='delete-base']").addEventListener("click", async () => {
      try {
        await apiFetch(`/base-configs/${encodeURIComponent(item.id)}`, { method: "DELETE" });
        if (String(baseConfigForm.elements.id.value).trim() === item.id) {
          resetBaseConfigForm();
        }
        if (String(datasourceForm.elements.baseConfigId.value).trim() === item.id) {
          datasourceForm.elements.baseConfigId.value = "";
        }
        await loadConfig("基础 JDBC 配置已删除");
      } catch (error) {
        setStatus(error.message, true);
      }
    });

    baseConfigTableBody.appendChild(row);
  }
}

function renderDatasources(datasources, baseConfigs) {
  const keyword = datasourceSearchInput.value.trim().toLowerCase();
  datasourceTableBody.innerHTML = "";
  const baseMap = new Map(baseConfigs.map((item) => [item.id, item]));
  for (const item of datasources.filter((config) => matchesDatasource(config, keyword))) {
    const base = baseMap.get(item.baseConfigId);
    const resolvedTarget = buildResolvedTarget(base, item);
    const schemaMode = item.schema || "跟随连接";
    const row = document.createElement("tr");
    row.innerHTML = `
      <td>${escapeHtml(item.id)}</td>
      <td>${escapeHtml(item.baseConfigId)}</td>
      <td>${escapeHtml(item.username || "-")}</td>
      <td>${escapeHtml(schemaMode)}</td>
      <td>${escapeHtml(resolvedTarget)}</td>
      <td>
        <div class="row-actions">
          <button class="button ghost" type="button" data-action="test-ds">测试</button>
          <button class="button ghost" type="button" data-action="edit-ds">编辑</button>
          <button class="button danger" type="button" data-action="delete-ds">删除</button>
        </div>
      </td>
    `;

    row.querySelector("[data-action='test-ds']").addEventListener("click", async () => {
      try {
        const result = await apiFetch(`/datasources/${encodeURIComponent(item.id)}/test`);
        setStatus(result.message, !result.success);
      } catch (error) {
        setStatus(error.message, true);
      }
    });

    row.querySelector("[data-action='edit-ds']").addEventListener("click", () => {
      fillDatasourceForm(item);
      setStatus(`正在编辑数据源 ${item.id}`);
    });

    row.querySelector("[data-action='delete-ds']").addEventListener("click", async () => {
      try {
        await apiFetch(`/datasources/${encodeURIComponent(item.id)}`, { method: "DELETE" });
        if (String(datasourceForm.elements.id.value).trim() === item.id) {
          resetDatasourceForm();
        }
        await loadConfig("数据源映射已删除");
      } catch (error) {
        setStatus(error.message, true);
      }
    });

    datasourceTableBody.appendChild(row);
  }
}

function matchesBaseConfig(item, keyword) {
  if (!keyword) {
    return true;
  }
  return [item.id, item.type, item.host, item.databaseName, item.sid, item.jdbcParams]
    .some((value) => String(value || "").toLowerCase().includes(keyword));
}

function matchesDatasource(item, keyword) {
  if (!keyword) {
    return true;
  }
  return [item.id, item.baseConfigId, item.username]
    .some((value) => String(value || "").toLowerCase().includes(keyword));
}

function fillBaseConfigForm(item) {
  baseConfigForm.elements.id.value = item.id || "";
  baseConfigForm.elements.type.value = normalizeType(item.type);
  baseConfigForm.elements.host.value = item.host || "";
  baseConfigForm.elements.port.value = item.port || "";
  baseConfigForm.elements.databaseName.value = item.databaseName || "";
  baseConfigForm.elements.sid.value = item.sid || "";
  baseConfigForm.elements.jdbcParams.value = item.jdbcParams || "";
}

function fillDatasourceForm(item) {
  datasourceForm.elements.id.value = item.id || "";
  datasourceForm.elements.baseConfigId.value = item.baseConfigId || "";
  datasourceForm.elements.username.value = item.username || "";
  datasourceForm.elements.password.value = item.password || "";
  datasourceForm.elements.schema.value = item.schema || "";
  updateDatasourceSchemaHint();
}

function resetBaseConfigForm() {
  baseConfigForm.reset();
  baseConfigForm.elements.type.value = "postgres";
}

function resetDatasourceForm() {
  datasourceForm.reset();
  datasourceForm.elements.baseConfigId.value = "";
  updateDatasourceSchemaHint();
}

async function apiFetch(path, options = {}) {
  const password = ensureAdminPassword(false);
  const response = await fetch(`${adminApiBase}${path}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      "X-Admin-Password": password,
      ...(options.headers || {})
    }
  });

  if (response.status === 401) {
    ensureAdminPassword(true);
    throw new Error("管理口令无效，请重新输入");
  }

  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `请求失败: ${response.status}`);
  }

  if (response.status === 204) {
    return null;
  }
  return response.json();
}

function setStatus(message, isError = false) {
  statusEl.textContent = message;
  statusEl.style.color = isError ? "#ff7b86" : "";
}

function resolveAdminApiBase() {
  const currentUrl = new URL(window.location.href);
  const adminPath = currentUrl.pathname.replace(/\/[^/]*$/, "");
  return `${adminPath}/api`;
}

function switchView(view) {
  navItems.forEach((item) => item.classList.toggle("active", item.dataset.view === view));
  panels.forEach((panel) => panel.classList.toggle("hidden", panel.dataset.panel !== view));
}

function normalizeType(value) {
  return String(value || "").toLowerCase();
}

function buildResolvedTarget(base, datasource) {
  if (!base) {
    return "基础配置不存在";
  }

  const type = normalizeType(base.type);
  const targetName = base.databaseName || base.sid || "-";
  const baseTarget = `${type}://${base.host}:${base.port}/${targetName}`;

  if (type !== "postgres") {
    return baseTarget;
  }

  const params = new URLSearchParams(base.jdbcParams || "");
  if (datasource?.schema) {
    params.set("currentSchema", datasource.schema);
  }

  const query = params.toString();
  return query ? `${baseTarget}?${query}` : baseTarget;
}

function updateDatasourceSchemaHint() {
  if (!datasourceSchemaHint) {
    return;
  }
  const base = currentBaseConfigs.find((item) => item.id === datasourceForm.elements.baseConfigId.value);
  const type = normalizeType(base?.type);
  if (type === "oracle") {
    datasourceSchemaHint.textContent = "Oracle 数据源通常应留空 schema。只有明确需要 ALTER SESSION SET CURRENT_SCHEMA 时才填写。";
    return;
  }
  if (type === "postgres") {
    datasourceSchemaHint.textContent = "PostgreSQL 通常可由 JDBC URL、search_path 或连接默认 schema 决定。只有需要显式覆盖时才填写。";
    return;
  }
  datasourceSchemaHint.textContent = "schema 是高级选项。默认建议留空，让连接自身决定当前 schema。";
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

resetBaseConfigForm();
resetDatasourceForm();
switchView("base");
loadConfig();
