import { createRequire } from 'module'; import fs from 'fs';
const require = createRequire('/opt/node22/lib/node_modules/');
const { chromium } = require('playwright');
const b = await chromium.launch({ args: ['--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader'] });
const p = await b.newPage({ viewport: { width: 1000, height: 800 } });
await p.goto('https://web.blockbench.net/', { waitUntil: 'load', timeout: 90000 });
await p.waitForFunction(() => typeof Codecs !== 'undefined' && Codecs.java_block, null, { timeout: 90000 });
const model = {parent:"block/block",textures:{all:"block/oak_planks",particle:"block/oak_planks"},elements:[
 {from:[0,0,0],to:[16,10,16],faces:{north:{uv:[0,6,16,16],texture:"#all"},south:{uv:[0,6,16,16],texture:"#all"},east:{uv:[0,6,16,16],texture:"#all"},west:{uv:[0,6,16,16],texture:"#all"},up:{uv:[0,0,16,16],texture:"#all"},down:{uv:[0,0,16,16],texture:"#all",cullface:"down"}}},
 {from:[4,10,4],to:[12,14,12],rotation:{origin:[8,8,8],axis:"y",angle:22.5},faces:{up:{uv:[4,4,12,12],texture:"#all"},north:{uv:[4,2,12,6],texture:"#all"}}}]};
const r = await p.evaluate((m) => {
  newProject(Formats.java_block);
  Codecs.java_block.parse(m, 'quern.json');
  const out = Codecs.java_block.compile();
  return { cubes: Cube.all.length, out: (typeof out==='string'?out:JSON.stringify(out)).slice(0,600) };
}, model);
console.log(JSON.stringify(r,null,1));
await b.close();
