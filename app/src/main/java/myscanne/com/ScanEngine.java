package myscanne.com;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.json.JSONArray;
import org.json.JSONObject;

public class ScanEngine {
    public static final int MAX_RESP=2*1024*1024;
    public static final String SNI_IP="142.250.185.78";

    public static class Result { public String h="",ip="";public int c,p=443;public String s="?",m="GET",e=null,x=null;public long ms;public boolean ok; }
    public static class CdnR { public boolean d;public String p="None",ip="",v="";public long ms; }
    public static class SecR { public boolean ok;public int c;public Map<String,String> p=new LinkedHashMap<String,String>();public List<String> m=new ArrayList<String>();public int score;public long ms;public String e; }
    public static class HvR { public boolean b1,b2,b3;public String a="";public long ms; }
    public static class DpiR { public boolean ok;public String m,r=""; }
    public static class DnsR { public List<String> a=new ArrayList<String>(),a4=new ArrayList<String>(),cn=new ArrayList<String>(),mx=new ArrayList<String>(),ns=new ArrayList<String>(),tx=new ArrayList<String>();public boolean ok;public String e; }
    public static class TechR { public List<String> t=new ArrayList<String>();public String sv="",p="",g="";public Map<String,String> h=new LinkedHashMap<String,String>();public long ms;public int c; }
    public static class TkR { public boolean v;public String cn="",sv="",fp="",dt=""; }
    public static class WbR { public List<String> u=new ArrayList<String>(),iu=new ArrayList<String>();public int t,in; }
    public static class EpR { public String url;public int c;public long ms,cl; }
    public static class SubR { public List<String> s=new ArrayList<String>();public int crt,cs,av,ht,tu; }
    public static class WsR { public boolean ok;public int c;public String st="",hdr="";public long ms; }
    public static class GeoR { public boolean ok;public String country="",city="",isp="",org="",asn="",lat="",lon="",e=""; }
    public static class CertR { public boolean ok;public String subject="",issuer="",serial="",sigAlg="",notBefore="",notAfter="",sans="",e="";public int daysLeft; }
    public static class PingR { public boolean ok;public long avgMs,maxMs,minMs;public int sent,recv;public String ip=""; }
    public static class RedR { public List<String> chain=new ArrayList<String>();public String finalUrl="";public int hops;public boolean loop; }
    public static class WhoR { public boolean ok;public String raw="",e=""; }

    public static class BugS {
        public int sc,confidence;public String lv,ip="";public int hc;public String sv="";public long ms;
        public boolean wf;public String wn="";public List<String> tg=new ArrayList<String>(),si=new ArrayList<String>(),th=new ArrayList<String>();
        public boolean tk,ws;public String tks="",wss="";public List<Integer> op=new ArrayList<Integer>();public int ep;
        public static String lvl(int s){return s>=61?"CRITICAL":s>=41?"HIGH":s>=21?"MEDIUM":"LOW";}
        public static int clr(int s){return s>=61?0xFFFF3B4E:s>=41?0xFFF59E0B:s>=21?0xFF3B82F6:0xFF64748B;}
        public String cfStr(){return confidence>=90?"CONFIRMED":confidence>=60?"LIKELY":confidence>=30?"POSSIBLE":"UNLIKELY";}
    }

    public static class HCB { public void st(String m){} public void pr(String h,int d,int t){} }

    public static final String[] COMMON_PATHS={
        "/.git/HEAD","/.env","/.env.local","/.env.production","/.env.development",
        "/wp-admin","/wp-login.php","/wp-config.php","/xmlrpc.php",
        "/admin","/administrator","/admin/login","/admin.php","/admin.asp",
        "/actuator/health","/actuator/env","/actuator/info","/actuator/metrics",
        "/api","/api/v1","/api/v2","/swagger-ui.html","/swagger.json",
        "/.DS_Store","/robots.txt","/sitemap.xml","/crossdomain.xml",
        "/phpinfo.php","/info.php","/test.php","/debug.php",
        "/.htaccess","/.htpasswd","/server-status","/server-info",
        "/config.json","/config.xml","/settings.json","/package.json",
        "/backup","/backups","/old","/temp","/tmp","/uploads",
        "/.svn/entries","/.hg","/.bzr","/CVS/Entries",
        "/login","/signin","/register","/signup","/user","/users",
        "/dashboard","/panel","/cp","/control","/manage"
    };

