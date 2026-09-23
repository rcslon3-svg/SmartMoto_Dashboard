import fs from 'node:fs';
import path from 'node:path';
import {randomUUID} from 'node:crypto';

// Only passive reports are supported. No remote bridge starts or termination.
export function createObserverControl(dataDir, known, now = Date.now) {
 const file=path.join(dataDir,'observer-control.json');
 let state=fs.existsSync(file)?JSON.parse(fs.readFileSync(file,'utf8')):{};
 const save=()=>{fs.mkdirSync(dataDir,{recursive:true});fs.writeFileSync(file+'.tmp',JSON.stringify(state));fs.renameSync(file+'.tmp',file);};
 const expire=row=>{if(row.command?.state==='pending'&&row.command.expiresAt<=now())row.command.state='expired';};
 return async (req,res,route)=>{
  if(!['/api/v2/observer/status','/api/v2/observer/request','/api/v2/observer/poll'].includes(route))return false;
  const json=(code,data)=>{res.writeHead(code,{'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store'});res.end(JSON.stringify(data));return true;};
  if(route.endsWith('/status')&&req.method==='GET'){
   const observers=Object.entries(state).map(([installation,row])=>{expire(row);const age=row.receivedAt?Math.max(0,now()-row.receivedAt):null;return {installation,...row,ageMs:age,contact:age===null?'never':age<=20000?'recent':'stale'};});
   return json(200,{observers,serverTime:now()});
  }
  if(req.method!=='POST')return json(405,{error:'POST required'});
  if(req.headers.origin&&req.headers.origin!==`http://${req.headers.host}`)return json(403,{error:'Origin rejected'});
  if(!req.headers['content-type']?.startsWith('application/json'))return json(415,{error:'JSON required'});
  if(route.endsWith('/request')&&!['127.0.0.1','::1','::ffff:127.0.0.1'].includes(req.socket.remoteAddress))return json(403,{error:'Commands require the server computer (loopback)'});
  let size=0;const chunks=[];for await(const chunk of req){size+=chunk.length;if(size>16000)return json(413,{error:'Body limit'});chunks.push(chunk);}
  let input;try{input=JSON.parse(Buffer.concat(chunks));}catch{return json(400,{error:'Invalid JSON'});}
  if(!input||typeof input.installation!=='string'||!known(input.installation))return json(404,{error:'Unknown diagnostic installation; upload events first'});
  const row=state[input.installation]??{};expire(row);
  if(route.endsWith('/request')){
   if(input.type!=='REPORT')return json(400,{error:'Only REPORT is supported'});
   if(row.command?.state==='pending')return json(409,{error:'Request already pending',command:row.command});
   row.command={id:randomUUID(),type:'REPORT',state:'pending',createdAt:now(),expiresAt:now()+120000};
   state[input.installation]=row;save();return json(200,{command:row.command});
  }
  const h=input.heartbeat;
  if(!h||typeof h.session!=='string'||h.session.length>80||!Number.isInteger(h.pid)||typeof h.utc!=='string'||typeof h.system!=='string'||h.system.length>10000||typeof h.lastBridge!=='string'||h.lastBridge.length>2000||!Number.isSafeInteger(h.uploadAck))return json(400,{error:'Invalid heartbeat'});
  if(input.result){
   const r=input.result;
   if(typeof r.id!=='string'||!['REPORT_SAVED','ERROR'].includes(r.outcome)||typeof r.detail!=='string'||r.detail.length>2000)return json(400,{error:'Invalid result'});
   if(row.command?.id===r.id&&row.command.state==='pending')row.command={...row.command,state:'completed',completedAt:now(),result:r};
  }
  row.receivedAt=now();row.heartbeat=h;state[input.installation]=row;save();
  return json(200,{command:row.command?.state==='pending'?row.command:null});
 };
}
