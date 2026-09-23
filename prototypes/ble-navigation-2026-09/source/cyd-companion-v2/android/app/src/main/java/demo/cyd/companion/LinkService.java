package demo.cyd.companion;

import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.os.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;
import java.nio.charset.StandardCharsets;

/** Single main-thread GATT owner. Every async result is checked against its connection generation. */
public class LinkService extends Service {
 static volatile boolean ready=false, active=false;
 static final UUID NUS=UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e"),RX=UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e"),TX=UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e"),CCCD=UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
 final Handler main=new Handler(Looper.getMainLooper());final ExecutorService worker=Executors.newSingleThreadExecutor();
 BluetoothGatt gatt;BluetoothGattCharacteristic rx;boolean dead,busy,started;int generation,attempt;long retryDeadline;
 String phase="CREATED",requestId=""; final ArrayDeque<byte[]> writes=new ArrayDeque<>();
 final Runnable timeout=()->failure("OPERATION_TIMEOUT",phase);
 final Runnable reconnect=()->connect();
 void record(String type,String detail){Journal.event(this,"LINK",type,detail);}
 void state(String s){phase=s;Config.get(this).edit().putString("link_state",s).apply();getSystemService(NotificationManager.class).notify(1,notification(s));}
 Notification notification(String text){return new Notification.Builder(this,"connection").setContentTitle("CYD Companion 2").setContentText(text).setSmallIcon(android.R.drawable.stat_sys_data_bluetooth).setOngoing(true).setContentIntent(PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE)).build();}
 @Override public void onCreate(){super.onCreate();active=true;record("SERVICE_CREATED","pid="+android.os.Process.myPid());getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel("connection","Соединение с платой",NotificationManager.IMPORTANCE_LOW));try{startForeground(1,notification("Подключение"));}catch(Exception e){record("FOREGROUND_ERROR",e.toString());stopSelf();}}
 @Override public int onStartCommand(Intent i,int flags,int id){record("START_REQUEST",i==null?"null intent":i.getStringExtra("source"));if(!Config.enabled(this)||Config.get(this).getBoolean("observe_only",false)||!Monitoring.permissions(this)){stopSelf();return START_NOT_STICKY;}if(!started){started=true;retryDeadline=SystemClock.elapsedRealtime()+90000;connect();}else record("COALESCED",phase);return START_NOT_STICKY;}
 void arm(long ms){main.removeCallbacks(timeout);main.postDelayed(timeout,ms);}
 void connect(){if(dead)return;state("CONNECTING");attempt++;record("CONNECT_ATTEMPT","attempt="+attempt+" generation="+generation);
  try{BluetoothAdapter a=getSystemService(BluetoothManager.class).getAdapter();if(a==null||!a.isEnabled())throw new IllegalStateException("Bluetooth off");gatt=a.getRemoteDevice(Config.address(this)).connectGatt(this,false,callback,BluetoothDevice.TRANSPORT_LE);if(gatt==null)throw new IllegalStateException("connectGatt returned null");arm(20000);}catch(SecurityException e){failure("PERMISSION_REVOKED",e.toString());}catch(Exception e){failure("CONNECT_ERROR",e.toString());}
 }
 boolean current(BluetoothGatt g){return !dead&&g==gatt;}
 void closeGatt(){BridgeApp.connection(false);generation++;ready=false;busy=false;writes.clear();rx=null;main.removeCallbacks(timeout);BluetoothGatt old=gatt;gatt=null;if(old!=null)try{old.disconnect();old.close();}catch(SecurityException e){record("CLOSE_ERROR",e.toString());}}
 void failure(String type,String detail){if(dead)return;record(type,detail);closeGatt();main.removeCallbacks(reconnect);if(SystemClock.elapsedRealtime()>=retryDeadline||!Config.enabled(this)||!Monitoring.permissions(this)){state("WAITING_FOR_PRESENCE");record("RETRY_WINDOW_ENDED","Waiting for next system event; no process-kill test");stopSelf();return;}long delay=Math.min(15000,2000L<<Math.min(attempt,3));state("RETRY_IN_"+delay+"MS");main.postDelayed(reconnect,delay);}
 final BluetoothGattCallback callback=new BluetoothGattCallback(){
  @Override public void onConnectionStateChange(BluetoothGatt g,int status,int next){main.post(()->{if(!current(g))return;record("GATT_STATE","status="+status+" state="+next);try{if(status==0&&next==BluetoothProfile.STATE_CONNECTED){BridgeApp.connection(true);state("DISCOVERING");if(!g.discoverServices())throw new IllegalStateException("discoverServices rejected");arm(12000);}else if(status!=0||next==BluetoothProfile.STATE_DISCONNECTED){if(ready){retryDeadline=SystemClock.elapsedRealtime()+90000;attempt=0;}failure("DISCONNECTED","status="+status);}}catch(SecurityException e){failure("PERMISSION_REVOKED",e.toString());}catch(Exception e){failure("DISCOVERY_ERROR",e.toString());}});}
  @Override public void onServicesDiscovered(BluetoothGatt g,int status){main.post(()->{if(!current(g))return;try{if(status!=0)throw new IllegalStateException("status="+status);BluetoothGattService s=g.getService(NUS);if(s==null)throw new IllegalStateException("NUS absent");rx=s.getCharacteristic(RX);BluetoothGattCharacteristic tx=s.getCharacteristic(TX);if(rx==null||tx==null||!g.setCharacteristicNotification(tx,true))throw new IllegalStateException("characteristics/notification");BluetoothGattDescriptor d=tx.getDescriptor(CCCD);if(d==null)throw new IllegalStateException("CCCD absent");state("SUBSCRIBING");int code=g.writeDescriptor(d,BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);if(code!=BluetoothStatusCodes.SUCCESS)throw new IllegalStateException("writeDescriptor="+code);arm(10000);}catch(SecurityException e){failure("PERMISSION_REVOKED",e.toString());}catch(Exception e){failure("SUBSCRIBE_ERROR",e.toString());}});}
  @Override public void onDescriptorWrite(BluetoothGatt g,BluetoothGattDescriptor d,int status){main.post(()->{if(!current(g))return;if(status!=0){failure("CCCD_ERROR","status="+status);return;}main.removeCallbacks(timeout);ready=true;attempt=0;state("READY");record("SUBSCRIBED","GATT ready; server round-trip not yet proven");});}
  @Override public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic c,byte[] value){byte[] copy=value.clone();main.post(()->{if(current(g)&&TX.equals(c.getUuid()))request(copy);});}
  @Override public void onCharacteristicWrite(BluetoothGatt g,BluetoothGattCharacteristic c,int status){main.post(()->{if(!current(g))return;if(status!=0){failure("WRITE_ERROR","status="+status);return;}main.removeCallbacks(timeout);writes.poll();if(writes.isEmpty()){busy=false;record("REPLY_WRITTEN","request="+requestId+"; GATT acknowledgement, not screen confirmation");}else writeNext();});}
 };
 void request(byte[] bytes){if(!ready)return;Protocol.Request request;try{request=Protocol.parse(bytes);}catch(Exception e){record("INVALID_PACKET",e.toString());return;}
  if(busy){record("BUSY_PACKET","ignored request="+request.id()+"; firmware retries");return;}busy=true;arm(10000);requestId=request.id();int token=generation;String endpoint=Config.endpoint(this);record("HTTP_REQUEST","id="+requestId+" op="+request.operation());
  worker.execute(()->{String response;String error="";try{JSONObject input=request.operation()==2?new JSONObject().put("id",request.id()).put("button",request.button()):null;JSONObject result=Http.call(endpoint+(input==null?"/api/state":"/api/button"),input);JSONArray a=result.getJSONArray("counts");if(a.length()!=3)throw new IllegalArgumentException("counts length");response=Protocol.response(request.sequence(),result.getLong("revision"),new long[]{a.getLong(0),a.getLong(1),a.getLong(2)},result.getString("message"),java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH.mm.ss",Locale.ROOT)));}catch(Exception e){error=e.toString();response="\rE|"+request.sequence()+"|HTTP_ERROR\n";}
   String reply=response,problem=error;main.post(()->{if(dead||generation!=token||gatt==null){record("STALE_HTTP_RESULT","id="+request.id());return;}record(problem.isEmpty()?"HTTP_OK":"HTTP_ERROR","id="+request.id()+" "+problem);byte[] data=reply.getBytes(StandardCharsets.US_ASCII);for(int offset=0;offset<data.length;offset+=20)writes.add(Arrays.copyOfRange(data,offset,Math.min(offset+20,data.length)));writeNext();});
  });
 }
 void writeNext(){try{if(gatt==null||rx==null||writes.isEmpty())return;int code=gatt.writeCharacteristic(rx,writes.peek(),BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);if(code!=BluetoothStatusCodes.SUCCESS)throw new IllegalStateException("writeCharacteristic="+code);arm(8000);}catch(SecurityException e){failure("PERMISSION_REVOKED",e.toString());}catch(Exception e){failure("WRITE_START_ERROR",e.toString());}}
 @Override public void onDestroy(){dead=true;active=false;main.removeCallbacksAndMessages(null);closeGatt();worker.shutdownNow();Config.get(this).edit().putString("link_state","SERVICE_STOPPED").apply();record("SERVICE_DESTROYED","No implication about application process lifetime");super.onDestroy();}
 @Override public IBinder onBind(Intent i){return null;}
}