    public static BugS bugProbe(String host,int to){
        BugS b=new BugS();int sc=0;
        DnsR d=dns(host);
        if(!d.a.isEmpty())b.ip=d.a.get(0);else if(!d.a4.isEmpty())b.ip=d.a4.get(0);
        if(!d.mx.isEmpty()){sc+=5;b.si.add("MX records found (email attack surface)");b.tg.add("MX");}
        if(!d.cn.isEmpty()){sc+=5;b.si.add("CNAME chain: "+d.cn.get(0));b.tg.add("CNAME");}

        Result t=tls(host,to,"HEAD");b.hc=t.c;b.sv=t.s;b.ms=t.ms;
        if(!t.ok&&b.ip.length()==0){b.sc=0;b.lv="LOW";b.confidence=0;b.si.add("Host unreachable");return b;}

        String sl=t.s.toLowerCase(Locale.US);boolean wf=false;
        if(sl.contains("cloudflare")){wf=true;b.wn="Cloudflare";}
        else if(sl.contains("akamai")){wf=true;b.wn="Akamai";}
        else if(sl.contains("incapsula")){wf=true;b.wn="Incapsula";}
        else if(sl.contains("sucuri")){wf=true;b.wn="Sucuri";}
        else if(sl.contains("f5")){wf=true;b.wn="F5";}
        else if(sl.contains("forti")){wf=true;b.wn="Fortinet";}
        else if(sl.contains("aws")){wf=true;b.wn="AWS WAF";}
        else if(sl.contains("fastly")){wf=true;b.wn="Fastly";}
        CdnR cd=cdn(host,to);if(cd.d){wf=true;if(b.wn.length()==0)b.wn=cd.p;}
        b.wf=wf;if(b.wn.length()==0)b.wn="None";
        if(!wf){sc+=25;b.si.add("NO WAF/CDN detected (direct server)");b.tg.add("NO WAF");b.confidence+=25;}
        else{b.si.add("WAF present: "+b.wn);b.tg.add("WAF");}

        TechR tr=tech(host,to);b.th=tr.t;
        boolean risky=false;
        for(int i=0;i<tr.t.size();i++){
            String tn=tr.t.get(i);
            if(tn.contains("WordPress")||tn.contains("Drupal")||tn.contains("Joomla")||tn.contains("Jenkins")||tn.contains("PHP")||tn.contains("ASP")||tn.contains("Tomcat")||tn.contains("Struts"))risky=true;
        }
        if(risky){sc+=15;b.si.add("Risky tech stack: "+joinN(tr.t,5));b.tg.add("RISKY TECH");b.confidence+=15;}
        else if(!tr.t.isEmpty()){sc+=5;b.si.add("Technology: "+joinN(tr.t,3));}

        SecR sr=sec(host,to);
        if(sr.score<30){sc+=15;b.si.add("Weak security headers: "+sr.score+"% ("+sr.m.size()+" missing)");b.tg.add("NO HSTS");b.confidence+=10;}
        else if(sr.score<60){sc+=8;b.si.add("Partial headers: "+sr.score+"%");}

        boolean http=false;
        try{HttpURLConnection hc=(HttpURLConnection)new URL("http://"+host+"/").openConnection();
            hc.setConnectTimeout(2000);hc.setReadTimeout(2000);hc.setInstanceFollowRedirects(false);
            int cd2=hc.getResponseCode();hc.disconnect();if(cd2>0&&cd2<500)http=true;
        }catch(Exception x){}
        if(http){sc+=8;b.si.add("HTTP open (no HTTPS redirect)");b.tg.add("HTTP");b.confidence+=5;}

        if(!d.cn.isEmpty()){TkR tk=takeover(host,to);b.tk=tk.v;b.tks=tk.sv;
            if(tk.v){sc+=30;b.si.add("SUBDOMAIN TAKEOVER: "+tk.dt);b.tg.add("TAKEOVER");b.confidence+=50;}
            else if(tk.sv.length()>0){sc+=5;b.si.add("CNAME -> "+tk.sv+" (verify manually)");b.tg.add("CNAME");}
        }

        WsR wr=ws(host,to);b.ws=wr.ok;b.wss=wr.st;
        if(wr.ok){sc+=15;b.si.add("WS upgrade ACCEPTED ("+wr.c+" "+wr.st+")");b.tg.add("WS");b.confidence+=10;}
        else if(wr.c>0){b.si.add("WS refused: HTTP "+wr.c+" "+wr.st);}

        int[]cp={443,8080,8443,21,22,25,3306,5432,6379,27017,3389,5900,9200,5601};
        int un=0;
        for(int i=0;i<cp.length;i++){if(port(host,cp[i],Math.min(to,1200))){b.op.add(cp[i]);if(cp[i]!=443&&cp[i]!=80)un++;}}
        if(un>=3){sc+=10;b.si.add(un+" unusual ports open: "+joinI(b.op));b.tg.add("PORTS");b.confidence+=10;}
        else if(un>=1&&un<3){sc+=5;b.si.add(un+" extra port(s): "+joinI(b.op));}

        int epc=0;
        for(int i=0;i<COMMON_PATHS.length;i++)if(epQuick(host,COMMON_PATHS[i],to))epc++;
        b.ep=epc;
        if(epc>=5){sc+=20;b.si.add(epc+" sensitive endpoints exposed");b.tg.add("EXPOSED");b.confidence+=25;}
        else if(epc>=1){sc+=10;b.si.add(epc+" interesting endpoint(s)");b.tg.add("ENDPOINT");b.confidence+=10;}

        CertR cert=sslCert(host,to);
        if(cert.ok&&cert.daysLeft<30){sc+=10;b.si.add("SSL cert expires in "+cert.daysLeft+" days");b.tg.add("EXPIRING CERT");}

        if(sc>100)sc=100;
        if(b.tk)b.confidence=Math.min(100,b.confidence+25);
        if(!wf&&risky)b.confidence=Math.min(100,b.confidence+15);
        b.sc=sc;b.lv=BugS.lvl(sc);
        if(b.confidence>100)b.confidence=100;
        return b;
    }

