import {test} from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {createDemoServer} from '../../ble-internet-demo/server/server.mjs';
import {assess} from '../server/telemetry.mjs';
const installation='00000000-0000-0000-0000-000000000001',session='00000000-0000-0000-0000-000000000002';
function event(seq,source='PI_SCAN',type='WAKE',detail='packets=3'){return {seq,source,type,detail,session,boot:70,pid:1234,elapsedMs:seq*1000,utc:'2026-09-21T15:00:00Z',firstComponent:'PI_SCAN',address:'68:09:47:58:FA:E6'};}
test('durable telemetry: acknowledgements, retries, gaps, validation, old API isolation',async()=>{
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'cyd-v2-'));let server,base;
 async function start(){server=createDemoServer(path.join(dir,'state.json'));await new Promise(r=>server.listen(0,'127.0.0.1',r));base=`http://127.0.0.1:${server.address().port}`;}
 async function stop(){await new Promise(r=>server.close(r));}
 async function post(events,extra={}){return fetch(base+'/api/v2/events',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({schema:2,installation,events,...extra})});}
 try{await start();const old=await(await fetch(base+'/api/state')).json();
  assert.deepEqual(await(await post([event(1),event(2)])).json(),{ack:2});
  assert.deepEqual(await(await post([event(1)])).json(),{ack:1});
  assert.equal((await post([event(2,'MANUAL_CONNECT')])).status,409);
  assert.equal((await post([event(4)])).status,409);
  assert.equal((await post([event(3),event(5)])).status,409);
  assert.equal((await post([event(3),event(2)])).status,400);
  assert.equal((await post([event(3)],{installation:'../../escape'})).status,400);
  assert.equal((await post([{...event(3),elapsedMs:-1}])).status,400);
  assert.equal((await post([{...event(3),detail:'x'.repeat(140000)}])).status,413);
  const foreign=await fetch(base+'/api/v2/events',{method:'POST',headers:{'Content-Type':'application/json',Origin:'https://example.com'},body:JSON.stringify({schema:2,installation,events:[event(3)]})});assert.equal(foreign.status,403);
  assert.equal((await post([event(2),event(3)])).status,200);
  let data=await(await fetch(base+'/api/v2/events')).json();assert.deepEqual(data.installations[0].events.map(e=>e.seq),[1,2,3]);
  assert.deepEqual(await(await fetch(base+'/api/state')).json(),old);
  const dashboard=await fetch(base+'/diagnostics');assert.equal(dashboard.status,200);assert.match(dashboard.headers.get('content-security-policy'),/frame-ancestors 'none'/);
  await stop();await start();data=await(await fetch(base+'/api/v2/events')).json();assert.equal(data.installations[0].ack,3);assert.equal((await post([event(4)])).status,200);
 }finally{if(server?.listening)await stop();fs.rmSync(dir,{recursive:true,force:true});}
});
test('assessment distinguishes radio, CDM, manual activity, process and reboot evidence',()=>{
 assert.match(assess([]).join(' '),/пока нет/);
 let rows=[event(1,'PROCESS','CREATED'),event(2,'CDM_SERVICE','CREATED'),event(3,'CDM_APPEARED'),event(4,'LINK','SUBSCRIBED'),event(5,'LINK','REPLY_WRITTEN')];
 let report=assess(rows).join(' ');assert.match(report,/первым записан системный BLE-компонент/);assert.match(report,/ответ передан в GATT/);assert.match(report,/нет BOOT_COMPLETED/);assert.match(report,/не измеряет питание/);
 rows.splice(1,0,event(1.5,'UI','OPENED'));report=assess(rows).join(' ');assert.match(report,/окно уже открывали/);assert.doesNotMatch(report,/первым записан системный BLE-компонент/);
 rows.push({...event(6,'SYSTEM_BROADCAST','RECEIVED','android.intent.action.BOOT_COMPLETED'),boot:71,session:'00000000-0000-0000-0000-000000000003'});report=assess(rows).join(' ');assert.match(report,/Получение BOOT_COMPLETED записано/);assert.match(report,/Событий пробуждения.*пока не записано/);assert.doesNotMatch(report,/ответ передан в GATT/);
});
test('independent observer: copied bridge evidence and Binder death stay distinct',()=>{
 const copied={...event(1,'MIRROR','CDM_APPEARED / WAKE',JSON.stringify({installation,event:event(3,'CDM_APPEARED')})),app:'demo.cyd.diagnostics'};
 const death={...event(2,'OBSERVER_CONTROL','BRIDGE_PROCESS_DIED','pid=1234 session='+session),app:'demo.cyd.diagnostics'};
 const report=assess([copied,death]).join(' ');assert.match(report,/1 подтверждений смерти/);assert.match(report,/CDM_APPEARED/);assert.match(report,/подписка не подтверждена/);assert.match(report,/не обращается к процессу/);
});
