/* 概览：总资产、资产配置、分类卡片（范围条/阈值/偏离/持仓）、机构与币种分布。 */
const OverviewView = {
  /** Expanded state per category id, kept across re-renders. */
  expanded: {},

  render(root, state) {
    const overview = state.overview;
    const page = el('div', 'page');

    this.banners(page, overview);
    page.appendChild(this.kpiGrid(overview));
    page.appendChild(this.allocationCard(overview));
    page.appendChild(this.categoryToolbar(overview));
    overview.categories.forEach(function (category) {
      page.appendChild(this.categoryCard(overview, category));
    }, this);
    page.appendChild(this.distributionCard(overview));
    root.appendChild(page);
  },

  banners(page, overview) {
    if (overview.hasMissingPrices) {
      page.appendChild(el('div', 'banner warn',
        '部分持仓缺少价格，金额与占比可能不完整。可在右上角刷新行情后重试。'));
    }
    if (overview.quote && overview.quote.failures.length > 0) {
      const banner = el('div', 'banner warn');
      banner.appendChild(el('div', null, '部分行情获取失败：'));
      const list = el('ul');
      overview.quote.failures.slice(0, 5).forEach(function (failure) {
        const label = failure.name || failure.code || failure.instrumentId || '未知标的';
        list.appendChild(el('li', null, label + '：' + failure.message));
      });
      banner.appendChild(list);
      page.appendChild(banner);
    }
  },

  kpiGrid(overview) {
    const kpis = el('div', 'kpi-grid');
    const shareOf = function (value) {
      const total = numOr(overview.totalValueCny, 0);
      return total === 0 ? '0' : String(numOr(value, 0) / total);
    };
    const parts = '红利 ' + Fmt.cny(overview.dividendValueCny) +
      ' · 组合 ' + Fmt.cny(overview.portfolioValueCny) +
      (overview.satelliteValueCny ? ' · 卫星 ' + Fmt.cny(overview.satelliteValueCny) : '');
    kpis.appendChild(this.kpi('总资产', Fmt.cny(overview.totalValueCny), parts));
    kpis.appendChild(this.kpi('红利部分', Fmt.cny(overview.dividendValueCny),
      '占总资产 ' + Fmt.percent(shareOf(overview.dividendValueCny)), true));
    kpis.appendChild(this.kpi('组合部分', Fmt.cny(overview.portfolioValueCny),
      '占总资产 ' + Fmt.percent(shareOf(overview.portfolioValueCny)), true));
    if (overview.satelliteValueCny) {
      kpis.appendChild(this.kpi('卫星资产', Fmt.cny(overview.satelliteValueCny),
        '占总资产 ' + Fmt.percent(shareOf(overview.satelliteValueCny)) + ' · 不计入再平衡', true));
    }
    return kpis;
  },

  kpi(label, value, sub, small) {
    const card = el('div', 'card kpi');
    card.appendChild(el('div', 'label', label));
    card.appendChild(el('div', 'value' + (small ? ' small' : ''), value));
    card.appendChild(el('div', 'sub', sub));
    return card;
  },

  allocationCard(overview) {
    const card = el('div', 'card');
    const title = el('h2', 'card-title', '资产配置');
    title.appendChild(el('span', 'hint', '柱长为当前占比，绿色区间为目标范围，深色竖线为目标值'));
    card.appendChild(title);
    const list = el('div', 'alloc-list');
    const scale = overviewScale(overview.categories);
    const self = this;

    overview.categories.forEach(function (category) {
      // Satellite assets have no target/range and stay out of the allocation
      // bars; their compact card below shows value and share.
      if (isSatellite(category)) return;
      const row = el('div', 'alloc-row');
      row.title = '点击定位到分类卡片';

      const name = el('div', 'alloc-name');
      name.appendChild(colorDot(category.color));
      name.appendChild(el('span', 'nm', category.name));
      name.appendChild(el('span', 'target',
        Fmt.pctPoints(category.minPct, 1) + '–' + Fmt.pctPoints(category.maxPct, 1)));
      row.appendChild(name);

      const amounts = el('div', 'alloc-amounts');
      amounts.appendChild(el('div', 'current', Fmt.cny(category.valueCny)));
      amounts.appendChild(el('div', 'target', '目标 ' + Fmt.cny(category.targetValueCny)));
      row.appendChild(amounts);

      const bar = el('div', 'alloc-bar');
      const minPos = barPosition(category.visualMin, scale);
      const maxPos = barPosition(category.visualMax, scale);
      const zone = el('div', 'alloc-zone');
      zone.style.left = minPos + '%';
      zone.style.width = Math.max(0, maxPos - minPos) + '%';
      bar.appendChild(zone);

      const fill = el('div', 'alloc-fill');
      fill.style.width = barPosition(category.visualShare, scale) + '%';
      fill.style.background = category.color || 'var(--gold)';
      bar.appendChild(fill);

      [minPos, maxPos].forEach(function (position) {
        const mark = el('div', 'alloc-mark');
        mark.style.left = position + '%';
        bar.appendChild(mark);
      });
      const target = el('div', 'alloc-target');
      target.style.left = barPosition(category.visualTarget, scale) + '%';
      bar.appendChild(target);
      row.appendChild(bar);

      const pcts = el('div', 'alloc-pcts');
      pcts.appendChild(el('div', 'current ' + (category.inRange ? 'in' : 'out'),
        Fmt.percent(category.share)));
      pcts.appendChild(el('div', 'target', Fmt.pctPoints(category.targetPct, 1)));
      row.appendChild(pcts);

      row.addEventListener('click', function () { self.focusCategory(category.id); });
      list.appendChild(row);
    });

    card.appendChild(list);
    return card;
  },

  /** Expands the target category card and scrolls it into view. */
  focusCategory(categoryId) {
    const card = document.getElementById('cat-' + categoryId);
    if (!card) return;
    this.expanded[categoryId] = true;
    card.classList.add('expanded');
    card.scrollIntoView({ behavior: 'smooth', block: 'start' });
  },

  categoryToolbar(overview) {
    const bar = el('div', 'cat-toolbar');
    const button = el('button', 'btn ghost', '全部展开/折叠');
    const self = this;
    button.addEventListener('click', function () {
      const anyCollapsed = overview.categories.some(function (category) {
        return !self.expanded[category.id];
      });
      overview.categories.forEach(function (category) {
        self.expanded[category.id] = anyCollapsed;
      });
      document.querySelectorAll('#app .cat-card').forEach(function (card) {
        card.classList.toggle('expanded', anyCollapsed);
      });
    });
    bar.appendChild(button);
    return bar;
  },

  categoryCard(overview, category) {
    const satellite = isSatellite(category);
    const positions = overview.positions.filter(function (position) {
      return position.categoryId === category.id;
    });
    const card = el('div', 'cat-card' + (this.expanded[category.id] ? ' expanded' : ''));
    card.id = 'cat-' + category.id;

    const head = el('div', 'cat-head');
    const line = el('div', 'cat-title-line');
    line.appendChild(colorDot(category.color));
    line.appendChild(el('span', 'cat-name', category.name));
    line.appendChild(el('span',
      'cat-share' + (satellite ? '' : ' ' + (category.inRange ? 'in' : 'out')),
      Fmt.percent(category.share)));
    line.appendChild(el('span', 'caret', '▾'));
    head.appendChild(line);
    const self = this;
    head.addEventListener('click', function () {
      self.expanded[category.id] = !self.expanded[category.id];
      card.classList.toggle('expanded', !!self.expanded[category.id]);
    });
    card.appendChild(head);

    const body = el('div', 'cat-body');
    const values = el('div', 'cat-values');
    const main = el('div');
    main.appendChild(el('div', 'cat-value', Fmt.cny(category.valueCny)));
    if (satellite) {
      main.appendChild(el('div', 'cat-target', '占总资产 ' + Fmt.percent(category.share)));
    } else {
      main.appendChild(el('div', 'cat-target',
        '目标 ' + Fmt.cny(category.targetValueCny) + ' · ' + Fmt.pctPoints(category.targetPct, 1)));
    }
    values.appendChild(main);
    values.appendChild(el('div', 'cat-range', satellite
      ? '不计入再平衡'
      : '区间 ' + Fmt.pctPoints(category.minPct, 1) + ' – ' + Fmt.pctPoints(category.maxPct, 1)));
    body.appendChild(values);

    const usd = usdSubtotal(positions);
    if (usd !== null) {
      body.appendChild(el('div', 'cat-meta', '美元资产 ' + Fmt.native(usd, 'USD')));
    }
    if (category.incomplete) {
      body.appendChild(el('div', 'cat-meta bad', '部分价格缺失，估值不完整'));
    }

    if (!satellite) body.appendChild(this.rangeViz(category));

    const details = el('div', 'cat-details');
    if (positions.length === 0) {
      details.appendChild(el('div', 'cat-empty', '该类别暂无持仓'));
    } else {
      details.appendChild(this.positionsTable(overview, positions));
    }
    body.appendChild(details);

    card.appendChild(body);
    return card;
  },

  /** Current-value callout, rule-band track and threshold axis for one category. */
  rangeViz(category) {
    const share = numOr(category.share, 0);
    const minShare = numOr(category.minPct, 0) / 100;
    const maxShare = numOr(category.maxPct, 0) / 100;
    const targetShare = numOr(category.targetPct, 0) / 100;
    const window = rangeWindow(share, minShare, maxShare);
    const position = function (value) { return window.position(value); };

    const viz = el('div', 'range-viz');

    const captionRow = el('div', 'range-caption-row');
    const caption = el('div', 'range-caption ' + (category.inRange ? 'in' : 'out'));
    caption.style.left = position(share) + '%';
    caption.appendChild(el('b', null, Fmt.percent(category.share)));
    caption.appendChild(el('span', null, Fmt.cnyCompact(category.valueCny)));
    captionRow.appendChild(caption);
    viz.appendChild(captionRow);

    const track = el('div', 'range-track');
    const minX = position(minShare);
    const maxX = position(maxShare);
    const outLeft = el('div', 'range-out');
    outLeft.style.left = '0%';
    outLeft.style.width = minX + '%';
    track.appendChild(outLeft);
    const zone = el('div', 'range-zone');
    zone.style.left = minX + '%';
    zone.style.width = Math.max(0, maxX - minX) + '%';
    track.appendChild(zone);
    const outRight = el('div', 'range-out');
    outRight.style.left = maxX + '%';
    outRight.style.right = '0%';
    track.appendChild(outRight);
    [minX, maxX].forEach(function (x) {
      const tick = el('div', 'range-tick');
      tick.style.left = x + '%';
      track.appendChild(tick);
    });
    const target = el('div', 'range-target');
    target.style.left = position(targetShare) + '%';
    track.appendChild(target);
    const marker = el('div', 'range-marker ' + (category.inRange ? 'in' : 'out'));
    marker.style.left = position(share) + '%';
    track.appendChild(marker);
    viz.appendChild(track);

    const hasGoldPrice = category.goldMinPriceCnyPerGram !== null &&
      category.goldMinPriceCnyPerGram !== undefined;
    const axis = el('div', 'range-axis' + (hasGoldPrice ? ' with-price' : ''));
    axis.appendChild(axisColumn('最低', minX,
      Fmt.pctPoints(category.minPct, 1), category.minValueCny,
      category.goldMinPriceCnyPerGram, category.gapToMinCny, true));
    axis.appendChild(axisColumn('目标', position(targetShare),
      Fmt.pctPoints(category.targetPct, 1), category.targetValueCny,
      null, category.gapToTargetCny, null));
    axis.appendChild(axisColumn('最高', maxX,
      Fmt.pctPoints(category.maxPct, 1), category.maxValueCny,
      category.goldMaxPriceCnyPerGram, category.gapToMaxCny, false));
    viz.appendChild(axis);

    const ends = el('div', 'axis-ends');
    ends.appendChild(el('span', null, Math.round(window.min * 100) + '%'));
    ends.appendChild(el('span', null, Math.round(window.max * 100) + '%'));
    viz.appendChild(ends);
    return viz;
  },

  positionsTable(overview, positions) {
    const total = numOr(overview.totalValueCny, 0);
    const wrap = el('div', 'table-wrap');
    const table = el('table', 'table pos-table');

    const head = el('thead');
    const headRow = el('tr');
    ['机构', '标的', '数量', '单价', '市值 (¥)', '占比'].forEach(function (label, index) {
      headRow.appendChild(el('th', index >= 2 ? 'num' : null, label));
    });
    head.appendChild(headRow);
    table.appendChild(head);

    const body = el('tbody');
    positions.forEach(function (position) {
      const row = el('tr');
      row.appendChild(el('td', 'pos-broker', position.institutionName));

      const nameCell = el('td', 'pos-name');
      if (position.kind === 'cash') {
        nameCell.appendChild(el('div', null, position.instrumentName));
      } else {
        nameCell.appendChild(el('div', 'pos-code', position.instrumentCode));
        nameCell.appendChild(el('div', 'pos-sub', position.instrumentName));
      }
      row.appendChild(nameCell);

      if (position.kind === 'cash') {
        row.appendChild(el('td', 'num', Fmt.native(position.quantity, position.currency)));
        row.appendChild(el('td', 'num', '—'));
      } else {
        row.appendChild(el('td', 'num', Fmt.quantity(position.quantity) + ' ' + position.unit));
        row.appendChild(el('td', 'num',
          position.price === null ? '--' : Fmt.native(position.price, position.currency)));
      }

      const valueCell = el('td', 'num');
      if (position.valueCny === null) {
        valueCell.appendChild(el('span', 'pos-missing', '缺价'));
      } else {
        valueCell.appendChild(el('div', null, Fmt.cny(position.valueCny)));
        if (position.kind === 'quoted' && !isCny(position.currency)) {
          const nativeValue = Fmt.mul(position.quantity, position.price);
          if (nativeValue !== null) {
            valueCell.appendChild(el('div', 'pos-sub',
              Fmt.native(nativeValue, position.currency)));
          }
        }
      }
      row.appendChild(valueCell);

      row.appendChild(el('td', 'num',
        position.valueCny === null || total <= 0
          ? '--'
          : Fmt.percent(numOr(position.valueCny, 0) / total)));
      body.appendChild(row);
    });
    table.appendChild(body);
    wrap.appendChild(table);
    return wrap;
  },

  distributionCard(overview) {
    const card = el('div', 'card');
    card.appendChild(el('h2', 'card-title', '分布'));
    const grid = el('div', 'dist-grid');
    // Distribution items without any current value are noise.
    const institutionRows = (overview.institutions || []).filter(function (row) {
      return Fmt.num(row.valueCny) !== 0;
    });
    const currencyRows = (overview.currencies || []).filter(function (row) {
      return Fmt.num(row.valueCny) !== 0;
    });
    grid.appendChild(distPanel('机构分布', institutionRows, function (row, index) {
      const palette = ['#e6a23c', '#e8613c', '#0984e3', '#6c5ce7', '#2e9e6b', '#b2bec3'];
      return palette[index % palette.length];
    }));
    grid.appendChild(distPanel('币种分布', currencyRows, function (row) {
      const colors = {
        CNY: '#e6a23c', USD: '#0984e3', HKD: '#6c5ce7', PHYSICAL_GOLD: '#f9ca24',
      };
      return colors[row.id] || '#8a8a8a';
    }));
    card.appendChild(grid);
    return card;
  },
};

