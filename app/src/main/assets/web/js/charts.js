/* 图表页：历史曲线（资金流归因）、分类盈亏、投入取出、调仓统计、分红统计、机构/币种分布。 */
const ChartsView = (() => {
  let chart = null;
  let requestSeq = 0;
  // View options of the asset charts: adjusted curve and flow markers.
  let historyView = { adjusted: false, markers: false };

  const TYPES = [
    // 总资产页内置类别切换：未选类别时显示全组合，选中类别时只统计该类别。
    { key: 'assets', label: '总资产', kind: 'history', category: true },
    { key: 'category_pnl', label: '类别盈亏', kind: 'history' },
    { key: 'flows', label: '投入取出', kind: 'flows' },
    { key: 'rebalance', label: '调仓统计', kind: 'rebalance' },
    { key: 'dividends', label: '分红统计', kind: 'dividends' },
    { key: 'institutions', label: '机构分布', kind: 'snapshot' },
    { key: 'currencies', label: '币种分布', kind: 'snapshot' },
  ];

  const RANGES = [
    { key: 'MTD', label: '月初至今' },
    { key: '1M', label: '近 1 月' },
    { key: '3M', label: '近 3 月' },
    { key: 'YTD', label: '年初至今' },
    { key: '1Y', label: '近 1 年' },
    { key: 'ALL', label: '全部' },
  ];

  function defaultRange(type) {
    return type === 'category_pnl' ? 'MTD' : 'YTD';
  }

  function pad(n) { return String(n).padStart(2, '0'); }
  function iso(date) {
    return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate());
  }

  function earliestDate(portfolio) {
    let min = null;
    (portfolio.events || []).forEach(function (event) {
      const date = Fmt.datePart(event.date);
      if (date && (min === null || date < min)) min = date;
    });
    return min;
  }

  function rangeFor(preset, state) {
    const today = new Date();
    let from = new Date(today.getFullYear(), today.getMonth(), today.getDate());
    if (preset === 'MTD') {
      from = new Date(today.getFullYear(), today.getMonth(), 1);
    } else if (preset === '1M') {
      from = new Date(today.getFullYear(), today.getMonth() - 1, today.getDate());
    } else if (preset === '3M') {
      from = new Date(today.getFullYear(), today.getMonth() - 3, today.getDate());
    } else if (preset === 'YTD') {
      from = new Date(today.getFullYear(), 0, 1);
    } else if (preset === '1Y') {
      from = new Date(today.getFullYear() - 1, today.getMonth(), today.getDate());
    } else if (preset === 'ALL') {
      const earliest = earliestDate(state.portfolio);
      if (earliest) {
        const parts = earliest.split('-').map(Number);
        from = new Date(parts[0], parts[1] - 1, parts[2]);
      } else {
        from = new Date(today.getFullYear() - 1, today.getMonth(), today.getDate());
      }
    }
    return { from: iso(from), to: iso(today) };
  }

  function destroy() {
    requestSeq += 1;
    if (chart) {
      chart.destroy();
      chart = null;
    }
  }

  function typeDef(key) {
    return TYPES.find(function (item) { return item.key === key; }) || TYPES[0];
  }

  return {
    destroy,

    render(root, state) {
      destroy();
      clearNode(root);
      const current = typeDef(state.charts.type);
      const page = el('div', 'page');

      const controls = el('div', 'card');
      const typeSeg = el('div', 'seg');
      TYPES.forEach(function (type) {
        const btn = el('button', type.key === current.key ? 'active' : '', type.label);
        btn.addEventListener('click', function () {
          if (state.charts.type === type.key) return;
          state.charts.type = type.key;
          historyView = { adjusted: false, markers: false };
          ChartsView.render(root, state);
        });
        typeSeg.appendChild(btn);
      });
      const toolbar = el('div', 'controls');
      toolbar.appendChild(typeSeg);

      const rangeKey = state.charts.ranges[current.key] || defaultRange(current.key);
      if (current.kind !== 'snapshot') {
        const rangeSeg = el('div', 'seg');
        RANGES.forEach(function (range) {
          const btn = el('button', range.key === rangeKey ? 'active' : '', range.label);
          btn.addEventListener('click', function () {
            if ((state.charts.ranges[current.key] || defaultRange(current.key)) === range.key) return;
            state.charts.ranges[current.key] = range.key;
            ChartsView.render(root, state);
          });
          rangeSeg.appendChild(btn);
        });
        toolbar.appendChild(rangeSeg);
      }

      let categoryId = '';
      if (current.category) {
        const categories = state.portfolio.settings.categories || [];
        const select = el('select', 'select');
        const total = el('option', null, '总资产');
        total.value = '';
        select.appendChild(total);
        categories.forEach(function (category) {
          const option = el('option', null, category.name);
          option.value = category.id;
          select.appendChild(option);
        });
        categoryId = state.charts.categoryId || '';
        if (!categories.some(function (category) { return category.id === categoryId; })) {
          categoryId = '';
        }
        select.value = categoryId;
        select.addEventListener('change', function () {
          state.charts.categoryId = select.value;
          historyView = { adjusted: false, markers: false };
          ChartsView.render(root, state);
        });
        toolbar.appendChild(select);
      }

      controls.appendChild(toolbar);
      page.appendChild(controls);

      const content = el('div', 'card');
      page.appendChild(content);
      root.appendChild(page);

      this.load(content, state, current, rangeKey, categoryId);
    },

    async load(content, state, current, rangeKey, categoryId) {
      clearNode(content);
      content.appendChild(el('div', 'loading', '正在加载…'));
      const seq = ++requestSeq;
      try {
        if (current.kind === 'snapshot') {
          const rows = current.key === 'institutions'
            ? state.overview.institutions
            : state.overview.currencies;
          if (seq !== requestSeq) return;
          clearNode(content);
          this.renderDistribution(content, current.key, rows);
          return;
        }
        const range = rangeFor(rangeKey, state);
        let data;
        if (current.kind === 'flows') {
          data = await Api.flows(range.from, range.to);
        } else if (current.kind === 'rebalance') {
          data = await Api.rebalance(range.from, range.to);
        } else if (current.kind === 'dividends') {
          data = await Api.dividends(range.from, range.to);
        } else {
          // The assets view reuses the category_assets API when scoped to one category.
          const apiType = current.key === 'assets' && categoryId
            ? 'category_assets'
            : current.key;
          data = await Api.chart(apiType, range.from, range.to, categoryId || null);
        }
        if (seq !== requestSeq) return;
        clearNode(content);
        this.renderContent(content, state, current, data);
      } catch (err) {
        if (seq !== requestSeq) return;
        clearNode(content);
        content.appendChild(el('div', 'banner error', '加载失败：' + err.message));
      }
    },

    renderContent(content, state, current, data) {
      content.appendChild(el('h2', 'card-title', current.label));

      if (current.key === 'assets') {
        this.renderHistory(content, state, current, data);
        return;
      }
      if (current.key === 'category_pnl') {
        this.renderCategoryPnl(content, data);
        return;
      }
      if (current.key === 'dividends') {
        this.renderDividends(content, state, data);
        return;
      }
      if (current.key === 'flows') {
        this.renderFlows(content, data);
        return;
      }
      if (current.key === 'rebalance') {
        this.renderRebalance(content, state, data);
      }
    },

    renderHistory(content, state, current, data) {
      // Rebuilding the body keeps the card title while toggles re-render.
      let body = content.querySelector('.history-body');
      if (body) {
        clearNode(body);
      } else {
        body = el('div', 'history-body');
        content.appendChild(body);
      }
      this.renderHistoryBody(body, state, current, data);
    },

    renderHistoryBody(body, state, current, data) {
      const isCategory = !!data.category;
      const summary = data.summary || {};
      const rows = [];
      rows.push({ label: '期初', value: cnyOrDash(summary.startValue) });
      const inflow = summary.inflowCny;
      const outflow = summary.outflowCny;
      const net = Fmt.add(inflow || '0', '-' + (outflow || '0'));
      rows.push({
        label: isCategory ? '资金流' : '出入金',
        value: cnyOrDash(net),
        sub: isCategory
          ? '流入 ' + cnyOrDash(inflow) + ' · 流出 ' + cnyOrDash(outflow)
          : '入金 ' + cnyOrDash(inflow) + ' · 出金 ' + cnyOrDash(outflow),
      });
      const market = summary.marketPnlCny;
      const marketNum = Fmt.num(market);
      rows.push({
        label: '市场盈亏',
        value: cnyOrDash(market),
        sub: '价格 + 分红 − 费用',
        valueClass: marketNum === null || marketNum === 0 ? '' : (marketNum > 0 ? 'pos' : 'neg'),
      });
      rows.push({ label: '期末', value: cnyOrDash(summary.endValue), emphasize: true });
      body.appendChild(summaryTable(rows));

      const rerender = function () {
        ChartsView.destroy();
        clearNode(body);
        ChartsView.renderHistoryBody(body, state, current, data);
      };
      const chips = el('div', 'mode-chips');
      chips.appendChild(modeChip('含资金流', !historyView.adjusted, function () {
        historyView.adjusted = false;
        rerender();
      }));
      chips.appendChild(modeChip('剔除资金流', historyView.adjusted, function () {
        historyView.adjusted = true;
        rerender();
      }));
      chips.appendChild(modeChip('资金标记', historyView.markers, function () {
        historyView.markers = !historyView.markers;
        rerender();
      }));
      body.appendChild(chips);
      if (historyView.adjusted) {
        const adjusted = data.adjustedPoints || [];
        const first = firstValue(adjusted);
        const last = lastValue(adjusted);
        if (first !== null && last !== null && first > 0) {
          const ret = (last - first) / first;
          const label = el('div', 'rb-return ' + (ret >= 0 ? 'pos' : 'neg'),
            '区间市场收益 ' + (ret * 100).toFixed(1) + '%');
          body.appendChild(label);
        }
      }

      const useAdjusted = historyView.adjusted && (data.adjustedPoints || []).length > 0;
      const points = useAdjusted ? data.adjustedPoints : data.points;
      const color = isCategory && data.category ? data.category.color : '#e6a23c';
      const label = isCategory && data.category ? data.category.name : '总资产';
      const values = points.map(function (point) { return Fmt.num(point.value); });
      const flowsByDate = {};
      if (historyView.markers) {
        (data.flows || []).forEach(function (flow) {
          const netValue = Fmt.num(flow.netCny);
          if (netValue !== null && netValue !== 0) flowsByDate[flow.date] = netValue;
        });
      }
      this.mount(body, {
        type: 'line',
        data: {
          labels: points.map(function (point) { return point.date; }),
          datasets: [{
            label: label,
            data: values,
            borderColor: color,
            backgroundColor: hexToRgba(color, 0.10),
            fill: true,
            tension: 0.25,
            pointRadius: values.length > 60 ? 0 : 2,
            borderWidth: 2,
            spanGaps: false,
          }],
        },
        options: lineOptions(label),
        plugins: [flowMarkerPlugin(flowsByDate, historyView.markers)],
      });

      if (historyView.markers) {
        const legend = el('div', 'chart-legend');
        legend.appendChild(el('span', 'up', isCategory ? '▲ 流入' : '▲ 入金'));
        legend.appendChild(el('span', 'down', isCategory ? '▼ 流出' : '▼ 出金'));
        body.appendChild(legend);
      }
    },

    renderCategoryPnl(content, data) {
      this.mount(content, {
        type: 'bar',
        data: {
          labels: data.categoryPnl.map(function (item) { return item.name; }),
          datasets: [{
            label: '盈亏',
            data: data.categoryPnl.map(function (item) {
              return item.pnlCny === null ? null : Fmt.num(item.pnlCny);
            }),
            backgroundColor: data.categoryPnl.map(function (item) {
              return item.incomplete ? '#d9d9d9' : item.color;
            }),
            borderRadius: 6,
          }],
        },
        options: barOptions('盈亏', true),
      });
      const rows = data.categoryPnl.map(function (item) {
        return [item.name, item.pnlCny === null ? '--' : Fmt.cny(item.pnlCny),
          item.incomplete ? '数据不完整' : ''];
      });
      content.appendChild(table(['类别', '盈亏'], rows, [false, true]));
    },

    renderFlows(content, data) {
      this.mount(content, {
        type: 'bar',
        data: {
          labels: data.months.map(function (month) { return month.month; }),
          datasets: [
            {
              label: '流入',
              data: data.months.map(function (month) { return Fmt.num(month.inflowCny); }),
              backgroundColor: '#2e9e6b',
              borderRadius: 4,
            },
            {
              label: '流出',
              data: data.months.map(function (month) { return Fmt.num(month.outflowCny); }),
              backgroundColor: '#e8613c',
              borderRadius: 4,
            },
          ],
        },
        options: barOptions('金额', true),
      });
      const rows = data.months.map(function (month) {
        return [month.month, Fmt.cny(month.inflowCny), Fmt.cny(month.outflowCny)];
      });
      content.appendChild(table(['月份', '流入', '流出'], rows, [false, true, true]));
    },

    renderRebalance(content, state, data) {
      if (data.incomplete) {
        content.appendChild(el('div', 'banner warn', '部分调仓缺少价格或汇率，金额可能不完整'));
      }
      const rows = [
        { label: '调仓笔数', value: data.count + ' 笔' },
        { label: '涉及天数', value: data.dayCount + ' 天' },
        {
          label: '平均间隔',
          value: data.averageIntervalDays === null || data.averageIntervalDays === undefined
            ? '--' : '约 ' + data.averageIntervalDays + ' 天一次',
        },
        { label: '近 30 天', value: data.recent30DaysCount + ' 次' },
        { label: '调仓金额', value: Fmt.cny(data.amountCny) },
        { label: '手续费/损耗', value: Fmt.cny(data.feeLossCny) },
      ];
      content.appendChild(summaryTable(rows));

      if (data.count === 0) {
        content.appendChild(el('div', 'empty', '所选区间内没有调仓（不同标的之间的转账）。'));
      } else {
        const ordered = data.months.slice().reverse();
        if (ordered.length > 0) {
          content.appendChild(rebalanceMonthly(ordered));
        }
        if ((data.categoryFlows || []).length > 0) {
          content.appendChild(rebalanceFlows(data.categoryFlows, state.portfolio));
        }
        if ((data.topPairs || []).length > 0) {
          content.appendChild(rebalancePairs(data.topPairs));
        }
      }

      const excluded = [];
      if (data.accountMoveCount > 0) excluded.push('同一标的换机构 ' + data.accountMoveCount + ' 笔');
      if (data.fxSwapCount > 0) excluded.push('现金换汇 ' + data.fxSwapCount + ' 笔');
      if (data.cashCategoryCount > 0) excluded.push('现金类别内互转 ' + data.cashCategoryCount + ' 笔');
      let footnote = '口径：只统计不同标的之间的转账，同一天多笔计 1 个调仓日';
      if (excluded.length > 0) footnote += '；' + excluded.join('、') + '未计入';
      footnote += '。';
      content.appendChild(el('div', 'rb-footnote', footnote));
    },

    renderDividends(content, state, data) {
      const total = Fmt.num(data.totalCny) || 0;
      if (data.incomplete) {
        content.appendChild(el('div', 'banner warn', '部分分红因缺少汇率未计入'));
      }
      const hero = el('div', 'dv-hero');
      hero.appendChild(el('div', 'label', '区间分红合计'));
      hero.appendChild(el('div', 'value', data.count > 0 ? Fmt.cny(data.totalCny) : '--'));
      content.appendChild(hero);
      if (data.count === 0) {
        content.appendChild(el('div', 'empty', '所选区间内没有分红事件。'));
        return;
      }
      const row = el('div', 'summary-row');
      row.appendChild(summaryItem('分红笔数', data.count + ' 笔'));
      row.appendChild(summaryItem('月均分红',
        data.averagePerMonthCny ? Fmt.cny(data.averagePerMonthCny) : '--'));
      row.appendChild(summaryItem('单笔最大', data.largest
        ? Fmt.cny(data.largest.amountCny) + ' · ' + monthDay(data.largest.date) : '--'));
      row.appendChild(summaryItem('最近一笔', data.latest
        ? monthDay(data.latest.date) + ' · ' + Fmt.cny(data.latest.amountCny) : '--'));
      content.appendChild(row);

      const palette = ['#e6a23c', '#e8613c', '#0984e3', '#6c5ce7', '#2e9e6b', '#b2bec3'];
      const categoryColor = categoryColors(state.portfolio);
      const blocks = el('div', 'dv-blocks');
      const direction = [];
      if (data.withdrawnCount > 0) {
        direction.push({ name: '立即转出', amountCny: data.withdrawnCny, count: data.withdrawnCount });
      }
      if (data.retainedCount > 0) {
        direction.push({ name: '留存现金', amountCny: data.retainedCny, count: data.retainedCount });
      }
      if (direction.length > 0) {
        blocks.appendChild(dividendBlock('去向分布', direction, total, ['#0984e3', '#2e9e6b']));
      }
      blocks.appendChild(dividendBlock('来源类别', data.bySourceCategory, total,
        data.bySourceCategory.map(function (item, index) {
          return categoryColor[item.name] || palette[index % palette.length];
        })));
      blocks.appendChild(dividendBlock('来源标的', data.bySourceInstrument, total, palette));
      blocks.appendChild(dividendBlock('入账账户', data.byDestinationAccount, total, palette));
      const months = data.monthly.map(function (item) {
        return { name: monthLabel(item.month), amountCny: item.amountCny, count: item.count };
      });
      blocks.appendChild(dividendBlock('月度分红', months, total,
        months.map(function () { return '#e6a23c'; })));
      content.appendChild(blocks);
    },

    renderDistribution(content, key, rows) {
      content.appendChild(el('h2', 'card-title',
        key === 'institutions' ? '机构分布' : '币种分布'));
      if (!rows || rows.length === 0) {
        content.appendChild(el('div', 'empty', '暂无数据'));
        return;
      }
      const colors = key === 'institutions'
        ? ['#e6a23c', '#e8613c', '#0984e3', '#6c5ce7', '#2e9e6b', '#b2bec3']
        : rows.map(function (row) {
          return { CNY: '#e6a23c', USD: '#0984e3', HKD: '#6c5ce7', PHYSICAL_GOLD: '#f9ca24' }[row.id] || '#8a8a8a';
        });
      this.mount(content, {
        type: 'bar',
        data: {
          labels: rows.map(function (row) { return row.name; }),
          datasets: [{
            label: '金额',
            data: rows.map(function (row) { return Fmt.num(row.valueCny); }),
            backgroundColor: key === 'institutions'
              ? rows.map(function (row, index) { return colors[index % colors.length]; })
              : colors,
            borderRadius: 5,
          }],
        },
        options: {
          indexAxis: 'y',
          responsive: true,
          maintainAspectRatio: false,
          plugins: {
            legend: { display: false },
            tooltip: {
              callbacks: {
                label: function (ctx) { return ' ' + Fmt.cny(ctx.parsed.x); },
              },
            },
          },
          scales: {
            x: {
              grid: { color: 'rgba(0,0,0,0.06)' },
              ticks: { color: '#8a8a8a', callback: function (v) { return compact(v); } },
            },
            y: { grid: { display: false }, ticks: { color: '#555555' } },
          },
        },
      });
    },

    mount(content, config) {
      const wrap = el('div', 'chart-wrap');
      const canvas = document.createElement('canvas');
      wrap.appendChild(canvas);
      content.appendChild(wrap);
      chart = new Chart(canvas.getContext('2d'), config);
    },
  };
})();

