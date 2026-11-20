// Downloads official 7-Zip 26.06 binaries + license into ../vendor
// Uses Node (OpenSSL), which works where Schannel is unavailable.
import https from 'node:https';
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const outDir = path.resolve(__dirname, '..', 'vendor');
fs.mkdirSync(outDir, { recursive: true });

const FILES = [
  ['7zr.exe', 'https://github.com/ip7z/7zip/releases/download/26.06/7zr.exe'],
  ['7z2606-x64.exe', 'https://github.com/ip7z/7zip/releases/download/26.06/7z2606-x64.exe'],
  ['7z-LICENSE.txt', 'https://www.7-zip.org/license.txt'],
];

function getDirect(url, redirects = 0) {
  return new Promise((resolve, reject) => {
    if (redirects > 5) return reject(new Error('too many redirects'));
    const lib = url.startsWith('https') ? https : http;
    const req = lib.get(url, { headers: { 'User-Agent': 'pzip-build' } }, (res) => {
      if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
        res.resume();
        return resolve(getDirect(res.headers.location, redirects + 1));
      }
      if (res.statusCode !== 200) {
        res.resume();
        return reject(new Error(`HTTP ${res.statusCode} for ${url}`));
      }
      const chunks = [];
      res.on('data', (c) => chunks.push(c));
      res.on('end', () => resolve(Buffer.concat(chunks)));
    });
    req.on('error', reject);
    req.setTimeout(60000, () => req.destroy(new Error('timeout')));
  });
}

let fail = false;
for (const [name, url] of FILES) {
  const dst = path.join(outDir, name);
  if (fs.existsSync(dst) && fs.statSync(dst).size > 0) {
    console.log(`SKIP ${name} (already present)`);
    continue;
  }
  try {
    const buf = await getDirect(url);
    fs.writeFileSync(dst, buf);
    console.log(`OK   ${name}  ${buf.length} bytes`);
  } catch (e) {
    fail = true;
    console.error(`FAIL ${name}: ${e.message}`);
  }
}
console.log(fail ? 'DONE_WITH_ERRORS' : 'ALL_DOWNLOADED');
process.exit(fail ? 1 : 0);