    public static class BhR { public String h,ip;public int c;public String sv;public long ms;public boolean wf,tk;public String wn,ts;public List<String> th=new ArrayList<String>(); }
    public static BhR bhFast(String host,int to){
        BhR r=new BhR();r.h=host;
        try{r.ip=InetAddress.getByName(host).getHostAddress();}catch(Exception x){}
        Result t=tls(host,to,"HEAD");r.c=t.c;r.sv=t.s;r.ms=t.ms;if(!t.ok)return r;
        String sl=t.s.toLowerCase(Locale.US);
        if(sl.contains("cloudflare")){r.wf=true;r.wn="Cloudflare";}
        else if(sl.contains("akamai")){r.wf=true;r.wn="Akamai";}
        TechR tr=tech(host,to);r.th=tr.t;
        DnsR d=dns(host);
        if(!d.cn.isEmpty()){TkR tk=takeover(host,to);r.tk=tk.v;r.ts=tk.sv;}
        return r;
    }

    public static WsR ws(String host,int to){
        long t0=System.currentTimeMillis();WsR r=new WsR();
        SSLSocket s=null;
        try{
            String ip=InetAddress.getByName(host).getHostAddress();
            Socket p=new Socket();p.connect(new InetSocketAddress(ip,443),to);p.setSoTimeout(to);
            s=(SSLSocket)factory().createSocket(p,host,443,true);
            s.setSoTimeout(to);s.startHandshake();
            String req="GET / HTTP/1.1\r\nHost: "+host
                +"\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                +"Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
                +"Sec-WebSocket-Version: 13\r\nUser-Agent: Mozilla/5.0\r\n\r\n";
            s.getOutputStream().write(req.getBytes("UTF-8"));s.getOutputStream().flush();
            String resp=readResp(s,4096);r.ms=System.currentTimeMillis()-t0;
            if(resp.length()>0){
                String[]ls=resp.split("\r\n");String sl2=ls[0];
                if(sl2.startsWith("HTTP/")){
                    String[]ps=sl2.split(" ");if(ps.length>=2)try{r.c=Integer.parseInt(ps[1]);}catch(Exception x){}
                    if(r.c==101)r.ok=true;
                    for(int i=1;i<ls.length;i++){if(ls[i].toLowerCase(Locale.US).startsWith("upgrade:"))r.hdr=ls[i];}
                }
                r.st=sl2;
            }
        }catch(Exception e){r.e=e.getMessage();}
        finally{try{if(s!=null)s.close();}catch(Exception x){}}
        return r;
    }

    public static DpiR dpi(String host,int to){
        DpiR r=new DpiR();
        try{
            String ip=InetAddress.getByName(host).getHostAddress();
            Result tr=tls(host,to,"HEAD");
            if(!tr.ok){r.ok=true;r.r="Blocked (no TLS)";return r;}
            Socket sk=new Socket();sk.connect(new InetSocketAddress(ip,443),to);sk.setSoTimeout(to);
            SSLSocket ss=(SSLSocket)factory().createSocket(sk,host,443,true);
            ss.setSoTimeout(to);ss.startHandshake();
            String req="GET / HTTP/1.1\r\nHost: "+host+"\r\n\r\n";
            ss.getOutputStream().write(req.getBytes("UTF-8"));
            String resp=readResp(ss,2048);
            if(resp.length()==0){r.ok=true;r.r="DPI detected (empty response)";}
            else{r.ok=false;r.r="No DPI (response received)";}
            ss.close();
        }catch(Exception e){r.ok=true;r.r="DPI detected ("+e.getMessage()+")";}
        return r;
    }

    public static Result sni(String host,String frontIp,int to,String mtd){
        Result r=new Result();r.h=host;r.m=mtd;
        long t0=System.currentTimeMillis();
        SSLSocket s=null;
        try{
            String ip=(frontIp!=null&&frontIp.length()>0)?frontIp:SNI_IP;
            Socket p=new Socket();p.connect(new InetSocketAddress(ip,443),to);p.setSoTimeout(to);
            s=(SSLSocket)factory().createSocket(p,host,443,true);
            s.setSoTimeout(to);s.startHandshake();
            String req=mtd+" / HTTP/1.1\r\nHost: "+host+"\r\nConnection: close\r\nUser-Agent: Mozilla/5.0\r\n\r\n";
            s.getOutputStream().write(req.getBytes("UTF-8"));s.getOutputStream().flush();
            String resp=readResp(s,MAX_RESP);r.ms=System.currentTimeMillis()-t0;
            if(resp.length()>0){
                String[]ls=resp.split("\r\n");String sl=ls[0];
                if(sl.startsWith("HTTP/")){
                    String[]ps=sl.split(" ");if(ps.length>=2)try{r.c=Integer.parseInt(ps[1]);}catch(Exception x){}
                }
                for(int i=1;i<ls.length;i++){
                    String ln=ls[i].toLowerCase(Locale.US);
                    if(ln.startsWith("server:")){r.s=ls[i].substring(7).trim();}
                }
                r.ip=ip;r.ok=true;
            }
        }catch(Exception e){r.e=e.getMessage();}
        finally{try{if(s!=null)s.close();}catch(Exception x){}}
        return r;
    }

