import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {createTelemetry} from '../../cyd-companion-v2/server/telemetry.mjs';
const here = path.dirname(fileURLToPath(import.meta.url));
export function createDemoServer(dataFile = path.join(here, 'data/state.json')) {
  const telemetry=createTelemetry(path.dirname(dataFile));
  let state = fs.existsSync(dataFile) ? JSON.parse(fs.readFileSync(dataFile, 'utf8')) : {revision:0,message:'Hello from PC!',counts:[0,0,0],events:[],seen:[]};
  function commit(next) {
    fs.mkdirSync(path.dirname(dataFile), {recursive:true});
    fs.writeFileSync(dataFile+'.tmp', JSON.stringify(next,null,2));
    fs.renameSync(dataFile+'.tmp', dataFile);
    state = next;
  }
  const publicState = () => ({revision:state.revision,message:state.message,counts:state.counts,events:state.events});
  return http.createServer(async (req,res) => {
    const send = (code, body) => {res.writeHead(code,{'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store'});res.end(JSON.stringify(body));};
    try {
      const route = new URL(req.url,'http://localhost').pathname;
      if(await telemetry(req,res,route)) return;
      if (req.method==='GET' && route==='/download/android.apk') {
        const apk=path.join(here,'../releases/cyd-internet-demo.apk');
        if(!fs.existsSync(apk)) return send(404,{error:'APK has not been built'});
        res.writeHead(200,{'Content-Type':'application/vnd.android.package-archive','Content-Disposition':'attachment; filename="cyd-internet-demo.apk"','Content-Length':fs.statSync(apk).size});
        fs.createReadStream(apk).pipe(res);return;
      }
      if (req.method==='GET' && route==='/') {res.writeHead(200,{'Content-Type':'text/html; charset=utf-8','Cache-Control':'no-store'});res.end(fs.readFileSync(path.join(here,'index.html')));return;}
      if (req.method==='GET' && route==='/api/state') return send(200,publicState());
      if (req.method!=='POST' || !['/api/message','/api/button','/api/diagnostics'].includes(route)) return send(404,{error:'Not found'});
      // No cross-origin writes from unrelated web pages on the local network.
      if (req.headers.origin && req.headers.origin !== `http://${req.headers.host}`) return send(403,{error:'Origin rejected'});
      if (!req.headers['content-type']?.startsWith('application/json')) return send(415,{error:'JSON required'});
      const chunks=[];let size=0;
      for await (const chunk of req) {size+=chunk.length; if (size>(route==='/api/diagnostics'?32768:2048)) return send(413,{error:'Body too large'}); chunks.push(chunk);}
      const body=Buffer.concat(chunks).toString('utf8');
      let input; try { input=JSON.parse(body); } catch {return send(400,{error:'Invalid JSON'});}
      if (!input || typeof input!=='object') return send(400,{error:'Object required'});
      if (route==='/api/diagnostics') {
        if (input.schema!==1 || typeof input.scanOutcome!=='string' || !Number.isInteger(input.packetCount) || input.packetCount<0) return send(400,{error:'Invalid diagnostic report'});
        fs.mkdirSync(path.dirname(dataFile),{recursive:true});
        const file=path.join(path.dirname(dataFile),'phone-diagnostics.json');
        fs.writeFileSync(file+'.tmp',JSON.stringify({receivedAt:new Date().toISOString(),report:input},null,2));
        fs.renameSync(file+'.tmp',file);
        return send(200,{ok:true});
      }
      const next=structuredClone(state);
      if (route==='/api/message') {
        if (typeof input.message!=='string' || !/^[\x20-\x7b\x7d-\x7e]{1,48}$/.test(input.message)) return send(400,{error:'Use 1–48 ASCII characters, without |'});
        next.message=input.message;
      } else {
        if (!Number.isInteger(input.button) || input.button<1 || input.button>3 || typeof input.id!=='string' || !/^[a-f0-9]{8}-[0-9]{1,10}$/.test(input.id)) return send(400,{error:'Invalid button or event id'});
        if (state.seen.includes(input.id)) return send(200,publicState());
        next.counts[input.button-1]++;
        next.seen=[...next.seen,input.id].slice(-256);
        next.events=[{id:input.id,button:input.button,at:new Date().toISOString()},...next.events].slice(0,30);
      }
      next.revision++;
      commit(next);
      send(200,publicState());
    } catch(e) {console.error(e.message); if(!res.headersSent) send(500,{error:'Server error'}); else res.end();}
  });
}
if (process.argv[1] && path.resolve(process.argv[1])===fileURLToPath(import.meta.url)) {
  const port=Number(process.env.PORT||8787);
  createDemoServer().listen(port,'0.0.0.0',()=>console.log(`ESP32 demo: http://localhost:${port}`));
}
