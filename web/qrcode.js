// Pure-JS QR Code SVG encoder (Version 2-4, Byte mode, ECC Level L, Mask 0)
// Keeps sensitive pairing identifiers 100% local in the browser without external QR image APIs.

const GF_EXP = new Uint8Array(512);
const GF_LOG = new Uint8Array(256);
(() => {
  let x = 1;
  for (let i = 0; i < 255; i++) {
    GF_EXP[i] = x;
    GF_LOG[x] = i;
    x <<= 1;
    if (x & 0x100) x ^= 0x11d;
  }
  for (let i = 255; i < 512; i++) {
    GF_EXP[i] = GF_EXP[i - 255];
  }
})();

function gfMul(a, b) {
  if (a === 0 || b === 0) return 0;
  return GF_EXP[GF_LOG[a] + GF_LOG[b]];
}

function rsGeneratorPoly(ecCount) {
  let poly = [1];
  for (let i = 0; i < ecCount; i++) {
    const next = new Array(poly.length + 1).fill(0);
    const factor = GF_EXP[i];
    for (let j = 0; j < poly.length; j++) {
      next[j] ^= poly[j];
      next[j + 1] ^= gfMul(poly[j], factor);
    }
    poly = next;
  }
  return poly;
}

function rsComputeEc(dataBytes, ecCount) {
  const gen = rsGeneratorPoly(ecCount);
  const res = new Uint8Array(dataBytes.length + ecCount);
  res.set(dataBytes);
  for (let i = 0; i < dataBytes.length; i++) {
    const coef = res[i];
    if (coef !== 0) {
      for (let j = 0; j < gen.length; j++) {
        res[i + j] ^= gfMul(gen[j], coef);
      }
    }
  }
  return Array.from(res.slice(dataBytes.length));
}

// Single-block ECC-L specs for Versions 1..4 (covers up to 78 UTF-8 bytes safely)
const VERSION_SPECS = [
  { version: 1, size: 21, dataCodewords: 19, ecCodewords: 7, alignments: [] },
  { version: 2, size: 25, dataCodewords: 34, ecCodewords: 10, alignments: [6, 18] },
  { version: 3, size: 29, dataCodewords: 55, ecCodewords: 15, alignments: [6, 22] },
  { version: 4, size: 33, dataCodewords: 80, ecCodewords: 20, alignments: [6, 26] }
];

function encodeBytePayload(text) {
  const utf8 = new TextEncoder().encode(text);
  const spec = VERSION_SPECS.find((s) => utf8.length + 2 <= s.dataCodewords) || VERSION_SPECS[VERSION_SPECS.length - 1];
  const maxBytes = spec.dataCodewords - 2;
  const payload = utf8.slice(0, maxBytes);

  const bits = [];
  const pushBits = (val, len) => {
    for (let i = len - 1; i >= 0; i--) bits.push((val >> i) & 1);
  };

  // Byte mode indicator 0100 + 8-bit character count
  pushBits(0b0100, 4);
  pushBits(payload.length, 8);
  for (const b of payload) pushBits(b, 8);

  const totalDataBits = spec.dataCodewords * 8;
  const terminatorLen = Math.min(4, totalDataBits - bits.length);
  pushBits(0, terminatorLen);
  while (bits.length % 8 !== 0) bits.push(0);

  const dataBytes = [];
  for (let i = 0; i < bits.length; i += 8) {
    let val = 0;
    for (let j = 0; j < 8; j++) val = (val << 1) | bits[i + j];
    dataBytes.push(val);
  }
  const padBytes = [0xec, 0x11];
  let padIdx = 0;
  while (dataBytes.length < spec.dataCodewords) {
    dataBytes.push(padBytes[padIdx % 2]);
    padIdx++;
  }

  const ecBytes = rsComputeEc(dataBytes, spec.ecCodewords);
  const allCodewords = [...dataBytes, ...ecBytes];
  const allBits = [];
  for (const byte of allCodewords) {
    for (let b = 7; b >= 0; b--) allBits.push((byte >> b) & 1);
  }
  return { spec, allBits };
}

