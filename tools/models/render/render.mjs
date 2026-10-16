// Licensed under the EUPL, Version 1.2.
// Headless Blockbench renderer: node render.mjs <model.bbmodel> <outDir> [animName] [t1,t2,...]
// Writes <outDir>/<view>_<anim>_<t>.png for views front-left, side, front and top, using the Blockbench web app in Playwright Chromium.
import { createRequire } from 'module';
import fs from 'fs';
import path from 'path';

const require = createRequire(process.env.PLAYWRIGHT_NODE_MODULES || '/opt/node22/lib/node_modules/');
const { chromium } = require('playwright');
const [file, outDir, animName = '', timesArg = '0'] = process.argv.slice(2);
const BB_URL = process.env.BLOCKBENCH_URL || 'https://web.blockbench.net/';
const times = timesArg.split(',').map(Number);
const VIEWS = {
  front_left: { position: [60, 40, -60], target: [0, 12, 0] },
  side: { position: [90, 16, 0], target: [0, 12, 0] },
  front: { position: [0, 16, -90], target: [0, 12, 0] },
  top: { position: [0, 110, -1], target: [0, 0, 0] },
};
fs.mkdirSync(outDir, { recursive: true });
const browser = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });
page.on('pageerror', (e) => console.error('pageerror', e.message.slice(0, 300)));
await page.goto(BB_URL, { waitUntil: 'load', timeout: 120000 });
await page.waitForFunction(() => typeof Codecs !== 'undefined' && Codecs.project, null, { timeout: 120000 });
const info = await page.evaluate((txt) => {
  Codecs.project.load(JSON.parse(txt), { path: 'model.bbmodel', name: 'model.bbmodel' });
  return { cubes: Cube.all.length, groups: Group.all.length, animations: Animation.all.map((a) => a.name) };
}, fs.readFileSync(file, 'utf8'));
console.log(JSON.stringify(info));
await page.waitForTimeout(1000);
for (const t of times) {
  for (const [view, preset] of Object.entries(VIEWS)) {
    const data = await page.evaluate(({ animName, t, preset }) => {
      const anim = Animation.all.find((a) => a.name === animName);
      if (anim) { Modes.options.animate.select(); anim.select(); Timeline.setTime(t); Animator.preview(); }
      Preview.selected.loadAnglePreset(preset);
      Preview.selected.render();
      return Preview.selected.canvas.toDataURL('image/png');
    }, { animName, t, preset });
    const out = path.join(outDir, `${view}_${animName || 'rest'}_${t}.png`);
    fs.writeFileSync(out, Buffer.from(data.split(',')[1], 'base64'));
  }
}
await browser.close();
