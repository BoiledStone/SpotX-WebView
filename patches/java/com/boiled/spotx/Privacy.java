package com.boiled.spotx;

public final class Privacy {
    private Privacy() {}
    public static String scrub(String value) {
        if(value==null)return "";
        String s=value.replaceAll("(?i)(https://[^\\s?#]+)[?#][^\\s]*","$1?[redacted]");
        s=s.replaceAll("(?i)Bearer\\s+[A-Za-z0-9._~+/=-]+","Bearer [redacted]");
        s=s.replaceAll("(?i)(sp_dc|sp_key|access_token|refresh_token|client_token|id_token|authorization|cookie|set-cookie)\\s*[:=]\\s*[^\\s;,]+","$1=[redacted]");
        s=s.replaceAll("eyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+","[redacted JWT]");
        return s.length()>4096?s.substring(0,4096)+"…":s;
    }
    public static Throwable scrubError(Throwable error) {
        if(error==null)return null;
        Throwable sanitized=new RuntimeException(error.getClass().getSimpleName()+": "+scrub(error.getMessage()));
        sanitized.setStackTrace(error.getStackTrace());
        return sanitized;
    }
}
