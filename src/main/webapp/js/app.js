const qs = (s, el = document) => el.querySelector(s);
const APP = qs("#app-shell");
const AUTH = qs("#auth-panel");
let currentUser = null;

/** App context path, e.g. /medstore/ */
function appBase() {
  let p = window.location.pathname;
  if (p.endsWith("/")) return p;
  const slash = p.lastIndexOf("/");
  return slash >= 0 ? p.substring(0, slash + 1) : "/";
}

function resolveUrl(path) {
  if (!path) return path;
  if (path.startsWith("http://") || path.startsWith("https://") || path.startsWith("data:")) {
    return path;
  }
  const clean = path.replace(/^\//, "");
  return appBase() + clean;
}

function toast(msg, err = false) {
  let z = qs(".toast-zone");
  if (!z) {
    z = document.createElement("div");
    z.className = "toast-zone";
    document.body.appendChild(z);
  }
  const t = document.createElement("div");
  t.className = "toast" + (err ? " err" : "");
  t.textContent = msg;
  z.appendChild(t);
  setTimeout(() => t.remove(), 4200);
}

async function api(path, opts = {}) {
  const headers = opts.headers ?? {};
  if (opts.body && !headers["Content-Type"]) headers["Content-Type"] = "application/json";
  const res = await fetch(resolveUrl(path), {
    credentials: "same-origin",
    ...opts,
    headers,
  });
  const text = await res.text();
  let data = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = { raw: text };
  }
  if (!res.ok) {
    const m = data && data.error ? data.error : `${res.status} ${res.statusText}`;
    throw new Error(m);
  }
  return data;
}

async function apiUpload(formData) {
  const res = await fetch(resolveUrl("api/upload"), {
    method: "POST",
    credentials: "same-origin",
    body: formData,
  });
  const text = await res.text();
  let data = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = { raw: text };
  }
  if (!res.ok) {
    const m = data && data.error ? data.error : `${res.status} ${res.statusText}`;
    throw new Error(m);
  }
  return data;
}

let cartCheckoutTotal = 0;
let editingMedicine = null;

async function refreshMe() {
  const data = await api("api/me");
  if (!data.authenticated) {
    currentUser = null;
    return false;
  }
  currentUser = normalizeUser(data.user);
  return true;
}

function normalizeUser(u) {
  if (!u) return u;
  if (u.role) u.role = String(u.role).toUpperCase();
  return u;
}

function mediaUrl(path) {
  if (!path) return null;
  if (path.startsWith("data:")) return path;
  if (path.startsWith("http://") || path.startsWith("https://")) return path;
  if (path.startsWith("api/media/")) return resolveUrl(path);
  const relative = path.startsWith("uploads/") ? path.substring("uploads/".length) : path;
  return resolveUrl("api/media/" + relative);
}

function canBrowseStore() {
  const r = currentUser?.role;
  return r === "CUSTOMER" || r === "PHARMACIST";
}

function defaultViewForRole() {
  const r = currentUser?.role;
  if (r === "ACCOUNTANT") return "acct-receipts";
  if (r === "ADMIN") return "adm-users";
  if (r === "PHARMACIST") return "pharm";
  return "shop";
}

function showAuth() {
  qs("#app-header").classList.add("hidden");
  AUTH.classList.remove("hidden");
  APP.classList.add("hidden");
}

function showApp() {
  qs("#app-header").classList.remove("hidden");
  AUTH.classList.add("hidden");
  APP.classList.remove("hidden");
  updateGreeting();
  renderNav();
  activateView(defaultViewForRole());
}

function updateGreeting() {
  if (!currentUser) return;
  const first = currentUser.fullName.split(" ").filter(Boolean)[0] || "";
  qs("#greet-h1").textContent = first ? `Hi, ${first}` : "Dashboard";
  qs("#greet-p").textContent = `Signed in as ${currentUser.email} · ${currentUser.role.replaceAll("_", " ")}`;
}

function renderNav() {
  const tabs = qs("#nav-tabs");
  tabs.innerHTML = "";
  const mk = (id, label) => {
    const b = document.createElement("button");
    b.type = "button";
    b.dataset.view = id;
    b.textContent = label;
    b.addEventListener("click", () => activateView(id));
    tabs.appendChild(b);
  };

  if (canBrowseStore()) {
    mk("shop", "Store");
  }
  if (currentUser && currentUser.role === "CUSTOMER") {
    const b = document.createElement("button");
    b.type = "button";
    b.dataset.view = "cart";
    b.innerHTML = `Cart <span class="badge" id="cart-badge">0</span>`;
    b.addEventListener("click", () => activateView("cart"));
    tabs.appendChild(b);
    mk("receipts", "Receipts");
  }
  if (currentUser && currentUser.role === "PHARMACIST") {
    mk("pharm", "Stock & catalogue");
  }
  if (currentUser && currentUser.role === "ACCOUNTANT") {
    mk("acct-receipts", "Receipts");
  }
  if (currentUser && currentUser.role === "ADMIN") {
    mk("adm-users", "Users");
  }
  if (currentUser) {
    mk("account", "My account");
  }
}

