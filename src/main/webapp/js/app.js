const qs = (s, el = document) => el.querySelector(s);
const APP = qs("#app-shell");
const AUTH = qs("#auth-panel");
let currentUser = null;

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
  const res = await fetch(path, {
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

async function refreshMe() {
  const data = await api("api/me");
  if (!data.authenticated) {
    currentUser = null;
    return false;
  }
  currentUser = data.user;
  return true;
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
  activateView("shop");
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

  mk("shop", "Store");
  if (currentUser && currentUser.role === "CUSTOMER") {
    const b = document.createElement("button");
    b.type = "button";
    b.dataset.view = "cart";
    b.innerHTML = `Cart <span class="badge" id="cart-badge">0</span>`;
    b.addEventListener("click", () => activateView("cart"));
    tabs.appendChild(b);
  }
  if (currentUser && currentUser.role === "PHARMACIST") {
    mk("pharm", "Stock & catalogue");
  }
  if (currentUser && currentUser.role === "ADMIN") {
    mk("adm-users", "Users");
    mk("adm-meds", "Medicines");
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

  if (view === "shop") loadMedicines();
  if (view === "cart") loadCart();
  if (view === "adm-users") loadAdminUsers();
  if (view === "adm-meds") loadMedicinesAdmin();
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
      el.innerHTML = `
        <h3>${escapeHtml(m.name)}</h3>
        <p class="small">${escapeHtml((m.description || "").slice(0, 160))}${(m.description || "").length > 160 ? "…" : ""}</p>
        <div class="product-meta">${m.stock ?? 0} in stock · ID ${m.id}</div>
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
    qs("#cart-total").textContent =
      `Total €${Number(data.total || 0).toFixed(2)}`;
  } catch (e) {
    body.innerHTML = `<p class='small'>${escapeHtml(e.message)}</p>`;
  }
}

qs("#checkout-btn").addEventListener("click", async () => {
  try {
    const data = await api("api/checkout", { method: "POST" });
    toast(`Paid — order #${data.orderId} · €${Number(data.total).toFixed(2)}`);
    loadCart();
    bumpCartBadge();
    loadMedicines();
  } catch (e) {
    toast(e.message, true);
  }
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
    try {
      await api("api/medicines", { method: "POST", body: JSON.stringify(payload) });
      toast("Medicine listed");
      ev.target.reset();
      await reloadMedicineManagementTables();
      loadMedicines();
    } catch (e) {
      toast(e.message, true);
    }
  });
}

attachCatalogCreateForm(qs("#pharm-form"));
attachCatalogCreateForm(qs("#admin-catalog-form"));

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
        <td><span class="pill-role">${u.role}</span></td>
        <td>${u.id !== currentUser?.id ? `<button type='button' class='btn btn-danger' data-del='${u.id}'>Delete</button>` : "—"}</td>`;
      tbody.appendChild(tr);
    });
    tbody.querySelectorAll("button[data-del]").forEach((b) =>
      b.addEventListener("click", async () => {
        const id = b.getAttribute("data-del");
        if (!confirm(`Delete user #${id}?`)) return;
        try {
          await api(`api/admin/users?id=${encodeURIComponent(id)}`, { method: "DELETE" });
          toast("User deleted");
          loadAdminUsers();
        } catch (e) {
          toast(e.message, true);
        }
      }),
    );
  } catch (e) {
    tbody.innerHTML = `<tr><td colspan='5'>${escapeHtml(e.message)}</td></tr>`;
  }
}

function openMedEditor(m) {
  const overlay = qs("#med-edit-overlay");
  overlay.classList.remove("hidden");
  overlay.setAttribute("aria-hidden", "false");
  const f = qs("#med-edit-form");
  f.elements.medicineId.value = m.id;
  f.elements.name.value = m.name;
  f.elements.description.value = m.description || "";
  f.elements.price.value = m.price;
  f.elements.stock.value = m.stock ?? 0;
}

function closeMedEditor() {
  const overlay = qs("#med-edit-overlay");
  overlay.classList.add("hidden");
  overlay.setAttribute("aria-hidden", "true");
}

async function reloadMedicineManagementTables() {
  await loadMedicineManagementTable("#pharm-meds-body");
  await loadMedicineManagementTable("#admin-meds-body");
}

async function loadPharmacistCatalog() {
  await loadMedicineManagementTable("#pharm-meds-body");
}

async function loadMedicinesAdmin() {
  await loadMedicineManagementTable("#admin-meds-body");
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
        `<tr><td colspan='5' class='small'>No medicines yet.</td></tr>`;
      return;
    }
    list.forEach((m) => {
      const tr = document.createElement("tr");
      tr.innerHTML = `
        <td>${m.id}</td>
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
    tbody.innerHTML = `<tr><td colspan='5'>${escapeHtml(e.message)}</td></tr>`;
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
    currentUser = data.user;
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
    currentUser = data.user;
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

(async function bootstrap() {
  try {
    if (await refreshMe()) {
      showApp();
      bumpCartBadge();
      loadMedicines();
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
}
