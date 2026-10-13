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

  var blacklistSection  = $id("blacklistSection");
  var blacklistRules    = $id("blacklistRules");
  var blacklistEmptyHint = $id("blacklistEmptyHint");
  var blacklistRestoreBtn = $id("blacklistRestoreDefaults");

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

  var blacklistViewOverlay = $id("blacklistViewOverlay");
  var blacklistViewTitle   = $id("blacklistViewTitle");
  var blacklistViewType    = $id("blacklistViewType");
  var blacklistViewSummary = $id("blacklistViewSummary");
  var blacklistViewList    = $id("blacklistViewList");

  var usageCountEl          = $id("usageCount");
  var usageSummaryEl        = $id("usageSummary");
  var usageDsTableBody      = $id("usageDsTableBody");
  var usageToolTableBody    = $id("usageToolTableBody");
  var usageRecordTableBody  = $id("usageRecordTableBody");
  var usageWindowSelect     = $id("usageWindow");
  var usageFilterDatasource = $id("usageFilterDatasource");
  var usageFilterTool       = $id("usageFilterTool");
  var usageFilterOutcome    = $id("usageFilterOutcome");
  var usagePagerEl          = $id("usagePager");
  var usagePageInfo         = $id("usagePageInfo");
  var usageDsMore           = $id("usageDsMore");
  var usageToolMore         = $id("usageToolMore");

  /* =========================  STATE  ========================= */
  var currentBaseConfigs = [];
  var currentDatasources = [];
  var basePage = 1;
  var dsPage = 1;

  /* Rule catalogs are per database type and never change at runtime, so they are fetched once. */
  var blacklistCatalogs = {};

  /* Rule ids of the datasource being edited. null means "not saved yet, show the defaults". */
  var dsBlacklistSelection = null;

  /* Datasource whose blacklist is currently shown in the read-only viewer. */
  var blacklistViewTarget = null;

  /* The hotspot panel is fetched on first view; its detail list is paged by the server. */
  var usageLoaded = false;
  var usagePage = 1;

  /* A ranking over every datasource is a wall of rows that pushes the tool ranking off screen,
     so each ranking shows its head and the tail sits behind an explicit expand. */
  var usageRankLimit = 20;
  var usageDsExpanded = false;
  var usageToolExpanded = false;
  var usageRankings = null;

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

  /* --- 热点数据源 --- */
  bind("usageRefreshBtn", "click", function () { loadUsage(true); });
  bind("usageWindow", "change", function () {
    usagePage = 1;
    loadUsage(true);
  });
  bind("usageClearBtn", "click", function () {
    showConfirm("清空调用统计", "确认删除已记录的全部工具调用明细？排行会随后续调用重新累积。").then(function (ok) {
      if (!ok) return;
      apiFetch("/usage-records", { method: "DELETE" })
        .then(function () {
          usagePage = 1;
          return loadUsage(true, "调用统计已清空");
        })
        .catch(function (err) { showToast(err.message, true); });
    });
  });
  ["usageFilterDatasource", "usageFilterTool", "usageFilterOutcome", "usagePageSize"].forEach(function (id) {
    bind(id, "change", function () {
      if (id === "usagePageSize") usagePage = 1;
      loadUsageRecords();
    });
  });
  [usageDsMore, usageToolMore].forEach(function (container) {
    if (!container) return;
    container.addEventListener("click", function (e) {
      var button = e.target.closest("button[data-rank]");
      if (!button) return;
      var kind = button.dataset.rank;
      if (kind === "datasource") usageDsExpanded = !usageDsExpanded;
      else usageToolExpanded = !usageToolExpanded;
      renderUsageRanking(kind);
    });
  });

  /* --- Reload --- */
  bind("reloadButton", "click", function () {
    loadConfig("配置已刷新");
    if (usageLoaded) loadUsage(false, "统计已刷新");
  });

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
    baseConfigSelect.addEventListener("change", function () {
      /* The rule catalog is per database type, so a switched profile starts from that type's defaults. */
      dsBlacklistSelection = null;
      updateDsFormByType();
    });
  }

  /* --- Execute blacklist checkboxes --- */
  if (blacklistRestoreBtn) {
    blacklistRestoreBtn.addEventListener("click", function () {
      dsBlacklistSelection = null;
      renderBlacklistRules();
    });
  }
  if (blacklistRules) {
    blacklistRules.addEventListener("change", updateBlacklistEmptyHint);
  }

  /* --- Blacklist viewer: jump straight into editing the datasource it describes --- */
  bind("blacklistViewEdit", "click", function () {
    var item = blacklistViewTarget;
    if (!item) return;
    closeModal(blacklistViewOverlay);
    fillDsForm(item);
    dsModalTitle.textContent = "Edit Datasource: " + item.id;
    dsForm.elements.id.setAttribute("readonly", "readonly");
    openModal(dsModal);
  });

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
    var blacklist = collectBlacklistSelection();
    var payload = {
      baseConfigId: baseId,
      username: String(fd.get("username") || "").trim(),
      password: String(fd.get("password") || "").trim(),
      schema: norm(selBase && selBase.type) === "oracle" ? "" : String(fd.get("schema") || "").trim(),
      executeBlacklist: blacklist
    };

    if (blacklist.length === 0) {
      showConfirm(
        "清空执行黑名单",
        "该数据源的 db_execute 将不拦截任何语句，包括 DROP DATABASE 和 DROP USER。确认保存？"
      ).then(function (ok) {
        if (ok) saveDatasource(id, payload);
      });
      return;
    }

    saveDatasource(id, payload);
  }

  function saveDatasource(id, payload) {
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

    markClippedCells(baseTableBody);

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
      emptyRow(dsTableBody, 7, '暂无数据源映射，点击 "Create Datasource" 开始创建。');
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
        '<td data-label="Execute Blacklist">' + blacklistCell(item) + '</td>' +
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

      tr.querySelector('[data-act="bl"]').addEventListener("click", function () {
        openBlacklistView(item, base);
      });

      dsTableBody.appendChild(tr);
    });

    markClippedCells(dsTableBody);

    renderPager(dsPagerEl, dsPageInfo, filtered.length, dsPage, pageSize, function (page) {
      dsPage = page;
      renderDatasources(currentDatasources, currentBaseConfigs);
    });
  }

  /* =========================================================
     HOTSPOT PANEL
     ========================================================= */

  function currentUsageWindow() {
    return usageWindowSelect ? usageWindowSelect.value : "7d";
  }

  function windowLabel(code) {
    var labels = { today: "今天", "7d": "近 7 天", "30d": "近 30 天", all: "全部" };
    return labels[code] || code;
  }

  function loadUsage(refreshFilters, successMsg) {
    usageLoaded = true;
    var filters = refreshFilters
      ? apiFetch("/usage-filters").then(applyUsageFilters).catch(function () {})
      : Promise.resolve();
    return Promise.all([
      filters,
      apiFetch("/usage-stats?window=" + encodeURIComponent(currentUsageWindow())),
      loadUsageRecords()
    ]).then(function (results) {
      renderUsageSummary(results[1]);
      renderUsageRankings(results[1]);
      if (successMsg) showToast(successMsg);
    }).catch(function (err) {
      showToast(err.message, true);
    });
  }

  function loadUsageRecords() {
    if (!usageRecordTableBody) return Promise.resolve();
    var query = "/usage-records?window=" + encodeURIComponent(currentUsageWindow()) +
      "&page=" + usagePage +
      "&size=" + getUsagePageSize() +
      "&datasourceId=" + encodeURIComponent(usageFilterDatasource ? usageFilterDatasource.value : "") +
      "&tool=" + encodeURIComponent(usageFilterTool ? usageFilterTool.value : "") +
      "&outcome=" + encodeURIComponent(usageFilterOutcome ? usageFilterOutcome.value : "");
    return apiFetch(query).then(renderUsageRecords).catch(function (err) {
      showToast(err.message, true);
    });
  }

  function applyUsageFilters(filters) {
    fillUsageSelect(usageFilterDatasource, "全部数据源", filters.datasourceIds);
    fillUsageSelect(usageFilterTool, "全部工具", filters.toolNames);
  }

  function fillUsageSelect(select, placeholder, values) {
    if (!select) return;
    var current = select.value;
    var list = values || [];
    select.innerHTML = '<option value="">' + esc(placeholder) + "</option>";
    list.forEach(function (value) {
      var option = document.createElement("option");
      option.value = value;
      option.textContent = value;
      select.appendChild(option);
    });
    if (current && list.indexOf(current) !== -1) select.value = current;
  }

  function renderUsageSummary(summary) {
    var total = Number(summary.totalCalls) || 0;
    if (usageCountEl) usageCountEl.textContent = fmtNum(total);
    if (!usageSummaryEl) return;

    if (total === 0) {
      usageSummaryEl.innerHTML = '<div class="usage-empty">该窗口内还没有调用记录。换个时间窗口，或让 MCP 客户端调用一次工具。</div>';
      return;
    }

    var lastSeen = null;
    (summary.datasources || []).forEach(function (item) {
      if (item.lastSeenAt && (!lastSeen || item.lastSeenAt > lastSeen)) lastSeen = item.lastSeenAt;
    });

    usageSummaryEl.innerHTML =
      usageCard("时间窗口", windowLabel(summary.window)) +
      usageCard("总调用", fmtNum(total)) +
      usageCard("失败", fmtNum(summary.totalFailures)) +
      usageCard("涉及数据源", fmtNum((summary.datasources || []).length)) +
      usageCard("最近调用", fmtTime(lastSeen));
  }

  function usageCard(label, value) {
    return '<div class="usage-card">' +
      '<span class="usage-card-label">' + esc(label) + "</span>" +
      '<span class="usage-card-value">' + esc(value) + "</span>" +
      "</div>";
  }

  function renderUsageRankings(summary) {
    usageRankings = summary;
    renderUsageRanking("datasource");
    renderUsageRanking("tool");
  }

  /** One ranking: its head by default, the tail only after the reader asks for it. */
  function renderUsageRanking(kind) {
    var isDatasource = kind === "datasource";
    var summary = usageRankings || {};
    var items = (isDatasource ? summary.datasources : summary.tools) || [];
    var expanded = isDatasource ? usageDsExpanded : usageToolExpanded;
    var more = isDatasource ? usageDsMore : usageToolMore;

    renderUsageRankTable(
      isDatasource ? usageDsTableBody : usageToolTableBody,
      expanded ? items : items.slice(0, usageRankLimit),
      isDatasource ? "datasourceId" : "toolName",
      isDatasource ? "该窗口内没有数据源调用记录。" : "该窗口内没有工具调用记录。",
      isDatasource,
      summary.totalCalls
    );

    if (!more) return;
    if (items.length <= usageRankLimit) {
      more.innerHTML = "";
      return;
    }
    more.innerHTML = '<button class="btn btn-default btn-sm" type="button" data-rank="' + kind + '">' +
      (expanded
        ? "只看前 " + usageRankLimit + " 名"
        : "展开全部 " + fmtNum(items.length) + " 名（当前显示前 " + usageRankLimit + "）") +
      "</button>";
  }

  /**
   * The bar is scaled to the top of the ranking, not to 100%, so the second place still reads as a
   * bar next to the first; the number beside it is the true share of all calls in the window, which
   * is why the window total is passed in rather than summed from the visible rows.
   */
  function renderUsageRankTable(tbody, items, nameField, emptyMsg, withLastSeen, totalCalls) {
    if (!tbody) return;
    var list = items || [];
    tbody.innerHTML = "";
    if (!list.length) {
      emptyRow(tbody, withLastSeen ? 7 : 6, emptyMsg);
      return;
    }

    var total = Number(totalCalls) || list.reduce(function (sum, item) { return sum + (Number(item.callCount) || 0); }, 0);
    var top = list.reduce(function (max, item) { return Math.max(max, Number(item.callCount) || 0); }, 0);

    list.forEach(function (item) {
      var calls = Number(item.callCount) || 0;
      var share = top > 0 ? (calls / top) * 100 : 0;
      var pct = total > 0 ? (calls / total) * 100 : 0;
      var name = item[nameField] || "-";
      var time = fmtTime(item.lastSeenAt);

      var tr = document.createElement("tr");
      tr.innerHTML =
        '<td data-label="名称" title="' + esc(name) + '">' + esc(name) + "</td>" +
        '<td data-label="调用数">' + fmtNum(calls) + "</td>" +
        '<td data-label="占比"><div class="usage-bar" title="' + pct.toFixed(1) + '%"><span style="width:' + share.toFixed(1) + '%"></span></div><span class="usage-share">' + pct.toFixed(1) + "%</span></td>" +
        '<td data-label="成功 / 失败"><span class="usage-ok">' + fmtNum(item.successCount) + '</span> / <span class="usage-bad">' + fmtNum(item.failureCount) + "</span></td>" +
        '<td data-label="平均耗时">' + fmtMs(item.avgDurationMillis) + "</td>" +
        '<td data-label="最大耗时">' + fmtMs(item.maxDurationMillis) + "</td>" +
        (withLastSeen ? '<td data-label="最近调用" title="' + esc(time) + '">' + esc(time) + "</td>" : "");
      tbody.appendChild(tr);
    });

    markClippedCells(tbody);
  }

  function renderUsageRecords(page) {
    if (!usageRecordTableBody) return;
    var items = (page && page.items) || [];
    usageRecordTableBody.innerHTML = "";

    if (!items.length) {
      emptyRow(usageRecordTableBody, 6, "该窗口内没有符合条件的调用记录。");
    } else {
      items.forEach(function (record) {
        var time = fmtTime(record.occurredAt);
        var tr = document.createElement("tr");
        tr.innerHTML =
          '<td data-label="时间" title="' + esc(time) + '">' + esc(time) + "</td>" +
          '<td data-label="Datasource ID" title="' + esc(record.datasourceId) + '">' + esc(record.datasourceId) + "</td>" +
          '<td data-label="工具" title="' + esc(record.toolName) + '">' + esc(record.toolName) + "</td>" +
          '<td data-label="结果"><span class="Label ' + (record.success ? "Label--success" : "Label--danger") + '">' + (record.success ? "成功" : "失败") + "</span></td>" +
          '<td data-label="耗时">' + fmtMs(record.durationMillis) + "</td>" +
          '<td data-label="错误" title="' + esc(record.errorMessage || "-") + '">' + esc(record.errorMessage || "-") + "</td>";
        usageRecordTableBody.appendChild(tr);
      });
    }

    markClippedCells(usageRecordTableBody);
    renderPager(usagePagerEl, usagePageInfo, (page && page.total) || 0, usagePage, getUsagePageSize(), function (page) {
      usagePage = page;
      loadUsageRecords();
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
    dsBlacklistSelection = item.executeBlacklist || null;
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
    dsBlacklistSelection = null;
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
      renderBlacklistRules();
      return;
    }

    if (dsDbTypeBadge) { dsDbTypeBadge.textContent = "DB Type: " + type; dsDbTypeBadge.className = "Label Label--accent"; }
    renderBlacklistRules();

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
     EXECUTE BLACKLIST
     ========================================================= */

  function findSelectedBase() {
    var selId = dsForm ? dsForm.elements.baseConfigId.value : "";
    return currentBaseConfigs.find(function (c) { return c.id === selId; }) || null;
  }

  /**
   * Renders the rule checklist for the selected base profile's database type. The catalog comes
   * from the server, so the page never hardcodes which statements the server knows how to block.
   */
  function renderBlacklistRules() {
    if (!blacklistSection || !blacklistRules) return;

    var base = findSelectedBase();
    if (!base) {
      blacklistSection.classList.add("hidden");
      blacklistRules.innerHTML = "";
      updateBlacklistEmptyHint();
      return;
    }

    blacklistSection.classList.remove("hidden");
    var type = norm(base.type);
    var catalog = blacklistCatalogs[type];

    if (!catalog) {
      blacklistRules.innerHTML = '<div class="blacklist-note">加载规则清单…</div>';
      apiFetch("/blacklist-rules?type=" + encodeURIComponent(type)).then(function (catalog) {
        blacklistCatalogs[type] = catalog;
        renderBlacklistRules();
      }).catch(function (err) {
        blacklistRules.innerHTML = '<div class="blacklist-note">规则清单加载失败：' + esc(err.message) + "</div>";
      });
      return;
    }

    /* An empty selection is a real setting ("block nothing"), so only null means "show the defaults". */
    var selected = dsBlacklistSelection == null ? catalog.defaultRuleIds : dsBlacklistSelection;

    blacklistRules.innerHTML = catalog.rules.map(function (rule) {
      var checked = selected.indexOf(rule.id) !== -1;
      return '<label class="blacklist-rule">' +
        '<input type="checkbox" name="blacklistRule" value="' + esc(rule.id) + '"' + (checked ? " checked" : "") + ">" +
        "<span>" + esc(rule.label) + "<code>" + esc(rule.phrase) + "</code></span>" +
        "</label>";
    }).join("");

    updateBlacklistEmptyHint();
  }

  function collectBlacklistSelection() {
    if (!blacklistRules) return [];
    var boxes = blacklistRules.querySelectorAll('input[name="blacklistRule"]:checked');
    var ids = [];
    for (var i = 0; i < boxes.length; i++) {
      ids.push(boxes[i].value);
    }
    return ids;
  }

  function updateBlacklistEmptyHint() {
    if (!blacklistEmptyHint) return;
    blacklistEmptyHint.classList.toggle("show", collectBlacklistSelection().length === 0);
  }

  /**
   * Table cell showing how tightly this datasource fences in db_execute. The badge is a button:
   * a count alone cannot tell you which statements are blocked, so clicking it opens the full list.
   */
  function blacklistCell(item) {
    var ruleIds = item.executeBlacklist;
    var variant, text, hint;

    if (ruleIds === null || ruleIds === undefined) {
      variant = "Label--secondary";
      text = "默认";
      hint = "沿用该数据库的默认黑名单，点击查看完整列表";
    } else if (ruleIds.length === 0) {
      variant = "Label--danger";
      text = "无限制";
      hint = "不拦截任何语句，点击查看完整列表";
    } else {
      variant = "Label--success";
      text = ruleIds.length + " 条";
      hint = "点击查看完整黑名单";
    }

    return '<button class="Label ' + variant + ' Label--clickable" type="button" data-act="bl" title="' + esc(hint) + '">' +
      text + "</button>";
  }

  /**
   * Opens the read-only blacklist viewer for one datasource.
   */
  function openBlacklistView(item, base) {
    if (!blacklistViewOverlay) return;

    blacklistViewTarget = item;
    var type = base ? norm(base.type) : "";
    var ruleIds = item.executeBlacklist;
    var usingDefaults = ruleIds === null || ruleIds === undefined;

    blacklistViewTitle.textContent = "执行黑名单 · " + item.id;
    blacklistViewType.textContent = type ? type.toUpperCase() : "未知类型";
    blacklistViewList.innerHTML = "";
    blacklistViewSummary.textContent = "";
    openModal(blacklistViewOverlay);

    if (!type) {
      blacklistViewSummary.textContent = "该数据源的基础配置缺失，取不到规则清单。";
      return;
    }

    var catalog = blacklistCatalogs[type];
    if (catalog) {
      renderBlacklistView(type, catalog, ruleIds, usingDefaults);
      return;
    }

    blacklistViewSummary.textContent = "加载规则清单…";
    apiFetch("/blacklist-rules?type=" + encodeURIComponent(type)).then(function (loaded) {
      blacklistCatalogs[type] = loaded;
      /* The viewer may have been closed, or pointed at another row, while loading. */
      if (blacklistViewTarget === item) {
        renderBlacklistView(type, loaded, ruleIds, usingDefaults);
      }
    }).catch(function (err) {
      blacklistViewSummary.textContent = "规则清单加载失败：" + err.message;
    });
  }

  /** Renders the whole catalog with each rule marked 拦截 / 放行, so the gaps are visible too. */
  function renderBlacklistView(type, catalog, ruleIds, usingDefaults) {
    var selected = usingDefaults ? (catalog.defaultRuleIds || []) : ruleIds;
    var blocked = 0;

    catalog.rules.forEach(function (rule) {
      if (selected.indexOf(rule.id) !== -1) blocked++;
    });

    if (usingDefaults) {
      blacklistViewSummary.innerHTML =
        "未单独配置，沿用 " + esc(type.toUpperCase()) + " 默认黑名单：拦截 <strong>" + blocked +
        "</strong> 条，放行 " + (catalog.rules.length - blocked) + " 条。";
    } else if (selected.length === 0) {
      blacklistViewSummary.innerHTML =
        '<span class="blacklist-view-warn">黑名单已清空：db_execute 不拦截任何语句，包括 DROP DATABASE。</span>';
    } else {
      blacklistViewSummary.innerHTML =
        "已拦截 <strong>" + blocked + "</strong> 条，放行 " + (catalog.rules.length - blocked) + " 条。";
    }

    blacklistViewList.innerHTML = catalog.rules.map(function (rule) {
      var on = selected.indexOf(rule.id) !== -1;
      return '<li class="blacklist-view-item' + (on ? " is-on" : " is-off") + '">' +
        '<span class="blacklist-view-state"><i class="bi ' + (on ? "bi-check-circle-fill" : "bi-dash-circle") +
          '" aria-hidden="true"></i>' + (on ? "拦截" : "放行") + "</span>" +
        '<span class="blacklist-view-main">' +
          '<span class="blacklist-view-label">' + esc(rule.label) + "</span>" +
          "<code>" + esc(rule.phrase) + "</code>" +
          (rule.description ? '<span class="blacklist-view-desc">' + esc(rule.description) + "</span>" : "") +
        "</span>" +
        "</li>";
    }).join("");
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
    /* Cells can only be measured once their panel is visible. */
    markAllClippedCells();

    /* The hotspot panel is the only view that reads a growing store, so it loads on first use. */
    if (view === "usage" && !usageLoaded) {
      loadUsage(false);
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

  function getUsagePageSize() {
    var el = $id("usagePageSize");
    var size = el ? parseInt(el.value, 10) : 20;
    return size > 0 ? size : 20;
  }

  function fmtNum(v) {
    var n = Number(v);
    return isNaN(n) ? "0" : n.toLocaleString("zh-CN");
  }

  /** Durations are read as "how long did a call take", so the unit follows the magnitude. */
  function fmtMs(v) {
    var n = Number(v);
    if (isNaN(n)) return "-";
    if (n < 1000) return n + " ms";
    if (n < 60000) return (n / 1000).toFixed(2) + " s";
    return Math.floor(n / 60000) + " 分 " + Math.round((n % 60000) / 1000) + " 秒";
  }

  /** The store keeps UTC; the panel shows the browser's local time, like every other timestamp. */
  function fmtTime(iso) {
    if (!iso) return "-";
    var date = new Date(iso);
    if (isNaN(date.getTime())) return String(iso);
    function pad(n) { return n < 10 ? "0" + n : String(n); }
    return date.getFullYear() + "-" + pad(date.getMonth() + 1) + "-" + pad(date.getDate()) +
      " " + pad(date.getHours()) + ":" + pad(date.getMinutes()) + ":" + pad(date.getSeconds());
  }

  /**
   * Table cells are single-line with an ellipsis. Flag the ones whose text is
   * actually cut, so the CSS can advertise them (help cursor + dotted underline)
   * and the title tooltip reads as intentional instead of appearing from nowhere.
   */
  function markClippedCells(tbody) {
    if (!tbody) return;
    var cells = tbody.querySelectorAll("td[data-label]");
    for (var i = 0; i < cells.length; i++) {
      var cell = cells[i];
      /* A hidden panel has no layout (clientWidth 0), which would make every
         cell look clipped. Skip those; switchView re-marks once it is shown. */
      if (!cell.clientWidth) continue;
      cell.classList.toggle("is-clipped", cell.scrollWidth > cell.clientWidth + 1);
    }
  }

  function markAllClippedCells() {
    markClippedCells(baseTableBody);
    markClippedCells(dsTableBody);
    markClippedCells(usageDsTableBody);
    markClippedCells(usageToolTableBody);
    markClippedCells(usageRecordTableBody);
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

  /* Whether a value is cut depends on the width available, so re-check on resize. */
  var resizeTimer = null;
  window.addEventListener("resize", function () {
    if (resizeTimer) clearTimeout(resizeTimer);
    resizeTimer = setTimeout(markAllClippedCells, 150);
  });

  if (!getUsername() || !getPassword()) {
    redirectToLogin(false);
  } else {
    loadConfig();
  }

})();
