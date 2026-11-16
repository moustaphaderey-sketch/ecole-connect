const crypto = require("node:crypto");

const QR_TTL_SECONDS = 120;
const DATABASE_ID = "ai-studio-android-coleconn-5a5a7572-a279-4b58-bee5-0a53310cb1f0";

function generateOpaqueId(byteLength = 18) {
  return crypto.randomBytes(byteLength).toString("base64url");
}

function generateProofSecret() {
  return crypto.randomBytes(32).toString("base64url");
}

function sha256Hex(value) {
  return crypto.createHash("sha256").update(String(value || ""), "utf8").digest("hex");
}

function timingSafeHashMatch(hexA, hexB) {
  if (typeof hexA !== "string" || typeof hexB !== "string") return false;
  if (hexA.length !== 64 || hexB.length !== 64) return false;
  const bufA = Buffer.from(hexA, "hex");
  const bufB = Buffer.from(hexB, "hex");
  if (bufA.length !== bufB.length) return false;
  return crypto.timingSafeEqual(bufA, bufB);
}

function toMillis(ts) {
  if (!ts) return 0;
  if (typeof ts === "number") return ts;
  if (typeof ts.toMillis === "function") return ts.toMillis();
  if (ts instanceof Date) return ts.getTime();
  if (typeof ts._seconds === "number") return ts._seconds * 1000;
  return 0;
}

function parseUserAgentMetadata(userAgent = "", provided = {}) {
  const ua = String(userAgent || "");
  let browserName = typeof provided.browserName === "string" ? provided.browserName.trim() : "";
  if (!browserName) {
    if (ua.includes("Edg/")) browserName = "Microsoft Edge";
    else if (ua.includes("OPR/") || ua.includes("Opera")) browserName = "Opera";
    else if (ua.includes("Firefox/")) browserName = "Mozilla Firefox";
    else if (ua.includes("Chrome/")) browserName = "Google Chrome";
    else if (ua.includes("Safari/")) browserName = "Apple Safari";
    else browserName = "Navigateur Web";
  }

  let osName = typeof provided.osName === "string" ? provided.osName.trim() : "";
  if (!osName) {
    if (ua.includes("Windows")) osName = "Windows";
    else if (ua.includes("Mac OS X")) osName = "macOS";
    else if (ua.includes("CrOS")) osName = "ChromeOS";
    else if (ua.includes("Linux")) osName = "Linux";
    else osName = "Ordinateur";
  }

  const userAgentLabel = (typeof provided.userAgentLabel === "string" && provided.userAgentLabel.trim())
    ? provided.userAgentLabel.trim().slice(0, 120)
    : `${browserName} sur ${osName}`.slice(0, 120);

  return {
    browserName: browserName.slice(0, 80),
    osName: osName.slice(0, 80),
    userAgentLabel
  };
}

function validatePairingClaim({ requestData, deviceSessionData, proofSecret, nowMs = Date.now() }) {
  if (!requestData) {
    return { ok: false, code: "NOT_FOUND", message: "Demande QR introuvable." };
  }
  if (!proofSecret || typeof proofSecret !== "string" || proofSecret.length < 16) {
    return { ok: false, code: "MISSING_PROOF", message: "Preuve de possession du navigateur manquante." };
  }
  const expiresAtMs = toMillis(requestData.expiresAt);
  if (!expiresAtMs || expiresAtMs <= nowMs) {
    return { ok: false, code: "EXPIRED", message: "La demande QR a expiré (délai de 120 secondes dépassé)." };
  }
  if (requestData.consumed === true || requestData.status === "consumed") {
    return { ok: false, code: "ALREADY_CONSUMED", message: "Ce QR Code a déjà été utilisé (usage unique)." };
  }
  if (requestData.status === "rejected") {
    return { ok: false, code: "REJECTED", message: "La demande d'association a été refusée." };
  }
  if (requestData.status !== "approved") {
    return { ok: false, code: "NOT_APPROVED", message: "La demande n'est pas encore confirmée sur Android." };
  }
  const computedHash = sha256Hex(proofSecret);
  if (!timingSafeHashMatch(computedHash, requestData.proofHash)) {
    return { ok: false, code: "INVALID_PROOF", message: "Preuve cryptographique du navigateur invalide." };
  }
  if (!requestData.approvedByUid || !requestData.sessionId) {
    return { ok: false, code: "INCOMPLETE_APPROVAL", message: "Approbation incomplète." };
  }
  if (!deviceSessionData || deviceSessionData.revoked === true) {
    return { ok: false, code: "SESSION_REVOKED", message: "Cette session ordinateur a déjà été révoquée." };
  }
  if (deviceSessionData.userId !== requestData.approvedByUid) {
    return { ok: false, code: "SESSION_MISMATCH", message: "Incohérence de session utilisateur." };
  }
  return { ok: true };
}

module.exports = {
  QR_TTL_SECONDS,
  DATABASE_ID,
  generateOpaqueId,
  generateProofSecret,
  sha256Hex,
  timingSafeHashMatch,
  toMillis,
  parseUserAgentMetadata,
  validatePairingClaim
};
