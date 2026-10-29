// pt.mjs — drive the Kompara prototype like a first-time participant (one persistent headless Chrome per working folder).
// Usage: node pt.mjs <dir> start | look | tap "<visible text>" | tapxy X Y | back | scroll down|up | swipe-down-top | task start|done|stuck | log | stop
import { spawn } from "node:child_process";
import { writeFileSync, readFileSync, existsSync, mkdirSync } from "node:fs";
const CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";
const URL0 = "file:///Users/panama/pilotea/design-system/ui_kits/android-app-v2/prototype.html";
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const [dir, cmd, ...args] = process.argv.slice(2);
if (!dir || !cmd) { console.log("usage: node pt.mjs <dir> start|look|tap \"text\"|tapxy X Y|back|scroll down|up|swipe-down-top|task start|done|stuck|log|stop"); process.exit(1); }
mkdirSync(dir, { recursive: true });
const portFile = dir + "/.port", countFile = dir + "/.n";
async function wsOf(port) {
  for (let i = 0; i < 50; i++) { try { const l = await (await fetch(`http://127.0.0.1:${port}/json/list`)).json(); const p = l.find((t) => t.type === "page" && t.url.startsWith("file:")) || l.find((t) => t.type === "page"); if (p) return p.webSocketDebuggerUrl; } catch {} await sleep(200); }
  throw new Error("no browser — run: node pt.mjs <dir> start");
}
async function connect(port) {
  const ws = new WebSocket(await wsOf(port)); await new Promise((r, j) => { ws.onopen = r; ws.onerror = j; });
  let id = 0; const pend = new Map();
  ws.onmessage = (m) => { const d = JSON.parse(m.data); if (d.id && pend.has(d.id)) { const p = pend.get(d.id); pend.delete(d.id); d.error ? p.j(new Error(JSON.stringify(d.error))) : p.r(d.result); } };
  const S = (method, params = {}) => new Promise((r, j) => { const i = ++id; pend.set(i, { r, j }); ws.send(JSON.stringify({ id: i, method, params })); });
  const ev = async (expr) => { const r = await S("Runtime.evaluate", { expression: expr, returnByValue: true, awaitPromise: true }); if (r.exceptionDetails) throw new Error(JSON.stringify(r.exceptionDetails).slice(0, 300)); return r.result.value; };
  return { S, ev, close: () => ws.close() };
}
const PHONE = `(function(){var s=document.querySelector('.pt-scr:not([hidden]) .phone');if(!s)return null;var r=s.getBoundingClientRect();return {x:r.left,y:r.top,w:r.width,h:r.height};})()`;
async function screen(c, note) {
  const n = (existsSync(countFile) ? +readFileSync(countFile, "utf8") : 0) + 1; writeFileSync(countFile, String(n));
  const box = await c.ev(PHONE);
  const shot = `${dir}/shot-${String(n).padStart(3, "0")}.png`;
  if (box) { const r = await c.S("Page.captureScreenshot", { format: "png", clip: { x: box.x, y: box.y, width: box.w, height: box.h, scale: 1 } }); writeFileSync(shot, Buffer.from(r.data, "base64")); }
  const info = await c.ev(`(function(){
    var p=document.querySelector('.pt-scr:not([hidden]) .phone'); var t=document.getElementById('pt-toast');
    var bar=document.querySelector('.pt-bar'); var host=document.getElementById('pt-host');
    var lines=[]; if(p){ var pr=p.getBoundingClientRect(); var dim=p.querySelector('.dim'); var tops=dim?[].slice.call(p.querySelectorAll('.sheet,.dialog')):[]; var snacks=[].slice.call(document.querySelectorAll('.pt-snack')).filter(function(x){var r=x.getBoundingClientRect(); return r.width&&r.height&&getComputedStyle(x).visibility!=='hidden'&&+getComputedStyle(x).opacity>0;});
      var walker=document.createTreeWalker(p,NodeFilter.SHOW_TEXT,null); var n, last=null;
      while((n=walker.nextNode())){ var el=n.parentElement; if(!el||el.closest('style,script'))continue; var tx=n.textContent.replace(/\\s+/g,' ').trim(); if(!tx)continue;
        var cs=getComputedStyle(el); if(cs.visibility==='hidden'||cs.display==='none'||+cs.opacity===0)continue;
        var rg=document.createRange(); rg.selectNodeContents(n); var r=rg.getBoundingClientRect(); if(!r.width||!r.height)continue;
        if(r.bottom<=pr.top+1||r.top>=pr.bottom-1||r.right<=pr.left||r.left>=pr.right)continue;
        if(snacks.some(function(x){return x.contains(el);}))continue;  // the snackbar is printed as a message
        var cx=(r.left+r.right)/2, cy=(r.top+r.bottom)/2, at=document.elementFromPoint(cx,cy);
        if(at&&!(at===el||el.contains(at))&&!(dim&&at.classList&&at.classList.contains('dim')))continue;  // hidden under a pinned bar or another layer
        var under=!!dim&&!tops.some(function(t){return t.contains(el);});
        if(under&&tops.some(function(t){var q=t.getBoundingClientRect(); return r.top>=q.top-1&&r.left>=q.left-1&&r.right<=q.right+1;}))continue;  // covered by the sheet or dialog
        if(last&&last.under===under&&r.top<last.bottom-2&&r.bottom>last.top+2){last.t+=' '+tx; last.top=Math.min(last.top,r.top); last.bottom=Math.max(last.bottom,r.bottom);}  // same visual line when the boxes overlap vertically
        else {last={top:r.top,bottom:r.bottom,under:under,t:tx}; lines.push(last);} } }
    var text=lines.map(function(l){return (l.under?'(dimmed, behind) ':'')+l.t;}).join('\\n');
    var snackTxt=snacks.map(function(x){return x.textContent.replace(/\\s+/g,' ').trim();}).join(' · ');
    return {text:text, snack:snackTxt, toast:(t&&t.classList.contains('on'))?t.textContent.trim():'',
      bar:bar?bar.innerText.replace(/\\s+/g,' ').trim():'', host:host?host.innerText.replace(/\\s+/g,' ').trim():''};})()`);
  console.log((note ? note + "\n" : "") + "— screenshot: " + shot + (box ? ` (${Math.round(box.w)}×${Math.round(box.h)} px; tapxy uses these pixels)` : ""));
  if (info.toast) console.log("— message on screen: «" + info.toast + "»");
  if (info.snack) console.log("— message on screen: «" + info.snack + "»");
  console.log("— controls above the phone: " + [info.bar, info.host].filter(Boolean).join(" · "));
  const host = await c.ev(`(function(){var p=document.querySelector('.pt-scr:not([hidden]) .phone');return !!p&&p.classList.contains('host');})()`);  // the screen's own phone, not a demo phone drawn inside it (A1)
  if (host) console.log("— this is the Uber app (with anything Kompara draws over it): LOOK at the screenshot; its text is not listed, as a driver only glances at it.");
  else console.log("— what the phone shows (text, top to bottom):\n" + info.text);
}
async function clickAt(c, x, y) { for (const type of ["mousePressed", "mouseReleased"]) await c.S("Input.dispatchMouseEvent", { type, x, y, button: "left", clickCount: 1 }); await sleep(450); }
async function key(c, k) { for (const type of ["keyDown", "keyUp"]) await c.S("Input.dispatchKeyEvent", { type, key: k, text: type === "keyDown" ? k : undefined }); await sleep(450); }
if (cmd === "start") {
  const port = 9300 + Math.floor(Math.random() * 600);
  const ch = spawn(CHROME, ["--headless=new", "--disable-gpu", `--remote-debugging-port=${port}`, `--user-data-dir=${dir}/.profile`, "--window-size=1400,1000", "--hide-scrollbars", "--no-first-run", URL0], { stdio: "ignore", detached: true });
  ch.unref(); writeFileSync(portFile, String(port)); writeFileSync(countFile, "0");
  await sleep(1500); const c = await connect(port);
  await c.S("Emulation.setDeviceMetricsOverride", { width: 1400, height: 1000, deviceScaleFactor: 1, mobile: false });
  await sleep(500); await screen(c, "Started."); c.close(); process.exit(0);
}
if (!existsSync(portFile)) { console.log("not started — run: node pt.mjs <dir> start"); process.exit(1); }
const port = +readFileSync(portFile, "utf8");
if (cmd === "stop") { try { const c = await connect(port); await c.S("Browser.close"); } catch {} console.log("stopped"); process.exit(0); }
const c = await connect(port);
try {
  if (cmd === "look") await screen(c);
  else if (cmd === "tap") {
    const text = args.join(" ");
    const hit = await c.ev(`(function(q){
      var norm=function(s){return s.replace(/\\s+/g,' ').trim();};
      var phone=document.querySelector('.pt-scr:not([hidden]) .phone'); var pr=phone?phone.getBoundingClientRect():null;
      var roots=[phone, document.querySelector('.pt-bar'), document.getElementById('pt-host')].filter(Boolean);
      var best=null,len=1e9;
      roots.forEach(function(root){ root.querySelectorAll('*').forEach(function(e){
        if(e.closest('style,script'))return; var t=norm(e.innerText||e.textContent||''); if(t.indexOf(q)===-1||t.length>len)return;
        if([].some.call(e.children,function(ch){return norm(ch.innerText||ch.textContent||'').indexOf(q)!==-1;}))return;  // a deeper element holds the text
        var r=e.getBoundingClientRect(); if(!r.width||!r.height)return; var cx=r.left+r.width/2, cy=r.top+r.height/2;
        if(root===phone && (cy<pr.top||cy>pr.bottom||cx<pr.left||cx>pr.right))return;
        var top=document.elementFromPoint(cx,cy); if(!top||!(top===e||e.contains(top)))return;
        best={x:cx,y:cy}; len=t.length; }); });
      return best;})(${JSON.stringify(text)})`);
    if (!hit) { console.log(`No visible, uncovered text «${text}» on screen (it may be off-screen — try scroll — or under a sheet). Nothing tapped.`); await screen(c); }
    else { await clickAt(c, hit.x, hit.y); await screen(c, `Tapped «${text}».`); }
  } else if (cmd === "tapxy") {
    const box = await c.ev(PHONE); const x = box.x + +args[0], y = box.y + +args[1];
    await clickAt(c, x, y); await screen(c, `Tapped at (${args[0]}, ${args[1]}) on the phone.`);
  } else if (cmd === "back") { await c.ev("history.back()"); await sleep(900); await screen(c, "Phone back gesture."); }
  else if (cmd === "scroll") {
    const box = await c.ev(PHONE); const dy = args[0] === "up" ? -320 : 320;
    await c.S("Input.dispatchMouseEvent", { type: "mouseWheel", x: box.x + box.w / 2, y: box.y + box.h / 2, deltaX: 0, deltaY: dy }); await sleep(600);
    await screen(c, `Scrolled ${args[0] || "down"}.`);
  } else if (cmd === "swipe-down-top") {
    const box = await c.ev(PHONE); const x = box.x + box.w / 2, y0 = box.y + 12;
    await c.S("Input.dispatchMouseEvent", { type: "mousePressed", x, y: y0, button: "left", clickCount: 1 });
    for (let i = 1; i <= 8; i++) { await c.S("Input.dispatchMouseEvent", { type: "mouseMoved", x, y: y0 + i * 40, button: "left", buttons: 1 }); await sleep(30); }
    await c.S("Input.dispatchMouseEvent", { type: "mouseReleased", x, y: y0 + 320, button: "left", clickCount: 1 }); await sleep(600);
    await screen(c, "Swiped down from the top of the screen.");
  } else if (cmd === "task") {
    const k = { start: "n", done: "h", stuck: "a" }[args[0]]; if (!k) throw new Error("task start|done|stuck");
    await key(c, k); await screen(c, `Facilitator: task ${args[0]}.`);
  } else if (cmd === "log") {
    const s = await c.ev(`(function(){var o={};for(var i=0;i<localStorage.length;i++){var k=localStorage.key(i);o[k]=localStorage.getItem(k);}return JSON.stringify(o);})()`);
    writeFileSync(dir + "/log.json", s); console.log("log saved: " + dir + "/log.json (" + s.length + " bytes)");
  } else console.log("unknown command " + cmd);
} finally { c.close(); }
process.exit(0);