function setActiveTab(view) {
  qs("#nav-tabs").querySelectorAll("button").forEach((btn) => {
    btn.classList.toggle("active", btn.dataset.view === view);
  });
}

function activateView(view) {
  setActiveTab(view);
  qs("#views").querySelectorAll("[data-pane]").forEach((p) => {
    p.classList.toggle("hidden", p.dataset.pane !== view);
  });

  if (view === "shop") {
    loadMedicines();
    loadPrescriptionPanel();
  }
  if (view === "cart") loadCart();
  if (view === "receipts") loadPatientReceipts();
  if (view === "acct-receipts") loadAccountantReceipts();
  if (view === "adm-users") loadAdminUsers();
  if (view === "pharm") loadPharmacistCatalog();
  if (view === "account" && currentUser) populateAccount(currentUser);
}

async function loadMedicines() {
  const wrap = qs("#medicine-grid");
  wrap.innerHTML = '<p class="small">Loading catalogue…</p>';
  try {
    const data = await api("api/medicines");
    wrap.innerHTML = "";
    const list = data.medicines || [];
    if (!list.length) {
      wrap.innerHTML = '<p class="small">No items yet — ask a pharmacist to publish stock.</p>';
      return;
    }
    list.forEach((m) => {
      const el = document.createElement("article");
      el.className = "product";
      const canBuy = currentUser && currentUser.role === "CUSTOMER";
      const imgSrc = mediaUrl(m.imageUrl || m.imagePath);
      const imgHtml = imgSrc
        ? `<img class="product-img" src="${escapeHtml(imgSrc)}?v=${encodeURIComponent(m.id)}" alt="" loading="lazy" onerror="this.classList.add('img-broken')" />`
        : `<div class="product-img product-img-placeholder" aria-hidden="true"></div>`;
      el.innerHTML = `
        ${imgHtml}
        <h3>${escapeHtml(m.name)}</h3>
        <p class="small">${escapeHtml((m.description || "").slice(0, 160))}${(m.description || "").length > 160 ? "…" : ""}</p>
        <div class="product-meta">${m.stock ?? 0} in stock</div>
        <div class="price-tag">€${Number(m.price).toFixed(2)}</div>
      `;
      if (canBuy) {
        const row = document.createElement("div");
        row.className = "btn-row";
        const qty = document.createElement("input");
        qty.type = "number";
        qty.min = "1";
        qty.value = "1";
        qty.style.maxWidth = "80px";
        const add = document.createElement("button");
        add.type = "button";
        add.className = "btn btn-primary";
        add.textContent = "Add to cart";
        add.addEventListener("click", async () => {
          try {
            const q = parseInt(qty.value, 10) || 1;
            await api("api/cart", {
              method: "POST",
              body: JSON.stringify({ medicineId: m.id, quantity: q }),
            });
            toast("Added to cart");
            bumpCartBadge();
          } catch (e) {
            toast(e.message, true);
          }
        });
        row.appendChild(qty);
        row.appendChild(add);
        el.appendChild(row);
      }
      wrap.appendChild(el);
    });
  } catch (e) {
    wrap.innerHTML = `<p class="small">${escapeHtml(e.message)}</p>`;
  }
}

async function bumpCartBadge() {
  if (!currentUser || currentUser.role !== "CUSTOMER") return;
  try {
    const data = await api("api/cart");
    let n = 0;
    (data.lines || []).forEach((l) => {
      n += l.quantity || 0;
    });
    const b = qs("#cart-badge");
    if (b) b.textContent = String(n);
  } catch {}
}