function cnyOrDash(value) {
  return value === null || value === undefined ? '--' : Fmt.cny(value);
}

function firstValue(points) {
  for (let i = 0; i < points.length; i += 1) {
    const n = Fmt.num(points[i].value);
    if (n !== null) return n;
  }
  return null;
}

function lastValue(points) {
  for (let i = points.length - 1; i >= 0; i -= 1) {
    const n = Fmt.num(points[i].value);
    if (n !== null) return n;
  }
  return null;
}

function summaryTable(rows) {
  const block = el('div', 'summary-table');
  rows.forEach(function (row) {
    const line = el('div', 'summary-line');
    const left = el('div');
    left.appendChild(el('div', 'label', row.label));
    if (row.sub) left.appendChild(el('div', 'sub', row.sub));
    line.appendChild(left);
    line.appendChild(el('div', 'value ' + (row.valueClass || ''), row.value));
    block.appendChild(line);
  });
  return block;
}

function modeChip(label, active, onClick) {
  const button = el('button', 'mode-chip' + (active ? ' active' : ''), label);
  button.addEventListener('click', onClick);
  return button;
}

/** Draws ▲/▼ flow markers along the bottom of a line chart. */
function flowMarkerPlugin(flowsByDate, enabled) {
  return {
    id: 'flowMarkers',
    afterDatasetsDraw(chartArg) {
      if (!enabled) return;
      const meta = chartArg.getDatasetMeta(0);
      const area = chartArg.chartArea;
      if (!meta || !area) return;
      const ctx = chartArg.ctx;
      chartArg.data.labels.forEach(function (label, index) {
        const net = flowsByDate[label];
        if (net === undefined) return;
        const point = meta.data[index];
        const x = point ? point.x : null;
        if (x === null || x === undefined) return;
        const baseY = area.bottom - 3;
        ctx.save();
        ctx.beginPath();
        if (net > 0) {
          ctx.moveTo(x, baseY - 7);
          ctx.lineTo(x + 4, baseY);
          ctx.lineTo(x - 4, baseY);
          ctx.fillStyle = '#2e9e6b';
        } else {
          ctx.moveTo(x, baseY);
          ctx.lineTo(x + 4, baseY - 7);
          ctx.lineTo(x - 4, baseY - 7);
          ctx.fillStyle = '#e8613c';
        }
        ctx.closePath();
        ctx.fill();
        ctx.restore();
      });
    },
  };
}