function numOr(value, fallback) {
  const n = Fmt.num(value);
  return n === null ? fallback : n;
}

function colorDot(color) {
  const dot = el('span', 'dot');
  dot.style.background = color || 'var(--gold)';
  return dot;
}

function isCny(currency) {
  const upper = String(currency || '').toUpperCase();
  return upper === 'CNY' || upper === 'CNH';
}

function isSatellite(category) {
  return !!category && category.specialRule === 'satellite';
}

/** Shared bar scale: the largest total-based share or target, floored at 10%, +15% headroom. */
function overviewScale(categories) {
  let scale = 0.1;
  categories.forEach(function (category) {
    if (isSatellite(category)) return;
    scale = Math.max(
      scale,
      numOr(category.visualShare, 0),
      numOr(category.visualTarget, 0),
    );
  });
  return Math.min(scale * 1.15, 1);
}

function barPosition(fraction, scale) {
  const value = numOr(fraction, 0) / scale;
  return Math.max(0, Math.min(1, value)) * 100;
}

/** Same window as the app's range bar: rule band plus the current share, 15% padding. */
function rangeWindow(share, minShare, maxShare) {
  const span = maxShare - Math.min(minShare, share);
  const pad = Math.max(span * 0.15, 0.005);
  const min = Math.max(0, Math.min(minShare, share) - pad);
  const max = Math.max(maxShare, share) + pad;
  return {
    min: min,
    max: max,
    position: function (value) {
      const range = max - min;
      if (range <= 0) return 0;
      return Math.max(0, Math.min(1, (value - min) / range)) * 100;
    },
  };
}

