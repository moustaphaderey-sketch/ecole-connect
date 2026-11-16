const admin = require("firebase-admin");
const { getFirestore, FieldValue, Timestamp } = require("firebase-admin/firestore");
const { onRequest } = require("firebase-functions/v2/https");
const {
  QR_TTL_SECONDS,
  DATABASE_ID,
  generateOpaqueId,
  generateProofSecret,
  sha256Hex,
  timingSafeHashMatch,
  toMillis,
  parseUserAgentMetadata,
  validatePairingClaim
} = require("./pairingCore");

if (!admin.apps.length) {
  admin.initializeApp();
}

const db = getFirestore(admin.app(), DATABASE_ID);

function setCors(res) {
  res.set("Access-Control-Allow-Origin", "*");
  res.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
  res.set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-QR-Proof");
  res.set("Cache-Control", "no-store, no-cache, must-revalidate");
}

function handlePreflight(req, res) {
  setCors(res);
  if (req.method === "OPTIONS") {
    res.status(204).send("");
    return true;
  }
  return false;
}

exports.createQrPairing = onRequest(async (req, res) => {
  if (handlePreflight(req, res)) return;
  if (req.method !== "POST") {
    res.status(405).json({ error: "Méthode non autorisée." });
    return;
  }

  try {
    const requestId = generateOpaqueId(18);
    const proofSecret = generateProofSecret();
    const proofHash = sha256Hex(proofSecret);
    const meta = parseUserAgentMetadata(req.get("user-agent"), req.body || {});
    const nowMs = Date.now();
    const expiresAtMs = nowMs + QR_TTL_SECONDS * 1000;

    await db.collection("qr_pairing_requests").doc(requestId).set({
      requestId,
      proofHash,
      browserName: meta.browserName,
      osName: meta.osName,
      userAgentLabel: meta.userAgentLabel,
      status: "pending",
      consumed: false,
      expiresAt: Timestamp.fromMillis(expiresAtMs),
      createdAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp()
    });

    res.status(200).json({
      requestId,
      proofSecret,
      expiresAtMs,
      ttlSeconds: QR_TTL_SECONDS,
      browserName: meta.browserName,
      osName: meta.osName
    });
  } catch (err) {
    res.status(500).json({ error: err.message || "Erreur serveur création QR." });
  }
});

exports.getQrPairingStatus = onRequest(async (req, res) => {
  if (handlePreflight(req, res)) return;

  try {
    const requestId = (req.body?.requestId || req.query?.requestId || "").trim();
    const proofSecret = (req.get("x-qr-proof") || req.body?.proofSecret || "").trim();
    if (!requestId || !proofSecret) {
      res.status(400).json({ error: "Paramètres requestId et preuve requis." });
      return;
    }

    const docRef = db.collection("qr_pairing_requests").doc(requestId);
    const snap = await docRef.get();
    if (!snap.exists) {
      res.status(404).json({ status: "expired", remainingSeconds: 0 });
      return;
    }

    const data = snap.data();
    if (!timingSafeHashMatch(sha256Hex(proofSecret), data.proofHash)) {
      res.status(403).json({ error: "Preuve du navigateur invalide." });
      return;
    }

    const nowMs = Date.now();
    const expiresAtMs = toMillis(data.expiresAt);
    const remainingSeconds = Math.max(0, Math.ceil((expiresAtMs - nowMs) / 1000));

    let effectiveStatus = data.status;
    if (effectiveStatus === "pending" && remainingSeconds <= 0) {
      effectiveStatus = "expired";
      await docRef.update({
        status: "expired",
        updatedAt: FieldValue.serverTimestamp()
      });
    }

    res.status(200).json({
      requestId,
      status: effectiveStatus,
      remainingSeconds,
      browserName: data.browserName || "",
      osName: data.osName || ""
    });
  } catch (err) {
    res.status(500).json({ error: err.message || "Erreur vérification statut QR." });
  }
});

