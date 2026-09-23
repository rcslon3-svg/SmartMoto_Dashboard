package demo.cyd.companion;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;

/** Wire format of the existing CYD firmware; no Android dependencies. */
public final class Protocol {
 public record Request(int operation, long session, long sequence, int button) {
  public String id() { return String.format(Locale.ROOT,"%08x-%d",session,sequence); }
 }
 public static Request parse(byte[] p) {
  if(p==null||p.length!=11||p[0]!=1) throw new IllegalArgumentException("length/version");
  int op=p[1]&255, button=p[10]&255;
  if((op!=1&&op!=2)||(op==2&&(button<1||button>3))||(op==1&&button!=0)) throw new IllegalArgumentException("operation/button");
  ByteBuffer b=ByteBuffer.wrap(p).order(ByteOrder.LITTLE_ENDIAN);
  return new Request(op,Integer.toUnsignedLong(b.getInt(2)),Integer.toUnsignedLong(b.getInt(6)),button);
 }
 public static String response(long seq,long revision,long[] counts,String message) {
  if(revision<0||counts.length!=3||!message.matches("[\\x20-\\x7b\\x7d-\\x7e]{1,48}")) throw new IllegalArgumentException("server response");
  for(long c:counts) if(c<0) throw new IllegalArgumentException("negative count");
  return "\rS|"+seq+"|"+revision+"|"+counts[0]+" / "+counts[1]+" / "+counts[2]+"|"+message+"\n";
 }
 public static String response(long seq,long revision,long[] counts,String message,String phoneTime) {
  if(phoneTime==null||!phoneTime.matches("(?:[01][0-9]|2[0-3])\\.[0-5][0-9]\\.[0-5][0-9]"))throw new IllegalArgumentException("phone time");
  String legacy=response(seq,revision,counts,message);
  return legacy.substring(0,legacy.length()-1)+"|"+phoneTime+"\n";
 }
 private Protocol() {}
}
