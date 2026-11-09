// Records docs/media/demo.gif from the running demo.
//
//   mvn -pl decoy-guard-demo spring-boot:run          (in another terminal, fresh start)
//   npm install --no-save playwright && npx playwright install chromium
//   node docs/media/record-demo.mjs
//
// Needs ffmpeg on the PATH. BASE_URL overrides http://localhost:8080.
// The red dot is a fake cursor added by this script only, so the GIF shows where it clicks.

import { chromium } from 'playwright';
import { execFileSync } from 'node:child_process';
import { mkdtempSync, readdirSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const BASE_URL = process.env.BASE_URL ?? 'http://localhost:8080';
const OUT = process.env.GIF_OUT ?? join(dirname(fileURLToPath(import.meta.url)), 'demo.gif');
const videoDir = mkdtempSync(join(tmpdir(), 'decoy-guard-video-'));

const browser = await chromium.launch();
const context = await browser.newContext({
  viewport: { width: 1240, height: 840 },
  recordVideo: { dir: videoDir, size: { width: 1240, height: 840 } },
  locale: 'pt-BR',
  timezoneId: 'America/Sao_Paulo'
});
await context.addInitScript(() => {
  window.addEventListener('DOMContentLoaded', () => {
    const dot = document.createElement('div');
    dot.style.cssText = 'position:fixed;z-index:9999;width:18px;height:18px;margin:-9px 0 0 -9px;border-radius:50%;'
      + 'background:rgba(239,68,68,.85);box-shadow:0 0 0 4px rgba(239,68,68,.25);pointer-events:none;left:-40px;top:-40px';
    document.body.appendChild(dot);
    window.addEventListener('mousemove', (e) => { dot.style.left = e.clientX + 'px'; dot.style.top = e.clientY + 'px'; });
  });
});

const page = await context.newPage();
const pause = (ms) => page.waitForTimeout(ms);
const phones = page.locator('[data-phone] .phone');
const normal = phones.nth(0);
const duress = phones.nth(1);

async function click(locator) {
  const box = await locator.boundingBox();
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2, { steps: 12 });
  await pause(120);
  await locator.click();
}

async function typePin(phone, pin) {
  for (const digit of pin) {
    await click(phone.locator(`[data-key="${digit}"]`));
    await pause(180);
  }
}

await page.goto(BASE_URL + '/');
await pause(1500);

// 1. Normal session: real balance.
await typePin(normal, '1234');
await normal.locator('[data-screen="home"]').waitFor({ state: 'visible' });
await pause(1500);

// 2. Duress session: looks the same, silent alert reaches the fraud desk.
await typePin(duress, '4321');
await duress.locator('[data-screen="home"]').waitFor({ state: 'visible' });
await page.locator('[data-alerts] li.alert').waitFor();
await pause(2000);

// 3. The attacker dictates a Pix of R$ 200.
await click(duress.locator('[data-action="open-pix"]'));
await pause(600);
await click(duress.locator('[data-pix-key]'));
await duress.locator('[data-pix-key]').pressSequentially('golpista@exemplo.com', { delay: 45 });
await click(duress.locator('[data-pix-amount]'));
await duress.locator('[data-pix-amount]').pressSequentially('20000', { delay: 160 });
await pause(600);
await click(duress.locator('[data-pix-submit]'));
await duress.locator('[data-screen="receipt"]').waitFor({ state: 'visible' });
await page.locator('[data-held] li.held').waitFor();
await pause(2500);

// 4. The fake balance drops, as the attacker expects.
await click(duress.locator('[data-screen="receipt"] [data-action="go-home"]'));
await pause(2200);

// 5. The real account is intact (fresh read from the API).
await click(normal.locator('[data-action="open-pix"]'));
await pause(500);
await click(normal.locator('[data-screen="pix"] [data-action="go-home"]'));
await pause(3000);

await context.close();
await browser.close();

const video = join(videoDir, readdirSync(videoDir).find((f) => f.endsWith('.webm')));
execFileSync('ffmpeg', ['-y', '-loglevel', 'error', '-i', video, '-vf',
  'fps=8,scale=930:-1:flags=lanczos,split[a][b];[a]palettegen=max_colors=96:stats_mode=diff[p];[b][p]paletteuse=dither=bayer:bayer_scale=4:diff_mode=rectangle',
  '-loop', '0', OUT]);
rmSync(videoDir, { recursive: true, force: true });
console.log('Wrote ' + OUT);
