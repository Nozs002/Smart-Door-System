'use strict';

const element = (id) => document.getElementById(id);
const state = {
  token: null,
  inventory: null,
  kits: null,
  replacements: null,
  factory: null,
  kitRequest: null,
  refreshId: 0,
};
const sections = {
  inventory: ['Kho ESP32', 'Đăng ký bo, kiểm tra trạng thái và tải cấu hình để nạp firmware.'],
  kits: ['Tạo bộ cửa', 'Gắn hai bo ESP32 vào một cửa và cấp mã bàn giao cho chủ nhà.'],
  replacements: ['Thay Node', 'Dùng mã ủy quyền của chủ cửa để gắn bo và theo dõi tiến độ.'],
};
const endpoints = {
  inventory: '/technician/inventory',
  kits: '/technician/kits',
  replacements: '/technician/replacements',
};
const statuses = {
  UNCLAIMED: ['Chưa bàn giao', 'amber'],
  SETUP_PENDING: ['Chờ chủ cửa thiết lập', 'blue'],
  ACTIVE: ['Đang hoạt động', 'green'],
  DISABLED: ['Đã vô hiệu hóa', ''],
  OPEN: ['Chờ gắn bo', 'amber'],
  BOUND: ['Đã gắn · Chờ chủ cửa', 'blue'],
  REVOKING: ['Đang thu hồi bo cũ', 'amber'],
  COMPLETED: ['Đã hoàn tất', 'green'],
  CANCELLED: ['Đã hủy', ''],
  EXPIRED: ['Đã hết hạn', ''],
};

function nodeLabel(type) {
  return { NODE_1: 'Node 1', NODE_2: 'Node 2' }[type] || type;
}

function feedback(target, message = '', tone = 'error') {
  const box = element(target);
  box.textContent = message;
  box.dataset.tone = tone;
  box.hidden = !message;
}

function resetSession(message = '') {
  state.token = null;
  state.inventory = null;
  state.kits = null;
  state.replacements = null;
  state.factory = null;
  state.kitRequest = null;
  for (const id of ['register-dialog', 'factory-dialog', 'claim-dialog']) {
    element(id).close();
  }
  for (const id of ['login-form', 'register-form', 'kit-form', 'replacement-form']) {
    element(id).reset();
  }
  for (const id of ['board-search', 'board-filter', 'board-status', 'account-name', 'account-initial']) {
    const field = element(id);
    if ('value' in field) field.value = '';
    else field.textContent = '';
  }
  renderData();
  element('workspace').hidden = true;
  element('sign-in').hidden = false;
  feedback('login-error', message);
  element('login-form').elements.username.focus();
}

async function api(path, { method = 'GET', data, token = state.token } = {}) {
  const headers = { Accept: 'application/json, application/problem+json' };
  if (token) headers.Authorization = 'Bearer ' + token;
  if (data !== undefined) headers['Content-Type'] = 'application/json';

  let response;
  try {
    response = await fetch('/api/v1' + path, {
      method,
      headers,
      body: data === undefined ? undefined : JSON.stringify(data),
      cache: 'no-store',
      credentials: 'omit',
      signal: AbortSignal.timeout(20000),
    });
  } catch {
    throw new Error('Không liên lạc được với hệ thống. Kiểm tra kết nối rồi thử lại.');
  }

  if (response.status === 401 && token && token === state.token) {
    resetSession('Phiên đăng nhập đã hết. Vui lòng đăng nhập lại.');
  }

  const payload = await response.json().catch(() => null);
  if (!response.ok) {
    throw new Error(payload?.detail || payload?.message || 'Hệ thống báo lỗi ' + response.status + '.');
  }
  if (!payload || !Object.hasOwn(payload, 'data')) {
    throw new Error('Phản hồi của hệ thống không đúng định dạng.');
  }
  return payload.data;
}