/**
 * One threshold column under the bar: name (expanded only), percent, compact
 * amount, gold threshold unit price, and the distance to the current value
 * (expanded only, colored by whether it keeps the category in range).
 */
function axisColumn(name, leftPercent, percentText, amount, goldPrice, diff, goodWhenPositive) {
  const column = el('div', 'axis-col');
  column.style.left = leftPercent + '%';
  column.appendChild(el('div', 'thr-name', name));
  column.appendChild(el('div', 'thr-pct', percentText));
  column.appendChild(el('div', 'thr-amt', Fmt.cnyCompact(amount)));
  if (goldPrice !== null && goldPrice !== undefined) {
    column.appendChild(el('div', 'thr-price', Fmt.cny(goldPrice) + '/克'));
  }
  column.appendChild(el('div', 'thr-diff ' + diffClass(diff, goodWhenPositive), diffText(diff)));
  return column;
}

function diffText(diff) {
  const value = Fmt.num(diff);
  if (value === null) return '--';
  if (value === 0) return '持平';
  return (value > 0 ? '+' : '') + Fmt.cnyCompact(diff);
}

function diffClass(diff, goodWhenPositive) {
  const value = Fmt.num(diff);
  if (value === null || value === 0) return '';
  if (goodWhenPositive === true) return value > 0 ? 'good' : 'bad';
  if (goodWhenPositive === false) return value < 0 ? 'good' : 'bad';
  return 'neutral';
}

