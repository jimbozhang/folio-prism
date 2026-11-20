/* Thin fetch wrapper; all errors carry a user-facing Chinese message. */
const Api = {
  async portfolio() {
    const r = await fetch('/api/portfolio', { cache: 'no-store' });
    if (!r.ok) throw new Error('数据文件读取失败（HTTP ' + r.status + '）');
    return r.json();
  },

  async overview() {
    return this.get('/api/overview');
  },

  async get(path) {
    const r = await fetch(path, { cache: 'no-store' });
    if (!r.ok) throw await this.toError(r);
    return r.json();
  },

  async send(method, path, body) {
    const r = await fetch(path, {
      method,
      headers: { 'Content-Type': 'application/json; charset=utf-8' },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    if (!r.ok) throw await this.toError(r);
    return r.json();
  },

  async toError(r) {
    let message = 'HTTP ' + r.status;
    try {
      const data = await r.json();
      if (data && data.error) message = data.error;
    } catch (e) { /* not JSON */ }
    const error = new Error(message);
    error.status = r.status;
    return error;
  },

  chart(type, from, to, categoryId) {
    let path = '/api/chart?type=' + encodeURIComponent(type) +
      '&from=' + encodeURIComponent(from) + '&to=' + encodeURIComponent(to);
    if (categoryId) path += '&categoryId=' + encodeURIComponent(categoryId);
    return this.get(path);
  },

  flows(from, to) {
    return this.get('/api/flows?from=' + encodeURIComponent(from) + '&to=' + encodeURIComponent(to));
  },

  expenses(from, to) {
    return this.get('/api/expenses?from=' + encodeURIComponent(from) + '&to=' + encodeURIComponent(to));
  },

  dividends(from, to) {
    return this.get('/api/dividends?from=' + encodeURIComponent(from) + '&to=' + encodeURIComponent(to));
  },

  refreshQuotes() {
    return this.send('POST', '/api/quotes/refresh');
  },

  uploadData(rawJson) {
    return this.sendRaw('POST', '/api/upload', rawJson);
  },

  /** Raw binary upload; the filename travels as the `name` query param. */
  async uploadApk(file) {
    const path = '/api/apk?name=' + encodeURIComponent(file.name);
    const r = await fetch(path, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/octet-stream' },
      body: file,
    });
    if (!r.ok) throw await this.toError(r);
    return r.json();
  },

  async sendRaw(method, path, body) {
    const r = await fetch(path, {
      method,
      headers: { 'Content-Type': 'application/json; charset=utf-8' },
      body,
    });
    if (!r.ok) throw await this.toError(r);
    return r.json();
  },

  createEvent(draft) {
    return this.send('POST', '/api/events', draft);
  },

  updateEvent(id, draft) {
    return this.send('PUT', '/api/events/' + encodeURIComponent(id), draft);
  },

  deleteEvent(id) {
    return this.send('DELETE', '/api/events/' + encodeURIComponent(id));
  },
};
