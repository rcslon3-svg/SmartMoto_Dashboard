package demo.cyd.companion;
import org.json.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
final class Http {
 static JSONObject call(String url,JSONObject payload)throws Exception {
  HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection(); c.setConnectTimeout(4000);c.setReadTimeout(4000);c.setInstanceFollowRedirects(false);
  try{if(payload!=null){c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");try(var out=c.getOutputStream()){out.write(payload.toString().getBytes(StandardCharsets.UTF_8));}}
   int code=c.getResponseCode();if(code!=200)throw new java.io.IOException("HTTP "+code);
   try(var in=c.getInputStream();var out=new java.io.ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(out.size()+n>131072)throw new java.io.IOException("Response limit");out.write(b,0,n);}return new JSONObject(out.toString(StandardCharsets.UTF_8.name()));}
  }finally{c.disconnect();}
 }
}