const REBALANCE_COLORS = {
  cashToAsset: '#e6a23c',
  assetToCash: '#0984e3',
  assetToAsset: '#b2bec3',
};

function rebalanceMonthly(months) {
  const block = el('div', 'rb-block');
  block.appendChild(el('div', 'rb-title', '月度调仓'));
  const hasAssetSwap = months.some(function (month) { return month.assetToAsset > 0; });
  const legend = el('div', 'rb-legend');
  legend.appendChild(legendItem(REBALANCE_COLORS.cashToAsset, '现金→资产'));
  legend.appendChild(legendItem(REBALANCE_COLORS.assetToCash, '资产→现金'));
  if (hasAssetSwap) {
    legend.appendChild(legendItem(REBALANCE_COLORS.assetToAsset, '资产→资产'));
  }
  block.appendChild(legend);

  const maxCount = months.reduce(function (acc, month) {
    return Math.max(acc, month.count);
  }, 0);
  const bars = el('div', 'rb-bars');
  months.forEach(function (month) {
    const column = el('div', 'rb-col');
    const stack = el('div', 'rb-stack');
    // Top-down order; the first segment gets the rounded top.
    const segments = [
      [month.cashToAsset, REBALANCE_COLORS.cashToAsset],
      [month.assetToCash, REBALANCE_COLORS.assetToCash],
      [month.assetToAsset, REBALANCE_COLORS.assetToAsset],
    ];
    segments.forEach(function (pair) {
      if (pair[0] <= 0 || maxCount <= 0) return;
      const segment = el('div', 'rb-seg');
      segment.style.height = (pair[0] / maxCount) * 130 + 'px';
      segment.style.background = pair[1];
      stack.appendChild(segment);
    });
    if (stack.firstChild) {
      stack.firstChild.classList.add('top');
    }
    column.appendChild(stack);
    bars.appendChild(column);
  });
  block.appendChild(bars);

  const axis = el('div', 'rb-axis');
  axis.appendChild(el('span', null, months.length ? months[0].month : ''));
  axis.appendChild(el('span', null, months.length ? months[months.length - 1].month : ''));
  block.appendChild(axis);
  return block;
}

