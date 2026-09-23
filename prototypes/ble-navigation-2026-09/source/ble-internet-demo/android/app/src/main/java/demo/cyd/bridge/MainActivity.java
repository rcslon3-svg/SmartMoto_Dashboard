package demo.cyd.bridge;

import android.Manifest;
import android.app.*;
import android.os.*;
import android.bluetooth.*;
import android.bluetooth.le.ScanResult;
import android.companion.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.widget.*;
import java.net.URI;
import java.util.ArrayList;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
 EditText endpoint; TextView status; final Handler handler=new Handler();
 final Runnable refresh=new Runnable(){public void run(){status.setText(prefs().getString("status","Связь ещё не запускалась")+"\n\nПлата: "+prefs().getString("address","не привязана")+"\nBluetooth-пара: "+bondStatus()+"\nЗапуск разрешён в приложении: "+(prefs().getBoolean("enabled",false)?"включён":"выключен")+"\n\n"+prefs().getString("diagnostic_status","")+"\n\nЖурнал запуска:\n"+prefs().getString("startup_log","Пока нет событий"));handler.postDelayed(this,1000);}};
 SharedPreferences prefs(){return getSharedPreferences("bridge",0);}
 @Override public void onCreate(Bundle b){super.onCreate(b);
  if("awaiting_return".equals(prefs().getString("test_phase",""))){prefs().edit().putString("test_phase","manual_open").commit();Startup.record(this,"ТЕСТ: окно открыто вручную до события CDM; автозапуск не подтверждён");}
  LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(36,50,36,30);root.setBackgroundColor(Color.rgb(239,245,247));
  ScrollView scroll=new ScrollView(this);scroll.addView(root);setContentView(scroll);
  TextView title=new TextView(this);title.setText("CYD → Интернет · 0.7");title.setTextSize(30);root.addView(title);
  TextView help=new TextView(this);help.setText("ESP32 ↔ BLE ↔ телефон ↔ сервер\n\n1. Введите адрес компьютера в Wi-Fi.\n2. Разрешите Bluetooth и привяжите плату.\n3. Нажмите «Запустить мост».\n\nПосле привязки связь запускается в фоне при появлении платы. Окно открывается нажатием уведомления.\n");root.addView(help);
  endpoint=new EditText(this);endpoint.setSingleLine();endpoint.setInputType(17);endpoint.setHint("http://192.168.1.100:8787");endpoint.setText(prefs().getString("url",""));root.addView(endpoint);
  button(root,"Диагностика BLE и CDM → компьютер",()->{
   if(AutostartTest.active){toast("Сначала отмените активный тест. Диагностика не меняет его состояние.");return;}
   if(!saveUrl())return;
   Diagnostics.start(this);
  });
  button(root,"Показать / скопировать отчёт",()->{
   String report=prefs().getString("diagnostic_report","Отчёт ещё не создан");
   new AlertDialog.Builder(this).setTitle("Диагностика приложения — не системный скан CDM").setMessage(report)
    .setPositiveButton("Копировать",(d,w)->{getSystemService(android.content.ClipboardManager.class).setPrimaryClip(android.content.ClipData.newPlainText("CYD diagnostics",report));toast("Отчёт скопирован");}).setNegativeButton("Закрыть",null).show();
  });
  button(root,"1. Сохранить адрес",()->saveUrl());
  button(root,"2. Привязать ESP32 по BLE",()->{if(permissions())pair();});
  button(root,"Создать Bluetooth-пару с выбранной платой",()->{if(permissions())bond();});
  button(root,"3. Запустить мост",()->{if(!saveUrl()||!permissions())return;if(!prefs().contains("address")){toast("Сначала привяжите плату");return;}AutostartTest.cancel(this);prefs().edit().putBoolean("enabled",true).commit();Startup.start(this,"Кнопка запуска");});
  button(root,"Остановить мост и автозапуск",()->{AutostartTest.cancel(this);prefs().edit().putBoolean("enabled",false).commit();Startup.record(this,"Пользователь выключил мост и автозапуск");stopService(new Intent(this,BridgeService.class));prefs().edit().putString("status","Остановлено пользователем").apply();});
  button(root,"Тест автозапуска без перезагрузки",()->{
   if(!prefs().getBoolean("enabled",false)){toast("Сначала запустите мост и проверьте связь");return;}
   new AlertDialog.Builder(this).setTitle("Проверка запуска при появлении платы")
    .setMessage("Оставьте ESP32 включённой и нажмите «Начать». Сначала приложение восстановит наблюдение и проверит получение события от Android.\n\nВыключайте плату только после надписи «Android обнаружил плату». Дождитесь сообщения об отсутствии платы. Затем нажмите отдельную кнопку «Плата выключена — завершить приложение». Само приложение закрываться не будет.\n\nКогда окно закроется — включите ESP32. Телефон перезагружать не нужно.")
    .setNegativeButton("Отмена",null).setPositiveButton("Начать",(dialog,which)->testExit()).show();
  });
  button(root,"Плата выключена — завершить приложение",()->{
   new AlertDialog.Builder(this).setTitle("Питание ESP32 выключено?")
    .setMessage("Подтвердите только после физического выключения платы. После закрытия приложения включите плату; приложение вручную не открывайте.")
    .setNegativeButton("Отмена",null).setPositiveButton("Да, выключено",(dialog,which)->{if(!AutostartTest.confirmExit(this,()->finishAndRemoveTask()))toast("Пока нельзя завершить: тест должен получить появление и отсутствие платы, а мост — остановиться.");}).show();
  });
  status=new TextView(this);status.setPadding(0,30,0,0);root.addView(status);
 }
 void testExit(){AutostartTest.begin(this,()->finishAndRemoveTask());}
 void button(LinearLayout root,String text,Runnable action){Button b=new Button(this);b.setText(text);b.setOnClickListener(v->{try{action.run();}catch(Exception e){toast(e.getMessage());}});root.addView(b);}
 void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
 boolean saveUrl(){try{String s=endpoint.getText().toString().trim().replaceAll("/+$","");URI u=new URI(s);if(!("http".equals(u.getScheme())||"https".equals(u.getScheme()))||u.getHost()==null||u.getUserInfo()!=null||u.getQuery()!=null||u.getFragment()!=null||!u.getPath().isEmpty())throw new Exception();prefs().edit().putString("url",s).apply();return true;}catch(Exception e){toast("Нужен адрес вида http://192.168.1.100:8787");return false;}}
 boolean permissions(){ArrayList<String> needed=new ArrayList<>();for(String p:new String[]{Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT})if(checkSelfPermission(p)!=PackageManager.PERMISSION_GRANTED)needed.add(p);if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)needed.add(Manifest.permission.POST_NOTIFICATIONS);if(!needed.isEmpty()){requestPermissions(needed.toArray(new String[0]),10);return false;}BluetoothAdapter a=getSystemService(BluetoothManager.class).getAdapter();if(a==null){toast("BLE недоступен");return false;}if(!a.isEnabled()){startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));return false;}return true;}
 void pair(){
  BluetoothLeDeviceFilter filter=new BluetoothLeDeviceFilter.Builder().setNamePattern(Pattern.compile("CYD-Internet-Demo")).build();
  getSystemService(CompanionDeviceManager.class).associate(new AssociationRequest.Builder().addDeviceFilter(filter).setSingleDevice(false).build(),new CompanionDeviceManager.Callback(){
   void chooser(IntentSender sender){try{startIntentSenderForResult(sender,20,null,0,0,0);}catch(Exception e){toast(e.getMessage());}}
   @Override public void onDeviceFound(IntentSender sender){chooser(sender);}
   @Override public void onAssociationPending(IntentSender sender){chooser(sender);}
   @android.annotation.TargetApi(33) @Override public void onAssociationCreated(AssociationInfo info){if(info.getDeviceMacAddress()!=null)runOnUiThread(()->associated(info.getDeviceMacAddress().toString()));}
   @Override public void onFailure(CharSequence reason){runOnUiThread(()->toast("Привязка: "+reason));}
  },handler);
 }
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==20&&result==RESULT_OK&&data!=null){Parcelable device=data.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE);if(device instanceof ScanResult)associated(((ScanResult)device).getDevice().getAddress());else if(device instanceof BluetoothDevice)associated(((BluetoothDevice)device).getAddress());}}
 void associated(String address){prefs().edit().putString("address",address).putBoolean("presence_known",false).commit();Startup.observe(this);bond();}
 String bondStatus(){
  if(checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)return "нет разрешения Bluetooth";
  String address=prefs().getString("address","");if(address.isEmpty())return "плата не выбрана";
  try{BluetoothAdapter a=getSystemService(BluetoothManager.class).getAdapter();if(a==null)return "Bluetooth недоступен";
   int state=a.getRemoteDevice(address).getBondState();
   if(state==BluetoothDevice.BOND_BONDED)return "сопряжено — подтверждено Android";
   if(state==BluetoothDevice.BOND_BONDING)return "сопряжение выполняется";
   return "НЕ сопряжено";
  }catch(SecurityException e){return "нет разрешения Bluetooth";}
 }
 void bond(){
  if(checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){toast("Разрешите Bluetooth");return;}
  String address=prefs().getString("address","");if(address.isEmpty()){toast("Сначала выберите плату");return;}
  try{BluetoothAdapter a=getSystemService(BluetoothManager.class).getAdapter();if(a==null||!a.isEnabled()){toast("Включите Bluetooth");return;}
   BluetoothDevice device=a.getRemoteDevice(address);
   if(device.getBondState()==BluetoothDevice.BOND_BONDED){Startup.record(this,"Bluetooth-пара уже существует в Android");toast("Сопряжено. Можно запускать мост.");return;}
   if(device.getBondState()==BluetoothDevice.BOND_BONDING){toast("Дождитесь завершения сопряжения");return;}
   boolean requested=device.createBond();Startup.record(this,requested?"Системное Bluetooth-сопряжение запрошено":"Android не начал Bluetooth-сопряжение");
   toast(requested?"Подтвердите сопряжение, если Android покажет запрос. Дождитесь статуса «сопряжено».":"Не удалось начать сопряжение. Проверьте питание платы.");
  }catch(SecurityException e){toast("Разрешение Bluetooth отозвано");}
 }
 @Override protected void onResume(){super.onResume();handler.post(refresh);}
 @Override protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}
}
