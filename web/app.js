import { initializeApp } from "https://www.gstatic.com/firebasejs/11.0.2/firebase-app.js";
import {
  getAuth,
  onAuthStateChanged,
  signInWithCustomToken,
  signOut
} from "https://www.gstatic.com/firebasejs/11.0.2/firebase-auth.js";
import {
  addDoc,
  collection,
  doc,
  getDoc,
  getDocs,
  initializeFirestore,
  onSnapshot,
  query,
  serverTimestamp,
  updateDoc,
  where
} from "https://www.gstatic.com/firebasejs/11.0.2/firebase-firestore.js";
import { renderQrSvg } from "./qrcode.js";

const FIREBASE_CONFIG = {
  apiKey: "AIzaSyAwYCWuAUOM9ewEa_vS5AhtJPlxV51GSo0",
  authDomain: "gen-lang-client-0442156732.firebaseapp.com",
  projectId: "gen-lang-client-0442156732",
  storageBucket: "gen-lang-client-0442156732.firebasestorage.app",
  messagingSenderId: "637837822907"
};

const DATABASE_ID = "ai-studio-android-coleconn-5a5a7572-a279-4b58-bee5-0a53310cb1f0";
const FUNCTIONS_BASE = `https://us-central1-${FIREBASE_CONFIG.projectId}.cloudfunctions.net`;
const STORAGE_SESSION_KEY = "ecole_connectee_device_session_id";

const app = initializeApp(FIREBASE_CONFIG);
const auth = getAuth(app);
const db = initializeFirestore(app, {}, DATABASE_ID);

const qrLoginView = document.getElementById("qr-login-view");
const appShell = document.getElementById("app-shell");
const qrSvgContainer = document.getElementById("qr-svg-container");
const qrCountdownEl = document.getElementById("qr-countdown");
const qrStatusPill = document.getElementById("qr-status-pill");
const qrStatusText = document.getElementById("qr-status-text");
const qrRefreshBtn = document.getElementById("qr-refresh-btn");
const topbarUserMeta = document.getElementById("topbar-user-meta");
const topbarRoleBadge = document.getElementById("topbar-role-badge");
const webSignOutBtn = document.getElementById("web-signout-btn");
const roleSidebar = document.getElementById("role-sidebar");
const dashboardContent = document.getElementById("dashboard-content");

const qrState = {
  requestId: null,
  proofSecret: null,
  expiresAtMs: 0,
  countdownTimer: null,
  pollTimer: null,
  claiming: false
};

const appState = {
  profile: null,
  activeTab: "overview",
  deviceUnsub: null,
  dataUnsubs: [],
  data: {
    schools: [],
    classes: [],
    subjects: [],
    schedules: [],
    students: [],
    lessons: [],
    assessments: [],
    attendance: [],
    teacherAttendance: [],
    difficulties: [],
    reportCards: [],
    announcements: [],
    messages: [],
    notifications: [],
    accessRequests: [],
    users: [],
    auditLogs: [],
    regions: []
  }
};

function esc(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

function roleLabel(role) {
  switch (role) {
    case "admin_national": return "Administration Nationale";
    case "admin_regional": return "Administration Régionale";
    case "directeur": return "Direction d'Établissement";
    case "enseignant": return "Espace Enseignant";
    case "parent": return "Espace Parent";
    case "eleve": return "Espace Élève";
    default: return role || "Utilisateur";
  }
}

function setQrStatus(type, message) {
  qrStatusPill.className = `status-pill status-${type}`;
  qrStatusText.textContent = message;
}

function stopQrTimers() {
  if (qrState.countdownTimer) clearInterval(qrState.countdownTimer);
  if (qrState.pollTimer) clearInterval(qrState.pollTimer);
  qrState.countdownTimer = null;
  qrState.pollTimer = null;
}

async function startQrPairing() {
  stopQrTimers();
  qrState.claiming = false;
  qrState.requestId = null;
  qrState.proofSecret = null;
  qrRefreshBtn.classList.add("hidden");
  setQrStatus("pending", "Génération d'une session QR sécurisée...");

  try {
    const resp = await fetch(`${FUNCTIONS_BASE}/createQrPairing`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ userAgentLabel: navigator.userAgent })
    });
    if (!resp.ok) throw new Error("Service d'association indisponible.");
    const payload = await resp.json();

    qrState.requestId = payload.requestId;
    qrState.proofSecret = payload.proofSecret;
    qrState.expiresAtMs = payload.expiresAtMs || (Date.now() + 120_000);

    // IMPORTANT: QR Code contains ONLY the opaque requestId (zero secret/token).
    qrSvgContainer.innerHTML = renderQrSvg(qrState.requestId, 232);
    setQrStatus("pending", "Connexion en attente de confirmation sur Android...");

    const tickCountdown = () => {
      const remaining = Math.max(0, Math.ceil((qrState.expiresAtMs - Date.now()) / 1000));
      qrCountdownEl.textContent = `${remaining} s`;
      if (remaining <= 0) {
        stopQrTimers();
        setQrStatus("error", "QR Code expiré après 120 secondes.");
        qrRefreshBtn.classList.remove("hidden");
      }
    };
    tickCountdown();
    qrState.countdownTimer = setInterval(tickCountdown, 1000);
    qrState.pollTimer = setInterval(pollQrPairingStatus, 2000);
  } catch (err) {
    setQrStatus("error", err.message || "Impossible de créer la demande QR.");
    qrRefreshBtn.classList.remove("hidden");
  }
}

