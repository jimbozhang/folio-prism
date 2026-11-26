/* 流水页：筛选、按日分组、6 类事件的新增/编辑/删除。 */
const LedgerView = (() => {
  const EVENT_TYPES = [
    { key: 'transfer', label: '转账' },
    { key: 'income', label: '收入' },
    { key: 'expense', label: '损耗' },
    { key: 'external', label: '外部资金' },
    { key: 'opening', label: '建仓' },
    { key: 'adjustment', label: '调整' },
  ];

  const TYPE_LABEL = {
    transfer: '转账', income: '收入', expense: '损耗',
    external: '外部资金', opening: '建仓', adjustment: '调整',
  };

  function instrumentOf(data, id) {
    return (data.instruments || []).find(function (item) { return item.id === id; });
  }

  function isCny(currency) {
    const upper = String(currency || '').toUpperCase();
    return upper === 'CNY' || upper === 'CNH';
  }

  function priceCurrencyOptions(instrument) {
    return [
      { value: 'native', label: instrument.currency || 'CNY' },
      { value: 'CNY', label: 'CNY' },
    ];
  }

  function institutionOf(data, id) {
    return (data.institutions || []).find(function (item) { return item.id === id; });
  }

  function categoryOf(data, id) {
    return (data.settings.categories || []).find(function (item) { return item.id === id; });
  }

  function isCash(data, instrumentId) {
    const instrument = instrumentOf(data, instrumentId);
    return !!instrument && instrument.kind === 'cash';
  }

  function institutionName(data, id) {
    const institution = institutionOf(data, id);
    return institution ? institution.name : id;
  }

  function positionLabel(data, ref) {
    if (!ref) return '--';
    const instrument = instrumentOf(data, ref.instrumentId);
    const name = instrument ? instrument.name : ref.instrumentId;
    return institutionName(data, ref.institutionId) + ' · ' + name;
  }

  function unitOf(data, instrumentId) {
    const instrument = instrumentOf(data, instrumentId);
    return instrument ? instrument.unit : '';
  }

  function currencyOf(data, instrumentId) {
    const instrument = instrumentOf(data, instrumentId);
    return instrument ? instrument.currency : 'CNY';
  }

  function refsOf(event) {
    const refs = [];
    function add(ref) { if (ref) refs.push(ref); }
    if (event.type === 'transfer') {
      add(event.from);
      add(event.to);
      add(event.feePosition);
    } else if (event.type === 'income') {
      add(event.source);
      add(event.destination);
    } else {
      add(event.position);
    }
    return refs;
  }

  function describe(event, data) {
    const note = event.note ? ' · ' + event.note : '';
    if (event.type === 'transfer') {
      const from = positionLabel(data, event.from);
      const to = positionLabel(data, event.to);
      const fromUnit = unitOf(data, event.from && event.from.instrumentId);
      const toUnit = unitOf(data, event.to && event.to.instrumentId);
      let sub = Fmt.quantity(event.fromQuantity) + ' ' + fromUnit + ' → ' +
        Fmt.quantity(event.toQuantity) + ' ' + toUnit;
      if (event.lossAmount && Fmt.num(event.lossAmount) !== 0) {
        sub += ' · 损耗 ' + Fmt.native(Fmt.money(event.lossAmount), event.lossCurrency || 'CNY');
      }
      return {
        title: '转出 ' + from + ' → 转入 ' + to,
        sub: sub + note,
        amount: Fmt.quantity(event.toQuantity) + ' ' + toUnit,
        amountClass: '',
      };
    }
    if (event.type === 'income') {
      const kind = event.kind === 'interest' ? '利息' : '分红';
      const source = event.source ? '来源 ' + positionLabel(data, event.source) : '无来源';
      return {
        title: kind + ' · ' + positionLabel(data, event.destination),
        sub: source + note,
        amount: Fmt.signedNative(event.amount, event.currency || 'CNY'),
        amountClass: 'pos',
      };
    }
    if (event.type === 'expense') {
      const category = categoryOf(data, event.categoryId);
      const position = event.position ? positionLabel(data, event.position) : '非现金损失';
      const value = Fmt.num(event.amount) || 0;
      return {
        title: (category ? category.name : event.categoryId) + ' · ' + position,
        sub: note.replace(/^ · /, ''),
        amount: (value >= 0 ? '-' : '+') + Fmt.native(Math.abs(value), event.currency || 'CNY'),
        amountClass: value >= 0 ? 'neg' : 'pos',
      };
    }
    if (event.type === 'external') {
      const isIn = event.direction === 'in';
      const classification = isIn ? '资本流入'
        : (event.classification === 'consumption' ? '消费' : '资本');
      return {
        title: (isIn ? '资金转入' : '资金转出') + ' · ' + positionLabel(data, event.position),
        sub: classification + note,
        amount: (isIn ? '+' : '-') + Fmt.native(event.amount, event.currency || 'CNY'),
        amountClass: isIn ? 'pos' : 'neg',
      };
    }
    if (event.type === 'opening') {
      const cash = isCash(data, event.position && event.position.instrumentId);
      const detail = cash
        ? '期初金额 ' + Fmt.native(event.amount, currencyOf(data, event.position && event.position.instrumentId))
        : '期初数量 ' + Fmt.quantity(event.quantity) + ' ' +
          unitOf(data, event.position && event.position.instrumentId);
      return {
        title: '期初建仓 · ' + positionLabel(data, event.position),
        sub: detail + note,
        amount: cash
          ? Fmt.native(event.amount, currencyOf(data, event.position && event.position.instrumentId))
          : Fmt.quantity(event.quantity) + ' ' + unitOf(data, event.position && event.position.instrumentId),
        amountClass: '',
      };
    }
    const cash = isCash(data, event.position && event.position.instrumentId);
    const delta = cash
      ? Fmt.signedNative(event.deltaAmount, currencyOf(data, event.position && event.position.instrumentId))
      : (Fmt.num(event.deltaQuantity) < 0 ? '-' : '+') +
        Fmt.quantity(Math.abs(Fmt.num(event.deltaQuantity) || 0)) + ' ' +
        unitOf(data, event.position && event.position.instrumentId);
    return {
      title: '调整 · ' + positionLabel(data, event.position),
      sub: (event.reason || '') + note,
      amount: delta,
      amountClass: (Fmt.num(cash ? event.deltaAmount : event.deltaQuantity) || 0) < 0 ? 'neg' : 'pos',
    };
  }

  function matches(event, filter, data) {
    if (filter.type !== 'all' && event.type !== filter.type) return false;
    // Same rule as the app: institution and instrument must be satisfied by
    // the SAME position reference, so a transfer touching two positions is
    // not matched by a combination no single leg fulfils.
    const institutionId = filter.institutionId === 'all' ? null : filter.institutionId;
    const instrumentId = filter.instrumentId === 'all' ? null : filter.instrumentId;
    if (institutionId !== null || instrumentId !== null) {
      const hit = refsOf(event).some(function (ref) {
        return (institutionId === null || ref.institutionId === institutionId) &&
          (instrumentId === null || ref.instrumentId === instrumentId);
      });
      if (!hit) return false;
    }
    if (filter.keyword) {
      const description = describe(event, data);
      const category = categoryOf(data, event.categoryId);
      const haystack = [
        description.title, description.sub, event.note, event.reason,
        category ? category.name : '',
        refsOf(event).map(function (ref) {
          const instrument = instrumentOf(data, ref.instrumentId);
          return institutionName(data, ref.institutionId) + ' ' +
            (instrument ? instrument.name + ' ' + instrument.code : ref.instrumentId);
        }).join(' '),
      ].join(' ').toLowerCase();
      if (haystack.indexOf(filter.keyword.toLowerCase()) < 0) return false;
    }
    return true;
  }

  function renderPage(root, state) {
    const page = el('div', 'page');
    const toolbarCard = el('div', 'card');
    const toolbar = el('div', 'ledger-toolbar');

    const typeOptions = [{ value: 'all', label: '全部类型' }].concat(
      EVENT_TYPES.map(function (type) { return { value: type.key, label: type.label }; }));
    const typeSelect = selectEl(typeOptions, state.ledger.type, function (value) {
      state.ledger.type = value;
      renderList(listCard, state);
    });

    const institutionOptions = [{ value: 'all', label: '全部机构' }].concat(
      (state.portfolio.institutions || []).map(function (item) {
        return { value: item.id, label: item.name };
      }));
    const institutionSelect = selectEl(institutionOptions, state.ledger.institutionId,
      function (value) {
        state.ledger.institutionId = value;
        renderList(listCard, state);
      });

    const instrumentOptions = [{ value: 'all', label: '全部标的' }].concat(
      (state.portfolio.instruments || []).map(function (item) {
        return { value: item.id, label: item.code + ' ' + item.name };
      }));
    const instrumentSelect = selectEl(instrumentOptions, state.ledger.instrumentId,
      function (value) {
        state.ledger.instrumentId = value;
        renderList(listCard, state);
      });

    // Tracked cash position: one specific account + instrument pair; any
    // account is selectable, the instrument name must end with 现金.
    const trackIndex = {};
    (state.portfolio.events || []).forEach(function (event) {
      refsOf(event).forEach(function (ref) {
        const instrument = instrumentOf(state.portfolio, ref.instrumentId);
        if (!instrument || instrument.kind !== 'cash' || !/现金$/.test(instrument.name)) return;
        const key = ref.institutionId + '|' + ref.instrumentId;
        if (trackIndex[key]) return;
        trackIndex[key] = institutionName(state.portfolio, ref.institutionId) +
          ' · ' + instrument.name;
      });
    });
    const trackOptions = [{ value: '', label: '不跟踪' }].concat(
      Object.keys(trackIndex)
        .sort(function (a, b) { return trackIndex[a].localeCompare(trackIndex[b], 'zh-CN'); })
        .map(function (key) { return { value: key, label: trackIndex[key] }; }));
    const knownTrack = trackOptions.some(function (option) {
      return option.value === state.ledger.trackedPosition;
    });
    if (!knownTrack) state.ledger.trackedPosition = '';
    const trackLabel = el('span', 'muted', '跟踪资产');
    const trackSelect = selectEl(trackOptions, state.ledger.trackedPosition,
      function (value) {
        state.ledger.trackedPosition = value;
        renderList(listCard, state);
      });

    const keyword = el('input', 'input wide');
    keyword.type = 'search';
    keyword.placeholder = '搜索备注、标的、机构…';
    keyword.value = state.ledger.keyword;
    keyword.addEventListener('input', function () {
      state.ledger.keyword = keyword.value;
      renderList(listCard, state);
    });

    const addBtn = el('button', 'btn primary', '新增流水');
    addBtn.addEventListener('click', function () { openEditor(null, state); });

    toolbar.appendChild(typeSelect);
    toolbar.appendChild(institutionSelect);
    toolbar.appendChild(instrumentSelect);
    toolbar.appendChild(trackLabel);
    toolbar.appendChild(trackSelect);
    toolbar.appendChild(keyword);
    const grow = el('span', 'grow');
    toolbar.appendChild(grow);
    toolbar.appendChild(addBtn);
    toolbarCard.appendChild(toolbar);
    page.appendChild(toolbarCard);

    const listCard = el('div', 'card');
    page.appendChild(listCard);
    root.appendChild(page);
    renderList(listCard, state);
  }

  function renderList(container, state) {
    clearNode(container);
    const data = state.portfolio;
    const filter = state.ledger;
    const events = (data.events || []).filter(function (event) {
      return matches(event, filter, data);
    });
    events.sort(function (a, b) {
      const ta = Fmt.timestamp(a.date);
      const tb = Fmt.timestamp(b.date);
      if (tb !== ta) return tb - ta;
      return a.id < b.id ? 1 : a.id > b.id ? -1 : 0;
    });
    if (events.length === 0) {
      container.appendChild(el('div', 'empty', '没有符合条件的流水'));
      return;
    }
    const groups = new Map();
    events.forEach(function (event) {
      const day = Fmt.datePart(event.date);
      if (!groups.has(day)) groups.set(day, []);
      groups.get(day).push(event);
    });
    const trackedPosition = state.ledger.trackedPosition;
    const balances = trackedBalances(data.events || [], trackedPosition);
    groups.forEach(function (list, day) {
      const group = el('div', 'ledger-day');
      const header = el('div', 'day-header');
      header.appendChild(el('span', null, Fmt.dateLabel(day)));
      header.appendChild(el('span', 'day-total', list.length + ' 笔'));
      group.appendChild(header);
      list.forEach(function (event) {
        group.appendChild(rowEl(event, data, state, balances, trackedPosition));
      });
      container.appendChild(group);
    });
  }

  function rowEl(event, data, state, balances, trackedPosition) {
    const description = describe(event, data);
    const row = el('div', 'ledger-row');
    row.appendChild(el('span', 'chip ' + event.type, TYPE_LABEL[event.type] || event.type));
    const main = el('div', 'ledger-main');
    main.appendChild(el('div', 'ledger-title', description.title));
    if (description.sub) main.appendChild(el('div', 'ledger-sub', description.sub));
    const balance = trackedPosition ? balances[event.id] : null;
    if (balance) {
      const instrument = instrumentOf(data, trackedPosition.split('|')[1]);
      const currency = instrument ? instrument.currency : 'CNY';
      main.appendChild(el('div', 'ledger-tracked',
        '跟踪余额 ' + Fmt.native(balance.before, currency) + ' → ' +
          Fmt.native(balance.after, currency)));
    }
    row.appendChild(main);
    row.appendChild(el('div', 'ledger-amount ' + (description.amountClass || ''), description.amount));
    row.addEventListener('click', function () { openEditor(event, state); });
    return row;
  }

  /**
   * Running balance of the tracked cash position (one account + instrument):
   * event id -> { before, after } (exact decimal strings). Events that do not
   * change it are omitted.
   */
  function trackedBalances(events, trackedPosition) {
    const result = {};
    if (!trackedPosition) return result;
    const ordered = events.slice().sort(function (a, b) {
      const ta = Fmt.timestamp(a.date);
      const tb = Fmt.timestamp(b.date);
      if (ta !== tb) return ta - tb;
      return a.id < b.id ? -1 : a.id > b.id ? 1 : 0;
    });
    let running = '0';
    ordered.forEach(function (event) {
      const delta = trackedDelta(event, trackedPosition);
      if (delta === null || Fmt.num(delta) === 0) return;
      const before = running;
      running = Fmt.add(running, delta);
      result[event.id] = { before: before, after: running };
    });
    return result;
  }

  /** Signed change of the tracked position caused by one event. */
  function trackedDelta(event, trackedPosition) {
    function negate(value) {
      return Fmt.mul(String(value), '-1');
    }
    function isTracked(ref) {
      return !!ref && (ref.institutionId + '|' + ref.instrumentId) === trackedPosition;
    }
    const parts = [];
    if (event.type === 'opening') {
      if (isTracked(event.position)) {
        const value = event.quantity === undefined || event.quantity === null
          ? event.amount : event.quantity;
        if (value !== undefined && value !== null) parts.push(String(value));
      }
    } else if (event.type === 'transfer') {
      if (isTracked(event.from) && event.fromQuantity !== undefined && event.fromQuantity !== null) {
        parts.push(negate(event.fromQuantity));
      }
      if (isTracked(event.to) && event.toQuantity !== undefined && event.toQuantity !== null) {
        parts.push(String(event.toQuantity));
      }
      if (isTracked(event.feePosition) && event.feeAmount !== undefined && event.feeAmount !== null) {
        parts.push(negate(event.feeAmount));
      }
    } else if (event.type === 'income') {
      if (isTracked(event.destination) && event.amount !== undefined && event.amount !== null) {
        parts.push(String(event.amount));
      }
    } else if (event.type === 'expense') {
      if (isTracked(event.position) && event.amount !== undefined && event.amount !== null) {
        parts.push(negate(event.amount));
      }
    } else if (event.type === 'external') {
      if (isTracked(event.position) && event.amount !== undefined && event.amount !== null) {
        parts.push(event.direction === 'in' ? String(event.amount) : negate(event.amount));
      }
    } else if (event.type === 'adjustment') {
      if (isTracked(event.position)) {
        const value = event.deltaQuantity === undefined || event.deltaQuantity === null
          ? event.deltaAmount : event.deltaQuantity;
        if (value !== undefined && value !== null) parts.push(String(value));
      }
    }
    if (parts.length === 0) return null;
    return Fmt.sum(parts);
  }

  function selectEl(options, value, onChange) {
    const select = el('select', 'select');
    options.forEach(function (option) {
      const node = el('option', null, option.label);
      node.value = option.value;
      if (option.value === value) node.selected = true;
      select.appendChild(node);
    });
    select.addEventListener('change', function () { onChange(select.value); });
    return select;
  }

  function institutionOptions(data) {
    return [{ value: '', label: '请选择机构' }].concat(
      (data.institutions || []).map(function (item) {
        return { value: item.id, label: item.name };
      }));
  }

  function instrumentOptions(data, kindFilter) {
    const list = (data.instruments || []).filter(function (item) {
      return kindFilter === 'cash' ? item.kind === 'cash' : true;
    });
    return [{ value: '', label: '请选择标的' }].concat(
      list.map(function (item) {
        return { value: item.id, label: item.code + ' ' + item.name };
      }));
  }

  function newDraft(type, data) {
    const firstCategory = (data.settings.categories || [])[0];
    return {
      type: type,
      date: Fmt.nowLocal(),
      note: '',
      fromInstitutionId: null,
      fromInstrumentId: null,
      fromQuantity: '',
      fromPrice: '',
      fromPriceInCny: false,
      toInstitutionId: null,
      toInstrumentId: null,
      toQuantity: '',
      toPrice: '',
      toPriceInCny: false,
      feeLoss: '',
      feeFromCash: false,
      feeInstitutionId: null,
      feeInstrumentId: null,
      incomeKind: 'dividend',
      incomeHasSource: true,
      sourceInstitutionId: null,
      sourceInstrumentId: null,
      incomeInstitutionId: null,
      incomeInstrumentId: null,
      incomeAmount: '',
      immediateWithdrawal: false,
      externalLabel: '',
      externalDirection: 'out',
      externalClassification: 'capital',
      externalInstitutionId: null,
      externalInstrumentId: null,
      externalAmount: '',
      expenseNoPosition: false,
      expenseInstitutionId: null,
      expenseInstrumentId: null,
      expenseAmount: '',
      expenseCategoryId: firstCategory ? firstCategory.id : 'cat_cash',
      openingInstitutionId: null,
      openingInstrumentId: null,
      openingValue: '',
      adjustmentInstitutionId: null,
      adjustmentInstrumentId: null,
      adjustmentValue: '',
      adjustmentReason: '',
    };
  }

  function draftFromEvent(event, data) {
    const draft = newDraft(event.type, data);
    draft.date = event.date || Fmt.nowLocal();
    draft.note = event.note || '';
    if (event.type === 'transfer') {
      draft.fromInstitutionId = event.from ? event.from.institutionId : null;
      draft.fromInstrumentId = event.from ? event.from.instrumentId : null;
      draft.fromQuantity = event.fromQuantity || '';
      draft.fromPrice = event.fromPrice || '';
      draft.toInstitutionId = event.to ? event.to.institutionId : null;
      draft.toInstrumentId = event.to ? event.to.instrumentId : null;
      draft.toQuantity = event.toQuantity || '';
      draft.toPrice = event.toPrice || '';
      draft.feeLoss = event.lossAmount || '';
      if (event.feePosition) {
        draft.feeFromCash = true;
        draft.feeInstitutionId = event.feePosition.institutionId;
        draft.feeInstrumentId = event.feePosition.instrumentId;
      }
    } else if (event.type === 'income') {
      draft.incomeKind = event.kind || 'dividend';
      draft.incomeHasSource = !!event.source;
      draft.sourceInstitutionId = event.source ? event.source.institutionId : null;
      draft.sourceInstrumentId = event.source ? event.source.instrumentId : null;
      draft.incomeInstitutionId = event.destination ? event.destination.institutionId : null;
      draft.incomeInstrumentId = event.destination ? event.destination.instrumentId : null;
      draft.incomeAmount = event.amount || '';
      draft.immediateWithdrawal = !!event.immediateExternalWithdrawal;
      draft.externalLabel = event.externalAccountLabel || '';
    } else if (event.type === 'expense') {
      draft.expenseNoPosition = !event.position;
      draft.expenseInstitutionId = event.position ? event.position.institutionId : null;
      draft.expenseInstrumentId = event.position ? event.position.instrumentId : null;
      draft.expenseAmount = event.amount || '';
      draft.expenseCategoryId = event.categoryId || draft.expenseCategoryId;
    } else if (event.type === 'external') {
      draft.externalDirection = event.direction || 'out';
      draft.externalClassification = event.classification || 'capital';
      draft.externalInstitutionId = event.position ? event.position.institutionId : null;
      draft.externalInstrumentId = event.position ? event.position.instrumentId : null;
      draft.externalAmount = event.amount || '';
    } else if (event.type === 'opening') {
      draft.openingInstitutionId = event.position ? event.position.institutionId : null;
      draft.openingInstrumentId = event.position ? event.position.instrumentId : null;
      draft.openingValue = isCash(data, event.position && event.position.instrumentId)
        ? (event.amount || '') : (event.quantity || '');
    } else if (event.type === 'adjustment') {
      draft.adjustmentInstitutionId = event.position ? event.position.institutionId : null;
      draft.adjustmentInstrumentId = event.position ? event.position.instrumentId : null;
      draft.adjustmentValue = isCash(data, event.position && event.position.instrumentId)
        ? (event.deltaAmount || '') : (event.deltaQuantity || '');
      draft.adjustmentReason = event.reason || '';
    }
    return draft;
  }

  function openEditor(event, state) {
    const data = state.portfolio;
    const editing = !!event;
    const draft = editing ? draftFromEvent(event, data) : newDraft('transfer', data);
    let currentType = draft.type;

    const modalRoot = document.getElementById('modal-root');
    clearNode(modalRoot);

    const overlay = el('div', 'modal-overlay');
    overlay.addEventListener('mousedown', function (e) {
      if (e.target === overlay) close();
    });
    const modal = el('div', 'modal');
    overlay.appendChild(modal);

    const header = el('div', 'modal-header');
    header.appendChild(el('h3', null, editing ? '编辑流水' : '新增流水'));
    const closeBtn = el('button', 'icon-btn close', '×');
    closeBtn.addEventListener('click', close);
    header.appendChild(closeBtn);
    modal.appendChild(header);

    const tabsWrap = el('div', 'modal-tabs');
    const tabs = el('div', 'seg');
    const typeButtons = [];
    EVENT_TYPES.forEach(function (type) {
      const btn = el('button', type.key === currentType ? 'active' : '', type.label);
      btn.disabled = editing;
      btn.title = editing ? '编辑时不能更改事件类型' : '';
      btn.addEventListener('click', function () {
        if (editing || currentType === type.key) return;
        currentType = type.key;
        const fresh = newDraft(type.key, data);
        fresh.date = draft.date;
        fresh.note = draft.note;
        Object.keys(draft).forEach(function (key) { delete draft[key]; });
        Object.assign(draft, fresh);
        updateTabs();
        renderBody();
      });
      typeButtons.push({ key: type.key, node: btn });
      tabs.appendChild(btn);
    });
    tabsWrap.appendChild(tabs);
    modal.appendChild(tabsWrap);

    const body = el('div', 'modal-body form-grid');
    modal.appendChild(body);

    const error = el('div', 'form-error');
    modal.appendChild(error);

    const footer = el('div', 'modal-footer');
    if (editing) {
      const deleteBtn = el('button', 'btn danger', '删除');
      deleteBtn.addEventListener('click', function () {
        if (!confirm('确定删除这笔流水吗？关联的自动生成流水也会一并删除。')) return;
        deleteBtn.disabled = true;
        Api.deleteEvent(event.id).then(async function () {
          close();
          await reload();
          render();
          showToast('流水已删除');
        }).catch(function (err) {
          deleteBtn.disabled = false;
          error.textContent = err.message;
        });
      });
      footer.appendChild(deleteBtn);
    }
    footer.appendChild(el('span', 'spacer'));
    const cancelBtn = el('button', 'btn', '取消');
    cancelBtn.addEventListener('click', close);
    footer.appendChild(cancelBtn);
    const saveBtn = el('button', 'btn primary', '保存');
    saveBtn.addEventListener('click', function () {
      saveBtn.disabled = true;
      error.textContent = '';
      const payload = Object.assign({}, draft);
      payload.type = currentType;
      const call = editing ? Api.updateEvent(event.id, payload) : Api.createEvent(payload);
      call.then(async function () {
        close();
        await reload();
        render();
        showToast(editing ? '流水已更新' : '流水已新增');
      }).catch(function (err) {
        saveBtn.disabled = false;
        error.textContent = err.message;
      });
    });
    footer.appendChild(saveBtn);
    modal.appendChild(footer);

    function updateTabs() {
      typeButtons.forEach(function (item) {
        item.node.className = item.key === currentType ? 'active' : '';
      });
    }

    function close() {
      clearNode(modalRoot);
    }

    function renderBody() {
      clearNode(body);
      renderFields(currentType, data, draft, body, renderBody);
    }

    renderBody();
    modalRoot.appendChild(overlay);
    const firstInput = modal.querySelector('select, input');
    if (firstInput) firstInput.focus();
  }

  function renderFields(type, data, draft, body, rerender) {
    function field(label, input, full) {
      const wrap = el('div', 'field' + (full ? ' full' : ''));
      if (label) wrap.appendChild(el('label', null, label));
      wrap.appendChild(input);
      return wrap;
    }

    function textInput(value, onChange, placeholder) {
      const input = el('input', 'input');
      input.type = 'text';
      input.value = value == null ? '' : value;
      if (placeholder) input.placeholder = placeholder;
      input.addEventListener('input', function () { onChange(input.value); });
      return input;
    }

    function checkbox(label, checked, onChange) {
      const wrap = el('label', 'checkbox');
      const input = document.createElement('input');
      input.type = 'checkbox';
      input.checked = !!checked;
      input.addEventListener('change', function () { onChange(input.checked); });
      wrap.appendChild(input);
      wrap.appendChild(document.createTextNode(label));
      return wrap;
    }

    function selector(options, value, onChange) {
      const select = el('select', 'select');
      options.forEach(function (option) {
        const node = el('option', null, option.label);
        node.value = option.value;
        if (option.value === value) node.selected = true;
        select.appendChild(node);
      });
      select.addEventListener('change', function () { onChange(select.value); });
      return select;
    }

    function positionBlock(labelPrefix, kindFilter, insKey, instKey) {
      body.appendChild(field(labelPrefix + '机构',
        selector(institutionOptions(data), draft[insKey] || '', function (value) {
          draft[insKey] = value || null;
        })));
      body.appendChild(field(labelPrefix + '标的',
        selector(instrumentOptions(data, kindFilter), draft[instKey] || '', function (value) {
          draft[instKey] = value || null;
          rerender();
        })));
    }

    if (type === 'transfer') {
      body.appendChild(el('div', 'section-label', '转出'));
      positionBlock('转出', 'all', 'fromInstitutionId', 'fromInstrumentId');
      body.appendChild(field('转出数量',
        textInput(draft.fromQuantity, function (value) { draft.fromQuantity = value; }, '如 1000')));
      const fromInstrument = instrumentOf(data, draft.fromInstrumentId);
      if (fromInstrument && fromInstrument.kind === 'quoted') {
        body.appendChild(field('转出单价',
          textInput(draft.fromPrice, function (value) { draft.fromPrice = value; })));
        if (!isCny(fromInstrument.currency)) {
          body.appendChild(field('转出单价币种',
            selector(priceCurrencyOptions(fromInstrument),
              draft.fromPriceInCny ? 'CNY' : 'native',
              function (value) {
                draft.fromPriceInCny = value === 'CNY';
                draft.fromPrice = '';
                rerender();
              })));
        }
      }

      body.appendChild(el('div', 'section-label', '转入'));
      positionBlock('转入', 'all', 'toInstitutionId', 'toInstrumentId');
      body.appendChild(field('转入数量',
        textInput(draft.toQuantity, function (value) { draft.toQuantity = value; }, '如 1000')));
      const toInstrument = instrumentOf(data, draft.toInstrumentId);
      if (toInstrument && toInstrument.kind === 'quoted') {
        body.appendChild(field('转入单价',
          textInput(draft.toPrice, function (value) { draft.toPrice = value; })));
        if (!isCny(toInstrument.currency)) {
          body.appendChild(field('转入单价币种',
            selector(priceCurrencyOptions(toInstrument),
              draft.toPriceInCny ? 'CNY' : 'native',
              function (value) {
                draft.toPriceInCny = value === 'CNY';
                draft.toPrice = '';
                rerender();
              })));
        }
      }

      body.appendChild(el('div', 'section-label', '手续费 / 损耗'));
      body.appendChild(field('损耗金额（留空则自动计算）',
        textInput(draft.feeLoss, function (value) { draft.feeLoss = value; })));
      body.appendChild(field('',
        checkbox('从现金账户支付手续费', draft.feeFromCash, function (checked) {
          draft.feeFromCash = checked;
          rerender();
        }), true));
      if (draft.feeFromCash) {
        positionBlock('手续费', 'cash', 'feeInstitutionId', 'feeInstrumentId');
      }
    } else if (type === 'income') {
      body.appendChild(field('类型',
        selector([{ value: 'dividend', label: '分红' }, { value: 'interest', label: '利息' }],
          draft.incomeKind, function (value) { draft.incomeKind = value; })));
      body.appendChild(field('',
        checkbox('有来源持仓', draft.incomeHasSource, function (checked) {
          draft.incomeHasSource = checked;
          rerender();
        }), true));
      if (draft.incomeHasSource) {
        positionBlock('来源', 'all', 'sourceInstitutionId', 'sourceInstrumentId');
      }
      body.appendChild(el('div', 'section-label', '入账'));
      positionBlock('入账', 'cash', 'incomeInstitutionId', 'incomeInstrumentId');
      body.appendChild(field('金额',
        textInput(draft.incomeAmount, function (value) { draft.incomeAmount = value; })));
      body.appendChild(field('',
        checkbox('立即转出（记一笔外部转出）', draft.immediateWithdrawal, function (checked) {
          draft.immediateWithdrawal = checked;
          rerender();
        }), true));
      if (draft.immediateWithdrawal) {
        body.appendChild(field('转出账号备注',
          textInput(draft.externalLabel, function (value) { draft.externalLabel = value; })));
      }
    } else if (type === 'expense') {
      body.appendChild(field('',
        checkbox('无持仓（非现金损失）', draft.expenseNoPosition, function (checked) {
          draft.expenseNoPosition = checked;
          rerender();
        }), true));
      if (!draft.expenseNoPosition) {
        positionBlock('支付账户', 'cash', 'expenseInstitutionId', 'expenseInstrumentId');
      }
      body.appendChild(field('金额（可为负）',
        textInput(draft.expenseAmount, function (value) { draft.expenseAmount = value; })));
      body.appendChild(field('类别',
        selector((data.settings.categories || []).map(function (category) {
          return { value: category.id, label: category.name };
        }), draft.expenseCategoryId, function (value) { draft.expenseCategoryId = value; })));
    } else if (type === 'external') {
      body.appendChild(field('方向',
        selector([{ value: 'in', label: '入金' }, { value: 'out', label: '出金' }],
          draft.externalDirection, function (value) {
            draft.externalDirection = value;
            rerender();
          })));
      if (draft.externalDirection === 'out') {
        body.appendChild(field('分类',
          selector([{ value: 'capital', label: '资本' }, { value: 'consumption', label: '消费' }],
            draft.externalClassification, function (value) { draft.externalClassification = value; })));
      }
      positionBlock('现金账户', 'cash', 'externalInstitutionId', 'externalInstrumentId');
      body.appendChild(field('金额',
        textInput(draft.externalAmount, function (value) { draft.externalAmount = value; })));
    } else if (type === 'opening') {
      positionBlock('建仓', 'all', 'openingInstitutionId', 'openingInstrumentId');
      const cash = isCash(data, draft.openingInstrumentId);
      body.appendChild(field(cash ? '期初金额' : '期初份额',
        textInput(draft.openingValue, function (value) { draft.openingValue = value; })));
    } else if (type === 'adjustment') {
      positionBlock('调整', 'all', 'adjustmentInstitutionId', 'adjustmentInstrumentId');
      const cash = isCash(data, draft.adjustmentInstrumentId);
      body.appendChild(field(cash ? '金额增减' : '份额增减',
        textInput(draft.adjustmentValue, function (value) { draft.adjustmentValue = value; })));
      body.appendChild(field('调整原因',
        textInput(draft.adjustmentReason, function (value) { draft.adjustmentReason = value; }), true));
    }

    body.appendChild(el('div', 'section-label', '其他'));
    const dateInput = el('input', 'input');
    dateInput.type = 'datetime-local';
    dateInput.value = Fmt.datetimeLocal(draft.date);
    dateInput.addEventListener('input', function () {
      draft.date = dateInput.value.replace('T', ' ');
    });
    body.appendChild(field('日期时间', dateInput));
    body.appendChild(field('备注',
      textInput(draft.note, function (value) { draft.note = value; }, '可选')));
  }

  return { render: renderPage, openEditor: openEditor };
})();
