/* LegalCase ERP - browser client (served by the backend at http://localhost:8080) */
"use strict";

/* ---------------- API helper ---------------- */
let TOKEN = localStorage.getItem("lce_token") || "";
let ME = null;

async function api(path, opts = {}) {
  const headers = opts.headers || {};
  if (TOKEN) headers["Authorization"] = "Bearer " + TOKEN;
  if (opts.json) { headers["Content-Type"] = "application/json"; opts.body = JSON.stringify(opts.json); }
  const res = await fetch(path, { ...opts, headers });
  if (res.status === 401) { logout(true); throw new Error("Session expired"); }
  if (res.status >= 400) {
    let msg = res.status + " " + res.statusText;
    try { const j = await res.json(); if (j.error) msg = j.error; } catch (e) {}
    throw new Error(msg);
  }
  return res;
}
async function apiJson(path, opts) { const r = await api(path, opts); return r.status === 204 ? null : r.json(); }
async function upload(path, formData) {
  const res = await fetch(path, { method: "POST", headers: { Authorization: "Bearer " + TOKEN }, body: formData });
  if (res.status >= 400) { let msg = res.statusText; try { const j = await res.json(); if (j.error) msg = j.error; } catch (e) {} throw new Error(msg); }
  return res.json();
}
async function download(path, filename) {
  const res = await api(path);
  const blob = await res.blob();
  const a = document.createElement("a");
  a.href = URL.createObjectURL(blob);
  a.download = filename;
  a.click();
  URL.revokeObjectURL(a.href);
}
function esc(s) { return String(s == null ? "" : s).replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c])); }
function toast(msg, type) {
  const t = document.getElementById("toast");
  t.textContent = msg;
  t.className = "toast " + (type || "");
  clearTimeout(t._h);
  t._h = setTimeout(() => t.className = "toast hidden", 3200);
}
function fmtDate(s) { if (!s) return "—"; const d = new Date(s); return isNaN(d) ? s : d.toLocaleDateString(undefined, { year: "numeric", month: "short", day: "numeric" }); }
function daysLeft(dateStr) {
  const d = new Date(dateStr + "T00:00:00");
  return Math.round((d - new Date(new Date().toDateString())) / 86400000);
}
function showModal(html) {
  const m = document.createElement("div");
  m.className = "modal-back";
  m.id = "modal";
  m.innerHTML = `<div class="modal">${html}<button class="btn btn-ghost btn-sm" style="margin-top:12px" onclick="closeModal()">Close</button></div>`;
  document.body.appendChild(m);
}
function closeModal() { const m = document.getElementById("modal"); if (m) m.remove(); }
function statusChip(status) {
  const map = { OPEN: "c-green", IN_LITIGATION: "c-blue", PENDING_HEARING: "c-amber", ON_HOLD: "c-amber", CLOSED: "c-gray" };
  return `<span class="chip ${map[status] || "c-gray"}">${esc(status || "")}</span>`;
}

/* ---------------- views ---------------- */
const NAV = {
  LAWYER: [["dashboard", "Dashboard"], ["cases", "Cases"], ["billing", "Billing & Trust"], ["precedents", "Precedent Search"], ["calendar", "Calendar"], ["activity", "Activity"], ["ratings", "Client Ratings"]],
  CLIENT: [["home", "My Case"], ["documents", "Documents"], ["messages", "Messages"], ["rate", "Rate Lawyer"]],
  ADMIN: [["users", "Users & Roles"], ["audit", "Audits & Reports"], ["system", "System & Model"]],
};
const TITLES = {
  cases: ["Cases"], billing: ["Billing & trust"], calendar: ["Calendar sync"],
  activity: ["Activity & audit"],
  ratings: ["Client ratings"],
  dashboard: ["Dashboard"],
  precedents: ["Precedent & document search"],
  home: ["My case", "Your secure client portal"], documents: ["Documents", "Download your documents and share new ones"],
  messages: ["Messages", "Secure messages with your attorney"], rate: ["Rate your lawyer", "Your feedback helps the firm improve"],
  users: ["Users & roles"],
  audit: ["Audits & reports"],
  system: ["System & model management"],
};

function renderNav() {
  const nav = document.getElementById("nav");
  nav.innerHTML = "";
  (NAV[ME.role] || []).forEach(([key, label]) => {
    const b = document.createElement("button");
    b.textContent = label;
    b.onclick = () => show(key);
    if (key === current) b.classList.add("active");
    nav.appendChild(b);
  });
}

let current = null;
async function show(key, param) {
  current = key;
  if (key === "case_detail") window._caseParam = param;
  renderNav();
  const el = document.getElementById("content");
  const [title, sub] = TITLES[key] || [key, ""];
  try {
    switch (key) {
      case "dashboard": el.innerHTML = await dashboardView(); break;
      case "cases": el.innerHTML = await casesView(param); break;
      case "billing": el.innerHTML = await firmBillingView(); break;
      case "case_detail": el.innerHTML = await caseDetailView(param); break;
      case "precedents": el.innerHTML = await precedentsView(param); break;
      case "calendar": el.innerHTML = await calendarView(param); break;
      case "activity": el.innerHTML = await activityView(); break;
      case "ratings": el.innerHTML = await ratingsView(); break;
      case "home": el.innerHTML = await clientHomeView(); break;
      case "documents": el.innerHTML = await clientDocumentsView(); break;
      case "messages": el.innerHTML = await clientMessagesView(); break;
      case "rate": el.innerHTML = await rateView(); break;
      case "users": el.innerHTML = await usersView(); break;
      case "audit": el.innerHTML = await auditView(); break;
      case "system": el.innerHTML = await systemView(); break;
      default: el.innerHTML = "";
    }
  } catch (e) {
    el.innerHTML = `<div class="error">${esc(e.message)}</div>`;
  }
  el.scrollTop = 0;
}

/* ================= LAWYER ================= */
async function dashboardView() {
  const [cases, deadlines, activity, ratingsAvg, unread] = await Promise.all([
    apiJson("/api/cases"), apiJson("/api/deadlines"),
    apiJson("/api/activity?limit=8"), apiJson("/api/ratings/average"),
    apiJson("/api/me/unread-lawyer")
  ]);
  const active = cases.filter(c => c.status !== "CLOSED").length;
  const next30 = deadlines.filter(d => daysLeft(d.date) >= 0 && daysLeft(d.date) <= 30).length;
  const overdue = deadlines.filter(d => daysLeft(d.date) < 0).length;
  const upcoming = deadlines.filter(d => daysLeft(d.date) >= 0).sort((a, b) => a.date < b.date ? -1 : 1).slice(0, 6);
  return `
  <div class="page-title">${TITLES.dashboard[0]}</div>
  <div class="stats">
    <div class="stat"><div class="k">ACTIVE MATTERS</div><div class="v">${active}</div></div>
    <div class="stat"><div class="k">DEADLINES IN 30 DAYS</div><div class="v">${next30}</div></div>
    <div class="stat"><div class="k">OVERDUE DEADLINES</div><div class="v" style="color:${overdue ? "var(--red)" : "var(--green)"}">${overdue}</div></div>
    <div class="stat"><div class="k">CLIENT RATING</div><div class="v">${ratingsAvg.average ? ratingsAvg.average.toFixed(1) : "—"} / 5</div></div>
    <div class="stat" style="cursor:pointer" onclick="gotoUnreadCase()" title="Go to the new message"><div class="k">UNREAD CLIENT MESSAGES</div><div class="v" style="color:${unread.unread ? "var(--red)" : "var(--green)"}">${unread.unread}</div></div>
  </div>

  <div class="grid2">
    <div class="card"><h3>Deadlines per month</h3>
      ${deadlineChart(deadlines)}
    </div>
    <div class="card"><h3>Cases by status</h3>
      ${caseStatusChart(cases)}
    </div>
  </div>

  <div class="grid2">
    <div class="card"><h3>Upcoming deadlines</h3>
      ${upcoming.map(d => `<div class="row-actions" style="justify-content:space-between;margin:4px 0">
        <span>${esc(d.title)} — ${esc(d.caseReference)} · ${fmtDate(d.date)}</span>
        <span class="chip ${daysLeft(d.date) <= 7 ? "c-amber" : "c-blue"}">${daysLeft(d.date)}d</span></div>`).join("") || '<p class="page-sub">No upcoming deadlines.</p>'}
    </div>
    <div class="card"><h3>Recent activity</h3>
      ${activity.map(a => `<div class="row-actions" style="justify-content:space-between;margin:4px 0">
        <span>${esc(a.action)} — ${esc(a.details)}</span>
        <span class="chip ${a.level === "SUCCESS" ? "c-green" : a.level === "WARNING" ? "c-amber" : "c-blue"}">${esc(a.level)}</span></div>`).join("") || '<p class="page-sub">No activity yet.</p>'}
    </div>
  </div>`;
}

const MONTH_NAMES = ["Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"];
function monthLabel(k) { // k = yyyy-mm
  const parts = (k || "").split("-");
  if (parts.length < 2) return k || "";
  return MONTH_NAMES[+parts[1] - 1] + " " + parts[0];
}

/* Pure-SVG bar chart: deadlines per month. No count numbers on the bars -
   hover shows the count; clicking a bar opens the Calendar filtered to that month. */
function deadlineChart(deadlines) {
  const months = {};
  deadlines.forEach(d => {
    const m = (d.date || "").slice(0, 7); // yyyy-mm
    if (m) months[m] = (months[m] || 0) + 1;
  });
  const keys = Object.keys(months).sort();
  if (!keys.length) return '<p class="page-sub">No deadlines recorded.</p>';
  const max = Math.max(...Object.values(months), 1);
  const showLabels = keys.length <= 8; // avoid cramped labels
  const bw = 46, gap = 18, base = 150, left = 8;
  const w = keys.length * (bw + gap) + left + 8;
  const h = base + (showLabels ? 42 : 20);
  const bars = keys.map((k, i) => {
    const n = months[k];
    const bh = Math.max(4, Math.round((n / max) * (base - 18)));
    const x = left + i * (bw + gap);
    const y = base - bh;
    const label = showLabels
      ? `<text x="${x + bw / 2}" y="${base + 18}" text-anchor="middle" font-size="10" fill="#66738C">${esc(monthLabel(k))}</text>`
      : "";
    return `<g class="chart-click" onclick="show('calendar',{month:'${k}'})" style="cursor:pointer">
        <title>${esc(monthLabel(k))} - ${n} deadline${n === 1 ? "" : "s"} (click to view)</title>
        <rect x="${x}" y="${y}" width="${bw}" height="${bh}" rx="4" fill="#C9A227"/>
        ${label}
      </g>`;
  }).join("");
  return `<svg viewBox="0 0 ${w} ${h}" style="max-width:100%;height:auto">
    <line x1="${left}" y1="${base}" x2="${w - 4}" y2="${base}" stroke="#E2E7F0"/>${bars}</svg>
`;
}

/* Horizontal bars: cases grouped by status. Click a row to open the case
   list filtered to that status. */
function caseStatusChart(cases) {
  const colors = { OPEN: "#2e7d32", IN_LITIGATION: "#1565c0", PENDING_HEARING: "#e65100", ON_HOLD: "#6a1b9a", CLOSED: "#8a95ab" };
  const counts = {};
  cases.forEach(c => { const st = c.status || "OPEN"; counts[st] = (counts[st] || 0) + 1; });
  const total = cases.length || 1;
  const order = ["OPEN", "IN_LITIGATION", "PENDING_HEARING", "ON_HOLD", "CLOSED"];
  const rows = order.filter(s => counts[s]).map(s => {
    const pct = Math.round((counts[s] / total) * 100);
    return `<div class="status-row chart-click" onclick="show('cases',{status:'${s}'})" title="View ${esc(s.replace(/_/g, " "))} cases">
      <div class="status-label">${esc(s.replace(/_/g, " "))} <b>${counts[s]}</b></div>
      <div class="status-track"><div class="status-fill" style="width:${pct}%;background:${colors[s] || "#8a95ab"}"></div></div>
      <div class="status-pct">${pct}%</div>
    </div>`;
  }).join("");
  return (rows || '<p class="page-sub">No cases.</p>');
}