function legendItem(color, label) {
  const item = el('span');
  const dot = el('i', 'dot');
  dot.style.background = color;
  item.appendChild(dot);
  item.appendChild(document.createTextNode(label));
  return item;
}

function signedCny(value) {
  const n = Fmt.num(value);
  if (n === null) return '--';
  if (n === 0) return Fmt.cny(value);
  const text = Fmt.cny(value);
  return n > 0 ? '+' + text : text.replace('¥-', '-¥');
}

function rebalanceFlows(flows, portfolio) {
  const block = el('div', 'rb-block rb-flows');
  block.appendChild(el('div', 'rb-title', '调仓流向'));
  const nets = flows.map(function (flow) {
    const netText = Fmt.add(flow.inCny, '-' + flow.outCny);
    return { flow: flow, netText: netText, net: Fmt.num(netText) || 0 };
  }).sort(function (a, b) { return b.net - a.net; });
  const maxGain = nets.reduce(function (acc, item) {
    return Math.max(acc, item.net);
  }, 0);
  const maxLoss = Math.abs(nets.reduce(function (acc, item) {
    return Math.min(acc, item.net);
  }, 0));
  const combined = maxGain + maxLoss;
  const zeroPct = combined > 0 ? (maxLoss / combined) * 100 : 50;
  const colors = {};
  ((portfolio.settings && portfolio.settings.categories) || []).forEach(function (category) {
    colors[category.id] = category.color;
  });

  nets.forEach(function (item) {
    const row = el('div', 'rb-flow');
    const top = el('div', 'rb-flow-top');
    const dot = el('i', 'dot');
    dot.style.background = colors[item.flow.categoryId] || '#8a8a8a';
    top.appendChild(dot);
    top.appendChild(el('span', 'rb-flow-name', item.flow.name));
    top.appendChild(el('span', 'rb-flow-count',
      (item.flow.outCount + item.flow.inCount) + ' 笔'));
    top.appendChild(el('span', 'rb-flow-amount ' + (item.net >= 0 ? 'pos' : 'neg'),
      signedCny(item.netText)));
    row.appendChild(top);

    const track = el('div', 'rb-track');
    const fill = el('div', 'rb-fill ' + (item.net >= 0 ? 'pos' : 'neg'));
    if (item.net >= 0) {
      const width = maxGain > 0 ? (item.net / maxGain) * (100 - zeroPct) : 0;
      fill.style.left = zeroPct + '%';
      fill.style.width = width + '%';
    } else {
      const width = maxLoss > 0 ? (Math.abs(item.net) / maxLoss) * zeroPct : 0;
      fill.style.left = (zeroPct - width) + '%';
      fill.style.width = width + '%';
    }
    track.appendChild(fill);
    const zero = el('div', 'rb-zero');
    zero.style.left = zeroPct + '%';
    track.appendChild(zero);
    row.appendChild(track);
    block.appendChild(row);
  });
  return block;
}

