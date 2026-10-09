package com.lanmessenger.nativeapp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class ApiClient {
    private String baseUrl;
    private String cookie;
    private final android.content.SharedPreferences prefs;

    public ApiClient(android.content.Context ctx) {
        prefs = ctx.getSharedPreferences("server", android.content.Context.MODE_PRIVATE);
        baseUrl = prefs.getString("url", "");
        cookie = prefs.getString("cookie", "");
    }

    public void setBaseUrl(String url) {
        if (url == null) url = "";
        url = url.trim();
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        baseUrl = url;
        prefs.edit().putString("url", url).apply();
    }
    public String getBaseUrl() { return baseUrl; }
    public void clearSession() { cookie = ""; prefs.edit().remove("cookie").apply(); }

    private String url(String path) {
        if (path.startsWith("http://") || path.startsWith("https://")) return path;
        if (baseUrl.isEmpty()) throw new IllegalStateException("آدرس سرور تنظیم نشده است");
        return baseUrl + (path.startsWith("/") ? path : "/" + path);
    }

    private HttpURLConnection open(String method, String path) throws Exception {
        URL u = new URL(url(path));
        HttpURLConnection c = (HttpURLConnection) u.openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(7000);
        c.setReadTimeout(12000);
        c.setUseCaches(false);
        c.setDoInput(true);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("User-Agent", "LAN-Messenger-Native/1.0");
        if (!cookie.isEmpty()) c.setRequestProperty("Cookie", cookie);
        if (method.equals("POST") || method.equals("PUT")) c.setDoOutput(true);
        return c;
    }

    private JSONObject finish(HttpURLConnection c) throws Exception {
        int code = c.getResponseCode();
        String set = c.getHeaderField("Set-Cookie");
        if (set != null) {
            String first = set.split(";", 2)[0].trim();
            if (!first.isEmpty()) { cookie = first; prefs.edit().putString("cookie", cookie).apply(); }
        }
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        if (in == null) in = new ByteArrayInputStream(new byte[0]);
        String body = read(in);
        if (body.trim().isEmpty()) return new JSONObject().put("ok", code < 400);
        JSONObject out;
        try { out = new JSONObject(body); }
        catch (Exception e) { out = new JSONObject().put("ok", code < 400).put("raw", body); }
        if (code >= 400 && !out.optBoolean("ok", false)) out.put("http_code", code);
        return out;
    }

    private static String read(InputStream in) throws IOException {
        try (InputStream x = in; ByteArrayOutputStream b = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192]; int n;
            while ((n = x.read(buf)) != -1) b.write(buf, 0, n);
            return b.toString(StandardCharsets.UTF_8.name());
        }
    }

    public JSONObject get(String path) throws Exception {
        HttpURLConnection c = open("GET", path);
        try { return finish(c); } finally { c.disconnect(); }
    }

    public JSONObject postJson(String path, JSONObject body) throws Exception {
        HttpURLConnection c = open("POST", path);
        c.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        try (OutputStream out = c.getOutputStream()) { out.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
        try { return finish(c); } finally { c.disconnect(); }
    }

    public JSONObject postForm(String path, Map<String,String> form) throws Exception {
        HttpURLConnection c = open("POST", path);
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String,String> e : form.entrySet()) {
            if (b.length() > 0) b.append('&');
            b.append(URLEncoder.encode(e.getKey(), "UTF-8"));
            b.append('=').append(URLEncoder.encode(e.getValue() == null ? "" : e.getValue(), "UTF-8"));
        }
        try (OutputStream out = c.getOutputStream()) { out.write(b.toString().getBytes(StandardCharsets.UTF_8)); }
        try { return finish(c); } finally { c.disconnect(); }
    }

    public JSONObject sendMultipart(String path, Map<String,String> fields, File file, String field, String mime) throws Exception {
        String boundary = "----LANMessenger" + System.currentTimeMillis();
        HttpURLConnection c = open("POST", path);
        c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        try (OutputStream out = new BufferedOutputStream(c.getOutputStream())) {
            for (Map.Entry<String,String> e : fields.entrySet()) {
                write(out, "--"+boundary+"\r\nContent-Disposition: form-data; name=\""+e.getKey()+"\"\r\n\r\n"+e.getValue()+"\r\n");
            }
            if (file != null) {
                write(out, "--"+boundary+"\r\nContent-Disposition: form-data; name=\""+field+"\"; filename=\""+file.getName().replace("\"", "")+"\"\r\nContent-Type: "+(mime==null?"application/octet-stream":mime)+"\r\n\r\n");
                try (InputStream in = new FileInputStream(file)) { byte[] buf=new byte[8192]; int n; while((n=in.read(buf))!=-1) out.write(buf,0,n); }
                write(out, "\r\n");
            }
            write(out, "--"+boundary+"--\r\n");
        }
        try { return finish(c); } finally { c.disconnect(); }
    }

    private static void write(OutputStream out, String s) throws IOException { out.write(s.getBytes(StandardCharsets.UTF_8)); }

    public JSONObject login(String username, String password) throws Exception {
        return postJson("/api/login.php", new JSONObject().put("username",username).put("password",password));
    }
    public JSONObject register(String username, String displayName, String password) throws Exception {
        return postJson("/api/register.php", new JSONObject().put("username",username).put("display_name",displayName).put("password",password));
    }
    public JSONObject me() throws Exception { return get("/api/me.php"); }
    public JSONObject chats() throws Exception { return get("/api/chats.php"); }
    public JSONObject messages(long cid, long after) throws Exception { return get("/api/messages.php?conversation_id="+cid+"&after="+after); }
    public JSONObject sendText(long cid, String body, long replyTo) throws Exception {
        Map<String,String> f=new LinkedHashMap<>(); f.put("conversation_id",String.valueOf(cid)); f.put("body",body); if(replyTo>0)f.put("reply_to_id",String.valueOf(replyTo));
        return postForm("/api/send.php",f);
    }
    public JSONObject sendFile(long cid, String body, File file, String mime) throws Exception {
        Map<String,String> f=new LinkedHashMap<>(); f.put("conversation_id",String.valueOf(cid)); f.put("body",body);
        return sendMultipart("/api/send.php",f,file,"file",mime);
    }
    public JSONObject users(String q) throws Exception { return get("/api/users.php?q="+URLEncoder.encode(q==null?"":q,"UTF-8")); }
    public JSONObject search(String q, long cid) throws Exception { return get("/api/search_messages.php?q="+URLEncoder.encode(q==null?"":q,"UTF-8")+"&conversation_id="+cid); }
    public JSONObject notifications() throws Exception { return get("/api/notifications.php"); }
    public JSONObject publicInfo() throws Exception { return get("/api/public_info.php"); }
    public JSONObject publicMessages() throws Exception { return get("/api/public_messages.php"); }
    public JSONObject publicSend(String body) throws Exception { return postForm("/api/public_send.php",Collections.singletonMap("body",body)); }
}
