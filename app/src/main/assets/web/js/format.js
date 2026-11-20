/* Number/date formatting. Money values arrive as decimal strings and are
 * displayed with exactly the digits they carry — no rounding — so small
 * residues stay visible and traceable. Values that only exist as JS numbers
 * fall back to a 2-decimal format. */
const Fmt = (() => {
  const fmt2 = new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });

  function pad(n) { return String(n).padStart(2, '0'); }

  function num(value) {
    if (value === null || value === undefined || value === '') return null;
    const n = Number(value);
    return Number.isFinite(n) ? n : null;
  }

  /* Exact decimal rendering of a plain decimal string: keeps every digit,
   * adds thousands separators, adds no digits. */
  function exact(value) {
    if (value === null || value === undefined || value === '') return null;
    const s = String(value);
    if (!/^-?\d+(\.\d+)?$/.test(s)) return null;
    let body = s;
    if (body[0] === '-') body = body.slice(1);
    const dot = body.indexOf('.');
    const int = dot === -1 ? body : body.slice(0, dot);
    const frac = dot === -1 ? '' : body.slice(dot);
    return (s[0] === '-' ? '-' : '') + int.replace(/\B(?=(\d{3})+(?!\d))/g, ',') + frac;
  }

  function cny(value) {
    const t = exact(value);
    if (t !== null) return '¥' + t;
    const n = num(value);
    return n === null ? '--' : '¥' + fmt2.format(n);
  }

  function plain(value, digits) {
    const n = num(value);
    if (n === null) return '--';
    if (digits === undefined) {
      const t = exact(n);
      return t !== null ? t : fmt2.format(n);
    }
    return n.toFixed(digits);
  }

  function percent(fraction, digits = 1) {
    const n = num(fraction);
    if (n === null) return '--';
    return (n * 100).toFixed(digits) + '%';
  }

  function pctPoints(value, digits = 1) {
    const n = num(value);
    if (n === null) return '--';
    return n.toFixed(digits) + '%';
  }

  function quantity(value) {
    const t = exact(value);
    if (t !== null) return t;
    const n = num(value);
    return n === null ? '--' : fmt2.format(n);
  }

  function native(value, currency) {
    const t = exact(value);
    if (t !== null) {
      const prefix = currency === 'USD' ? '$' : currency === 'HKD' ? 'HK$' : '¥';
      return prefix + t;
    }
    const n = num(value);
    if (n === null) return '--';
    const prefix = currency === 'USD' ? '$' : currency === 'HKD' ? 'HK$' : '¥';
    return prefix + fmt2.format(n);
  }

  function bytes(value) {
    const n = num(value);
    if (n === null) return '--';
    const units = ['B', 'KB', 'MB', 'GB'];
    let i = 0;
    let v = n;
    while (v >= 1024 && i < units.length - 1) {
      v /= 1024;
      i += 1;
    }
    return v.toFixed(i === 0 ? 0 : 1) + ' ' + units[i];
  }

  function signedNative(value, currency) {
    const n = num(value);
    if (n === null) return '--';
    return (n < 0 ? '-' : '+') + native(Math.abs(n), currency);
  }

  function datePart(value) {
    if (!value) return '';
    return String(value).replace('T', ' ').slice(0, 10);
  }

  function timePart(value) {
    if (!value) return '';
    const s = String(value).replace('T', ' ');
    return s.length >= 16 ? s.slice(11, 16) : '';
  }

  function timestamp(value) {
    if (!value) return 0;
    const t = Date.parse(String(value).replace(' ', 'T'));
    return Number.isFinite(t) ? t : 0;
  }

  function nowLocal() {
    const d = new Date();
    return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) +
      ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes());
  }

  function datetimeLocal(value) {
    if (!value) return nowLocal().replace(' ', 'T');
    const s = String(value).replace(' ', 'T');
    return s.length >= 16 ? s.slice(0, 16) : s;
  }

  function dateLabel(value) {
    const date = datePart(value);
    const today = new Date();
    const todayStr = today.getFullYear() + '-' + pad(today.getMonth() + 1) + '-' + pad(today.getDate());
    const y = new Date(today);
    y.setDate(today.getDate() - 1);
    const yesterdayStr = y.getFullYear() + '-' + pad(y.getMonth() + 1) + '-' + pad(y.getDate());
    const parts = date.split('-');
    const m = Number(parts[1]);
    const d = Number(parts[2]);
    if (date === todayStr) return '今天 ' + m + '月' + d + '日';
    if (date === yesterdayStr) return '昨天 ' + m + '月' + d + '日';
    return date;
  }

  return {
    num, cny, plain, percent, pctPoints, quantity,
    native, signedNative, bytes, datePart, timePart, timestamp,
    nowLocal, datetimeLocal, dateLabel,
  };
})();