/**
 * USD subtotal of a category's quoted holdings, mirroring the app's
 * "美元资产" line. Returns null when the category holds no USD instruments.
 */
function usdSubtotal(positions) {
  const usd = positions.filter(function (position) {
    return position.kind === 'quoted' &&
      String(position.currency || '').toUpperCase() === 'USD' &&
      position.price !== null;
  });
  if (usd.length === 0) return null;
  const total = Fmt.sum(usd.map(function (position) {
    return Fmt.mul(position.quantity, position.price) || '0';
  }));
  return /^-?0(\.0*)?$/.test(total) ? null : total;
}

function distPanel(title, rows, colorFor) {
  const panel = el('div');
  panel.appendChild(el('div', 'card-title', title));
  if (!rows || rows.length === 0) {
    panel.appendChild(el('div', 'empty', '暂无数据'));
    return panel;
  }
  const max = rows.reduce(function (acc, row) {
    const n = Fmt.num(row.valueCny) || 0;
    return Math.max(acc, n);
  }, 0);
  rows.forEach(function (row, index) {
    const line = el('div', 'dist-row');
    line.appendChild(el('div', 'dist-name', row.name));
    const bar = el('div', 'dist-bar');
    const fill = el('div', 'dist-fill');
    const value = Fmt.num(row.valueCny) || 0;
    fill.style.width = (max > 0 ? (value / max) * 100 : 0) + '%';
    fill.style.background = colorFor(row, index);
    bar.appendChild(fill);
    line.appendChild(bar);
    line.appendChild(el('div', 'dist-val', Fmt.cny(row.valueCny)));
    line.appendChild(el('div', 'dist-share', Fmt.percent(row.share)));
    panel.appendChild(line);
  });
  return panel;
}
