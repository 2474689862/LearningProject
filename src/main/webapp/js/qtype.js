/* 题型模板组件：选择 / 判断 / 填空 / 简答 / 解答 / 阅读 / 实验 / 写作 / 听力 / 翻译 / 计算
 *
 * 用法：
 *   QTypes.mount(el, { type: 'choice', options: '["A. 1","B. 2"]', correctAnswer: 'B', answer: 'A' });
 *   QTypes.get(el)   -> { type, options: '["A. 1",...]', correctAnswer, answer }
 *   QTypes.setData(el, data)   // 识图回填 / 打开编辑弹窗时用
 *   QTypes.label(type)         // '选择题'
 */
window.QTypes = (function () {
  var TYPES = [
    { key: 'choice', label: '选择题', tip: '单选 A/B/C/D' },
    { key: 'judge', label: '判断题', tip: '正确 / 错误' },
    { key: 'blank', label: '填空题', tip: '按空填写' },
    { key: 'short', label: '简答题', tip: '要点作答' },
    { key: 'essay', label: '解答题', tip: '大题 / 步骤' },
    { key: 'reading', label: '阅读理解', tip: '材料 + 多小问' },
    { key: 'experiment', label: '实验题', tip: '现象 / 结论' },
    { key: 'writing', label: '写作题', tip: '作文 / 书面表达' },
    { key: 'listening', label: '听力题', tip: '听录音作答' },
    { key: 'translation', label: '翻译题', tip: '文言 / 英汉互译' },
    { key: 'calculation', label: '计算题', tip: '过程 + 结果' }
  ];

  var KEYS = TYPES.map(function (t) { return t.key; });

  function esc(s) {
    return String(s == null ? '' : s)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }

  function label(type) {
    for (var i = 0; i < TYPES.length; i++) {
      if (TYPES[i].key === type) return TYPES[i].label;
    }
    return '解答题';
  }

  function parseOptions(raw) {
    if (!raw) return [];
    if (Array.isArray(raw)) return raw.slice();
    var s = String(raw).trim();
    if (s.charAt(0) === '[') {
      try {
        var arr = JSON.parse(s);
        if (Array.isArray(arr)) return arr;
      } catch (e) { /* 退回按行拆分 */ }
    }
    return s.split(/\r?\n/).map(function (v) { return v.trim(); }).filter(Boolean);
  }

  /** 去掉选项文本里已有的「A.」「B、」前缀，字母由列表位置自动生成 */
  function stripKey(v) {
    var s = String(v == null ? '' : v).trim();
    if (s.length >= 2 && /[A-Za-z]/.test(s.charAt(0)) && '.、:：)） '.indexOf(s.charAt(1)) >= 0) {
      return s.substring(1).replace(/^[.、:：)）\s]+/, '').trim();
    }
    return s;
  }

  function normalize(data) {
    data = data || {};
    var type = KEYS.indexOf(data.type) >= 0 ? data.type : 'essay';
    return {
      type: type,
      options: parseOptions(data.options).map(stripKey),
      correctAnswer: data.correctAnswer == null ? '' : String(data.correctAnswer),
      answer: data.answer == null ? '' : String(data.answer)
    };
  }

  function chipsHtml(state) {
    return '<div class="field qtype-field"><label>题型 <span class="req">*</span>'
      + '<span class="hint" style="font-weight:400">不同题型用不同模板填写</span></label>'
      + '<div class="qtype-chips">' + TYPES.map(function (t) {
        return '<button type="button" class="qtype-chip' + (t.key === state.type ? ' active' : '')
          + '" data-qtype="' + t.key + '">' + t.label + '<small>' + esc(t.tip) + '</small></button>';
      }).join('') + '</div></div>';
  }

  function bodyHtml(type) {
    if (type === 'choice') {
      return '<div class="field"><label>选项 <span class="req">*</span>'
        + '<span class="hint" style="font-weight:400">至少 2 项，字母自动排</span></label>'
        + '<div class="opt-list" data-role="options"></div>'
        + '<button class="btn btn-sm" type="button" data-role="add-opt">＋ 添加选项</button></div>'
        + '<div class="row">'
        + '<div class="field"><label>标准答案</label><select data-role="correct"></select></div>'
        + '<div class="field"><label>我的作答</label>'
        + '<input type="text" data-role="answer" maxlength="120" placeholder="例如：B"></div>'
        + '</div>';
    }
    if (type === 'judge') {
      return '<div class="row">'
        + '<div class="field"><label>标准答案</label><select data-role="correct">'
        + '<option value="">未选择</option><option>正确</option><option>错误</option></select></div>'
        + '<div class="field"><label>我的作答</label><select data-role="answer">'
        + '<option value="">未作答</option><option>正确</option><option>错误</option></select></div>'
        + '</div>'
        + '<p class="qtype-note">判断题只需选对错；判断理由建议写在「错因分析」里。</p>';
    }
    if (type === 'blank') {
      return '<div class="field"><label>标准答案 <span class="hint" style="font-weight:400">每空一行，按顺序</span></label>'
        + '<textarea data-role="correct" rows="3" placeholder="第 1 空：2&#10;第 2 空：-1"></textarea></div>'
        + '<div class="field"><label>我的作答 <span class="hint" style="font-weight:400">与标准答案逐空对应</span></label>'
        + '<textarea data-role="answer" rows="3" placeholder="第 1 空：2&#10;第 2 空：1"></textarea></div>';
    }
    if (type === 'short') {
      return '<div class="field"><label>参考答案要点 <span class="hint" style="font-weight:400">分点写，AI 讲解会参考</span></label>'
        + '<textarea data-role="correct" rows="4" placeholder="1. …&#10;2. …&#10;3. …"></textarea></div>'
        + '<div class="field"><label>我的作答</label>'
        + '<textarea data-role="answer" rows="3" placeholder="写下你的简答内容"></textarea></div>';
    }
    if (type === 'reading') {
      return '<div class="field"><label>参考答案 <span class="hint" style="font-weight:400">按小问分点，用 1. 2. 3. 编号</span></label>'
        + '<textarea data-role="correct" rows="5" placeholder="1. …&#10;2. …&#10;3. …"></textarea></div>'
        + '<div class="field"><label>我的作答 <span class="hint" style="font-weight:400">与参考答案的小问一一对应</span></label>'
        + '<textarea data-role="answer" rows="4" placeholder="1. …&#10;2. …"></textarea></div>'
        + '<p class="qtype-note">阅读材料原文请贴在「题干」里，一篇材料有多个小问时按 1. 2. 3. 顺序作答。</p>';
    }
    if (type === 'experiment') {
      return '<div class="field"><label>实验结论 / 标准答案 <span class="hint" style="font-weight:400">现象、数据、结论分开写</span></label>'
        + '<textarea data-role="correct" rows="5" placeholder="现象：…&#10;数据：…&#10;结论：…&#10;表达式 / 注意事项：…"></textarea></div>'
        + '<div class="field"><label>我的作答 <span class="hint" style="font-weight:400">记录你观察到的现象与得出的结论</span></label>'
        + '<textarea data-role="answer" rows="4" placeholder="我的现象记录：…&#10;我的结论：…"></textarea></div>'
        + '<p class="qtype-note">实验目的、器材和步骤建议写在「题干」里，AI 讲解时会结合作答分析失分点。</p>';
    }
    if (type === 'writing') {
      return '<div class="field"><label>评分要点 / 范文</label>'
        + '<textarea data-role="correct" rows="4" placeholder="立意方向：…&#10;结构建议：…&#10;可参考的表达：…"></textarea></div>'
        + '<div class="field"><label>我的作文</label>'
        + '<textarea data-role="answer" rows="6" placeholder="贴上你写的作文或书面表达"></textarea></div>'
        + '<p class="qtype-note">作文题目、字数和文体要求在「题干」里填写。</p>';
    }
    if (type === 'listening') {
      return '<div class="field"><label>听力原文与答案</label>'
        + '<textarea data-role="correct" rows="4" placeholder="原文关键信息：…&#10;答案：…"></textarea></div>'
        + '<div class="field"><label>我的作答</label>'
        + '<textarea data-role="answer" rows="3" placeholder="写下你听到并记录的内容"></textarea></div>'
        + '<p class="qtype-note">听力材料（或来源说明）贴在「题干」里，便于日后复盘。</p>';
    }
    if (type === 'translation') {
      return '<div class="field"><label>参考译文</label>'
        + '<textarea data-role="correct" rows="3" placeholder="标准译文"></textarea></div>'
        + '<div class="field"><label>我的译文</label>'
        + '<textarea data-role="answer" rows="3" placeholder="你翻译的版本"></textarea></div>'
        + '<p class="qtype-note">需要翻译的原文写在「题干」里。</p>';
    }
    if (type === 'calculation') {
      return '<div class="field"><label>参考答案 / 计算过程</label>'
        + '<textarea data-role="correct" rows="4" placeholder="解：&#10;第一步…&#10;结果：…"></textarea></div>'
        + '<div class="field"><label>我的计算过程</label>'
        + '<textarea data-role="answer" rows="4" placeholder="写下你的计算步骤与结果"></textarea></div>';
    }
    return '<div class="field"><label>参考答案 / 解题步骤</label>'
      + '<textarea data-role="correct" rows="4" placeholder="第一步…&#10;第二步…&#10;结论：…"></textarea></div>'
      + '<div class="field"><label>我的作答</label>'
      + '<textarea data-role="answer" rows="3" placeholder="写下你的解答过程"></textarea></div>';
  }

  function renderOptions(body, options) {
    var list = body.querySelector('[data-role="options"]');
    if (!list) return;
    var items = options.length ? options : ['', '', '', ''];
    list.innerHTML = items.map(function (v, i) {
      return optionRowHtml(String.fromCharCode(65 + i), v);
    }).join('');
    syncCorrectOptions(body);
  }

  function optionRowHtml(letter, value) {
    return '<div class="opt-row">'
      + '<span class="opt-key">' + letter + '</span>'
      + '<input type="text" data-role="opt-input" maxlength="200" value="' + esc(value) + '" placeholder="选项内容">'
      + '<button class="opt-del" type="button" data-role="del-opt" title="删除">×</button>'
      + '</div>';
  }

  /** 选项变化后，同步「标准答案」下拉的字母与预览文字 */
  function syncCorrectOptions(body) {
    var sel = body.querySelector('[data-role="correct"]');
    if (!sel) return;
    var inputs = body.querySelectorAll('[data-role="opt-input"]');
    var current = sel.value;
    var html = '<option value="">未选择</option>';
    for (var i = 0; i < inputs.length; i++) {
      var letter = String.fromCharCode(65 + i);
      var text = (inputs[i].value || '').trim();
      html += '<option value="' + letter + '">' + letter
        + (text ? ' · ' + esc(text.length > 18 ? text.substring(0, 18) + '…' : text) : '') + '</option>';
    }
    sel.innerHTML = html;
    sel.value = current;
  }

  function renderBody(root) {
    var state = root.__qtype;
    var body = root.querySelector('[data-role="type-body"]');
    body.innerHTML = bodyHtml(state.type);

    // 题型按钮的选中态
    var chips = root.querySelectorAll('[data-qtype]');
    for (var i = 0; i < chips.length; i++) {
      chips[i].classList.toggle('active', chips[i].getAttribute('data-qtype') === state.type);
    }

    if (state.type === 'choice') {
      renderOptions(body, state.options);
      var sel = body.querySelector('[data-role="correct"]');
      if (state.correctAnswer) sel.value = state.correctAnswer.toUpperCase().charAt(0);
      body.querySelector('[data-role="answer"]').value = state.answer;
      bindChoiceBody(body);
    } else if (state.type === 'judge') {
      body.querySelector('[data-role="correct"]').value = state.correctAnswer;
      body.querySelector('[data-role="answer"]').value = state.answer;
    } else {
      body.querySelector('[data-role="correct"]').value = state.correctAnswer;
      body.querySelector('[data-role="answer"]').value = state.answer;
    }
  }

  function bindChoiceBody(body) {
    var list = body.querySelector('[data-role="options"]');
    list.addEventListener('input', function (e) {
      if (e.target.getAttribute('data-role') === 'opt-input') {
        syncCorrectOptions(body);
      }
    });
    list.addEventListener('click', function (e) {
      var btn = e.target.closest('[data-role="del-opt"]');
      if (!btn) return;
      var rows = list.querySelectorAll('.opt-row');
      if (rows.length <= 2) {
        toast('选择题至少保留 2 个选项', 'error');
        return;
      }
      btn.closest('.opt-row').remove();
      // 删除后重排字母与答案下拉
      var rest = list.querySelectorAll('[data-role="opt-input"]');
      for (var i = 0; i < rest.length; i++) {
        list.querySelectorAll('.opt-key')[i].textContent = String.fromCharCode(65 + i);
      }
      syncCorrectOptions(body);
    });
    body.querySelector('[data-role="add-opt"]').addEventListener('click', function () {
      var rows = list.querySelectorAll('.opt-row');
      if (rows.length >= 8) {
        toast('最多支持 8 个选项', 'error');
        return;
      }
      list.insertAdjacentHTML('beforeend', optionRowHtml(String.fromCharCode(65 + rows.length), ''));
      syncCorrectOptions(body);
      var inputs = list.querySelectorAll('[data-role="opt-input"]');
      inputs[inputs.length - 1].focus();
    });
  }

  function collect(root) {
    var state = root.__qtype;
    var body = root.querySelector('[data-role="type-body"]');
    var res = { type: state.type, options: [], correctAnswer: '', answer: '' };
    if (!body) return res;

    if (state.type === 'choice') {
      // 有空行时把选项前移，并同步换算标准答案的字母，避免键位错乱
      var values = [];
      var letterMap = {};
      var inputs = body.querySelectorAll('[data-role="opt-input"]');
      for (var i = 0; i < inputs.length; i++) {
        var v = (inputs[i].value || '').trim();
        if (v) {
          var oldLetter = String.fromCharCode(65 + i);
          var newLetter = String.fromCharCode(65 + values.length);
          letterMap[oldLetter] = newLetter;
          values.push(newLetter + '. ' + v);
        }
      }
      res.options = values;
      var sel = body.querySelector('[data-role="correct"]');
      var chosen = sel ? sel.value : '';
      res.correctAnswer = letterMap[chosen] || '';
      if (sel && res.correctAnswer) {
        sel.value = res.correctAnswer;
      }
      var ans = body.querySelector('[data-role="answer"]');
      res.answer = ans ? (ans.value || '').trim() : '';
    } else {
      var corr = body.querySelector('[data-role="correct"]');
      var ansInput = body.querySelector('[data-role="answer"]');
      res.correctAnswer = corr ? (corr.value || '').trim() : '';
      res.answer = ansInput ? (ansInput.value || '').trim() : '';
    }
    state.options = state.type === 'choice' ? res.options : [];
    state.correctAnswer = res.correctAnswer;
    state.answer = res.answer;
    return res;
  }

  function mount(root, data) {
    root.classList.add('qtype-root');
    root.__qtype = normalize(data);
    root.innerHTML = chipsHtml(root.__qtype) + '<div data-role="type-body"></div>';
    root.querySelector('.qtype-chips').addEventListener('click', function (e) {
      var chip = e.target.closest('[data-qtype]');
      if (!chip) return;
      var next = chip.getAttribute('data-qtype');
      if (next === root.__qtype.type) return;
      collect(root);                 // 先保存当前模板已填内容
      root.__qtype.type = next;
      if (next === 'choice' && !root.__qtype.options.length) {
        root.__qtype.options = ['', '', '', ''];
      }
      renderBody(root);
    });
    renderBody(root);
  }

  function setData(root, data) {
    root.__qtype = normalize(data);
    renderBody(root);
  }

  function get(root) {
    var res = collect(root);
    return {
      type: res.type,
      options: JSON.stringify(res.options),
      optionsArray: res.options,
      correctAnswer: res.correctAnswer,
      answer: res.answer
    };
  }

  return { TYPES: TYPES, mount: mount, setData: setData, get: get, label: label };
})();
