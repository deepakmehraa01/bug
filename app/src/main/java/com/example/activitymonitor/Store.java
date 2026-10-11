package com.example.activitymonitor;
import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
public class Store {
 static SharedPreferences prefs(Context c){return c.getSharedPreferences("lab",Context.MODE_PRIVATE);}
 static String get(Context c,String k){return prefs(c).getString(k,"");}
 static void put(Context c,String k,String v){prefs(c).edit().putString(k,v).apply();}
 static synchronized void addUrl(Context c,String url){
  try{
   JSONArray old=new JSONArray(get(c,"history").isEmpty()?"[]":get(c,"history"));
   JSONArray next=new JSONArray();next.put(System.currentTimeMillis()+" | "+url);
   for(int i=0;i<old.length()&&i<99;i++)next.put(old.getString(i));
   put(c,"history",next.toString());
  }catch(Exception ignored){}
 }
}