/* ================= LAWYER ================= */
let _allCases = [];
let _statusFilter = "ALL";
async function casesView(param) {
  _allCases = await apiJson("/api/cases");
  _statusFilter = (param && param.status) || "ALL";
  const STATUSES = ["ALL", "OPEN", "IN_LITIGATION", "PENDING_HEARING", "ON_HOLD", "CLOSED"];
  const opts = STATUSES.map(st => `<option value="${st}" ${st === _statusFilter ? "selected" : ""}>${st === "ALL" ? "All statuses" : st.replace(/_/g, " ")}</option>`).join("");
  return `
    <div class="page-title">${TITLES.cases[0]}</div>
    <div class="card">
      <div class="searchbar">
        <input id="case-search" placeholder="Search reference, client, party..." oninput="searchCases(this.value)">
        <select id="status-filter" style="width:190px" onchange="filterCasesByStatus(this.value)">${opts}</select>
        <button class="btn btn-primary" onclick="newCase()">+ New case</button>
      </div>
      <table><thead><tr><th>Reference</th><th>Case title</th><th>Client</th><th>Opposing party</th><th>Status</th><th>Hearing</th></tr></thead>
      <tbody id="case-rows">${caseRows(filteredCases())}</tbody></table>
    </div>`;
}
function filteredCases() {
  if (_statusFilter === "ALL") return _allCases;
  return _allCases.filter(c => (c.status || "OPEN") === _statusFilter);
}
function filterCasesByStatus(st) {
  _statusFilter = st;
  document.getElementById("case-rows").innerHTML = caseRows(filteredCases());
}
function caseRows(cases) {
  return cases.map(c => `
    <tr onclick="show('case_detail','${c.id}')" style="cursor:pointer">
      <td><b>${esc(c.reference)}</b></td><td>${esc(c.title)}</td><td>${esc(c.clientName)}</td>
      <td>${esc(c.opposingParty)}</td><td>${statusChip(c.status)}</td>
      <td>${esc(c.hearingDate || "—")}</td></tr>`).join("");
}
async function searchCases(q) {
  const cases = await apiJson("/api/cases?q=" + encodeURIComponent(q));
  _allCases = cases;
  document.getElementById("case-rows").innerHTML = caseRows(filteredCases());
}
let MATTER_TYPES = null;
async function matterTypes() {
  if (!MATTER_TYPES) MATTER_TYPES = await apiJson("/api/matter-types");
  return MATTER_TYPES;
}
async function newCase() {
  const types = await matterTypes();
  showModal(`
    <h3>New case</h3>
    <label>Case title *</label><input id="nc-title" placeholder="e.g. Mokoena v Siyaya Transport">
    <label>Client name</label><input id="nc-client" placeholder="Client name" oninput="scheduleConflictCheck()">
    <label>Opposing party</label><input id="nc-opposing" placeholder="Opposing party" oninput="scheduleConflictCheck()">
    <label>Matter type *</label>
    <select id="nc-matter">${types.map(t => `<option>${esc(t)}</option>`).join("")}</select>
    <div id="nc-conflict-box" style="margin:10px 0"></div>
    <div class="row-actions">
      <button class="btn btn-ghost btn-sm" onclick="runModalConflictCheck()">Check conflicts</button>
      <span style="flex:1"></span>
      <button class="btn btn-primary" onclick="createCase()">Create case</button>
      <button class="btn btn-ghost" onclick="closeModal()">Cancel</button>
    </div>`);
}
let _confTimer = null;
function scheduleConflictCheck() {
  clearTimeout(_confTimer);
  _confTimer = setTimeout(runModalConflictCheck, 350);
}
async function runModalConflictCheck() {
  const box = document.getElementById("nc-conflict-box");
  if (!box) return null;
  const cl = (document.getElementById("nc-client")?.value || "").trim();
  const op = (document.getElementById("nc-opposing")?.value || "").trim();
  if (!cl && !op) { box.innerHTML = ""; return null; }
  try {
    const r = await apiJson(`/api/conflicts/check?clientName=${encodeURIComponent(cl)}&opposingParty=${encodeURIComponent(op)}`);
    if (!r.matches || !r.matches.length) {
      box.innerHTML = `<div style="padding:8px 10px;border-radius:6px;background:#E6F4EA;color:#1E6F3D;font-size:13px"><b>Conflict check clear:</b> no adverse party matches found across firm matters.</div>`;
    } else {
      box.innerHTML = r.matches.map(m => `
        <div style="padding:8px 10px;margin-bottom:6px;border-radius:6px;background:${m.severity === "CRITICAL" ? "#FDE7E7" : m.severity === "WARNING" ? "#FFF4E0" : "#E8F0FB"};color:${m.severity === "CRITICAL" ? "#B3261E" : "#24304A"};font-size:13px">
          <b>${esc(m.severity)}:</b> ${esc(m.explanation)}
        </div>`).join("");
    }
    return r;
  } catch (e) { return null; }
}
async function createCase() {
  const title = document.getElementById("nc-title").value.trim();
  if (!title) { toast("Case title is required", "err"); return; }
  const matter = document.getElementById("nc-matter").value;
  const rep = await runModalConflictCheck();
  if (rep && rep.hasCriticalConflict) {
    if (!confirm("CONFLICT OF INTEREST ALERT: One or more parties conflict with an existing firm client or matter. Record override in audit log and proceed?")) return;
  }
  try {
    const c = await apiJson("/api/cases", { method: "POST", json: {
      title,
      clientName: document.getElementById("nc-client").value.trim() || "Client",
      opposingParty: document.getElementById("nc-opposing").value.trim() || "Unknown",
      matterType: matter } });
    closeModal();
    toast("Case created: " + c.reference, "ok");
    show("cases");
  } catch (e) { toast(e.message, "err"); }
}

