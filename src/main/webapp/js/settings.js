/* 设置页：AI 服务（全局，写 t_setting）+ 浏览器偏好（localStorage） */
(function () {
  'use strict';

  var $ = function (id) { return document.getElementById(id); };

  var LS = {
    searchOn: 'aistudy-search-on',
    searchTier: 'aistudy-search-tier',
    anyKey: 'aistudy-anysearch-key',
    katex: 'aistudy-katex',
    rawOpen: 'aistudy-raw-open',
    subject: 'aistudy-default-subject',
    type: 'aistudy-default-type',
    difficulty: 'aistudy-default-difficulty'
  };

  var TYPE_KEYS = ['choice', 'judge', 'blank', 'short', 'essay',
    'reading', 'experiment', 'writing', 'listening', 'translation', 'calculation'];

  var state = {
    user: null,
    provider: 'ollama',
    vendors: [],
    hasCloudKey: false,
    cloudKeyMasked: '',
    clearKey: false
  };

  var searchTier = localStorage.getItem(LS.searchTier) === 'anysearch' ? 'anysearch' : 'default';

  init();

  async function init() {
    try {
      state.user = await guard();
      $('account-name').textContent = '@' + state.user.username;
      $('nickname').value = state.user.nickname || state.user.username;
    } catch (e) {
      return;
    }
    bindEvents();
    initLocalPrefs();
    loadTypes();
    loadSubjects();
    loadSettings();
    loadDiag();
  }

  /* ---------------- 事件绑定 ---------------- */
  function bindEvents() {
    $('provider-group').addEventListener('click', function (e) {
      var btn = e.target.closest('[data-provider]');
      if (!btn) return;
      setProvider(btn.dataset.provider);
    });

    $('vendor').addEventListener('change', function () {
      var v = findVendor(this.value);
      if (!v) return;
      $('cloud-base').value = v.baseUrl;
      $('cloud-model').value = v.model;
      renderVendorNote(v);
      syncDsPick();
    });

    $('ds-model-pick').addEventListener('click', function (e) {
      var btn = e.target.closest('[data-dsmodel]');
      if (!btn) return;
      $('cloud-model').value = btn.dataset.dsmodel;
      syncDsPick();
    });
    $('cloud-model').addEventListener('input', syncDsPick);

    $('local-plan').addEventListener('click', function (e) {
      var btn = e.target.closest('[data-localplan]');
      if (!btn) return;
      applyLocalPlan(btn.dataset.localplan);
    });
    $('ollama-text').addEventListener('input', syncLocalPlan);
    $('ollama-vision').addEventListener('input', syncLocalPlan);

    $('key-eye').addEventListener('click', function () {
      var el = $('cloud-key');
      var show = el.type === 'password';
      el.type = show ? 'text' : 'password';
      this.textContent = show ? '隐藏' : '显示';
    });

    $('key-clear').addEventListener('click', function () {
      $('cloud-key').value = '';
      state.clearKey = true;
      $('key-hint').textContent = '保存后会清除已保存的 Key（回落到 ai.properties）';
    });

    $('cloud-key').addEventListener('input', function () {
      state.clearKey = false;
    });

    $('save-ai').addEventListener('click', saveAi);
    $('test-ai').addEventListener('click', testAi);
    $('reset-ai').addEventListener('click', resetAi);
    $('save-ocr').addEventListener('click', saveOcr);

    $('katex-on').addEventListener('change', function () {
      localStorage.setItem(LS.katex, this.checked ? '1' : '0');
      toast(this.checked ? '已开启公式渲染' : '已关闭公式渲染', 'success');
    });
    $('raw-open').addEventListener('change', function () {
      localStorage.setItem(LS.rawOpen, this.checked ? '1' : '0');
    });

    $('search-on').addEventListener('change', function () {
      localStorage.setItem(LS.searchOn, this.checked ? '1' : '0');
      syncSearch();
    });
    document.querySelectorAll('[data-tier]').forEach(function (b) {
      b.addEventListener('click', function () {
        searchTier = b.dataset.tier;
        localStorage.setItem(LS.searchTier, searchTier);
        syncSearch();
      });
    });
    $('anysearch-key').addEventListener('input', function () {
      localStorage.setItem(LS.anyKey, this.value);
      syncSearch();
    });
    $('any-key-eye').addEventListener('click', function () {
      var el = $('anysearch-key');
      var show = el.type === 'password';
      el.type = show ? 'text' : 'password';
      this.textContent = show ? '隐藏' : '显示';
    });
    $('any-key-clear').addEventListener('click', function () {
      $('anysearch-key').value = '';
      localStorage.removeItem(LS.anyKey);
      syncSearch();
    });
    $('save-search').addEventListener('click', saveSearchParams);

    $('save-learn').addEventListener('click', saveLearn);
    $('save-nickname').addEventListener('click', saveNickname);
    $('save-password').addEventListener('click', savePassword);
    $('export-questions').addEventListener('click', exportQuestions);
    $('refresh-diag').addEventListener('click', loadDiag);
  }

  /* ---------------- AI 服务（全局） ---------------- */
  async function loadSettings() {
    try {
      var res = await Api.get('api/settingsGet.action');
      applySettings(res.data || {});
    } catch (e) {
      showMsg('读取设置失败：' + e.message, 'error');
    }
  }

  function applySettings(d) {
    state.vendors = d.vendors || [];
    state.hasCloudKey = !!d.hasCloudKey;
    state.cloudKeyMasked = d.cloudApiKeyMasked || '';

    fillVendors(d.vendor);
    $('cloud-base').value = d.cloudBaseUrl || '';
    $('cloud-model').value = d.cloudModel || '';
    $('cloud-key').value = '';
    $('cloud-key').placeholder = state.cloudKeyMasked || '尚未配置，请填写 API Key';
    $('key-hint').textContent = state.hasCloudKey
      ? '已保存 ' + state.cloudKeyMasked + '（来源：'
        + (d.cloudKeySource === 'settings' ? '设置页' : 'ai.properties') + '）；留空表示不修改'
      : '尚未配置 Key，切换到「云端 API」前需要先填写';

    $('ollama-base').value = d.ollamaBaseUrl || '';
    $('ollama-text').value = d.ollamaTextModel || '';
    $('ollama-vision').value = d.ollamaVisionModel || '';
    $('keep-alive').value = d.keepAlive || '';
    $('single-model').checked = d.singleModel !== false;
    syncLocalPlan();

    $('timeout').value = d.timeoutSeconds;
    $('max-tokens').value = d.maxTokens;
    $('temperature').value = d.temperature;

    $('two-stage').checked = d.twoStage !== false;
    $('preprocess').checked = d.preprocess !== false;
    $('vision-max-tokens').value = d.visionMaxTokens;
    $('vision-num-ctx').value = d.visionNumCtx;
    $('vision-temperature').value = d.visionTemperature;

    $('search-enabled').checked = d.searchEnabled !== false;
    $('search-topn').value = d.searchTopN;
    $('search-default-maxchars').value = d.searchDefaultMaxChars;
    $('search-default-timeout').value = d.searchDefaultTimeoutMs;
    $('search-any-maxchars').value = d.searchAnyMaxChars;
    $('search-any-timeout').value = d.searchAnyTimeoutMs;
    $('search-any-base').value = d.searchAnyBaseUrl || '';
    $('search-cache-size').value = d.searchCacheSize;
    $('search-cache-ttl').value = d.searchCacheTtlMinutes;

    var ov = d.overriddenKeys || [];
    $('head-state').textContent = ov.length
      ? '当前有 ' + ov.length + ' 项覆盖 ai.properties'
      : '当前全部沿用 ai.properties';

    renderVendorNote(findVendor(d.vendor));
    syncDsPick();
    setProvider(d.provider || 'ollama', true);
    $('ollama-state').textContent = d.ollamaReachable ? '服务可连接' : '服务不可连接';
    $('ollama-state').className = 'badge ' + (d.ollamaReachable ? 'ok' : 'off');
  }

  function fillVendors(selected) {
    var sel = $('vendor');
    sel.innerHTML = state.vendors.map(function (v) {
      var tag = v.level === 'best' ? '最佳适配' : (v.level === 'limited' ? '功能受限' : '未适配');
      return '<option value="' + v.id + '">' + escapeHtml(v.name) + '（' + tag + '）</option>';
    }).join('');
    sel.value = selected || 'deepseek';
  }

  function findVendor(id) {
    for (var i = 0; i < state.vendors.length; i++) {
      if (state.vendors[i].id === id) return state.vendors[i];
    }
    return null;
  }

  /* DeepSeek 专属：Flash / V4 Pro 快捷选择，其它厂商隐藏 */
  function syncDsPick() {
    var isDs = $('vendor').value === 'deepseek';
    $('ds-model-pick').classList.toggle('hidden', !isDs);
    var cur = $('cloud-model').value.trim();
    document.querySelectorAll('[data-dsmodel]').forEach(function (b) {
      b.classList.toggle('active', isDs && b.dataset.dsmodel === cur);
    });
  }

  /* 本地模型方案：完整（qwen3 + qwen2.5vl）/ 轻量（文字与识图共用 qwen2.5vl） */
  function syncLocalPlan() {
    var text = $('ollama-text').value.trim().toLowerCase();
    var vision = $('ollama-vision').value.trim().toLowerCase();
    var light = text !== '' && text === vision;
    document.querySelectorAll('[data-localplan]').forEach(function (b) {
      b.classList.toggle('active', b.dataset.localplan === (light ? 'light' : 'full'));
    });
    $('local-plan-hint').textContent = light
      ? '文字与识图共用一个模型：磁盘约 6 GB，显存占用最小；文字能力弱于 qwen3:8b。'
      : '两个模型各司其职：磁盘约 11 GB；8 GB 显卡请保持「同一时刻只加载一个模型」。';
  }

  function applyLocalPlan(plan) {
    if (plan === 'light') {
      $('ollama-text').value = 'qwen2.5vl:7b';
      $('ollama-vision').value = 'qwen2.5vl:7b';
    } else {
      $('ollama-text').value = 'qwen3:8b';
      $('ollama-vision').value = 'qwen2.5vl:7b';
    }
    syncLocalPlan();
  }

  function renderVendorNote(v) {
    var badge = $('vendor-level');
    var note = $('vendor-note');
    if (!v) {
      badge.textContent = '';
      note.textContent = '';
      return;
    }
    if (v.level === 'best') {
      badge.textContent = '最佳适配';
      badge.className = 'badge ok';
      note.className = 'notice good';
      note.textContent = '该厂商已完整适配：解析、问答、总结、分类等文字功能可以直接使用。'
        + '识图仍走本机 Ollama 视觉模型（云端厂商暂无通用的公网视觉接口）。';
    } else if (v.level === 'limited') {
      badge.textContent = '可选 · 功能受限';
      badge.className = 'badge warn';
      note.className = 'notice warn';
      note.textContent = '协议与 OpenAI 兼容，文字功能通常可用；但鉴权细节、参数名与 JSON 输出未逐个验证，'
        + '出现 400/兼容问题时请切回 DeepSeek。识图同样走本机 Ollama。';
    } else {
      badge.textContent = '未适配 · 不建议';
      badge.className = 'badge danger';
      note.className = 'notice danger';
      note.textContent = '该厂商使用非 OpenAI 协议（鉴权方式、接口路径、消息结构都不同），当前实现无法直接对接，'
        + '调用大概率失败。建议选择 DeepSeek，或使用「自定义 OpenAI 兼容网关」。';
    }
  }

  function setProvider(p, silent) {
    state.provider = p || 'ollama';
    document.querySelectorAll('[data-provider]').forEach(function (b) {
      b.classList.toggle('active', b.dataset.provider === state.provider);
    });
    var cloudOn = state.provider === 'cloud' || state.provider === 'auto';
    var localOn = state.provider === 'ollama' || state.provider === 'auto';
    $('cloud-box').classList.toggle('hidden', !cloudOn);
    $('local-box').classList.toggle('hidden', !localOn);
    $('provider-hint').textContent = {
      ollama: '所有 AI 任务都走本机模型；'
        + ($('ollama-text').value.trim() === $('ollama-vision').value.trim()
          ? '文字与识图共用一个模型。'
          : '切换文字/识图时会卸载另一个模型释放显存。'),
      cloud: '文字任务走云端；识图固定走本机 Ollama 视觉模型。',
      auto: '有云端 Key 时优先云端，否则用本机 Ollama，都没有就退回本地规则模板。',
      local: '完全不用模型，所有回答由本地规则模板生成（不联网、无模型调用）。'
    }[state.provider] || '';
    if (!silent && cloudOn && !state.hasCloudKey) {
      toast('云端模式需要填写 API Key', 'error');
    }
  }

  function collectAi() {
    var payload = {
      'ai.provider': state.provider,
      'ai.cloud.vendor': $('vendor').value,
      'ai.cloud.baseUrl': $('cloud-base').value.trim(),
      'ai.cloud.model': $('cloud-model').value.trim(),
      'ai.ollama.baseUrl': $('ollama-base').value.trim(),
      'ai.ollama.textModel': $('ollama-text').value.trim(),
      'ai.ollama.visionModel': $('ollama-vision').value.trim(),
      'ai.ollama.keepAlive': $('keep-alive').value.trim(),
      'ai.ollama.singleModel': $('single-model').checked ? 'true' : 'false',
      'ai.timeout.seconds': $('timeout').value,
      'ai.max.tokens': $('max-tokens').value,
      'ai.temperature': $('temperature').value,
      'ai.ocr.twoStage': $('two-stage').checked ? 'true' : 'false',
      'ai.ocr.preprocess': $('preprocess').checked ? 'true' : 'false',
      'ai.vision.maxTokens': $('vision-max-tokens').value,
      'ai.vision.numCtx': $('vision-num-ctx').value,
      'ai.vision.temperature': $('vision-temperature').value
    };
    var key = $('cloud-key').value.trim();
    if (key) {
      payload['ai.cloud.apiKey'] = key;
    } else if (state.clearKey) {
      payload['ai.cloud.apiKey'] = '';
    }
    return payload;
  }

  async function saveAi() {
    var v = findVendor($('vendor').value);
    var cloudOn = state.provider === 'cloud' || state.provider === 'auto';
    if (v && v.level === 'unsupported' && cloudOn) {
      if (!window.confirm('「' + v.name + '」未适配，调用大概率直接失败。仍要保存吗？')) return;
    }
    var btn = $('save-ai');
    btn.disabled = true;
    btn.textContent = '保存中…';
    try {
      await Api.post('api/settingsSave.action', { data: JSON.stringify(collectAi()) });
      state.clearKey = false;
      toast('已保存，立即生效', 'success');
      showMsg('');
      await loadSettings();
    } catch (e) {
      showMsg('保存失败：' + e.message, 'error');
    } finally {
      btn.disabled = false;
      btn.textContent = '保存并生效';
    }
  }

  async function testAi() {
    var box = $('test-result');
    box.innerHTML = '<div class="test-box">正在测试当前生效的模型…</div>';
    try {
      var res = await Api.get('api/settingsTestAi.action');
      renderTest(res.data || {});
    } catch (e) {
      box.innerHTML = '<div class="test-box fail"><b>测试失败</b><p>' + escapeHtml(e.message) + '</p></div>';
    }
  }

  function renderTest(d) {
    var box = $('test-result');
    var ok = !!d.ok;
    var head = ok
      ? '连接正常 · ' + (d.providerLabel || d.provider) + ' · ' + (d.model || '')
      : '连接失败 · ' + (d.providerLabel || d.provider || '');
    var lines = [];
    if (ok) {
      lines.push('响应耗时 ' + (d.latencyMs || 0) + ' ms');
    } else if (d.reason) {
      lines.push(d.reason);
    }
    if (d.visionModel) {
      lines.push('识图模型 ' + d.visionModel + '：' + (d.visionReady ? '就绪' : '未就绪（上传识图会失败）'));
    }
    if (d.hint) lines.push(d.hint);
    box.innerHTML = '<div class="test-box ' + (ok ? 'ok' : 'fail') + '">'
      + '<b>' + escapeHtml(head) + '</b>'
      + lines.map(function (t) { return '<p>' + escapeHtml(t) + '</p>'; }).join('')
      + '</div>';
  }

  async function resetAi() {
    if (!window.confirm('恢复默认会清空设置页保存的所有 AI 配置（回到 ai.properties）。继续吗？')) return;
    try {
      await Api.get('api/settingsReset.action');
      toast('已恢复默认配置', 'success');
      await loadSettings();
    } catch (e) {
      toast('恢复失败：' + e.message, 'error');
    }
  }

  /* ---------------- 联网校验（服务端高级参数） ---------------- */
  async function saveSearchParams() {
    var btn = $('save-search');
    btn.disabled = true;
    btn.textContent = '保存中…';
    try {
      await Api.post('api/settingsSave.action', {
        data: JSON.stringify({
          'ai.search.enabled': $('search-enabled').checked ? 'true' : 'false',
          'ai.search.topN': $('search-topn').value,
          'ai.search.default.maxChars': $('search-default-maxchars').value,
          'ai.search.default.timeoutMs': $('search-default-timeout').value,
          'ai.search.anysearch.maxChars': $('search-any-maxchars').value,
          'ai.search.anysearch.timeoutMs': $('search-any-timeout').value,
          'ai.search.anysearch.baseUrl': $('search-any-base').value.trim(),
          'ai.search.cache.size': $('search-cache-size').value,
          'ai.search.cache.ttlMinutes': $('search-cache-ttl').value
        })
      });
      $('search-save-state').textContent = '已保存，立即生效';
      toast('检索参数已保存', 'success');
      await loadSettings();
    } catch (e) {
      $('search-save-state').textContent = '';
      toast('保存失败：' + e.message, 'error');
    } finally {
      btn.disabled = false;
      btn.textContent = '保存检索参数';
    }
  }

  /* ---------------- 识图（全局） ---------------- */
  async function saveOcr() {
    var btn = $('save-ocr');
    btn.disabled = true;
    btn.textContent = '保存中…';
    try {
      await Api.post('api/settingsSave.action', {
        data: JSON.stringify({
          'ai.ocr.twoStage': $('two-stage').checked ? 'true' : 'false',
          'ai.ocr.preprocess': $('preprocess').checked ? 'true' : 'false',
          'ai.vision.maxTokens': $('vision-max-tokens').value,
          'ai.vision.numCtx': $('vision-num-ctx').value,
          'ai.vision.temperature': $('vision-temperature').value
        })
      });
      toast('识图设置已保存', 'success');
    } catch (e) {
      showMsg('保存失败：' + e.message, 'error');
    } finally {
      btn.disabled = false;
      btn.textContent = '保存识图设置';
    }
  }

  /* ---------------- 浏览器偏好 ---------------- */
  function initLocalPrefs() {
    $('katex-on').checked = localStorage.getItem(LS.katex) !== '0';
    $('raw-open').checked = localStorage.getItem(LS.rawOpen) === '1';
    $('search-on').checked = localStorage.getItem(LS.searchOn) !== '0';
    $('anysearch-key').value = localStorage.getItem(LS.anyKey) || '';
    syncSearch();
    $('default-difficulty').value = localStorage.getItem(LS.difficulty) || '';
    $('default-type').value = localStorage.getItem(LS.type) || '';
  }

  function syncSearch() {
    document.querySelectorAll('[data-tier]').forEach(function (b) {
      b.classList.toggle('active', b.dataset.tier === searchTier);
    });
    $('anysearch-wrap').classList.toggle('hidden', searchTier !== 'anysearch');
    var on = $('search-on').checked;
    var hasKey = !!$('anysearch-key').value.trim();
    $('search-state').textContent = !on ? '已关闭'
      : (searchTier === 'anysearch'
        ? (hasKey ? 'AnySearch 档 · Key 已填写' : 'AnySearch 档 · 还缺 Key')
        : '默认档 · 无需 Key');
  }

  function loadTypes() {
    $('default-type').innerHTML = '<option value="">不预设</option>' + TYPE_KEYS.map(function (k) {
      return '<option value="' + k + '">' + escapeHtml(QTypes.label(k)) + '</option>';
    }).join('');
  }

  async function loadSubjects() {
    try {
      var res = await Api.get('api/categoryTree.action');
      var tree = res.data || [];
      $('default-subject').innerHTML = '<option value="">不预设</option>' + tree.map(function (s) {
        return '<option value="' + s.id + '">' + escapeHtml(s.name) + '</option>';
      }).join('');
      $('default-subject').value = localStorage.getItem(LS.subject) || '';
    } catch (e) { /* 未登录已跳转 */ }
  }

  function saveLearn() {
    var sub = $('default-subject').value;
    var type = $('default-type').value;
    var diff = $('default-difficulty').value;
    setOrRemove(LS.subject, sub);
    setOrRemove(LS.type, type);
    setOrRemove(LS.difficulty, diff);
    $('learn-state').textContent = '已保存，下次打开上传页生效';
    toast('学习偏好已保存', 'success');
  }

  function setOrRemove(key, value) {
    if (value) localStorage.setItem(key, value);
    else localStorage.removeItem(key);
  }

  /* ---------------- 账号与数据 ---------------- */
  async function saveNickname() {
    var name = $('nickname').value.trim();
    if (!name) {
      toast('昵称不能为空', 'error');
      return;
    }
    try {
      var res = await Api.post('api/updateProfile.action', { nickname: name });
      state.user = res.data || state.user;
      document.querySelectorAll('[data-username]').forEach(function (el) {
        el.textContent = name;
      });
      toast('昵称已更新', 'success');
    } catch (e) {
      toast('更新失败：' + e.message, 'error');
    }
  }

  async function savePassword() {
    var oldPwd = $('old-password').value;
    var newPwd = $('new-password').value;
    if (!oldPwd || !newPwd) {
      toast('请填写原密码和新密码', 'error');
      return;
    }
    if (newPwd.length < 6) {
      toast('新密码至少 6 位', 'error');
      return;
    }
    try {
      await Api.post('api/changePassword.action', { oldPassword: oldPwd, newPassword: newPwd });
      $('old-password').value = '';
      $('new-password').value = '';
      toast('密码已修改，下次登录用新密码', 'success');
    } catch (e) {
      toast('修改失败：' + e.message, 'error');
    }
  }

  async function exportQuestions() {
    var btn = $('export-questions');
    btn.disabled = true;
    btn.textContent = '导出中…';
    try {
      var all = [];
      var page = 1;
      var total = Infinity;
      while (page <= 100) {
        var res = await Api.get('api/questions.action', { page: page, size: 50 });
        var d = res.data || {};
        var list = d.list || [];
        total = d.total || 0;
        list.forEach(function (q) { all.push(q); });
        if (list.length < 50 || all.length >= total) break;
        page++;
      }
      var payload = {
        exportedAt: new Date().toISOString(),
        username: state.user ? state.user.username : '',
        count: all.length,
        questions: all
      };
      var blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' });
      var a = document.createElement('a');
      a.href = URL.createObjectURL(blob);
      a.download = '错题导出_' + stamp() + '.json';
      document.body.appendChild(a);
      a.click();
      a.remove();
      setTimeout(function () { URL.revokeObjectURL(a.href); }, 2000);
      toast('已导出 ' + all.length + ' 道错题', 'success');
    } catch (e) {
      toast('导出失败：' + e.message, 'error');
    } finally {
      btn.disabled = false;
      btn.textContent = '导出 JSON';
    }
  }

  function stamp() {
    var d = new Date();
    function p(n) { return n < 10 ? '0' + n : '' + n; }
    return d.getFullYear() + p(d.getMonth() + 1) + p(d.getDate()) + '_' + p(d.getHours()) + p(d.getMinutes());
  }

  /* ---------------- 诊断 ---------------- */
  async function loadDiag() {
    try {
      var res = await Api.get('api/settingsDiagnose.action');
      renderDiag(res.data || {});
    } catch (e) {
      $('diag-grid').innerHTML = '<span class="hint">加载失败：' + escapeHtml(e.message) + '</span>';
    }
  }

  function renderDiag(d) {
    var overrides = d.overridden || {};
    var rows = [
      ['数据库', (d.dbName || '') + ' · ' + (d.dbUrl || '')],
      ['生效提供方', (d.providerLabel || '') + '（配置值：' + (d.provider || '') + '）'],
      ['文字模型', d.model || ''],
      ['识图模型', (d.visionModel || '') + '（固定本机）'],
      ['云端厂商', d.cloudVendor || ''],
      ['云端 Key', d.cloudKeyConfigured
        ? '已配置（' + (d.cloudKeySource === 'settings' ? '设置页' : 'ai.properties') + '）'
        : '未配置'],
      ['本机 Ollama', d.ollamaReachable ? '可连接' : '不可连接'],
      ['上传目录', d.uploadsPath || '未获取到'],
      ['页面覆盖项', Object.keys(overrides).length + ' 项' + (Object.keys(overrides).length
        ? '：' + Object.keys(overrides).join(', ') : '')]
    ];
    $('diag-grid').innerHTML = rows.map(function (r) {
      return '<div class="diag-row"><span class="k">' + escapeHtml(r[0]) + '</span>'
        + '<span class="v">' + escapeHtml(r[1]) + '</span></div>';
    }).join('');

    var logs = d.recentLogs || [];
    $('diag-logs').innerHTML = logs.length
      ? logs.map(function (l) {
        return '<div class="diag-row"><span class="k">' + escapeHtml(formatDate(l.createdAt))
          + '</span><span class="v">' + escapeHtml(l.scene || '') + ' · '
          + escapeHtml(l.provider || '') + ' · ' + escapeHtml(l.model || '')
          + ' · ' + (l.elapsedMs || 0) + 'ms' + (l.success === 0 ? ' · 失败' : '')
          + '</span></div>';
      }).join('')
      : '暂无调用记录';
  }

  /* ---------------- 通用 ---------------- */
  function showMsg(text, type) {
    var el = $('msg');
    if (!text) {
      el.className = 'msg';
      el.textContent = '';
      return;
    }
    el.textContent = text;
    el.className = 'msg show ' + (type || 'error');
  }
})();