async function run(button, operation, onError = (message) => feedback('notice', message)) {
  button.disabled = true;
  try {
    await operation();
  } catch (error) {
    onError(error.message);
  } finally {
    button.disabled = false;
  }
}

async function refreshData() {
  const token = state.token;
  if (!token) return [];
  const refreshId = ++state.refreshId;
  const results = await Promise.allSettled(Object.entries(endpoints).map(async ([key, path]) => {
    const records = await api(path, { token });
    if (!Array.isArray(records)) throw new Error('Danh sách không đúng định dạng.');
    return [key, records];
  }));
  if (token !== state.token || refreshId !== state.refreshId) return [];
  const failed = [];
  results.forEach((result, index) => {
    if (result.status === 'fulfilled') {
      const [key, records] = result.value;
      state[key] = records;
    } else {
      const view = Object.keys(endpoints)[index];
      failed.push(sections[view][0]);
    }
  });
  renderData();
  return failed;
}

async function refreshAfter(message) {
  const failed = await refreshData();
  if (!state.token) return;
  if (failed.length) {
    feedback('notice', message + ' Chưa cập nhật được danh sách ' + failed.join(', ') + '. Bấm Làm mới để xem lại.');
  } else {
    feedback('notice', message, 'success');
  }
}

function showView(view, focus = false) {
  element('page-title').textContent = sections[view][0];
  element('page-summary').textContent = sections[view][1];
  element('open-register').hidden = view !== 'inventory';
  feedback('notice');
  for (const key of Object.keys(sections)) {
    element(key).hidden = key !== view;
    const item = document.querySelector('[data-view="' + key + '"]');
    if (key === view) item.setAttribute('aria-current', 'page');
    else item.removeAttribute('aria-current');
  }
  if (focus) element('page-title').focus();
}

function dom(tag, content, className) {
  const item = document.createElement(tag);
  if (content !== undefined) item.textContent = content;
  if (className) item.className = className;
  return item;
}

function cell(row, value) {
  const item = row.insertCell();
  if (value instanceof Node) item.append(value);
  else item.textContent = value;
  return item;
}

function statusTag(label, tone = '') {
  return dom('span', label, 'tag ' + tone);
}

function knownStatus(status) {
  return statusTag(...(statuses[status] || [String(status ?? '–')]));
}

function emptyRow(body, count, message) {
  const item = cell(body.insertRow(), message);
  item.colSpan = count;
  item.className = 'empty';
}

function tableAction(label, handler) {
  const button = dom('button', label, 'button table-action');
  button.type = 'button';
  button.addEventListener('click', () => run(button, handler));
  return button;
}

function renderInventory() {
  const boards = state.inventory || [];
  const available = boards.filter((board) => board.deviceId == null).length;
  element('board-total').textContent = state.inventory ? boards.length : '–';
  element('board-available').textContent = state.inventory ? available : '–';
  element('board-assigned').textContent = state.inventory ? boards.length - available : '–';
  const doorByBoard = new Map();
  for (const kit of state.kits || []) {
    for (const board of kit.devices) doorByBoard.set(board.unitCode, kit.name);
  }
  for (const request of state.replacements || []) {
    if (request.newDeviceCode) doorByBoard.set(request.newDeviceCode, request.doorName);
  }
  const search = element('board-search').value.trim().toLowerCase();
  const filter = element('board-filter').value;
  const status = element('board-status').value;
  const filtered = boards.filter((board) => board.unitCode.toLowerCase().includes(search)
    && (!filter || board.nodeType === filter)
    && (!status || (board.deviceId == null ? 'available' : 'assigned') === status));
  const body = element('board-rows');
  body.replaceChildren();
  if (!filtered.length) {
    emptyRow(body, 5, !state.inventory ? 'Chưa tải danh sách bo.' :
      boards.length ? 'Không tìm thấy bo phù hợp.' : 'Kho chưa có bo. Bấm Đăng ký bo để bắt đầu.');
  }
  for (const board of filtered) {
    const row = body.insertRow();
    const linked = board.deviceId != null;
    cell(row, dom('code', board.unitCode, 'mono code-block'));
    cell(row, nodeLabel(board.nodeType));
    cell(row, linked ? statusTag('Đã gắn cửa', 'blue') : statusTag('Còn trong kho', 'green'));
    cell(row, doorByBoard.get(board.unitCode) || '–');
    cell(row, tableAction('Chuẩn bị nạp', () => openFactory(board.unitCode)));
  }
}

