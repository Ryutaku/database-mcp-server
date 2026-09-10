/* ===================================================================
   Database MCP Admin — JavaScript
   =================================================================== */
(function () {
  "use strict";

  /* =========================  CONFIG  ========================= */
  var adminApiBase = resolveAdminApiBase();
  var USER_STORAGE_KEY = "database-mcp-admin-user";
  var PASSWORD_STORAGE_KEY = "database-mcp-admin-password";

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
  var dsSchemaLabel     = $id("datasourceSchemaLabel");
  var dsSchemaInput     = $id("datasourceSchemaInput");
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

  var basePagerEl   = $id("basePager");
  var basePageInfo  = $id("basePageInfo");
  var dsPagerEl     = $id("dsPager");
  var dsPageInfo    = $id("dsPageInfo");

  var passwordModal = $id("passwordModal");
  var passwordForm  = $id("passwordForm");

  /* =========================  STATE  ========================= */
  var currentBaseConfigs = [];
  var currentDatasources = [];
  var basePage = 1;
  var dsPage = 1;

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

  /* --- Sign out --- */
  bind("logoutButton", "click", function () {
    showConfirm("退出登录", "确认退出当前管理后台登录？").then(function (ok) {
      if (!ok) return;
      clearCredentials();
      redirectToLogin(false);
    });
  });

  /* --- Change password --- */
  bind("changePasswordBtn", "click", function () {
    resetPasswordForm();
    openModal(passwordModal);
  });

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
      basePage = 1;
      renderBaseConfigs(currentBaseConfigs);
    });
  }
  if (dsSearchInput) {
    dsSearchInput.addEventListener("input", function () {
      dsPage = 1;
      renderDatasources(currentDatasources, currentBaseConfigs);
    });
  }

  /* --- Page size selectors --- */
  bind("basePageSize", "change", function () {
    basePage = 1;
    renderBaseConfigs(currentBaseConfigs);
  });
  bind("dsPageSize", "change", function () {
    dsPage = 1;
    renderDatasources(currentDatasources, currentBaseConfigs);
  });

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

  /* --- Change password form submit --- */
  if (passwordForm) {
    passwordForm.addEventListener("submit", function (e) {
      e.preventDefault();
      submitPasswordForm();
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

  function submitPasswordForm() {
    var fd = new FormData(passwordForm);
    var currentPassword = String(fd.get("currentPassword") || "");
    var newPassword = String(fd.get("newPassword") || "");
    var confirmPassword = String(fd.get("confirmPassword") || "");

    if (!currentPassword) { showToast("请输入当前密码", true); return; }
    if (!newPassword) { showToast("请输入新密码", true); return; }
    if (newPassword !== confirmPassword) { showToast("两次输入的新密码不一致", true); return; }
    if (newPassword.length < 8 || newPassword.length > 64) { showToast("密码长度需为 8-64 位", true); return; }

    apiFetch("/password", {
      method: "POST",
      body: JSON.stringify({ currentPassword: currentPassword, newPassword: newPassword })
    }).then(function (r) {
      closeModal(passwordModal);
      savePassword(newPassword);
      showToast((r && r.message) || "密码已更新");
    }).catch(function (err) {
      showToast(err.message, true);
    });
  }

  function resetPasswordForm() {
    if (!passwordForm) return;
    passwordForm.reset();
  }

  /* =========================================================
     MODAL
     ========================================================= */

  function openModal(el) {
    if (!el) return;
    var modal = el.querySelector(".Overlay");
    if (modal) {
      modal.style.transform = ""; // Reset transform on open
      modal._tx = 0;
      modal._ty = 0;
    }
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

  /* --- Modal Drag Logic --- */
  var isDragging = false;
  var dragTarget = null;
  var dragStartX = 0, dragStartY = 0;
  var initialTx = 0, initialTy = 0;

  document.addEventListener("mousedown", function(e) {
    var header = e.target.closest(".Overlay-header");
    if (!header) return;
    if (e.target.closest("button") || e.target.closest(".btn") || e.target.closest("input")) return;

    dragTarget = header.closest(".Overlay");
    if (!dragTarget) return;

    isDragging = true;
    dragStartX = e.clientX;
    dragStartY = e.clientY;

    // Use custom properties to track translation instead of parsing CSS matrices
    initialTx = dragTarget._tx || 0;
    initialTy = dragTarget._ty || 0;

    dragTarget.style.transition = "none";
  });

  document.addEventListener("mousemove", function(e) {
    if (!isDragging || !dragTarget) return;
    
    // Prevent text selection while dragging
    e.preventDefault();

    var dx = e.clientX - dragStartX;
    var dy = e.clientY - dragStartY;
    
    var newTx = initialTx + dx;
    var newTy = initialTy + dy;
    
    dragTarget._tx = newTx;
    dragTarget._ty = newTy;
    
    // Apply new translation inline (this overrides the default CSS scale/translate)
    dragTarget.style.transform = "translate(" + newTx + "px, " + newTy + "px) scale(1)";
  });

  document.addEventListener("mouseup", function() {
    if (isDragging && dragTarget) {
      dragTarget.style.transition = "";
    }
    isDragging = false;
    dragTarget = null;
  });

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

  function getUsername() {
    return window.sessionStorage.getItem(USER_STORAGE_KEY) ||
      window.localStorage.getItem(USER_STORAGE_KEY) || "";
  }

  function getPassword() {
    return window.sessionStorage.getItem(PASSWORD_STORAGE_KEY) ||
      window.localStorage.getItem(PASSWORD_STORAGE_KEY) || "";
  }

  function savePassword(password) {
    var persist = window.localStorage.getItem(PASSWORD_STORAGE_KEY) !== null;
    var storage = persist ? window.localStorage : window.sessionStorage;
    storage.setItem(PASSWORD_STORAGE_KEY, password);
  }

  function clearCredentials() {
    window.localStorage.removeItem(USER_STORAGE_KEY);
    window.localStorage.removeItem(PASSWORD_STORAGE_KEY);
    window.sessionStorage.removeItem(USER_STORAGE_KEY);
    window.sessionStorage.removeItem(PASSWORD_STORAGE_KEY);
  }

  function redirectToLogin(expired) {
    var target = window.location.pathname.split("/").pop() || "index.html";
    var query = "?redirect=" + encodeURIComponent(target);
    if (expired) query += "&reason=expired";
    window.location.replace("login.html" + query);
  }

  function apiFetch(path, opts) {
    opts = opts || {};
    var username = getUsername();
    var pw = getPassword();
    if (!username || !pw) {
      redirectToLogin(false);
      return Promise.reject(new Error("未登录，正在跳转登录页"));
    }

    var headers = Object.assign({ "Content-Type": "application/json" }, opts.headers || {});
    headers["X-Admin-User"] = username;
    headers["X-Admin-Password"] = pw;

    return fetch(adminApiBase + path, Object.assign({}, opts, { headers: headers }))
      .then(function (res) {
        if (res.status === 401) {
          clearCredentials();
          redirectToLogin(true);
          throw new Error("登录状态已失效，请重新登录");
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
      basePage = 1;
      dsPage = 1;

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
    var pageSize = getBasePageSize();
    baseTableBody.innerHTML = "";

    var filtered = list.filter(function (c) { return matchBase(c, kw); });

    if (filtered.length === 0) {
      emptyRow(baseTableBody, 7, '暂无基础配置，点击 "Create Profile" 开始创建。');
      renderPager(basePagerEl, basePageInfo, 0, 1, pageSize, function () {});
      return;
    }

    var totalPages = Math.max(1, Math.ceil(filtered.length / pageSize));
    if (basePage > totalPages) basePage = totalPages;
    if (basePage < 1) basePage = 1;
    var start = (basePage - 1) * pageSize;
    var pageItems = filtered.slice(start, start + pageSize);

    pageItems.forEach(function (item) {
      var type = norm(item.type);
      var target = type === "oracle" ? (item.sid || "-") : (item.databaseName || "-");
      var tr = document.createElement("tr");

      tr.innerHTML =
        '<td data-label="ID" title="' + esc(item.id) + '">' + esc(item.id) + '</td>' +
        '<td data-label="Type" title="' + esc(type) + '"><span class="Label Label--accent">' + esc(type) + '</span></td>' +
        '<td data-label="Host" title="' + esc(item.host) + '">' + esc(item.host) + '</td>' +
        '<td data-label="Port" title="' + esc(item.port) + '">' + esc(item.port) + '</td>' +
        '<td data-label="Target" title="' + esc(target) + '">' + esc(target) + '</td>' +
        '<td data-label="JDBC Params" title="' + esc(item.jdbcParams || "-") + '">' + esc(item.jdbcParams || "-") + '</td>' +
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

    renderPager(basePagerEl, basePageInfo, filtered.length, basePage, pageSize, function (page) {
      basePage = page;
      renderBaseConfigs(currentBaseConfigs);
    });
  }

  function renderDatasources(dsList, baseList) {
    if (!dsTableBody) return;
    var kw = dsSearchInput ? dsSearchInput.value.trim().toLowerCase() : "";
    var pageSize = getDsPageSize();
    dsTableBody.innerHTML = "";

    var baseMap = {};
    baseList.forEach(function (c) { baseMap[c.id] = c; });

    var filtered = dsList.filter(function (c) { return matchDs(c, kw); });

    if (filtered.length === 0) {
      emptyRow(dsTableBody, 6, '暂无数据源映射，点击 "Create Datasource" 开始创建。');
      renderPager(dsPagerEl, dsPageInfo, 0, 1, pageSize, function () {});
      return;
    }

    var totalPages = Math.max(1, Math.ceil(filtered.length / pageSize));
    if (dsPage > totalPages) dsPage = totalPages;
    if (dsPage < 1) dsPage = 1;
    var start = (dsPage - 1) * pageSize;
    var pageItems = filtered.slice(start, start + pageSize);

    pageItems.forEach(function (item) {
      var base = baseMap[item.baseConfigId];
      var resolved = buildTarget(base, item);
      var schema = item.schema || "跟随连接";

      var tr = document.createElement("tr");
      tr.innerHTML =
        '<td data-label="Datasource ID" title="' + esc(item.id) + '">' + esc(item.id) + '</td>' +
        '<td data-label="Base Profile" title="' + esc(item.baseConfigId) + '"><span class="Label Label--accent">' + esc(item.baseConfigId) + '</span></td>' +
        '<td data-label="Username" title="' + esc(item.username || "-") + '">' + esc(item.username || "-") + '</td>' +
        '<td data-label="Schema" title="' + esc(schema) + '">' + esc(schema) + '</td>' +
        '<td data-label="Resolved Target" title="' + esc(resolved) + '">' + esc(resolved) + '</td>' +
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

    renderPager(dsPagerEl, dsPageInfo, filtered.length, dsPage, pageSize, function (page) {
      dsPage = page;
      renderDatasources(currentDatasources, currentBaseConfigs);
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
   *  - PostgreSQL → Database Name visible, SID hidden
   *  - Oracle     → SID visible, Database Name hidden
   *  - MySQL      → both hidden; database is selected per datasource schema or USE
   */
  function updateBaseFormByType() {
    var type = norm(baseForm.elements.type.value);
    var isPg = type === "postgres";
    var isMysql = type === "mysql";
    var usesDatabaseName = isPg;
    var usesSid = type === "oracle";

    if (baseDatabaseField) baseDatabaseField.classList.toggle("hidden", !usesDatabaseName);
    if (baseSidField) baseSidField.classList.toggle("hidden", !usesSid);

    baseForm.elements.databaseName.required = usesDatabaseName;
    baseForm.elements.sid.required = usesSid;

    if (!usesSid) {
      baseForm.elements.sid.value = "";
    }
    if (!usesDatabaseName) {
      baseForm.elements.databaseName.value = "";
    }

    if (basePortInput) basePortInput.placeholder = isPg ? "5432" : (isMysql ? "3306" : "1521");
    if (baseJdbcInput) baseJdbcInput.placeholder = isPg
      ? "applicationName=database-mcp-http&connectTimeout=10"
      : (isMysql ? "useSSL=false&connectTimeout=10000" : "oracle.net.CONNECT_TIMEOUT=10000");
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
      if (dsSchemaLabel) dsSchemaLabel.textContent = "Schema (Optional)";
      if (dsSchemaInput) dsSchemaInput.placeholder = "Only fill when override is required";
      dsForm.elements.schema.required = false;
      hintSpan.textContent = "schema 是高级选项，默认建议留空，由连接或数据库默认策略决定。";
      if (dsDbTypeBadge) { dsDbTypeBadge.textContent = "DB Type: —"; dsDbTypeBadge.className = "Label Label--secondary"; }
      return;
    }

    if (dsDbTypeBadge) { dsDbTypeBadge.textContent = "DB Type: " + type; dsDbTypeBadge.className = "Label Label--accent"; }

    if (type === "oracle") {
      if (dsSchemaField) dsSchemaField.classList.add("hidden");
      if (dsSchemaLabel) dsSchemaLabel.textContent = "Schema (Optional)";
      if (dsSchemaInput) dsSchemaInput.placeholder = "Only fill when override is required";
      dsForm.elements.schema.value = "";
      dsForm.elements.schema.required = false;
      hintSpan.textContent = "Oracle 通常不需要 schema 覆盖，当前已自动隐藏该字段。";
      return;
    }

    if (dsSchemaField) dsSchemaField.classList.remove("hidden");
    dsForm.elements.schema.required = false;
    if (type === "mysql") {
      if (dsSchemaLabel) dsSchemaLabel.textContent = "Default Database (Optional)";
      if (dsSchemaInput) dsSchemaInput.placeholder = "e.g. order_db";
      hintSpan.textContent = "MySQL 在这里填写该 datasource 用户默认进入的库；留空时连接不选择数据库，可后续用 db_switch_schema 切换。";
      return;
    }

    if (dsSchemaLabel) dsSchemaLabel.textContent = "Schema (Optional)";
    if (dsSchemaInput) dsSchemaInput.placeholder = "Only fill when override is required";
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

  function getBasePageSize() {
    var el = $id("basePageSize");
    var size = el ? parseInt(el.value, 10) : 10;
    return size > 0 ? size : 10;
  }

  function getDsPageSize() {
    var el = $id("dsPageSize");
    var size = el ? parseInt(el.value, 10) : 10;
    return size > 0 ? size : 10;
  }

  function fmtNum(v) {
    var n = Number(v);
    return isNaN(n) ? "0" : n.toLocaleString("zh-CN");
  }

  function renderPager(pagerEl, infoEl, totalItems, page, pageSize, onGo) {
    var totalPages = Math.max(1, Math.ceil(totalItems / pageSize));

    if (infoEl) {
      var from = totalItems === 0 ? 0 : (page - 1) * pageSize + 1;
      var to = totalItems === 0 ? 0 : Math.min(page * pageSize, totalItems);
      infoEl.textContent = "显示第 " + fmtNum(from) + " 到 " + fmtNum(to) + " 条，共 " + fmtNum(totalItems) + " 条";
    }

    if (!pagerEl) return;
    pagerEl.innerHTML = "";
    if (totalPages <= 1) return;

    function makeButton(label, targetPage, opts) {
      opts = opts || {};
      var btn = document.createElement("button");
      btn.type = "button";
      btn.className = "page-btn" + (opts.active ? " active" : "");
      btn.innerHTML = label;
      if (opts.disabled || opts.active) {
        btn.disabled = true;
      } else {
        btn.addEventListener("click", function () { onGo(targetPage); });
      }
      return btn;
    }

    pagerEl.appendChild(makeButton('<i class="bi bi-chevron-left"></i>', page - 1, { disabled: page <= 1 }));

    buildPageList(totalPages, page).forEach(function (item) {
      if (item === "...") {
        var gap = document.createElement("span");
        gap.className = "page-gap";
        gap.textContent = "…";
        pagerEl.appendChild(gap);
      } else {
        pagerEl.appendChild(makeButton(String(item), item, { active: item === page }));
      }
    });

    pagerEl.appendChild(makeButton('<i class="bi bi-chevron-right"></i>', page + 1, { disabled: page >= totalPages }));
  }

  function buildPageList(totalPages, current) {
    if (totalPages <= 7) {
      var pages = [];
      for (var i = 1; i <= totalPages; i++) pages.push(i);
      return pages;
    }
    if (current <= 4) {
      return [1, 2, 3, 4, 5, "...", totalPages];
    }
    if (current >= totalPages - 3) {
      return [1, "...", totalPages - 4, totalPages - 3, totalPages - 2, totalPages - 1, totalPages];
    }
    return [1, "...", current - 1, current, current + 1, "...", totalPages];
  }

  function norm(v) { return String(v || "").trim().toLowerCase(); }

  function buildTarget(base, ds) {
    if (!base) return "基础配置不存在";
    var type = norm(base.type);
    var name = base.databaseName || base.sid || "-";
    if (type === "mysql" && ds && ds.schema) name = ds.schema;
    var t = type + "://" + base.host + ":" + base.port + (name === "-" && type === "mysql" ? "" : "/" + name);
    if (type !== "postgres" && type !== "mysql") return t;
    var params = new URLSearchParams(base.jdbcParams || "");
    if (type === "postgres" && ds && ds.schema) params.set("currentSchema", ds.schema);
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

  if (!getUsername() || !getPassword()) {
    redirectToLogin(false);
  } else {
    loadConfig();
  }

})();