function rebalancePairs(pairs) {
  const block = el('div', 'rb-block');
  block.appendChild(el('div', 'rb-title', '最频繁的调仓方向'));
  pairs.forEach(function (pair) {
    const row = el('div', 'rb-pair');
    row.appendChild(el('span', 'name', pair.fromName + ' → ' + pair.toName));
    row.appendChild(el('span', 'count', pair.count + ' 次'));
    row.appendChild(el('span', 'amount', Fmt.cny(pair.amountCny)));
    block.appendChild(row);
  });
  return block;
}

function summaryItem(label, value) {
  const item = el('div', 'summary-item');
  item.appendChild(el('div', 'label', label));
  item.appendChild(el('div', 'value', value));
  return item;
}

function table(headers, rows, numeric) {
  const wrapper = el('div');
  wrapper.style.marginTop = '14px';
  const tableEl = el('table', 'table');
  const head = el('tr');
  headers.forEach(function (header, index) {
    head.appendChild(el('th', numeric[index] ? 'num' : '', header));
  });
  tableEl.appendChild(head);
  rows.forEach(function (row) {
    const tr = el('tr');
    row.forEach(function (cell, index) {
      tr.appendChild(el('td', numeric[index] ? 'num' : '', cell));
    });
    tableEl.appendChild(tr);
  });
  wrapper.appendChild(tableEl);
  return wrapper;
}

