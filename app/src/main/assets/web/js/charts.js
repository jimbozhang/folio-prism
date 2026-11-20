/* 图表页：历史曲线、分类盈亏、投入取出、费用统计、机构/币种分布。 */
const ChartsView = (() => {
  let chart = null;
  let requestSeq = 0;

  const TYPES = [
    { key: 'assets', label: '总资产', kind: 'history' },
    { key: 'category_assets', label: '类别资产', kind: 'history', category: true },
    { key: 'category_pnl', label: '类别盈亏', kind: 'history' },
    { key: 'dividends', label: '分红统计', kind: 'dividends' },
    { key: 'flows', label: '投入取出', kind: 'flows' },
    { key: 'expenses', label: '费用统计', kind: 'expenses' },
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

      let categoryId = null;
      if (current.category) {
        const categories = state.portfolio.settings.categories || [];
        if (categories.length > 0) {
          const select = el('select', 'select');
          categories.forEach(function (category) {
            const option = el('option', null, category.name);
            option.value = category.id;
            select.appendChild(option);
          });
          categoryId = state.charts.categoryId || categories[0].id;
          select.value = categoryId;
          select.addEventListener('change', function () {
            state.charts.categoryId = select.value;
            ChartsView.render(root, state);
          });
          toolbar.appendChild(select);
        }
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
        } else if (current.kind === 'expenses') {
          data = await Api.expenses(range.from, range.to);
        } else if (current.kind === 'dividends') {
          data = await Api.dividends(range.from, range.to);
        } else {
          data = await Api.chart(current.key, range.from, range.to, categoryId);
        }
        if (seq !== requestSeq) return;
        clearNode(content);
        this.renderChart(content, state, current, data);
        this.renderSummary(content, state, current, data);
      } catch (err) {
        if (seq !== requestSeq) return;
        clearNode(content);
        content.appendChild(el('div', 'banner error', '加载失败：' + err.message));
      }
    },

    renderChart(content, state, current, data) {
      content.appendChild(el('h2', 'card-title', current.label));

      if (current.key === 'assets' || current.key === 'category_assets') {
        const color = current.key === 'category_assets' && data.category
          ? data.category.color : '#e6a23c';
        const label = current.key === 'category_assets' && data.category
          ? data.category.name : '总资产';
        const values = data.points.map(function (point) { return Fmt.num(point.value); });
        this.mount(content, {
          type: 'line',
          data: {
            labels: data.points.map(function (point) { return point.date; }),
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
        });
        return;
      }

      if (current.key === 'category_pnl') {
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
        return;
      }

      if (current.key === 'dividends') {
        this.renderDividends(content, state, data);
        return;
      }

      if (current.key === 'flows') {
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
        return;
      }

      if (current.key === 'expenses') {
        const names = [];
        data.months.forEach(function (month) {
          Object.keys(month.byCategory).forEach(function (name) {
            if (names.indexOf(name) < 0) names.push(name);
          });
        });
        const colors = categoryColors(state.portfolio);
        const palette = ['#e6a23c', '#e8613c', '#0984e3', '#6c5ce7', '#2e9e6b', '#b2bec3', '#f9ca24'];
        const datasets = names.map(function (name, index) {
          return {
            label: name,
            data: data.months.map(function (month) {
              return month.byCategory[name] === undefined ? 0 : Fmt.num(month.byCategory[name]);
            }),
            backgroundColor: colors[name] || palette[index % palette.length],
            stack: 'expenses',
            borderRadius: 3,
          };
        });
        this.mount(content, {
          type: 'bar',
          data: {
            labels: data.months.map(function (month) { return month.month; }),
            datasets: datasets,
          },
          options: barOptions('费用', true, true),
        });
      }
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
      blocks.appendChild(dividendBlock('来源分布（类别）', data.bySourceCategory, total,
        data.bySourceCategory.map(function (item, index) {
          return categoryColor[item.name] || palette[index % palette.length];
        })));
      blocks.appendChild(dividendBlock('来源明细（标的）', data.bySourceInstrument, total, palette));
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

    renderSummary(content, state, current, data) {
      if (current.key === 'assets' || current.key === 'category_assets') {
        const summary = data.summary || {};
        const row = el('div', 'summary-row');
        row.appendChild(summaryItem('期初', summary.startValue === null || summary.startValue === undefined
          ? '--' : Fmt.cny(summary.startValue)));
        row.appendChild(summaryItem('期末', summary.endValue === null || summary.endValue === undefined
          ? '--' : Fmt.cny(summary.endValue)));
        if (summary.totalPnl !== null && summary.totalPnl !== undefined) {
          row.appendChild(summaryItem('区间盈亏', Fmt.cny(summary.totalPnl)));
        }
        content.appendChild(row);
        return;
      }
      if (current.key === 'category_pnl') {
        const rows = data.categoryPnl.map(function (item) {
          return [item.name, item.pnlCny === null ? '--' : Fmt.cny(item.pnlCny),
            item.incomplete ? '数据不完整' : ''];
        });
        content.appendChild(table(['类别', '盈亏'], rows, [false, true]));
        return;
      }
      if (current.key === 'flows') {
        const rows = data.months.map(function (month) {
          return [month.month, Fmt.cny(month.inflowCny), Fmt.cny(month.outflowCny)];
        });
        content.appendChild(table(['月份', '流入', '流出'], rows, [false, true, true]));
        return;
      }
      if (current.key === 'expenses') {
        const rows = data.months.map(function (month) {
          return [month.month, Fmt.cny(month.totalCny)];
        });
        content.appendChild(table(['月份', '费用合计'], rows, [false, true]));
      }
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
  map['手续费/损耗'] = '#e8613c';
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