    public static Result tls(String host,int to,String mtd){
        Result r=new Result();r.h=host;r.m=mtd;
        long t0=System.currentTimeMillis();
        SSLSocket s=null;
        try{
            String ip=InetAddress.getByName(host).getHostAddress();
            Socket p=new Socket();p.connect(new InetSocketAddress(ip,443),to);p.setSoTimeout(to);
            s=(SSLSocket)factory().createSocket(p,host,443,true);
            s.setSoTimeout(to);s.startHandshake();
            String req=mtd+" / HTTP/1.1\r\nHost: "+host+"\r\nConnection: close\r\nUser-Agent: Mozilla/5.0\r\n\r\n";
            s.getOutputStream().write(req.getBytes("UTF-8"));s.getOutputStream().flush();
            String resp=readResp(s,MAX_RESP);r.ms=System.currentTimeMillis()-t0;
            if(resp.length()>0){
                String[]ls=resp.split("\r\n");String sl=ls[0];
                if(sl.startsWith("HTTP/")){
                    String[]ps=sl.split(" ");if(ps.length>=2)try{r.c=Integer.parseInt(ps[1]);}catch(Exception x){}
                }
                for(int i=1;i<ls.length;i++){
                    String ln=ls[i].toLowerCase(Locale.US);
                    if(ln.startsWith("server:")){r.s=ls[i].substring(7).trim();}
                }
                r.ip=ip;r.ok=true;
            }
        }catch(Exception e){r.e=e.getMessage();}
        finally{try{if(s!=null)s.close();}catch(Exception x){}}
        return r;
    }

    public static Result proxy(String host,String sni,int to,String mtd){
        Result r=new Result();r.h=host;r.m=mtd;
        long t0=System.currentTimeMillis();
        SSLSocket s=null;
        try{
            String ip=InetAddress.getByName(host).getHostAddress();
            Socket p=new Socket();p.connect(new InetSocketAddress(ip,443),to);p.setSoTimeout(to);
            String cn=(sni!=null&&sni.length()>0)?sni:host;
            s=(SSLSocket)factory().createSocket(p,cn,443,true);
            s.setSoTimeout(to);s.startHandshake();
            String req=mtd+" / HTTP/1.1\r\nHost: "+cn+"\r\nConnection: close\r\nUser-Agent: Mozilla/5.0\r\n\r\n";
            s.getOutputStream().write(req.getBytes("UTF-8"));s.getOutputStream().flush();
            String resp=readResp(s,MAX_RESP);r.ms=System.currentTimeMillis()-t0;
            if(resp.length()>0){
                String[]ls=resp.split("\r\n");String sl=ls[0];
                if(sl.startsWith("HTTP/")){
                    String[]ps=sl.split(" ");if(ps.length>=2)try{r.c=Integer.parseInt(ps[1]);}catch(Exception x){}
                }
                r.ip=ip;r.ok=true;
            }
        }catch(Exception e){r.e=e.getMessage();}
        finally{try{if(s!=null)s.close();}catch(Exception x){}}
        return r;
    }

    public static boolean port(String host,int p,int to){
        Socket s=null;
        try{s=new Socket();s.connect(new InetSocketAddress(host,p),to);return true;}
        catch(Exception e){return false;}
        finally{try{if(s!=null)s.close();}catch(Exception x){}}
    }

    public static String portService(int p){
        if(p==21)return "FTP";if(p==22)return "SSH";if(p==23)return "Telnet";if(p==25)return "SMTP";
        if(p==53)return "DNS";if(p==80)return "HTTP";if(p==110)return "POP3";if(p==143)return "IMAP";
        if(p==443)return "HTTPS";if(p==3306)return "MySQL";if(p==3389)return "RDP";if(p==5432)return "PostgreSQL";
        if(p==6379)return "Redis";if(p==8080)return "HTTP-Alt";if(p==8443)return "HTTPS-Alt";if(p==9200)return "Elasticsearch";
        if(p==27017)return "MongoDB";if(p==5900)return "VNC";if(p==5601)return "Kibana";if(p==8123)return "ClickHouse";
        return "Unknown";
    }

    public static CdnR cdn(String host,int to){
        CdnR r=new CdnR();long t0=System.currentTimeMillis();
        try{
            HttpURLConnection c=(HttpURLConnection)new URL("https://"+host+"/").openConnection();
            c.setConnectTimeout(to);c.setReadTimeout(to);c.setInstanceFollowRedirects(false);c.setRequestMethod("HEAD");
            c.connect();int cd=c.getResponseCode();r.ms=System.currentTimeMillis()-t0;
            Map<String,List<String>> hdrs=c.getHeaderFields();c.disconnect();
            String[]cdnKeys={"cf-ray","x-cdn","x-fastly-request-id","x-akamai-transformed","x-sucuri-id","x-cf-powered-by","x-edge-location","x-amz-cf-id","x-cache","x-hw"};
            String[]cdnNames={"Cloudflare","Generic CDN","Fastly","Akamai","Sucuri","Cloudflare","Edge","AWS CloudFront","Generic","StackPath"};
            for(Map.Entry<String,List<String>> e:hdrs.entrySet()){
                String k=e.getKey();if(k==null)continue;
                String kl=k.toLowerCase(Locale.US);
                for(int i=0;i<cdnKeys.length;i++){if(kl.equals(cdnKeys[i])){r.d=true;r.p=cdnNames[i];r.v=e.getValue().get(0);break;}}
                if(r.d)break;
            }
            if(!r.d){
                String srv=c.getHeaderField("Server");if(srv!=null&&srv.toLowerCase(Locale.US).contains("cloudfront")){r.d=true;r.p="AWS CloudFront";}
            }
        }catch(Exception e){r.ms=System.currentTimeMillis()-t0;}
        return r;
    }