async function pollQrPairingStatus() {
  if (!qrState.requestId || !qrState.proofSecret || qrState.claiming) return;
  try {
    const resp = await fetch(`${FUNCTIONS_BASE}/getQrPairingStatus`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-QR-Proof": qrState.proofSecret
      },
      body: JSON.stringify({ requestId: qrState.requestId })
    });
    if (!resp.ok) return;
    const data = await resp.json();

    if (data.status === "approved") {
      qrState.claiming = true;
      stopQrTimers();
      setQrStatus("success", "Confirmé sur Android. Ouverture de votre espace...");
      await claimAndSignIn();
    } else if (data.status === "rejected") {
      stopQrTimers();
      setQrStatus("error", "Demande refusée depuis votre appareil Android.");
      qrRefreshBtn.classList.remove("hidden");
    } else if (data.status === "expired") {
      stopQrTimers();
      setQrStatus("error", "QR Code expiré.");
      qrRefreshBtn.classList.remove("hidden");
    }
  } catch (_) {
    // Transient polling failure ignored while countdown remains active
  }
}

async function claimAndSignIn() {
  try {
    const resp = await fetch(`${FUNCTIONS_BASE}/claimQrToken`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        requestId: qrState.requestId,
        proofSecret: qrState.proofSecret
      })
    });
    const data = await resp.json();
    if (!resp.ok || !data.customToken) {
      throw new Error(data.error || "Échec de vérification de preuve.");
    }
    sessionStorage.setItem(STORAGE_SESSION_KEY, data.sessionId);
    qrState.proofSecret = null;
    qrState.requestId = null;
    await signInWithCustomToken(auth, data.customToken);
  } catch (err) {
    qrState.claiming = false;
    setQrStatus("error", err.message || "Erreur d'authentification Web.");
    qrRefreshBtn.classList.remove("hidden");
  }
}

qrRefreshBtn.addEventListener("click", () => startQrPairing());

function teardownSubscriptions() {
  if (appState.deviceUnsub) appState.deviceUnsub();
  appState.deviceUnsub = null;
  appState.dataUnsubs.forEach((unsub) => unsub());
  appState.dataUnsubs = [];
}

async function forceRevokedLogout(reasonMessage) {
  teardownSubscriptions();
  sessionStorage.removeItem(STORAGE_SESSION_KEY);
  try {
    await signOut(auth);
  } catch (_) {}
  appShell.classList.add("hidden");
  qrLoginView.classList.remove("hidden");
  await startQrPairing();
  if (reasonMessage) {
    setQrStatus("error", reasonMessage);
    qrRefreshBtn.classList.remove("hidden");
  }
}

webSignOutBtn.addEventListener("click", async () => {
  const user = auth.currentUser;
  const sessionId = sessionStorage.getItem(STORAGE_SESSION_KEY);
  if (user && sessionId) {
    try {
      await updateDoc(doc(db, "connected_devices", sessionId), {
        revoked: true,
        revokedAt: serverTimestamp(),
        revokedByUid: user.uid,
        updatedAt: serverTimestamp()
      });
    } catch (_) {}
  }
  await forceRevokedLogout("Déconnecté avec succès.");
});

function watchCollection(name, q, targetKey) {
  const unsub = onSnapshot(
    q,
    (snap) => {
      appState.data[targetKey] = snap.docs.map((d) => ({ id: d.id, ...d.data() }));
      renderDashboard();
    },
    (err) => {
      if (err?.code === "permission-denied") {
        forceRevokedLogout("Session révoquée ou accès refusé côté serveur.");
      }
    }
  );
  appState.dataUnsubs.push(unsub);
}