async function loadCart() {
  const body = qs("#cart-body");
  body.innerHTML = "Loading…";
  try {
    const data = await api("api/cart");
    body.innerHTML = "";
    const lines = data.lines || [];
    if (!lines.length) {
      body.innerHTML = "<p class='small'>Cart is empty.</p>";
      qs("#checkout-btn").disabled = true;
      return;
    }
    qs("#checkout-btn").disabled = false;
    lines.forEach((l) => {
      const div = document.createElement("div");
      div.className = "product";
      div.innerHTML = `
        <strong>${escapeHtml(l.name)}</strong>
        <div class="product-meta">${Number(l.unitPrice).toFixed(2)} € each</div>
        <div class="price-tag">€${Number(l.lineTotal).toFixed(2)}</div>
      `;
      const row = document.createElement("div");
      row.className = "btn-row";
      const qty = document.createElement("input");
      qty.type = "number";
      qty.min = "0";
      qty.value = String(l.quantity);
      const save = document.createElement("button");
      save.type = "button";
      save.className = "btn btn-ghost";
      save.textContent = "Update qty";
      save.addEventListener("click", async () => {
        try {
          const q = parseInt(qty.value, 10) || 0;
          await api("api/cart", {
            method: "PUT",
            body: JSON.stringify({ medicineId: l.medicineId, quantity: q }),
          });
          toast("Cart updated");
          loadCart();
          bumpCartBadge();
        } catch (e) {
          toast(e.message, true);
        }
      });
      const rm = document.createElement("button");
      rm.type = "button";
      rm.className = "btn btn-ghost";
      rm.textContent = "Remove";
      rm.addEventListener("click", async () => {
        try {
          await api("api/cart?medicineId=" + encodeURIComponent(l.medicineId), {
            method: "DELETE",
          });
          toast("Removed");
          loadCart();
          bumpCartBadge();
        } catch (e) {
          toast(e.message, true);
        }
      });
      row.appendChild(qty);
      row.appendChild(save);
      row.appendChild(rm);
      div.appendChild(row);
      body.appendChild(div);
    });
    cartCheckoutTotal = Number(data.total || 0);
    qs("#cart-total").textContent = `Total €${cartCheckoutTotal.toFixed(2)}`;
  } catch (e) {
    body.innerHTML = `<p class='small'>${escapeHtml(e.message)}</p>`;
  }
}

qs("#checkout-btn").addEventListener("click", async () => {
  try {
    const data = await api("api/cart");
    if (!(data.lines || []).length) {
      toast("Cart is empty", true);
      return;
    }
    cartCheckoutTotal = Number(data.total || 0);
    qs("#payment-total-label").textContent = `Total €${cartCheckoutTotal.toFixed(2)}`;
    const payForm = qs("#payment-form");
    payForm.reset();
    if (currentUser?.fullName) {
      payForm.elements.cardholderName.value = currentUser.fullName;
    }
    openPaymentModal();
  } catch (e) {
    toast(e.message, true);
  }
});

function openPaymentModal() {
  const overlay = qs("#payment-overlay");
  overlay.classList.remove("hidden");
  overlay.setAttribute("aria-hidden", "false");
}

function closePaymentModal() {
  const overlay = qs("#payment-overlay");
  overlay.classList.add("hidden");
  overlay.setAttribute("aria-hidden", "true");
}

qs("#payment-cancel").addEventListener("click", () => closePaymentModal());
qs("#payment-overlay").addEventListener("click", (ev) => {
  if (ev.target === qs("#payment-overlay")) closePaymentModal();
});

qs("#payment-form").addEventListener("submit", async (ev) => {
  ev.preventDefault();
  const fd = new FormData(ev.target);
  try {
    const data = await api("api/checkout", {
      method: "POST",
      body: JSON.stringify({ paymentMethod: fd.get("paymentMethod") }),
    });
    closePaymentModal();
    toast("Payment successful");
    loadCart();
    bumpCartBadge();
    loadMedicines();
    if (data.receiptId) {
      await showReceipt(data.receiptId);
    }
  } catch (e) {
    toast(e.message, true);
  }
});

async function showReceipt(receiptId) {
  const data = await api(`api/receipts?id=${encodeURIComponent(receiptId)}`);
  const r = data.receipt;
  const body = qs("#receipt-view-body");
  let linesHtml = "";
  (r.lines || []).forEach((line) => {
    linesHtml += `<tr>
      <td>${escapeHtml(line.medicineName)}</td>
      <td>${line.quantity}</td>
      <td>€${Number(line.unitPrice).toFixed(2)}</td>
      <td>€${Number(line.lineTotal).toFixed(2)}</td>
    </tr>`;
  });
  body.innerHTML = `
    <p class="receipt-number"><strong>${escapeHtml(r.receiptNumber)}</strong></p>
    <p class="small">${escapeHtml(r.customerName)} · ${escapeHtml(r.customerEmail)}</p>
    <p class="small">Paid via ${escapeHtml(r.paymentMethod)} · ${formatDate(r.issuedAt)}</p>
    <table class="data receipt-lines">
      <thead><tr><th>Item</th><th>Qty</th><th>Unit</th><th>Line</th></tr></thead>
      <tbody>${linesHtml}</tbody>
    </table>
    <p class="price-tag" style="margin-top:0.75rem">Total €${Number(r.totalAmount).toFixed(2)}</p>
    ${r.notes ? `<p class="small">Notes: ${escapeHtml(r.notes)}</p>` : ""}
  `;
  const overlay = qs("#receipt-overlay");
  overlay.classList.remove("hidden");
  overlay.setAttribute("aria-hidden", "false");
}