function fmtMoney(v) {
  return "R " + Number(v || 0).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

async function firmBillingView() {
  const ov = await apiJson("/api/billing/overview");
  const rows = ov.matters || [];
  const payBadge = (st) => {
    const map = { PAID: "c-green", PARTIALLY_PAID: "c-amber", UNPAID: "c-red", UNBILLED: "c-gray" };
    return `<span class="chip ${map[st] || "c-gray"}">${esc((st || "").replace(/_/g, " "))}</span>`;
  };
  return `
  <div class="page-title">${TITLES.billing[0]}</div>
  <div class="stats">
    <div class="stat"><div class="k">BILLED (INCL. 15% VAT)</div><div class="v">${fmtMoney(ov.firmBilledInclVat)}</div><div class="c">${(ov.firmHours || 0).toFixed(1)} billable hours</div></div>
    <div class="stat"><div class="k">SETTLED / COLLECTED</div><div class="v" style="color:var(--green)">${fmtMoney(ov.firmTotalSettled)}</div><div class="c">${ov.paidMatters || 0} settled in full</div></div>
    <div class="stat"><div class="k">OUTSTANDING DEBTORS</div><div class="v" style="color:${ov.firmOutstanding > 0 ? "var(--red)" : "var(--green)"}">${fmtMoney(ov.firmOutstanding)}</div><div class="c">${ov.unpaidOrPartialMatters || 0} open fee notes</div></div>
    <div class="stat"><div class="k">CLIENT TRUST HELD (S86)</div><div class="v">${fmtMoney(ov.firmTrustHeld)}</div><div class="c">${ov.highFeeAlerts ? ov.highFeeAlerts + " fee-ratio alert(s)" : "within quantum thresholds"}</div></div>
  </div>
  <div class="card">
    <div class="row-actions" style="justify-content:space-between;align-items:center">
      <h3 style="margin:0">Matter fee notes, trust credits &amp; claim quantum</h3>
      <button class="btn btn-primary btn-sm" onclick="download('/api/reports/erp-workbook.xlsx','LegalCase-ERP-Workbook.xlsx')">Export ERP workbook (.xlsx)</button>
    </div>
    <table>
      <thead><tr><th>Matter</th><th>Client</th><th>Claim quantum</th><th>Billed (incl. VAT)</th><th>Settled</th><th>Outstanding</th><th>Trust credit</th><th>Fee / claim</th><th>Status</th><th></th></tr></thead>
      <tbody>${rows.map(m => `<tr>
        <td><a href="#" onclick="show('case_detail','${m.caseId}');return false"><b>${esc(m.reference)}</b></a><br><span class="page-sub">${esc(m.title)}</span></td>
        <td>${esc(m.clientName)}</td>
        <td>${m.effectiveClaimAmount > 0 ? fmtMoney(m.effectiveClaimAmount) : "—"} ${m.claimSource === "AI_EXTRACTED" ? '<span class="chip c-blue" title="Extracted from pleading by AI">AI</span>' : ""}</td>
        <td><b>${fmtMoney(m.totalInclVat)}</b></td>
        <td>${fmtMoney(m.totalSettled)}</td>
        <td><b>${fmtMoney(m.outstandingBalance)}</b></td>
        <td style="color:var(--green)"><b>${fmtMoney(m.availableTrustBalance)}</b></td>
        <td>${m.effectiveClaimAmount > 0
          ? `<span class="chip ${m.highFeeRatioWarning ? "c-red" : "c-green"}">${m.feeToClaimPct}%${m.highFeeRatioWarning ? " HIGH" : ""}</span>`
          : "—"}</td>
        <td>${payBadge(m.paymentStatus)}</td>
        <td style="white-space:nowrap">
          ${m.outstandingBalance > 0.01 && m.availableTrustBalance > 0.01
            ? `<button class="btn btn-sm btn-gold" onclick="settleCaseFromTrust('${m.caseId}', false)">Settle from trust</button> ` : ""}
          ${m.outstandingBalance > 0.01
            ? `<button class="btn btn-sm btn-ghost" onclick="recordCasePayment('${m.caseId}', ${m.outstandingBalance}, false)">Record payment</button> ` : ""}
          ${m.totalInclVat > 0
            ? `<button class="btn btn-sm btn-ghost" onclick="download('/api/cases/${m.caseId}/invoice.pdf','Invoice-${esc(m.reference)}.pdf')">Invoice PDF</button>` : ""}
        </td>
      </tr>`).join("") || '<tr><td colspan="10">No matters found.</td></tr>'}
      </tbody>
    </table>
  </div>`;
}

async function settleCaseFromTrust(caseId, fromDetail) {
  try {
    const r = await apiJson(`/api/cases/${caseId}/billing/settle-from-trust`, { method: "POST", json: {} });
    toast(`Settled from Section 86 trust · Status: ${(r.paymentStatus || "").replace(/_/g, " ")}`, "ok");
    show(fromDetail ? "case_detail" : "billing", fromDetail ? caseId : undefined);
  } catch (e) { toast(e.message, "err"); }
}

async function recordCasePayment(caseId, suggestedAmt, fromDetail) {
  const raw = prompt("Direct client payment amount (ZAR):", Number(suggestedAmt || 0).toFixed(2));
  if (!raw) return;
  const amount = parseFloat(raw);
  if (isNaN(amount) || amount <= 0) { toast("Enter a valid positive amount", "err"); return; }
  try {
    const r = await apiJson(`/api/cases/${caseId}/billing/record-payment`, { method: "POST", json: { amount } });
    toast(`Payment recorded · Status: ${(r.paymentStatus || "").replace(/_/g, " ")}`, "ok");
    show(fromDetail ? "case_detail" : "billing", fromDetail ? caseId : undefined);
  } catch (e) { toast(e.message, "err"); }
}

async function billDocReview(docId, caseId) {
  const raw = prompt("Hours spent verifying AI extraction & summary:", "0.5");
  if (!raw) return;
  const hours = parseFloat(raw);
  if (isNaN(hours) || hours <= 0) { toast("Enter valid billable hours", "err"); return; }
  try {
    await apiJson(`/api/documents/${docId}/bill-review`, { method: "POST", json: { hours, hourlyRate: 1850 } });
    toast("AI-assisted document review added to fee note", "ok");
    show("case_detail", caseId);
  } catch (e) { toast(e.message, "err"); }
}

async function caseDetailView(id) {
  const c = await apiJson("/api/cases/" + id);
  const [docs, extractions, summaries, deadlines, messages, billing, conflict, intel, trust] = await Promise.all([
    apiJson(`/api/cases/${id}/documents`), Promise.resolve([]), apiJson(`/api/cases/${id}/summaries`),
    apiJson(`/api/cases/${id}/deadlines`), apiJson(`/api/cases/${id}/messages`),
    apiJson(`/api/cases/${id}/billing`),
    apiJson(`/api/conflicts/check?clientName=${encodeURIComponent(c.clientName || "")}&opposingParty=${encodeURIComponent(c.opposingParty || "")}&excludeCaseId=${encodeURIComponent(id)}`),
    apiJson(`/api/cases/${id}/intelligence`),
    apiJson(`/api/cases/${id}/trust`),
  ]);
  const sum = summaries.find(s => s.status === "APPROVED") || summaries[0];
  const STATUSES = ["OPEN", "IN_LITIGATION", "PENDING_HEARING", "ON_HOLD", "CLOSED"];
  const opts = STATUSES.map(s => `<option ${s === c.status ? "selected" : ""}>${s}</option>`).join("");
  const adverseMatches = (conflict.matches || []).filter(m => m.severity === "CRITICAL" || m.severity === "WARNING");
  return `
  <button class="btn btn-ghost btn-sm" onclick="show('cases')">&#8592; Back to cases</button>
  <div class="card">
    <div class="row-actions">
      <span class="chip c-blue">${esc(c.reference)}</span> ${statusChip(c.status)}
      ${adverseMatches.length
        ? `<span class="chip c-red">CONFLICT ALERT (${adverseMatches.length})</span>`
        : `<span class="chip c-green">CONFLICT CHECK CLEAR</span>`}
      <span style="flex:1"></span>
      <select id="status-select" style="width:170px">${opts}</select>
      <button class="btn btn-sm" onclick="changeStatus('${id}')">Apply status</button>
      <button class="btn btn-sm btn-gold" onclick="download('/api/cases/${id}/case-file.pdf','CaseFile-${esc(c.reference)}.pdf')" title="One-page PDF with summary, deadlines and billing">Case file (PDF)</button>
      <button class="btn btn-sm btn-ghost" onclick="editCase('${id}')">Edit case</button>
      <button class="btn btn-sm btn-danger" onclick="deleteCase('${id}')">Delete case</button>
    </div>
    ${adverseMatches.map(m => `<div style="margin-top:8px;padding:8px 10px;border-radius:6px;background:#FDE7E7;color:#B3261E;font-size:13px"><b>${esc(m.severity)}:</b> ${esc(m.explanation)}</div>`).join("")}
    <h2>${esc(c.title)}</h2>
    <div class="kv" style="margin-top:8px">
      <span class="k">CLIENT</span><span>${esc(c.clientName)}</span>
      <span class="k">OPPOSING PARTY</span><span>${esc(c.opposingParty)}</span>
      <span class="k">COURT</span><span>${esc(c.court)}</span>
      <span class="k">MATTER TYPE</span><span>${esc(c.matterType)}</span>
      <span class="k">OPENED</span><span>${fmtDate(c.openedDate)}</span>
      <span class="k">AMOUNT</span><span>R ${c.amountInvolved ? c.amountInvolved.toLocaleString() : "—"}</span>
      <span class="k">DESCRIPTION</span><span>${esc(c.description)}</span>
    </div>
    <div class="row-actions" style="margin-top:12px;padding-top:10px;border-top:1px solid #E2E7F0">
      <span style="font-size:13px;font-weight:600;color:#475569">Draft pleadings (.docx):</span>
      <button class="btn btn-sm btn-ghost" onclick="download('/api/cases/${id}/templates/letter_of_demand','Letter-of-Demand-${esc(c.reference)}.docx')">Letter of Demand (.docx)</button>
      <button class="btn btn-sm btn-ghost" onclick="download('/api/cases/${id}/templates/notice_to_defend','Notice-to-Defend-${esc(c.reference)}.docx')">Notice to Defend (.docx)</button>
      <button class="btn btn-sm btn-ghost" onclick="download('/api/cases/${id}/templates/discovery_affidavit','Discovery-Schedule-${esc(c.reference)}.docx')">Discovery Schedule (.docx)</button>
      <span style="flex:1"></span>
      <button class="btn btn-sm btn-primary" onclick="download('/api/cases/${id}/court-bundle.pdf','CourtBundle-${esc(c.reference)}.pdf')">Compile court bundle (PDF)</button>
    </div>
  </div>

  <div class="card dropzone" data-dropzone="${id}">
    <h3>Documents</h3>
    <p class="hint">Drag a PDF or Word file onto this card, or choose a file below.</p>
    <div class="row-actions">
      <input type="file" id="upload-input" accept=".pdf,.doc,.docx">
      <button class="btn btn-gold" onclick="uploadDoc('${id}')">Upload legal document</button>
      <label style="margin:0"><input type="checkbox" id="upload-consent" checked style="width:auto"> Client consent (POPIA)</label>
    </div>
    <table><thead><tr><th>File</th><th>Type</th><th>Uploaded</th><th>AI status</th><th>Actions</th></tr></thead>
    <tbody>${docs.map(d => `
      <tr><td>${esc(d.fileName)}</td><td>${esc(d.docType)}</td><td>${fmtDate(d.uploadedAt)}</td>
      <td>${d.processingStatus === "PROCESSED" ? '<span class="chip c-green">PROCESSED</span>'
           : d.processingStatus === "FAILED" ? '<span class="chip c-red">FAILED</span>'
           : d.processingStatus === "REJECTED" ? `<span class="chip c-red" title="${esc(d.rejectionReason)}">BLOCKED</span>`
           : '<span class="chip c-amber">PROCESSING</span>'}</td>
      <td style="white-space:nowrap">
        ${d.processingStatus === "FAILED" ? `<button class="btn btn-sm btn-ghost" onclick="retryDoc('${d.id}','${id}')">Retry AI</button> `
           : d.processingStatus === "PROCESSED" ? `<button class="btn btn-sm btn-ghost" onclick="viewExtractions('${d.id}','${id}')">AI</button> <button class="btn btn-sm btn-ghost" onclick="readDoc('${d.id}')">Read full</button> <button class="btn btn-sm btn-ghost" onclick="billDocReview('${d.id}','${id}')">Bill review</button> `
           : d.processingStatus === "REJECTED" ? `<button class="btn btn-sm btn-ghost" onclick="viewExtractions('${d.id}','${id}')">Review AI</button> ` : ""}
        <button class="btn btn-sm btn-danger" onclick="deleteDoc('${d.id}','${id}')">Delete</button>
      </td></tr>${d.processingStatus === "REJECTED" ? `<tr><td colspan="5" style="background:#FDE7E7;color:#B3261E"><b>Blocked:</b> ${esc(d.rejectionReason)}</td></tr>` : ""}`).join("") || '<tr><td colspan="5">No documents yet.</td></tr>'}
    </tbody></table>
    <div id="extract-panel"></div>
  </div>

  <div class="card">
    <h3>AI summary</h3>
    ${sum ? `
      <div class="row-actions"><span class="chip ${sum.status === "APPROVED" ? "c-green" : "c-amber"}">${sum.status}</span>
        <span class="chip c-blue">v${sum.version}</span></div>
      <label>1. Facts</label><textarea id="sum-p1" rows="3">${esc(sum.paragraph1)}</textarea>
      <label>2. Legal issues</label><textarea id="sum-p2" rows="3">${esc(sum.paragraph2)}</textarea>
      <label>3. Current status</label><textarea id="sum-p3" rows="3">${esc(sum.paragraph3)}</textarea>
      <div class="row-actions">
        <button class="btn btn-ghost" onclick="saveSummary('${sum.id}')">Save draft</button>
        ${sum.status !== "APPROVED" ? `<button class="btn btn-gold" onclick="approveSummary('${sum.id}','${id}')">Approve & notify client</button>` : ""}
      </div>` : `<p class="page-sub">Upload a document to generate a summary.</p>`}
  </div>

  <div class="card">
    <h3>Deadlines &amp; Google Calendar</h3>
    <div class="row-actions">
      <button class="btn btn-ghost btn-sm" onclick="addDeadline('${id}')">+ Add deadline</button>
      <button class="btn btn-ghost btn-sm" onclick="caseSync('${id}', true)">Sync all</button>
      <button class="btn btn-ghost btn-sm" onclick="caseSync('${id}', false)">Unsync all</button>
      <button class="btn btn-gold btn-sm" onclick="download('/api/cases/${id}/calendar.ics','case-${id}-calendar.ics')">Case calendar (.ics)</button>
    </div>
    <table><thead><tr><th>Deadline</th><th>Date</th><th>Countdown</th><th>Type</th><th>Reminder</th><th>Calendar</th><th></th></tr></thead>
    <tbody>${deadlines.map(d => {
      const dl = daysLeft(d.date);
      return `<tr><td>${esc(d.title)}</td><td>${fmtDate(d.date)}</td>
        <td><span class="chip ${dl < 0 ? "c-red" : dl <= 7 ? "c-amber" : "c-blue"}">${dl < 0 ? dl + "d overdue" : dl + "d"}</span></td>
        <td>${esc(d.type)}</td><td>${d.reminderDays} days before</td>
        <td>${d.synced ? '<span class="chip c-green">SYNCED</span>' : '<span class="chip c-gray">PENDING</span>'}</td>
        <td><button class="btn btn-sm btn-ghost" onclick="toggleDeadline('${d.id}','${id}',${d.synced})">${d.synced ? "Unsync" : "Sync now"}</button></td></tr>`;}).join("") || '<tr><td colspan="7">No deadlines.</td></tr>'}
    </tbody></table>
  </div>

  <div class="card">
    <h3>Cross-document consistency &amp; master case timeline</h3>
    <div style="margin-bottom:10px">
      ${intel.consistent
        ? `<span class="chip c-green">CONSISTENT ACROSS ${intel.processedDocumentsAnalysed} PROCESSED DOCUMENT(S)</span>`
        : `<span class="chip c-red">${intel.contradictions.length} CONTRADICTION(S) DETECTED ACROSS DOCUMENTS</span>`}
    </div>
    ${(intel.contradictions || []).map(ct => `
      <div style="padding:8px 10px;margin-bottom:8px;border-radius:6px;background:#FFF4E0;color:#7A4B00;font-size:13px">
        <b>Conflicting ${esc(ct.fieldName)}:</b> ${esc(ct.explanation)}
      </div>`).join("")}
    <table><thead><tr><th>Date</th><th>Milestone / Event</th><th>Category</th><th>Source</th></tr></thead>
    <tbody>${(intel.timeline || []).map(ev => `<tr>
      <td><b>${fmtDate(ev.date)}</b></td>
      <td>${esc(ev.title)}${ev.quote ? `<div class="page-sub" style="margin:0">"${esc(ev.quote)}"</div>` : ""}</td>
      <td><span class="chip ${ev.category === "DEADLINE" ? "c-blue" : "c-green"}">${esc(ev.category.replace(/_/g," "))}</span></td>
      <td>${esc(ev.source)}${ev.pageNumber ? ` (p. ${ev.pageNumber})` : ""}</td>
    </tr>`).join("") || '<tr><td colspan="4">No timeline events yet.</td></tr>'}
    </tbody></table>
  </div>

  <div class="card">
    <h3>Time &amp; billing</h3>
    <div class="stats" style="margin:8px 0 12px 0">
      <div class="stat"><div class="k">BILLABLE HOURS</div><div class="v">${(billing.totalHours || 0).toFixed(1)} h</div></div>
      <div class="stat"><div class="k">FEES &amp; DISBURSEMENTS</div><div class="v">R ${(billing.subtotalExVat || 0).toLocaleString()}</div></div>
      <div class="stat"><div class="k">VAT (15%)</div><div class="v">R ${(billing.vatAmount || 0).toLocaleString()}</div></div>
      <div class="stat"><div class="k">TOTAL DUE</div><div class="v">R ${(billing.totalInclVat || 0).toLocaleString()}</div></div>
    </div>
    <div class="row-actions">
      <button class="btn btn-primary btn-sm" onclick="openBillingModal('${id}')">+ Log time / disbursement</button>
      ${(billing.totalInclVat || 0) > 0 && (trust.trustBalance || 0) > 0 ? `<button class="btn btn-ghost btn-sm" onclick="settleCaseFromTrust('${id}', true)">Settle from trust</button>` : ""}
      <button class="btn btn-gold btn-sm" onclick="download('/api/cases/${id}/invoice.pdf','Invoice-${esc(c.reference)}.pdf')">Download fee note (PDF)</button>
    </div>
    <table><thead><tr><th>Date</th><th>Type</th><th>Description</th><th>Units</th><th>Amount (ZAR)</th><th></th></tr></thead>
    <tbody>${(billing.entries || []).map(b => `<tr>
      <td>${fmtDate(b.entryDate)}</td>
      <td><span class="chip ${b.entryType === "TIME" ? "c-blue" : "c-amber"}">${esc(b.entryType)}</span></td>
      <td>${esc(b.description)}</td>
      <td>${b.entryType === "TIME" ? `${b.hours} h @ R ${b.hourlyRate}` : "Disbursement"}</td>
      <td><b>R ${Number(b.amount).toLocaleString(undefined,{minimumFractionDigits:2,maximumFractionDigits:2})}</b></td>
      <td><button class="btn btn-sm btn-danger" onclick="deleteBillingEntry('${b.id}','${id}')">Delete</button></td>
    </tr>`).join("") || '<tr><td colspan="6">No billable items recorded yet.</td></tr>'}
    </tbody></table>
  </div>

  <div class="card">
    <h3>Section 86 client trust account ledger</h3>
    <div class="stats" style="margin:8px 0 12px 0">
      <div class="stat"><div class="k">TRUST DEPOSITS</div><div class="v">R ${(trust.totalDeposits || 0).toLocaleString()}</div></div>
      <div class="stat"><div class="k">TRANSFERS &amp; REFUNDS</div><div class="v">R ${(trust.totalTransfersOut || 0).toLocaleString()}</div></div>
      <div class="stat"><div class="k">AVAILABLE TRUST CREDIT</div><div class="v" style="color:var(--green)">R ${(trust.trustBalance || 0).toLocaleString()}</div></div>
    </div>
    <div class="row-actions">
      <button class="btn btn-primary btn-sm" onclick="openTrustModal('${id}', ${trust.trustBalance || 0})">+ Record trust deposit / transfer</button>
      <button class="btn btn-gold btn-sm" onclick="download('/api/cases/${id}/trust-statement.pdf','TrustStatement-${esc(c.reference)}.pdf')">Download trust statement (PDF)</button>
    </div>
    <table><thead><tr><th>Date</th><th>Reference</th><th>Type</th><th>Description</th><th>Amount</th><th>Trust Balance</th></tr></thead>
    <tbody>${(trust.entries || []).map(t => `<tr>
      <td>${fmtDate(t.txDate)}</td><td class="mono">${esc(t.reference)}</td>
      <td><span class="chip ${t.txType === "DEPOSIT" ? "c-green" : "c-amber"}">${esc(t.txType.replace(/_/g," "))}</span></td>
      <td>${esc(t.description)}</td>
      <td>${t.txType === "DEPOSIT" ? "+" : "-"}R ${Number(t.amount).toLocaleString(undefined,{minimumFractionDigits:2,maximumFractionDigits:2})}</td>
      <td><b>R ${Number(t.balanceAfter).toLocaleString(undefined,{minimumFractionDigits:2,maximumFractionDigits:2})}</b></td>
    </tr>`).join("") || '<tr><td colspan="6">No trust transactions recorded yet.</td></tr>'}
    </tbody></table>
  </div>

  <div class="card">
    <h3>Message the client</h3>
    <div class="chat-thread">${messages.map(m => `
      <div class="bubble ${m.fromLawyer ? "mine" : "theirs"}">
        <div class="meta">${esc(m.fromName)} · ${fmtDate(m.sentAt)} ${!m.fromLawyer && !m.readByLawyer ? '<span class="chip c-red">NEW</span>' : ""}</div>
        <b>${esc(m.subject)}</b><br>${esc(m.body)}</div>`).join("") || '<div class="page-sub">No messages yet.</div>'}
    </div>
    <input id="lawyer-msg-subject" placeholder="Subject (optional)" style="margin-top:10px">
    <textarea id="lawyer-msg-body" rows="2" placeholder="Type your message..."></textarea>
    <div class="row-actions"><button class="btn btn-gold" onclick="sendLawyerMsg('${id}')">Send to client</button></div>
  </div>`;
}

async function viewExtractions(docId, caseId) {
  const ex = await apiJson(`/api/documents/${docId}/extractions`);
  const el = document.getElementById("extract-panel");
  el.innerHTML = `<h4 style="margin-top:14px">AI extraction</h4>
    <table><thead><tr><th>Field</th><th>Value</th><th>Confidence</th><th>Source span</th><th>Page</th></tr></thead>
    <tbody>${ex.map((e, idx) => `<tr style="cursor:pointer" onclick="readDoc('${docId}', ${idx})" title="Click to view highlighted in full document"><td><b>${esc(e.fieldName)}</b></td><td>${esc(e.fieldValue)}</td>
      <td><span class="chip ${e.confidence >= .9 ? "c-green" : e.confidence >= .75 ? "c-amber" : "c-red"}">${Math.round(e.confidence * 100)}%</span></td>
      <td class="mono">${esc(e.spanText)}</td><td>${e.pageNumber}</td></tr>`).join("") || '<tr><td colspan="5">No extractions.</td></tr>'}
    </tbody></table>`;
}
async function uploadDoc(caseId) {
  const input = document.getElementById("upload-input");
  if (!input.files[0]) { toast("Choose a file first", "err"); return; }
  const consent = document.getElementById("upload-consent").checked;
  if (!consent) { toast("Client consent is required (POPIA)", "err"); return; }
  const fd = new FormData();
  fd.append("file", input.files[0]);
  fd.append("consent", String(consent));
  try {
    toast("Uploading...");
    const doc = await upload(`/api/cases/${caseId}/documents`, fd);
    toast("Uploaded - AI is processing now", "ok");
    await waitForProcessed(doc.id, caseId);
  } catch (e) { toast(e.message, "err"); }
}

async function waitForProcessed(docId, caseId) {
  for (let i = 0; i < 90; i++) {
    await new Promise(r => setTimeout(r, 1000));
    try {
      const d = await apiJson(`/api/documents/${docId}`);
      if (d.processingStatus === "PROCESSED") {
        show("case_detail", caseId);
        setTimeout(() => viewExtractions(docId, caseId), 600);
        toast("AI extraction complete - see the panel below", "ok");
        return;
      }
      if (d.processingStatus === "REJECTED") {
        show("case_detail", caseId);
        setTimeout(() => viewExtractions(docId, caseId), 600);
        toast("Document BLOCKED: " + (d.rejectionReason || "does not match the case"), "err");
        return;
      }
      if (d.processingStatus === "FAILED") {
        show("case_detail", caseId);
        toast("AI processing failed - use Retry AI", "err");
        return;
      }
    } catch (e) {}
  }
}
async function deleteDoc(docId, caseId) {
  if (!confirm("Delete this document? The file and its AI extraction will be removed.")) return;
  try { await api(`/api/documents/${docId}`, { method: "DELETE" }); toast("Document deleted", "ok"); show(caseId ? "case_detail" : "documents", caseId); }
  catch (e) { toast(e.message, "err"); }
}
async function readDoc(docId, focusIdx) {
  try {
    const c = await apiJson(`/api/documents/${docId}/content`);
    const ex = c.extractions || [];
    let htmlText = esc(c.fullText || "");
    // Highlight extracted spans directly inside the escaped document text
    ex.forEach((e, idx) => {
      const span = (e.spanText || "").trim();
      if (span.length >= 3) {
        const escSpan = esc(span);
        const bg = e.confidence >= 0.9 ? "#FEF08A" : "#FDE68A";
        htmlText = htmlText.replace(escSpan,
          `<mark id="doc-hl-${idx}" style="background:${bg};padding:1px 4px;border-radius:3px;border-bottom:2px solid #CA8A04" title="${esc(e.fieldName)}: ${Math.round(e.confidence * 100)}% confidence">${escSpan}</mark>`);
      }
    });
    const chips = ex.map((e, idx) =>
      `<button class="btn btn-sm btn-ghost" style="margin:2px" onclick="focusHighlight(${idx})">${esc(e.fieldName)}: <b>${esc(e.fieldValue)}</b> (${Math.round(e.confidence * 100)}%)</button>`
    ).join("");
    showModal(`<h3>${esc(c.fileName)} <span class="chip c-blue">${esc(c.docType)}</span></h3>
      ${chips ? `<div style="margin-bottom:10px;padding:6px;background:#F8FAFC;border-radius:6px">${chips}</div>` : ""}
      <pre class="doc-text" id="doc-reader-pre">${htmlText}</pre>`);
    if (typeof focusIdx === "number") setTimeout(() => focusHighlight(focusIdx), 120);
  } catch (e) { toast(e.message, "err"); }
}
function focusHighlight(idx) {
  const el = document.getElementById("doc-hl-" + idx);
  if (!el) { toast("Source span located on summary/header line", "ok"); return; }
  el.scrollIntoView({ behavior: "smooth", block: "center" });
  el.style.outline = "3px solid #1D4ED8";
  setTimeout(() => { el.style.outline = "none"; }, 1800);
}
async function retryDoc(docId, caseId) {
  try { await apiJson(`/api/documents/${docId}/retry`, { method: "POST", json: {} }); toast("Retry queued", "ok"); show("case_detail", caseId); }
  catch (e) { toast(e.message, "err"); }
}
async function saveSummary(id) {
  try {
    await apiJson(`/api/summaries/${id}`, { method: "PUT", json: {
      paragraph1: document.getElementById("sum-p1").value,
      paragraph2: document.getElementById("sum-p2").value,
      paragraph3: document.getElementById("sum-p3").value } });
    toast("Draft saved & correction recorded for model training", "ok");
  } catch (e) { toast(e.message, "err"); }
}

function openBillingModal(caseId) {
  showModal(`
    <h3>Record time or disbursement</h3>
    <label>Entry type</label>
    <select id="bl-type" onchange="toggleBillingFields()">
      <option value="TIME">Time attendance (hours x rate)</option>
      <option value="DISBURSEMENT">Disbursement (fixed expense)</option>
    </select>
    <label>Description *</label><input id="bl-desc" placeholder="e.g. Drafting plea / Sheriff service fee">
    <div id="bl-time-fields">
      <label>Hours</label><input id="bl-hours" type="number" step="0.25" value="1.5">
      <label>Hourly rate (ZAR)</label><input id="bl-rate" type="number" step="50" value="1850">
    </div>
    <div id="bl-disb-fields" style="display:none">
      <label>Disbursement amount (ZAR)</label><input id="bl-amount" type="number" step="10" value="500">
    </div>
    <div class="row-actions">
      <button class="btn btn-primary" onclick="submitBillingEntry('${caseId}')">Save entry</button>
      <button class="btn btn-ghost" onclick="closeModal()">Cancel</button>
    </div>`);
}
function toggleBillingFields() {
  const t = document.getElementById("bl-type").value;
  document.getElementById("bl-time-fields").style.display = t === "TIME" ? "block" : "none";
  document.getElementById("bl-disb-fields").style.display = t === "DISBURSEMENT" ? "block" : "none";
}
async function submitBillingEntry(caseId) {
  const entryType = document.getElementById("bl-type").value;
  const description = document.getElementById("bl-desc").value.trim();
  if (!description) { toast("Description is required", "err"); return; }
  const hours = parseFloat(document.getElementById("bl-hours").value || "1");
  const hourlyRate = parseFloat(document.getElementById("bl-rate").value || "1850");
  const amount = parseFloat(document.getElementById("bl-amount").value || "0");
  try {
    await apiJson(`/api/cases/${caseId}/billing`, { method: "POST", json: { entryType, description, hours, hourlyRate, amount } });
    closeModal();
    toast("Billing item saved", "ok");
    show("case_detail", caseId);
  } catch (e) { toast(e.message, "err"); }
}
async function deleteBillingEntry(entryId, caseId) {
  try { await apiJson(`/api/billing/${entryId}`, { method: "DELETE" }); toast("Billing item removed", "ok"); show("case_detail", caseId); }
  catch (e) { toast(e.message, "err"); }
}
async function approveSummary(id, caseId) {
  try { await apiJson(`/api/summaries/${id}/approve`, { method: "POST", json: {} }); toast("Summary approved - client notified", "ok"); show("case_detail", caseId); }
  catch (e) { toast(e.message, "err"); }
}
async function changeStatus(caseId) {
  const status = document.getElementById("status-select").value;
  try { await apiJson(`/api/cases/${caseId}/status`, { method: "PATCH", json: { status } }); toast("Status changed - client notified", "ok"); show("case_detail", caseId); }
  catch (e) { toast(e.message, "err"); }
}
async function editCase(caseId) {
  const title = prompt("Case title", "") ; if (!title) return;
  const opposing = prompt("Opposing party", "Unknown") || "Unknown";
  const desc = prompt("Description", "") || "";
  try { await apiJson(`/api/cases/${caseId}`, { method: "PATCH", json: { title, opposingParty: opposing, description: desc } }); toast("Case updated - client notified", "ok"); show("case_detail", caseId); }
  catch (e) { toast(e.message, "err"); }
}
async function deleteCase(caseId) {
  if (!confirm("Delete this case permanently? This cannot be undone.")) return;
  try { await api(`/api/cases/${caseId}`, { method: "DELETE" }); toast("Case deleted", "ok"); show("cases"); }
  catch (e) { toast(e.message, "err"); }
}
async function addDeadline(caseId) {
  const title = prompt("Deadline title:"); if (!title) return;
  const date = prompt("Date (yyyy-mm-dd):", new Date(Date.now() + 7 * 864e5).toISOString().slice(0, 10)); if (!date) return;
  const reminder = prompt("Reminder days before (1/3/7/14):", "7");
  try { await apiJson(`/api/cases/${caseId}/deadlines`, { method: "POST", json: { title, date, type: "OTHER", reminderDays: +reminder } }); toast("Deadline added", "ok"); show("case_detail", caseId); }
  catch (e) { toast(e.message, "err"); }
}
async function toggleDeadline(id, caseId, synced) {
  try {
    await apiJson("/api/calendar/" + (synced ? "unsync" : "sync"), { method: "POST", json: { deadlineIds: [id] } });
    toast(synced ? "Removed from calendar" : "Synced to calendar", "ok");
    show("case_detail", caseId);
  } catch (e) { toast(e.message, "err"); }
}
async function caseSync(caseId, sync) {
  try { const r = await apiJson(`/api/cases/${caseId}/calendar-${sync ? "sync" : "unsync"}`, { method: "POST", json: {} }); toast(r.message, "ok"); show("case_detail", caseId); }
  catch (e) { toast(e.message, "err"); }
}
async function sendLawyerMsg(caseId) {
  const subject = document.getElementById("lawyer-msg-subject").value;
  const body = document.getElementById("lawyer-msg-body").value;
  if (!body.trim()) return;
  try { await apiJson(`/api/cases/${caseId}/messages/lawyer`, { method: "POST", json: { subject, body } }); toast("Message sent", "ok"); show("case_detail", caseId); }
  catch (e) { toast(e.message, "err"); }
}

async function precedentsView() {
  return `
  <div class="page-title">${TITLES.precedents[0]}</div>
  <div class="card">
    <div class="row-actions" style="margin:0">
      <input id="prec-q" placeholder="Search inside pleadings (e.g. collision, National Credit Act, tibia, R1,250,000)..." onkeydown="if(event.key==='Enter')runPrecedentSearch()" style="max-width:520px">
      <button class="btn btn-primary" onclick="runPrecedentSearch()">Search documents</button>
    </div>
    <div id="prec-results" style="margin-top:14px">
    </div>
  </div>`;
}
async function runPrecedentSearch() {
  const q = (document.getElementById("prec-q")?.value || "").trim();
  const box = document.getElementById("prec-results");
  if (q.length < 2) { toast("Enter at least 2 characters", "err"); return; }
  try {
    const hits = await apiJson(`/api/search/precedents?q=${encodeURIComponent(q)}`);
    if (!hits.length) {
      box.innerHTML = `<p class="page-sub">No processed documents matched "<b>${esc(q)}</b>".</p>`;
      return;
    }
    const re = new RegExp("(" + q.replace(/[.*+?^${}()|[\]\\]/g, "\\$&") + ")", "ig");
    box.innerHTML = `<table><thead><tr><th>Document</th><th>Matter</th><th>Practice area / Court</th><th>Matches</th><th>Contextual snippet</th><th></th></tr></thead>
      <tbody>${hits.map(h => `<tr>
        <td><b>${esc(h.fileName)}</b> <span class="chip c-blue">${esc(h.docType)}</span></td>
        <td><a href="#" onclick="show('case_detail','${h.caseId}');return false"><b>${esc(h.caseReference)}</b></a><br><span class="page-sub">${esc(h.caseTitle)}</span></td>
        <td>${esc(h.matterType)}<br><span class="page-sub">${esc(h.court)}</span></td>
        <td><span class="chip c-green">${h.matchCount}x</span></td>
        <td style="font-size:12.5px">${esc(h.snippet).replace(re, '<mark style="background:#FEF08A;padding:0 3px;border-radius:3px">$1</mark>')}</td>
        <td style="white-space:nowrap">
          <button class="btn btn-sm btn-ghost" onclick="readDoc('${h.documentId}')">Read</button>
          <button class="btn btn-sm btn-primary" onclick="show('case_detail','${h.caseId}')">Open case</button>
        </td>
      </tr>`).join("")}</tbody></table>`;
  } catch (e) { toast(e.message, "err"); }
}


async function calendarView(param) {
  let ds = await apiJson("/api/deadlines");
  let month = (param && param.month) || null;
  const all = ds.length;
  if (month) ds = ds.filter(d => (d.date || "").slice(0, 7) === month);
  const monthChip = month
    ? `<span class="chip c-blue" style="margin-left:8px">Showing ${esc(monthLabel(month))} (${ds.length} of ${all})</span>
       <button class="btn btn-sm btn-ghost" onclick="show('calendar')">Clear filter</button>`
    : "";
  return `<div class="page-title">${TITLES.calendar[0]}</div>
  <div class="card"><div class="row-actions">
    <button class="btn btn-primary" onclick="calendarSyncAll(true)">Sync all pending</button>
    <button class="btn btn-ghost" onclick="calendarSyncAll(false)">Unsync all</button>
    <button class="btn btn-gold" onclick="download('/api/calendar/export.ics','legal-case-erp-calendar.ics')">Download calendar (.ics)</button>
  </div>
  ${monthChip ? `<div style="margin:8px 0">${monthChip}</div>` : ""}
  <table><thead><tr><th>Case</th><th>Deadline</th><th>Date</th><th>Countdown</th><th>Reminder</th><th>Calendar</th><th></th></tr></thead>
  <tbody>${ds.map(d => { const dl = daysLeft(d.date);
    return `<tr><td>${esc(d.caseReference)}</td><td>${esc(d.title)}</td><td>${fmtDate(d.date)}</td>
      <td><span class="chip ${dl < 0 ? "c-red" : dl <= 7 ? "c-amber" : "c-blue"}">${dl < 0 ? dl + "d overdue" : dl + "d"}</span></td>
      <td>${d.reminderDays}d</td>
      <td>${d.synced ? '<span class="chip c-green">SYNCED</span>' : '<span class="chip c-gray">PENDING</span>'}</td>
      <td><button class="btn btn-sm btn-ghost" onclick="calendarToggle('${d.id}',${d.synced})">${d.synced ? "Unsync" : "Sync"}</button></td></tr>`;}).join("") || '<tr><td colspan="7">No deadlines.</td></tr>'}
  </tbody></table></div>`;
}
async function calendarToggle(id, synced) {
  try { await apiJson("/api/calendar/" + (synced ? "unsync" : "sync"), { method: "POST", json: { deadlineIds: [id] } }); toast("Done", "ok"); show("calendar"); }
  catch (e) { toast(e.message, "err"); }
}
async function calendarSyncAll(sync) {
  try { const r = await apiJson("/api/calendar/" + (sync ? "sync-all" : "unsync"), { method: "POST", json: sync ? {} : { deadlineIds: [] } });
    if (!sync) { // unsync-all: collect synced ids client-side
      const ds = await apiJson("/api/deadlines");
      const ids = ds.filter(d => d.synced).map(d => d.id);
      await apiJson("/api/calendar/unsync", { method: "POST", json: { deadlineIds: ids } });
      toast("Unsynced " + ids.length, "ok");
    } else toast(r.message, "ok");
    show("calendar");
  } catch (e) { toast(e.message, "err"); }
}

async function activityView() {
  const logs = await apiJson("/api/activity?limit=200");
  return `<div class="page-title">${TITLES.activity[0]}</div>
  <div class="card"><div class="row-actions"><button class="btn btn-primary" onclick="download('/api/activity.csv','activity-audit.csv')">Download audit history (CSV)</button></div>
  <table><thead><tr><th>Timestamp</th><th>User</th><th>Action</th><th>Details</th><th>Level</th></tr></thead>
  <tbody>${logs.map(a => `<tr><td>${esc(fmtDate(a.timestamp))}</td><td>${esc(a.userName)}</td><td><b>${esc(a.action)}</b></td>
    <td>${esc(a.details)}</td><td><span class="chip ${a.level === "SUCCESS" ? "c-green" : a.level === "WARNING" ? "c-amber" : "c-blue"}">${esc(a.level)}</span></td></tr>`).join("") || '<tr><td colspan="5">No activity yet.</td></tr>'}
  </tbody></table></div>`;
}

async function ratingsView() {
  const ratings = await apiJson("/api/ratings");
  const avg = ratings.length ? (ratings.reduce((a, r) => a + r.score, 0) / ratings.length).toFixed(1) : "—";
  return `<div class="page-title">${TITLES.ratings[0]}</div>
  <div class="stats"><div class="stat"><div class="k">AVERAGE RATING</div><div class="v">${avg} / 5</div></div>
    <div class="stat"><div class="k">TOTAL RATINGS</div><div class="v">${ratings.length}</div></div></div>
  <div class="card"><table><thead><tr><th>Case</th><th>Score</th><th>Comment</th><th>Date</th></tr></thead>
  <tbody>${ratings.map(r => `<tr><td>${esc(r.caseId)}</td><td class="stars">${"★".repeat(r.score)}${"☆".repeat(5 - r.score)}</td><td>${esc(r.comment)}</td><td>${fmtDate(r.createdAt)}</td></tr>`).join("") || '<tr><td colspan="4">No ratings yet.</td></tr>'}
  </tbody></table></div>`;
}

/* ================= CLIENT ================= */
async function clientHomeView() {
  const cases = await apiJson("/api/client/cases");
  if (!cases.length) return `<div class="page-title">My case</div><p class="page-sub">No case linked to your account yet.</p>`;
  const c = cases[0];
  const view = await apiJson(`/api/cases/${c.id}/client-view`);
  const s = view.summary && view.summary.id ? view.summary : null;
  const trust = view.trust || { trustBalance: 0 };
  return `<div class="page-title">${esc(c.title)}</div>
  <div class="card"><h3>Your case update</h3>
    ${s ? `<p>${esc(s.paragraph1)}</p><p style="margin-top:8px">${esc(s.paragraph2)}</p><p style="margin-top:8px">${esc(s.paragraph3)}</p>
      <p class="page-sub" style="margin-top:8px">Approved by your attorney · v${s.version}</p>`
        : '<p class="page-sub">Your attorney is still preparing your update. It will appear here once approved.</p>'}
  </div>
  <div class="card"><h3>Upcoming deadlines</h3>
    ${(view.deadlines || []).map(d => `<div class="row-actions" style="justify-content:space-between;margin:4px 0">
      <span>${esc(d.title)} — ${fmtDate(d.date)}</span><span class="chip ${daysLeft(d.date) <= 7 ? "c-amber" : "c-blue"}">${daysLeft(d.date)}d</span></div>`).join("") || '<p class="page-sub">No upcoming deadlines.</p>'}
  </div>
  <div class="card"><h3>Latest message</h3>
    ${(view.messages || []).slice(-1).map(m => `<p><b>${esc(m.subject)}</b> — ${esc(m.body)}</p>`).join("") || '<p class="page-sub">No messages yet.</p>'}
  </div>
  <div class="card"><h3>Statement, trust account &amp; fee note</h3>
    <div class="row-actions" style="justify-content:space-between;align-items:center">
      <span>Fee note (incl. 15% VAT): <b>R ${((view.billing && view.billing.totalInclVat) || 0).toLocaleString(undefined,{minimumFractionDigits:2,maximumFractionDigits:2})}</b> &nbsp;·&nbsp; Available trust credit: <b style="color:var(--green)">R ${(trust.trustBalance || 0).toLocaleString(undefined,{minimumFractionDigits:2,maximumFractionDigits:2})}</b></span>
      <div>
        <button class="btn btn-ghost btn-sm" onclick="download('/api/cases/${c.id}/trust-statement.pdf','TrustStatement-${esc(c.reference)}.pdf')">Trust statement (PDF)</button>
        <button class="btn btn-gold btn-sm" onclick="download('/api/cases/${c.id}/invoice.pdf','Invoice-${esc(c.reference)}.pdf')">Download invoice (PDF)</button>
      </div>
    </div>
  </div>`;
}

async function clientDocumentsView() {
  const docs = await apiJson("/api/documents");
  return `<div class="page-title">${TITLES.documents[0]}</div><div class="page-sub">${TITLES.documents[1]}</div>
  <div class="card">
    <div class="row-actions">
      <input type="file" id="share-input" accept=".pdf,.doc,.docx">
      <button class="btn btn-gold" onclick="shareDoc()">+ Share a document with your lawyer</button>
    </div>
    <table><thead><tr><th>File</th><th>Case</th><th>Shared on</th><th>Size</th><th></th></tr></thead>
    <tbody>${docs.map(d => `<tr><td>${esc(d.fileName)}</td><td>${esc(d.caseId)}</td><td>${fmtDate(d.uploadedAt)}</td><td>${d.sizeKb} KB</td>
      <td style="white-space:nowrap"><button class="btn btn-sm btn-gold" onclick="downloadDoc('${d.id}','${esc(d.fileName)}')">Download</button>
      <button class="btn btn-sm btn-danger" onclick="deleteDoc('${d.id}','')">Delete</button></td></tr>`).join("") || '<tr><td colspan="5">No documents shared yet.</td></tr>'}
    </tbody></table></div>`;
}
async function downloadDoc(id, filename) { try { await download(`/api/documents/${id}/download`, filename); toast("Download started", "ok"); } catch (e) { toast(e.message, "err"); } }
async function shareDoc() {
  const input = document.getElementById("share-input");
  if (!input.files[0]) { toast("Choose a file first", "err"); return; }
  if (!confirm("I consent to this document being shared and processed (POPIA).")) return;
  const cases = await apiJson("/api/client/cases");
  const fd = new FormData();
  fd.append("file", input.files[0]);
  fd.append("consent", "true");
  try { await upload(`/api/cases/${cases[0].id}/documents`, fd); toast("Document sent to your lawyer", "ok"); show("documents"); }
  catch (e) { toast(e.message, "err"); }
}

async function clientMessagesView() {
  const cases = await apiJson("/api/client/cases");
  const c = cases[0];
  const msgs = await apiJson(`/api/cases/${c.id}/messages`);
  // Opening the thread marks the lawyer's messages as read - the unread bell
  // (and /api/me/unread count) updates immediately.
  if (msgs.some(m => m.fromLawyer && !m.readByClient)) {
    try { await apiJson(`/api/cases/${c.id}/messages/read`, { method: "POST", json: {} }); refreshBell(); } catch (e) {}
  }
  return `<div class="page-title">${TITLES.messages[0]}</div><div class="page-sub">${TITLES.messages[1]}</div>
  <div class="card">
    <div class="chat-thread">${msgs.map(m => `<div class="bubble ${m.fromLawyer ? "theirs" : "mine"}">
      <div class="meta">${esc(m.fromName)} · ${fmtDate(m.sentAt)}</div><b>${esc(m.subject)}</b><br>${esc(m.body)}</div>`).join("") || '<div class="page-sub">No messages yet.</div>'}
    </div>
    <textarea id="client-msg" rows="2" placeholder="Write a message to your attorney..."></textarea>
    <div class="row-actions"><button class="btn btn-gold" onclick="sendClientMsg('${c.id}')">Send</button></div>
  </div>`;
}
async function sendClientMsg(caseId) {
  const body = document.getElementById("client-msg").value;
  if (!body.trim()) return;
  try { await apiJson(`/api/cases/${caseId}/messages`, { method: "POST", json: { body } }); toast("Message sent", "ok"); show("messages"); }
  catch (e) { toast(e.message, "err"); }
}

async function rateView() {
  const ratings = await apiJson("/api/ratings");
  const cases = await apiJson("/api/client/cases");
  const c = cases[0];
  return `<div class="page-title">${TITLES.rate[0]}</div><div class="page-sub">${TITLES.rate[1]}</div>
  <div class="card" style="max-width:560px">
    <h3>How was your experience?</h3>
    <div class="stars" id="star-row" style="font-size:34px">
      ${[1,2,3,4,5].map(i => `<button data-s="${i}" onclick="pickStar(${i})">☆</button>`).join("")}
    </div>
    <label>Your comment (optional)</label>
    <textarea id="rate-comment" rows="3" placeholder="What went well, what could improve..."></textarea>
    <div class="row-actions"><button class="btn btn-gold" onclick="submitRating('${c ? c.id : ""}')">Submit rating</button></div>
  </div>
  <div class="card"><h3>Ratings received by the firm</h3>
    <table><thead><tr><th>Case</th><th>Score</th><th>Comment</th><th>Date</th></tr></thead>
    <tbody>${ratings.map(r => `<tr><td>${esc(r.caseId)}</td><td class="stars">${"★".repeat(r.score)}${"☆".repeat(5 - r.score)}</td><td>${esc(r.comment)}</td><td>${fmtDate(r.createdAt)}</td></tr>`).join("") || '<tr><td colspan="4">No ratings yet.</td></tr>'}
    </tbody></table></div>`;
}
let pickedStar = 0;
function pickStar(n) { pickedStar = n; document.querySelectorAll("#star-row button").forEach(b => { b.textContent = +b.dataset.s <= n ? "★" : "☆"; b.classList.toggle("on", +b.dataset.s <= n); }); }
async function submitRating(caseId) {
  if (!caseId) { toast("No case linked", "err"); return; }
  if (!pickedStar) { toast("Choose a star rating", "err"); return; }
  try { await apiJson("/api/ratings", { method: "POST", json: { caseId, score: pickedStar, comment: document.getElementById("rate-comment").value } });
    toast("Thank you! Your rating was submitted", "ok"); show("rate"); }
  catch (e) { toast(e.message, "err"); }
}

/* ================= ADMIN ================= */
async function usersView() {
  const [users, locks] = await Promise.all([
    apiJson("/api/admin/users"),
    apiJson("/api/admin/locked-accounts")
  ]);
  const lockMap = {};
  (locks || []).forEach(l => { lockMap[l.key] = l; });
  return `<div class="page-title">${TITLES.users[0]}</div>
  ${(locks && locks.length) ? `<div class="card" style="border-left:4px solid #B3261E">
    <h3>Brute-force login lockouts (${locks.length} locked after 3 failed attempts)</h3>
    <table><thead><tr><th>Account / Key</th><th>Failed attempts</th><th>Auto-unlocks in</th><th>Action</th></tr></thead>
    <tbody>${locks.map(l => `<tr><td class="mono">${esc(l.key)}</td><td>${l.failedAttempts}</td><td>${Math.ceil(l.secondsUntilUnlock / 60)} min</td>
      <td><button class="btn btn-sm btn-gold" onclick="unlockLoginKey('${esc(l.key)}')">Unlock now</button></td></tr>`).join("")}
    </tbody></table>
  </div>` : ""}
  <div class="card">
    <div class="row-actions" style="justify-content:flex-end;margin-top:0">
      <button class="btn btn-primary btn-sm" onclick="openCreateUserModal()">+ New user</button>
    </div>
    <table><thead><tr><th>Name</th><th>Email</th><th>Role</th><th>Status</th><th>Action</th></tr></thead>
  <tbody>${users.map(u => {
    const isLocked = !!lockMap[(u.email || "").toLowerCase()];
    return `<tr><td>${esc(u.name)}</td><td>${esc(u.email)}</td>
    <td><span class="chip ${u.role === "LAWYER" ? "c-blue" : u.role === "CLIENT" ? "c-green" : "c-amber"}">${esc(u.role)}</span></td>
    <td>${!u.active ? '<span class="chip c-red">REVOKED</span>' : isLocked ? '<span class="chip c-amber">LOCKED (3 FAILS)</span>' : '<span class="chip c-green">ACTIVE</span>'}</td>
    <td>${isLocked ? `<button class="btn btn-sm btn-gold" onclick="unlockLoginKey('${u.id}')">Unlock login</button> ` : ""}${u.role === "ADMIN" ? "Protected"
        : u.active ? `<button class="btn btn-sm btn-danger" onclick="toggleUser('${u.id}',false)">Revoke access</button>`
        : `<button class="btn btn-sm btn-ghost" onclick="toggleUser('${u.id}',true)">Restore access</button>`}</td></tr>`;
  }).join("")}
  </tbody></table></div>`;
}
async function openCreateUserModal() {
  let cases = [];
  try { cases = await apiJson("/api/cases"); } catch (e) {}
  showModal(`
    <h3>Create user account</h3>
    <label>Full name *</label><input id="nu-name" placeholder="e.g. Sibongile Dlamini">
    <label>Email address *</label><input id="nu-email" type="email" placeholder="user@example.com">
    <label>Password * (min 6 characters)</label><input id="nu-password" type="password" placeholder="Initial password">
    <label>Role *</label>
    <select id="nu-role" onchange="document.getElementById('nu-case-wrap').style.display = this.value === 'CLIENT' ? 'block' : 'none'">
      <option value="CLIENT">CLIENT</option>
      <option value="LAWYER">LAWYER</option>
      <option value="ADMIN">ADMIN</option>
    </select>
    <div id="nu-case-wrap">
      <label>Link to existing matter (optional)</label>
      <select id="nu-case">
        <option value="">— None —</option>
        ${cases.map(c => `<option value="${esc(c.id)}">${esc(c.reference)} — ${esc(c.title)}</option>`).join("")}
      </select>
    </div>
    <div class="row-actions" style="margin-top:12px">
      <button class="btn btn-primary" onclick="submitCreateUser()">Create account</button>
      <button class="btn btn-ghost" onclick="closeModal()">Cancel</button>
    </div>`);
}
async function submitCreateUser() {
  const name = (document.getElementById("nu-name")?.value || "").trim();
  const email = (document.getElementById("nu-email")?.value || "").trim();
  const password = document.getElementById("nu-password")?.value || "";
  const role = document.getElementById("nu-role")?.value || "CLIENT";
  const caseId = role === "CLIENT" ? (document.getElementById("nu-case")?.value || "") : "";
  if (!name || !email || password.length < 6) {
    toast("Enter name, valid email and a password of at least 6 characters", "err");
    return;
  }
  try {
    await apiJson("/api/admin/users", { method: "POST", json: { name, email, password, role, caseId } });
    closeModal();
    toast("User account created: " + email, "ok");
    show("users");
  } catch (e) { toast(e.message, "err"); }
}
async function toggleUser(id, active) {
  try { await apiJson(`/api/admin/users/${id}/${active ? "restore" : "revoke"}`, { method: "POST", json: {} }); toast(active ? "Access restored" : "Access revoked", "ok"); show("users"); }
  catch (e) { toast(e.message, "err"); }
}
async function unlockLoginKey(idOrKey) {
  try { await apiJson(`/api/admin/users/${encodeURIComponent(idOrKey)}/unlock`, { method: "POST", json: {} }); toast("Login lockout cleared", "ok"); show("users"); }
  catch (e) { toast(e.message, "err"); }
}

async function auditView() {
  const [logs, rejected] = await Promise.all([
    apiJson("/api/activity?limit=500"),
    apiJson("/api/admin/rejections")
  ]);
  return `<div class="page-title">${TITLES.audit[0]}</div>
  <div class="card"><div class="row-actions">
    <button class="btn btn-primary" onclick="download('/api/admin/reports/audit.csv','audit-report.csv')">Download audit report</button>
    <button class="btn btn-ghost" onclick="download('/api/admin/reports/cases.csv','cases-report.csv')">Download case register</button>
    <button class="btn btn-ghost" onclick="download('/api/reports/erp-workbook.xlsx','LegalCase-ERP-Workbook.xlsx')">Download ERP workbook (.xlsx)</button>
    <button class="btn btn-gold" onclick="runChainVerify()">Verify audit hash chain</button>
  </div>
  <div id="chain-verify-box" style="margin:8px 0"></div>
  <table><thead><tr><th>Timestamp</th><th>User</th><th>Action</th><th>Details</th><th>Level</th></tr></thead>
  <tbody>${logs.map(a => `<tr><td>${esc(fmtDate(a.timestamp))}</td><td>${esc(a.userName)}</td><td><b>${esc(a.action)}</b></td><td>${esc(a.details)}</td><td><span class="chip ${a.level === "SUCCESS" ? "c-green" : a.level === "WARNING" ? "c-amber" : "c-blue"}">${esc(a.level)}</span></td></tr>`).join("") || '<tr><td colspan="5">No activity yet.</td></tr>'}
  </tbody></table></div>

  <div class="card">
    <h3>Rejected documents (AI validation) - ${rejected.length} blocked</h3>
    <p class="page-sub">Documents the AI blocked because they were irrelevant to the case or contained incorrect information.</p>
    <div class="row-actions">
      <button class="btn btn-primary" onclick="download('/api/admin/reports/rejections.csv','rejection-reasons.csv')">Download rejection reasons (CSV)</button>
    </div>
    <table><thead><tr><th>File</th><th>Type</th><th>Case</th><th>Uploaded by</th><th>Uploaded</th><th>Rejection reason</th></tr></thead>
    <tbody>${rejected.map(r => `<tr><td>${esc(r.fileName)}</td><td>${esc(r.docType)}</td><td>${esc(r.caseReference || "—")}</td><td>${esc(r.uploadedBy)}</td><td>${fmtDate(r.uploadedAt)}</td><td style="color:#B3261E"><b>${esc(r.rejectionReason)}</b></td></tr>`).join("") || '<tr><td colspan="6">No rejected documents yet.</td></tr>'}
    </tbody></table>
  </div>`;
}

async function runChainVerify() {
  const box = document.getElementById("chain-verify-box");
  try {
    const r = await apiJson("/api/admin/audit/verify");
    box.innerHTML = r.intact
      ? `<div style="padding:10px 12px;border-radius:6px;background:#E6F4EA;color:#1E6F3D"><b>Hash chain intact:</b> ${r.totalRows} entries verified in ${r.elapsedMs} ms · Head SHA-256: <span class="mono">${esc((r.headHash || "").slice(0, 24))}...</span></div>`
      : `<div style="padding:10px 12px;border-radius:6px;background:#FDE7E7;color:#B3261E"><b>Tampering detected:</b> chain broken at entry #${r.brokenAtId}</div>`;
  } catch (e) { toast(e.message, "err"); }
}

async function systemView() {
  const [metrics, runs, datasets, backups, corrections] = await Promise.all([
    apiJson("/api/admin/metrics"),
    apiJson("/api/admin/training-runs"),
    apiJson("/api/admin/datasets"),
    apiJson("/api/admin/backups"),
    apiJson("/api/admin/corrections")
  ]);
  const keys = ["totalUsers", "lawyers", "clients", "revokedUsers", "totalCases", "activeCases", "totalDocuments", "processedDocuments", "failedDocuments", "totalDeadlines", "pendingSyncs", "totalMessages", "totalRatings", "trainingRuns", "datasets", "avgConfidence", "dataResidency"];
  return `<div class="page-title">${TITLES.system[0]}</div>
  <div class="card"><h3>Usage metrics</h3><div class="row-actions"><button class="btn btn-primary" onclick="download('/api/admin/reports/system.csv','system-model-report.csv')">Download system report</button></div>
    <div class="metric-grid">${keys.map(k => `<div class="metric"><div class="k">${esc(k)}</div><div class="v">${typeof metrics[k] === "number" ? (metrics[k] % 1 ? metrics[k].toFixed(2) : metrics[k]) : esc(metrics[k] || "—")}</div></div>`).join("")}</div></div>
  <div class="card"><h3>Encrypted backups &amp; recovery verification</h3>
    <div class="row-actions"><button class="btn btn-gold" onclick="triggerBackup()">+ Create encrypted backup &amp; verify restore</button></div>
    <table><thead><tr><th>Archive</th><th>Rows</th><th>Size</th><th>Backup time</th><th>Restore verify</th><th>Status</th><th></th></tr></thead>
    <tbody>${backups.map(b => `<tr>
      <td class="mono">${esc(b.filename)}</td><td>${b.totalRows}</td><td>${Math.max(1, Math.round(b.sizeBytes / 1024))} KB</td>
      <td>${b.backupMs} ms</td><td>${b.restoreVerifyMs} ms</td>
      <td>${b.verified ? '<span class="chip c-green">VERIFIED</span>' : '<span class="chip c-red">FAILED</span>'}</td>
      <td><button class="btn btn-sm btn-ghost" onclick="download('/api/admin/backups/${encodeURIComponent(b.filename)}/download','${esc(b.filename)}')">Download</button></td>
    </tr>`).join("") || '<tr><td colspan="7">No backups created yet.</td></tr>'}
    </tbody></table>
  </div>
  <div class="card"><h3>Model retraining runs</h3>
    <table><thead><tr><th>Model</th><th>Dataset</th><th>Status</th><th>Requested by</th><th>Action</th></tr></thead>
    <tbody>${runs.map(r => `<tr><td>${esc(r.modelName)}</td><td>${esc(r.datasetId)}</td><td><span class="chip ${r.status === "APPROVED" ? "c-green" : r.status === "PENDING" ? "c-amber" : "c-blue"}">${esc(r.status)}</span></td><td>${esc(r.requestedBy)}</td>
      <td>${r.status === "PENDING" ? `<button class="btn btn-sm btn-gold" onclick="approveRun('${r.id}')">Approve run</button>` : ""}</td></tr>`).join("") || '<tr><td colspan="5">No training runs.</td></tr>'}
    </tbody></table></div>
  <div class="card"><h3>Training datasets (${corrections.length} practitioner edits harvested)</h3>
    <div class="row-actions"><input type="file" id="ds-input" accept=".csv,.txt,.jsonl">
      <button class="btn btn-primary" onclick="uploadDataset()">+ Upload dataset</button></div>
    <table><thead><tr><th>Name</th><th>Records</th><th>Size (KB)</th><th>Uploaded</th><th></th></tr></thead>
    <tbody>${datasets.map(d => `<tr><td>${esc(d.name)}</td><td>${d.records}</td><td>${d.sizeKb}</td><td>${fmtDate(d.uploadedAt)}</td>
      <td><button class="btn btn-sm btn-ghost" onclick="download('/api/admin/datasets/${d.id}/download','${esc(d.name.replace(/[^a-zA-Z0-9._-]/g,"_"))}${d.storagePath && d.storagePath.endsWith(".jsonl") ? ".jsonl" : ".csv"}')">Download</button></td></tr>`).join("") || '<tr><td colspan="5">No datasets.</td></tr>'}
    </tbody></table></div>`;
}
async function triggerBackup() {
  try {
    const b = await apiJson("/api/admin/backups", { method: "POST", json: {} });
    toast(`Backup verified (${b.backupMs} ms backup · ${b.restoreVerifyMs} ms restore check)`, "ok");
    show("system");
  } catch (e) { toast(e.message, "err"); }
}
async function approveRun(id) {
  try { await apiJson(`/api/admin/training-runs/${id}/approve`, { method: "POST", json: {} }); toast("Retraining run approved", "ok"); show("system"); }
  catch (e) { toast(e.message, "err"); }
}
async function uploadDataset() {
  const input = document.getElementById("ds-input");
  if (!input.files[0]) { toast("Choose a dataset file first", "err"); return; }
  const name = prompt("Dataset name:", input.files[0].name) || input.files[0].name;
  const fd = new FormData();
  fd.append("file", input.files[0]);
  fd.append("name", name);
  try { await upload("/api/admin/datasets", fd); toast("Dataset uploaded", "ok"); show("system"); }
  catch (e) { toast(e.message, "err"); }
}

/* ---------------- boot / auth ---------------- */
async function login(email, password) {
  const res = await fetch("/api/auth/login", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ email, password }) });
  if (res.status === 403) { const j = await res.json(); throw new Error(j.error || "Account disabled"); }
  if (res.status >= 400) { const j = await res.json().catch(() => ({})); throw new Error(j.error || "Invalid email or password"); }
  const j = await res.json();
  TOKEN = j.token; localStorage.setItem("lce_token", TOKEN); ME = j.user;
  enterApp();
}
function logout(expired) {
  if (window._sseAbort) window._sseAbort.abort();
  window._sseAbort = null;
  window._sseActive = false;
  TOKEN = ""; localStorage.removeItem("lce_token"); ME = null;
  document.getElementById("app").classList.add("hidden");
  document.getElementById("login-view").classList.remove("hidden");
  removeHelpChat();
  if (expired) document.getElementById("login-error").textContent = "Session expired - please sign in again.";
}
function enterApp() {
  document.getElementById("login-view").classList.add("hidden");
  document.getElementById("app").classList.remove("hidden");
  document.getElementById("top-user").textContent = ME.name;
  const rc = document.getElementById("top-role");
  rc.textContent = ME.role;
  rc.className = "role-chip role-" + ME.role;
  document.getElementById("status-text").textContent = "Connected to backend: " + location.origin;
  show(ME.role === "LAWYER" ? "dashboard" : ME.role === "ADMIN" ? "users" : "home");
  startPoller();
  initHelpChat();
}
async function bootstrap() {
  if (!TOKEN) { return; }
  try {
    const res = await fetch("/api/me", { headers: { Authorization: "Bearer " + TOKEN } });
    if (res.status === 401) { logout(true); return; }
    ME = await res.json();
    if (!ME || !ME.role) { logout(); return; }
    enterApp();
  } catch (e) { /* backend unreachable - stay on login */ }
}
document.getElementById("login-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  const err = document.getElementById("login-error");
  err.classList.add("hidden");
  try { await login(document.getElementById("login-email").value, document.getElementById("login-password").value); }
  catch (ex) { err.textContent = ex.message; err.classList.remove("hidden"); }
});
document.getElementById("pw-toggle").addEventListener("click", () => {
  const p = document.getElementById("login-password");
  const t = document.getElementById("pw-toggle");
  const shown = p.type === "text";
  p.type = shown ? "password" : "text";
  t.textContent = shown ? "Show" : "Hide";
});
document.getElementById("logout-btn").addEventListener("click", () => logout(false));
document.getElementById("alert-bell").addEventListener("click", () => {
  if (ME.role === "CLIENT") { show("messages"); return; }
  gotoUnreadCase();
});