function renderKits() {
  const body = element('kit-rows');
  body.replaceChildren();
  if (!state.kits?.length) emptyRow(body, 4, state.kits ? 'Chưa tạo bộ cửa nào.' : 'Chưa tải danh sách bộ cửa.');
  for (const kit of state.kits || []) {
    const row = body.insertRow();
    const name = cell(row, dom('strong', kit.name));
    name.append(dom('div', 'Cửa #' + kit.doorId, 'muted'));
    const devices = cell(row, '');
    for (const board of kit.devices) {
      const line = dom('div', nodeLabel(board.nodeType) + ' ');
      line.append(dom('code', board.unitCode, 'mono'));
      devices.append(line);
    }
    cell(row, knownStatus(kit.status));
    if (kit.status === 'UNCLAIMED') {
      cell(row, tableAction('Cấp lại mã', async () => {
        const token = state.token;
        const result = await api(endpoints.kits + '/' + encodeURIComponent(kit.requestId) + '/claim-code', { method: 'POST' });
        if (token === state.token) showClaim(result, kit.name);
      }));
    } else {
      cell(row, '–');
    }
  }
}

function renderReplacements() {
  const body = element('replacement-rows');
  body.replaceChildren();
  if (!state.replacements?.length) {
    emptyRow(body, 4, state.replacements ? 'Chưa có yêu cầu thay Node đã gắn bo.' : 'Chưa tải lịch sử thay Node.');
  }
  for (const request of state.replacements || []) {
    const row = body.insertRow();
    const name = cell(row, dom('strong', request.doorName || 'Cửa #' + request.doorId));
    name.append(dom('div', nodeLabel(request.nodeType), 'muted'));
    cell(row, dom('code', request.oldDeviceCode, 'mono code-block'));
    cell(row, dom('code', request.newDeviceCode || '–', 'mono code-block'));
    cell(row, knownStatus(request.status));
  }
}

function renderData() {
  renderInventory();
  renderKits();
  renderReplacements();
  for (const [id, type] of [['node1-list', 'NODE_1'], ['node2-list', 'NODE_2'], ['replacement-list', null]]) {
    const options = (state.inventory || [])
      .filter((board) => board.deviceId == null && (!type || board.nodeType === type))
      .map((board) => {
        const option = dom('option');
        option.value = board.unitCode;
        option.label = nodeLabel(board.nodeType);
        return option;
      });
    element(id).replaceChildren(...options);
  }
}

async function openFactory(code) {
  const token = state.token;
  const board = await api(endpoints.inventory + '/' + encodeURIComponent(code) + '/factory');
  if (state.token !== token) return;
  if (!board?.firmwareConfig) throw new Error('Hệ thống chưa trả về cấu hình nạp.');
  state.factory = board;
  element('factory-code').textContent = board.unitCode;
  element('factory-ssid').textContent = board.setupSsid;
  element('factory-setup').textContent = board.setupCode;
  const directory = board.nodeType === 'NODE_1' ? 'firmware/node-auth/' : 'firmware/node-control/';
  element('factory-instruction').textContent = 'Lưu thành ' + directory + 'enrollment_config.local.h. Nếu file đã có, thay toàn bộ nội dung cũ bằng cấu hình bên dưới.';
  element('factory-config').value = board.firmwareConfig;
  feedback('factory-copy-status');
  element('factory-dialog').showModal();
}

