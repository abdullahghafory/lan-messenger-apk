package com.lanmessenger.nativeapp;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;

public final class LocalDb extends SQLiteOpenHelper {
    public LocalDb(Context c) { super(c, "lan_messenger_local.db", null, 1); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE users(id INTEGER PRIMARY KEY AUTOINCREMENT,username TEXT UNIQUE NOT NULL,display_name TEXT NOT NULL,password TEXT NOT NULL,role TEXT NOT NULL DEFAULT 'user',created_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE chats(id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT NOT NULL,username TEXT,peer_id INTEGER DEFAULT 0,is_public INTEGER DEFAULT 0,last_body TEXT,last_time INTEGER DEFAULT 0)");
        db.execSQL("CREATE TABLE messages(id INTEGER PRIMARY KEY AUTOINCREMENT,chat_id INTEGER NOT NULL,sender_id INTEGER NOT NULL,body TEXT,file_name TEXT,file_path TEXT,file_type TEXT,created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_messages_chat ON messages(chat_id, id)");
        db.execSQL("INSERT INTO chats(title,username,peer_id,is_public,last_body,last_time) VALUES('گفتگوی عمومی','','0',1,'',strftime('%s','now'))");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldV,int newV) {}

    public boolean hasUsers() { Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM users LIMIT 1",null); try{return c.moveToFirst();}finally{c.close();} }
    public boolean register(String u,String name,String pass,String role) {
        try { ContentValues v=new ContentValues();v.put("username",u);v.put("display_name",name);v.put("password",pass);v.put("role",role);v.put("created_at",System.currentTimeMillis()/1000);getWritableDatabase().insertOrThrow("users",null,v);return true; } catch(Exception e){return false;}
    }
    public boolean login(String u,String pass) { Cursor c=getReadableDatabase().rawQuery("SELECT id FROM users WHERE username=? AND password=?",new String[]{u,pass});try{return c.moveToFirst();}finally{c.close();} }
    public long userId(String u) { Cursor c=getReadableDatabase().rawQuery("SELECT id FROM users WHERE username=?",new String[]{u});try{return c.moveToFirst()?c.getLong(0):0;}finally{c.close();} }
    public long addMessage(long chat,long sender,String body,String name,String path,String type) { ContentValues v=new ContentValues();v.put("chat_id",chat);v.put("sender_id",sender);v.put("body",body);v.put("file_name",name);v.put("file_path",path);v.put("file_type",type);v.put("created_at",System.currentTimeMillis()/1000);return getWritableDatabase().insert("messages",null,v); }
    public Cursor messages(long chat,long after) { return getReadableDatabase().rawQuery("SELECT id,sender_id,body,file_name,file_path,file_type,created_at FROM messages WHERE chat_id=? AND id>? ORDER BY id ASC",new String[]{String.valueOf(chat),String.valueOf(after)}); }
}