/* Lawyer: jump straight to the case that has a new client message and open the chat */
async function gotoUnreadCase() {
  try {
    const cases = await apiJson("/api/cases");
    for (const c of cases) {
      const msgs = await apiJson(`/api/cases/${c.id}/messages`);
      const hasUnread = msgs.some(m => !m.fromLawyer && !m.readByLawyer);
      if (hasUnread) {
        // mark the client->lawyer messages read, then open that case's chat
        await apiJson(`/api/cases/${c.id}/messages/read-by-lawyer`, { method: "POST", json: {} });
        show("case_detail", c.id);
        setTimeout(scrollToMessages, 900);
        toast("New message on " + (c.reference || c.id), "ok");
        return;
      }
    }
    toast("No unread client messages", "ok");
  } catch (e) { toast(e.message, "err"); }
}
function scrollToMessages() {
  const headings = document.querySelectorAll("#content h3");
  for (const h of headings) {
    if (h.textContent.includes("Message the client")) {
      const card = h.closest(".card");
      if (card) {
        card.scrollIntoView({ behavior: "smooth", block: "center" });
        card.style.boxShadow = "0 0 0 3px var(--gold)";
        setTimeout(() => { card.style.boxShadow = ""; }, 2500);
      }
      break;
    }
  }
}

/* Live refresh: when a deadline changes server-side (added / synced / unsynced),
   re-render the current view if it shows deadlines (dashboard or calendar). */