function compact(value) {
  const n = Number(value);
  if (!Number.isFinite(n)) return '';
  if (Math.abs(n) >= 10000) return (n / 10000).toFixed(1) + '万';
  return String(Math.round(n));
}

function hexToRgba(hex, alpha) {
  const value = String(hex || '').replace('#', '');
  if (value.length !== 6) return 'rgba(230,162,60,' + alpha + ')';
  const r = parseInt(value.slice(0, 2), 16);
  const g = parseInt(value.slice(2, 4), 16);
  const b = parseInt(value.slice(4, 6), 16);
  return 'rgba(' + r + ',' + g + ',' + b + ',' + alpha + ')';
}

function categoryColors(portfolio) {
  const map = {};
  const categories = (portfolio.settings && portfolio.settings.categories) || [];
  categories.forEach(function (category) { map[category.name] = category.color; });
  return map;
}

function dividendBlock(title, rows, total, colors) {
  const block = el('div', 'dv-block');
  block.appendChild(el('h3', null, title));
  rows.forEach(function (item, index) {
    const amount = Fmt.num(item.amountCny) || 0;
    const share = total > 0 ? amount / total : 0;
    const pct = Math.round(share * 1e6) / 1e4;
    const color = colors[index % colors.length];
    const row = el('div', 'dv-row');
    const top = el('div', 'dv-top');
    const dot = el('i', 'dot');
    dot.style.background = color;
    top.appendChild(dot);
    top.appendChild(el('span', 'name', item.name));
    top.appendChild(el('span', 'amount', Fmt.cny(item.amountCny)));
    top.appendChild(el('span', 'meta', pct.toFixed(1) + '% · ' + item.count + ' 笔'));
    row.appendChild(top);
    const bar = el('div', 'dv-bar');
    const fill = el('div', 'dv-fill');
    fill.style.width = Math.max(pct, 0) + '%';
    fill.style.background = color;
    bar.appendChild(fill);
    row.appendChild(bar);
    block.appendChild(row);
  });
  return block;
}

