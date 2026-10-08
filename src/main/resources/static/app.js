// Interview Scheduler frontend: a small single-page app with no build step.
//
// Request flow: a page function below calls api('/api/...') -> fetch() sends JSON (plus the session
// cookie and CSRF header) -> Spring Controller -> Service -> Repository (JDBC) -> PostgreSQL, and the
// JSON response comes back the same way. Errors come back as {status, message} and are shown in a banner.

'use strict';

const view = document.getElementById('view');
let me = null;            // logged-in user {id, username, fullName, role}, or null
let lastBooking = null;   // appointment returned by the booking POST, shown on the confirmation page
const slotSearch = { serviceId: '', providerId: '', date: '', page: 0 };
let keepFlash = false;    // true: keep the current banner through the next page change

// ---------- API helper ----------

function cookie(name) {
  const match = document.cookie.match(new RegExp('(?:^|; )' + name + '=([^;]*)'));
  return match ? decodeURIComponent(match[1]) : null;
}

/** fetch() wrapper: sends the CSRF token on writes, parses JSON, and throws on HTTP errors. */
async function api(path, { method = 'GET', json, form } = {}) {
  const headers = {};
  let body;
  if (json !== undefined) {
    headers['Content-Type'] = 'application/json';
    body = JSON.stringify(json);
  } else if (form) {
    body = new URLSearchParams(form);
  }
  if (method !== 'GET') {
    const token = cookie('XSRF-TOKEN');   // Spring Security sets this cookie; we echo it in a header
    if (token) headers['X-XSRF-TOKEN'] = token;
  }
  const res = await fetch(path, { method, headers, body, credentials: 'same-origin' });
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  if (!res.ok) {
    const err = new Error((data && data.message) || res.statusText);
    err.status = res.status;
    throw err;
  }
  return data;
}

// ---------- formatting ----------