function buildQrMatrix(text) {
  const { spec, allBits } = encodeBytePayload(text);
  const n = spec.size;
  const matrix = Array.from({ length: n }, () => new Int8Array(n).fill(-1));
  const reserved = Array.from({ length: n }, () => new Uint8Array(n));

  function setModule(r, c, dark, isReserved = true) {
    if (r < 0 || c < 0 || r >= n || c >= n) return;
    matrix[r][c] = dark ? 1 : 0;
    if (isReserved) reserved[r][c] = 1;
  }

  function placeFinder(topR, leftC) {
    for (let dr = -1; dr <= 7; dr++) {
      for (let dc = -1; dc <= 7; dc++) {
        const r = topR + dr;
        const c = leftC + dc;
        if (r < 0 || c < 0 || r >= n || c >= n) continue;
        const inOuter = dr >= 0 && dr <= 6 && dc >= 0 && dc <= 6;
        const dark =
          inOuter &&
          (dr === 0 || dr === 6 || dc === 0 || dc === 6 || (dr >= 2 && dr <= 4 && dc >= 2 && dc <= 4));
        setModule(r, c, dark, true);
      }
    }
  }

  function placeAlignment(centerR, centerC) {
    for (let dr = -2; dr <= 2; dr++) {
      for (let dc = -2; dc <= 2; dc++) {
        const dark = Math.abs(dr) === 2 || Math.abs(dc) === 2 || (dr === 0 && dc === 0);
        setModule(centerR + dr, centerC + dc, dark, true);
      }
    }
  }

  placeFinder(0, 0);
  placeFinder(0, n - 7);
  placeFinder(n - 7, 0);

  if (spec.alignments.length >= 2) {
    const pos = spec.alignments[1];
    placeAlignment(pos, pos);
  }

  for (let i = 8; i < n - 8; i++) {
    setModule(6, i, i % 2 === 0, true);
    setModule(i, 6, i % 2 === 0, true);
  }

  // Dark module at (4 * version + 9, 8)
  setModule(4 * spec.version + 9, 8, true, true);

  // Format info for ECC-L (01) + Mask 0 (000) -> 0b111011111000100 (MSB first)
  const formatBits = [1, 1, 1, 0, 1, 1, 1, 1, 1, 0, 0, 0, 1, 0, 0];
  for (let i = 0; i < 15; i++) {
    const bit = formatBits[i] === 1;
    // Top-left vertical/horizontal
    if (i < 6) setModule(8, i, bit, true);
    else if (i < 8) setModule(8, i + 1, bit, true);
    else if (i === 8) setModule(7, 8, bit, true);
    else setModule(14 - i, 8, bit, true);

    // Bottom-left & top-right
    if (i < 7) setModule(n - 1 - i, 8, bit, true);
    else setModule(8, n - 15 + i, bit, true);
  }

  // Zigzag placement
  let bitIndex = 0;
  let goingUp = true;
  for (let rightCol = n - 1; rightCol > 0; rightCol -= 2) {
    if (rightCol === 6) rightCol--;
    for (let step = 0; step < n; step++) {
      const r = goingUp ? n - 1 - step : step;
      for (let offset = 0; offset < 2; offset++) {
        const c = rightCol - offset;
        if (!reserved[r][c]) {
          const dataBit = bitIndex < allBits.length ? allBits[bitIndex++] : 0;
          const masked = ((r + c) % 2 === 0) ? (dataBit ^ 1) : dataBit;
          matrix[r][c] = masked;
        }
      }
    }
    goingUp = !goingUp;
  }

  return matrix;
}

export function renderQrSvg(text, sizePx = 240) {
  const matrix = buildQrMatrix(text);
  const n = matrix.length;
  const quiet = 3;
  const total = n + quiet * 2;
  let rects = "";
  for (let r = 0; r < n; r++) {
    for (let c = 0; c < n; c++) {
      if (matrix[r][c] === 1) {
        rects += `<rect x="${c + quiet}" y="${r + quiet}" width="1" height="1" />`;
      }
    }
  }
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${sizePx}" height="${sizePx}" viewBox="0 0 ${total} ${total}" shape-rendering="crispEdges" role="img" aria-label="QR Code d'association ÉCOLE CONNECTÉE"><rect width="${total}" height="${total}" fill="#ffffff"/><g fill="#0F172A">${rects}</g></svg>`;
}
