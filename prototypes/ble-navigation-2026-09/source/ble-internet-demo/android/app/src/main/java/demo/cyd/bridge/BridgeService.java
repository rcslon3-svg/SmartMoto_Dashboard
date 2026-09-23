package demo.cyd.bridge;

import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.os.*;
import org.json.*;
import java.net.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public class BridgeService extends Service {
 static volatile boolean running=false;
 static final UUID SERVICE=UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e"),RX=UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e"),TX=UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e"),CCCD=UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
 final Handler main=new Handler(Looper.getMainLooper());
 final ExecutorService http=Executors.newSingleThreadExecutor();
 BluetoothGatt gatt;BluetoothGattCharacteristic rx;boolean destroyed=false,busy=false;int generation=0;
 final ArrayDeque<byte[]> writes=new ArrayDeque<>();
 final Runnable reconnect=()->connect();
 final Runnable timeout=()->fail("BLE timeout; reconnecting");
 SharedPreferences prefs(){return getSharedPreferences("bridge",0);}
 void status(String s){prefs().edit().putString("status",s).apply();getSystemService(NotificationManager.class).notify(1,notification(s));}
 Notification notification(String s){PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);return new Notification.Builder(this,"bridge").setContentTitle("CYD Internet Demo").setContentText(s).setSmallIcon(android.R.drawable.stat_sys_data_bluetooth).setContentIntent(open).setOngoing(true).build();}
 @Override public void onCreate(){super.onCreate();running=true;Startup.record(this,"Создан сервис в PID="+android.os.Process.myPid());getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel("bridge","BLE-мост",NotificationManager.IMPORTANCE_LOW));startForeground(1,notification("Ожидание платы"));}
 @Override public int onStartCommand(Intent i,int flags,int id){if(!prefs().getBoolean("enabled",false)){stopSelf();return START_NOT_STICKY;}Startup.record(this,"Сервис запущен: "+(i==null?"восстановление Android":i.getStringExtra("startup_source")));if(gatt==null)connect();return START_STICKY;}
 void connect(){if(destroyed||!prefs().getBoolean("enabled",false))return;main.removeCallbacks(reconnect);String address=prefs().getString("address","");try{if(address.isEmpty())throw new IllegalStateException("Сначала привяжите плату");BluetoothAdapter a=getSystemService(BluetoothManager.class).getAdapter();if(a==null||!a.isEnabled())throw new IllegalStateException("Включите Bluetooth");if(a.getRemoteDevice(address).getBondState()!=BluetoothDevice.BOND_BONDED)throw new IllegalStateException("Нет Bluetooth-пары. Завершите сопряжение в приложении.");status("Подключение BLE…");gatt=a.getRemoteDevice(address).connectGatt(this,false,callbacks,BluetoothDevice.TRANSPORT_LE);armTimeout(20000);}catch(SecurityException e){fail("Bluetooth permission revoked");}catch(Exception e){fail(e.getMessage());}}
 void armTimeout(long ms){main.removeCallbacks(timeout);main.postDelayed(timeout,ms);}
 void fail(String reason){if(destroyed)return;main.removeCallbacks(timeout);generation++;busy=false;writes.clear();rx=null;if(gatt!=null){try{gatt.disconnect();gatt.close();}catch(SecurityException ignored){}gatt=null;}status(reason);main.removeCallbacks(reconnect);main.postDelayed(reconnect,5000);}
 final BluetoothGattCallback callbacks=new BluetoothGattCallback(){
  @Override public void onConnectionStateChange(BluetoothGatt g,int code,int state){main.post(()->{if(g!=gatt||destroyed)return;if(checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)!=android.content.pm.PackageManager.PERMISSION_GRANTED){fail("Bluetooth permission required");return;}if(code==BluetoothGatt.GATT_SUCCESS&&state==BluetoothProfile.STATE_CONNECTED){status("BLE подключён; поиск сервиса…");if(!g.discoverServices())fail("Ошибка поиска BLE сервиса");else armTimeout(12000);}else if(state==BluetoothProfile.STATE_DISCONNECTED||code!=0)fail("BLE отключён; повтор через 5 секунд");});}
  @Override public void onServicesDiscovered(BluetoothGatt g,int code){main.post(()->{if(g!=gatt||destroyed)return;if(checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)!=android.content.pm.PackageManager.PERMISSION_GRANTED){fail("Bluetooth permission required");return;}BluetoothGattService s=g.getService(SERVICE);if(code!=0||s==null){fail("Нет сервиса CYD");return;}rx=s.getCharacteristic(RX);BluetoothGattCharacteristic tx=s.getCharacteristic(TX);if(rx==null||tx==null||!g.setCharacteristicNotification(tx,true)){fail("Ошибка BLE характеристик");return;}BluetoothGattDescriptor d=tx.getDescriptor(CCCD);if(d==null){fail("Нет CCCD");return;}d.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);if(!g.writeDescriptor(d))fail("Не удалось включить BLE уведомления");else armTimeout(10000);});}
  @Override public void onDescriptorWrite(BluetoothGatt g,BluetoothGattDescriptor d,int code){main.post(()->{if(g!=gatt||destroyed)return;if(code!=0){fail("Ошибка подписки BLE");return;}main.removeCallbacks(timeout);Startup.record(BridgeService.this,"BLE подключён, подписка готова");if("process_started".equals(prefs().getString("test_phase",""))){prefs().edit().putString("test_phase","ble_connected").commit();Startup.record(BridgeService.this,"ТЕСТ пройден: новый процесс запущен через CDM, BLE подключён");}status("BLE готов. Ожидание запросов платы.");});}
  @Override public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic c){byte[] value=c.getValue().clone();main.post(()->{if(g==gatt&&!destroyed)request(value);});}
  @Override public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic c,byte[] value){byte[] copy=value.clone();main.post(()->{if(g==gatt&&!destroyed)request(copy);});}
  @Override public void onCharacteristicWrite(BluetoothGatt g,BluetoothGattCharacteristic c,int code){main.post(()->{if(g!=gatt||destroyed)return;if(code!=0){fail("Ошибка передачи ответа BLE");return;}main.removeCallbacks(timeout);writes.poll();if(writes.isEmpty())busy=false;else writeNext();});}
 };
 void request(byte[] packet){
  if(busy||rx==null||packet.length!=11||packet[0]!=1)return;
  ByteBuffer b=ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN);int operation=packet[1]&255;long session=Integer.toUnsignedLong(b.getInt(2)),seq=Integer.toUnsignedLong(b.getInt(6));int button=packet[10]&255;
  if(operation!=1&&operation!=2||operation==2&&(button<1||button>3))return;
  busy=true;int current=generation;String url=prefs().getString("url","");status("BLE → HTTP: "+(operation==1?"чтение":"кнопка "+button));
  http.execute(()->{String reply;String info;
   try{JSONObject payload=operation==2?new JSONObject().put("button",button).put("id",String.format(Locale.ROOT,"%08x-%d",session,seq)):null;
    JSONObject result=call(url+(operation==1?"/api/state":"/api/button"),payload);
    String text=result.getString("message");if(!text.matches("[\\x20-\\x7b\\x7d-\\x7e]{1,48}"))throw new Exception("Invalid server text");JSONArray counts=result.getJSONArray("counts");
    reply="S|"+seq+"|"+result.getLong("revision")+"|"+counts.getLong(0)+" / "+counts.getLong(1)+" / "+counts.getLong(2)+"|"+text+"\n";
    info="Сервер OK · "+text;
   }catch(Exception e){reply="E|"+seq+"|HTTP_ERROR\n";info="Ошибка сервера: "+e.getMessage();}
   String response=reply,display=info;main.post(()->{if(destroyed||current!=generation||gatt==null)return;status(display);byte[] data=("\r"+response).getBytes(StandardCharsets.US_ASCII);for(int offset=0;offset<data.length;offset+=20)writes.add(Arrays.copyOfRange(data,offset,Math.min(offset+20,data.length)));writeNext();});
  });
 }
 JSONObject call(String url,JSONObject payload)throws Exception{
  HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(3500);c.setReadTimeout(3500);c.setInstanceFollowRedirects(false);
  try{if(payload!=null){c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");try(var out=c.getOutputStream()){out.write(payload.toString().getBytes(StandardCharsets.UTF_8));}}if(c.getResponseCode()!=200)throw new Exception("HTTP "+c.getResponseCode());try(var in=c.getInputStream();var out=new java.io.ByteArrayOutputStream()){byte[] block=new byte[1024];int n;while((n=in.read(block))!=-1){if(out.size()+n>16384)throw new Exception("Response too large");out.write(block,0,n);}return new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));}}finally{c.disconnect();}
 }
 void writeNext(){if(checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)!=android.content.pm.PackageManager.PERMISSION_GRANTED){fail("Bluetooth permission required");return;}if(gatt==null||rx==null||writes.isEmpty())return;rx.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);rx.setValue(writes.peek());if(!gatt.writeCharacteristic(rx))fail("BLE занят; переподключение");else armTimeout(8000);}
 @Override public void onDestroy(){destroyed=true;main.removeCallbacksAndMessages(null);http.shutdownNow();if(gatt!=null){try{gatt.disconnect();gatt.close();}catch(SecurityException ignored){}}running=false;super.onDestroy();}
 @Override public IBinder onBind(Intent i){return null;}
}