    public static SecR sec(String host,int to){
        SecR r=new SecR();long t0=System.currentTimeMillis();
        try{
            HttpURLConnection c=(HttpURLConnection)new URL("https://"+host+"/").openConnection();
            c.setConnectTimeout(to);c.setReadTimeout(to);c.setInstanceFollowRedirects(false);c.setRequestMethod("HEAD");
            c.connect();r.c=c.getResponseCode();r.ms=System.currentTimeMillis()-t0;
            String[]need={"strict-transport-security","content-security-policy","x-frame-options","x-content-type-options","referrer-policy","permissions-policy"};
            String[]nice={"x-xss-protection","expect-ct","feature-policy"};
            for(int i=0;i<need.length;i++){String v=c.getHeaderField(need[i]);if(v!=null&&v.length()>0)r.p.put(need[i],v);else r.m.add(need[i]);}
            for(int i=0;i<nice.length;i++){String v=c.getHeaderField(nice[i]);if(v!=null&&v.length()>0)r.p.put(nice[i],v);}
            r.score=(r.p.size()*100)/(r.p.size()+r.m.size());r.ok=true;
            c.disconnect();
        }catch(Exception e){r.e=e.getMessage();}
        return r;
    }

    public static HvR httpVer(String host,int to){
        HvR r=new HvR();long t0=System.currentTimeMillis();
        try{
            HttpURLConnection c=(HttpURLConnection)new URL("https://"+host+"/").openConnection();
            c.setConnectTimeout(to);c.setReadTimeout(to);c.setRequestProperty("Accept","*/*");
            c.connect();r.b1=true;
            String protocol=c.getHeaderField(null);if(protocol!=null&&protocol.contains("HTTP/2"))r.b2=true;
            c.disconnect();
            r.ms=System.currentTimeMillis()-t0;
        }catch(Exception e){}
        r.b3=false;return r;
    }

    public static DnsR dns(String host){
        DnsR r=new DnsR();
        try{r.a.add(InetAddress.getByName(host).getHostAddress());r.ok=true;}catch(Exception e){r.e=e.getMessage();}
        return r;
    }

    public static TechR tech(String host,int to){
        TechR r=new TechR();long t0=System.currentTimeMillis();
        try{
            HttpURLConnection c=(HttpURLConnection)new URL("https://"+host+"/").openConnection();
            c.setConnectTimeout(to);c.setReadTimeout(to);c.setInstanceFollowRedirects(false);c.setRequestMethod("HEAD");
            c.connect();r.c=c.getResponseCode();r.ms=System.currentTimeMillis()-t0;
            String[]keys={"server","x-powered-by","x-generator","x-aspnet-version","x-drupal-cache","x-redirect-by","x-pingback"};
            for(int i=0;i<keys.length;i++){String v=c.getHeaderField(keys[i]);if(v!=null){r.h.put(keys[i],v);if(keys[i].equals("server"))r.sv=v;if(keys[i].equals("x-powered-by"))r.p=v;}}
            String[]techs={"cloudflare","akamai","nginx","apache","iis","litespeed","caddy","haproxy","varnish"};
            String sl=r.sv.toLowerCase(Locale.US)+" "+r.p.toLowerCase(Locale.US);
            for(int i=0;i<techs.length;i++){if(sl.contains(techs[i])&&!r.t.contains(techs[i]))r.t.add(capitalize(techs[i]));}
            c.disconnect();
        }catch(Exception e){}
        return r;
    }

    public static TkR takeover(String host,int to){
        TkR r=new TkR();
        try{
            DnsR d=dns(host);r.cn=join(d.cn);
            if(d.cn.isEmpty())return r;
            String cn=d.cn.get(0).toLowerCase(Locale.US);
            String[]svs={"github.io","herokuapp.com","aws.amazon.com","azurewebsites.net","firebaseapp.com","surge.sh","netlify.app","vercel.app","bitbucket.io","fastly.net"};
            String[]names={"GitHub Pages","Heroku","AWS S3","Azure","Firebase","Surge","Netlify","Vercel","Bitbucket","Fastly"};
            for(int i=0;i<svs.length;i++){if(cn.contains(svs[i])){r.v=true;r.sv=names[i];r.dt=names[i]+" takeover risk on "+host;break;}}
        }catch(Exception e){}
        return r;
    }

    public static WbR wayback(String host,int max){
        WbR r=new WbR();
        try{
            HttpURLConnection c=(HttpURLConnection)new URL("http://web.archive.org/cdx/search/cdx?url="+host+"/*&output=json&collapse=urlkey&limit="+max).openConnection();
            c.setConnectTimeout(8000);c.setReadTimeout(8000);c.setRequestProperty("User-Agent","Mozilla/5.0");
            BufferedReader br=new BufferedReader(new InputStreamReader(c.getInputStream(),"UTF-8"));
            String line;boolean first=true;
            while((line=br.readLine())!=null){if(first){first=false;continue;}r.t++;
                try{JSONArray a=new JSONArray(line);if(a.length()>2){String u=a.getString(2);if(!r.u.contains(u)){r.u.add(u);if(isJuicy(u)){r.iu.add(u);r.in++;}}}}
                catch(Exception x){}
            }
            br.close();c.disconnect();
        }catch(Exception e){}
        return r;
    }

