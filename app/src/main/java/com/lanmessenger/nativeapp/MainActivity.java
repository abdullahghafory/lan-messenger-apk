package com.lanmessenger.nativeapp;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

import org.json.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private final int REQ_AUDIO=10, REQ_FILE=11, REQ_NOTIF=12;
    private ApiClient api;
    private LocalServer localServer;
    private LinearLayout root, content;
    private TextView title, status;
    private EditText host, port, username, password, displayName, messageInput;
    private long currentChat=0, lastMessage=0;
    private boolean embedded=false;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private MediaRecorder recorder;
    private File recordingFile;
    private MediaPlayer player;
    private TextView globalPlayer;

    @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(Color.rgb(7,94,84));api=new ApiClient(this);localServer=new LocalServer(this);requestNotification();showStart();}
    @Override protected void onDestroy(){io.shutdownNow();if(localServer!=null)localServer.stop();stopRecord();if(player!=null){player.release();player=null;}super.onDestroy();}
    private void requestNotification(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},REQ_NOTIF);}

    private int teal = Color.rgb(18,140,126);
    private int blue = Color.rgb(22,135,248);
    private int bg = Color.rgb(246,248,250);
    private int text = Color.rgb(27,39,48);
    private int muted = Color.rgb(103,116,126);

    private GradientDrawable rounded(int color, float radius){
        GradientDrawable d=new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    private void base(String t){
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg); root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        LinearLayout bar=new LinearLayout(this); bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(12,8,12,8); bar.setBackgroundColor(Color.WHITE); bar.setElevation(5);
        boolean home="پیام‌رسان محلی".equals(t);
        Button back=btn("‹",teal,Color.WHITE); back.setTextSize(30);
        back.setVisibility(home?View.GONE:View.VISIBLE); bar.addView(back,new LinearLayout.LayoutParams(50,50));
        title=txt(t,text,19); title.setTypeface(null,android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL|Gravity.RIGHT);
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,52,1); tp.setMargins(10,0,0,0); bar.addView(title,tp);
        TextView dot=txt("●",teal,12); dot.setGravity(Gravity.CENTER); bar.addView(dot,new LinearLayout.LayoutParams(34,50));
        root.addView(bar,new LinearLayout.LayoutParams(-1,66)); back.setOnClickListener(v->showStart());
        content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(16,16,16,18);
        ScrollView sv=new ScrollView(this); sv.setFillViewport(true); sv.setBackgroundColor(bg); sv.addView(content);
        root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        globalPlayer=txt("♫  پخش صوت",text,13); globalPlayer.setPadding(16,0,16,0);
        globalPlayer.setGravity(Gravity.CENTER_VERTICAL|Gravity.RIGHT); globalPlayer.setBackground(rounded(Color.WHITE,22)); globalPlayer.setElevation(7);
        globalPlayer.setVisibility(View.GONE); LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(-1,52); gp.setMargins(12,4,12,8); root.addView(globalPlayer,gp);
        setContentView(root);
    }

    private void showStart(){base("پیام‌رسان محلی");
        TextView hero=txt("💬",Color.WHITE,32); hero.setGravity(Gravity.CENTER); hero.setBackground(rounded(teal,26));
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(76,76); hp.gravity=Gravity.CENTER_HORIZONTAL; hp.setMargins(0,8,0,10); content.addView(hero,hp);
        TextView welcome=txt("پیام‌رسان شبکه محلی",text,22); welcome.setTypeface(null,android.graphics.Typeface.BOLD); welcome.setGravity(Gravity.CENTER); content.addView(welcome,lp(0,34));
        TextView sub=txt("سریع، ساده و بدون نیاز به اینترنت عمومی",muted,14); sub.setGravity(Gravity.CENTER); content.addView(sub,lp(0,34));
        Button newNet=btn("🖥  ساخت شبکه محلی جدید",Color.WHITE,teal); content.addView(newNet,lp(0,58));
        Button join=btn("📱  اتصال به شبکه موجود",Color.WHITE,blue); content.addView(join,lp(0,58));
        Button settings=btn("⚙  تنظیمات اتصال",text,Color.WHITE); content.addView(settings,lp(0,52));
        TextView note=txt("در حالت ساخت شبکه، همین گوشی میزبان شبکه می‌شود. در حالت اتصال، به سرور موجود در شبکه وصل می‌شوی.",muted,13);
        note.setGravity(Gravity.CENTER); note.setPadding(8,12,8,12); note.setLineSpacing(2,1.08f); content.addView(note,lp(0,0));
        newNet.setOnClickListener(v->showServerMode()); join.setOnClickListener(v->showConnect()); settings.setOnClickListener(v->showConnect());
    }
    private void showServerMode(){base("ساخت شبکه محلی جدید");
        addText("این گوشی به سرور شبکه تبدیل می‌شود.\nپورت پیش‌فرض: 8080",16,Color.DKGRAY);
        port=edit("8080","پورت",false);content.addView(port,lp(0,58));
        Button start=btn("▶ شروع سرور داخلی",Color.WHITE,Color.rgb(18,140,126));content.addView(start,lp(0,56));
        status=txt("سرور هنوز اجرا نشده است.",14,Color.DKGRAY);status.setPadding(12,14,12,14);content.addView(status,lp(0,0));
        start.setOnClickListener(v->{int p;try{p=Integer.parseInt(port.getText().toString());}catch(Exception e){p=8080;}if(p<1||p>65535)p=8080;int finalP=p;io.execute(()->{boolean ok=localServer.start(finalP);runOnUiThread(()->{if(ok){embedded=true;api.setBaseUrl("http://127.0.0.1:"+finalP);status.setText("سرور فعال است: http://"+lanIp()+":"+finalP+"\nهمین گوشی نیز می‌تواند وارد برنامه شود.");showAuth(true);}else status.setText("پورت در دسترس نیست. یک پورت دیگر امتحان کنید.");});});});
    }
    private String lanIp(){try{java.util.Enumeration<java.net.NetworkInterface> es=java.net.NetworkInterface.getNetworkInterfaces();while(es.hasMoreElements()){java.net.NetworkInterface n=es.nextElement();java.util.Enumeration<java.net.InetAddress> as=n.getInetAddresses();while(as.hasMoreElements()){java.net.InetAddress a=as.nextElement();if(!a.isLoopbackAddress()&&a instanceof java.net.Inet4Address)return a.getHostAddress();}}}catch(Exception ignored){}return "IP-گوشی";}

    private void showConnect(){base("اتصال به شبکه موجود");
        addText("اگر سرور روی شبکه پیدا نشد، آدرس IP و پورت را دستی وارد کن.",15,Color.DKGRAY);
        host=edit(api.getBaseUrl().replace("http://","").replaceAll(":\\d+$",""),"آدرس سرور",false);content.addView(host,lp(0,58));
        port=edit(api.getBaseUrl().matches(".*:\\d+$")?api.getBaseUrl().replaceAll(".*:",""):"8080","پورت",true);content.addView(port,lp(0,58));
        Button test=btn("🔎 بررسی و اتصال",Color.WHITE,Color.rgb(22,135,248));content.addView(test,lp(0,56));
        status=txt("",14,Color.DKGRAY);status.setPadding(12,12,12,12);content.addView(status,lp(0,0));
        test.setOnClickListener(v->{String h=host.getText().toString().trim().replace("http://","").replace("https://","");String p=port.getText().toString().trim();if(h.contains("/"))h=h.substring(0,h.indexOf('/'));String base="http://"+h+":"+(p.isEmpty()?"8080":p);api.setBaseUrl(base);status.setText("در حال بررسی...");io.execute(()->{try{JSONObject j=api.get(embedded?"/health":"/api/db_status.php");runOnUiThread(()->{status.setText("سرور پاسخ داد. اکنون وارد حساب شو.");showAuth(false);});}catch(Exception e){runOnUiThread(()->status.setText("اتصال برقرار نشد: "+e.getMessage()+"\nآدرس و پورت را بررسی کن."));}});});
    }

    private void showAuth(boolean serverMode){base(serverMode?"حساب مدیر شبکه":"ورود به پیام‌رسان");
        username=edit("","نام کاربری",false);password=edit("","رمز عبور",true);content.addView(username,lp(0,58));content.addView(password,lp(0,58));
        if(serverMode){displayName=edit("مدیر شبکه","نام نمایشی",false);content.addView(displayName,lp(0,58));}
        Button login=btn("ورود",Color.WHITE,Color.rgb(18,140,126));content.addView(login,lp(0,54));
        Button reg=btn(serverMode?"ثبت مدیر اولیه":"ثبت حساب جدید",Color.DKGRAY,Color.WHITE);content.addView(reg,lp(0,54));
        status=txt("",14,Color.DKGRAY);content.addView(status,lp(0,0));
        login.setOnClickListener(v->doLogin());reg.setOnClickListener(v->showRegister(serverMode));
    }
    private void showRegister(boolean serverMode){base("ثبت حساب");username=edit("","نام کاربری",false);displayName=edit("","نام نمایشی",false);password=edit("","رمز عبور",true);content.addView(username,lp(0,58));content.addView(displayName,lp(0,58));content.addView(password,lp(0,58));Button b=btn("ثبت نام",Color.WHITE,Color.rgb(18,140,126));content.addView(b,lp(0,54));status=txt("نام کاربری حداقل ۳ حرف باشد.",13,Color.GRAY);content.addView(status,lp(0,0));b.setOnClickListener(v->{String u=username.getText().toString().trim(),n=displayName.getText().toString().trim(),p=password.getText().toString();io.execute(()->{try{JSONObject j=api.register(u,n,p);runOnUiThread(()->{if(j.optBoolean("ok")){status.setText("ثبت شد؛ در حال ورود...");doLoginValues(u,p);}else status.setText(j.optString("error","ثبت نام ناموفق"));});}catch(Exception e){runOnUiThread(()->status.setText("خطا: "+e.getMessage()));}});});}
    private void doLogin(){doLoginValues(username.getText().toString().trim(),password.getText().toString());}
    private void doLoginValues(String u,String p){io.execute(()->{try{JSONObject j=api.login(u,p);runOnUiThread(()->{if(j.optBoolean("ok"))showHome();else status.setText(j.optString("error","ورود ناموفق"));});}catch(Exception e){runOnUiThread(()->status.setText("خطا: "+e.getMessage()));}});}

    private void showHome(){base("گفتگوها");
        Button search=btn("🔎 جستجوی کاربران و پیام‌ها",Color.DKGRAY,Color.WHITE);content.addView(search,lp(0,50));
        LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);content.addView(list,lp(0,0));
        search.setOnClickListener(v->showSearch());
        io.execute(()->{try{JSONObject j=embedded?api.get("/api/chats"):api.chats();JSONArray a=j.optJSONArray("conversations");runOnUiThread(()->{if(a==null)return;for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x==null)continue;Button c=btn((x.optInt("is_public",0)==1?"🌐 ":"👤 ")+x.optString("display_name","گفتگو"),Color.DKGRAY,Color.WHITE);list.addView(c,lp(0,58));long id=x.optLong("id",0);c.setOnClickListener(v->openChat(id,x.optString("display_name","گفتگو")));}});}catch(Exception e){runOnUiThread(()->addText("دریافت گفتگوها ناموفق بود: "+e.getMessage(),14,Color.RED));}});
        Button mic=btn("🎙 ضبط ویس",Color.WHITE,Color.rgb(18,140,126));content.addView(mic,lp(0,54));mic.setOnClickListener(v->toggleRecord());
    }
    private void showSearch(){base("جستجو");EditText q=edit("","نام کاربری یا متن پیام",false);content.addView(q,lp(0,58));Button b=btn("جستجو",Color.WHITE,Color.rgb(22,135,248));content.addView(b,lp(0,54));LinearLayout out=new LinearLayout(this);out.setOrientation(LinearLayout.VERTICAL);content.addView(out,lp(0,0));b.setOnClickListener(v->{String s=q.getText().toString().trim();io.execute(()->{try{JSONObject j=api.users(s);runOnUiThread(()->{JSONArray a=j.optJSONArray("users");if(a!=null)for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);Button u=btn("@"+x.optString("username")+"  —  "+x.optString("display_name"),Color.DKGRAY,Color.WHITE);out.addView(u,lp(0,52));}});}catch(Exception e){runOnUiThread(()->out.addView(txt("خطا: "+e.getMessage(),13,Color.RED),lp(0,0)));}});});}

    private void openChat(long cid,String name){currentChat=cid;lastMessage=0;base(name);TextView head=txt("پیام‌ها",13,Color.GRAY);content.addView(head,lp(0,32));LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);content.addView(list,lp(0,0));
        LinearLayout sendRow=new LinearLayout(this); sendRow.setOrientation(LinearLayout.HORIZONTAL); sendRow.setGravity(Gravity.CENTER_VERTICAL);
        sendRow.setPadding(8,5,8,5); sendRow.setBackground(rounded(Color.WHITE,22)); sendRow.setElevation(6);
        messageInput=edit("","پیام...",false); sendRow.addView(messageInput,new LinearLayout.LayoutParams(0,52,1));
        Button send=btn("➤",Color.WHITE,teal); send.setTextSize(20); sendRow.addView(send,new LinearLayout.LayoutParams(52,52));
        Button file=btn("📎",text,Color.WHITE); sendRow.addView(file,new LinearLayout.LayoutParams(52,52));
        Button voice=btn("🎙",Color.WHITE,blue); sendRow.addView(voice,new LinearLayout.LayoutParams(52,52));
        LinearLayout.LayoutParams sr=new LinearLayout.LayoutParams(-1,62); sr.setMargins(10,4,10,8); root.addView(sendRow,sr);
        send.setOnClickListener(v->sendMessage(list));file.setOnClickListener(v->pickFile());voice.setOnClickListener(v->toggleRecord());loadMessages(list);
    }
    private void loadMessages(LinearLayout list){io.execute(()->{try{JSONObject j;if(embedded)j=api.get("/api/messages?chat_id="+currentChat);else j=api.messages(currentChat,lastMessage);JSONArray a=j.optJSONArray("messages");if(a!=null)for(int i=0;i<a.length();i++){JSONObject m=a.optJSONObject(i);if(m==null)continue;lastMessage=Math.max(lastMessage,m.optLong("id"));runOnUiThread(()->addMessageView(list,m));}}catch(Exception e){runOnUiThread(()->addText("خطا در دریافت پیام‌ها: "+e.getMessage(),13,Color.RED));}});}
    private void addMessageView(LinearLayout list,JSONObject m){
        boolean mine="من".equals(m.optString("display_name",""));
        String body=m.optString("body",""); if(body.isEmpty()) body="📎 فایل پیوست";
        TextView v=txt(body+"\n"+m.optString("display_name",""),14,mine?Color.WHITE:text);
        v.setPadding(16,11,16,11); v.setLineSpacing(1,1.05f); v.setBackground(rounded(mine?teal:Color.WHITE,20));
        v.setElevation(1); v.setGravity(mine?Gravity.RIGHT:Gravity.LEFT);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.setMargins(mine?50:8,4,mine?8:50,4); list.addView(v,p);
        String type=m.optString("file_type",""); if(type.startsWith("audio/")) v.setOnClickListener(x->playFile(m.optString("file_path","")));
    }
    private void sendMessage(LinearLayout list){String s=messageInput.getText().toString().trim();if(s.isEmpty())return;messageInput.setText("");io.execute(()->{try{JSONObject j;if(embedded)j=api.postJson("/api/send",new JSONObject().put("chat_id",currentChat).put("sender_id",1).put("body",s));else j=api.sendText(currentChat,s,0);JSONObject sentMessage=new JSONObject().put("body",s).put("display_name","من");runOnUiThread(()->{if(j.optBoolean("ok")){addMessageView(list,sentMessage);}else Toast.makeText(this,j.optString("error","ارسال ناموفق"),Toast.LENGTH_SHORT).show();});}catch(Exception e){runOnUiThread(()->Toast.makeText(this,"ارسال ناموفق: "+e.getMessage(),Toast.LENGTH_LONG).show());}});}
    private void pickFile(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,REQ_FILE);}
    @Override protected void onActivityResult(int req,int res,Intent data){super.onActivityResult(req,res,data);if(req==REQ_FILE&&res==RESULT_OK&&data!=null&&data.getData()!=null){Uri u=data.getData();io.execute(()->{try{File f=copyUri(u);String mime=getContentResolver().getType(u);JSONObject j=api.sendFile(currentChat,"",f,mime);runOnUiThread(()->Toast.makeText(this,j.optBoolean("ok")?"فایل ارسال شد":"ارسال فایل ناموفق",Toast.LENGTH_SHORT).show());}catch(Exception e){runOnUiThread(()->Toast.makeText(this,"خطا: "+e.getMessage(),Toast.LENGTH_LONG).show());}});}}
    private File copyUri(Uri u)throws Exception{String name="upload.bin";CursorName cn=new CursorName(u);if(cn.name!=null)name=cn.name;File f=new File(getCacheDir(),System.currentTimeMillis()+"_"+name);try(InputStream in=getContentResolver().openInputStream(u);OutputStream out=new FileOutputStream(f)){byte[]b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}return f;}
    private class CursorName{String name;CursorName(Uri u){try(android.database.Cursor c=getContentResolver().query(u,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())name=c.getString(0);}}}

    private void toggleRecord(){if(recorder!=null){stopRecord();return;}if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQ_AUDIO);return;}try{recordingFile=new File(getCacheDir(),"voice_"+System.currentTimeMillis()+".m4a");recorder=new MediaRecorder();recorder.setAudioSource(MediaRecorder.AudioSource.MIC);recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);recorder.setOutputFile(recordingFile.getAbsolutePath());recorder.prepare();recorder.start();Toast.makeText(this,"ضبط شروع شد؛ دوباره بزن برای توقف",Toast.LENGTH_SHORT).show();}catch(Exception e){stopRecord();Toast.makeText(this,"شروع ضبط صدا انجام نشد: "+e.getMessage(),Toast.LENGTH_LONG).show();}}
    private void stopRecord(){if(recorder!=null){try{recorder.stop();}catch(Exception ignored){}try{recorder.release();}catch(Exception ignored){}recorder=null;}}
    private void playFile(String path){if(path==null||path.isEmpty())return;try{if(player!=null){player.stop();player.release();}player=new MediaPlayer();if(path.startsWith("http")){player.setDataSource(path);}else{player.setDataSource(path);}player.setOnPreparedListener(mp->{mp.start();globalPlayer.setVisibility(View.VISIBLE);globalPlayer.setText("▶ در حال پخش صوت");});player.setOnCompletionListener(mp->{globalPlayer.setText("پخش پایان یافت");});player.prepareAsync();}catch(Exception e){Toast.makeText(this,"پخش صوت ناموفق",Toast.LENGTH_SHORT).show();}}

    private EditText edit(String value,String hint,boolean pass){
        EditText e=new EditText(this); e.setText(value); e.setHint(hint); e.setTextSize(15); e.setSingleLine(true);
        e.setTextColor(text); e.setHintTextColor(Color.rgb(150,160,168)); e.setPadding(16,0,16,0);
        e.setBackground(rounded(Color.WHITE,18)); e.setElevation(1); if(pass)e.setInputType(0x81); return e;
    }
    private Button btn(String s,int fg,int bgColor){
        Button b=new Button(this); b.setText(s); b.setTextColor(fg); b.setTextSize(15); b.setAllCaps(false);
        b.setGravity(Gravity.CENTER); b.setPadding(12,0,12,0); b.setMinHeight(0); b.setMinimumHeight(0);
        b.setBackground(rounded(bgColor,18)); b.setElevation(2); return b;
    }
    private TextView txt(String s,int color,int size){
        TextView t=new TextView(this); t.setText(s); t.setTextColor(color); t.setTextSize(size);
        t.setGravity(Gravity.CENTER_VERTICAL); t.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); return t;
    }
    private void addText(String s,int size,int color){
        TextView t=txt(s,color,size); t.setPadding(4,6,4,18); t.setLineSpacing(2,1.08f); content.addView(t,lp(0,0));
    }
    private LinearLayout.LayoutParams lp(int w,int h){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w==0?-1:w,h==0?-2:h); p.setMargins(0,5,0,5); return p;
    }

}