function closeReceiptView() {
  const overlay = qs("#receipt-overlay");
  overlay.classList.add("hidden");
  overlay.setAttribute("aria-hidden", "true");
}

qs("#receipt-close").addEventListener("click", () => closeReceiptView());
qs("#receipt-overlay").addEventListener("click", (ev) => {
  if (ev.target === qs("#receipt-overlay")) closeReceiptView();
});
qs("#receipt-print").addEventListener("click", () => window.print());

function formatDate(iso) {
  if (!iso) return "";
  try {
    return new Date(iso).toLocaleString();
  } catch {
    return iso;
  }
}

async function loadPatientReceipts() {
  const wrap = qs("#patient-receipts-list");
  wrap.innerHTML = "Loading…";
  try {
    const data = await api("api/receipts");
    const list = data.receipts || [];
    if (!list.length) {
      wrap.innerHTML = "<p class='small'>No receipts yet.</p>";
      return;
    }
    wrap.innerHTML = "";
    list.forEach((r) => {
      const row = document.createElement("div");
      row.className = "product receipt-row";
      row.innerHTML = `
        <strong>${escapeHtml(r.receiptNumber)}</strong>
        <div class="product-meta">${formatDate(r.issuedAt)} · ${escapeHtml(r.paymentMethod)}</div>
        <div class="price-tag">€${Number(r.totalAmount).toFixed(2)}</div>
      `;
      const btn = document.createElement("button");
      btn.type = "button";
      btn.className = "btn btn-ghost";
      btn.textContent = "View";
      btn.addEventListener("click", () => showReceipt(r.id));
      row.appendChild(btn);
      wrap.appendChild(row);
    });
  } catch (e) {
    wrap.innerHTML = `<p class="small">${escapeHtml(e.message)}</p>`;
  }
}

async function loadAccountantReceipts() {
  const tbody = qs("#accountant-receipts-body");
  tbody.innerHTML = "<tr><td colspan='6' class='small'>Loading…</td></tr>";
  try {
    const data = await api("api/receipts");
    const list = data.receipts || [];
    tbody.innerHTML = "";
    if (!list.length) {
      tbody.innerHTML =
        "<tr><td colspan='6' class='small'>No receipts yet. A patient must complete checkout first.</td></tr>";
      return;
    }
    list.forEach((r) => {
      const tr = document.createElement("tr");
      tr.innerHTML = `
        <td>${r.id}</td>
        <td>${escapeHtml(r.receiptNumber)}</td>
        <td>${escapeHtml(r.customerName)}<br/><span class="small">${escapeHtml(r.customerEmail)}</span></td>
        <td>€${Number(r.totalAmount).toFixed(2)}</td>
        <td>${formatDate(r.issuedAt)}</td>
        <td class="nowrap-actions"></td>`;
      const cell = tr.querySelector(".nowrap-actions");
      const viewBtn = document.createElement("button");
      viewBtn.type = "button";
      viewBtn.className = "btn btn-ghost";
      viewBtn.textContent = "View";
      viewBtn.addEventListener("click", () => showReceipt(r.id));
      const editBtn = document.createElement("button");
      editBtn.type = "button";
      editBtn.className = "btn btn-ghost";
      editBtn.style.marginLeft = "0.35rem";
      editBtn.textContent = "Edit";
      editBtn.addEventListener("click", () => openReceiptEditor(r));
      const delBtn = document.createElement("button");
      delBtn.type = "button";
      delBtn.className = "btn btn-danger";
      delBtn.style.marginLeft = "0.35rem";
      delBtn.textContent = "Delete";
      delBtn.addEventListener("click", async () => {
        if (!confirm(`Delete receipt ${r.receiptNumber}?`)) return;
        try {
          await api(`api/receipts?id=${encodeURIComponent(r.id)}`, { method: "DELETE" });
          toast("Receipt deleted");
          loadAccountantReceipts();
        } catch (e) {
          toast(e.message, true);
        }
      });
      cell.appendChild(viewBtn);
      cell.appendChild(editBtn);
      cell.appendChild(delBtn);
      tbody.appendChild(tr);
    });
  } catch (e) {
    tbody.innerHTML = `<tr><td colspan="6">${escapeHtml(e.message)}</td></tr>`;
  }
}

