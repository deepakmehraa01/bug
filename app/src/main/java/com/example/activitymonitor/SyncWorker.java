package com.example.activitymonitor;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import org.json.JSONObject;
import java.net.URL;
import java.net.HttpURLConnection;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
public class SyncWorker extends Worker {
 public SyncWorker(@NonNull Context c,@NonNull WorkerParameters p){super(c,p);}
 @NonNull @Override public Result doWork(){
  Context c=getApplicationContext();
  String base=Store.get(c,"server"),token=Store.get(c,"token");
  if(!base.startsWith("https://")||token.isEmpty())return Result.failure();
  HttpURLConnection conn=null;
  try{
   JSONObject body=new JSONObject();
   body.put("deviceId",Store.get(c,"deviceId"));
   body.put("recordedAt",System.currentTimeMillis());
   body.put("apps",UsageReader.today(c));
   body.put("browserHistory",new org.json.JSONArray(Store.get(c,"history").isEmpty()?"[]":Store.get(c,"history")));
   URL u=new URL(base.replaceAll("/+$","")+"/api/report");
   conn=(HttpURLConnection)u.openConnection();conn.setRequestMethod("POST");conn.setConnectTimeout(12000);conn.setReadTimeout(12000);
   conn.setRequestProperty("Content-Type","application/json");conn.setRequestProperty("X-Agent-Token",token);conn.setDoOutput(true);
   byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);
   try(OutputStream out=conn.getOutputStream()){out.write(bytes);}
   int code=conn.getResponseCode();
   if(code==200)return Result.success();
   return code>=500?Result.retry():Result.failure();
  }catch(SecurityException e){return Result.failure();}
   catch(Exception e){return Result.retry();}
   finally{if(conn!=null)conn.disconnect();}
 }
}