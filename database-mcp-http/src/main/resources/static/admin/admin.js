/* ===================================================================
   Database MCP Admin — JavaScript
   =================================================================== */
(function () {
  "use strict";

  /* =========================  CONFIG  ========================= */
  var adminApiBase = resolveAdminApiBase();
  var STORAGE_KEY = "database-mcp-admin-password";

  /* =========================  DOM  ========================= */
  var $id = function (id) { return document.getElementById(id); };

  var statusEl       = $id("status");
  var statusTextEl   = statusEl ? statusEl.querySelector("span") : null;

  var baseTableBody  = $id("baseConfigTableBody");
  var dsTableBody    = $id("datasourceTableBody");

  var baseForm       = $id("baseConfigForm");
  var dsForm         = $id("datasourceForm");

  var baseSearchInput = $id("baseConfigSearch");
  var dsSearchInput   = $id("datasourceSearch");
  var baseConfigSelect = $id("baseConfigSelect");

  var baseDatabaseField = $id("baseDatabaseField");
  var baseSidField      = $id("baseSidField");
  var basePortInput     = $id("basePortInput");
  var baseJdbcInput     = $id("baseJdbcInput");
  var baseTypeSelect    = $id("baseTypeSelect");

  var dsSchemaField     = $id("datasourceSchemaField");
  var dsSchemaHint      = $id("datasourceSchemaHint");
  var dsDbTypeBadge     = $id("datasourceDbTypeBadge");

  var baseCountEl = $id("baseCount");
  var dsCountEl   = $id("dsCount");

  var baseModal      = $id("baseModal");
  var baseModalTitle = $id("baseModalTitle");
  var dsModal        = $id("dsModal");
  var dsModalTitle   = $id("dsModalTitle");

  var toastEl     = $id("toast");
  var toastTextEl = $id("toastText");
  var toastIcon   = toastEl ? toastEl.querySelector("i") : null;
  var toastTimer  = null;

  var confirmOverlay = $id("confirmOverlay");
  var confirmTitleEl = $id("confirmTitle");
  var confirmMsgEl   = $id("confirmMessage");
  var confirmResolve = null;

  /* =========================  STATE  ========================= */
  var currentBaseConfigs = [];
  var currentDatasources = [];

  /* =========================================================
     EVENT BINDINGS
     ========================================================= */

  /* --- Tab navigation (event delegation) --- */
  var tabNav = $id("tabNav");
  if (tabNav) {
    tabNav.addEventListener("click", function (e) {
      var btn = e.target.closest(".UnderlineNav-item");
      if (btn && btn.dataset.view) {
        switchView(btn.dataset.view);
      }
    });
  }

  /* --- Reload --- */
  bind("reloadButton", "click", function () { loadConfig("配置已刷新"); });

  /* --- Create buttons → open empty modal --- */
  bind("createBaseBtn", "click", function () {
    resetBaseForm();
    baseModalTitle.textContent = "Create Base Profile";
    baseForm.elements.id.removeAttribute("readonly");
    openModal(baseModal);
  });

  bind("createDsBtn", "click", function () {
    resetDsForm();
    dsModalTitle.textContent = "Create Datasource";
    dsForm.elements.id.removeAttribute("readonly");
    openModal(dsModal);
  });

  /* --- Search (client-side filter) --- */
  if (baseSearchInput) {
    baseSearchInput.addEventListener("input", function () {
      renderBaseConfigs(currentBaseConfigs);
    });
  }
  if (dsSearchInput) {
    dsSearchInput.addEventListener("input", function () {
      renderDatasources(currentDatasources, currentBaseConfigs);
    });
  }

  /* --- Type switch in base form --- */
  if (baseTypeSelect) {
    baseTypeSelect.addEventListener("change", updateBaseFormByType);
  }

  /* --- Base profile switch in datasource form --- */
  if (baseConfigSelect) {
    baseConfigSelect.addEventListener("change", updateDsFormByType);
  }

  /* --- Modal close: all .js-modal-close buttons --- */
  document.addEventListener("click", function (e) {
    /* Close button inside a data-modal overlay */
    if (e.target.closest(".js-modal-close")) {
      var overlay = e.target.closest("[data-modal]");
      if (overlay) closeModal(overlay);
      return;
    }
    /* Click on backdrop itself */
    var backdrop = e.target;
    if (backdrop.hasAttribute && backdrop.hasAttribute("data-modal")) {
      closeModal(backdrop);
      return;
    }
  });

  /* --- Confirm dialog --- */
  document.addEventListener("click", function (e) {
    if (e.target.closest(".js-confirm-cancel")) {
      resolveConfirm(false);
      return;
    }
    if (e.target.closest(".js-confirm-ok")) {
      resolveConfirm(true);
      return;
    }
    /* Click backdrop of confirm */
    if (e.target === confirmOverlay) {
      resolveConfirm(false);
    }
  });

  /* --- Base form submit --- */
  if (baseForm) {
    baseForm.addEventListener("submit", function (e) {
      e.preventDefault();
      submitBaseForm();
    });
  }

  /* --- Datasource form submit --- */
  if (dsForm) {
    dsForm.addEventListener("submit", function (e) {
      e.preventDefault();
      submitDsForm();
    });
  }

  /* =========================================================
     FORM SUBMISSION
     ========================================================= */

  function submitBaseForm() {
    var fd = new FormData(baseForm);
    var id = String(fd.get("id") || "").trim();
    if (!id) { showToast("基础配置别名不能为空", true); return; }

    var type = norm(fd.get("type"));
    var payload = {
      type: type,
      host: String(fd.get("host") || "").trim(),
      port: Number(fd.get("port")),
      databaseName: type === "postgres" ? String(fd.get("databaseName") || "").trim() : "",
      sid: type === "oracle" ? String(fd.get("sid") || "").trim() : "",
      jdbcParams: String(fd.get("jdbcParams") || "").trim()
    };

    apiFetch("/base-configs/" + encodeURIComponent(id), {
      method: "PUT",
      body: JSON.stringify(payload)
    }).then(function () {
      closeModal(baseModal);
      return loadConfig("基础 JDBC 配置已保存：" + id);
    }).catch(function (err) {
      showToast(err.message, true);
    });
  }

  function submitDsForm() {
    var fd = new FormData(dsForm);
    var id = String(fd.get("id") || "").trim();
    if (!id) { showToast("Datasource ID 不能为空", true); return; }

    var baseId = String(fd.get("baseConfigId") || "").trim();
    var selBase = currentBaseConfigs.find(function (c) { return c.id === baseId; });
    var payload = {
      baseConfigId: baseId,
      username: String(fd.get("username") || "").trim(),
      password: String(fd.get("password") || "").trim(),
      schema: norm(selBase && selBase.type) === "oracle" ? "" : String(fd.get("schema") || "").trim()
    };

    apiFetch("/datasources/" + encodeURIComponent(id), {
      method: "PUT",
      body: JSON.stringify(payload)
    }).then(function () {
      closeModal(dsModal);
      return loadConfig("数据源映射已保存：" + id);
    }).catch(function (err) {
      showToast(err.message, true);
    });
  }

  /* =========================================================
     MODAL
     ========================================================= */

  function openModal(el) {
    if (!el) return;
    el.classList.add("show");
    document.body.style.overflow = "hidden";
  }

  function closeModal(el) {
    if (!el) return;
    el.classList.remove("show");
    if (!document.querySelector(".Overlay-backdrop.show")) {
      document.body.style.overflow = "";
    }
  }

  /* =========================================================
     TOAST
     ========================================================= */

  function showToast(msg, isError) {
    if (toastTimer) clearTimeout(toastTimer);
    if (toastTextEl) toastTextEl.textContent = msg;
    if (toastEl) {
      toastEl.classList.toggle("error", !!isError);
      if (toastIcon) toastIcon.className = isError ? "bi bi-exclamation-triangle" : "bi bi-check-circle";
      toastEl.classList.add("show");
      toastTimer = setTimeout(function () { toastEl.classList.remove("show"); }, isError ? 5000 : 3000);
    }
    setStatus(msg, isError);
  }

  function setStatus(msg, isError) {
    if (statusEl) statusEl.classList.toggle("error", !!isError);
    if (statusTextEl) statusTextEl.textContent = msg;
  }

  /* =========================================================
     CONFIRM DIALOG
     ========================================================= */

  function showConfirm(title, message) {
    if (confirmTitleEl) confirmTitleEl.textContent = title;
    if (confirmMsgEl) confirmMsgEl.textContent = message;
    if (confirmOverlay) {
      confirmOverlay.classList.add("show");
      document.body.style.overflow = "hidden";
    }
    return new Promise(function (resolve) {
      confirmResolve = resolve;
    });
  }

  function resolveConfirm(result) {
    if (confirmOverlay) confirmOverlay.classList.remove("show");
    if (!document.querySelector(".Overlay-backdrop.show")) {
      document.body.style.overflow = "";
    }
    if (confirmResolve) {
      var fn = confirmResolve;
      confirmResolve = null;
      fn(result);
    }
  }

  /* =========================================================
     API & AUTH
     ========================================================= */

  function ensurePassword(force) {
    var pw = window.localStorage.getItem(STORAGE_KEY);
    if (!pw || force) {
      var today = new Date().toISOString().slice(0, 10);
      var input = window.prompt("请输入管理后台口令（留空时默认可尝试当天日期，格式 yyyy-MM-dd）", pw || today);
      if (input && input.trim()) {
        pw = input.trim();
        window.localStorage.setItem(STORAGE_KEY, pw);
        showToast("管理口令已更新");
      }
    }
    return pw || "";
  }

  function apiFetch(path, opts) {
    opts = opts || {};
    var pw = ensurePassword(false);
    var headers = Object.assign({ "Content-Type": "application/json" }, opts.headers || {});
    if (pw) headers["X-Admin-Password"] = pw;

    return fetch(adminApiBase + path, Object.assign({}, opts, { headers: headers }))
      .then(function (res) {
        if (res.status === 401) {
          ensurePassword(true);
          throw new Error("管理口令无效，请重新输入后重试");
        }
        if (!res.ok) {
          return res.text().then(function (t) { throw new Error(t || "请求失败: " + res.status); });
        }
        if (res.status === 204) return null;
        return res.json();
      });
  }

  /* =========================================================
     DATA LOADING
     ========================================================= */

  function loadConfig(successMsg) {
    return apiFetch("/config").then(function (snap) {
      currentBaseConfigs = (snap && snap.baseConfigs) || [];
      currentDatasources = (snap && snap.datasources) || [];

      renderBaseOptions(currentBaseConfigs);
      renderBaseConfigs(currentBaseConfigs);
      renderDatasources(currentDatasources, currentBaseConfigs);
      updateCounters();

      if (successMsg) {
        showToast(successMsg);
      } else {
        setStatus("已加载 " + currentBaseConfigs.length + " 条基础配置，" + currentDatasources.length + " 条数据源映射");
      }
    }).catch(function (err) {
      showToast(err.message, true);
    });
  }

  function updateCounters() {
    if (baseCountEl) baseCountEl.textContent = currentBaseConfigs.length;
    if (dsCountEl) dsCountEl.textContent = currentDatasources.length;
  }

  function renderBaseOptions(list) {
    if (!baseConfigSelect) return;
    var cur = dsForm ? dsForm.elements.baseConfigId.value : "";
    baseConfigSelect.innerHTML = '<option value="">请选择基础配置</option>';
    list.forEach(function (c) {
      var o = document.createElement("option");
      o.value = c.id;
      o.textContent = c.id + " | " + norm(c.type) + " | " + c.host + ":" + c.port;
      baseConfigSelect.appendChild(o);
    });
    if (list.some(function (c) { return c.id === cur; })) {
      baseConfigSelect.value = cur;
    }
  }

  /* =========================================================
     RENDER TABLES
     ========================================================= */

  function renderBaseConfigs(list) {
    if (!baseTableBody) return;
    var kw = baseSearchInput ? baseSearchInput.value.trim().toLowerCase() : "";
    baseTableBody.innerHTML = "";

    var filtered = list.filter(function (c) { return matchBase(c, kw); });

    if (filtered.length === 0) {
      emptyRow(baseTableBody, 7, '暂无基础配置，点击 "Create Profile" 开始创建。');
      return;
    }

    filtered.forEach(function (item) {
      var type = norm(item.type);
      var target = type === "postgres" ? (item.databaseName || "-") : (item.sid || "-");
      var tr = document.createElement("tr");

      tr.innerHTML =
        '<td data-label="ID">' + esc(item.id) + '</td>' +
        '<td data-label="Type"><span class="Label Label--accent">' + esc(type) + '</span></td>' +
        '<td data-label="Host">' + esc(item.host) + '</td>' +
        '<td data-label="Port">' + esc(item.port) + '</td>' +
        '<td data-label="Target">' + esc(target) + '</td>' +
        '<td data-label="JDBC Params">' + esc(item.jdbcParams || "-") + '</td>' +
        '<td data-label="Actions">' +
          '<div class="row-actions">' +
            '<button class="btn btn-sm btn-icon" type="button" data-act="edit" title="编辑"><i class="bi bi-pencil"></i></button>' +
            '<button class="btn btn-sm btn-icon icon-danger" type="button" data-act="del" title="删除"><i class="bi bi-trash3"></i></button>' +
          '</div>' +
        '</td>';

      tr.querySelector('[data-act="edit"]').addEventListener("click", function () {
        fillBaseForm(item);
        baseModalTitle.textContent = "Edit Base Profile: " + item.id;
        baseForm.elements.id.setAttribute("readonly", "readonly");
        openModal(baseModal);
      });

      tr.querySelector('[data-act="del"]').addEventListener("click", function () {
        showConfirm("删除基础配置", '确认删除基础配置 "' + item.id + '"？此操作不可撤销。').then(function (ok) {
          if (!ok) return;
          apiFetch("/base-configs/" + encodeURIComponent(item.id), { method: "DELETE" })
            .then(function () { return loadConfig("基础 JDBC 配置已删除：" + item.id); })
            .catch(function (err) { showToast(err.message, true); });
        });
      });

      baseTableBody.appendChild(tr);
    });
  }

  function renderDatasources(dsList, baseList) {
    if (!dsTableBody) return;
    var kw = dsSearchInput ? dsSearchInput.value.trim().toLowerCase() : "";
    dsTableBody.innerHTML = "";

    var baseMap = {};
    baseList.forEach(function (c) { baseMap[c.id] = c; });

    var filtered = dsList.filter(function (c) { return matchDs(c, kw); });

    if (filtered.length === 0) {
      emptyRow(dsTableBody, 6, '暂无数据源映射，点击 "Create Datasource" 开始创建。');
      return;
    }

    filtered.forEach(function (item) {
      var base = baseMap[item.baseConfigId];
      var resolved = buildTarget(base, item);
      var schema = item.schema || "跟随连接";

      var tr = document.createElement("tr");
      tr.innerHTML =
        '<td data-label="Datasource ID">' + esc(item.id) + '</td>' +
        '<td data-label="Base Profile"><span class="Label Label--accent">' + esc(item.baseConfigId) + '</span></td>' +
        '<td data-label="Username">' + esc(item.username || "-") + '</td>' +
        '<td data-label="Schema">' + esc(schema) + '</td>' +
        '<td data-label="Resolved Target">' + esc(resolved) + '</td>' +
        '<td data-label="Actions">' +
          '<div class="row-actions">' +
            '<button class="btn btn-sm btn-icon icon-success" type="button" data-act="test" title="测试连接"><i class="bi bi-plug"></i></button>' +
            '<button class="btn btn-sm btn-icon" type="button" data-act="edit" title="编辑"><i class="bi bi-pencil"></i></button>' +
            '<button class="btn btn-sm btn-icon icon-danger" type="button" data-act="del" title="删除"><i class="bi bi-trash3"></i></button>' +
          '</div>' +
        '</td>';

      tr.querySelector('[data-act="test"]').addEventListener("click", function () {
        apiFetch("/datasources/" + encodeURIComponent(item.id) + "/test")
          .then(function (r) { showToast(r.message, !r.success); })
          .catch(function (err) { showToast(err.message, true); });
      });

      tr.querySelector('[data-act="edit"]').addEventListener("click", function () {
        fillDsForm(item);
        dsModalTitle.textContent = "Edit Datasource: " + item.id;
        dsForm.elements.id.setAttribute("readonly", "readonly");
        openModal(dsModal);
      });

      tr.querySelector('[data-act="del"]').addEventListener("click", function () {
        showConfirm("删除数据源", '确认删除数据源 "' + item.id + '"？此操作不可撤销。').then(function (ok) {
          if (!ok) return;
          apiFetch("/datasources/" + encodeURIComponent(item.id), { method: "DELETE" })
            .then(function () { return loadConfig("数据源映射已删除：" + item.id); })
            .catch(function (err) { showToast(err.message, true); });
        });
      });

      dsTableBody.appendChild(tr);
    });
  }

  /* =========================================================
     FILTERS
     ========================================================= */

  function matchBase(c, kw) {
    if (!kw) return true;
    return [c.id, c.type, c.host, c.databaseName, c.sid, c.jdbcParams]
      .some(function (v) { return String(v || "").toLowerCase().indexOf(kw) !== -1; });
  }

  function matchDs(c, kw) {
    if (!kw) return true;
    return [c.id, c.baseConfigId, c.username]
      .some(function (v) { return String(v || "").toLowerCase().indexOf(kw) !== -1; });
  }

  /* =========================================================
     FORM HELPERS
     ========================================================= */

  function fillBaseForm(item) {
    baseForm.elements.id.value = item.id || "";
    baseForm.elements.type.value = norm(item.type);
    baseForm.elements.host.value = item.host || "";
    baseForm.elements.port.value = item.port || "";
    baseForm.elements.databaseName.value = item.databaseName || "";
    baseForm.elements.sid.value = item.sid || "";
    baseForm.elements.jdbcParams.value = item.jdbcParams || "";
    updateBaseFormByType();
  }

  function fillDsForm(item) {
    dsForm.elements.id.value = item.id || "";
    dsForm.elements.baseConfigId.value = item.baseConfigId || "";
    dsForm.elements.username.value = item.username || "";
    dsForm.elements.password.value = item.password || "";
    dsForm.elements.schema.value = item.schema || "";
    updateDsFormByType();
  }

  function resetBaseForm() {
    baseForm.reset();
    baseForm.elements.type.value = "postgres";
    updateBaseFormByType();
  }

  function resetDsForm() {
    dsForm.reset();
    if (baseConfigSelect) baseConfigSelect.value = "";
    updateDsFormByType();
  }

  /**
   * Toggle Base Config form fields based on database type.
   *  - PostgreSQL → Database Name visible, SID hidden, port 5432
   *  - Oracle     → SID visible, Database Name hidden, port 1521
   */
  function updateBaseFormByType() {
    var type = norm(baseForm.elements.type.value);
    var isPg = type === "postgres";

    if (baseDatabaseField) baseDatabaseField.classList.toggle("hidden", !isPg);
    if (baseSidField) baseSidField.classList.toggle("hidden", isPg);

    baseForm.elements.databaseName.required = isPg;
    baseForm.elements.sid.required = !isPg;

    if (isPg) {
      baseForm.elements.sid.value = "";
    } else {
      baseForm.elements.databaseName.value = "";
    }

    if (basePortInput) basePortInput.placeholder = isPg ? "5432" : "1521";
    if (baseJdbcInput) baseJdbcInput.placeholder = isPg
      ? "applicationName=database-mcp-http&connectTimeout=10"
      : "oracle.net.CONNECT_TIMEOUT=10000";
  }

  /**
   * Toggle Datasource form fields based on selected base profile type.
   *  - Oracle     → Schema hidden
   *  - PostgreSQL → Schema visible with hint
   */
  function updateDsFormByType() {
    var hintSpan = dsSchemaHint ? dsSchemaHint.querySelector("span") : null;
    if (!hintSpan) return;

    var selId = dsForm.elements.baseConfigId.value;
    var base = currentBaseConfigs.find(function (c) { return c.id === selId; });
    var type = norm(base ? base.type : "");

    if (!selId || !base) {
      if (dsSchemaField) dsSchemaField.classList.remove("hidden");
      dsForm.elements.schema.required = false;
      hintSpan.textContent = "schema 是高级选项，默认建议留空，由连接或数据库默认策略决定。";
      if (dsDbTypeBadge) { dsDbTypeBadge.textContent = "DB Type: —"; dsDbTypeBadge.className = "Label Label--secondary"; }
      return;
    }

    if (dsDbTypeBadge) { dsDbTypeBadge.textContent = "DB Type: " + type; dsDbTypeBadge.className = "Label Label--accent"; }

    if (type === "oracle") {
      if (dsSchemaField) dsSchemaField.classList.add("hidden");
      dsForm.elements.schema.value = "";
      dsForm.elements.schema.required = false;
      hintSpan.textContent = "Oracle 通常不需要 schema 覆盖，当前已自动隐藏该字段。";
      return;
    }

    if (dsSchemaField) dsSchemaField.classList.remove("hidden");
    dsForm.elements.schema.required = false;
    hintSpan.textContent = type === "postgres"
      ? "PostgreSQL 可按需填写 schema；留空时沿用 JDBC URL 或数据库默认策略。"
      : "当前数据库支持 schema 覆盖，建议仅在明确需要时填写。";
  }

  /* =========================================================
     NAVIGATION
     ========================================================= */

  function switchView(view) {
    /* Update tabs */
    var tabs = document.querySelectorAll(".UnderlineNav-item");
    for (var i = 0; i < tabs.length; i++) {
      tabs[i].classList.toggle("selected", tabs[i].getAttribute("data-view") === view);
    }
    /* Update panels */
    var pans = document.querySelectorAll(".panel-view");
    for (var j = 0; j < pans.length; j++) {
      pans[j].classList.toggle("active", pans[j].getAttribute("data-panel") === view);
    }
  }

  /* =========================================================
     UTILITIES
     ========================================================= */

  function resolveAdminApiBase() {
    var cur = new URL(window.location.href);
    var p = cur.pathname.replace(/\/[^/]*$/, "");
    return p + "/api";
  }

  function norm(v) { return String(v || "").toLowerCase(); }

  function buildTarget(base, ds) {
    if (!base) return "基础配置不存在";
    var type = norm(base.type);
    var name = base.databaseName || base.sid || "-";
    var t = type + "://" + base.host + ":" + base.port + "/" + name;
    if (type !== "postgres") return t;
    var params = new URLSearchParams(base.jdbcParams || "");
    if (ds && ds.schema) params.set("currentSchema", ds.schema);
    var q = params.toString();
    return q ? t + "?" + q : t;
  }

  function emptyRow(tbody, cols, msg) {
    var tr = document.createElement("tr");
    var td = document.createElement("td");
    td.colSpan = cols;
    td.textContent = msg;
    td.className = "empty-row";
    tr.appendChild(td);
    tbody.appendChild(tr);
  }

  function esc(v) {
    return String(v).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#39;");
  }

  function bind(id, evt, fn) {
    var el = $id(id);
    if (el) el.addEventListener(evt, fn);
  }

  /* =========================================================
     INIT
     ========================================================= */

  resetBaseForm();
  resetDsForm();
  switchView("base");
  loadConfig();

})();
