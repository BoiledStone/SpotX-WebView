package com.boiled.spotx;

import java.net.URI;
import java.util.Locale;
import java.util.Arrays;

/** One strict parser shared by intents, WebView navigation and native HTTP. */
public final class Policy {
    private Policy() {}
    public static String host(String raw) {
        if (raw == null || raw.length() > 16384 || raw.isEmpty()) return null;
        for (int i=0; i<raw.length(); i++)
            if (raw.charAt(i) <= 32 || raw.charAt(i) == '\\' || raw.charAt(i) == 127) return null;
        try {
            URI uri = new URI(raw);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.isOpaque() ||
                uri.getRawUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)) return null;
            return uri.getHost() == null ? null : uri.getHost().toLowerCase(Locale.ROOT);
        } catch (Exception e) { return null; }
    }
    private static boolean domain(String h, String d) { return h != null && (h.equals(d) || h.endsWith("."+d)); }
    public static boolean link(String u) {
        String h=host(u);
        return h != null && Arrays.asList("open.spotify.com","spotify.com","www.spotify.com","play.spotify.com","spotify.link").contains(h);
    }
    public static boolean bridge(String u) {
        String h=host(u);
        return "open.spotify.com".equals(h) || "accounts.spotify.com".equals(h);
    }
    public static boolean player(String u) { return "open.spotify.com".equals(host(u)); }
    public static boolean spotify(String u) { return domain(host(u),"spotify.com"); }
    public static boolean google(String u) {
        String h=host(u);
        return domain(h,"google.com") || domain(h,"youtube.com");
    }
    public static boolean oauth(String u) {
        String h=host(u);
        return google(u) || "www.facebook.com".equals(h) || "m.facebook.com".equals(h)
            || "facebook.com".equals(h) || "appleid.apple.com".equals(h);
    }
    public static boolean navigation(String u) { return spotify(u) || "spotify.link".equals(host(u)) || oauth(u); }
    public static boolean nativeUrl(String u) { return spotify(u) || domain(host(u),"scdn.co"); }
    public static boolean artwork(String u) {
        String h=host(u);
        return domain(h,"scdn.co") || domain(h,"spotifycdn.com") || domain(h,"spotify.com")
            || domain(h,"ytimg.com") || domain(h,"ggpht.com") || domain(h,"googleusercontent.com");
    }
    public static boolean forwardCredentials(String from, String to) {
        String h=host(from);
        return h != null && h.equals(host(to));
    }
    public static boolean nativeHeader(String name, boolean credentials) {
        if(name==null)return false;
        String key=name.toLowerCase(Locale.ROOT);
        if(!Arrays.asList("accept","accept-language","authorization","client-token","content-type",
                "spotify-app-version","app-platform","x-client-id","x-spotify-connection-id").contains(key))return false;
        return credentials || (!key.equals("authorization") && !key.equals("client-token") && !key.equals("x-spotify-connection-id"));
    }
    public static String method(String m) {
        if (m==null) return "GET";
        String value=m.toUpperCase(Locale.ROOT);
        if (!Arrays.asList("GET","HEAD","POST","PUT","DELETE","PATCH","OPTIONS").contains(value))
            throw new IllegalArgumentException("Unsupported HTTP method");
        return value;
    }
}
