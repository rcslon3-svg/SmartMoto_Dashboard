package demo.cyd.companion;
import java.nio.*;
import java.util.*;
public class ProtocolTest {
 static int checks;
 static void check(boolean condition){checks++;if(!condition)throw new AssertionError("check "+checks);}
 static void rejects(Runnable action){boolean failed=false;try{action.run();}catch(IllegalArgumentException e){failed=true;}check(failed);}
 static byte[] packet(int op,long session,long seq,int button){return ByteBuffer.allocate(11).order(ByteOrder.LITTLE_ENDIAN).put((byte)1).put((byte)op).putInt((int)session).putInt((int)seq).put((byte)button).array();}
 public static void main(String[] args){
  var read=Protocol.parse(packet(1,0xffffffffL,0x80000000L,0));check(read.session()==4294967295L);check(read.sequence()==2147483648L);check(read.id().equals("ffffffff-2147483648"));
  for(int button=1;button<=3;button++){var p=Protocol.parse(packet(2,0x123,7,button));check(p.id().equals("00000123-7"));check(p.button()==button);}
  rejects(()->Protocol.parse(null));for(int n=0;n<24;n++){if(n==11)continue;final int len=n;rejects(()->Protocol.parse(new byte[len]));}
  for(int op=0;op<256;op++)for(int button=0;button<5;button++){final int o=op,b=button;boolean valid=op==1&&button==0||op==2&&button>=1&&button<=3;if(valid)check(Protocol.parse(packet(op,1,1,button))!=null);else rejects(()->Protocol.parse(packet(o,1,1,b)));}
  check(Protocol.response(9,2,new long[]{0,1,2},"Hello").equals("\rS|9|2|0 / 1 / 2|Hello\n"));
  for(String bad:new String[]{"","bad|line","bad\nline","кириллица","x".repeat(49)})rejects(()->Protocol.response(1,0,new long[]{0,0,0},bad));
  rejects(()->Protocol.response(1,-1,new long[]{0,0,0},"x"));rejects(()->Protocol.response(1,0,new long[]{0,-1,0},"x"));rejects(()->Protocol.response(1,0,new long[]{0,0},"x"));
  // Session/sequence reuse across a firmware retry must preserve the server deduplication key.
  Random random=new Random(42);for(int i=0;i<10000;i++){long a=Integer.toUnsignedLong(random.nextInt()),b=Integer.toUnsignedLong(random.nextInt());byte[] packet=packet(2,a,b,1);var r=Protocol.parse(packet);check(r.session()==a&&r.sequence()==b);check(r.id().equals(Protocol.parse(packet.clone()).id()));}
  System.out.println("Protocol: "+checks+" checks passed");
 }
}