    public static List<EpR> fuzzEndpoints(String host,int to){
        List<EpR> res=new ArrayList<EpR>();
        for(int i=0;i<COMMON_PATHS.length;i++){
            EpR er=new EpR();er.url="https://"+host+COMMON_PATHS[i];
            long t0=System.currentTimeMillis();
            try{
                HttpURLConnection c=(HttpURLConnection)new URL(er.url).openConnection();
                c.setConnectTimeout(to);c.setReadTimeout(to);c.setInstanceFollowRedirects(false);c.setRequestMethod("HEAD");
                c.connect();er.c=c.getResponseCode();er.cl=c.getContentLength();c.disconnect();er.ms=System.currentTimeMillis()-t0;
            }catch(Exception e){er.c=0;er.ms=System.currentTimeMillis()-t0;}
            res.add(er);
        }
        return res;
    }

    public static SubR deepEnum(String domain,HCB cb){
        SubR r=new SubR();Set<String> all=new LinkedHashSet<String>();
        try{
            cb.st("Querying crt.sh...");
            HttpURLConnection c=(HttpURLConnection)new URL("https://crt.sh/?q=%."+domain+"&output=json").openConnection();
            c.setConnectTimeout(15000);c.setReadTimeout(15000);c.setRequestProperty("User-Agent","Mozilla/5.0");
            BufferedReader br=new BufferedReader(new InputStreamReader(c.getInputStream(),"UTF-8"));
            StringBuilder sb=new StringBuilder();String line;
            while((line=br.readLine())!=null)sb.append(line);
            br.close();c.disconnect();
            JSONArray arr=new JSONArray(sb.toString());
            for(int i=0;i<arr.length();i++){
                JSONObject o=arr.getJSONObject(i);String n=o.optString("name_value","");
                String[]parts=n.split("\n");
                for(int j=0;j<parts.length;j++){String sub=parts[j].trim().toLowerCase(Locale.US);if(sub.length()>0&&sub.endsWith(domain)&&!sub.startsWith("*")&&!all.contains(sub)){all.add(sub);r.crt++;}}
            }
        }catch(Exception e){}
        try{
            cb.st("Querying certspotter...");
            HttpURLConnection c=(HttpURLConnection)new URL("https://api.certspotter.com/v1/issuances?domain="+domain+"&include_subdomains=true&expand=dns_names").openConnection();
            c.setConnectTimeout(15000);c.setReadTimeout(15000);
            BufferedReader br=new BufferedReader(new InputStreamReader(c.getInputStream(),"UTF-8"));
            StringBuilder sb=new StringBuilder();String line;
            while((line=br.readLine())!=null)sb.append(line);
            br.close();c.disconnect();
            JSONArray arr=new JSONArray(sb.toString());
            for(int i=0;i<arr.length();i++){
                JSONObject o=arr.getJSONObject(i);JSONArray dns=o.optJSONArray("dns_names");
                if(dns!=null){for(int j=0;j<dns.length();j++){String sub=dns.getString(j).trim().toLowerCase(Locale.US);if(sub.length()>0&&sub.endsWith(domain)&&!sub.startsWith("*")&&!all.contains(sub)){all.add(sub);r.cs++;}}}
            }
        }catch(Exception e){}
        try{
            cb.st("Querying AlienVault...");
            HttpURLConnection c=(HttpURLConnection)new URL("https://otx.alienvault.com/api/v1/indicators/hostname/"+domain+"/passive_dns").openConnection();
            c.setConnectTimeout(15000);c.setReadTimeout(15000);
            BufferedReader br=new BufferedReader(new InputStreamReader(c.getInputStream(),"UTF-8"));
            StringBuilder sb=new StringBuilder();String line;
            while((line=br.readLine())!=null)sb.append(line);
            br.close();c.disconnect();
            JSONObject o=new JSONObject(sb.toString());JSONArray arr=o.optJSONArray("passive_dns");
            if(arr!=null){for(int i=0;i<arr.length();i++){String sub=arr.getJSONObject(i).optString("hostname","").trim().toLowerCase(Locale.US);if(sub.length()>0&&sub.endsWith(domain)&&!all.contains(sub)){all.add(sub);r.av++;}}}
        }catch(Exception e){}
        try{
            cb.st("Querying HackerTarget...");
            HttpURLConnection c=(HttpURLConnection)new URL("https://api.hackertarget.com/hostsearch/?q="+domain).openConnection();
            c.setConnectTimeout(15000);c.setReadTimeout(15000);
            BufferedReader br=new BufferedReader(new InputStreamReader(c.getInputStream(),"UTF-8"));
            String line;
            while((line=br.readLine())!=null){String[]p=line.split(",");if(p.length>=1){String sub=p[0].trim().toLowerCase(Locale.US);if(sub.length()>0&&sub.endsWith(domain)&&!all.contains(sub)){all.add(sub);r.ht++;}}}
            br.close();c.disconnect();
        }catch(Exception e){}
        r.s.addAll(all);r.tu=all.size();
        Collections.sort(r.s);
        return r;
    }