function refreshDeadlineViews() {
  if (!ME) return;
  if (current === "dashboard" && ME.role === "LAWYER") { show("dashboard"); }
  else if (current === "calendar") { show("calendar"); }
  else if (current === "case_detail") { show("case_detail", window._caseParam); }
  refreshBell();
}

/* unread bell: INSTANT refresh via server-push (SSE) + a slow fallback poll */
function refreshBell() {
  const path = ME.role === "CLIENT" ? "/api/me/unread" : "/api/me/unread-lawyer";
  apiJson(path).then(r => {
    const n = (r && r.unread) || 0;
    const bell = document.getElementById("alert-bell");
    if (n > 0) { document.getElementById("alert-count").textContent = n; bell.classList.remove("hidden"); }
    else bell.classList.add("hidden");
  }).catch(() => {});
}

function startPoller() {
  refreshBell();
  connectSse();
  clearInterval(window._poll);
  window._poll = setInterval(refreshBell, 30000); // fallback only if the stream drops
}

/* Connects to GET /api/events (SSE) and refreshes the bell the instant a
   "new-message" event arrives - no waiting for a poll interval. */
function connectSse() {
  if (window._sseActive) return;
  window._sseActive = true;
  window._sseAbort = new AbortController();
  (async () => {
    try {
      const res = await fetch("/api/events", {
        headers: { Authorization: "Bearer " + TOKEN, Accept: "text/event-stream" },
        signal: window._sseAbort.signal
      });
      if (!res.ok || !res.body) throw new Error("sse " + res.status);
      const reader = res.body.getReader();
      const decoder = new TextDecoder();
      let buf = "";
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buf += decoder.decode(value, { stream: true });
        let idx;
        while ((idx = buf.indexOf("\n\n")) >= 0) {
          const frame = buf.slice(0, idx);
          buf = buf.slice(idx + 2);
          for (const line of frame.split("\n")) {
            const ev = line.indexOf("event:") === 0 ? line.slice(6).trim() : "";
            if (ev === "new-message") {
              refreshBell();
            } else if (ev === "deadline-updated") {
              refreshDeadlineViews();
            }
          }
        }
      }
    } catch (e) {
      // aborted on logout or stream error - handled below
    }
    window._sseActive = false;
    window._sseAbort = null;
    if (TOKEN) setTimeout(connectSse, 3000); // auto-reconnect
  })();
}