function showClaim(result, doorName) {
  if (!result?.activationCode) throw new Error('Chưa có mã bàn giao. Bấm Cấp lại mã ở danh sách bộ cửa.');
  element('claim-door').textContent = doorName;
  element('claim-code').textContent = result.activationCode;
  const expiresAt = result.expiresAt;
  // The provisioning API returns a UTC LocalDateTime without a zone suffix.
  const hasOffset = expiresAt && /(?:Z|[+-]\d{2}:\d{2})$/i.test(expiresAt);
  const expiry = expiresAt ? new Date(hasOffset ? expiresAt : expiresAt + 'Z') : null;
  element('claim-expiry').textContent = expiry && !isNaN(expiry)
    ? 'Hết hạn lúc ' + new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' }).format(expiry)
    : '';
  feedback('copy-status');
  element('claim-dialog').showModal();
}

function formData(form) {
  return Object.fromEntries([...new FormData(form)].map(([key, value]) => [key, value.trim()]));
}

function requestId() {
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 15) | 64;
  bytes[8] = (bytes[8] & 63) | 128;
  const hex = Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('');
  return [hex.slice(0, 8), hex.slice(8, 12), hex.slice(12, 16), hex.slice(16, 20), hex.slice(20)].join('-');
}

function onSubmit(id, handler, onError) {
  element(id).addEventListener('submit', (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    const button = form.querySelector('[type="submit"]');
    if (!button.disabled) void run(button, () => handler(form), onError);
  });
}

onSubmit('login-form', async (form) => {
  feedback('login-error');
  const username = form.elements.username.value.trim();
  const password = form.elements.password.value;
  if (!username) throw new Error('Nhập tên đăng nhập.');
  const login = await api('/auth/login', { method: 'POST', data: { username, password } });
  if (!login?.accessToken) throw new Error('Không nhận được phiên đăng nhập.');
  form.elements.password.value = '';
  const profile = await api('/accounts/me', { token: login.accessToken });
  if (profile?.role !== 'TECHNICIAN') {
    await api('/auth/logout', { method: 'POST', token: login.accessToken }).catch(() => {});
    throw new Error('Khu vực này dành cho tài khoản kỹ thuật viên.');
  }
  state.token = login.accessToken;
  const name = profile.displayName || profile.username;
  element('account-name').textContent = name;
  element('account-initial').textContent = Array.from(name)[0].toLocaleUpperCase('vi');
  form.reset();
  element('sign-in').hidden = true;
  element('workspace').hidden = false;
  showView('inventory', true);
  feedback('notice', 'Đang tải dữ liệu…', 'success');
  const failed = await refreshData();
  if (state.token) feedback('notice', failed.length ? 'Chưa tải được ' + failed.join(', ') + '. Hãy bấm Làm mới.' : '');
}, (message) => feedback('login-error', message));

onSubmit('register-form', async (form) => {
  feedback('register-error');
  const token = state.token;
  const board = await api(endpoints.inventory, { method: 'POST', data: formData(form) });
  if (token !== state.token) return;
  element('register-dialog').close();
  await refreshAfter('Đã đăng ký ' + nodeLabel(board.nodeType) + '. Chọn Chuẩn bị nạp để tải cấu hình.');
}, (message) => feedback('register-error', message));

onSubmit('kit-form', async (form) => {
  feedback('notice');
  const fields = formData(form);
  const { name, node1Code, node2Code } = fields;
  if (!name || !node1Code || !node2Code) throw new Error('Nhập tên cửa và mã của cả hai bo.');
  if (node1Code === node2Code) throw new Error('Chọn hai bo ESP32 khác nhau.');
  const signature = JSON.stringify(fields);
  if (state.kitRequest?.signature !== signature) state.kitRequest = { signature, id: requestId() };
  const token = state.token;
  const kit = await api(endpoints.kits, { method: 'POST', data: { requestId: state.kitRequest.id, ...fields } });
  if (token !== state.token) return;
  state.kitRequest = null;
  form.reset();
  if (kit.activationCode) showClaim(kit, kit.name);
  await refreshAfter(kit.activationCode ? 'Đã tạo bộ cửa.' : 'Bộ cửa đã tồn tại. Nếu chưa bàn giao, chọn Cấp lại mã.');
});

