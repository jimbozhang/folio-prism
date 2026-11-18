/* 概览：总资产、分类目标区间、持仓明细、机构/币种分布。 */
const OverviewView = {
  render(root, state) {
    const overview = state.overview;
    const page = el('div', 'page');

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

    const kpis = el('div', 'kpi-grid');
    kpis.appendChild(this.kpi('总资产', Fmt.cny(overview.totalValueCny),
      '红利 ' + Fmt.cny(overview.dividendValueCny) + ' · 组合 ' + Fmt.cny(overview.portfolioValueCny)));
    kpis.appendChild(this.kpi('红利部分', Fmt.cny(overview.dividendValueCny),
      '占全部资产 ' + Fmt.percent(overview.dividendShare), true));
    kpis.appendChild(this.kpi('组合部分', Fmt.cny(overview.portfolioValueCny),
      '占全部资产 ' + Fmt.percent(overview.portfolioShare), true));
    page.appendChild(kpis);

    page.appendChild(this.allocationCard(overview));
    page.appendChild(this.holdingsCard(overview));
    page.appendChild(this.distributionCard(overview));
    root.appendChild(page);
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
    const title = el('h2', 'card-title', '分类占比');
    title.appendChild(el('span', 'hint', '绿色区间为目标范围，竖线为目标值'));
    card.appendChild(title);
    const list = el('div', 'alloc-list');
    overview.categories.forEach(function (category) {
      const row = el('div', 'alloc-row');

      const name = el('div', 'alloc-name');
      const dot = el('span', 'dot');
      dot.style.background = category.color || 'var(--gold)';
      name.appendChild(dot);
      name.appendChild(el('span', 'nm', category.name));
      name.appendChild(el('span', 'target',
        Fmt.pctPoints(category.minPct, 0) + '–' + Fmt.pctPoints(category.maxPct, 0)));
      row.appendChild(name);

      const bar = el('div', 'alloc-bar');
      const min = clampPercent(Fmt.num(category.visualMin) * 100);
      const max = clampPercent(Fmt.num(category.visualMax) * 100);
      const target = clampPercent(Fmt.num(category.visualTarget) * 100);
      const share = clampPercent(Fmt.num(category.visualShare) * 100);

      const zone = el('div', 'alloc-zone');
      zone.style.left = min + '%';
      zone.style.width = Math.max(0, max - min) + '%';
      bar.appendChild(zone);

      const fill = el('div', 'alloc-fill');
      fill.style.width = share + '%';
      fill.style.background = category.color || 'var(--gold)';
      bar.appendChild(fill);

      const marker = el('div', 'alloc-target');
      marker.style.left = target + '%';
      bar.appendChild(marker);
      row.appendChild(bar);

      row.appendChild(el('div', 'alloc-value', Fmt.cny(category.valueCny)));
      row.appendChild(el('div', 'alloc-share ' + (category.inRange ? 'in' : 'out'),
        Fmt.percent(category.share)));
      list.appendChild(row);
    });
    card.appendChild(list);
    return card;
  },

  holdingsCard(overview) {
    const card = el('div', 'card');
    card.appendChild(el('h2', 'card-title', '持仓明细'));
    const byCategory = {};
    overview.positions.forEach(function (position) {
      (byCategory[position.categoryId] = byCategory[position.categoryId] || []).push(position);
    });
    let groups = 0;
    overview.categories.forEach(function (category) {
      const positions = byCategory[category.id] || [];
      if (positions.length === 0) return;
      groups += 1;
      const details = el('details', 'holding-group');
      const summary = el('summary', 'holding-summary');
      summary.appendChild(el('span', 'name', category.name));
      summary.appendChild(el('span', 'count', positions.length + ' 项'));
      summary.appendChild(el('span', 'sum', Fmt.cny(category.valueCny)));
      details.appendChild(summary);

      const list = el('ul', 'holding-list');
      positions.forEach(function (position) {
        const row = el('div', 'holding-row');
        const pos = el('div', 'pos');
        pos.appendChild(el('div', null, position.instrumentName));
        pos.appendChild(el('div', 'inst',
          position.institutionName + ' · ' + position.instrumentCode));
        row.appendChild(pos);
        row.appendChild(el('div', 'qty',
          Fmt.quantity(position.quantity) + ' ' + position.unit));
        row.appendChild(el('div', 'val',
          position.valueCny === null ? '缺价' : Fmt.cny(position.valueCny)));
        list.appendChild(row);
      });
      details.appendChild(list);
      card.appendChild(details);
    });
    if (groups === 0) card.appendChild(el('div', 'empty', '暂无持仓'));
    return card;
  },

  distributionCard(overview) {
    const card = el('div', 'card');
    card.appendChild(el('h2', 'card-title', '分布'));
    const grid = el('div', 'dist-grid');
    grid.appendChild(distPanel('机构分布', overview.institutions, function (row, index) {
      const palette = ['#e6a23c', '#e8613c', '#0984e3', '#6c5ce7', '#2e9e6b', '#b2bec3'];
      return palette[index % palette.length];
    }));
    grid.appendChild(distPanel('币种分布', overview.currencies, function (row) {
      const colors = {
        CNY: '#e6a23c', USD: '#0984e3', HKD: '#6c5ce7', PHYSICAL_GOLD: '#f9ca24',
      };
      return colors[row.id] || '#8a8a8a';
    }));
    card.appendChild(grid);
    return card;
  },
};

function clampPercent(value) {
  if (!Number.isFinite(value)) return 0;
  return Math.max(0, Math.min(100, value));
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