/* ---------------- Trust Ledger, Accessibility & Offline PWA ---------------- */
function openTrustModal(caseId, availableCredit) {
  showModal(`
    <h3>Record client trust transaction</h3>
    <p class="page-sub">Available client trust credit: <b>R ${Number(availableCredit || 0).toLocaleString(undefined,{minimumFractionDigits:2,maximumFractionDigits:2})}</b> (Section 86 accounts cannot go into debit)</p>
    <label>Transaction type</label>
    <select id="tr-type">
      <option value="DEPOSIT">Client trust deposit (credit +)</option>
      <option value="TRANSFER_TO_BUSINESS">Transfer to settle fee note / disbursement (debit -)</option>
      <option value="REFUND">Refund trust surplus to client (debit -)</option>
    </select>
    <label>Description *</label><input id="tr-desc" placeholder="e.g. EFT retainer deposit / Transfer against INV-014">
    <label>Amount (ZAR) *</label><input id="tr-amount" type="number" step="50" value="2500">
    <label>Payment / EFT reference (optional)</label><input id="tr-ref" placeholder="e.g. EFT-99201">
    <div class="row-actions">
      <button class="btn btn-primary" onclick="submitTrustTx('${caseId}')">Record transaction</button>
      <button class="btn btn-ghost" onclick="closeModal()">Cancel</button>
    </div>`);
}
async function submitTrustTx(caseId) {
  const txType = document.getElementById("tr-type").value;
  const description = document.getElementById("tr-desc").value.trim();
  const amount = parseFloat(document.getElementById("tr-amount").value || "0");
  const reference = document.getElementById("tr-ref").value.trim();
  if (!description) { toast("Description is required", "err"); return; }
  try {
    await apiJson(`/api/cases/${caseId}/trust`, { method: "POST", json: { txType, description, amount, reference } });
    closeModal();
    toast("Trust ledger updated", "ok");
    show("case_detail", caseId);
  } catch (e) { toast(e.message, "err"); }
}