onSubmit('replacement-form', async (form) => {
  feedback('notice');
  const data = formData(form);
  if (!data.authorizationCode || !data.unitCode) throw new Error('Nhập mã ủy quyền và mã bo thay thế.');
  const token = state.token;
  await api(endpoints.replacements, { method: 'POST', data });
  if (token !== state.token) return;
  form.reset();
  await refreshAfter('Đã tiếp nhận yêu cầu gắn bo. Xem tiến độ trong Lịch sử thay Node.');
});

element('logout').addEventListener('click', () => {
  const token = state.token;
  resetSession();
  void api('/auth/logout', { method: 'POST', token }).catch(() => {
    if (!state.token) feedback('login-error', 'Đã thoát khỏi web. Hệ thống chưa xác nhận thu hồi phiên.');
  });
});

element('refresh').addEventListener('click', (event) => run(event.currentTarget, async () => {
  feedback('notice', 'Đang tải dữ liệu…', 'success');
  const failed = await refreshData();
  if (!state.token) return;
  if (failed.length) feedback('notice', 'Chưa cập nhật được ' + failed.join(', ') + '.');
  else feedback('notice', 'Đã cập nhật dữ liệu.', 'success');
}));

for (const button of document.querySelectorAll('[data-view]')) {
  button.addEventListener('click', () => showView(button.dataset.view, true));
}
for (const button of document.querySelectorAll('[data-close]')) {
  button.addEventListener('click', () => element(button.dataset.close).close());
}
for (const id of ['board-search', 'board-filter', 'board-status']) {
  element(id).addEventListener('input', renderInventory);
}

element('open-register').addEventListener('click', () => {
  feedback('register-error');
  element('register-form').reset();
  element('register-dialog').showModal();
});
element('factory-dialog').addEventListener('close', () => {
  state.factory = null;
  for (const id of ['factory-code', 'factory-ssid', 'factory-setup']) element(id).textContent = '';
  element('factory-config').value = '';
  feedback('factory-copy-status');
});
element('copy-config').addEventListener('click', async () => {
  const board = state.factory;
  if (!board) return;
  try {
    await navigator.clipboard.writeText(board.firmwareConfig);
    if (board === state.factory) feedback('factory-copy-status', 'Đã sao chép cấu hình nạp.', 'success');
  } catch {
    if (board !== state.factory) return;
    const field = element('factory-config');
    field.focus();
    field.select();
    feedback('factory-copy-status', 'Đã chọn toàn bộ cấu hình. Nhấn Ctrl+C để sao chép.', 'success');
  }
});
element('download').addEventListener('click', () => {
  if (!state.factory) return;
  const url = URL.createObjectURL(new Blob([state.factory.firmwareConfig], { type: 'text/plain' }));
  const link = dom('a');
  link.download = 'enrollment_config.local.h';
  link.href = url;
  document.body.append(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 10000);
});
element('claim-dialog').addEventListener('close', () => {
  for (const id of ['claim-door', 'claim-code', 'claim-expiry']) element(id).textContent = '';
  feedback('copy-status');
});
element('copy-code').addEventListener('click', async () => {
  try {
    await navigator.clipboard.writeText(element('claim-code').textContent);
    feedback('copy-status', 'Đã sao chép mã.', 'success');
  } catch {
    const selection = window.getSelection();
    const range = document.createRange();
    range.selectNodeContents(element('claim-code'));
    selection.removeAllRanges();
    selection.addRange(range);
    feedback('copy-status', 'Mã đã được chọn. Nhấn Ctrl+C để sao chép.', 'success');
  }
});
