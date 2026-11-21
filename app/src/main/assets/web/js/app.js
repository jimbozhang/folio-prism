/* App shell: state, header, navigation and the shared DOM helpers. */
const State = {
  portfolio: null,
  overview: null,
  view: 'overview',
  ledger: {
    type: 'all',
    institutionId: 'all',
    instrumentId: 'all',
    keyword: '',
    trackedPosition: '',
  },
  charts: { type: 'assets', ranges: {} },
};

function el(tag, cls, text) {
  const node = document.createElement(tag);
  if (cls) node.className = cls;
  if (text !== undefined && text !== null) node.textContent = text;
  return node;
}

function clearNode(node) {
  while (node.firstChild) node.removeChild(node.firstChild);
}

let toastTimer = null;
function showToast(message, isError) {
  const toast = document.getElementById('toast');
  toast.textContent = message;
  toast.className = 'toast' + (isError ? ' error' : '');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { toast.className = 'toast hidden'; }, 3000);
}

function updateHeader() {
  const overview = State.overview;
  const asOf = document.getElementById('as-of');
  if (!overview) { asOf.textContent = ''; return; }
  const updated = overview.updatedAt
    ? Fmt.datePart(overview.updatedAt) + ' ' + Fmt.timePart(overview.updatedAt)
    : '尚未保存';
  asOf.textContent = '更新于 ' + updated +
    ' · 机构 ' + overview.counts.institutions +
    ' · 标的 ' + overview.counts.instruments +
    ' · 事件 ' + overview.counts.events;
}

function render() {
  const root = document.getElementById('app');
  if (window.ChartsView) ChartsView.destroy();
  clearNode(root);
  if (!State.overview || !State.portfolio) {
    root.appendChild(el('div', 'loading', '正在加载…'));
    return;
  }
  document.querySelectorAll('.tab').forEach(function (tab) {
    tab.classList.toggle('active', tab.dataset.view === State.view);
  });
  if (State.view === 'overview') OverviewView.render(root, State);
  else if (State.view === 'charts') ChartsView.render(root, State);
  else LedgerView.render(root, State);
}

async function reload() {
  const results = await Promise.all([Api.portfolio(), Api.overview()]);
  State.portfolio = results[0];
  State.overview = results[1];
  updateHeader();
}

async function boot() {
  document.querySelectorAll('.tab').forEach(function (tab) {
    tab.addEventListener('click', function () {
      if (State.view === tab.dataset.view) return;
      State.view = tab.dataset.view;
      render();
    });
  });

  document.getElementById('refresh-quotes').addEventListener('click', async function (event) {
    const btn = event.currentTarget;
    btn.disabled = true;
    btn.textContent = '刷新中…';
    try {
      await Api.refreshQuotes();
      await reload();
      showToast('行情已刷新');
      render();
    } catch (err) {
      showToast(err.message, true);
    } finally {
      btn.disabled = false;
      btn.textContent = '刷新行情';
    }
  });

  // Data file download: the server answers with Content-Disposition
  // attachment, so the browser saves it without leaving this page.
  document.getElementById('download-data').addEventListener('click', function () {
    window.location.href = '/api/download';
  });

  // Data file upload: only a file literally named folio-prism.json is
  // accepted; format compatibility is enforced server-side before the
  // on-device file is replaced.
  const uploadFile = document.getElementById('upload-file');
  document.getElementById('upload-data').addEventListener('click', function () {
    uploadFile.click();
  });
  uploadFile.addEventListener('change', async function () {
    const file = uploadFile.files && uploadFile.files[0];
    uploadFile.value = '';
    if (!file) return;
    if (file.name !== 'folio-prism.json') {
      showToast('上传文件名必须为 folio-prism.json', true);
      return;
    }
    let text;
    try {
      text = await file.text();
      JSON.parse(text);
    } catch (e) {
      showToast('文件内容不是有效的 JSON', true);
      return;
    }
    const btn = document.getElementById('upload-data');
    btn.disabled = true;
    btn.textContent = '上传中…';
    try {
      await Api.uploadData(text);
      await reload();
      showToast('数据已更新');
      render();
    } catch (err) {
      showToast(err.message, true);
    } finally {
      btn.disabled = false;
      btn.textContent = '上传数据';
    }
  });

  // APK upload: the file is stored into /sdcard/Documents/folio-prism so the
  // phone can install a newer build straight from the workbench.
  const apkFile = document.getElementById('apk-file');
  document.getElementById('upload-apk').addEventListener('click', function () {
    apkFile.click();
  });
  apkFile.addEventListener('change', async function () {
    const file = apkFile.files && apkFile.files[0];
    apkFile.value = '';
    if (!file) return;
    if (!/\.apk$/i.test(file.name)) {
      showToast('请选择 .apk 安装包文件', true);
      return;
    }
    const btn = document.getElementById('upload-apk');
    btn.disabled = true;
    btn.textContent = '上传中…';
    try {
      const saved = await Api.uploadApk(file);
      showToast('APK 已保存：' + saved.fileName + '（' + Fmt.bytes(saved.sizeBytes) + '）');
    } catch (err) {
      showToast(err.message, true);
    } finally {
      btn.disabled = false;
      btn.textContent = '上传APK';
    }
  });

  try {
    await reload();
    render();
  } catch (err) {
    const root = document.getElementById('app');
    clearNode(root);
    root.appendChild(el('div', 'banner error', '加载失败：' + err.message));
  }
}

boot();