function subscribeRoleData(profile) {
  teardownSubscriptions();
  const uid = profile.userId;
  const role = profile.role;

  const sessionId = sessionStorage.getItem(STORAGE_SESSION_KEY);
  if (sessionId) {
    appState.deviceUnsub = onSnapshot(
      doc(db, "connected_devices", sessionId),
      (snap) => {
        if (!snap.exists() || snap.data().revoked === true) {
          forceRevokedLogout("Session déconnectée à distance depuis votre application Android.");
        }
      },
      () => {
        forceRevokedLogout("Session ordinateur révoquée côté serveur.");
      }
    );
  }

  const scopedQuery = (col) => {
    const base = collection(db, col);
    if (role === "admin_national") return base;
    if (role === "admin_regional") return query(base, where("regionalAdminUid", "==", uid));
    if (role === "directeur") return query(base, where("directorUid", "==", uid));
    if (role === "enseignant") return query(base, where("teacherUid", "==", uid));
    if (role === "parent") return query(base, where("parentUids", "array-contains", uid));
    return query(base, where("authorizedUids", "array-contains", uid));
  };

  if (role === "parent") {
    watchCollection("students", query(collection(db, "students"), where("parentUids", "array-contains", uid)), "students");
    watchCollection("lessons", query(collection(db, "lessons"), where("authorizedUids", "array-contains", uid)), "lessons");
    watchCollection("schedules", query(collection(db, "schedules"), where("authorizedUids", "array-contains", uid)), "schedules");
    watchCollection("assessments", scopedQuery("assessments"), "assessments");
    watchCollection("attendance", scopedQuery("attendance"), "attendance");
    watchCollection("difficulties", scopedQuery("difficulties"), "difficulties");
    watchCollection("report_cards", query(collection(db, "report_cards"), where("parentUids", "array-contains", uid), where("published", "==", true)), "reportCards");
    watchCollection("announcements", query(collection(db, "announcements"), where("authorizedUids", "array-contains", uid)), "announcements");
  } else if (role === "enseignant") {
    watchCollection("classes", query(collection(db, "classes"), where("authorizedUids", "array-contains", uid)), "classes");
    watchCollection("subjects", query(collection(db, "subjects"), where("authorizedUids", "array-contains", uid)), "subjects");
    watchCollection("schedules", query(collection(db, "schedules"), where("authorizedUids", "array-contains", uid)), "schedules");
    watchCollection("students", query(collection(db, "students"), where("authorizedUids", "array-contains", uid)), "students");
    watchCollection("lessons", scopedQuery("lessons"), "lessons");
    watchCollection("assessments", scopedQuery("assessments"), "assessments");
    watchCollection("attendance", scopedQuery("attendance"), "attendance");
    watchCollection("teacher_attendance", query(collection(db, "teacher_attendance"), where("teacherUid", "==", uid)), "teacherAttendance");
    watchCollection("difficulties", scopedQuery("difficulties"), "difficulties");
    watchCollection("announcements", query(collection(db, "announcements"), where("authorizedUids", "array-contains", uid)), "announcements");
  } else if (role === "directeur") {
    watchCollection("schools", query(collection(db, "schools"), where("directorUid", "==", uid)), "schools");
    watchCollection("classes", scopedQuery("classes"), "classes");
    watchCollection("subjects", scopedQuery("subjects"), "subjects");
    watchCollection("schedules", scopedQuery("schedules"), "schedules");
    watchCollection("students", scopedQuery("students"), "students");
    watchCollection("lessons", scopedQuery("lessons"), "lessons");
    watchCollection("assessments", scopedQuery("assessments"), "assessments");
    watchCollection("attendance", scopedQuery("attendance"), "attendance");
    watchCollection("teacher_attendance", scopedQuery("teacher_attendance"), "teacherAttendance");
    watchCollection("difficulties", scopedQuery("difficulties"), "difficulties");
    watchCollection("report_cards", scopedQuery("report_cards"), "reportCards");
    watchCollection("users", query(collection(db, "users"), where("directorUid", "==", uid)), "users");
    if (profile.schoolId) {
      watchCollection(
        "access_requests",
        query(collection(db, "access_requests"), where("schoolId", "==", profile.schoolId), where("requestedRole", "in", ["parent", "enseignant", "eleve"])),
        "accessRequests"
      );
    }
    watchCollection("announcements", query(collection(db, "announcements"), where("directorUid", "==", uid)), "announcements");
  } else {
    // admin_national (or regional supervision)
    watchCollection("regions", scopedQuery("regions"), "regions");
    watchCollection("schools", scopedQuery("schools"), "schools");
    watchCollection("classes", scopedQuery("classes"), "classes");
    watchCollection("subjects", scopedQuery("subjects"), "subjects");
    watchCollection("students", scopedQuery("students"), "students");
    watchCollection("assessments", scopedQuery("assessments"), "assessments");
    watchCollection("attendance", scopedQuery("attendance"), "attendance");
    watchCollection("teacher_attendance", scopedQuery("teacher_attendance"), "teacherAttendance");
    watchCollection("users", role === "admin_national" ? collection(db, "users") : query(collection(db, "users"), where("regionalAdminUid", "==", uid)), "users");
    if (role === "admin_national") {
      watchCollection("access_requests", collection(db, "access_requests"), "accessRequests");
      watchCollection("audit_logs", collection(db, "audit_logs"), "auditLogs");
    }
    watchCollection("announcements", role === "admin_national" ? collection(db, "announcements") : query(collection(db, "announcements"), where("authorizedUids", "array-contains", uid)), "announcements");
  }

  watchCollection("messages", query(collection(db, "messages"), where("participants", "array-contains", uid)), "messages");
  watchCollection("notifications", query(collection(db, "notifications"), where("recipientUid", "==", uid)), "notifications");
}

