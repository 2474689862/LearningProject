/* 统一请求封装：所有接口返回 {code, msg, data}，code=0 成功，code=401 未登录 */
(function (global) {
  'use strict';

  const BASE = '';

  /* 应用上下文路径：根上下文部署时为 ''，子上下文（如 /ai-study）时为该前缀 */
  const CTX = (function () {
    const seg = location.pathname.split('/')[1] || '';
    return seg.endsWith('.html') ? '' : '/' + seg;
  })();

  /* 把服务端返回的绝对路径（如 /uploads/x.jpg）拼上上下文前缀 */
  function url(path) {
    if (!path) return path;
    return path.charAt(0) === '/' ? CTX + path : path;
  }

  function toQuery(params) {
    if (!params) return '';
    const usp = new URLSearchParams();
    Object.keys(params).forEach(function (k) {
      const v = params[k];
      if (v !== undefined && v !== null && v !== '') usp.append(k, v);
    });
    const s = usp.toString();
    return s ? '?' + s : '';
  }

  async function handle(res) {
    let body;
    try {
      body = await res.json();
    } catch (e) {
      throw new Error('服务异常（HTTP ' + res.status + '）');
    }
    if (body.code === 401) {
      const here = location.pathname.split('/').pop();
      if (here !== 'index.html' && here !== '') {
        location.href = 'index.html?expired=1';
      }
      throw new Error(body.msg || '请先登录');
    }
    if (body.code !== 0) throw new Error(body.msg || '操作失败');
    return body;
  }

  const Api = {
    get: async function (path, params) {
      return handle(await fetch(BASE + path + toQuery(params), {
        method: 'GET',
        credentials: 'same-origin'
      }));
    },
    post: async function (path, data) {
      const usp = new URLSearchParams();
      Object.keys(data || {}).forEach(function (k) {
        if (data[k] !== undefined && data[k] !== null) usp.append(k, data[k]);
      });
      return handle(await fetch(BASE + path, {
        method: 'POST',
        credentials: 'same-origin',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
        body: usp.toString()
      }));
    },
    upload: async function (path, file, fieldName) {
      const fd = new FormData();
      fd.append(fieldName || 'image', file);
      return handle(await fetch(BASE + path, {
        method: 'POST',
        credentials: 'same-origin',
        body: fd
      }));
    }
  };

  function toast(message, type) {
    let box = document.getElementById('toast-box');
    if (!box) {
      box = document.createElement('div');
      box.id = 'toast-box';
      document.body.appendChild(box);
    }
    const item = document.createElement('div');
    item.className = 'toast ' + (type || 'info');
    item.textContent = message;
    box.appendChild(item);
    setTimeout(function () { item.classList.add('hide'); }, 2200);
    setTimeout(function () { item.remove(); }, 2700);
  }

  function escapeHtml(str) {
    if (str === null || str === undefined) return '';
    return String(str)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }

  function formatDate(v) {
    if (!v) return '';
    return String(v).replace('T', ' ').substring(0, 16);
  }

  async function currentUser() {
    const res = await Api.get('auth/me.action');
    return res.data;
  }

  async function guard() {
    try {
      const user = await currentUser();
      document.querySelectorAll('[data-username]').forEach(function (el) {
        el.textContent = user.nickname || user.username;
      });
      return user;
    } catch (e) {
      location.href = 'index.html?expired=1';
      throw e;
    }
  }

  async function logout() {
    try { await Api.get('auth/logout.action'); } catch (e) { /* ignore */ }
    location.href = 'index.html';
  }

  global.Api = Api;
  global.Api.url = url;
  global.toast = toast;
  global.escapeHtml = escapeHtml;
  global.formatDate = formatDate;
  global.guard = guard;
  global.logout = logout;
})(window);
