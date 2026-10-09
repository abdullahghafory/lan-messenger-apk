package com.lanmessenger.nativeapp;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public final class LocalServer {
    private final Context context;
    private final LocalDb db;
    private ServerSocket server;
    private ExecutorService pool;
    private volatile boolean running;
    private int port = 8080;

    public LocalServer(Context c) { context=c.getApplicationContext(); db=new LocalDb(context); }
    public synchronized boolean start(int requestedPort) {
        if (running) return true;
        try { port=requestedPort; server=new ServerSocket(port); pool=Executors.newCachedThreadPool(); running=true; pool.execute(this::loop); return true; }
        catch(Exception e){running=false;return false;}
    }
    public synchronized void stop() { running=false; try{if(server!=null)server.close();}catch(Exception ignored){} if(pool!=null)pool.shutdownNow(); }
    public boolean isRunning(){return running;}
    public int getPort(){return port;}

    private void loop(){ while(running){ try{ final Socket s=server.accept(); pool.execute(()->handle(s)); }catch(Exception e){if(running){} } } }
    private void handle(Socket s){ try(Socket socket=s; InputStream in=socket.getInputStream(); OutputStream out=socket.getOutputStream()){
        BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8));
        String first=r.readLine(); if(first==null)return; String[] p=first.split(" ",3); if(p.length<2){respond(out,400,"bad request");return;}
        String method=p[0], target=p[1]; int content=0; String line; while((line=r.readLine())!=null&&!line.isEmpty()){int i=line.indexOf(':');if(i>0&&line.substring(0,i).equalsIgnoreCase("Content-Length"))content=Integer.parseInt(line.substring(i+1).trim());}
        char[] chars=new char[content]; int read=0; while(read<content){int n=r.read(chars,read,content-read);if(n<0)break;read+=n;} String body=new String(chars,0,read);
        String path=target; int q=path.indexOf('?'); if(q>=0)path=path.substring(0,q);
        JSONObject result;
        if(path.equals("/health")) result=new JSONObject().put("ok",true).put("server","LAN Messenger").put("mode","embedded");
        else if(path.equals("/api/register")) result=register(body);
        else if(path.equals("/api/login")) result=login(body);
        else if(path.equals("/api/chats")) result=chats();
        else if(path.equals("/api/messages")) result=messages(parseQuery(target).get("chat_id"));
        else if(path.equals("/api/send")) result=send(body);
        else { respond(out,404,"not found"); return; }
        respond(out,200,result.toString());
    }catch(Exception ignored){} }

    private JSONObject register(String body)throws Exception{JSONObject j=new JSONObject(body);String u=j.optString("username").trim(),n=j.optString("display_name").trim(),pw=j.optString("password");if(u.length()<3||n.length()<2||pw.length()<4)return new JSONObject().put("ok",false).put("error","اطلاعات ثبت نام نامعتبر است");String role=db.hasUsers()?"user":"owner";if(!db.register(u,n,pw,role))return new JSONObject().put("ok",false).put("error","نام کاربری تکراری است");return new JSONObject().put("ok",true);}
    private JSONObject login(String body)throws Exception{JSONObject j=new JSONObject(body);boolean ok=db.login(j.optString("username"),j.optString("password"));return new JSONObject().put("ok",ok).put("error",ok?"":"نام کاربری یا رمز عبور اشتباه است");}
    private JSONObject chats()throws Exception{JSONArray a=new JSONArray();a.put(new JSONObject().put("id",0).put("is_public",1).put("display_name","گفتگوی عمومی").put("username",""));return new JSONObject().put("ok",true).put("conversations",a);}
    private JSONObject messages(String id)throws Exception{long chat=toLong(id);JSONArray a=new JSONArray();try(android.database.Cursor c=db.messages(chat,0)){while(c.moveToNext())a.put(new JSONObject().put("id",c.getLong(0)).put("sender_id",c.getLong(1)).put("body",c.getString(2)).put("file_name",c.getString(3)).put("file_path",c.getString(4)).put("file_type",c.getString(5)).put("created_at",c.getLong(6)));}return new JSONObject().put("ok",true).put("messages",a);}
    private JSONObject send(String body)throws Exception{JSONObject j=new JSONObject(body);long chat=j.optLong("chat_id",0);long sender=j.optLong("sender_id",1);long id=db.addMessage(chat,sender,j.optString("body"),null,null,null);return new JSONObject().put("ok",id>0).put("id",id);}
    private static long toLong(String x){try{return Long.parseLong(x==null?"0":x);}catch(Exception e){return 0;}}
    private static Map<String,String> parseQuery(String t){Map<String,String>m=new HashMap<>();int q=t.indexOf('?');if(q<0)return m;for(String x:t.substring(q+1).split("&")){String[]p=x.split("=",2);if(p.length==2)m.put(p[0],URLDecoder.decode(p[1],StandardCharsets.UTF_8));}return m;}
    private static void respond(OutputStream out,int code,String body)throws IOException{byte[]b=body.getBytes(StandardCharsets.UTF_8);String status=code==200?"OK":(code==400?"Bad Request":"Not Found");String h="HTTP/1.1 "+code+" "+status+"\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: "+b.length+"\r\nConnection: close\r\n\r\n";out.write(h.getBytes(StandardCharsets.UTF_8));out.write(b);out.flush();}
}