exports.claimQrToken = onRequest(async (req, res) => {
  if (handlePreflight(req, res)) return;
  if (req.method !== "POST") {
    res.status(405).json({ error: "Méthode non autorisée." });
    return;
  }

  try {
    const requestId = (req.body?.requestId || "").trim();
    const proofSecret = (req.body?.proofSecret || req.get("x-qr-proof") || "").trim();
    if (!requestId || !proofSecret) {
      res.status(400).json({ error: "Identifiant de demande et preuve requis." });
      return;
    }

    const reqRef = db.collection("qr_pairing_requests").doc(requestId);
    const txResult = await db.runTransaction(async (tx) => {
      const reqSnap = await tx.get(reqRef);
      if (!reqSnap.exists) {
        return { ok: false, httpStatus: 404, message: "Demande QR introuvable." };
      }
      const reqData = reqSnap.data();
      const sessionId = reqData.sessionId || "";
      const sessionRef = sessionId ? db.collection("connected_devices").doc(sessionId) : null;
      const sessionSnap = sessionRef ? await tx.get(sessionRef) : null;
      const deviceSessionData = sessionSnap && sessionSnap.exists ? sessionSnap.data() : null;

      const verdict = validatePairingClaim({
        requestData: reqData,
        deviceSessionData,
        proofSecret,
        nowMs: Date.now()
      });

      if (!verdict.ok) {
        if (verdict.code === "EXPIRED" && reqData.status === "pending") {
          tx.update(reqRef, {
            status: "expired",
            updatedAt: FieldValue.serverTimestamp()
          });
        }
        return { ok: false, httpStatus: 403, message: verdict.message };
      }

      tx.update(reqRef, {
        status: "consumed",
        consumed: true,
        consumedAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp()
      });
      tx.update(sessionRef, {
        lastActiveAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp()
      });

      return {
        ok: true,
        uid: reqData.approvedByUid,
        role: reqData.approvedByRole || "",
        sessionId
      };
    });

    if (!txResult.ok) {
      res.status(txResult.httpStatus).json({ error: txResult.message });
      return;
    }

    const customToken = await admin.auth().createCustomToken(txResult.uid, {
      deviceSessionId: txResult.sessionId,
      pairingRequestId: requestId
    });

    res.status(200).json({
      customToken,
      sessionId: txResult.sessionId,
      userId: txResult.uid,
      role: txResult.role
    });
  } catch (err) {
    res.status(500).json({ error: err.message || "Impossible de délivrer le jeton personnalisé." });
  }
});

exports.touchDeviceSession = onRequest(async (req, res) => {
  if (handlePreflight(req, res)) return;
  try {
    const authHeader = req.get("authorization") || "";
    const idToken = authHeader.startsWith("Bearer ") ? authHeader.slice(7).trim() : "";
    if (!idToken) {
      res.status(401).json({ error: "Authentification requise." });
      return;
    }
    const decoded = await admin.auth().verifyIdToken(idToken);
    const sessionId = (req.body?.sessionId || decoded.deviceSessionId || "").trim();
    if (!sessionId) {
      res.status(400).json({ error: "Identifiant de session manquant." });
      return;
    }

    const sessionRef = db.collection("connected_devices").doc(sessionId);
    const snap = await sessionRef.get();
    if (!snap.exists || snap.data().userId !== decoded.uid || snap.data().revoked === true) {
      res.status(403).json({ revoked: true, error: "Session révoquée." });
      return;
    }

    await sessionRef.update({
      lastActiveAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp()
    });
    res.status(200).json({ ok: true, revoked: false });
  } catch (err) {
    res.status(500).json({ error: err.message || "Erreur mise à jour session." });
  }
});

exports.revokeDeviceSession = onRequest(async (req, res) => {
  if (handlePreflight(req, res)) return;
  try {
    const authHeader = req.get("authorization") || "";
    const idToken = authHeader.startsWith("Bearer ") ? authHeader.slice(7).trim() : "";
    if (!idToken) {
      res.status(401).json({ error: "Authentification requise." });
      return;
    }
    const decoded = await admin.auth().verifyIdToken(idToken);
    const sessionId = (req.body?.sessionId || decoded.deviceSessionId || "").trim();
    if (!sessionId) {
      res.status(400).json({ error: "Identifiant de session manquant." });
      return;
    }

    const sessionRef = db.collection("connected_devices").doc(sessionId);
    const snap = await sessionRef.get();
    if (!snap.exists || snap.data().userId !== decoded.uid) {
      res.status(403).json({ error: "Action non autorisée." });
      return;
    }

    await sessionRef.update({
      revoked: true,
      revokedAt: FieldValue.serverTimestamp(),
      revokedByUid: decoded.uid,
      updatedAt: FieldValue.serverTimestamp()
    });
    res.status(200).json({ ok: true });
  } catch (err) {
    res.status(500).json({ error: err.message || "Erreur révocation session." });
  }
});