function tabsForRole(role) {
  switch (role) {
    case "parent":
      return [
        { id: "overview", label: "Mes enfants" },
        { id: "grades", label: "Notes & Bulletins PDF" },
        { id: "attendance", label: "Absences & Alertes" },
        { id: "lessons", label: "Devoirs & Emploi du temps" },
        { id: "comms", label: "Messagerie & Annonces" }
      ];
    case "enseignant":
      return [
        { id: "overview", label: "Mes classes & Élèves" },
        { id: "grades", label: "Saisie des notes" },
        { id: "attendance", label: "Présences & Observations" },
        { id: "lessons", label: "Cahier de texte & EDT" },
        { id: "comms", label: "Messagerie & Annonces" }
      ];
    case "directeur":
      return [
        { id: "overview", label: "Mon établissement" },
        { id: "approvals", label: "Validations comptes" },
        { id: "students", label: "Élèves, Classes & CSV" },
        { id: "teachers", label: "Enseignants & Présences" },
        { id: "comms", label: "Annonces & Messagerie" }
      ];
    default:
      return [
        { id: "overview", label: "Supervision nationale" },
        { id: "approvals", label: "Validations directions" },
        { id: "schools", label: "Établissements & Export" },
        { id: "audit", label: "Journal d'audit" },
        { id: "comms", label: "Annonces nationales" }
      ];
  }
}

function renderSidebar(role) {
  const tabs = tabsForRole(role);
  if (!tabs.some((t) => t.id === appState.activeTab)) {
    appState.activeTab = tabs[0].id;
  }
  roleSidebar.innerHTML = tabs
    .map(
      (t) =>
        `<button class="nav-item ${appState.activeTab === t.id ? "active" : ""}" data-tab="${t.id}">${esc(t.label)}</button>`
    )
    .join("");
  roleSidebar.querySelectorAll(".nav-item").forEach((btn) => {
    btn.addEventListener("click", () => {
      appState.activeTab = btn.dataset.tab;
      renderSidebar(role);
      renderDashboard();
    });
  });
}

function downloadCsv(filename, headers, rows) {
  const escapeCell = (v) => {
    const s = String(v ?? "").replace(/\r|\n/g, " ");
    return s.includes(",") || s.includes('"') ? `"${s.replaceAll('"', '""')}"` : s;
  };
  const csv = [headers.join(","), ...rows.map((r) => r.map(escapeCell).join(","))].join("\n");
  const blob = new Blob([csv], { type: "text/csv;charset=utf-8;" });
  const a = document.createElement("a");
  a.href = URL.createObjectURL(blob);
  a.download = filename;
  a.click();
}

function renderDashboard() {
  const p = appState.profile;
  if (!p) return;
  const d = appState.data;
  const tab = appState.activeTab;

  if (p.role === "parent") {
    renderParentDashboard(tab, d);
  } else if (p.role === "enseignant") {
    renderTeacherDashboard(tab, p, d);
  } else if (p.role === "directeur") {
    renderDirectorDashboard(tab, p, d);
  } else {
    renderNationalDashboard(tab, p, d);
  }
}

