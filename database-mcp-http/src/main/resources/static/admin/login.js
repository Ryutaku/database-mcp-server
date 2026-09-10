/* ===================================================================
   Database MCP Admin — Login
   =================================================================== */
(function () {
  "use strict";

  var USER_STORAGE_KEY = "database-mcp-admin-user";
  var PASSWORD_STORAGE_KEY = "database-mcp-admin-password";
  var adminApiBase = resolveAdminApiBase();

  var form       = document.getElementById("loginForm");
  var usernameInput = document.getElementById("usernameInput");
  var input      = document.getElementById("passwordInput");
  var remember   = document.getElementById("rememberInput");
  var toggle     = document.getElementById("togglePassword");
  var toggleIcon = toggle ? toggle.querySelector("i") : null;
  var submitBtn  = document.getElementById("loginButton");
  var submitText = document.getElementById("loginButtonText");
  var errorBox   = document.getElementById("loginError");
  var errorText  = document.getElementById("loginErrorText");

  var params = new URLSearchParams(window.location.search);
  var redirectTo = sanitizeRedirect(params.get("redirect")) || "index.html";
  var sessionExpired = params.get("reason") === "expired";

  /* =========================  HELPERS  ========================= */

  function resolveAdminApiBase() {
    var cur = new URL(window.location.href);
    var p = cur.pathname.replace(/\/[^/]*$/, "");
    return p + "/api";
  }

  function sanitizeRedirect(value) {
    if (!value) return "";
    return /^[A-Za-z0-9._-]+$/.test(value) ? value : "";
  }

  function getStoredUsername() {
    return window.sessionStorage.getItem(USER_STORAGE_KEY) ||
      window.localStorage.getItem(USER_STORAGE_KEY) || "";
  }

  function getStoredPassword() {
    return window.sessionStorage.getItem(PASSWORD_STORAGE_KEY) ||
      window.localStorage.getItem(PASSWORD_STORAGE_KEY) || "";
  }

  function saveCredentials(username, password, persist) {
    clearCredentials();
    var storage = persist ? window.localStorage : window.sessionStorage;
    storage.setItem(USER_STORAGE_KEY, username);
    storage.setItem(PASSWORD_STORAGE_KEY, password);
  }

  function clearCredentials() {
    window.localStorage.removeItem(USER_STORAGE_KEY);
    window.localStorage.removeItem(PASSWORD_STORAGE_KEY);
    window.sessionStorage.removeItem(USER_STORAGE_KEY);
    window.sessionStorage.removeItem(PASSWORD_STORAGE_KEY);
  }

  function showError(msg) {
    if (!errorBox) return;
    if (errorText) errorText.textContent = msg;
    errorBox.classList.remove("hidden");
  }

  function hideError() {
    if (errorBox) errorBox.classList.add("hidden");
  }

  function setBusy(busy) {
    if (!submitBtn) return;
    submitBtn.classList.toggle("is-loading", busy);
    submitBtn.disabled = busy;
    if (submitIcon()) submitIcon().className = busy ? "bi bi-arrow-repeat" : "bi bi-box-arrow-in-right";
    if (submitText) submitText.textContent = busy ? "验证中…" : "登录";
  }

  function submitIcon() {
    return submitBtn ? submitBtn.querySelector("i") : null;
  }

  function verify(username, password) {
    return fetch(adminApiBase + "/health", {
      method: "GET",
      headers: {
        "X-Admin-User": username,
        "X-Admin-Password": password
      },
      cache: "no-store"
    }).then(function (res) {
      if (res.ok) return true;
      if (res.status === 401) return false;
      throw new Error("服务端返回异常状态：" + res.status);
    });
  }

  /* =========================  EVENTS  ========================= */

  if (toggle) {
    toggle.addEventListener("click", function () {
      var show = input.type === "password";
      input.type = show ? "text" : "password";
      if (toggleIcon) toggleIcon.className = show ? "bi bi-eye-slash" : "bi bi-eye";
      input.focus();
    });
  }

  if (form) {
    form.addEventListener("submit", function (event) {
      event.preventDefault();
      var username = (usernameInput.value || "").trim();
      var password = input.value || "";

      if (!username) {
        showError("请输入用户名");
        usernameInput.focus();
        return;
      }
      if (!password) {
        showError("请输入密码");
        input.focus();
        return;
      }

      hideError();
      setBusy(true);

      verify(username, password)
        .then(function (ok) {
          if (ok) {
            saveCredentials(username, password, remember ? remember.checked : true);
            window.location.replace(redirectTo);
            return;
          }
          setBusy(false);
          showError("用户名或密码不正确，请重试");
          input.select();
        })
        .catch(function (err) {
          setBusy(false);
          showError((err && err.message) || "无法连接到服务器，请检查服务是否可用");
        });
    });
  }

  /* =========================  INIT  ========================= */

  if (sessionExpired) {
    showError("登录状态已失效，请重新登录");
  }

  var storedUsername = getStoredUsername();
  var storedPassword = getStoredPassword();
  if (storedUsername && storedPassword) {
    setBusy(true);
    verify(storedUsername, storedPassword)
      .then(function (ok) {
        if (ok) {
          window.location.replace(redirectTo);
          return;
        }
        clearCredentials();
        setBusy(false);
      })
      .catch(function () {
        setBusy(false);
      });
  } else if (usernameInput) {
    (usernameInput.value ? input : usernameInput).focus();
  }

})();