function toggleLargeText() {
  const on = document.body.classList.toggle("a11y-large");
  localStorage.setItem("lce_a11y_large", on ? "1" : "0");
  toast(on ? "Large text enabled" : "Standard text restored", "ok");
}
function toggleHighContrast() {
  const on = document.body.classList.toggle("a11y-contrast");
  localStorage.setItem("lce_a11y_contrast", on ? "1" : "0");
  toast(on ? "High-contrast mode enabled" : "Standard palette restored", "ok");
}
function toggleDarkMode() {
  const on = document.body.classList.toggle("dark");
  localStorage.setItem("lce_dark", on ? "1" : "0");
  const b = document.getElementById("theme-btn");
  if (b) { b.textContent = on ? "Light" : "Dark"; b.setAttribute("aria-pressed", String(on)); }
}
(function initA11yAndOffline() {
  if (localStorage.getItem("lce_dark") === "1") {
    document.body.classList.add("dark");
    const b = document.getElementById("theme-btn");
    if (b) { b.textContent = "Light"; b.setAttribute("aria-pressed", "true"); }
  }
  if (localStorage.getItem("lce_a11y_large") === "1") document.body.classList.add("a11y-large");
  if (localStorage.getItem("lce_a11y_contrast") === "1") document.body.classList.add("a11y-contrast");
  if ("serviceWorker" in navigator) {
    navigator.serviceWorker.register("/sw.js").catch(() => {});
  }
  window.addEventListener("offline", () => {
    const el = document.getElementById("status-text");
    if (el) el.textContent = "Offline mode — viewing cached LegalCase ERP records";
  });
  window.addEventListener("online", () => {
    const el = document.getElementById("status-text");
    if (el) el.textContent = "Connected to backend: " + location.origin;
  });
})();