function renderParentDashboard(tab, d) {
  if (tab === "overview") {
    dashboardContent.innerHTML = `
      <div class="page-header"><h2 class="page-title">Espace Parent — Mes enfants</h2></div>
      <div class="kpi-grid">
        <div class="kpi-card"><div class="kpi-label">Enfants associés</div><div class="kpi-value">${d.students.length}</div></div>
        <div class="kpi-card"><div class="kpi-label">Notes publiées</div><div class="kpi-value">${d.assessments.length}</div></div>
        <div class="kpi-card"><div class="kpi-label">Absences</div><div class="kpi-value">${d.attendance.filter((a) => a.status === "absent").length}</div></div>
        <div class="kpi-card"><div class="kpi-label">Devoirs / Leçons</div><div class="kpi-value">${d.lessons.length}</div></div>
      </div>
      <div class="panel-card">
        <h3>Informations scolaires de mes enfants</h3>
        <div class="table-wrap"><table>
          <thead><tr><th>Élève</th><th>Matricule</th><th>Établissement</th><th>Classe</th><th>Année</th></tr></thead>
          <tbody>${d.students.map((s) => `<tr><td>${esc(s.firstName)} ${esc(s.lastName)}</td><td><span class="badge">${esc(s.matricule)}</span></td><td>${esc(s.schoolName)}</td><td>${esc(s.className)}</td><td>${esc(s.academicYear || "2026-2027")}</td></tr>`).join("") || "<tr><td colspan='5'>Aucun enfant associé à ce compte.</td></tr>"}</tbody>
        </table></div>
      </div>`;
    return;
  }
  if (tab === "grades") {
    dashboardContent.innerHTML = `
      <div class="page-header"><h2 class="page-title">Notes & Bulletins scolaires</h2></div>
      <div class="panel-card">
        <h3>Bulletins publiés (impression PDF)</h3>
        ${d.reportCards.map((rc) => `<div style="display:flex;justify-content:space-between;align-items:center;padding:10px 0;border-bottom:1px solid var(--border);">
          <div><strong>${esc(rc.studentName)}</strong> • ${esc(rc.period)} • Moyenne : <strong>${Number(rc.overallAverage || 0).toFixed(2)}/20</strong></div>
          <button class="btn-outline print-rc-btn" data-id="${esc(rc.id)}">Télécharger / Imprimer PDF</button>
        </div>`).join("") || "<p>Aucun bulletin publié.</p>"}
      </div>
      <div class="panel-card">
        <h3>Notes détaillées</h3>
        <div class="table-wrap"><table>
          <thead><tr><th>Élève</th><th>Matière</th><th>Évaluation</th><th>Note</th><th>Coeff</th></tr></thead>
          <tbody>${d.assessments.map((g) => `<tr><td>${esc(g.studentName)}</td><td>${esc(g.subjectName)}</td><td>${esc(g.title)}</td><td><strong>${g.score}/${g.maxScore}</strong></td><td>${g.coefficient}</td></tr>`).join("") || "<tr><td colspan='5'>Aucune note enregistrée.</td></tr>"}</tbody>
        </table></div>
      </div>`;
    dashboardContent.querySelectorAll(".print-rc-btn").forEach((btn) => {
      btn.addEventListener("click", () => window.print());
    });
    return;
  }
  if (tab === "attendance") {
    dashboardContent.innerHTML = `
      <div class="page-header"><h2 class="page-title">Absences, Retards & Observations</h2></div>
      <div class="panel-card"><h3>Absences & Retards</h3><div class="table-wrap"><table>
        <thead><tr><th>Date</th><th>Élève</th><th>Statut</th><th>Motif</th></tr></thead>
        <tbody>${d.attendance.map((a) => `<tr><td>${esc(a.date)}</td><td>${esc(a.studentName)}</td><td><span class="badge ${a.status === "absent" ? "badge-danger" : ""}">${esc(a.status)}</span></td><td>${esc(a.reason)}</td></tr>`).join("") || "<tr><td colspan='4'>Aucune absence.</td></tr>"}</tbody>
      </table></div></div>
      <div class="panel-card"><h3>Observations des enseignants</h3>
        ${d.difficulties.map((al) => `<p style="padding:8px 0;border-bottom:1px solid var(--border);"><strong>${esc(al.studentName)}</strong> (${esc(al.severity)}) : ${esc(al.observation)}</p>`).join("") || "<p>Aucune observation.</p>"}
      </div>`;
    return;
  }
  if (tab === "lessons") {
    dashboardContent.innerHTML = `
      <div class="page-header"><h2 class="page-title">Devoirs, Leçons & Emploi du temps</h2></div>
      <div class="panel-card"><h3>Emploi du temps</h3><div class="table-wrap"><table>
        <thead><tr><th>Jour</th><th>Horaire</th><th>Matière</th><th>Classe</th><th>Salle</th></tr></thead>
        <tbody>${d.schedules.map((sc) => `<tr><td>${esc(sc.dayOfWeek)}</td><td>${esc(sc.startTime)} - ${esc(sc.endTime)}</td><td>${esc(sc.subjectName)}</td><td>${esc(sc.className)}</td><td>${esc(sc.room)}</td></tr>`).join("") || "<tr><td colspan='5'>Aucun créneau.</td></tr>"}</tbody>
      </table></div></div>
      <div class="panel-card"><h3>Leçons & Devoirs</h3>
        ${d.lessons.map((l) => `<div style="padding:10px 0;border-bottom:1px solid var(--border);"><strong>${esc(l.subjectName)} — ${esc(l.title)}</strong> (${esc(l.lessonDate)})<br/><small>${esc(l.summary)}</small>${l.homework ? `<div class="badge" style="margin-top:6px;">Devoir : ${esc(l.homework)}</div>` : ""}</div>`).join("") || "<p>Aucune leçon.</p>"}
      </div>`;
    return;
  }
  renderCommsSection(appState.profile, d);
}