function monthLabel(month) {
  const parts = String(month).split('-');
  return parts[0] + ' 年 ' + Number(parts[1]) + ' 月';
}

function monthDay(date) {
  const parts = String(date).split('-');
  return Number(parts[1]) + '月' + Number(parts[2]) + '日';
}

function lineOptions(label) {
  return {
    responsive: true,
    maintainAspectRatio: false,
    interaction: { mode: 'index', intersect: false },
    plugins: {
      legend: { display: false },
      tooltip: {
        callbacks: {
          label: function (ctx) { return ' ' + label + ' ' + Fmt.cny(ctx.parsed.y); },
        },
      },
    },
    scales: {
      x: {
        grid: { display: false },
        ticks: { color: '#8a8a8a', maxTicksLimit: 10, maxRotation: 0, autoSkip: true },
      },
      y: {
        grid: { color: 'rgba(0,0,0,0.06)' },
        ticks: { color: '#8a8a8a', callback: function (v) { return compact(v); } },
      },
    },
  };
}

function barOptions(label, money, stacked) {
  return {
    responsive: true,
    maintainAspectRatio: false,
    interaction: { mode: 'index', intersect: false },
    plugins: {
      legend: { display: true, labels: { boxWidth: 10, color: '#555555' } },
      tooltip: {
        callbacks: {
          label: function (ctx) {
            return ' ' + ctx.dataset.label + ' ' +
              (money ? Fmt.cny(ctx.parsed.y) : String(ctx.parsed.y));
          },
        },
      },
    },
    scales: {
      x: {
        stacked: !!stacked,
        grid: { display: false },
        ticks: { color: '#8a8a8a', maxRotation: 0, autoSkip: true },
      },
      y: {
        stacked: !!stacked,
        grid: { color: 'rgba(0,0,0,0.06)' },
        ticks: { color: '#8a8a8a', callback: function (v) { return compact(v); } },
      },
    },
  };
}
