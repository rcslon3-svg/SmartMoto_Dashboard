import {test} from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {createDemoServer} from './server.mjs';
test('HTTP round trip, deduplication, validation and persistence',async()=>{
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'ble-demo-'));const file=path.join(dir,'state.json');let server;
 const start=async()=>{server=createDemoServer(file);await new Promise(r=>server.listen(0,'127.0.0.1',r));return `http://127.0.0.1:${server.address().port}`};
 const stop=()=>new Promise(r=>server.close(r));
 try{let url=await start();const post=(route,value)=>fetch(url+route,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(value)});
 assert.equal((await fetch(url)).status,200);
 assert.equal((await post('/api/message',{message:'Test from computer'})).status,200);
 const event={button:2,id:'a1b2c3d4-1'};await post('/api/button',event);await post('/api/button',event);
 let state=await (await fetch(url+'/api/state')).json();assert.deepEqual(state.counts,[0,1,0]);assert.equal(state.message,'Test from computer');assert.equal(state.revision,2);
 assert.equal((await post('/api/button',{...event,button:4})).status,400);
 assert.equal((await post('/api/message',{message:'bad|packet'})).status,400);
 assert.equal((await post('/api/message',null)).status,400);
 const foreign=await fetch(url+'/api/button',{method:'POST',headers:{'Content-Type':'application/json',Origin:'https://example.com'},body:JSON.stringify(event)});assert.equal(foreign.status,403);
 const diagnostic={schema:1,scanOutcome:'completed',packetCount:0,startupLogBeforeScan:'Плата не обнаружена'};
 assert.equal((await post('/api/diagnostics',diagnostic)).status,200);
 assert.deepEqual(JSON.parse(fs.readFileSync(path.join(dir,'phone-diagnostics.json'),'utf8')).report,diagnostic);
 assert.equal((await post('/api/diagnostics',{schema:1})).status,400);
 assert.equal((await post('/api/diagnostics',{...diagnostic,extra:'x'.repeat(33000)})).status,413);
 const beforeState=await(await fetch(url+'/api/state')).json();assert.deepEqual(beforeState,state);
 assert.equal((await fetch(url+'/api/diagnostics')).status,404);
 await stop();url=await start();state=await(await fetch(url+'/api/state')).json();assert.deepEqual(state.counts,[0,1,0]);assert.equal(state.message,'Test from computer');
 }finally{if(server?.listening)await stop();fs.rmSync(dir,{recursive:true,force:true})}
});