function renderTeacherDashboard(tab, p, d) {
  if (tab === "overview") {
    dashboardContent.innerHTML = `
      <div class="page-header"><h2 class="page-title">Espace Enseignant</h2></div>
      <div class="kpi-grid">
        <div class="kpi-card"><div class="kpi-label">Mes classes</div><div class="kpi-value">${d.classes.length}</div></div>
        <div class="kpi-card"><div class="kpi-label">Mes élèves</div><div class="kpi-value">${d.students.length}</div></div>
        <div class="kpi-card"><div class="kpi-label">Leçons publiées</div><div class="kpi-value">${d.lessons.length}</div></div>
        <div class="kpi-card"><div class="kpi-label">Notes saisies</div><div class="kpi-value">${d.assessments.length}</div></div>
      </div>
      <div class="panel-card"><h3>Mes élèves affectés</h3><div class="table-wrap"><table>
        <thead><tr><th>Élève</th><th>Classe</th><th>Matricule</th><th>Établissement</th></tr></thead>
        <tbody>${d.students.map((s) => `<tr><td>${esc(s.firstName)} ${esc(s.lastName)}</td><td>${esc(s.className)}</td><td>${esc(s.matricule)}</td><td>${esc(s.schoolName)}</td></tr>`).join("") || "<tr><td colspan='4'>Aucun élève affecté.</td></tr>"}</tbody>
      </table></div></div>`;
    return;
  }
  if (tab === "grades") {
    dashboardContent.innerHTML = `
      <div class="page-header"><h2 class="page-title">Saisie et consultation des notes</h2></div>
      <div class="panel-card"><h3>Enregistrer une note</h3>
        <form id="teacher-grade-form" class="form-grid">
          <select name="studentId" required>${d.students.map((s) => `<option value="${esc(s.id)}">${esc(s.firstName)} ${esc(s.lastName)} (${esc(s.className)})</option>`).join("")}</select>
          <select name="subjectId" required>${d.subjects.map((sub) => `<option value="${esc(sub.id)}">${esc(sub.name)}</option>`).join("")}</select>
          <input name="title" placeholder="Titre du devoir *" required />
          <input name="score" type="number" step="0.25" min="0" max="20" placeholder="Note / 20 *" required />
          <button class="btn-primary" type="submit">Enregistrer la note</button>
        </form>
      </div>
      <div class="panel-card"><h3>Notes récentes</h3><div class="table-wrap"><table>
        <thead><tr><th>Élève</th><th>Matière</th><th>Devoir</th><th>Note</th></tr></thead>
        <tbody>${d.assessments.map((g) => `<tr><td>${esc(g.studentName)}</td><td>${esc(g.subjectName)}</td><td>${esc(g.title)}</td><td><strong>${g.score}/${g.maxScore}</strong></td></tr>`).join("") || "<tr><td colspan='4'>Aucune note.</td></tr>"}</tbody>
      </table></div></div>`;
    document.getElementById("teacher-grade-form")?.addEventListener("submit", async (e) => {
      e.preventDefault();
      const fd = new FormData(e.target);
      const stu = d.students.find((s) => s.id === fd.get("studentId"));
      const sub = d.subjects.find((x) => x.id === fd.get("subjectId"));
      if (!stu || !sub) return;
      await addDoc(collection(db, "assessments"), {
        schoolId: stu.schoolId,
        tenantId: stu.tenantId || stu.schoolId,
        classId: stu.classId,
        className: stu.className,
        subjectId: sub.id,
        subjectName: sub.name,
        studentId: stu.id,
        studentName: `${stu.firstName} ${stu.lastName}`.trim(),
        parentUids: stu.parentUids || [],
        gradesVisibleToStudent: stu.gradesVisibleToStudent !== false,
        teacherUid: p.userId,
        teacherName: p.fullName,
        directorUid: stu.directorUid || p.directorUid || "",
        authorizedUids: Array.from(new Set([...(stu.authorizedUids || []), p.userId, ...(stu.parentUids || [])])).filter(Boolean),
        title: String(fd.get("title")).trim(),
        assessmentType: "devoir",
        score: Number(fd.get("score")),
        maxScore: 20,
        coefficient: sub.coefficient || 1,
        period: "Trimestre 1",
        date: new Date().toISOString().slice(0, 10),
        comment: "",
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp()
      });
      e.target.reset();
    });
    return;
  }
  if (tab === "attendance") {
    dashboardContent.innerHTML = `
      <div class="page-header"><h2 class="page-title">Présences & Observations</h2></div>
      <div class="panel-card"><h3>Absences élèves enregistrées</h3><div class="table-wrap"><table>
        <thead><tr><th>Date</th><th>Élève</th><th>Statut</th><th>Motif</th></tr></thead>
        <tbody>${d.attendance.map((a) => `<tr><td>${esc(a.date)}</td><td>${esc(a.studentName)}</td><td>${esc(a.status)}</td><td>${esc(a.reason)}</td></tr>`).join("") || "<tr><td colspan='4'>Aucune donnée.</td></tr>"}</tbody>
      </table></div></div>`;
    return;
  }
  if (tab === "lessons") {
    dashboardContent.innerHTML = `
      <div class="page-header"><h2 class="page-title">Cahier de texte & Emploi du temps</h2></div>
      <div class="panel-card"><h3>Emploi du temps</h3><div class="table-wrap"><table>
        <thead><tr><th>Jour</th><th>Horaire</th><th>Matière</th><th>Classe</th></tr></thead>
        <tbody>${d.schedules.map((sc) => `<tr><td>${esc(sc.dayOfWeek)}</td><td>${esc(sc.startTime)} - ${esc(sc.endTime)}</td><td>${esc(sc.subjectName)}</td><td>${esc(sc.className)}</td></tr>`).join("") || "<tr><td colspan='4'>Aucun créneau.</td></tr>"}</tbody>
      </table></div></div>`;
    return;
  }
  renderCommsSection(p, d);
}