function openReceiptEditor(r) {
  const overlay = qs("#receipt-edit-overlay");
  overlay.classList.remove("hidden");
  overlay.setAttribute("aria-hidden", "false");
  const f = qs("#receipt-edit-form");
  f.elements.receiptId.value = r.id;
  f.elements.customerName.value = r.customerName || "";
  f.elements.customerEmail.value = r.customerEmail || "";
  f.elements.totalAmount.value = r.totalAmount;
  f.elements.paymentMethod.value = r.paymentMethod || "Card";
  f.elements.notes.value = r.notes || "";
}

function closeReceiptEditor() {
  const overlay = qs("#receipt-edit-overlay");
  overlay.classList.add("hidden");
  overlay.setAttribute("aria-hidden", "true");
}

qs("#receipt-edit-form").addEventListener("submit", async (ev) => {
  ev.preventDefault();
  const fd = new FormData(ev.target);
  const id = fd.get("receiptId");
  try {
    await api(`api/receipts?id=${encodeURIComponent(id)}`, {
      method: "PUT",
      body: JSON.stringify({
        customerName: fd.get("customerName"),
        customerEmail: fd.get("customerEmail"),
        totalAmount: String(fd.get("totalAmount")),
        paymentMethod: fd.get("paymentMethod"),
        notes: fd.get("notes") || "",
      }),
    });
    toast("Receipt updated");
    closeReceiptEditor();
    loadAccountantReceipts();
  } catch (e) {
    toast(e.message, true);
  }
});

qs("#receipt-edit-cancel").addEventListener("click", () => closeReceiptEditor());
qs("#receipt-edit-overlay").addEventListener("click", (ev) => {
  if (ev.target === qs("#receipt-edit-overlay")) closeReceiptEditor();
});

function attachCatalogCreateForm(form) {
  if (!form) return;
  form.addEventListener("submit", async (ev) => {
    ev.preventDefault();
    const fd = new FormData(ev.target);
    const payload = {
      name: fd.get("name"),
      description: fd.get("description") || "",
      price: fd.get("price"),
      stock: parseInt(fd.get("stock"), 10) || 0,
    };
    const imageFile = form.querySelector('input[type="file"][name="image"]')?.files?.[0];
    try {
      const created = await api("api/medicines", { method: "POST", body: JSON.stringify(payload) });
      if (imageFile && created.id) {
        const up = new FormData();
        up.append("type", "medicine");
        up.append("medicineId", String(created.id));
        up.append("file", imageFile);
        await apiUpload(up);
      }
      toast("Medicine listed");
      ev.target.reset();
      await reloadMedicineManagementTables();
      if (canBrowseStore()) loadMedicines();
    } catch (e) {
      toast(e.message, true);
    }
  });
}

attachCatalogCreateForm(qs("#pharm-form"));

async function loadAdminUsers() {
  const tbody = qs("#admin-users-body");
  tbody.innerHTML = "";
  try {
    const data = await api("api/admin/users");
    (data.users || []).forEach((u) => {
      const tr = document.createElement("tr");
      tr.innerHTML = `
        <td>${u.id}</td>
        <td>${escapeHtml(u.email)}</td>
        <td>${escapeHtml(u.fullName)}</td>
        <td><span class="pill-role">${escapeHtml(u.role)}</span></td>
        <td class="nowrap-actions"></td>`;
      const cell = tr.querySelector(".nowrap-actions");
      const editBtn = document.createElement("button");
      editBtn.type = "button";
      editBtn.className = "btn btn-ghost";
      editBtn.textContent = "Edit";
      editBtn.addEventListener("click", () => openUserEditor(u));
      cell.appendChild(editBtn);
      if (u.id !== currentUser?.id) {
        const delBtn = document.createElement("button");
        delBtn.type = "button";
        delBtn.className = "btn btn-danger";
        delBtn.style.marginLeft = "0.35rem";
        delBtn.textContent = "Delete";
        delBtn.addEventListener("click", async () => {
          if (!confirm(`Delete user #${u.id}?`)) return;
          try {
            await api(`api/admin/users?id=${encodeURIComponent(u.id)}`, { method: "DELETE" });
            toast("User deleted");
            loadAdminUsers();
          } catch (e) {
            toast(e.message, true);
          }
        });
        cell.appendChild(delBtn);
      }
      tbody.appendChild(tr);
    });
  } catch (e) {
    tbody.innerHTML = `<tr><td colspan='5'>${escapeHtml(e.message)}</td></tr>`;
  }
}

