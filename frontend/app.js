const state = { dashboard: null, bill: null };
const $ = (selector) => document.querySelector(selector);

async function api(path, options = {}) {
  const response = await fetch(`/api${path}`, {
    credentials: "include",
    headers: { "Content-Type": "application/x-www-form-urlencoded", ...(options.headers || {}) },
    ...options
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(data.error || "The request could not be completed.");
  return data;
}

function formBody(form) {
  return new URLSearchParams(new FormData(form));
}

function toast(message, type = "success") {
  const node = document.createElement("div");
  node.className = `toast ${type}`;
  node.textContent = message;
  $("#toast-region").append(node);
  setTimeout(() => node.remove(), 4500);
}

function setBusy(button, busy) {
  if (!button) return;
  if (busy) { button.dataset.label = button.textContent; button.disabled = true; button.textContent = "Working..."; }
  else { button.disabled = false; button.textContent = button.dataset.label || button.textContent; }
}

function showApp() {
  $("#login-view").classList.add("hidden");
  $("#app-view").classList.remove("hidden");
  refresh();
}

function renderDashboard(data) {
  state.dashboard = data;
  $("#total-slots").textContent = data.totalSlots;
  $("#occupied-slots").textContent = data.occupiedSlots;
  $("#vacant-slots").textContent = `${data.availableSlots} available`;
  $("#slot-utilization").textContent = `${Math.round((data.occupiedSlots / Math.max(1, data.totalSlots)) * 100)}% utilization`;
  $("#daily-revenue").textContent = `Rs. ${Number(data.dailyRevenue).toFixed(2)}`;
  $("#last-updated").textContent = `Updated ${new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}`;
  const body = $("#vehicles-body");
  const headers = ["Ticket", "Reg_No", "Owner", "Type", "Slot", "Entry_Time"];
  body.innerHTML = data.parkedVehicles.length ? data.parkedVehicles.map(vehicle => `<tr>${headers.map(key => `<td>${key === "Type" ? `<span class="badge">${escapeHtml(vehicle[key])}</span>` : escapeHtml(vehicle[key])}</td>`).join("")}</tr>`).join("") : `<tr><td colspan="6" class="loading">No vehicles are currently parked.</td></tr>`;
  $("#allocation-status").textContent = "Ready for allocation";
}

function renderSlots(slots) {
  const grid = $("#parking-grid");
  const counts = { FREE: 0, OCCUPIED: 0, RESERVED: 0, CHARGING: 0 };
  slots.forEach(slot => counts[slot.status] = (counts[slot.status] || 0) + 1);
  grid.innerHTML = slots.map(slot => `<div class="slot ${slot.status}"><b>${escapeHtml(slot.slot)}</b><small>${escapeHtml(slot.type)}</small><small>${slot.status === "FREE" ? "Available" : escapeHtml(slot.status.toLowerCase())}${slot.reg ? ` · ${escapeHtml(slot.reg)}` : ""}</small></div>`).join("");
  $("#ev-status").textContent = `${counts.CHARGING || 0} active`;
}

async function refresh() {
  try {
    const [dashboard, slots] = await Promise.all([api("/dashboard"), api("/slots")]);
    renderDashboard(dashboard);
    renderSlots(slots);
  } catch (error) {
    if (error.message.includes("Authentication")) {
      $("#app-view").classList.add("hidden");
      $("#login-view").classList.remove("hidden");
    } else toast(error.message, "error");
  }
}

function escapeHtml(value) {
  return String(value ?? "").replace(/[&<>"']/g, char => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[char]));
}

function showBill(bill, registration) {
  state.bill = bill;
  $("#exit-reg").value = registration;
  $("#exit-amount").value = bill.total;
  $("#bill-content").innerHTML = `<div class="bill-breakdown">
    <div class="bill-line"><span>Vehicle</span><strong>${escapeHtml(bill.registration)} · ${escapeHtml(bill.vehicleType)}</strong></div>
    <div class="bill-line"><span>Slot</span><strong>${escapeHtml(bill.slot)}</strong></div>
    <div class="bill-line"><span>Duration</span><strong>${bill.minutes} minutes (${bill.hours} hour(s))</strong></div>
    <div class="bill-line"><span>Parking charge</span><strong>Rs. ${Number(bill.parking).toFixed(2)}</strong></div>
    <div class="bill-line"><span>EV charging</span><strong>Rs. ${Number(bill.evCharging).toFixed(2)}</strong></div>
    <div class="bill-line total"><span>Total due</span><strong>Rs. ${Number(bill.total).toFixed(2)}</strong></div>
  </div>`;
  $("#bill-dialog").showModal();
}

$("#login-form").addEventListener("submit", async event => {
  event.preventDefault();
  const button = event.submitter;
  setBusy(button, true);
  try { await api("/login", { method: "POST", body: formBody(event.target) }); showApp(); toast("Signed in successfully."); }
  catch (error) { toast(error.message, "error"); }
  finally { setBusy(button, false); }
});

$("#park-form").addEventListener("submit", async event => {
  event.preventDefault();
  if (!event.target.reportValidity()) return;
  const button = event.submitter; setBusy(button, true);
  try { const result = await api("/park", { method: "POST", body: formBody(event.target) }); toast(`Vehicle parked. Ticket #${result.ticketId} created.`); event.target.reset(); await refresh(); }
  catch (error) { toast(error.message, "error"); }
  finally { setBusy(button, false); }
});

$("#checkout-form").addEventListener("submit", async event => {
  event.preventDefault();
  const button = event.submitter; setBusy(button, true);
  try { const reg = new FormData(event.target).get("reg"); showBill(await api(`/checkout?reg=${encodeURIComponent(reg)}`), reg); }
  catch (error) { toast(error.message, "error"); }
  finally { setBusy(button, false); }
});

$("#exit-form").addEventListener("submit", async event => {
  event.preventDefault();
  const button = event.submitter; setBusy(button, true);
  try { await api("/exit", { method: "POST", body: formBody(event.target) }); $("#bill-dialog").close(); toast("Payment recorded and vehicle checked out."); $("#checkout-form").reset(); await refresh(); }
  catch (error) { toast(error.message, "error"); }
  finally { setBusy(button, false); }
});

$("#payment-mode").addEventListener("change", event => $("#txn-field").classList.toggle("hidden", event.target.value !== "UPI"));
$("#close-dialog").addEventListener("click", () => $("#bill-dialog").close());
$("#cancel-dialog").addEventListener("click", () => $("#bill-dialog").close());
$("#refresh-button").addEventListener("click", refresh);
$("#logout-button").addEventListener("click", async () => { await api("/logout", { method: "POST" }); location.reload(); });
$("#theme-toggle").addEventListener("click", () => { document.body.classList.toggle("dark"); localStorage.setItem("theme", document.body.classList.contains("dark") ? "dark" : "light"); });
if (localStorage.getItem("theme") === "dark") document.body.classList.add("dark");
api("/health").then(() => api("/dashboard").then(showApp)).catch(() => {});
setInterval(() => { if (!$("#app-view").classList.contains("hidden")) refresh(); }, 10000);
