const test = require("node:test");
const assert = require("node:assert/strict");
const {
  QR_TTL_SECONDS,
  generateOpaqueId,
  generateProofSecret,
  sha256Hex,
  timingSafeHashMatch,
  parseUserAgentMetadata,
  validatePairingClaim
} = require("./pairingCore");

test("QR opaque requestId contains no secret tokens and differs from proofSecret", () => {
  const requestId = generateOpaqueId();
  const proofSecret = generateProofSecret();
  assert.ok(requestId.length >= 20);
  assert.ok(proofSecret.length >= 32);
  assert.notEqual(requestId, proofSecret);
  assert.equal(QR_TTL_SECONDS, 120);
});

test("sha256Hex and timingSafeHashMatch verify browser proof securely", () => {
  const secret = generateProofSecret();
  const hash = sha256Hex(secret);
  assert.equal(hash.length, 64);
  assert.equal(timingSafeHashMatch(sha256Hex(secret), hash), true);
  assert.equal(timingSafeHashMatch(sha256Hex("wrong-secret"), hash), false);
});

test("validatePairingClaim accepts valid approved request within TTL", () => {
  const now = 1_750_000_000_000;
  const secret = generateProofSecret();
  const res = validatePairingClaim({
    requestData: {
      requestId: "req-abc",
      proofHash: sha256Hex(secret),
      status: "approved",
      consumed: false,
      expiresAt: now + 60_000,
      approvedByUid: "parent-1",
      sessionId: "sess-1"
    },
    deviceSessionData: {
      sessionId: "sess-1",
      userId: "parent-1",
      revoked: false
    },
    proofSecret: secret,
    nowMs: now
  });
  assert.equal(res.ok, true);
});

test("validatePairingClaim rejects expired request (120s TTL)", () => {
  const now = 1_750_000_200_000;
  const secret = generateProofSecret();
  const res = validatePairingClaim({
    requestData: {
      requestId: "req-exp",
      proofHash: sha256Hex(secret),
      status: "approved",
      consumed: false,
      expiresAt: now - 1000,
      approvedByUid: "parent-1",
      sessionId: "sess-1"
    },
    deviceSessionData: { sessionId: "sess-1", userId: "parent-1", revoked: false },
    proofSecret: secret,
    nowMs: now
  });
  assert.equal(res.ok, false);
  assert.equal(res.code, "EXPIRED");
});

test("validatePairingClaim rejects already consumed single-use request", () => {
  const now = 1_750_000_000_000;
  const secret = generateProofSecret();
  const res = validatePairingClaim({
    requestData: {
      requestId: "req-used",
      proofHash: sha256Hex(secret),
      status: "consumed",
      consumed: true,
      expiresAt: now + 60_000,
      approvedByUid: "parent-1",
      sessionId: "sess-1"
    },
    deviceSessionData: { sessionId: "sess-1", userId: "parent-1", revoked: false },
    proofSecret: secret,
    nowMs: now
  });
  assert.equal(res.ok, false);
  assert.equal(res.code, "ALREADY_CONSUMED");
});

test("validatePairingClaim rejects forged browser proofSecret", () => {
  const now = 1_750_000_000_000;
  const realSecret = generateProofSecret();
  const forgedSecret = generateProofSecret();
  const res = validatePairingClaim({
    requestData: {
      requestId: "req-forged",
      proofHash: sha256Hex(realSecret),
      status: "approved",
      consumed: false,
      expiresAt: now + 60_000,
      approvedByUid: "parent-1",
      sessionId: "sess-1"
    },
    deviceSessionData: { sessionId: "sess-1", userId: "parent-1", revoked: false },
    proofSecret: forgedSecret,
    nowMs: now
  });
  assert.equal(res.ok, false);
  assert.equal(res.code, "INVALID_PROOF");
});

test("validatePairingClaim rejects revoked connected device session", () => {
  const now = 1_750_000_000_000;
  const secret = generateProofSecret();
  const res = validatePairingClaim({
    requestData: {
      requestId: "req-revoked",
      proofHash: sha256Hex(secret),
      status: "approved",
      consumed: false,
      expiresAt: now + 60_000,
      approvedByUid: "parent-1",
      sessionId: "sess-1"
    },
    deviceSessionData: { sessionId: "sess-1", userId: "parent-1", revoked: true },
    proofSecret: secret,
    nowMs: now
  });
  assert.equal(res.ok, false);
  assert.equal(res.code, "SESSION_REVOKED");
});

test("parseUserAgentMetadata extracts browser and OS labels", () => {
  const meta = parseUserAgentMetadata(
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/126.0.0.0 Safari/537.36"
  );
  assert.equal(meta.browserName, "Google Chrome");
  assert.equal(meta.osName, "Linux");
});