bootstrap();

/* ================= HELP CHAT ROBOT =================
   Rule-based assistant (POST /api/chat). Floating button bottom-right.
   Nothing is stored on the server or in the browser; the log clears on sign-out. */
function removeHelpChat() {
  ["hc-fab", "hc-panel"].forEach(id => { const el = document.getElementById(id); if (el) el.remove(); });
}
function initHelpChat() {
  removeHelpChat();
  const fab = document.createElement("button");
  fab.id = "hc-fab"; fab.type = "button"; fab.className = "hc-fab";
  fab.textContent = "Help chat";
  fab.setAttribute("aria-label", "Open help chat");
  fab.setAttribute("aria-controls", "hc-panel");
  fab.setAttribute("aria-expanded", "false");
  fab.addEventListener("click", toggleHelpChat);

  const panel = document.createElement("section");
  panel.id = "hc-panel"; panel.className = "hc-panel hidden";
  panel.setAttribute("role", "dialog"); panel.setAttribute("aria-label", "Help chat");
  panel.innerHTML = `
    <div class="hc-head"><span>Help assistant</span>
      <button type="button" class="hc-close" aria-label="Close help chat">&times;</button></div>
    <div id="hc-log" class="hc-log" aria-live="polite"></div>
    <div id="hc-sugg" class="hc-sugg"></div>
    <form id="hc-form" class="hc-form" autocomplete="off">
      <input id="hc-input" type="text" maxlength="500" placeholder="Type your question..." aria-label="Your question">
      <button type="submit" class="btn btn-primary btn-sm">Send</button>
    </form>`;
  document.body.appendChild(fab);
  document.body.appendChild(panel);
  panel.querySelector(".hc-close").addEventListener("click", toggleHelpChat);
  panel.querySelector("#hc-form").addEventListener("submit", e => {
    e.preventDefault();
    const inp = document.getElementById("hc-input");
    const text = inp.value.trim();
    if (!text) return;
    inp.value = "";
    sendHelpChat(text);
  });
  const hi = ME && ME.role === "CLIENT"
    ? "Hi! Ask me about your next court date, what you owe, how to pay, your documents or your messages."
    : "Hi! Ask me about a case by its reference (for example C-2026-001), or how to use the system.";
  addHelpBubble("bot", hi);
  renderHelpSuggestions(ME && ME.role === "CLIENT"
    ? ["When is my next court date?", "How much do I owe?", "How do I pay my invoice?"]
    : ["What is the status of C-2026-001?", "help"]);
}
function toggleHelpChat() {
  const panel = document.getElementById("hc-panel");
  const fab = document.getElementById("hc-fab");
  if (!panel) return;
  const open = panel.classList.toggle("hidden") === false;
  fab.setAttribute("aria-expanded", String(open));
  if (open) document.getElementById("hc-input").focus();
}
function addHelpBubble(who, text) {
  const log = document.getElementById("hc-log");
  if (!log) return null;
  const div = document.createElement("div");
  div.className = "hc-msg hc-" + who;
  div.innerHTML = esc(text).replace(/\n/g, "<br>");
  log.appendChild(div);
  log.scrollTop = log.scrollHeight;
  return div;
}
function renderHelpSuggestions(list) {
  const box = document.getElementById("hc-sugg");
  if (!box) return;
  box.innerHTML = "";
  (list || []).forEach(s => {
    const b = document.createElement("button");
    b.type = "button"; b.className = "hc-chip"; b.textContent = s;
    b.addEventListener("click", () => sendHelpChat(s));
    box.appendChild(b);
  });
}
async function sendHelpChat(text) {
  addHelpBubble("user", text);
  const pending = addHelpBubble("bot", "...");
  renderHelpSuggestions([]);
  try {
    const data = await apiJson("/api/chat", { method: "POST", json: { message: text } });
    if (pending) pending.innerHTML = esc(data.reply).replace(/\n/g, "<br>");
    renderHelpSuggestions(data.suggestions || []);
  } catch (e) {
    if (pending) pending.textContent = "Sorry, I could not answer just now. " + e.message;
  }
}

/* ================= DRAG-AND-DROP UPLOAD =================
   Drop a PDF or Word file on a case's Documents card. Uses the same upload as the button. */
document.addEventListener("dragover", e => {
  const z = e.target.closest && e.target.closest("[data-dropzone]");
  e.preventDefault();
  if (z) { z.classList.add("dragover"); if (e.dataTransfer) e.dataTransfer.dropEffect = "copy"; }
});
document.addEventListener("dragleave", e => {
  const z = e.target.closest && e.target.closest("[data-dropzone]");
  if (z && !z.contains(e.relatedTarget)) z.classList.remove("dragover");
});
document.addEventListener("drop", e => {
  e.preventDefault();
  document.querySelectorAll(".dragover").forEach(x => x.classList.remove("dragover"));
  const z = e.target.closest && e.target.closest("[data-dropzone]");
  if (!z || !e.dataTransfer || !e.dataTransfer.files.length) return;
  const file = e.dataTransfer.files[0];
  if (!/\.(pdf|docx?)$/i.test(file.name)) { toast("Please drop a PDF or Word file", "err"); return; }
  const input = document.getElementById("upload-input");
  if (!input) return;
  const dt = new DataTransfer();
  dt.items.add(file);
  input.files = dt.files;
  uploadDoc(z.dataset.dropzone);
});