function openUserEditor(u) {
  const overlay = qs("#user-edit-overlay");
  overlay.classList.remove("hidden");
  overlay.setAttribute("aria-hidden", "false");
  const f = qs("#user-edit-form");
  f.elements.userId.value = u.id;
  f.elements.email.value = u.email || "";
  f.elements.fullName.value = u.fullName || "";
  f.elements.role.value = String(u.role || "CUSTOMER").toUpperCase();
}

function closeUserEditor() {
  const overlay = qs("#user-edit-overlay");
  overlay.classList.add("hidden");
  overlay.setAttribute("aria-hidden", "true");
}

qs("#user-edit-form").addEventListener("submit", async (ev) => {
  ev.preventDefault();
  const fd = new FormData(ev.target);
  const id = fd.get("userId");
  try {
    const data = await api(`api/admin/users?id=${encodeURIComponent(id)}`, {
      method: "PUT",
      body: JSON.stringify({
        email: fd.get("email"),
        fullName: fd.get("fullName"),
        role: fd.get("role"),
      }),
    });
    toast("User updated");
    closeUserEditor();
    loadAdminUsers();
    if (String(id) === String(currentUser?.id) && data.user) {
      currentUser = normalizeUser(data.user);
      updateGreeting();
      renderNav();
    }
  } catch (e) {
    toast(e.message, true);
  }
});

qs("#user-edit-cancel").addEventListener("click", () => closeUserEditor());
qs("#user-edit-overlay").addEventListener("click", (ev) => {
  if (ev.target === qs("#user-edit-overlay")) closeUserEditor();
});

function openMedEditor(m) {
  editingMedicine = m;
  const overlay = qs("#med-edit-overlay");
  overlay.classList.remove("hidden");
  overlay.setAttribute("aria-hidden", "false");
  const f = qs("#med-edit-form");
  f.elements.medicineId.value = m.id;
  f.elements.name.value = m.name;
  f.elements.description.value = m.description || "";
  f.elements.price.value = m.price;
  f.elements.stock.value = m.stock ?? 0;
  const preview = qs("#med-edit-preview");
  const imgInput = qs("#med-edit-image");
  imgInput.value = "";
  const previewSrc = mediaUrl(m.imageUrl || m.imagePath);
  if (previewSrc) {
    preview.classList.remove("hidden");
    preview.innerHTML = `<img src="${escapeHtml(previewSrc)}" alt="" class="med-preview-img" />`;
  } else {
    preview.classList.add("hidden");
    preview.innerHTML = "";
  }
}

function closeMedEditor() {
  const overlay = qs("#med-edit-overlay");
  overlay.classList.add("hidden");
  overlay.setAttribute("aria-hidden", "true");
}

async function reloadMedicineManagementTables() {
  await loadMedicineManagementTable("#pharm-meds-body");
}

async function loadPharmacistCatalog() {
  await loadMedicineManagementTable("#pharm-meds-body");
}

async function loadMedicineManagementTable(tbodySel) {
  const tbody = qs(tbodySel);
  if (!tbody) return;
  tbody.innerHTML = "";
  try {
    const data = await api("api/medicines");
    const list = data.medicines || [];
    if (!list.length) {
      tbody.innerHTML =
        `<tr><td colspan='6' class='small'>No medicines yet.</td></tr>`;
      return;
    }
    list.forEach((m) => {
      const tr = document.createElement("tr");
      const thumbSrc = mediaUrl(m.imageUrl || m.imagePath);
      const thumb = thumbSrc
        ? `<img class="table-thumb" src="${escapeHtml(thumbSrc)}" alt="" />`
        : `<span class="small">—</span>`;
      tr.innerHTML = `
        <td>${m.id}</td>
        <td>${thumb}</td>
        <td>${escapeHtml(m.name)}</td>
        <td>€${Number(m.price).toFixed(2)}</td>
        <td>${m.stock ?? 0}</td>
        <td class="nowrap-actions"></td>`;
      const cell = tr.querySelector(".nowrap-actions");
      const editBtn = document.createElement("button");
      editBtn.type = "button";
      editBtn.className = "btn btn-ghost";
      editBtn.textContent = "Edit";
      editBtn.addEventListener("click", () => openMedEditor(m));
      const delBtn = document.createElement("button");
      delBtn.type = "button";
      delBtn.className = "btn btn-danger";
      delBtn.style.marginLeft = "0.4rem";
      delBtn.textContent = "Delete";
      delBtn.addEventListener("click", async () => {
        const id = m.id;
        if (!confirm(`Delete medicine #${id}? Removes from everyone's cart.`)) return;
        try {
          await api(`api/medicines?id=${encodeURIComponent(id)}`, { method: "DELETE" });
          toast("Medicine removed");
          await reloadMedicineManagementTables();
          loadMedicines();
        } catch (e) {
          toast(e.message, true);
        }
      });
      cell.appendChild(editBtn);
      cell.appendChild(delBtn);
      tbody.appendChild(tr);
    });
  } catch (e) {
    tbody.innerHTML = `<tr><td colspan='6'>${escapeHtml(e.message)}</td></tr>`;
  }
}

