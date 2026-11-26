// CDP screenshot at true device metrics: node shot.js <url> <out.png> [width] [height] [dsf]
const { spawn } = require('child_process');
const fs = require('fs');
const url = process.argv[2], out = process.argv[3];
const EV = process.argv[7] || ''; const W = +(process.argv[4] || 390), H = +(process.argv[5] || 760), DSF = +(process.argv[6] || 2);

const chrome = spawn('google-chrome', ['--headless', '--disable-gpu', '--no-sandbox', '--autoplay-policy=no-user-gesture-required', '--hide-scrollbars', '--remote-debugging-port=0', 'about:blank'], { stdio: ['ignore', 'pipe', 'pipe'] });
let wsUrl = null, done = false;
const killer = setTimeout(() => { console.error('TIMEOUT'); cleanup(2); }, 400000);
function cleanup(code) { if (done) return; done = true; try { chrome.kill('SIGKILL'); } catch (e) {} clearTimeout(killer); process.exit(code); }
chrome.stderr.on('data', d => {
  const m = String(d).match(/DevTools listening on (ws:\/\/\S+)/);
  if (m && !wsUrl) { wsUrl = m[1]; main().catch(e => { console.error('ERR', e.message); cleanup(1); }); }
});
async function main() {
  const ws = new WebSocket(wsUrl);
  let id = 0; const pending = {};
  const send = (method, params, sessionId) => new Promise((res, rej) => {
    const i = ++id; pending[i] = { res, rej };
    ws.send(JSON.stringify({ id: i, method, params: params || {}, sessionId }));
  });
  ws.onmessage = e => {
    const m = JSON.parse(e.data);
    if (m.id && pending[m.id]) {
      m.error ? pending[m.id].rej(new Error(m.error.message)) : pending[m.id].res(m.result);
      delete pending[m.id];
    }
  };
  await new Promise(r => ws.onopen = r);
  const { targetId } = await send('Target.createTarget', { url: 'about:blank' });
  const { sessionId } = await send('Target.attachToTarget', { targetId, flatten: true });
  await send('Emulation.setDeviceMetricsOverride', { width: W, height: H, deviceScaleFactor: DSF, mobile: true }, sessionId);
  await send('Page.enable', {}, sessionId); if (process.env.THROTTLE) await send('Emulation.setCPUThrottlingRate', { rate: +process.env.THROTTLE }, sessionId);
  await send('Page.navigate', { url }, sessionId);
  await new Promise(r => setTimeout(r, 2500));
  const ev = async (x) => { const r = await send('Runtime.evaluate', { expression: x, awaitPromise: true, returnByValue: true }, sessionId); return r.result && r.result.value !== undefined ? r.result.value : (r.exceptionDetails ? 'EXC '+JSON.stringify(r.exceptionDetails).slice(0,300) : null); };
  for (const part of EV.split('@@')) { if (!part) continue; const v = await ev(part); if (v !== null && v !== undefined) console.log('EV:', typeof v==='string'? v : JSON.stringify(v)); await new Promise(r => setTimeout(r, 1500)); }
  const shot = await send('Page.captureScreenshot', { format: 'png' }, sessionId);
  fs.writeFileSync(out, Buffer.from(shot.data, 'base64'));
  console.log('saved', out);
  cleanup(0);
}
