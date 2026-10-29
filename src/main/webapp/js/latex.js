/* 公式渲染：把文本里的 LaTeX（$...$ / $$...$$ / \(...\) / \[...\]）交给 KaTeX 渲染。
 * KaTeX 未加载或渲染出错时保留原始文本，不影响页面其它功能。 */
(function (global) {
  var DELIMITERS = [
    { left: '$$', right: '$$', display: true },
    { left: '\\[', right: '\\]', display: true },
    { left: '$', right: '$', display: false },
    { left: '\\(', right: '\\)', display: false }
  ];

  var IGNORED = ['script', 'noscript', 'style', 'textarea', 'pre', 'code', 'option'];

  /** 设置页可关闭公式渲染（仅本机偏好，默认开启） */
  function enabled() {
    try {
      return global.localStorage.getItem('aistudy-katex') !== '0';
    } catch (e) {
      return true;
    }
  }

  function available() {
    return enabled() && !!(global.katex && global.renderMathInElement);
  }

  function looksLikeMath(text) {
    return /\$|\\\(|\\\[|\\frac|\\sqrt/.test(text || '');
  }

  /** 用纯文本填充元素，再渲染其中的公式（textContent 赋值天然避免 XSS） */
  function renderIn(el, text) {
    if (!el) return;
    el.textContent = text == null ? '' : String(text);
    if (!available()) return;
    try {
      global.renderMathInElement(el, {
        delimiters: DELIMITERS,
        throwOnError: false,
        strict: false,
        errorColor: '#c0392b',
        ignoredTags: IGNORED
      });
    } catch (e) {
      el.textContent = text == null ? '' : String(text);
    }
  }

  /** 遍历容器内的公式文本块（已是 HTML 转义后的文本），逐个渲染 */
  function renderBlocks(root, selector) {
    if (!root) return;
    var list = root.querySelectorAll(selector || '.latex');
    for (var i = 0; i < list.length; i++) {
      renderIn(list[i], list[i].textContent);
    }
  }

  global.Latex = {
    available: available,
    looksLikeMath: looksLikeMath,
    renderIn: renderIn,
    renderBlocks: renderBlocks
  };
})(window);