qs("#med-edit-form").addEventListener("submit", async (ev) => {
  ev.preventDefault();
  const fd = new FormData(ev.target);
  const id = fd.get("medicineId");
  try {
    await api(`api/medicines?id=${encodeURIComponent(id)}`, {
      method: "PUT",
      body: JSON.stringify({
        name: fd.get("name"),
        description: fd.get("description") || "",
        price: String(fd.get("price")),
        stock: parseInt(fd.get("stock"), 10) || 0,
      }),
    });
    const imgFile = qs("#med-edit-image").files?.[0];
    if (imgFile) {
      const up = new FormData();
      up.append("type", "medicine");
      up.append("medicineId", String(id));
      up.append("file", imgFile);
      await apiUpload(up);
    }
    toast("Medicine updated");
    closeMedEditor();
    await reloadMedicineManagementTables();
    loadMedicines();
  } catch (e) {
    toast(e.message, true);
  }
});

qs("#med-edit-cancel").addEventListener("click", () => closeMedEditor());

qs("#med-edit-overlay").addEventListener("click", (ev) => {
  if (ev.target === qs("#med-edit-overlay")) closeMedEditor();
});

qs("#login-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  const fd = new FormData(e.target);
  try {
    const data = await api("api/login", {
      method: "POST",
      body: JSON.stringify({
        email: fd.get("email"),
        password: fd.get("password"),
      }),
    });
    currentUser = normalizeUser(data.user);
    toast(`Welcome ${currentUser.fullName}`);
    showApp();
    bumpCartBadge();
    loadMedicines();
  } catch (err) {
    toast(err.message, true);
  }
});

qs("#register-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  const fd = new FormData(e.target);
  try {
    const data = await api("api/register", {
      method: "POST",
      body: JSON.stringify({
        email: fd.get("email"),
        password: fd.get("password"),
        fullName: fd.get("fullName"),
        role: fd.get("role"),
        adminKey: fd.get("adminKey") || "",
      }),
    });
    toast(`Registered — now sign in (${data.role})`);
    if (!qs("#register-form-section").classList.contains("hidden")) {
      qs("#toggle-auth").click();
    }
    qs('#login-form [name="email"]').value = fd.get("email") || "";
  } catch (err) {
    toast(err.message, true);
  }
});

// Toggle admin key input on registration form
const regRole = qs('#register-form select[name="role"]');
const adminKeyRow = qs("#admin-key-row");
if (regRole && adminKeyRow) {
  const sync = () => {
    const isAdmin = regRole.value === "ADMIN";
    adminKeyRow.classList.toggle("hidden", !isAdmin);
    adminKeyRow.querySelector("input").required = isAdmin;
  };
  regRole.addEventListener("change", sync);
  sync();
}

qs("#toggle-auth").addEventListener("click", () => {
  const lg = qs("#login-form-section");
  const rg = qs("#register-form-section");
  const showRegisterNext = rg.classList.contains("hidden");
  lg.classList.toggle("hidden", showRegisterNext);
  rg.classList.toggle("hidden", !showRegisterNext);
  qs("#toggle-auth").textContent = showRegisterNext ? "Back to sign in" : "Switch to register";
});

qs("#account-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  const fd = new FormData(e.target);
  try {
    const data = await api("api/profile", {
      method: "PUT",
      body: JSON.stringify({
        email: fd.get("email"),
        fullName: fd.get("fullName"),
      }),
    });
    currentUser = normalizeUser(data.user);
    toast("Profile saved");
    updateGreeting();
    renderNav();
  } catch (err) {
    toast(err.message, true);
  }
});

qs("#logout-btn").addEventListener("click", async () => {
  await api("api/logout", { method: "POST" });
  currentUser = null;
  toast("Logged out");
  showAuth();
});

