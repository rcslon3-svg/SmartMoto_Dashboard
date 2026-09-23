package demo.cyd.companion;
import java.util.Locale;
public class BluetoothAddressTest {
 public static void main(String[] args){
  Locale original=Locale.getDefault();
  try{for(Locale locale:new Locale[]{Locale.ROOT,Locale.forLanguageTag("tr-TR")}){
   Locale.setDefault(locale);
   for(String input:new String[]{"68:09:47:58:fa:e6","68:09:47:58:Fa:E6","68:09:47:58:FA:E6"}){
    String actual=BluetoothAddress.canonical(input);
    if(!actual.equals("68:09:47:58:FA:E6")||!actual.matches("([0-9A-F]{2}:){5}[0-9A-F]{2}"))throw new AssertionError(input);
    if(!BluetoothAddress.canonical(actual).equals(actual))throw new AssertionError("not idempotent");
   }
   if(!BluetoothAddress.canonical("").isEmpty())throw new AssertionError("unset address changed");
  }}finally{Locale.setDefault(original);}
  System.out.println("Bluetooth address regression: passed (saved lowercase, mixed case, uppercase, unset, locale independence)");
 }
}
