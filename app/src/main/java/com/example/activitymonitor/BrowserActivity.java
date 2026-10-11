package com.example.activitymonitor;
import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.Button;
import android.widget.LinearLayout;
import android.view.ViewGroup;
import android.text.InputType;
public class BrowserActivity extends Activity {
 WebView web;
 @Override public void onCreate(Bundle b){
  super.onCreate(b);
  LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);
  EditText url=new EditText(this);url.setSingleLine(true);url.setHint("https://example.org");url.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
  Button go=new Button(this);go.setText("Go");
  web=new WebView(this);web.getSettings().setJavaScriptEnabled(false);
  web.setWebViewClient(new WebViewClient(){
   @Override public void onPageFinished(WebView v,String current){
    if(current!=null&&(current.startsWith("https://")||current.startsWith("http://"))){url.setText(current);Store.addUrl(BrowserActivity.this,current);}
   }
  });
  go.setOnClickListener(v->{String u=url.getText().toString().trim();if(!u.startsWith("https://")){url.setError("Use an HTTPS URL");return;}web.loadUrl(u);});
  layout.addView(url);layout.addView(go);layout.addView(web,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));setContentView(layout);
 }
 @Override public void onBackPressed(){if(web.canGoBack())web.goBack();else super.onBackPressed();}
 @Override protected void onDestroy(){web.destroy();super.onDestroy();}
}