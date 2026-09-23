import {test} from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import http from 'node:http';
import {createObserverControl} from '../server/observer-control.mjs';

test('passive remote report: durable request, retry, completion, heartbeat age and expiry',async()=>{
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'cyd-remote-'));let time=100000,server,base;
 const installation='00000000-0000-0000-0000-000000000001';
 const heartbeat={session:'session1',pid:42,utc:'2026-09-22T05:00:00Z',system:'package enabled; CDM snapshot old',lastBridge:'none',uploadAck:10};
 async function start(){const handler=createObserverControl(dir,id=>id===installation,()=>time);server=http.createServer((req,res)=>handler(req,res,req.url));await new Promise(r=>server.listen(0,'127.0.0.1',r));base=`http://127.0.0.1:${server.address().port}/api/v2/observer/`;}
 async function stop(){await new Promise(r=>server.close(r));}
 const post=(route,body,headers={})=>fetch(base+route,{method:'POST',headers:{'Content-Type':'application/json',...headers},body:JSON.stringify({installation,...body})});
 const status=async()=>(await(await fetch(base+'status')).json()).observers[0];
 try{
  await start();
  assert.equal((await post('request',{type:'TERMINATE'})).status,400);
  assert.equal((await post('request',{type:'REPORT'},{Origin:'https://other.example'})).status,403);
  assert.equal((await post('poll',{installation:'unknown',heartbeat})).status,404);
  const command=(await(await post('request',{type:'REPORT'})).json()).command;
  assert.equal((await status()).contact,'never');
  assert.equal((await post('request',{type:'REPORT'})).status,409);
  await stop();await start();
  for(let i=0;i<2;i++)assert.equal((await(await post('poll',{heartbeat})).json()).command.id,command.id);
  assert.equal((await status()).contact,'recent');
  time+=21000;assert.equal((await status()).contact,'stale');
  const result={id:command.id,outcome:'REPORT_SAVED',detail:'local report seq=11'};
  assert.equal((await(await post('poll',{heartbeat,result})).json()).command,null);
  assert.equal((await status()).command.state,'completed');
  await post('poll',{heartbeat,result});assert.equal((await status()).command.result.detail,result.detail);
  const second=(await(await post('request',{type:'REPORT'})).json()).command;
  assert.notEqual(second.id,command.id);
  await post('poll',{heartbeat,result});assert.equal((await status()).command.state,'pending');
  time+=120001;
  assert.equal((await(await post('poll',{heartbeat})).json()).command,null);
  assert.equal((await status()).command.state,'expired');
  assert.equal((await post('poll',{heartbeat:{...heartbeat,system:'x'.repeat(11000)}})).status,400);
  assert.equal((await post('poll',{heartbeat,result:{id:second.id,outcome:'arbitrary',detail:''}})).status,400);
 }finally{if(server?.listening)await stop();fs.rmSync(dir,{recursive:true,force:true});}
});

test('a LAN caller cannot enqueue commands',async()=>{
 const handler=createObserverControl(path.join(os.tmpdir(),'cyd-unused'),()=>true);
 let code,body;
 await handler({method:'POST',headers:{'content-type':'application/json'},socket:{remoteAddress:'192.168.1.10'}},{writeHead(c){code=c;},end(s){body=JSON.parse(s);}},'/api/v2/observer/request');
 assert.equal(code,403);assert.match(body.error,/loopback/);
});
