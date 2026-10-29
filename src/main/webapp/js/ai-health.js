/* AI 模型健康检查：进入 AI 相关页面时自动探测一次，模型不响应则在页面顶部给出警告 */
(function (global) {
  'use strict';

  const TIMEOUT_MS = 30000; // 后端 ollama 最长等 20s，前端再留余量
  let checking = false;
  let lastResult = null;

  function pageRoot() {
    return document.querySelector('main.page') || document.body;
  }

  function ensureBox() {
    let box = document.getElementById('ai-alert');
    if (box) return box;
    box = document.createElement('div');
    box.id = 'ai-alert';
    box.className = 'ai-alert';
    box.innerHTML = '<span class="ico" aria-hidden="true">⚠</span>'
      + '<div class="ai-alert-body"><b id="ai-alert-title"></b><span id="ai-alert-text"></span></div>'
      + '<div class="ai-alert-actions">'
      + '<button class="btn btn-sm" type="button" id="ai-alert-retry">重新检测</button>'
      + '<button class="btn btn-sm btn-ghost" type="button" id="ai-alert-close">知道了</button>'
      + '</div>';
    const root = pageRoot();
    root.insertBefore(box, root.firstChild);
    box.querySelector('#ai-alert-retry').addEventListener('click', function () {
      check({ force: true, toastOnError: true });
    });
    box.querySelector('#ai-alert-close').addEventListener('click', hide);
    return box;
  }

  function show(title, text, level) {
    const box = ensureBox();
    box.className = 'ai-alert show' + (level === 'error' ? ' error' : (level === 'info' ? ' info' : ''));
    box.querySelector('#ai-alert-title').textContent = title;
    box.querySelector('#ai-alert-text').textContent = text;
    const retry = box.querySelector('#ai-alert-retry');
    if (retry) retry.style.display = (level === 'info') ? 'none' : '';
  }

  function hide() {
    const box = document.getElementById('ai-alert');
    if (box) box.className = 'ai-alert';
  }

  function withTimeout(promise, ms) {
    return new Promise(function (resolve, reject) {
      const timer = setTimeout(function () {
        reject(new Error('检测超时（' + Math.round(ms / 1000) + ' 秒无响应）'));
      }, ms);
      promise.then(function (v) { clearTimeout(timer); resolve(v); },
        function (e) { clearTimeout(timer); reject(e); });
    });
  }

  /**
   * 检测一次模型状态。
   * @param {{force?: boolean, toastOnError?: boolean}} options force=跳过服务端缓存
   */
  async function check(options) {
    const opts = options || {};
    if (checking) return lastResult;
    checking = true;
    // 模型冷启动可能等十几秒，超过 5 秒先给个"检测中"的轻提示
    const pendingTimer = setTimeout(function () {
      if (checking) {
        show('正在检测 AI 模型…', '正在确认模型是否响应，最多等待约 20 秒。', 'info');
      }
    }, 5000);
    try {
      const res = await withTimeout(
        Api.get('api/aiHealth.action', opts.force ? { force: 1 } : null), TIMEOUT_MS);
      lastResult = res.data || {};
      applyResult(lastResult, opts);
      return lastResult;
    } catch (e) {
      const msg = (e && e.message) ? e.message : '未知错误';
      show('AI 模型状态检测失败',
        '无法获取模型状态：' + msg + '。生成解析、学习总结、图片识别都可能失败，请检查服务后点「重新检测」。',
        'error');
      if (opts.toastOnError) toast('AI 模型状态检测失败：' + msg, 'error');
      return null;
    } finally {
      clearTimeout(pendingTimer);
      checking = false;
    }
  }

  function applyResult(d, opts) {
    if (d.busy) {
      hide();
      return;
    }
    if (d.ok && d.visionReady === false) {
      show('识图模型未就绪',
        '文字解析正常，但图片识别所需的 ' + (d.visionModel || '视觉模型')
        + ' 未就绪，拍照上传会退回本地规则。' + (d.reason ? '原因：' + d.reason : '请执行 ollama pull ' + (d.visionModel || '视觉模型')),
        'warn');
      return;
    }
    if (d.ok) {
      hide();
      return;
    }
    const isLocal = d.provider === 'local';
    const label = d.providerLabel || d.provider || 'AI 模型';
    const title = isLocal ? '当前没有可用的 AI 模型，已退回本地规则模式' : label + ' 没有响应';
    let text = d.reason || '未知原因';
    text += isLocal
      ? '。AI 解析、学习总结与对话会使用本地规则模板生成，内容会比较简单。'
      : '。此时生成解析、学习总结、图片识别会自动退回本地规则，结果可能不准确；请检查模型服务后点「重新检测」。';
    if (d.checkedAt) text += '（检测时间 ' + d.checkedAt + '）';
    show(title, text, isLocal ? 'warn' : 'error');
    if (!isLocal && opts && opts.toastOnError) {
      toast(label + ' 没有响应，AI 功能已降级', 'error');
    }
  }

  global.AiHealth = {
    check: check,
    result: function () { return lastResult; },
    hide: hide
  };
})(window);