qs("#delete-account-btn").addEventListener("click", async () => {
  if (!confirm("Delete your account permanently?")) return;
  try {
    await api("api/profile", { method: "DELETE" });
    currentUser = null;
    toast("Account deleted");
    showAuth();
  } catch (e) {
    toast(e.message, true);
  }
});

function escapeHtml(s) {
  return String(s)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

async function loadPrescriptionPanel() {
  const panel = qs("#prescription-panel");
  if (!panel) return;
  const isCustomer = currentUser?.role === "CUSTOMER";
  panel.classList.toggle("hidden", !isCustomer);
  if (!isCustomer) return;
  const cur = qs("#prescription-current");
  const rmBtn = qs("#prescription-remove-btn");
  try {
    const data = await api("api/prescription");
    const rx = data.prescription;
    if (!rx) {
      cur.textContent = "No prescription on file.";
      rmBtn.classList.add("hidden");
      return;
    }
    const url = mediaUrl(rx.fileUrl || rx.filePath);
    if (rx.contentType === "application/pdf") {
      cur.innerHTML = `On file: <a href="${escapeHtml(url)}" target="_blank" rel="noopener">${escapeHtml(rx.originalName || "prescription.pdf")}</a>`;
    } else {
      cur.innerHTML = `On file: ${escapeHtml(rx.originalName || "image")}<br/><img class="rx-preview" src="${escapeHtml(url)}" alt="Prescription preview" />`;
    }
    rmBtn.classList.remove("hidden");
  } catch (e) {
    cur.textContent = e.message;
    rmBtn.classList.add("hidden");
  }
}

async function uploadPrescriptionFile() {
  const file = qs("#prescription-file")?.files?.[0];
  if (!file) {
    toast("Choose a file first", true);
    return;
  }
  const up = new FormData();
  up.append("file", file);
  const res = await fetch(resolveUrl("api/prescription"), {
    method: "POST",
    credentials: "same-origin",
    body: up,
  });
  const text = await res.text();
  let data = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = {};
  }
  if (!res.ok) {
    throw new Error(data?.error || res.statusText);
  }
  return data;
}

qs("#prescription-upload-btn")?.addEventListener("click", async () => {
  try {
    await uploadPrescriptionFile();
    qs("#prescription-file").value = "";
    toast("Prescription uploaded");
    loadPrescriptionPanel();
  } catch (e) {
    toast(e.message, true);
  }
});

qs("#prescription-remove-btn")?.addEventListener("click", async () => {
  if (!confirm("Remove your prescription file?")) return;
  try {
    await api("api/prescription", { method: "DELETE" });
    toast("Prescription removed");
    loadPrescriptionPanel();
  } catch (e) {
    toast(e.message, true);
  }
});

(async function bootstrap() {
  try {
    if (await refreshMe()) {
      showApp();
      bumpCartBadge();
    } else {
      showAuth();
    }

    qs("#login-form-section").querySelector('[name="email"]')?.focus?.();
    if (currentUser) populateAccount(currentUser);
  } catch {
    toast("Offline or server unreachable", true);
    showAuth();
  }
})();

document.addEventListener("visibilitychange", () => {
  if (document.visibilityState === "visible" && currentUser?.role === "CUSTOMER") {
    bumpCartBadge();
  }
});

function populateAccount(u) {
  const f = qs("#account-form");
  f.elements.email.value = u.email || "";
  f.elements.fullName.value = u.fullName || "";
  const photoRow = qs("#profile-photo-row");
  const avatarWrap = qs("#profile-avatar-wrap");
  const avatar = qs("#profile-avatar");
  const isPatient = u.role === "CUSTOMER";
  photoRow?.classList.toggle("hidden", !isPatient);
  if (isPatient && u.profileImagePath) {
    avatarWrap.classList.remove("hidden");
    avatar.src = mediaUrl(u.profileImagePath);
  } else if (isPatient) {
    avatarWrap.classList.add("hidden");
  } else {
    avatarWrap.classList.add("hidden");
  }
}

const profilePhotoInput = qs("#profile-photo-input");
if (profilePhotoInput) {
  profilePhotoInput.addEventListener("change", async () => {
    const file = profilePhotoInput.files?.[0];
    if (!file || !currentUser) return;
    try {
      const up = new FormData();
      up.append("type", "profile");
      up.append("file", file);
      const data = await apiUpload(up);
      if (data.user) currentUser = data.user;
      populateAccount(currentUser);
      toast("Profile photo updated");
    } catch (e) {
      toast(e.message, true);
    }
    profilePhotoInput.value = "";
  });
}
