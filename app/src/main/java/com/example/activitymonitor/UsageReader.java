package com.example.activitymonitor;
import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Process;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Calendar;
import java.util.List;
public class UsageReader {
 static boolean allowed(Context c){
  AppOpsManager a=(AppOpsManager)c.getSystemService(Context.APP_OPS_SERVICE);
  return a.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,Process.myUid(),c.getPackageName())==AppOpsManager.MODE_ALLOWED;
 }
 static JSONArray today(Context c)throws Exception{
  if(!allowed(c))throw new SecurityException("Usage Access not enabled");
  Calendar cal=Calendar.getInstance();
  cal.set(Calendar.HOUR_OF_DAY,0);cal.set(Calendar.MINUTE,0);cal.set(Calendar.SECOND,0);cal.set(Calendar.MILLISECOND,0);
  UsageStatsManager m=(UsageStatsManager)c.getSystemService(Context.USAGE_STATS_SERVICE);
  List<UsageStats> stats=m.queryUsageStats(UsageStatsManager.INTERVAL_DAILY,cal.getTimeInMillis(),System.currentTimeMillis());
  JSONArray result=new JSONArray();
  if(stats!=null)for(UsageStats s:stats){
   if(s.getTotalTimeInForeground()<=0)continue;
   JSONObject o=new JSONObject();o.put("package",s.getPackageName());o.put("foregroundMs",s.getTotalTimeInForeground());o.put("lastUsed",s.getLastTimeUsed());result.put(o);
  }
  return result;
 }
}