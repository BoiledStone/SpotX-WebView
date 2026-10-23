import com.boiled.spotx.Policy;
import com.boiled.spotx.Privacy;

public final class PolicyTest {
    private static int checks=0;
    private static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    public static void main(String[] args) {
        String[] bad={null,"","http://open.spotify.com/","javascript://open.spotify.com/%0aalert(1)",
            "file://open.spotify.com/","content://open.spotify.com/","data:text/html,hello",
            "https://open.spotify.com.evil.test/","https://evilspotify.com/","https://evil.test@open.spotify.com/",
            "https://open.spotify.com@evil.test/","https://open.spotify.com:444/","https://open.spotify.com:0/",
            "https://open.spotify.com\\@evil.test/","https://open.spotify.com/\n","https://open.spotify.com/\t",
            "https://open.spotify.com /","https://open.spotify.com./","https://open.spotify.com%2e.evil.test/",
            "https://127.0.0.1/","https://[::1]/","https://192.168.1.1/","https://localhost/",
            "https://accounts.google.com.evil.test/","https://evil.google.test/","https:open.spotify.com",
            "//open.spotify.com/","https:///open.spotify.com/","https://open.spotify.com:443.evil.test/"};
        for(String value:bad){
            check(!Policy.link(value),"intent: "+value);
            check(!Policy.bridge(value),"bridge: "+value);
            check(!Policy.nativeUrl(value),"native: "+value);
            check(!Policy.navigation(value),"navigation: "+value);
        }
        for(String value:new String[]{"https://open.spotify.com","https://open.spotify.com/track/abc?si=foo","HTTPS://OPEN.SPOTIFY.COM:443/"}){
            check(Policy.link(value),"valid intent");check(Policy.bridge(value),"valid bridge");check(Policy.player(value),"valid player");
        }
        check(Policy.link("https://spotify.link/x"),"short link");
        check(!Policy.bridge("https://spclient.wg.spotify.com/"),"API cannot send bridge messages");
        check(Policy.nativeUrl("https://spclient.wg.spotify.com/pathfinder/v1/query"),"Spotify API");
        check(Policy.navigation("https://accounts.google.com/o/oauth2/auth"),"Google login");
        check(Policy.navigation("https://appleid.apple.com/auth/authorize"),"Apple login");
        check(!Policy.navigation("https://evil.apple.com/"),"unrelated Apple site");
        check(Policy.artwork("https://i.scdn.co/image/abc"),"valid artwork");
        check(!Policy.artwork("https://i.scdn.co.evil.test/image/abc"),"spoof artwork");
        check(Policy.forwardCredentials("https://api.spotify.com/a","https://api.spotify.com/b"),"same host auth");
        check(!Policy.forwardCredentials("https://api.spotify.com/a","https://accounts.spotify.com/b"),"cross-host auth");
        check(!Policy.forwardCredentials("https://api.spotify.com/a","https://127.0.0.1/b"),"local redirect auth");
        for(String name:new String[]{"authorization","client-token","X-Spotify-Connection-Id"}){
            check(Policy.nativeHeader(name,true),"required Spotify credential header");
            check(!Policy.nativeHeader(name,false),"credential removed on host change");
        }
        for(String name:new String[]{"Cookie","Host","X-Forwarded-For","Connection","Authorization\r\nHost",null})
            check(!Policy.nativeHeader(name,true),"unsafe native header rejected");
        check(Policy.nativeHeader("Content-Type",false),"content type survives safe redirect");
        check(Policy.method("post").equals("POST"),"normalize HTTP method");
        boolean rejected=false;try{Policy.method("CONNECT");}catch(IllegalArgumentException e){rejected=true;}
        check(rejected,"CONNECT forbidden");
        String logs=Privacy.scrub("Bearer secretABC.123 sp_dc=secretCookie; refresh_token=secretRefresh https://open.spotify.com/path?code=secretCode#secretHash");
        for(String secret:new String[]{"secretABC","secretCookie","secretRefresh","secretCode","secretHash"})check(!logs.contains(secret),"redact "+secret);
        check(Privacy.scrub("plain diagnostic").equals("plain diagnostic"),"keep useful log");
        check(Privacy.scrub(new String(new char[5000]).replace('\0','x')).length()<4200,"bound log");
        System.out.println("PASS "+checks+" policy and privacy assertions");
    }
}
