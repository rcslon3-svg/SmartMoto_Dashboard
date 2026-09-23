package demo.cyd.companion;
import java.util.Locale;
/** Android Bluetooth APIs require uppercase hex; MacAddress.toString() uses lowercase. */
final class BluetoothAddress {
 static String canonical(String value){return value.toUpperCase(Locale.ROOT);}
 private BluetoothAddress(){}
}