function renderDirectorDashboard(tab, p, d) {
  if (tab === "overview") {
    const pendingCount = d.accessRequests.filter((r) => r.status === "pending").length;
    dashboardContent.innerHTML = `
      <div class="page-header"><h2 class="page-title">Direction — ${esc(p.schoolName || "Mon établissement")}</h2></div>
      <div class="kpi-grid">
        <div class="kpi-card"><div class="kpi-label">Classes</div><div class="kpi-value">${d.classes.length}</div></div>
        <div class="kpi-card"><div class="kpi-label">Élèves inscrits</div><div class="kpi-value">${d.students.length}</div></div>
        <div class="kpi-card"><div class="kpi-label">Matières</div><div class="kpi-value">${d.subjects.length}</div></div>
        <div class="kpi-card"><div class="kpi-label">Demandes en attente</div><div class="kpi-value">${pendingCount}</div></div>
      </div>`;
    return;
  }
  if (tab === "approvals") {
    renderApprovalsSection(p, d.accessRequests, d.schools[0]);
    return;
  }
  if (tab === "students") {
    dashboardContent.innerHTML = `
      <div class="page-header">
        <h2 class="page-title">Élèves & Classes</h2>
        <button id="dir-export-csv-btn" class="btn-outline">Exporter les élèves (CSV)</button>
      </div>
      <div class="panel-card"><h3>Élèves de l'établissement</h3><div class="table-wrap"><table>
        <thead><tr><th>Nom complet</th><th>Matricule</th><th>Classe</th><th>Responsable légal</th></tr></thead>
        <tbody>${d.students.map((s) => `<tr><td>${esc(s.firstName)} ${esc(s.lastName)}</td><td>${esc(s.matricule)}</td><td>${esc(s.className)}</td><td>${esc(s.legalGuardianName)} ${esc(s.legalGuardianPhone)}</td></tr>`).join("") || "<tr><td colspan='4'>Aucun élève.</td></tr>"}</tbody>
      </table></div></div>`;
    document.getElementById("dir-export-csv-btn")?.addEventListener("click", () => {
      downloadCsv(
        "eleves_etablissement.csv",
        ["matricule", "nom", "prenom", "classe", "annee_scolaire", "responsable", "telephone"],
        d.students.map((s) => [s.matricule, s.lastName, s.firstName, s.className, s.academicYear, s.legalGuardianName, s.legalGuardianPhone])
      );
    });
    return;
  }
  if (tab === "teachers") {
    const teachers = d.users.filter((u) => u.role === "enseignant");
    dashboardContent.innerHTML = `
      <div class="page-header"><h2 class="page-title">Enseignants & Présences</h2></div>
      <div class="panel-card"><h3>Équipe enseignante (${teachers.length})</h3><div class="table-wrap"><table>
        <thead><tr><th>Nom</th><th>Statut</th></tr></thead>
        <tbody>${teachers.map((t) => `<tr><td>${esc(t.fullName)}</td><td><span class="badge ${t.isActive ? "badge-success" : "badge-danger"}">${t.isActive ? "Actif" : "Inactif"}</span></td></tr>`).join("") || "<tr><td colspan='2'>Aucun enseignant.</td></tr>"}</tbody>
      </table></div></div>
      <div class="panel-card"><h3>Relevés de présence enseignants</h3><div class="table-wrap"><table>
        <thead><tr><th>Date</th><th>Enseignant</th><th>Statut</th><th>Motif</th></tr></thead>
        <tbody>${d.teacherAttendance.map((ta) => `<tr><td>${esc(ta.date)}</td><td>${esc(ta.teacherName)}</td><td>${esc(ta.status)}</td><td>${esc(ta.reason)}</td></tr>`).join("") || "<tr><td colspan='4'>Aucun relevé.</td></tr>"}</tbody>
      </table></div></div>`;
    return;
  }
  renderCommsSection(p, d);
}

function renderNationalDashboard(tab, p, d) {
  if (tab === "overview") {
    const pendingCount = d.accessRequests.filter((r) => r.status === "pending").length;
    dashboardContent.innerHTML = `
      <div class="page-header"><h2 class="page-title">Administration Nationale — ÉCOLE CONNECTÉE</h2></div>
      <div class="kpi-grid">
        <div class="kpi-card"><div class="kpi-label">Établissements</div><div class="kpi-value">${d.schools.length}</div></div>
        <div class="kpi-card"><div class="kpi-label">Classes</div><div class="kpi-value">${d.classes.length}</div></div>
        <div class="kpi-card"><div class="kpi-label">Élèves</div><div class="kpi-value">${d.students.length}</div></div>
        <div class="kpi-card"><div class="kpi-label">Validations en attente</div><div class="kpi-value">${pendingCount}</div></div>
      </div>`;
    return;
  }
  if (tab === "approvals") {
    renderApprovalsSection(p, d.accessRequests, null);
    return;
  }
  if (tab === "schools") {
    dashboardContent.innerHTML = `
      <div class="page-header">
        <h2 class="page-title">Établissements enregistrés</h2>
        <button id="nat-export-csv-btn" class="btn-outline">Exporter rapport national (CSV)</button>
      </div>
      <div class="panel-card"><div class="table-wrap"><table>
        <thead><tr><th>Nom</th><th>Code</th><th>Type</th><th>Téléphone / E-mail</th><th>Classes</th><th>Élèves</th></tr></thead>
        <tbody>${d.schools.map((sc) => {
          const cc = d.classes.filter((c) => c.schoolId === sc.id).length;
          const stc = d.students.filter((st) => st.schoolId === sc.id).length;
          return `<tr><td>${esc(sc.name)}</td><td>${esc(sc.code)}</td><td>${esc(sc.schoolType)}</td><td>${esc([sc.phone, sc.email].filter(Boolean).join(" • "))}</td><td>${cc}</td><td>${stc}</td></tr>`;
        }).join("") || "<tr><td colspan='6'>Aucun établissement.</td></tr>"}</tbody>
      </table></div></div>`;
    document.getElementById("nat-export-csv-btn")?.addEventListener("click", () => {
      downloadCsv(
        "rapport_national_ecole_connectee.csv",
        ["code", "etablissement", "type", "region", "directeur", "classes", "eleves"],
        d.schools.map((sc) => [
          sc.code, sc.name, sc.schoolType, sc.regionName, sc.directorName,
          d.classes.filter((c) => c.schoolId === sc.id).length,
          d.students.filter((st) => st.schoolId === sc.id).length
        ])
      );
    });
    return;
  }
  if (tab === "audit") {
    dashboardContent.innerHTML = `
      <div class="page-header"><h2 class="page-title">Journal d'audit de sécurité</h2></div>
      <div class="panel-card"><div class="table-wrap"><table>
        <thead><tr><th>Acteur</th><th>Action</th><th>Cible</th><th>Détails</th></tr></thead>
        <tbody>${d.auditLogs.slice(0, 50).map((log) => `<tr><td>${esc(log.actorName)}</td><td><span class="badge">${esc(log.action)}</span></td><td>${esc(log.targetType)}</td><td>${esc(log.details)}</td></tr>`).join("") || "<tr><td colspan='4'>Aucune entrée d'audit.</td></tr>"}</tbody>
      </table></div></div>`;
    return;
  }
  renderCommsSection(p, d);
}