    public static List<String> hostsFind(String tld,int limit,boolean validate,int to,HCB cb){
        List<String> res=new ArrayList<String>();
        try{
            cb.st("Fetching crt.sh for TLD: "+tld);
            HttpURLConnection c=(HttpURLConnection)new URL("https://crt.sh/?q=%."+tld+"&output=json").openConnection();
            c.setConnectTimeout(20000);c.setReadTimeout(20000);
            BufferedReader br=new BufferedReader(new InputStreamReader(c.getInputStream(),"UTF-8"));
            StringBuilder sb=new StringBuilder();String line;
            while((line=br.readLine())!=null)sb.append(line);
            br.close();c.disconnect();
            JSONArray arr=new JSONArray(sb.toString());
            Set<String> seen=new LinkedHashSet<String>();
            for(int i=0;i<arr.length();i++){
                if(limit>0&&seen.size()>=limit)break;
                JSONObject o=arr.getJSONObject(i);String n=o.optString("name_value","");
                String[]parts=n.split("\n");
                for(int j=0;j<parts.length;j++){
                    String sub=parts[j].trim().toLowerCase(Locale.US);
                    if(sub.length()>0&&sub.endsWith(tld)&&!sub.startsWith("*")&&!seen.contains(sub)){seen.add(sub);}
                }
            }
            List<String> list=new ArrayList<String>(seen);Collections.sort(list);
            cb.st("Found "+list.size()+" hosts");
            if(validate){
                for(int i=0;i<list.size();i++){
                    String h=list.get(i);cb.pr(h,i+1,list.size());
                    Result tr=tls(h,to,"HEAD");
                    if(tr.ok)res.add(h+"  code="+tr.c+"  ms="+tr.ms);
                    else res.add(h+"  FAIL");
                }
            }else{res.addAll(list);}
        }catch(Exception e){}
        return res;
    }

    public static GeoR geo(String ip){
        GeoR r=new GeoR();
        try{
            HttpURLConnection c=(HttpURLConnection)new URL("http://ip-api.com/json/"+ip+"?fields=status,country,city,isp,org,as,lat,lon").openConnection();
            c.setConnectTimeout(8000);c.setReadTimeout(8000);
            BufferedReader br=new BufferedReader(new InputStreamReader(c.getInputStream(),"UTF-8"));
            StringBuilder sb=new StringBuilder();String line;
            while((line=br.readLine())!=null)sb.append(line);
            br.close();c.disconnect();
            JSONObject o=new JSONObject(sb.toString());
            if("success".equals(o.optString("status"))){
                r.ok=true;r.country=o.optString("country","");r.city=o.optString("city","");
                r.isp=o.optString("isp","");r.org=o.optString("org","");r.asn=o.optString("as","");
                r.lat=String.valueOf(o.optDouble("lat",0));r.lon=String.valueOf(o.optDouble("lon",0));
            }
        }catch(Exception e){r.e=e.getMessage();}
        return r;
    }

    public static CertR sslCert(String host,int to){
        CertR r=new CertR();
        SSLSocket s=null;
        try{
            String ip=InetAddress.getByName(host).getHostAddress();
            Socket p=new Socket();p.connect(new InetSocketAddress(ip,443),to);p.setSoTimeout(to);
            s=(SSLSocket)factory().createSocket(p,host,443,true);
            s.setSoTimeout(to);s.startHandshake();
            Certificate[] certs=s.getSession().getPeerCertificates();
            if(certs.length>0&&certs[0] instanceof X509Certificate){
                X509Certificate xc=(X509Certificate)certs[0];
                r.ok=true;r.subject=xc.getSubjectX500Principal().getName();
                r.issuer=xc.getIssuerX500Principal().getName();
                r.serial=xc.getSerialNumber().toString(16);
                r.sigAlg=xc.getSigAlgName();
                r.notBefore=xc.getNotBefore().toString();
                r.notAfter=xc.getNotAfter().toString();
                long diff=xc.getNotAfter().getTime()-System.currentTimeMillis();
                r.daysLeft=(int)(diff/(1000*60*60*24));
                try{
                    java.util.Collection<List<?>> sans=xc.getSubjectAlternativeNames();
                    if(sans!=null){StringBuilder sb=new StringBuilder();for(List<?> l:sans){if(sb.length()>0)sb.append(", ");sb.append(l.get(1));}r.sans=sb.toString();}
                }catch(Exception x){}
            }
        }catch(Exception e){r.e=e.getMessage();}
        finally{try{if(s!=null)s.close();}catch(Exception x){}}
        return r;
    }

    public static PingR ping(String host,int count,int to){
        PingR r=new PingR();r.sent=count;
        long min=Long.MAX_VALUE,max=0,sum=0;int recv=0;
        for(int i=0;i<count;i++){
            long t0=System.currentTimeMillis();
            try{
                Socket s=new Socket();s.connect(new InetSocketAddress(host,443),to);s.close();
                long ms=System.currentTimeMillis()-t0;
                if(ms<min)min=ms;if(ms>max)max=ms;sum+=ms;recv++;
            }catch(Exception e){}
            try{Thread.sleep(200);}catch(Exception x){}
        }
        r.recv=recv;r.ok=recv>0;
        if(recv>0){r.minMs=min;r.maxMs=max;r.avgMs=sum/recv;}
        try{r.ip=InetAddress.getByName(host).getHostAddress();}catch(Exception x){}
        return r;
    }

