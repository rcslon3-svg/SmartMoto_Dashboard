package demo.cyd.companion;

import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.companion.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
 final Handler handler=new Handler(Looper.getMainLooper());TextView status,log;EditText endpoint;boolean visible;
 final Runnable refresh=new Runnable(){public void run(){render();handler.postDelayed(this,1500);}};
 @Override public void onCreate(Bundle saved){super.onCreate(saved);Journal.event(this,"UI","OPENED","Manual activity entry; no automatic re-registration or connection");Diagnostics.snapshot(this,"UI");
  LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);int pad=(int)(16*getResources().getDisplayMetrics().density);root.setPadding(pad,pad,pad,pad);
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(root);setContentView(scroll);
  root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(pad,pad+bars.top,pad,pad+bars.bottom);return insets;});
  text(root,"CYD Companion 2.0.4",26);text(root,"После 60 секунд без BLE-соединения процесс завершится автоматически. Наблюдение за платой сохраняется.",14);text(root,"Связь с ESP32 в фоне. Окно открывается через уведомление. События передаются отдельному приложению «CYD Диагност».",16);
  status=text(root,"",16);endpoint=new EditText(this);endpoint.setSingleLine(true);endpoint.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);endpoint.setText(Config.endpoint(this).isEmpty()?"http://192.168.1.101:8787":Config.endpoint(this));root.addView(endpoint);
  button(root,"1 · Сохранить сервер",()->{String url=Config.validateEndpoint(endpoint.getText().toString());if(Config.enabled(this))throw new IllegalStateException("Сначала выключите наблюдение для смены сервера");Config.get(this).edit().putString("endpoint",url).commit();Journal.event(this,"USER","ENDPOINT_SAVED",url);});
  button(root,"2 · Разрешения и выбор платы",()->{if(Config.enabled(this))throw new IllegalStateException("Сначала выключите наблюдение");if(permissions())associate();});
  button(root,"Создать системную Bluetooth-пару",()->{if(permissions())bond();});
  button(root,"3 · Включить наблюдение и связь",()->enable(false));
  button(root,"Выключить наблюдение и связь",()->Monitoring.disable(this));
  button(root,"Завершить процесс",this::terminateProcess);
  text(root,"«Завершить процесс» закрывает мост, сохраняя текущую настройку наблюдения BLE. Android может снова запустить его по системному событию.",14);
  button(root,"Подключиться вручную",()->Monitoring.wake(this,"MANUAL_CONNECT","Explicit user action; not an autostart test"));
  button(root,"Диагностика: только наблюдение",()->enable(true));
  text(root,"В режиме диагностики приложение не подключается к плате. Записываются независимые события CDM и BLE-скана. Чтобы вернуть обмен данными, нажмите «Включить наблюдение и связь».",14);
  button(root,"Снимок состояния и отправка журнала",()->{Diagnostics.snapshot(this,"USER_DIAGNOSTICS");});
  button(root,"Настройки приложения в Android",()->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))));
  button(root,"Скопировать последние события",()->{getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("CYD Companion 2",Journal.recent(this)));toast("Скопировано");});
  text(root,"Журнал фактов",20);log=text(root,"",13);
 }
 TextView text(LinearLayout root,String s,int size){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setPadding(0,10,0,10);root.addView(v);return v;}
 void button(LinearLayout root,String s,Runnable r){Button b=new Button(this);b.setText(s);b.setAllCaps(false);root.addView(b);b.setOnClickListener(v->{try{r.run();render();}catch(Exception e){Journal.event(this,"UI","ACTION_ERROR",e.toString());toast(e.getMessage());}});}
 void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
 void terminateProcess(){
  Journal.event(this,"USER_PROCESS_EXIT","REQUESTED","pid="+android.os.Process.myPid()+" session="+Journal.session+"; enabled="+Config.enabled(this)+"; observation unchanged; not force-stop");
  handler.removeCallbacks(refresh);
  finishAndRemoveTask();
  // Removing the activity task first avoids retaining a visible activity that requests recreation.
  // Do not stop observation or disable the package; this deliberately kills only our Linux process.
  new Handler(Looper.getMainLooper()).postDelayed(()->android.os.Process.killProcess(android.os.Process.myPid()),250);
 }
 boolean permissions(){ArrayList<String> missing=new ArrayList<>();for(String p:new String[]{Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.POST_NOTIFICATIONS})if(checkSelfPermission(p)!=PackageManager.PERMISSION_GRANTED)missing.add(p);if(!missing.isEmpty()){requestPermissions(missing.toArray(new String[0]),10);toast("После разрешения нажмите кнопку ещё раз");return false;}return true;}
 void associate(){
  if(!getSystemService(CompanionDeviceManager.class).getMyAssociations().isEmpty()){
   var items=getSystemService(CompanionDeviceManager.class).getMyAssociations();String[] names=items.stream().map(a->"ID "+a.getId()+" · "+a.getDeviceMacAddress()).toArray(String[]::new);
   new AlertDialog.Builder(this).setTitle("Сохранённые привязки этого приложения").setItems(names,(d,w)->selected(items.get(w))).setNeutralButton("Новая плата",(d,w)->chooser()).setNegativeButton("Отмена",null).show();return;
  }chooser();
 }
 void chooser(){BluetoothLeDeviceFilter filter=new BluetoothLeDeviceFilter.Builder().setNamePattern(Pattern.compile("CYD-Internet-Demo")).build();getSystemService(CompanionDeviceManager.class).associate(new AssociationRequest.Builder().addDeviceFilter(filter).setSingleDevice(false).build(),getMainExecutor(),new CompanionDeviceManager.Callback(){
  @Override public void onAssociationPending(IntentSender sender){try{startIntentSenderForResult(sender,20,null,0,0,0);}catch(Exception e){Journal.event(MainActivity.this,"ASSOCIATION","CHOOSER_ERROR",e.toString());toast(e.getMessage());}}
  @Override public void onAssociationCreated(AssociationInfo a){selected(a);}
  @Override public void onFailure(CharSequence reason){Journal.event(MainActivity.this,"ASSOCIATION","FAILED",String.valueOf(reason));toast(String.valueOf(reason));}
 });}
 void selected(AssociationInfo a){if(a.getDeviceMacAddress()==null){toast("У привязки нет MAC-адреса");return;}Config.get(this).edit().putString("address",a.getDeviceMacAddress().toString()).putInt("association",a.getId()).commit();Journal.event(this,"ASSOCIATION","SELECTED","id="+a.getId());Diagnostics.snapshot(this,"ASSOCIATION");render();}
 void bond(){if(Config.address(this).isEmpty())throw new IllegalStateException("Сначала выберите плату");try{BluetoothAdapter adapter=getSystemService(BluetoothManager.class).getAdapter();if(adapter==null||!adapter.isEnabled())throw new IllegalStateException("Включите Bluetooth");BluetoothDevice device=adapter.getRemoteDevice(Config.address(this));if(device.getBondState()==BluetoothDevice.BOND_BONDED){Journal.event(this,"USER","BOND_ALREADY_PRESENT","Android reports BOND_BONDED");toast("Android подтверждает: пара уже существует");}else{boolean accepted=device.createBond();Journal.event(this,"USER","BOND_REQUEST","accepted="+accepted+"; completion not yet confirmed");toast(accepted?"Android начал сопряжение; подтвердите системный запрос":"Android не начал сопряжение");}Diagnostics.snapshot(this,"BOND_REQUEST");}catch(SecurityException e){throw new IllegalStateException("Разрешение Bluetooth отозвано",e);}}
 void enable(boolean only){if(!permissions())return;if(Config.endpoint(this).isEmpty())throw new IllegalStateException("Сохраните адрес сервера");if(!Monitoring.associated(this))throw new IllegalStateException("Выберите плату через системное окно");
  // Mode changes are explicit and recorded; never reset CDM's presence history.
  Config.get(this).edit().putBoolean("enabled",true).putBoolean("observe_only",only).commit();if(only)stopService(new Intent(this,LinkService.class));Journal.event(this,"USER","MODE_CHANGED",only?"OBSERVE_ONLY":"NORMAL");Monitoring.arm(this,"USER_ENABLE");Diagnostics.snapshot(this,"USER_ENABLE");
 }
 void render(){if(status==null)return;status.setText("Плата: "+(Config.address(this).isEmpty()?"не выбрана":Config.address(this))+"\nНаблюдение запрошено: "+(Config.enabled(this)?"да":"нет")+"\nРежим: "+(Config.get(this).getBoolean("observe_only",false)?"только наблюдение":"обмен данными")+"\nСвязь в текущем процессе: "+(LinkService.ready?"подписка BLE готова":(LinkService.active?Config.get(this).getString("link_state",""):"сервис не запущен"))+"\nПередача журнала: "+Config.get(this).getString("upload","ожидается")+"\nЗагрузка: "+Journal.boot+" · PID "+android.os.Process.myPid()+"\n");log.setText(Journal.recent(this));}
 @Override protected void onResume(){super.onResume();visible=true;Journal.event(this,"UI","RESUMED","Window visible");handler.post(refresh);}
 @Override protected void onPause(){visible=false;handler.removeCallbacks(refresh);Journal.event(this,"UI","PAUSED","Window left foreground; process may still be alive");super.onPause();}
}

