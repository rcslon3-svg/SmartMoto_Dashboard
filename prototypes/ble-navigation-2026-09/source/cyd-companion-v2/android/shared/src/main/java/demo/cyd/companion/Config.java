package demo.cyd.companion;
import android.content.*;
import java.net.URI;
final class Config {
 static android.content.SharedPreferences get(Context c){return c.getSharedPreferences("config",0);}
 static String address(Context c){return BluetoothAddress.canonical(get(c).getString("address",""));}
 static boolean enabled(Context c){return get(c).getBoolean("enabled",false);}
 static String endpoint(Context c){return get(c).getString("endpoint","");}
 static String validateEndpoint(String value){
  String s=value.trim().replaceAll("/+$",""); URI u=URI.create(s);
  if(!("http".equals(u.getScheme())||"https".equals(u.getScheme()))||u.getHost()==null||u.getUserInfo()!=null||u.getQuery()!=null||u.getFragment()!=null||!u.getPath().isEmpty())throw new IllegalArgumentException("Нужен http(s)://адрес:порт");
  return s;
 }
}