function renderApprovalsSection(actorProfile, requests, defaultSchool) {
  const actionable = requests.filter((r) => r.status === "pending" || r.status === "info_requested");
  dashboardContent.innerHTML = `
    <div class="page-header"><h2 class="page-title">Validation des comptes (${actionable.length})</h2></div>
    <div class="panel-card">
      ${actionable.map((r) => `
        <div style="display:flex;justify-content:space-between;align-items:center;gap:12px;padding:12px 0;border-bottom:1px solid var(--border);flex-wrap:wrap;">
          <div>
            <strong>${esc(r.fullName)}</strong> <span class="badge">${esc(roleLabel(r.requestedRole))}</span><br/>
            <small>${esc(r.email)} • ${esc(r.schoolName || r.schoolHint || "Établissement")}</small>
          </div>
          <div style="display:flex;gap:8px;">
            <button class="btn-outline reject-req-btn" data-uid="${esc(r.userId)}">Refuser</button>
            <button class="btn-primary approve-req-btn" data-uid="${esc(r.userId)}">Approuver</button>
          </div>
        </div>
      `).join("") || "<p>Aucune demande d'inscription en attente.</p>"}
    </div>`;

  dashboardContent.querySelectorAll(".reject-req-btn").forEach((btn) => {
    btn.addEventListener("click", async () => {
      await updateDoc(doc(db, "access_requests", btn.dataset.uid), {
        status: "rejected",
        reviewerUid: actorProfile.userId,
        reviewerName: actorProfile.fullName,
        reviewerRole: actorProfile.role,
        updatedAt: serverTimestamp()
      });
    });
  });

  dashboardContent.querySelectorAll(".approve-req-btn").forEach((btn) => {
    btn.addEventListener("click", async () => {
      const req = actionable.find((x) => x.userId === btn.dataset.uid);
      if (!req) return;
      await updateDoc(doc(db, "access_requests", req.userId), {
        status: "approved",
        reviewerUid: actorProfile.userId,
        reviewerName: actorProfile.fullName,
        reviewerRole: actorProfile.role,
        assignedSchoolId: defaultSchool?.id || req.schoolId || "",
        updatedAt: serverTimestamp()
      });
    });
  });
}

function renderCommsSection(profile, d) {
  dashboardContent.innerHTML = `
    <div class="page-header"><h2 class="page-title">Annonces, Notifications & Messagerie</h2></div>
    <div class="panel-card"><h3>Notifications récentes (${d.notifications.length})</h3>
      ${d.notifications.slice(0, 10).map((n) => `<p style="padding:6px 0;border-bottom:1px solid var(--border);"><strong>${esc(n.title)}</strong> — ${esc(n.body)}</p>`).join("") || "<p>Aucune notification.</p>"}
    </div>
    <div class="panel-card"><h3>Annonces (${d.announcements.length})</h3>
      ${d.announcements.slice(0, 10).map((a) => `<p style="padding:6px 0;border-bottom:1px solid var(--border);"><strong>${esc(a.title)}</strong> (${esc(a.authorName)}) : ${esc(a.content)}</p>`).join("") || "<p>Aucune annonce.</p>"}
    </div>`;
}

onAuthStateChanged(auth, async (user) => {
  if (!user) {
    teardownSubscriptions();
    appState.profile = null;
    appShell.classList.add("hidden");
    qrLoginView.classList.remove("hidden");
    startQrPairing();
    return;
  }

  try {
    const profileSnap = await getDoc(doc(db, "users", user.uid));
    if (!profileSnap.exists() || profileSnap.data().isActive !== true) {
      await forceRevokedLogout("Compte non activé ou en attente de validation.");
      return;
    }
    const profile = { userId: user.uid, ...profileSnap.data() };
    appState.profile = profile;
    stopQrTimers();
    qrLoginView.classList.add("hidden");
    appShell.classList.remove("hidden");
    topbarUserMeta.textContent = `${profile.fullName || user.email || ""} ${profile.schoolName ? "• " + profile.schoolName : ""}`;
    topbarRoleBadge.textContent = roleLabel(profile.role);

    renderSidebar(profile.role);
    subscribeRoleData(profile);
    renderDashboard();
  } catch (err) {
    await forceRevokedLogout("Accès refusé ou session Web révoquée.");
  }
});
