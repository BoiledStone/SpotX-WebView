package com.boiled.spotx;

import org.json.JSONArray;
import org.json.JSONObject;

public final class BridgeInput {
    private BridgeInput(){}
    private static void artwork(JSONObject o)throws Exception{
        if(!Policy.artwork(o.optString("cover")))o.put("cover","");
    }
    private static void track(JSONObject o)throws Exception{
        if(!o.optString("trackId").matches("[A-Za-z0-9]{22}"))throw new SecurityException();
        for(String key:new String[]{"title","artist","album"})
            if(o.optString(key).length()>1024)throw new SecurityException();
        artwork(o);
    }
    public static String download(String raw,boolean collection)throws Exception{
        JSONObject o=new JSONObject(raw);
        if(collection){
            JSONArray tracks=o.optJSONArray("tracks");
            if(tracks==null||tracks.length()==0||tracks.length()>500)throw new SecurityException();
            for(int i=0;i<tracks.length();i++)track(tracks.getJSONObject(i));
            artwork(o);
            if(o.optString("name").length()>1024)throw new SecurityException();
        }else track(o);
        return o.toString();
    }
    public static String media(String raw)throws Exception{
        JSONObject o=new JSONObject(raw);artwork(o);
        for(String key:new String[]{"track","artist","album"})
            if(o.optString(key).length()>1024)throw new SecurityException();
        return o.toString();
    }
}
