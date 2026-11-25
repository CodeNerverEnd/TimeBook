'use strict';
/* 生成应用图标：绿色圆角方块 + 白色表盘指针（不依赖任何图形库） */

const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const CRC_TABLE = (() => {
  const t = new Int32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xEDB88320 ^ (c >>> 1) : c >>> 1;
    t[n] = c;
  }
  return t;
})();

function crc32(buf) {
  let c = -1;
  for (let i = 0; i < buf.length; i++) c = CRC_TABLE[(c ^ buf[i]) & 0xFF] ^ (c >>> 8);
  return (c ^ -1) >>> 0;
}

function chunk(type, data) {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length, 0);
  const t = Buffer.from(type, 'ascii');
  const body = Buffer.concat([t, data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(body), 0);
  return Buffer.concat([len, body, crc]);
}

function encodePng(width, height, rgba) {
  const raw = Buffer.alloc((width * 4 + 1) * height);
  for (let y = 0; y < height; y++) {
    raw[y * (width * 4 + 1)] = 0;
    rgba.copy(raw, y * (width * 4 + 1) + 1, y * width * 4, (y + 1) * width * 4);
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0);
  ihdr.writeUInt32BE(height, 4);
  ihdr[8] = 8;   // bit depth
  ihdr[9] = 6;   // RGBA
  return Buffer.concat([
    Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]),
    chunk('IHDR', ihdr),
    chunk('IDAT', zlib.deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0))
  ]);
}

function distToSegment(px, py, x1, y1, x2, y2) {
  const dx = x2 - x1, dy = y2 - y1;
  const l2 = dx * dx + dy * dy;
  let t = l2 ? ((px - x1) * dx + (py - y1) * dy) / l2 : 0;
  t = Math.max(0, Math.min(1, t));
  const cx = x1 + t * dx, cy = y1 + t * dy;
  return Math.hypot(px - cx, py - cy);
}

function draw(size) {
  const S = size;
  const buf = Buffer.alloc(S * S * 4);
  const cx = S / 2, cy = S / 2;
  const radius = S * 0.22;          // 圆角
  const scale = S / 256;

  const set = (x, y, r, g, b, a) => {
    const i = (y * S + x) * 4;
    if (a >= 255) { buf[i] = r; buf[i + 1] = g; buf[i + 2] = b; buf[i + 3] = 255; return; }
    const ia = a / 255, oa = buf[i + 3] / 255;
    const na = ia + oa * (1 - ia);
    if (na <= 0) return;
    buf[i] = Math.round((r * ia + buf[i] * oa * (1 - ia)) / na);
    buf[i + 1] = Math.round((g * ia + buf[i + 1] * oa * (1 - ia)) / na);
    buf[i + 2] = Math.round((b * ia + buf[i + 2] * oa * (1 - ia)) / na);
    buf[i + 3] = Math.round(na * 255);
  };

  for (let y = 0; y < S; y++) {
    for (let x = 0; x < S; x++) {
      // 圆角矩形遮罩
      const dx = Math.max(Math.abs(x - cx) - (S / 2 - radius), 0);
      const dy = Math.max(Math.abs(y - cy) - (S / 2 - radius), 0);
      const dCorner = Math.hypot(dx, dy);
      let alpha = 0;
      if (dCorner <= radius) alpha = 255;
      else if (dCorner <= radius + 1.2) alpha = Math.round(255 * (1 - (dCorner - radius) / 1.2));
      if (!alpha) continue;

      // 竖向渐变：#2F6B4F → #245840
      const t = y / S;
      const r = Math.round(0x2F + (0x24 - 0x2F) * t);
      const g = Math.round(0x6B + (0x58 - 0x6B) * t);
      const b = Math.round(0x4F + (0x40 - 0x4F) * t);
      set(x, y, r, g, b, alpha);

      // 白色表盘外环
      const dc = Math.hypot(x - cx, y - cy);
      const ringR = 74 * scale;
      const ringW = 9 * scale;
      if (Math.abs(dc - ringR) <= ringW / 2) set(x, y, 255, 255, 255, 235);

      // 时针（指向 7 点方向）与分针（指向 12 点）
      if (distToSegment(x, y, cx, cy, cx, cy - 46 * scale) <= 5.5 * scale) set(x, y, 255, 255, 255, 245);
      if (distToSegment(x, y, cx, cy, cx - 26 * scale, cy + 30 * scale) <= 6.5 * scale) set(x, y, 255, 255, 255, 245);

      // 中心点
      if (dc <= 8 * scale) set(x, y, 255, 255, 255, 255);
    }
  }
  return encodePng(S, S, buf);
}

const outDir = path.join(__dirname, '..', 'build');
fs.mkdirSync(outDir, { recursive: true });
fs.writeFileSync(path.join(outDir, 'icon.png'), draw(256));
fs.writeFileSync(path.join(outDir, 'tray.png'), draw(32));

/* 托盘用 ICO：Windows 会按 DPI 自己挑 16/20/24/32，比单张 png 缩放清楚 */
function buildIco(sizes) {
  const images = sizes.map(s => draw(s));
  const header = Buffer.alloc(6);
  header.writeUInt16LE(0, 0);
  header.writeUInt16LE(1, 2);          // 1 = icon
  header.writeUInt16LE(images.length, 4);

  const entries = [];
  let offset = 6 + images.length * 16;
  images.forEach((png, i) => {
    const e = Buffer.alloc(16);
    const s = sizes[i];
    e[0] = s >= 256 ? 0 : s;           // 宽（256 记 0）
    e[1] = s >= 256 ? 0 : s;           // 高
    e[2] = 0;                          // 调色板数
    e[3] = 0;                          // 保留
    e.writeUInt16LE(1, 4);             // 色彩平面
    e.writeUInt16LE(32, 6);            // 位深
    e.writeUInt32LE(png.length, 8);    // 数据长度
    e.writeUInt32LE(offset, 12);       // 数据偏移
    offset += png.length;
    entries.push(e);
  });

  return Buffer.concat([header, ...entries, ...images]);
}

fs.writeFileSync(path.join(outDir, 'tray.ico'), buildIco([16, 20, 24, 32]));
console.log('icon.png / tray.png / tray.ico 已生成');
