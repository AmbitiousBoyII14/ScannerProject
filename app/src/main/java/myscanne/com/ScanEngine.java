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
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
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

    // ==== RESULT CLASSES ====
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

    // ==== BUG SCORE 0-100 + CONFIDENCE ====
    public static class BugS {
        public int sc,confidence;public String lv,ip="";public int hc;public String sv="";public long ms;
        public boolean wf;public String wn="";public List<String> tg=new ArrayList<String>(),si=new ArrayList<String>(),th=new ArrayList<String>();
        public boolean tk,ws;public String tks="",wss="";public List<Integer> op=new ArrayList<Integer>();public int ep;
        public static String lvl(int s){return s>=61?"CRITICAL":s>=41?"HIGH":s>=21?"MEDIUM":"LOW";}
        public static int clr(int s){return s>=61?0xFFFF3B4E:s>=41?0xFFF59E0B:s>=21?0xFF3B82F6:0xFF64748B;}
        public String cfStr(){return confidence>=90?"\u2714 CONFIRMED":confidence>=60?"\u26A0 LIKELY BUG":confidence>=30?"\u2753 POSSIBLE":"\u274C UNLIKELY";}
    }

    // ==== COMPACT BUG PROBE (FAST) ====
    public static BugS bugProbe(String host,int to){
        BugS b=new BugS();int sc=0;
        DnsR d=dns(host);
        if(!d.a.isEmpty())b.ip=d.a.get(0);else if(!d.a4.isEmpty())b.ip=d.a4.get(0);
        if(!d.mx.isEmpty()){sc+=5;b.si.add("MX records (email attack surface)");b.tg.add("MX");}
        if(!d.cn.isEmpty()){sc+=5;b.si.add("CNAME chain: "+d.cn.get(0));b.tg.add("CNAME");}

        Result t=tls(host,to,"HEAD");b.hc=t.c;b.sv=t.s;b.ms=t.ms;
        if(!t.ok&&b.ip.length()==0){b.sc=0;b.lv="LOW";b.confidence=0;b.si.add("Host unreachable");return b;}

        // WAF/CDN check
        String sl=t.s.toLowerCase(Locale.US);boolean wf=false;
        if(sl.contains("cloudflare")){wf=true;b.wn="Cloudflare";}
        else if(sl.contains("akamai")){wf=true;b.wn="Akamai";}
        else if(sl.contains("incapsula")){wf=true;b.wn="Incapsula";}
        else if(sl.contains("sucuri")){wf=true;b.wn="Sucuri";}
        else if(sl.contains("f5")){wf=true;b.wn="F5";}
        else if(sl.contains("forti")){wf=true;b.wn="Fortinet";}
        CdnR cd=cdn(host,to);if(cd.d){wf=true;if(b.wn.length()==0)b.wn=cd.p;}
        b.wf=wf;if(b.wn.length()==0)b.wn="None";
        if(!wf){sc+=25;b.si.add("NO WAF/CDN detected (direct server)");b.tg.add("NO WAF");b.confidence+=25;}
        else{b.si.add("WAF present: "+b.wn);b.tg.add("WAF");}

        // Tech fingerprint
        TechR tr=tech(host,to);b.th=tr.t;
        boolean risky=false;
        for(int i=0;i<tr.t.size();i++){
            String tn=tr.t.get(i);
            if(tn.contains("WordPress")||tn.contains("Drupal")||tn.contains("Joomla")||tn.contains("Jenkins")||tn.contains("PHP")||tn.contains("ASP"))risky=true;
        }
        if(risky){sc+=15;b.si.add("Risky tech stack: "+joinN(tr.t,5));b.tg.add("RISKY TECH");b.confidence+=15;}
        else if(!tr.t.isEmpty()){sc+=5;b.si.add("Technology: "+joinN(tr.t,3));}

        // Security headers
        SecR sr=sec(host,to);
        if(sr.score<30){sc+=15;b.si.add("Weak headers: "+sr.score+"% ("+sr.m.size()+" missing)");b.tg.add("NO HSTS");b.confidence+=10;}
        else if(sr.score<60){sc+=8;b.si.add("Partial headers: "+sr.score+"%");}

        // HTTP accessible
        boolean http=false;
        try{HttpURLConnection hc=(HttpURLConnection)new URL("http://"+host+"/").openConnection();
            hc.setConnectTimeout(2000);hc.setReadTimeout(2000);hc.setInstanceFollowRedirects(false);
            int cd2=hc.getResponseCode();hc.disconnect();if(cd2>0&&cd2<500)http=true;
        }catch(Exception x){}
        if(http){sc+=8;b.si.add("HTTP open (no HTTPS redirect)");b.tg.add("HTTP");b.confidence+=5;}

        // Takeover
        if(!d.cn.isEmpty()){TkR tk=takeover(host,to);b.tk=tk.v;b.tks=tk.sv;
            if(tk.v){sc+=30;b.si.add("SUB TAKEOVER: "+tk.dt);b.tg.add("TAKEOVER");b.confidence+=50;}
            else if(tk.sv.length()>0){sc+=5;b.si.add("CNAME -> "+tk.sv+" (verify manually)");b.tg.add("CNAME");}
        }

        // WebSocket
        WsR wr=ws(host,to);b.ws=wr.ok;b.wss=wr.st;
        if(wr.ok){sc+=15;b.si.add("WS upgrade ACCEPTED ("+wr.c+" "+wr.st+")");b.tg.add("WS");b.confidence+=10;}
        else if(wr.c>0){b.si.add("WS refused: HTTP "+wr.c+" "+wr.st);}

        // Open ports
        int[]cp={443,8080,8443,21,22,25,3306,5432,6379,27017};int un=0;
        for(int i=0;i<cp.length;i++){if(port(host,cp[i],Math.min(to,1200))){b.op.add(cp[i]);if(cp[i]!=443&&cp[i]!=80)un++;}}
        if(un>=3){sc+=10;b.si.add(un+" unusual ports open: "+joinI(b.op));b.tg.add("PORTS");b.confidence+=10;}
        else if(un>=1&&un<3){sc+=5;b.si.add(un+" extra port(s): "+joinI(b.op));}

        // Quick endpoint fuzz (reduced to 8 critical paths for speed)
        String[]qp={"/.git/HEAD","/.env","/wp-admin","/admin","/actuator/health","/.DS_Store","/robots.txt","/phpinfo.php"};
        int epc=0;
        for(int i=0;i<qp.length;i++)if(epQuick(host,qp[i],to))epc++;
        b.ep=epc;
        if(epc>=3){sc+=20;b.si.add(epc+" sensitive endpoints exposed");b.tg.add("EXPOSED");b.confidence+=25;}
        else if(epc>=1){sc+=10;b.si.add(epc+" interesting endpoint(s)");b.tg.add("ENDPOINT");b.confidence+=10;}

        // Cap & calculate confidence
        if(sc>100)sc=100;
        if(b.tk)b.confidence=Math.min(100,b.confidence+25);
        if(!wf&&risky)b.confidence=Math.min(100,b.confidence+15);
        b.sc=sc;b.lv=BugS.lvl(sc);
        if(b.confidence>100)b.confidence=100;
        return b;
    }

    // ==== BATCH FAST MODE ====
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

    // ==== WEBSOCKET UPGRADE (returns status) ====
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
                    if(ps.length>=3){r.st=ps[2];for(int i=3;i<ps.length;i++)r.st+=" "+ps[i];}
                }
                r.ok=resp.contains("101")&&resp.toLowerCase(Locale.US).contains("upgrade");
                // Build header summary
                StringBuilder hb=new StringBuilder();
                for(int i=1;i<Math.min(ls.length,8);i++){if(hb.length()>0)hb.append(" | ");hb.append(ls[i].trim());}
                r.hdr=hb.toString();
            }
        }catch(Exception e){r.st=shortM(e);r.ms=System.currentTimeMillis()-t0;}
        finally{closeQ(s);}
        return r;
    }

    // ==== DPI SCANNER ====
    public static DpiR dpi(String host,int to){
        DpiR r=new DpiR();String[]ms={host,"cloudflare.com","cdn.cloudflare.net"};boolean ac=false;
        for(int i=0;i<ms.length;i++){
            int st=dpiTry(host,ms[i],to);
            if(st==1){r.ok=true;r.m=ms[i];r.r="VULNERABLE (mask="+ms[i]+")";return r;}
            if(st==0)ac=true;
        }
        r.r=ac?"Not vulnerable (refused 101)":"No response";return r;
    }
    private static int dpiTry(String host,String sni,int to){
        SSLSocket s=null;
        try{
            String ip=InetAddress.getByName(host).getHostAddress();
            Socket p=new Socket();p.connect(new InetSocketAddress(ip,443),to);p.setSoTimeout(to);
            s=(SSLSocket)factory().createSocket(p,sni,443,true);s.setSoTimeout(to);s.startHandshake();
            String[]hv={"hOsT: "+host,"Host:  "+host,"host:"+host};
            String ch=hv[(int)(Math.random()*hv.length)];
            String p1="GET / HTTP/1.1\r\n"+ch+"\r\nUser-Agent: Mozilla/5.0\r\nUpgr";
            String p2="ade: websocket\r\nConnection: Upgrade\r\n"
                +"Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\n\r\n";
            OutputStream o=s.getOutputStream();
            o.write(p1.getBytes("UTF-8"));o.flush();
            try{Thread.sleep(80);}catch(InterruptedException x){}
            o.write(p2.getBytes("UTF-8"));o.flush();
            return readResp(s,4096).contains("101")?1:0;
        }catch(Exception e){return -1;}
        finally{closeQ(s);}
    }

    // ==== DNS ====
    public static DnsR dns(String d){
        DnsR r=new DnsR();
        try{InetAddress[]ia=InetAddress.getAllByName(d);
            for(int i=0;i<ia.length;i++){String ip=ia[i].getHostAddress();if(ip.contains(":"))r.a4.add(ip);else r.a.add(ip);}
            r.ok=true;
        }catch(Exception e){r.e=shortM(e);}
        String[]ts={"CNAME","MX","NS","TXT"};
        for(int t=0;t<ts.length;t++){
            String j=httpGet("https://dns.google/resolve?name="+d+"&type="+ts[t],4000,6000);
            if(j==null)continue;
            try{JSONObject rt=new JSONObject(j);JSONArray a2=rt.optJSONArray("Answer");
                if(a2==null)continue;
                for(int i=0;i<a2.length();i++){JSONObject o=a2.optJSONObject(i);if(o==null)continue;
                    String dt=o.optString("data","").trim().replaceAll("\\.$","");if(dt.length()==0)continue;
                    if("CNAME".equals(ts[t]))r.cn.add(dt);else if("MX".equals(ts[t]))r.mx.add(dt);
                    else if("NS".equals(ts[t]))r.ns.add(dt);else r.tx.add(o.optString("data",""));}
            }catch(Exception x){}
        }
        return r;
    }

    // ==== TECH ====
    private static final String[][]THS={{"server","cloudflare","Cloudflare"},{"server","nginx","Nginx"},{"server","apache","Apache"},{"server","iis","IIS"},{"server","litespeed","LiteSpeed"},{"server","gws","Google WS"},{"server","openresty","OpenResty"},{"server","gunicorn","Gunicorn"},{"server","werkzeug","Flask"},{"x-powered-by","php","PHP"},{"x-powered-by","asp.net","ASP.NET"},{"x-powered-by","express","Express"},};
    private static final String[][]TCK={{"PHPSESSID","PHP"},{"JSESSIONID","Java"},{"laravel_session","Laravel"},{"wordpress_logged_in","WordPress"},};
    private static final String[][]TBD={{"wp-content","WordPress"},{"bootstrap","Bootstrap"},{"jquery","jQuery"},{"react","React"},{"vue","Vue"},{"angular","Angular"},{"/_next/","Next.js"},{"cloudflare","Cloudflare"},};
    public static TechR tech(String d,int to){
        long t0=System.currentTimeMillis();TechR r=new TechR();SSLSocket s=null;
        try{String ip=InetAddress.getByName(d).getHostAddress();s=tlsSock(ip,d,to);
            String req="GET / HTTP/1.1\r\nHost: "+d+"\r\nUser-Agent: Mozilla/5.0\r\nAccept: text/html,*/*\r\nConnection: close\r\n\r\n";
            s.getOutputStream().write(req.getBytes("UTF-8"));s.getOutputStream().flush();
            String resp=readResp(s,65536);r.ms=System.currentTimeMillis()-t0;
            if(resp.length()==0)return r;
            int sp=resp.indexOf("\r\n\r\n");String hd=sp>=0?resp.substring(0,sp):resp,bd=sp>=0?resp.substring(sp+4):"";
            String[]ls=hd.split("\r\n");
            if(ls.length>0){String[]ps=ls[0].split(" ");if(ps.length>=2)try{r.c=Integer.parseInt(ps[1]);}catch(Exception x){}}
            Set<String>fd=new LinkedHashSet<String>();
            for(int i=1;i<ls.length;i++){
                int ci=ls[i].indexOf(':');if(ci<=0)continue;
                String k=ls[i].substring(0,ci).trim(),v=ls[i].substring(ci+1).trim();
                r.h.put(k,v);String lk=k.toLowerCase(Locale.US),lv=v.toLowerCase(Locale.US);
                if("server".equals(lk))r.sv=v;if("x-powered-by".equals(lk))r.p=v;if("x-generator".equals(lk))r.g=v;
                for(int x=0;x<THS.length;x++)if(lk.equals(THS[x][0]))if(THS[x][1].length()==0||lv.contains(THS[x][1]))fd.add(THS[x][2]);
            }
            String ck=r.h.get("Set-Cookie");if(ck==null)ck=r.h.get("set-cookie");
            if(ck!=null){String cl=ck.toLowerCase(Locale.US);for(int x=0;x<TCK.length;x++)if(cl.contains(TCK[x][0].toLowerCase(Locale.US)))fd.add(TCK[x][1]);}
            String bl=bd.toLowerCase(Locale.US);for(int x=0;x<TBD.length;x++)if(bl.contains(TBD[x][0].toLowerCase(Locale.US)))fd.add(TBD[x][1]);
            r.t.addAll(fd);
        }catch(Exception x){r.ms=System.currentTimeMillis()-t0;}finally{closeQ(s);}
        return r;
    }

    // ==== TAKEOVER ====
    private static final String[][]TKSV={{"s3.amazonaws.com","AWS S3"},{"cloudfront.net","AWS CloudFront"},{"elasticbeanstalk.com","AWS EB"},{"azurewebsites.net","Azure"},{"cloudapp.net","Azure"},{"azureedge.net","Azure CDN"},{"trafficmanager.net","Azure TM"},{"blob.core.windows.net","Azure Blob"},{"github.io","GitHub"},{"herokuapp.com","Heroku"},{"herokussl.com","Heroku"},{"surge.sh","Surge"},{"firebaseapp.com","Firebase"},{"web.app","Firebase"},{"fastly.net","Fastly"},{"shopify.com","Shopify"},{"myshopify.com","Shopify"},{"netlify.app","Netlify"},{"netlify.com","Netlify"},{"zendesk.com","Zendesk"},};
    private static final String[]UNCL={"NoSuchBucket","does not exist","NoSuchWebsite","no app","not found","no site","no CNAME","no record"};
    public static TkR takeover(String d,int to){
        TkR r=new TkR();DnsR dr=dns(d);if(dr.cn.isEmpty())return r;
        String cn=dr.cn.get(0);r.cn=cn;String cl=cn.toLowerCase(Locale.US);
        for(int i=0;i<TKSV.length;i++)if(cl.contains(TKSV[i][0])){r.sv=TKSV[i][1];break;}
        if(r.sv.length()==0)return r;
        SSLSocket s=null;
        try{String ip=InetAddress.getByName(d).getHostAddress();s=tlsSock(ip,d,to);
            String req="GET / HTTP/1.1\r\nHost: "+d+"\r\nUser-Agent: Mozilla/5.0\r\nConnection: close\r\n\r\n";
            s.getOutputStream().write(req.getBytes("UTF-8"));s.getOutputStream().flush();
            String resp=readResp(s,16384);String rl=resp.toLowerCase(Locale.US);
            for(int i=0;i<UNCL.length;i++)if(rl.contains(UNCL[i].toLowerCase(Locale.US))){r.v=true;r.fp=UNCL[i];r.dt="CNAME "+cn+" -> "+r.sv+" | "+UNCL[i];return r;}
            int sp=resp.indexOf("\r\n\r\n");
            if(sp>=0){String[]hl=resp.substring(0,sp).split("\r\n");
                if(hl.length>0){String[]ps=hl[0].split(" ");if(ps.length>=2){int c=0;try{c=Integer.parseInt(ps[1]);}catch(Exception x){}
                    if(c==404||c==403){r.v=true;r.fp="HTTP "+c;r.dt="CNAME "+cn+" -> "+r.sv+" | "+c;}}}}
        }catch(Exception x){r.v=true;r.fp="No resp";r.dt="CNAME "+cn+" -> "+r.sv+" | unreachable";}
        finally{closeQ(s);}
        return r;
    }

    // ==== QUICK ENDPOINT ====
    private static boolean epQuick(String d,String p,int to){
        SSLSocket s=null;
        try{String ip=InetAddress.getByName(d).getHostAddress();s=tlsSock(ip,d,to);
            String req="GET "+p+" HTTP/1.1\r\nHost: "+d+"\r\nUser-Agent: Mozilla/5.0\r\nConnection: close\r\n\r\n";
            s.getOutputStream().write(req.getBytes("UTF-8"));s.getOutputStream().flush();
            String resp=readResp(s,4096);
            if(resp.length()>0){String[]ps=resp.split("\r\n")[0].split(" ");if(ps.length>=2&&ps[0].startsWith("HTTP/")){int c=0;try{c=Integer.parseInt(ps[1]);}catch(Exception x){}return c>=200&&c<500;}}
        }catch(Exception x){}finally{closeQ(s);}
        return false;
    }

    // ==== ENDPOINT FUZZER ====
    public static final String[]COMMON_PATHS={"/.git/HEAD","/.env","/.env.backup","/.svn/entries","/.DS_Store","/robots.txt","/sitemap.xml","/admin","/wp-admin","/wp-login.php","/login","/dashboard","/panel","/console","/phpmyadmin","/phpinfo.php","/api/","/graphql","/swagger","/actuator/health","/actuator/env","/debug","/server-status","/jmx-console","/manager/html","/jenkins/login","/wp-json/","/backup","/.idea/workspace.xml","/sftp-config.json","/package.json","/composer.json","/Dockerfile","/config.yml","/config.json","/crossdomain.xml",};
    public static List<EpR> fuzzEndpoints(String d,int to){
        List<EpR> res=new ArrayList<EpR>();
        for(int i=0;i<COMMON_PATHS.length;i++){
            EpR er=new EpR();er.url="https://"+d+COMMON_PATHS[i];er.c=-1;long t0=System.currentTimeMillis();
            SSLSocket s=null;
            try{String ip=InetAddress.getByName(d).getHostAddress();s=tlsSock(ip,d,to);
                String req="GET "+COMMON_PATHS[i]+" HTTP/1.1\r\nHost: "+d+"\r\nUser-Agent: Mozilla/5.0\r\nConnection: close\r\n\r\n";
                s.getOutputStream().write(req.getBytes("UTF-8"));s.getOutputStream().flush();
                String r2=readResp(s,8192);er.ms=System.currentTimeMillis()-t0;
                if(r2.length()>0){String[]ls=r2.split("\r\n");if(ls.length>0){String[]ps=ls[0].split(" ");if(ps.length>=2)try{er.c=Integer.parseInt(ps[1]);}catch(Exception x){}}for(int j=1;j<ls.length;j++)if(ls[j].toLowerCase(Locale.US).startsWith("content-length:"))try{er.cl=Long.parseLong(ls[j].substring(ls[j].indexOf(':')+1).trim());}catch(Exception x){}}
            }catch(Exception x){er.ms=System.currentTimeMillis()-t0;}finally{closeQ(s);}
            res.add(er);
        }
        return res;
    }

    // ==== TLS / SNI / PROXY / PORT / CDN / HEADERS ====
    public static Result tls(String d,int to,String m){long t0=System.currentTimeMillis();Result r=new Result();r.h=d;r.m=m;try{r.ip=InetAddress.getByName(d).getHostAddress();}catch(Exception e){r.e="DNS";return r;}SSLSocket s=null;try{s=tlsSock(r.ip,d,to);String req=m+" / HTTP/1.1\r\nHost: "+d+"\r\nUser-Agent: Mozilla/5.0\r\nConnection: close\r\n\r\n";OutputStream o=s.getOutputStream();o.write(req.getBytes("UTF-8"));o.flush();String resp=readResp(s,8192);r.ms=System.currentTimeMillis()-t0;String[]ls=resp.split("\r\n");if(ls.length>0){String[]ps=ls[0].split(" ");if(ps.length>=2&&ps[0].startsWith("HTTP/"))try{r.c=Integer.parseInt(ps[1]);}catch(Exception x){}}for(int i=1;i<ls.length;i++)if(ls[i].toLowerCase(Locale.US).startsWith("server:")){String v=ls[i].substring(ls[i].indexOf(':')+1).trim();if(v.length()>24)v=v.substring(0,24);r.s=v;break;}r.ok=true;return r;}catch(Exception e){r.e=shortM(e);return r;}finally{closeQ(s);}}
    public static final String SNI_IP="142.250.80.46";
    public static Result sni(String d,String fi,int to,String m){long t0=System.currentTimeMillis();Result r=new Result();r.h=d;String ip=(fi==null||fi.trim().length()==0)?SNI_IP:fi.trim();r.ip=ip;r.x=ip;SSLSocket s=null;try{s=tlsSock(ip,d,to);r.ok=true;try{s.setSoTimeout(Math.min(800,to));String req=m+" / HTTP/1.1\r\nHost: "+d+"\r\nUser-Agent: curl/8.0\r\nConnection: close\r\n\r\n";s.getOutputStream().write(req.getBytes("UTF-8"));s.getOutputStream().flush();String resp=readResp(s,2048);if(resp.length()>0){String[]ps=resp.split("\r\n")[0].split(" ");if(ps.length>=2&&ps[0].startsWith("HTTP/"))try{r.c=Integer.parseInt(ps[1]);}catch(Exception x){}}}catch(Exception x){}r.ms=System.currentTimeMillis()-t0;return r;}catch(Exception e){r.ms=System.currentTimeMillis()-t0;r.e=shortM(e);return r;}finally{closeQ(s);}}
    public static Result proxy(String h,String sn,int to,String m){long t0=System.currentTimeMillis();Result r=new Result();String si=(sn==null||sn.trim().length()==0)?h:sn.trim();r.h=h;r.x=si;try{r.ip=InetAddress.getByName(h).getHostAddress();}catch(Exception e){r.e="DNS";return r;}SSLSocket s=null;try{s=tlsSock(r.ip,si,to);String req=m+" / HTTP/1.1\r\nHost: "+h+"\r\nUser-Agent: curl/8.0\r\nConnection: close\r\n\r\n";s.getOutputStream().write(req.getBytes("UTF-8"));s.getOutputStream().flush();String resp=readResp(s,4096);r.ms=System.currentTimeMillis()-t0;if(resp.length()>0&&resp.contains("HTTP")){r.ok=true;String[]ps=resp.split("\r\n")[0].split(" ");if(ps.length>=2)try{r.c=Integer.parseInt(ps[1]);}catch(Exception x){}}else r.e="No HTTP";return r;}catch(Exception e){r.e=shortM(e);return r;}finally{closeQ(s);}}
    public static boolean port(String h,int p,int to){Socket s=null;try{s=new Socket();s.connect(new InetSocketAddress(h,p),to);return true;}catch(Exception e){return false;}finally{closeQ(s);}}
    private static final String[][]CDNS={{"cloudflare","Cloudflare"},{"AkamaiGHost","Akamai"},{"Fastly","Fastly"},{"CloudFront","AWS CloudFront"},{"BunnyCDN","BunnyCDN"},{"Varnish","Varnish"}};
    private static final String[]CDNH={"cf-ray","cf-cache-status","x-cache","x-amz-cf-id","x-served-by","x-timer","x-varnish","via"};
    public static CdnR cdn(String d,int to){long t0=System.currentTimeMillis();CdnR r=new CdnR();try{r.ip=InetAddress.getByName(d).getHostAddress();}catch(Exception e){return r;}HttpURLConnection c=null;try{URL u=new URL("https://"+d+"/");c=(HttpURLConnection)u.openConnection();if(c instanceof HttpsURLConnection){HttpsURLConnection h=(HttpsURLConnection)c;h.setSSLSocketFactory(factory());h.setHostnameVerifier(new HostnameVerifier(){public boolean verify(String h2,SSLSession s){return true;}});}c.setConnectTimeout(to);c.setReadTimeout(to);c.setRequestMethod("HEAD");c.setRequestProperty("User-Agent","Mozilla/5.0");c.getResponseCode();r.ms=System.currentTimeMillis()-t0;String sh=c.getHeaderField("Server");if(sh!=null){String sl=sh.toLowerCase(Locale.US);for(int i=0;i<CDNS.length;i++)if(sl.contains(CDNS[i][0].toLowerCase(Locale.US))){r.d=true;r.p=CDNS[i][1];break;}}StringBuilder vi=new StringBuilder();for(int i=0;i<CDNH.length;i++){String v=c.getHeaderField(CDNH[i]);if(v==null)v=c.getHeaderField(CDNH[i].toLowerCase(Locale.US));if(v!=null&&v.trim().length()>0){if(vi.length()>0)vi.append(" | ");vi.append(CDNH[i]).append(": ").append(v.trim());if(!r.d){r.d=true;String lk=CDNH[i].toLowerCase(Locale.US);if(lk.startsWith("cf-"))r.p="Cloudflare";else if(lk.startsWith("x-amz-cf"))r.p="AWS CloudFront";else r.p="CDN";}if(vi.length()>200)break;}}r.v=vi.toString();}catch(Exception e){r.ms=System.currentTimeMillis()-t0;}finally{if(c!=null)c.disconnect();}return r;}
    private static final String[]SHD={"Strict-Transport-Security","Content-Security-Policy","X-Frame-Options","X-Content-Type-Options","Referrer-Policy","Permissions-Policy","X-XSS-Protection","Cross-Origin-Embedder-Policy","Cross-Origin-Opener-Policy"};
    public static SecR sec(String d,int to){long t0=System.currentTimeMillis();SecR r=new SecR();HttpURLConnection c=null;boolean ok=false;try{URL u=new URL("https://"+d+"/");c=(HttpURLConnection)u.openConnection();if(c instanceof HttpsURLConnection){HttpsURLConnection h=(HttpsURLConnection)c;h.setSSLSocketFactory(factory());h.setHostnameVerifier(new HostnameVerifier(){public boolean verify(String h2,SSLSession s){return true;}});}c.setConnectTimeout(to);c.setReadTimeout(to);c.setInstanceFollowRedirects(true);c.setRequestMethod("GET");c.setRequestProperty("User-Agent","Mozilla/5.0");r.c=c.getResponseCode();r.ms=System.currentTimeMillis()-t0;ok=true;}catch(Exception e){if(c!=null){try{c.disconnect();}catch(Exception x){}c=null;}}if(!ok){try{URL u2=new URL("http://"+d+"/");c=(HttpURLConnection)u2.openConnection();c.setConnectTimeout(to);c.setReadTimeout(to);c.setRequestMethod("GET");c.setRequestProperty("User-Agent","Mozilla/5.0");r.c=c.getResponseCode();ok=true;}catch(Exception e2){if(c!=null){try{c.disconnect();}catch(Exception x){}c=null;}}}if(!ok){SSLSocket s=null;try{String ip=InetAddress.getByName(d).getHostAddress();s=tlsSock(ip,d,to);String req="GET / HTTP/1.1\r\nHost: "+d+"\r\nUser-Agent: Mozilla/5.0\r\nConnection: close\r\n\r\n";s.getOutputStream().write(req.getBytes("UTF-8"));s.getOutputStream().flush();String resp=readResp(s,16384);r.ms=System.currentTimeMillis()-t0;if(resp.length()>0&&resp.contains("HTTP/")){ok=true;String[]ps=resp.split("\r\n")[0].split(" ");if(ps.length>=2)try{r.c=Integer.parseInt(ps[1]);}catch(Exception x){}String[]ls=resp.split("\r\n");for(int i=0;i<SHD.length;i++){String h=SHD[i],hl=h.toLowerCase(Locale.US)+":";boolean fd=false;for(int j=1;j<ls.length;j++)if(ls[j].toLowerCase(Locale.US).startsWith(hl)){String v=ls[j].substring(ls[j].indexOf(':')+1).trim();if(v.length()>60)v=v.substring(0,57)+"...";r.p.put(h,v);fd=true;break;}if(!fd)r.m.add(h);}int n=SHD.length;r.score=n>0?(r.p.size()*100)/n:0;r.ok=true;}}catch(Exception e3){}finally{closeQ(s);}return r;}if(c!=null){try{for(int i=0;i<SHD.length;i++){String h=SHD[i],v=c.getHeaderField(h);if(v==null)v=c.getHeaderField(h.toLowerCase(Locale.US));if(v!=null&&v.trim().length()>0){String vt=v.trim();if(vt.length()>60)vt=vt.substring(0,57)+"...";r.p.put(h,vt);}else r.m.add(h);}int n=SHD.length;r.score=n>0?(r.p.size()*100)/n:0;r.ok=true;}catch(Exception e){}finally{c.disconnect();}}return r;}
    public static HvR httpVer(String d,int to){long t0=System.currentTimeMillis();HvR r=new HvR();try{InetAddress.getByName(d).getHostAddress();}catch(Exception e){r.ms=System.currentTimeMillis()-t0;return r;}SSLSocket s=null;try{s=tlsSock(InetAddress.getByName(d).getHostAddress(),d,to);String req="HEAD / HTTP/1.1\r\nHost: "+d+"\r\nConnection: close\r\n\r\n";s.getOutputStream().write(req.getBytes("UTF-8"));s.getOutputStream().flush();String resp=readResp(s,2048);if(resp.startsWith("HTTP/1.1")||resp.startsWith("HTTP/1.0"))r.b1=true;}catch(Exception x){}finally{closeQ(s);}HttpURLConnection c=null;try{URL u=new URL("https://"+d+"/");c=(HttpURLConnection)u.openConnection();if(c instanceof HttpsURLConnection){HttpsURLConnection hc=(HttpsURLConnection)c;hc.setSSLSocketFactory(factory());hc.setHostnameVerifier(new HostnameVerifier(){public boolean verify(String h,SSLSession ss){return true;}});}c.setConnectTimeout(to);c.setReadTimeout(to);c.setRequestMethod("GET");c.setRequestProperty("User-Agent","Mozilla/5.0");c.getResponseCode();String as=c.getHeaderField("Alt-Svc");if(as==null)as=c.getHeaderField("alt-svc");if(as!=null){r.a=as.trim();String la=as.toLowerCase(Locale.US);if(la.contains("\"h2\"")||la.contains("h2="))r.b2=true;if(la.contains("\"h3\"")||la.contains("h3=")||la.contains("quic"))r.b3=true;}}catch(Exception x){}finally{if(c!=null)c.disconnect();}r.ms=System.currentTimeMillis()-t0;return r;}

    // ==== WAYBACK ====
    private static final String[]IPAT={".git/config",".env",".svn/entries","backup","bak","admin","login","wp-admin","dashboard","panel","api/","graphql","swagger","phpmyadmin","config","error","log","robots.txt","redirect","token"};
    public static WbR wayback(String d,int mx){WbR r=new WbR();String j=httpGet("https://web.archive.org/cdx/search/cdx?url=*."+d+"/*&output=json&fl=original&collapse=urlkey&limit="+mx,8000,25000);if(j==null||j.length()==0)return r;try{JSONArray a=new JSONArray(j);r.t=a.length();for(int i=0;i<a.length();i++){JSONArray rw=a.optJSONArray(i);if(rw==null||rw.length()==0)continue;String u=rw.optString(0,"");if(u.length()==0)continue;r.u.add(u);String ul=u.toLowerCase(Locale.US);for(int p=0;p<IPAT.length;p++)if(ul.contains(IPAT[p])){r.iu.add(u);r.in++;break;}}}catch(Exception x){}return r;}

    // ==== DEEP ENUM ====
    public static SubR deepEnum(String d,final HCB cb){
        SubR r=new SubR();Set<String> all=new LinkedHashSet<String>();
        if(cb!=null)cb.st("crt.sh ...");
        String j=httpGetCapped("https://crt.sh/?q=%25."+d+"&output=json",10000,60000,MAX_RESP);
        if(j!=null)try{JSONArray a=new JSONArray(j);r.crt=a.length();for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o==null)continue;exN(o.optString("name_value",""),d,all);exN(o.optString("common_name",""),d,all);}}catch(Exception x){}
        if(cb!=null)cb.st("certspotter ...");
        j=httpGetCapped("https://api.certspotter.com/v1/issuances?domain="+d+"&include_subdomains=true&expand=dns_names",8000,25000,MAX_RESP);
        if(j!=null)try{JSONArray a=new JSONArray(j);r.cs=a.length();for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o==null)continue;JSONArray na=o.optJSONArray("dns_names");if(na==null)continue;for(int k=0;k<na.length();k++){String n=na.optString(k,"").trim().toLowerCase(Locale.US);if(n.length()>0&&(n.equals(d)||n.endsWith("."+d))){if(n.startsWith("*."))n=n.substring(2);if(!n.contains("*")&&!n.contains(" "))all.add(n);}}}}catch(Exception x){}
        if(cb!=null)cb.st("AlienVault ...");
        j=httpGetCapped("https://otx.alienvault.com/api/v1/indicators/domain/"+d+"/passive_dns",8000,20000,MAX_RESP);
        if(j!=null)try{JSONObject rt=new JSONObject(j);JSONArray a=rt.optJSONArray("passive_dns");if(a!=null){r.av=a.length();for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o==null)continue;String hn=o.optString("hostname","").trim().toLowerCase(Locale.US);if(hn.length()>0&&(hn.equals(d)||hn.endsWith("."+d)))if(!hn.contains("*")&&!hn.contains(" "))all.add(hn);}}}catch(Exception x){}
        if(cb!=null)cb.st("HackerTarget ...");
        String ht=httpGet("https://api.hackertarget.com/hostsearch/?q="+d,8000,15000);
        if(ht!=null&&!ht.contains("error")&&!ht.contains("API count exceeded")){String[]ls=ht.split("\n");r.ht=ls.length;for(int i=0;i<ls.length;i++){String[]ps=ls[i].split(",");if(ps.length>0){String n=ps[0].trim().toLowerCase(Locale.US);if(n.length()>0&&(n.equals(d)||n.endsWith("."+d)))if(!n.contains("*")&&!n.contains(" "))all.add(n);}}}
        if(cb!=null)cb.st("Brute-force ...");
        for(int i=0;i<COMMON_SUBS.length;i++)all.add(COMMON_SUBS[i]+"."+d);
        r.s.addAll(all);r.tu=all.size();return r;
    }
    private static void exN(String raw,String d,Set<String>out){if(raw==null||raw.length()==0)return;for(String p:raw.split("\n")){String s=p.trim().toLowerCase(Locale.US);if(s.startsWith("*."))s=s.substring(2);if(s.length()==0||s.contains("*")||s.contains(" "))continue;if(s.equals(d)||s.endsWith("."+d))out.add(s);}}

    // ==== SUB FINDER ====
    public static final String[]COMMON_SUBS={"www","mail","webmail","smtp","ftp","cpanel","ns1","ns2","dns","admin","api","dev","test","staging","beta","portal","vpn","m","mobile","blog","shop","cdn","static","img","images","media","secure","login","app","apps","dashboard","remote","server","gateway","proxy","email","cloud","git","gitlab","jenkins","docs","support","help","status","monitor"};
    public static List<String> subFind(String d){Set<String> ct=new LinkedHashSet<String>();String j=httpGet("https://crt.sh/?q=%25."+d+"&output=json",8000,20000);if(j!=null)try{JSONArray a=new JSONArray(j);for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o==null)continue;String nv=o.optString("name_value",""),cn=o.optString("common_name","");if(nv.length()>0)for(String p:nv.split("\\n")){String s=p.trim().toLowerCase(Locale.US);if(s.startsWith("*."))s=s.substring(2);if(s.length()>0&&!s.contains("*")&&!s.contains(" ")&&(s.equals(d)||s.endsWith("."+d)))ct.add(s);}if(cn.length()>0)for(String p:cn.split("\\n")){String s=p.trim().toLowerCase(Locale.US);if(s.startsWith("*."))s=s.substring(2);if(s.length()>0&&!s.contains("*")&&!s.contains(" ")&&(s.equals(d)||s.endsWith("."+d)))ct.add(s);}}}catch(Exception x){}Set<String> ord=new LinkedHashSet<String>();ord.addAll(ct);for(int i=0;i<COMMON_SUBS.length;i++)ord.add(COMMON_SUBS[i]+"."+d);List<String> out=new ArrayList<String>();int n=0;for(String f:ord){if(n++>=1000)break;try{out.add(f+" -> "+InetAddress.getByName(f).getHostAddress());}catch(Exception e){if(ct.contains(f))out.add(f+" -> (unresolved)");}}return out;}

    // ==== HOSTS FINDER ====
    public static List<String> hostsFind(String tld,int limit,boolean vtls,int to,HCB cb){String qt=tld.startsWith(".")?tld.substring(1):tld;if(cb!=null)cb.st("crt.sh ...");String j=httpGetCapped("https://crt.sh/?q=%25."+qt+"&output=json",12000,90000,MAX_RESP);Set<String> rs=new LinkedHashSet<String>();int tc=0;if(j!=null)try{JSONArray a=new JSONArray(j);tc=a.length();for(int i=0;i<tc;i++){JSONObject o=a.optJSONObject(i);if(o==null)continue;String nv=o.optString("name_value",""),cn=o.optString("common_name","");String sf="."+qt.toLowerCase(Locale.US),tl=qt.toLowerCase(Locale.US);if(nv.length()>0)for(String p:nv.split("\n")){String s=p.trim().toLowerCase(Locale.US);if(s.startsWith("*."))s=s.substring(2);if(s.length()>0&&!s.contains("*")&&!s.contains(" ")&&(s.equals(tl)||s.endsWith(sf)))rs.add(s);}if(cn.length()>0)for(String p:cn.split("\n")){String s=p.trim().toLowerCase(Locale.US);if(s.startsWith("*."))s=s.substring(2);if(s.length()>0&&!s.contains("*")&&!s.contains(" ")&&(s.equals(tl)||s.endsWith(sf)))rs.add(s);}}}catch(Exception x){}if(cb!=null)cb.st(tc+" certs -> "+rs.size()+" hosts");List<String> ah=new ArrayList<String>(rs);rs.clear();Collections.shuffle(ah,new Random());int take=(limit<=0||limit>=ah.size())?ah.size():limit;List<String> pk=new ArrayList<String>(ah.subList(0,take));ah.clear();if(!vtls){if(cb!=null)cb.st("Extracted "+pk.size());return pk;}if(cb!=null)cb.st("Validating "+pk.size()+" via TLS...");List<String> vv=new ArrayList<String>();for(int i=0;i<pk.size();i++){String h=pk.get(i);if(cb!=null)cb.pr(h,i+1,pk.size());Result rt=tls(h,to,"HEAD");if(rt.ok)vv.add(h+"  code="+rt.c+"  ip="+rt.ip+"  "+rt.ms+"ms");}return vv;}
    public interface HCB{void st(String m);void pr(String h,int d,int t);}

    // ==== REVERSE IP ====
    public static List<String> revIp(String hi){List<String> out=new ArrayList<String>();Set<String> seen=new LinkedHashSet<String>();String ip;try{ip=InetAddress.getByName(hi).getHostAddress();}catch(Exception e){return out;}try{String ptr=InetAddress.getByName(ip).getCanonicalHostName();if(ptr!=null&&!ptr.equals(ip)&&seen.add(ptr.toLowerCase(Locale.US)))out.add(ptr.toLowerCase(Locale.US)+"  (PTR)");}catch(Exception x){}String d=httpGet("https://api.hackertarget.com/reverseiplookup/?q="+ip,6000,12000);if(d!=null){String lo=d.toLowerCase(Locale.US);if(!lo.contains("error")&&!lo.contains("no records")&&!lo.contains("api count exceeded")&&!lo.contains("<html"))for(String r2:d.split("\\n")){String dm=r2.trim().toLowerCase(Locale.US);if(dm.length()>0&&dm.indexOf('.')>0&&dm.indexOf(' ')<0&&seen.add(dm))out.add(dm);}}if(out.isEmpty())out.add(ip+" -> none");else out.add(0,"IP "+ip+" -- "+out.size()+" domains:");return out;}

    // ==== SSL / HELPERS ====
    private static SSLSocketFactory trustAllFactory;
    private static synchronized SSLSocketFactory factory(){if(trustAllFactory!=null)return trustAllFactory;try{SSLContext ctx=SSLContext.getInstance("TLS");ctx.init(null,new TrustManager[]{new X509TrustManager(){public void checkClientTrusted(X509Certificate[] c,String a){}public void checkServerTrusted(X509Certificate[] c,String a){}public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}}},new SecureRandom());trustAllFactory=ctx.getSocketFactory();}catch(Exception e){trustAllFactory=(SSLSocketFactory)SSLSocketFactory.getDefault();}return trustAllFactory;}
    private static void closeQ(Socket s){if(s!=null)try{s.close();}catch(Exception x){}}
    private static SSLSocket tlsSock(String ip,String sni,int to)throws Exception{Socket p=new Socket();p.connect(new InetSocketAddress(ip,443),to);p.setSoTimeout(to);SSLSocket ss=(SSLSocket)factory().createSocket(p,sni,443,true);ss.setSoTimeout(to);ss.startHandshake();return ss;}
    private static String readResp(SSLSocket s,int max)throws Exception{InputStream in=s.getInputStream();byte[]b=new byte[4096];StringBuilder sb=new StringBuilder();int t=0;while(t<max){int n;try{n=in.read(b);}catch(Exception e){break;}if(n<=0)break;sb.append(new String(b,0,n,"UTF-8"));t+=n;if(sb.indexOf("\r\n\r\n")>=0)break;}return sb.toString();}
    private static String httpGet(String u,int ct,int rt){return httpGetCapped(u,ct,rt,Integer.MAX_VALUE);}
    private static String httpGetCapped(String u,int ct,int rt,int max){HttpURLConnection c=null;BufferedReader br=null;try{URL ur=new URL(u);c=(HttpURLConnection)ur.openConnection();if(c instanceof HttpsURLConnection){HttpsURLConnection h=(HttpsURLConnection)c;h.setSSLSocketFactory(factory());h.setHostnameVerifier(new HostnameVerifier(){public boolean verify(String a,SSLSession b){return true;}});}c.setConnectTimeout(ct);c.setReadTimeout(rt);c.setInstanceFollowRedirects(true);c.setRequestProperty("User-Agent","Mozilla/5.0");c.setRequestProperty("Accept","application/json,*/*");int cd=c.getResponseCode();InputStream is=(cd>=200&&cd<400)?c.getInputStream():c.getErrorStream();if(is==null)return null;br=new BufferedReader(new InputStreamReader(is,"UTF-8"),8192);StringBuilder sb=new StringBuilder();char[]buf=new char[4096];int n;while((n=br.read(buf))!=-1){if(sb.length()+n>max){sb.append(buf,0,max-sb.length());break;}sb.append(buf,0,n);}return sb.toString();}catch(Exception e){return null;}finally{try{if(br!=null)br.close();}catch(Exception x){}if(c!=null)c.disconnect();}}
    private static String shortM(Exception e){String m=e.getMessage();if(m==null)m=e.getClass().getSimpleName();if(m.length()>20)m=m.substring(0,20);return m;}
    private static String joinN(List<String> l,int max){StringBuilder sb=new StringBuilder();int n=0;for(int i=0;i<l.size()&&n<max;i++){if(sb.length()>0)sb.append(", ");sb.append(l.get(i));n++;}if(l.size()>max)sb.append(" +").append(l.size()-max);return sb.toString();}
    private static String joinI(List<Integer> l){StringBuilder sb=new StringBuilder();for(int i=0;i<l.size();i++){if(i>0)sb.append(", ");sb.append(l.get(i));}return sb.toString();}
}