const esc = s => String(s ?? '').replace(/[&<>"']/g, c =>
  ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

const money = price => '$' + Number(price).toFixed(2).replace(/\.00$/, '');

function when(start, end) {
  const s = new Date(start);
  const day = s.toLocaleDateString(undefined, { weekday: 'short', month: 'numeric', day: 'numeric' });
  const time = d => d.toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' });
  return end ? `${day}, ${time(s)}–${time(new Date(end))}` : `${day}, ${time(s)}`;
}

const badge = status => `<span class="badge status-${esc(status)}">${esc(status)}</span>`;

function flash(message, type = 'danger') {
  document.getElementById('flash').innerHTML = message
    ? `<div class="alert alert-${type} alert-dismissible mb-0" role="alert">${esc(message)}
         <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button></div>`
    : '';
}

function showError(err) {
  if (err.status === 401) {
    me = null;
    renderNav();
    flash('Please log in first.', 'warning');
    go('#/login?next=' + encodeURIComponent(location.hash), true);
  } else {
    flash(`${err.message}${err.status ? ` (HTTP ${err.status})` : ''}`);
  }
}

// ---------- session ----------

async function refreshMe() {
  me = await api('/api/auth/me');   // 204 (null) when nobody is logged in
  renderNav();
}

function renderNav() {
  document.querySelectorAll('[data-role]').forEach(el => { el.hidden = !me || me.role !== el.dataset.role; });
  document.querySelectorAll('[data-when="in"]').forEach(el => { el.hidden = !me; });
  document.querySelectorAll('[data-when="out"]').forEach(el => { el.hidden = !!me; });
  document.getElementById('whoami').textContent = me ? `${me.fullName} (${me.role.toLowerCase()})` : '';
}

document.getElementById('logoutBtn').onclick = async () => {
  await api('/api/auth/logout', { method: 'POST' });
  await refreshMe();
  flash('Logged out.', 'secondary');
  go('#/', true);
};

/** Page guard: returns true if the current user may see a page for this role. */
function requireRole(role) {
  if (!me) {
    go('#/login?next=' + encodeURIComponent(location.hash));
    return false;
  }
  if (me.role !== role) {
    view.innerHTML = `<div class="panel"><h1 class="h4">Not available</h1>
      <p class="mb-0">This page is for ${role.toLowerCase()}s. You are logged in as a ${me.role.toLowerCase()}.</p></div>`;
    return false;
  }
  return true;
}

// ---------- pages ----------

async function homePage() {
  const home = await api('/api/home');
  view.innerHTML = `
    <section class="panel hero mb-5">
      <div class="eyebrow mb-2">Mentor-led interview prep</div>
      <h1 class="display-5">${esc(home.appName)}</h1>
      <p class="lead text-secondary">Book resume reviews and mock interviews with experienced engineers.</p>
      <a class="btn btn-dark btn-lg" href="#/slots">Browse open slots</a>
      <span class="ms-3 text-secondary">${home.openSlotCount} open slots</span>
    </section>
    <div class="d-flex justify-content-between align-items-baseline">
      <h2 class="h3">Services</h2><span class="text-secondary small">Choose the support you need</span></div>
    <div class="row g-3 mb-5">${home.services.map(s => `
      <div class="col-sm-6 col-lg-3"><div class="panel h-100">
        <h3 class="h6">${esc(s.name)}</h3>
        <p class="small text-secondary">${esc(s.description)}</p>
        <div class="d-flex justify-content-between"><span>${s.durationMinutes} min</span>
          <strong>${money(s.price)}</strong></div>
      </div></div>`).join('')}
    </div>
    <div class="d-flex justify-content-between align-items-baseline">
      <h2 class="h3">Mentors</h2><span class="text-secondary small">Learn from experienced professionals</span></div>
    <div class="row g-3">${home.providers.map(p => `
      <div class="col-md-4"><div class="panel h-100">
        <div class="fw-semibold">${esc(p.name)}</div>
        <div class="small text-secondary">${esc(p.headline)}</div>
        <a class="small" href="#/slots" data-provider="${p.id}">See open slots</a>
      </div></div>`).join('')}
    </div>`;
  view.querySelectorAll('[data-provider]').forEach(a => a.onclick = () => {
    Object.assign(slotSearch, { serviceId: '', providerId: a.dataset.provider, date: '', page: 0 });
  });
}

async function slotsPage() {
  const home = await api('/api/home');
  view.innerHTML = `
    <div class="eyebrow">Find a time</div>
    <h1 class="display-6">Available Slots</h1>
    <p class="text-secondary">Filter available sessions by service, mentor, or date.</p>
    <form class="panel row g-3 align-items-end mb-4" id="filters">
      <div class="col-md"><label class="form-label fw-semibold" for="fService">Service</label>
        <select class="form-select" id="fService"><option value="">Any service</option>
          ${home.services.map(s => `<option value="${s.id}">${esc(s.name)}</option>`).join('')}</select></div>
      <div class="col-md"><label class="form-label fw-semibold" for="fProvider">Mentor</label>
        <select class="form-select" id="fProvider"><option value="">Any mentor</option>
          ${home.providers.map(p => `<option value="${p.id}">${esc(p.name)}</option>`).join('')}</select></div>
      <div class="col-md"><label class="form-label fw-semibold" for="fDate">Date</label>
        <input class="form-control" type="date" id="fDate"></div>
      <div class="col-md-auto"><button class="btn btn-dark px-4">Search</button>
        <button class="btn btn-outline-dark" type="button" id="clearFilters">Clear</button></div>
    </form>
    <div class="table-responsive"><table class="table table-app align-middle bg-white">
      <thead><tr><th>When</th><th>Service</th><th>Mentor</th><th>Price</th><th></th></tr></thead>
      <tbody id="slotRows"></tbody></table></div>
    <div class="d-flex justify-content-center align-items-center gap-3">
      <button class="btn btn-outline-dark" id="prev">Prev</button>
      <span id="pageInfo" class="text-secondary"></span>
      <button class="btn btn-outline-dark" id="next">Next</button>
    </div>`;

  const $ = id => document.getElementById(id);
  $('fService').value = slotSearch.serviceId;
  $('fProvider').value = slotSearch.providerId;
  $('fDate').value = slotSearch.date;

  async function load() {
    const q = new URLSearchParams({ page: slotSearch.page, size: 5 });
    if (slotSearch.serviceId) q.set('serviceId', slotSearch.serviceId);
    if (slotSearch.providerId) q.set('providerId', slotSearch.providerId);
    if (slotSearch.date) q.set('date', slotSearch.date);
    const res = await api('/api/slots?' + q);   // server pages with SQL LIMIT/OFFSET
    $('slotRows').innerHTML = res.items.length ? res.items.map(s => `
      <tr><td>${esc(when(s.startTime))}</td><td>${esc(s.serviceName)}</td><td>${esc(s.providerName)}</td>
          <td><strong>${money(s.price)}</strong></td>
          <td class="text-end"><a class="btn btn-outline-dark btn-sm px-3" href="#/book/${s.id}">Book</a></td></tr>`
    ).join('') : '<tr><td colspan="5" class="text-center text-secondary py-4">No open slots match.</td></tr>';
    $('pageInfo').textContent = `Page ${res.page + 1} of ${Math.max(res.totalPages, 1)} (${res.totalItems} open)`;
    $('prev').disabled = res.page === 0;
    $('next').disabled = res.page + 1 >= res.totalPages;
  }

  $('filters').onsubmit = e => {
    e.preventDefault();
    Object.assign(slotSearch, { serviceId: $('fService').value, providerId: $('fProvider').value,
                                date: $('fDate').value, page: 0 });
    load().catch(showError);
  };
  $('clearFilters').onclick = () => {
    Object.assign(slotSearch, { serviceId: '', providerId: '', date: '', page: 0 });
    $('fService').value = $('fProvider').value = $('fDate').value = '';
    load().catch(showError);
  };
  $('prev').onclick = () => { slotSearch.page--; load().catch(showError); };
  $('next').onclick = () => { slotSearch.page++; load().catch(showError); };
  await load();
}

async function bookPage(slotId) {
  if (!requireRole('CUSTOMER')) return;
  const slot = await api('/api/slots/' + slotId);
  view.innerHTML = `
    <div class="text-center mb-4">
      <div class="eyebrow">Final step</div>
      <h1 class="display-6">Book Appointment</h1>
      <p class="text-secondary">Review your session details and add a note for your mentor.</p>
    </div>
    <div class="row g-4 justify-content-center">
      <div class="col-md-5"><div class="panel h-100">
        <div class="eyebrow mb-3">Appointment summary</div>
        <dl class="mb-0">
          <div class="detail-row"><dt>Service</dt><dd>${esc(slot.serviceName)}</dd></div>
          <div class="detail-row"><dt>Mentor</dt><dd>${esc(slot.providerName)}</dd></div>
          <div class="detail-row"><dt>Time</dt><dd>${esc(when(slot.startTime, slot.endTime))}</dd></div>
          <div class="detail-row"><dt>Price</dt><dd>${money(slot.price)}</dd></div>
        </dl></div></div>
      <div class="col-md-6"><form class="panel bg-white h-100" id="bookForm" novalidate>
        <label class="form-label fw-semibold" for="notes">Notes</label>
        <p class="small text-secondary">Help your mentor prepare for your session.</p>
        <textarea class="form-control mb-1" id="notes" rows="6" maxlength="500"
                  placeholder="Target role, resume link, topics to focus on"></textarea>
        <div class="form-text mb-3">Optional, up to 500 characters.</div>
        <button class="btn btn-dark" id="confirmBtn">Confirm Booking</button>
        <a class="btn btn-outline-dark" href="#/slots">Cancel</a>
      </form></div>
    </div>`;

  document.getElementById('bookForm').onsubmit = async e => {
    e.preventDefault();
    const btn = document.getElementById('confirmBtn');
    btn.disabled = true;
    try {
      lastBooking = await api('/api/customer/appointments', {
        method: 'POST',
        json: { slotId: slot.id, serviceId: slot.serviceId, notes: document.getElementById('notes').value.trim() || null },
      });
      go('#/confirmation/' + lastBooking.id);
    } catch (err) {
      btn.disabled = false;
      if (err.status === 409) {   // someone else booked it first (or it overlaps another booking)
        flash(err.message + ' Please pick another slot.', 'warning');
      } else {
        showError(err);
      }
    }
  };
}

async function confirmationPage(apptId) {
  if (!requireRole('CUSTOMER')) return;
  let appt = lastBooking && String(lastBooking.id) === apptId ? lastBooking : null;
  if (!appt) {   // page was reloaded: look it up in my upcoming appointments
    appt = (await api('/api/customer/appointments?scope=upcoming')).find(a => String(a.id) === apptId);
  }
  if (!appt) {
    go('#/appointments');
    return;
  }
  view.innerHTML = `
    <div class="text-center mb-4">
      <div class="check-circle mb-3" aria-hidden="true">&#10003;</div>
      <div class="eyebrow">Appointment confirmed</div>
      <h1 class="display-6">You're booked!</h1>
      <p class="text-secondary">Your mentor can now see this session on their dashboard.</p>
    </div>
    <div class="panel mx-auto mb-4" style="max-width: 40rem">
      <div class="eyebrow mb-3">Appointment details</div>
      <dl class="row mb-0">
        <div class="col-md-6"><div class="detail-row"><dt>Appointment</dt><dd>#${appt.id}</dd></div>
          <div class="detail-row"><dt>Mentor</dt><dd>${esc(appt.providerName)}</dd></div></div>
        <div class="col-md-6"><div class="detail-row"><dt>Service</dt><dd>${esc(appt.serviceName)}</dd></div>
          <div class="detail-row"><dt>Time</dt><dd>${esc(when(appt.startTime, appt.endTime))}</dd></div></div>
      </dl>
    </div>
    <div class="text-center">
      <a class="btn btn-dark" href="#/appointments">View My Appointments</a>
      <a class="btn btn-outline-dark" href="#/slots">Back to Slots</a>
    </div>`;
}

function appointmentTable(list, { showCustomer, showProvider, cancellable }) {
  if (!list.length) return '<p class="text-secondary">Nothing here yet.</p>';
  return `<div class="table-responsive"><table class="table table-app align-middle bg-white">
    <thead><tr><th>#</th><th>When</th><th>Service</th>${showProvider ? '<th>Mentor</th>' : ''}
      ${showCustomer ? '<th>Customer</th>' : ''}<th>Notes</th><th>Status</th><th></th></tr></thead>
    <tbody>${list.map(a => `
      <tr><td>${a.id}</td><td>${esc(when(a.startTime, a.endTime))}</td><td>${esc(a.serviceName)}</td>
        ${showProvider ? `<td>${esc(a.providerName)}</td>` : ''}
        ${showCustomer ? `<td>${esc(a.customerName)}</td>` : ''}
        <td class="small">${esc(a.notes)}</td><td>${badge(a.status)}</td>
        <td class="text-end">${cancellable && a.status === 'BOOKED'
          ? `<button class="btn btn-outline-danger btn-sm" data-cancel="${a.id}">Cancel</button>` : ''}</td></tr>`
    ).join('')}</tbody></table></div>`;
}

/** Tabs for "upcoming" and "history"; loadInto(scope) fills #tabBody. */
function scopeTabs(loadInto) {
  const tabs = document.getElementById('scopeTabs');
  tabs.innerHTML = ['upcoming', 'history'].map((s, i) => `
    <li class="nav-item"><button class="nav-link text-dark ${i === 0 ? 'active' : ''}" data-scope="${s}">
      ${s === 'upcoming' ? 'Upcoming' : 'History'}</button></li>`).join('');
  tabs.querySelectorAll('[data-scope]').forEach(btn => btn.onclick = () => {
    tabs.querySelectorAll('.nav-link').forEach(b => b.classList.toggle('active', b === btn));
    loadInto(btn.dataset.scope).catch(showError);
  });
  return loadInto('upcoming');
}

async function myAppointmentsPage() {
  if (!requireRole('CUSTOMER')) return;
  view.innerHTML = `<div class="eyebrow">Your sessions</div><h1 class="display-6 mb-3">My Appointments</h1>
    <ul class="nav nav-tabs mb-3" id="scopeTabs"></ul><div id="tabBody"></div>`;

  async function load(scope) {
    const list = await api('/api/customer/appointments?scope=' + scope);
    const body = document.getElementById('tabBody');
    body.innerHTML = appointmentTable(list, { showProvider: true, cancellable: scope === 'upcoming' });
    body.querySelectorAll('[data-cancel]').forEach(btn => btn.onclick = async () => {
      if (!confirm(`Cancel appointment #${btn.dataset.cancel}? The slot will be released.`)) return;
      try {
        await api(`/api/customer/appointments/${btn.dataset.cancel}/cancel`, { method: 'POST' });
        flash(`Appointment #${btn.dataset.cancel} cancelled.`, 'success');
        await load(scope);
      } catch (err) { showError(err); }
    });
  }
  await scopeTabs(load);
}

async function providerPage() {
  if (!requireRole('PROVIDER')) return;
  const services = await api('/api/services');
  view.innerHTML = `
    <div class="eyebrow">Provider dashboard</div>
    <h1 class="display-6 mb-4">My Availability</h1>
    <div class="row g-4 mb-5">
      <div class="col-lg-4"><form class="panel" id="slotForm">
        <h2 class="h5">Add a slot</h2>
        <label class="form-label fw-semibold" for="sService">Service</label>
        <select class="form-select mb-3" id="sService" required>
          ${services.map(s => `<option value="${s.id}">${esc(s.name)} (${s.durationMinutes} min)</option>`).join('')}
        </select>
        <label class="form-label fw-semibold" for="sStart">Start time</label>
        <input class="form-control mb-1" type="datetime-local" id="sStart" required step="300">
        <div class="form-text mb-3">The end time is set from the service's length.</div>
        <button class="btn btn-dark">Add slot</button>
      </form></div>
      <div class="col-lg-8"><h2 class="h5">My upcoming slots</h2><div id="mySlots"></div></div>
    </div>
    <h2 class="h4">Appointments booked with me</h2>
    <ul class="nav nav-tabs mb-3" id="scopeTabs"></ul><div id="tabBody"></div>`;

  async function loadSlots() {
    const slots = await api('/api/provider/slots');
    const box = document.getElementById('mySlots');
    box.innerHTML = slots.length ? `<div class="table-responsive"><table class="table table-app align-middle bg-white">
      <thead><tr><th>When</th><th>Service</th><th>Status</th><th></th></tr></thead><tbody>
      ${slots.map(s => `<tr><td>${esc(when(s.startTime, s.endTime))}</td><td>${esc(s.serviceName)}</td>
        <td>${badge(s.status)}</td><td class="text-end">${s.status === 'OPEN'
          ? `<button class="btn btn-outline-danger btn-sm" data-remove="${s.id}">Remove</button>` : ''}</td></tr>`).join('')}
      </tbody></table></div>` : '<p class="text-secondary">No upcoming slots. Add one on the left.</p>';
    box.querySelectorAll('[data-remove]').forEach(btn => btn.onclick = async () => {
      try {
        await api('/api/provider/slots/' + btn.dataset.remove, { method: 'DELETE' });
        flash('Slot removed.', 'success');
        await loadSlots();
      } catch (err) { showError(err); }
    });
  }

  async function loadAppointments(scope) {
    const list = await api('/api/provider/appointments?scope=' + scope);
    document.getElementById('tabBody').innerHTML = appointmentTable(list, { showCustomer: true });
  }

  document.getElementById('slotForm').onsubmit = async e => {
    e.preventDefault();
    try {
      const slot = await api('/api/provider/slots', {
        method: 'POST',
        json: { serviceId: Number(document.getElementById('sService').value),
                startTime: document.getElementById('sStart').value },
      });
      flash(`Slot added: ${slot.serviceName}, ${when(slot.startTime, slot.endTime)}.`, 'success');
      await loadSlots();
    } catch (err) { showError(err); }
  };

  await loadSlots();
  await scopeTabs(loadAppointments);
}

function loginPage(params) {
  if (me) { go(home()); return; }
  view.innerHTML = `
    <div class="mx-auto" style="max-width: 24rem">
      <h1 class="h3 mb-3">Log in</h1>
      <form class="panel bg-white" id="loginForm">
        <label class="form-label fw-semibold" for="username">Username</label>
        <input class="form-control mb-3" id="username" autocomplete="username" required>
        <label class="form-label fw-semibold" for="password">Password</label>
        <input class="form-control mb-3" id="password" type="password" autocomplete="current-password" required>
        <button class="btn btn-dark w-100">Log in</button>
      </form>
      <p class="small text-secondary mt-3">Demo accounts (password <code>password123</code>):
        customers <code>sam.dev</code>, <code>jordan.dev</code>;
        providers <code>alice.mentor</code>, <code>raj.mentor</code>, <code>maria.mentor</code>.</p>
    </div>`;
  document.getElementById('loginForm').onsubmit = async e => {
    e.preventDefault();
    try {
      // Form-encoded POST handled by Spring Security; on success the server keeps us in its session.
      me = await api('/api/auth/login', { method: 'POST', form: {
        username: document.getElementById('username').value,
        password: document.getElementById('password').value } });
      renderNav();
      flash('');
      go(params.get('next') || home());
    } catch (err) {
      flash(err.status === 401 ? 'Invalid username or password.' : err.message);
    }
  };
}

const home = () => (me && me.role === 'PROVIDER' ? '#/provider' : '#/slots');

// ---------- router ----------

const routes = [
  [/^#?\/?$/, homePage],
  [/^#\/slots$/, slotsPage],
  [/^#\/book\/(\d+)$/, bookPage],
  [/^#\/confirmation\/(\d+)$/, confirmationPage],
  [/^#\/appointments$/, myAppointmentsPage],
  [/^#\/provider$/, providerPage],
  [/^#\/login$/, loginPage],
];

function go(hash, keepMessage = false) {
  keepFlash = keepMessage;
  if (location.hash === hash) render(); else location.hash = hash;
}

async function render() {
  const [path, query] = location.hash.split('?');
  const params = new URLSearchParams(query || '');
  for (const [pattern, page] of routes) {
    const match = path.match(pattern);
    if (match) {
      try {
        await (match[1] ? page(match[1]) : page(params));
      } catch (err) { showError(err); }
      return;
    }
  }
  view.innerHTML = '<p>Page not found. <a href="#/">Go home</a></p>';
}

window.addEventListener('hashchange', () => {
  if (!keepFlash) flash('');
  keepFlash = false;
  render();
});
refreshMe().catch(() => {}).finally(render);
