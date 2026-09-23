import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {createObserverControl} from './observer-control.mjs';
const here=path.dirname(fileURLToPath(import.meta.url));
const uuid=/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/;
export function assess(events){
 if(!events.length)return ['Данных от нового приложения пока нет.'];
 if(events.some(e=>e.app==='demo.cyd.diagnostics')){
  const deaths=events.filter(e=>e.source==='OBSERVER_CONTROL'&&e.type==='BRIDGE_PROCESS_DIED');
  const copies=new Map();let latestInstallation;for(const e of events.filter(e=>e.source==='MIRROR'))try{const m=JSON.parse(e.detail);if(m.event&&Number.isInteger(m.event.seq)){latestInstallation=m.installation;copies.set(m.installation+':'+m.event.seq,{...m.event,installation:m.installation});}}catch{}
  return [`Независимый диагност: ${deaths.length} подтверждений смерти процесса через Binder.`,...deaths.slice(-3).map(e=>`${e.utc}: ${e.detail}`),...(copies.size?assess([...copies.values()].filter(e=>e.installation===latestInstallation).sort((a,b)=>a.seq-b.seq)):['Копии событий моста ещё не поступили.']), 'Чтение этого журнала не обращается к процессу моста. Команды SNAPSHOT/ARCHIVE, если они были, записаны отдельно.'];
 }
 const last=events.at(-1), current=events.filter(e=>e.boot===last.boot);
 const output=[`Последняя записанная загрузка телефона: ${last.boot}.`];
 output.push(current.some(e=>e.source==='SYSTEM_BROADCAST'&&e.detail==='android.intent.action.BOOT_COMPLETED')?'Получение BOOT_COMPLETED записано.':'В сохранённых событиях этой загрузки нет BOOT_COMPLETED. Причина неизвестна.');
 const sessions=Map.groupBy(current,e=>e.session);
 let found=false;
 for(const [id,rows] of sessions){
  const wake=rows.find(e=>['PI_SCAN','CDM_APPEARED'].includes(e.source)&&e.type==='WAKE');
  if(!wake)continue;found=true;
  const ui=rows.some(e=>e.source==='UI'&&e.seq<wake.seq);
  const created=rows.find(e=>e.type==='CREATED'&&e.source==='PROCESS');
  const first=rows.find(e=>e.source!=='PROCESS');
  const linked=rows.find(e=>e.type==='SUBSCRIBED'&&e.seq>wake.seq);
  const reply=rows.find(e=>e.type==='REPLY_WRITTEN'&&linked&&e.seq>linked.seq);
  const manual=rows.some(e=>e.source==='MANUAL_CONNECT'&&(!linked||e.seq<linked.seq));
  output.push(`Процесс ${id.slice(0,8)}: ${wake.source}; ${created&&first&&['CDM_SERVICE','PI_SCAN'].includes(first.source)?'первым записан системный BLE-компонент':'первоначальная причина процесса не установлена или другая'}; ${ui?'окно уже открывали':'до события нет записи открытия окна'}; ${linked?'подписка GATT получена':'подписка не подтверждена'}; ${reply?'ответ передан в GATT':'передача ответа не подтверждена'}${manual?'; был ручной запрос подключения':''}.`);
 }
 if(!found)output.push('Событий пробуждения CDM/PI_SCAN в этой загрузке пока не записано.');
 output.push('Журнал не измеряет питание платы и не подтверждает будущие запуски. CDM_APPEARED на API 33 означает «рядом или подключена».');
 return output;
}
export function createTelemetry(dataDir){
 const file=path.join(dataDir,'companion-v2-events.json');
 let store=fs.existsSync(file)?JSON.parse(fs.readFileSync(file,'utf8')):{schema:2,installations:{}};
 const observerControl=createObserverControl(dataDir,id=>store.installations[id]?.events.some(e=>e.app==='demo.cyd.diagnostics'));
 return async function handle(req,res,route){
  if(await observerControl(req,res,route))return true;
  const json=(code,value)=>{res.writeHead(code,{'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store'});res.end(JSON.stringify(value));};
  if(req.method==='GET'&&['/download/companion-v2.apk','/download/diagnostics-v2.apk','/download/navprobe.apk'].includes(route)){
   const name=route.includes('navprobe')?'cyd-navprobe-1.1.0.apk':route.includes('diagnostics')?'cyd-diagnostics-2.0.3.apk':'cyd-companion-2.0.4.apk';const apk=path.join(here,'../releases',name);if(!fs.existsSync(apk)){json(404,{error:'Not built'});return true;}
   res.writeHead(200,{'Content-Type':'application/vnd.android.package-archive','Content-Disposition':`attachment; filename="${name}"`,'Content-Length':fs.statSync(apk).size});fs.createReadStream(apk).pipe(res);return true;
  }
  if(req.method==='GET'&&route==='/diagnostics'){res.writeHead(200,{'Content-Type':'text/html; charset=utf-8','Cache-Control':'no-store','Content-Security-Policy':"default-src 'self'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self'; frame-ancestors 'none'"});res.end(fs.readFileSync(path.join(here,'dashboard.html')));return true;}
  if(route!=='/api/v2/events')return false;
  if(req.method==='GET'){
   json(200,{schema:2,installations:Object.entries(store.installations).map(([id,value])=>({installation:id,...value,assessment:assess(value.events)}))});return true;
  }
  if(req.method!=='POST'){json(405,{error:'GET or POST required'});return true;}
  if(req.headers.origin&&req.headers.origin!==`http://${req.headers.host}`){json(403,{error:'Origin rejected'});return true;}
  if(!req.headers['content-type']?.startsWith('application/json')){json(415,{error:'JSON required'});return true;}
  const chunks=[];let size=0;for await(const chunk of req){size+=chunk.length;if(size>131072){json(413,{error:'Body limit'});return true;}chunks.push(chunk);}
  let input;try{input=JSON.parse(Buffer.concat(chunks));}catch{json(400,{error:'Invalid JSON'});return true;}
  if(input?.schema!==2||!uuid.test(input.installation??'')||!Array.isArray(input.events)||!input.events.length||input.events.length>80){json(400,{error:'Invalid envelope'});return true;}
  let prev=0;for(const e of input.events){
   if(!e||!Number.isSafeInteger(e.seq)||e.seq<=prev||!uuid.test(e.session??'')||!Number.isInteger(e.boot)||!Number.isInteger(e.pid)||!Number.isSafeInteger(e.elapsedMs)||e.elapsedMs<0||typeof e.utc!=='string'||!Number.isFinite(Date.parse(e.utc))||!['source','type','detail','firstComponent','address'].every(k=>typeof e[k]==='string'&&e[k].length<(k==='detail'?65536:8192))){json(400,{error:'Invalid event/order'});return true;}prev=e.seq;
  }
  // A different/restored server can first see a later batch. Its retained range stays visible.
  const existing=store.installations[input.installation]??{ack:input.events[0].seq-1,events:[]};
  if(!store.installations[input.installation]&&Object.keys(store.installations).length>=8){json(409,{error:'Installation limit'});return true;}
  let ack=existing.ack;const additions=[];
  for(const e of input.events){if(e.seq<=ack){const old=existing.events.find(v=>v.seq===e.seq);if(old&&JSON.stringify(old)!==JSON.stringify(e)){json(409,{error:'Conflicting duplicate'});return true;}continue;}if(e.seq!==ack+1){json(409,{error:'Sequence gap',expected:ack+1});return true;}additions.push(e);ack=e.seq;}
  const next=structuredClone(store);next.installations[input.installation]={ack,receivedAt:new Date().toISOString(),events:[...existing.events,...additions].slice(-20000)};
  fs.mkdirSync(dataDir,{recursive:true});fs.writeFileSync(file+'.tmp',JSON.stringify(next));fs.renameSync(file+'.tmp',file);store=next;
  // Acknowledge the submitted batch, including an older retransmission, not unseen later records.
  json(200,{ack:input.events.at(-1).seq});return true;
 };
}
