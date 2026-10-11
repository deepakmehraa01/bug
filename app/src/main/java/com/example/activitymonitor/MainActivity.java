package com.example.activitymonitor;
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.provider.Settings;
import android.widget.*;
import android.view.ViewGroup;
import androidx.work.*;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
public class MainActivity extends Activity {
 TextView output;EditText server,token;
 void show(String text){output.setText(text);}
 @Override public void onCreate(Bundle b){
  super.onCreate(b);
  if(Store.get(this,"deviceId").isEmpty())Store.put(this,"deviceId",UUID.randomUUID().toString());
  LinearLayout l=new LinearLayout(this);l.setPadding(24,24,24,24);l.setOrientation(LinearLayout.VERTICAL);
  ScrollView scroll=new ScrollView(this);scroll.addView(l);
  TextView title=new TextView(this);title.setText("Activity Monitor Lab — visible, consent-based reporting");title.setTextSize(19);l.addView(title);
  Button grant=button("Grant usage access",l);grant.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)));
  Button refresh=button("Refresh usage",l);
  output=new TextView(this);l.addView(output);
  refresh.setOnClickListener(v->{try{show("Today (foreground milliseconds):\n"+UsageReader.today(this).toString(2));}catch(Exception e){show("Enable Usage Access first: "+e.getMessage());}});
  Button browser=button("Open own browser",l);browser.setOnClickListener(v->startActivity(new Intent(this,BrowserActivity.class)));
  Button history=button("Show own browser URL history",l);history.setOnClickListener(v->show(Store.get(this,"history")));
  server=new EditText(this);server.setHint("HTTPS server URL");server.setSingleLine(true);server.setText(Store.get(this,"server"));l.addView(server);
  token=new EditText(this);token.setHint("Agent token");token.setSingleLine(true);token.setText(Store.get(this,"token"));l.addView(token);
  Button save=button("Save + enable periodic sync",l);
  save.setOnClickListener(v->{
   String s=server.getText().toString().trim(),t=token.getText().toString().trim();
   if(!s.startsWith("https://")||t.isEmpty()||!UsageReader.allowed(this)){show("HTTPS URL, agent token and Usage Access required");return;}
   Store.put(this,"server",s);Store.put(this,"token",t);
   PeriodicWorkRequest req=new PeriodicWorkRequest.Builder(SyncWorker.class,15,TimeUnit.MINUTES).build();
   WorkManager.getInstance(this).enqueueUniquePeriodicWork("usage_sync",ExistingPeriodicWorkPolicy.UPDATE,req);
   show("Periodic sync enabled. Device ID: "+Store.get(this,"deviceId"));
  });
  Button now=button("Sync now",l);now.setOnClickListener(v->WorkManager.getInstance(this).enqueue(new OneTimeWorkRequest.Builder(SyncWorker.class).build()));
  Button stop=button("Stop sync",l);stop.setOnClickListener(v->{WorkManager.getInstance(this).cancelUniqueWork("usage_sync");Store.put(this,"token","");token.setText("");show("Periodic sync stopped; token cleared.");});
  Button clear=button("Clear browser history",l);clear.setOnClickListener(v->{Store.put(this,"history","[]");show("Browser history cleared locally.");});
  show("Device ID: "+Store.get(this,"deviceId")+"\nGrant Usage Access to begin.");
  setContentView(scroll);
 }
 Button button(String text,LinearLayout l){Button b=new Button(this);b.setText(text);l.addView(b,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));return b;}
}