package demo.cyd.companion;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;

/** Reads only this application's local database. No polling/binding of the bridge. */
public class ObserverActivity extends Activity {
 TextView status,log,system;EditText endpoint;final Handler handler=new Handler(Looper.getMainLooper());
 final Runnable refresh=new Runnable(){public void run(){status.setText(ObserverControl.status+"\n\nСвязь с компьютером: "+Config.get(ObserverActivity.this).getString("remote_status","ещё не подтверждена")+"\n\nПоследнее событие моста: "+Config.get(ObserverActivity.this).getString("last_bridge_event","ещё не получено")+"\n\nОтправка на сервер: "+Config.get(ObserverActivity.this).getString("upload","ожидается"));log.setText(Journal.recent(ObserverActivity.this));system.setText(SystemStatus.read(ObserverActivity.this,true));handler.postDelayed(this,1000);}};
 @Override public void onCreate(Bundle b){super.onCreate(b);Journal.event(this,"OBSERVER_UI","OPENED","Reading own journal; bridge not contacted");
  LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);int pad=(int)(16*getResources().getDisplayMetrics().density);root.setPadding(pad,pad,pad,pad);ScrollView scroll=new ScrollView(this);scroll.addView(root);setContentView(scroll);root.setOnApplyWindowInsetsListener((v,i)->{var bars=i.getInsets(WindowInsets.Type.systemBars());v.setPadding(pad,pad+bars.top,pad,pad+bars.bottom);return i;});
  text(root,"CYD Диагност",26);text(root,"Отдельное приложение. Получает копии событий моста, сохраняет и отправляет их на компьютер. Просмотр журнала не запускает мост.",16);
  endpoint=new EditText(this);endpoint.setSingleLine(true);endpoint.setText(Config.endpoint(this).isEmpty()?"http://192.168.1.101:8787":Config.endpoint(this));root.addView(endpoint);
  button(root,"Сохранить адрес сервера",()->{String url=Config.validateEndpoint(endpoint.getText().toString());Config.get(this).edit().putString("endpoint",url).commit();Journal.event(this,"OBSERVER_UI","ENDPOINT_SAVED",url);startRemote();});
  text(root,"При открытии диагноста начинается часовой сеанс связи с компьютером. Он передаёт состояние и принимает запросы отчёта без запуска моста. Состояние связи и время последнего ответа показаны ниже.",14);
  button(root,"Начать сеанс связи с компьютером",()->{Config.get(this).edit().putBoolean("remote_enabled",true).commit();startRemote();});
  button(root,"Остановить сеанс связи с компьютером",()->{Config.get(this).edit().putBoolean("remote_enabled",false).commit();stopService(new Intent(this,ObserverRemoteService.class));});
  button(root,"Завершить процесс моста",()->ObserverControl.terminate(this));
  text(root,"Кнопка завершает процесс, сохраняя наблюдение BLE. Это не системная «Принудительная остановка». Диагност подтверждает смерть через Binder и дальше не обращается к мосту. Если мост не работал, команда может сначала запустить его — это будет записано.",14);
  button(root,"Отправить сохранённую диагностику",()->{Journal.event(this,"OBSERVER_UI","UPLOAD_REQUEST","Local records only; no bridge contact");EventUpload.request(this);});
  button(root,"Копировать последние события",()->{getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("CYD diagnostics",Journal.recent(this)));});
  button(root,"Запросить состояние моста — запустит его",()->send("SNAPSHOT"));
  button(root,"Забрать архив моста — запустит его",()->ObserverArchive.start(this));
  text(root,"Две последние команды нарушают наблюдение за самостоятельным запуском. Во время проверки их не нажимайте. Диагност не читает закрытые системные журналы CDM: он получает события и снимки, доступные мосту через публичные API.",14);
  status=text(root,"",17);text(root,"Состояние из Android",20);system=text(root,"",14);text(root,"Сохранённые события",20);log=text(root,"",13);
 }
 void send(String command){Journal.event(this,"OBSERVER_UI","EXPLICIT_BRIDGE_REQUEST",command+"; may start bridge");sendBroadcast(ObserverControl.command(command),EventRelay.PERMISSION);}
 TextView text(LinearLayout root,String text,int size){TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setPadding(0,12,0,12);root.addView(v);return v;}
 void button(LinearLayout root,String text,Runnable action){Button b=new Button(this);b.setText(text);b.setAllCaps(false);root.addView(b);b.setOnClickListener(v->{try{action.run();}catch(Exception e){Journal.event(this,"OBSERVER_UI","ERROR",e.toString());Toast.makeText(this,e.toString(),Toast.LENGTH_LONG).show();}});}
 void startRemote(){if(Config.endpoint(this).isEmpty()||!Config.get(this).getBoolean("remote_enabled",true))return;try{startForegroundService(new Intent(this,ObserverRemoteService.class));}catch(RuntimeException e){Journal.event(this,"OBSERVER_REMOTE","START_FAILED",e.toString());Config.get(this).edit().putString("remote_status","Не запущено: "+e).apply();}}
 @Override protected void onResume(){super.onResume();handler.post(refresh);startRemote();}
 @Override protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}
}