    public static RedR traceRedirect(String url,int maxHops,int to){
        RedR r=new RedR();r.hops=0;
        String current=url;
        if(!current.startsWith("http"))current="https://"+current;
        Set<String> seen=new LinkedHashSet<String>();
        while(r.hops<maxHops){
            if(seen.contains(current)){r.loop=true;break;}
            seen.add(current);
            try{
                HttpURLConnection c=(HttpURLConnection)new URL(current).openConnection();
                c.setConnectTimeout(to);c.setReadTimeout(to);c.setInstanceFollowRedirects(false);
                c.setRequestMethod("HEAD");c.connect();
                int code=c.getResponseCode();
                r.chain.add(code+" "+current);
                String loc=c.getHeaderField("Location");
                c.disconnect();
                if(loc!=null&&(code==301||code==302||code==307||code==308)){
                    current=loc;if(!current.startsWith("http"))current="https://"+current;
                    r.hops++;
                }else{break;}
            }catch(Exception e){r.chain.add("ERR "+current+" ("+e.getMessage()+")");break;}
        }
        r.finalUrl=current;
        return r;
    }

    public static WhoR whois(String domain){
        WhoR r=new WhoR();
        try{
            HttpURLConnection c=(HttpURLConnection)new URL("https://rdap.org/domain/"+domain).openConnection();
            c.setConnectTimeout(15000);c.setReadTimeout(15000);
            BufferedReader br=new BufferedReader(new InputStreamReader(c.getInputStream(),"UTF-8"));
            StringBuilder sb=new StringBuilder();String line;
            while((line=br.readLine())!=null)sb.append(line).append("\n");
            br.close();c.disconnect();
            r.ok=true;r.raw=sb.toString();
        }catch(Exception e){r.e=e.getMessage();}
        return r;
    }

    public static List<String> revIp(String ip){
        List<String> res=new ArrayList<String>();
        try{
            String ptr=InetAddress.getByName(ip).getCanonicalHostName();
            if(!ptr.equals(ip))res.add("PTR: "+ptr);
        }catch(Exception e){}
        try{
            HttpURLConnection c=(HttpURLConnection)new URL("https://api.hackertarget.com/reverseiplookup/?q="+ip).openConnection();
            c.setConnectTimeout(10000);c.setReadTimeout(10000);
            BufferedReader br=new BufferedReader(new InputStreamReader(c.getInputStream(),"UTF-8"));
            String line;
            while((line=br.readLine())!=null){String t=line.trim();if(t.length()>0&&!t.startsWith("Error"))res.add(t);}
            br.close();c.disconnect();
        }catch(Exception e){}
        return res;
    }

    private static SSLSocketFactory factory() throws Exception{
        SSLContext ctx=SSLContext.getInstance("TLS");
        ctx.init(null,new TrustManager[]{new X509TrustManager(){public void checkClientTrusted(X509Certificate[]c,String a){}public void checkServerTrusted(X509Certificate[]c,String a){}public X509Certificate[]getAcceptedIssuers(){return new X509Certificate[0];}}},new SecureRandom());
        return ctx.getSocketFactory();
    }

    private static String readResp(Socket s,int max) throws Exception{
        InputStream is=s.getInputStream();byte[]buf=new byte[Math.min(max,MAX_RESP)];int n=0,total=0;
        while(total<buf.length&&(n=is.read(buf,total,buf.length-total))>0)total+=n;
        return new String(buf,0,total,"UTF-8");
    }

    private static boolean epQuick(String host,String path,int to){
        try{
            HttpURLConnection c=(HttpURLConnection)new URL("https://"+host+path).openConnection();
            c.setConnectTimeout(to);c.setReadTimeout(to);c.setInstanceFollowRedirects(false);c.setRequestMethod("HEAD");
            c.connect();int cd=c.getResponseCode();c.disconnect();
            return cd==200||cd==301||cd==302||cd==401||cd==403||cd==407;
        }catch(Exception e){return false;}
    }

    private static boolean isJuicy(String u){
        String l=u.toLowerCase(Locale.US);
        return l.contains("admin")||l.contains("api")||l.contains("config")||l.contains("backup")||l.contains(".env")||l.contains(".git")||l.contains("wp-")||l.contains("phpinfo")||l.contains("swagger")||l.contains("actuator");
    }

    private static String capitalize(String s){if(s.length()==0)return s;return Character.toUpperCase(s.charAt(0))+s.substring(1);}
    private static String join(List<String> l){StringBuilder sb=new StringBuilder();for(int i=0;i<l.size();i++){if(i>0)sb.append(", ");sb.append(l.get(i));}return sb.toString();}
    private static String joinN(List<String> l,int n){StringBuilder sb=new StringBuilder();for(int i=0;i<Math.min(l.size(),n);i++){if(i>0)sb.append(", ");sb.append(l.get(i));}return sb.toString();}
    private static String joinI(List<Integer> l){StringBuilder sb=new StringBuilder();for(int i=0;i<l.size();i++){if(i>0)sb.append(", ");sb.append(l.get(i));}return sb.toString();}
}
