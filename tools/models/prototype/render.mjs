import { createRequire } from 'module'; import fs from 'fs';
const require = createRequire('/opt/node22/lib/node_modules/');
const { chromium } = require('playwright');
const file = process.argv[2], out = process.argv[3];
const b = await chromium.launch({ args: ['--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader'] });
const p = await b.newPage({ viewport: { width: 1280, height: 900 } });
p.on('pageerror', e => console.log('pageerror', e.message.slice(0,300)));
await p.goto('https://web.blockbench.net/', { waitUntil: 'load', timeout: 90000 });
await p.waitForFunction(() => typeof Codecs !== 'undefined' && Codecs.project, null, { timeout: 90000 });
const res = await p.evaluate((txt) => {
  const model = JSON.parse(txt);
  Codecs.project.load(model, { path: 'test.bbmodel', name: 'test.bbmodel' });
  const info = { elements: Cube.all.length, groups: Group.all.length, anims: Animation.all.map(a=>a.name), textures: Texture.all.map(t=>[t.width,t.height]) };
  // re-compile to check round-trip
  const c = Codecs.project.compile({raw:true}); info.roundtrip_keys = Object.keys(c); info.fv = c.meta.format_version;
  return info;
}, fs.readFileSync(file, 'utf8'));
console.log(JSON.stringify(res));
await p.waitForTimeout(1500);
// screenshot the main preview canvas only
const dataUrl = await p.evaluate(() => { Modes.options.animate.select(); Animation.all[0].select(); Timeline.setTime(0.0); Animator.preview(); Preview.selected.loadAnglePreset({position:[60,40,-60], target:[0,10,0]}); Preview.selected.render(); return Preview.selected.canvas.toDataURL('image/png'); });
fs.writeFileSync(out, Buffer.from(dataUrl.split(',')[1], 'base64'));
await b.close();
