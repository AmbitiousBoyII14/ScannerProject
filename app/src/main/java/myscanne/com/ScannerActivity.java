package myscanne.com;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class ScannerActivity extends Activity {
    private static final int REQ_PICK = 3001, SPLIT_CHUNK = 25000;
    private int C_OK = 0xFF22C55E, C_WARN = 0xFFF59E0B, C_INFO = 0xFF3D8BFF, C_MUTED = 0xFF8A93A6, C_DANGER = 0xFFFF3B4E;
    private String mode = "TLS", title = "Scanner";
    private EditText etTarget, etSni;
    private Button btnSingle, btnFile, btnStop, btnSave;
    private TextView tvTitle, tvSubtitle, tvStatus, tvProgress, tvEta;
    private ProgressBar progressHorizontal;
    private ScrollView svResults, svLive, svHits;
    private LinearLayout resultsContainer, liveContainer, hitsContainer;
    private Button tabResults, tabLive, tabHits;
    private int currentTab = 0;
    private volatile boolean cancelled, running;
    private long startTime;
    private int totalCount;
    private final List<String> sessionHits = new ArrayList<String>();
    private final List<String> sessionLines = new ArrayList<String>();

    @Override protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_scanner);
        if (getIntent() != null) {
            if (getIntent().getStringExtra("mode") != null) mode = getIntent().getStringExtra("mode");
            if (getIntent().getStringExtra("title") != null) title = getIntent().getStringExtra("title");
        }
        etTarget = (EditText) findViewById(R.id.etTarget);
        etSni = (EditText) findViewById(R.id.etSni);
        btnSingle = (Button) findViewById(R.id.btnSingle);
        btnFile = (Button) findViewById(R.id.btnFile);
        btnStop = (Button) findViewById(R.id.btnStop);
        btnSave = (Button) findViewById(R.id.btnSave);
        tvTitle = (TextView) findViewById(R.id.tvTitle);
        tvSubtitle = (TextView) findViewById(R.id.tvSubtitle);
        tvStatus = (TextView) findViewById(R.id.tvStatus);
        tvProgress = (TextView) findViewById(R.id.tvProgress);
        tvEta = (TextView) findViewById(R.id.tvEta);
        progressHorizontal = (ProgressBar) findViewById(R.id.progressHorizontal);
        svResults = (ScrollView) findViewById(R.id.svResults);
        svLive = (ScrollView) findViewById(R.id.svLive);
        svHits = (ScrollView) findViewById(R.id.svHits);
        tabResults = (Button) findViewById(R.id.tabResults);
        tabLive = (Button) findViewById(R.id.tabLive);
        tabHits = (Button) findViewById(R.id.tabHits);
        resultsContainer = new LinearLayout(this);
        resultsContainer.setOrientation(LinearLayout.VERTICAL);
        liveContainer = new LinearLayout(this);
        liveContainer.setOrientation(LinearLayout.VERTICAL);
        hitsContainer = new LinearLayout(this);
        hitsContainer.setOrientation(LinearLayout.VERTICAL);
        svResults.addView(resultsContainer);
        svLive.addView(liveContainer);
        svHits.addView(hitsContainer);
        tabResults.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { switchTab(0); }
        });
        tabLive.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { switchTab(1); }
        });
        tabHits.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { switchTab(2); }
        });
        tvTitle.setText(title);
        applyTheme();
        configureMode();
        findViewById(R.id.btnBack).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        btnSingle.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if ("HOSTS".equals(mode)) { startHosts(); return; }
                if ("DEEPENUM".equals(mode)) { startDeep(); return; }
                if ("TAKEOVER".equals(mode)) { startTk(); return; }
                if ("ENDPOINT".equals(mode)) { startEp(); return; }
                if ("SPLIT".equals(mode)) return;
                startSingle();
            }
        });
        btnFile.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pickFile(); }
        });
        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { cancelled = true; setStatus("Stopping...", C_WARN); }
        });
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (sessionHits.isEmpty() && sessionLines.isEmpty()) { toast("Nothing to save"); return; }
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < sessionHits.size(); i++) sb.append(sessionHits.get(i)).append("\n");
                for (int i = 0; i < sessionLines.size(); i++) sb.append(sessionLines.get(i)).append("\n");
                saveFile(mode.toLowerCase(Locale.US) + "_" + System.currentTimeMillis() + ".txt", sb.toString());
            }
        });
    }

    private void switchTab(int tab) {
        currentTab = tab;
        int ac = Prefs.accent(this), mu = C_MUTED;
        Button[] tabs = {tabResults, tabLive, tabHits};
        for (int i = 0; i < tabs.length; i++) {
            if (i == tab) {
                tabs[i].setBackgroundDrawable(Theme.filled(this, ac));
                tabs[i].setTextColor(Theme.onColor(ac));
            } else {
                tabs[i].setBackgroundDrawable(Theme.outline(this, Prefs.stroke(this)));
                tabs[i].setTextColor(mu);
            }
        }
        svResults.setVisibility(tab == 0 ? View.VISIBLE : View.GONE);
        svLive.setVisibility(tab == 1 ? View.VISIBLE : View.GONE);
        svHits.setVisibility(tab == 2 ? View.VISIBLE : View.GONE);
    }

    private void applyTheme() {
        C_MUTED = Prefs.muted(this);
        int t = Prefs.text(this);
        C_OK = 0xFF22C55E;
        C_WARN = 0xFFF59E0B;
        C_INFO = Prefs.accent(this);
        View root = findViewById(R.id.scannerRoot), hdr = findViewById(R.id.scannerHeader);
        if (root != null) root.setBackgroundColor(Prefs.bg(this));
        if (hdr != null) hdr.setBackgroundColor(Prefs.card(this));
        ((TextView) findViewById(R.id.btnBack)).setTextColor(C_INFO);
        tvTitle.setTextColor(t);
        tvSubtitle.setTextColor(C_MUTED);
        tvStatus.setTextColor(C_MUTED);
        int pad = Prefs.isCompact(this) ? dp(6) : dp(12);
        etTarget.setBackgroundDrawable(Theme.input(this));
        etTarget.setTextColor(t);
        etTarget.setHintTextColor(C_MUTED);
        etSni.setBackgroundDrawable(Theme.input(this));
        etSni.setTextColor(t);
        etSni.setHintTextColor(C_MUTED);
        btnSingle.setBackgroundDrawable(Theme.filled(this, C_INFO));
        btnSingle.setTextColor(Theme.onColor(C_INFO));
        btnFile.setBackgroundDrawable(Theme.outline(this, Prefs.stroke(this)));
        btnFile.setTextColor(C_MUTED);
        btnStop.setBackgroundDrawable(Theme.outline(this, C_WARN));
        btnStop.setTextColor(C_WARN);
        btnSave.setBackgroundDrawable(Theme.filled(this, C_INFO));
        btnSave.setTextColor(Theme.onColor(C_INFO));
        Button[] tabs = {tabResults, tabLive, tabHits};
        int ac = C_INFO;
        for (int i = 0; i < tabs.length; i++) {
            if (i == currentTab) {
                tabs[i].setBackgroundDrawable(Theme.filled(this, ac));
                tabs[i].setTextColor(Theme.onColor(ac));
            } else {
                tabs[i].setBackgroundDrawable(Theme.outline(this, Prefs.stroke(this)));
                tabs[i].setTextColor(C_MUTED);
            }
        }
        svResults.setPadding(pad, pad, pad, pad);
        svLive.setPadding(pad, pad, pad, pad);
        svHits.setPadding(pad, pad, pad, pad);
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private void configureMode() {
        String cfg = " to=" + (Prefs.getTimeoutMs(this) / 1000.0) + "s th=" + Prefs.getThreads(this);
        if ("BUGHOST".equals(mode)) {
            tvSubtitle.setText("Full probe: WAF+tech+takeover+WS+endpoints+ports+cert\n" + cfg);
            etTarget.setHint("domain or file");
            btnSingle.setText("PROBE");
        } else if ("WS".equals(mode)) {
            tvSubtitle.setText("WebSocket upgrade tester\n" + cfg);
            etTarget.setHint("domain");
            btnSingle.setText("TEST WS");
        } else if ("DPI".equals(mode)) {
            tvSubtitle.setText("Fragmented DPI bypass detection\n" + cfg);
            etTarget.setHint("host/ip");
        } else if ("SNI".equals(mode)) {
            tvSubtitle.setText("Direct SSL to Google IP + SNI host\n" + cfg);
            etTarget.setHint("domain");
            etSni.setHint("front IP");
            etSni.setText(ScanEngine.SNI_IP);
            etSni.setVisibility(View.VISIBLE);
        } else if ("TLS".equals(mode)) {
            tvSubtitle.setText("HTTP status + server over TLS\n" + cfg);
            etTarget.setHint("domain");
        } else if ("PROXY".equals(mode)) {
            tvSubtitle.setText("Check host as proxy (optional SNI)\n" + cfg);
            etTarget.setHint("proxy host/ip");
            etSni.setHint("SNI mask");
            etSni.setVisibility(View.VISIBLE);
        } else if ("PORT".equals(mode)) {
            tvSubtitle.setText("Scan ports: " + Prefs.getPorts(this) + "\n" + cfg);
            etTarget.setHint("host/ip");
        } else if ("SUBDOMAIN".equals(mode)) {
            tvSubtitle.setText("crt.sh + brute subdomain enum\n" + cfg);
            etTarget.setHint("domain");
        } else if ("REVIP".equals(mode)) {
            tvSubtitle.setText("Reverse IP lookup\n" + cfg);
            etTarget.setHint("ip/host");
        } else if ("CDN".equals(mode)) {
            tvSubtitle.setText("CDN provider detection\n" + cfg);
            etTarget.setHint("domain");
        } else if ("HEADERS".equals(mode)) {
            tvSubtitle.setText("Security headers + score\n" + cfg);
            etTarget.setHint("domain");
        } else if ("HTTP_VER".equals(mode)) {
            tvSubtitle.setText("HTTP/1.1, HTTP/2, HTTP/3 probe\n" + cfg);
            etTarget.setHint("domain");
        } else if ("DNS".equals(mode)) {
            tvSubtitle.setText("A, AAAA, CNAME, MX, NS, TXT records\n" + cfg);
            etTarget.setHint("domain");
        } else if ("TECH".equals(mode)) {
            tvSubtitle.setText("Server, framework, CMS, CDN\n" + cfg);
            etTarget.setHint("domain");
        } else if ("TAKEOVER".equals(mode)) {
            tvSubtitle.setText("Deep enum + CNAME takeover check\n" + cfg);
            etTarget.setHint("domain");
            btnSingle.setText("CHECK");
        } else if ("WAYBACK".equals(mode)) {
            tvSubtitle.setText("Wayback URLs + juicy filter\n" + cfg);
            etTarget.setHint("domain");
            etSni.setHint("Max URLs");
            etSni.setVisibility(View.VISIBLE);
            etSni.setText("5000");
            btnSingle.setText("FETCH");
        } else if ("ENDPOINT".equals(mode)) {
            tvSubtitle.setText("Fuzz " + ScanEngine.COMMON_PATHS.length + " paths\n" + cfg);
            etTarget.setHint("domain");
            btnSingle.setText("FUZZ");
        } else if ("DEEPENUM".equals(mode)) {
            tvSubtitle.setText("4-source: crt+certspotter+alienvault+HT\n" + cfg);
            etTarget.setHint("domain");
            btnSingle.setText("ENUM");
        } else if ("HOSTS".equals(mode)) {
            tvSubtitle.setText("TLD domains via crt.sh\n" + cfg);
            etTarget.setHint("TLD");
            etSni.setHint("Count");
            etSni.setVisibility(View.VISIBLE);
            btnSingle.setText("FIND");
        } else if ("SPLIT".equals(mode)) {
            tvSubtitle.setText("Split " + SPLIT_CHUNK + "-line parts\n" + cfg);
            etTarget.setVisibility(View.GONE);
            btnSingle.setVisibility(View.GONE);
            btnFile.setText("PICK & SPLIT");
        } else if ("PING".equals(mode)) {
            tvSubtitle.setText("TCP ping with latency stats\n" + cfg);
            etTarget.setHint("domain/ip");
            etSni.setHint("Count (default 4)");
            etSni.setVisibility(View.VISIBLE);
            etSni.setText("4");
            btnSingle.setText("PING");
        } else if ("CERT".equals(mode)) {
            tvSubtitle.setText("SSL certificate info, expiry, SANs\n" + cfg);
            etTarget.setHint("domain");
            btnSingle.setText("VIEW CERT");
        } else if ("REDIRECT".equals(mode)) {
            tvSubtitle.setText("Trace HTTP redirect chain\n" + cfg);
            etTarget.setHint("domain/URL");
            etSni.setHint("Max hops");
            etSni.setVisibility(View.VISIBLE);
            etSni.setText("10");
            btnSingle.setText("TRACE");
        } else if ("GEO".equals(mode)) {
            tvSubtitle.setText("IP geolocation: country, city, ISP, ASN\n" + cfg);
            etTarget.setHint("IP address");
            btnSingle.setText("LOCATE");
        } else if ("WHOIS".equals(mode)) {
            tvSubtitle.setText("RDAP domain registration data\n" + cfg);
            etTarget.setHint("domain");
            btnSingle.setText("WHOIS");
        }
    }

    private void startSingle() {
        if (running) { toast("Busy"); return; }
        final String t = san(etTarget.getText().toString());
        if (t.length() == 0) { toast("Enter target"); return; }
        clearRes();
        beginRun("Scanning " + t + " ...");
        final int to = Prefs.getTimeoutMs(this);
        final String mtd = Prefs.getMethodStr(this), sni = getSni();
        new Thread(new Runnable() {
            @Override public void run() {
                if ("BUGHOST".equals(mode)) {
                    ScanEngine.BugS bs = ScanEngine.bugProbe(t, to);
                    addBugCard(bs);
                    if (bs.sc > 0) sessionHits.add(t + " score=" + bs.sc + " conf=" + bs.confidence + "%");
                    finishRun(t, 1, bs.sc > 0 ? 1 : 0);
                } else if ("WS".equals(mode)) {
                    ScanEngine.WsR wr = ScanEngine.ws(t, to);
                    addWsCard(wr, t);
                    finishRun(t, 1, wr.ok ? 1 : 0);
                } else if ("PING".equals(mode)) {
                    int cnt = 4;
                    try { cnt = Integer.parseInt(sni); if (cnt < 1) cnt = 1; if (cnt > 20) cnt = 20; } catch (Exception x) {}
                    ScanEngine.PingR pr = ScanEngine.ping(t, cnt, to);
                    addPingCard(pr, t);
                    finishRun(t, cnt, pr.recv);
                } else if ("CERT".equals(mode)) {
                    ScanEngine.CertR cr = ScanEngine.sslCert(t, to);
                    addCertCard(cr, t);
                    finishRun(t, 1, cr.ok ? 1 : 0);
                } else if ("REDIRECT".equals(mode)) {
                    int hops = 10;
                    try { hops = Integer.parseInt(sni); if (hops < 1) hops = 1; if (hops > 50) hops = 50; } catch (Exception x) {}
                    ScanEngine.RedR rr = ScanEngine.traceRedirect(t, hops, to);
                    addRedirectCard(rr, t);
                    finishRun(t, rr.hops, rr.hops);
                } else if ("GEO".equals(mode)) {
                    ScanEngine.GeoR gr = ScanEngine.geo(t);
                    addGeoCard(gr, t);
                    finishRun(t, 1, gr.ok ? 1 : 0);
                } else if ("WHOIS".equals(mode)) {
                    ScanEngine.WhoR wr = ScanEngine.whois(t);
                    addWhoisCard(wr, t);
                    finishRun(t, 1, wr.ok ? 1 : 0);
                } else {
                    addResultCard(buildProbeCard(t, sni, to, mtd));
                    finishRun(t, 1, 1);
                }
            }
        }).start();
    }

    private void startFile(final Uri uri) {
        if (running) { toast("Busy"); return; }
        clearRes();
        beginRun("Scanning file...");
        final int to = Prefs.getTimeoutMs(this);
        final String mtd = Prefs.getMethodStr(this), sni = getSni();
        new Thread(new Runnable() {
            @Override public void run() {
                InputStream is = null;
                BufferedReader br = null;
                final List<ScanEngine.BugS> scores = new ArrayList<ScanEngine.BugS>();
                int scanned = 0, bugs = 0;
                try {
                    is = getContentResolver().openInputStream(uri);
                    if (is == null) { finishRun("file", 0, 0); return; }
                    br = new BufferedReader(new InputStreamReader(is, "UTF-8"), 65536);
                    String line;
                    while ((line = br.readLine()) != null) {
                        if (cancelled) break;
                        final String host = san(line);
                        if (host.length() == 0) continue;
                        if ("BUGHOST".equals(mode)) {
                            ScanEngine.BugS bs = ScanEngine.bugProbe(host, to);
                            scores.add(bs);
                            scanned++;
                            if (bs.sc >= 21) bugs++;
                            if (bs.sc >= 41) sessionHits.add(host + " score=" + bs.sc + " conf=" + bs.confidence + "%");
                        } else if ("WS".equals(mode)) {
                            ScanEngine.WsR wr = ScanEngine.ws(host, to);
                            scanned++;
                            if (wr.ok) { bugs++; sessionHits.add(host + " WS=OK " + wr.c + " " + wr.st); }
                            addTextLine(wr.ok ? "[OK] " + host + " WS " + wr.c + " " + wr.st + " " + wr.ms + "ms" : "[--] " + host + " WS " + wr.c + " " + wr.st + " " + wr.ms + "ms", wr.ok ? C_OK : C_WARN);
                            sessionLines.add((wr.ok ? "OK" : "FAIL") + " " + host + " WS " + wr.c + " " + wr.st + " " + wr.ms + "ms");
                        } else if ("TLS".equals(mode)) {
                            ScanEngine.Result r = ScanEngine.tls(host, to, mtd);
                            scanned++;
                            if (r.ok) { bugs++; sessionHits.add(host + " HTTP " + r.c + " " + r.s); }
                            addTextLine(r.ok ? "[OK] " + host + " HTTP " + r.c + " " + r.s + " " + r.ms + "ms" : "[--] " + host + " (" + r.e + ")", r.ok ? C_OK : C_WARN);
                            sessionLines.add((r.ok ? "OK" : "FAIL") + " " + host + " HTTP " + r.c + " " + r.s + " " + r.ms + "ms");
                        } else if ("SNI".equals(mode)) {
                            String fi = (sni.length() == 0) ? null : sni;
                            ScanEngine.Result r = ScanEngine.sni(host, fi, to, mtd);
                            scanned++;
                            if (r.ok) { bugs++; sessionHits.add(host + " SNI OK code=" + r.c); }
                            addTextLine(r.ok ? "[OK] SNI " + host + " code=" + r.c + " " + r.ms + "ms" : "[--] " + host + " (" + r.e + ")", r.ok ? C_OK : C_WARN);
                            sessionLines.add((r.ok ? "OK" : "FAIL") + " SNI " + host + " code=" + r.c + " " + r.ms + "ms");
                        } else if ("PROXY".equals(mode)) {
                            ScanEngine.Result r = ScanEngine.proxy(host, sni, to, mtd);
                            scanned++;
                            if (r.ok) { bugs++; sessionHits.add(host + " proxy OK code=" + r.c); }
                            addTextLine(r.ok ? "[OK] " + host + " code=" + r.c + " " + r.ms + "ms" : "[--] " + host + " (" + r.e + ")", r.ok ? C_OK : C_WARN);
                            sessionLines.add((r.ok ? "OK" : "FAIL") + " " + host + " proxy code=" + r.c + " " + r.ms + "ms");
                        } else if ("PORT".equals(mode)) {
                            int[] ports = Prefs.getPortsArray(ScannerActivity.this);
                            StringBuilder op = new StringBuilder();
                            for (int i = 0; i < ports.length; i++)
                                if (ScanEngine.port(host, ports[i], Math.min(to, 1200)))
                                    op.append(op.length() > 0 ? "," : "").append(ports[i]);
                            scanned++;
                            if (op.length() > 0) { bugs++; sessionHits.add(host + " ports=" + op); }
                            addTextLine(op.length() > 0 ? "[OK] " + host + " open: " + op : "[--] " + host + " none", op.length() > 0 ? C_OK : C_WARN);
                            sessionLines.add(op.length() > 0 ? "OPEN " + host + " " + op : "CLOSED " + host);
                        } else if ("DPI".equals(mode)) {
                            ScanEngine.DpiR dr = ScanEngine.dpi(host, to);
                            scanned++;
                            if (dr.ok) { bugs++; sessionHits.add(host + " DPI VULN " + dr.r); }
                            addTextLine(dr.ok ? "[VULN] " + host + " " + dr.r : "[SAFE] " + host + " " + dr.r, dr.ok ? C_DANGER : C_OK);
                            sessionLines.add(dr.ok ? "VULN " + host + " " + dr.r : "SAFE " + host + " " + dr.r);
                        } else if ("CDN".equals(mode)) {
                            ScanEngine.CdnR cr = ScanEngine.cdn(host, to);
                            scanned++;
                            if (cr.d) { bugs++; sessionHits.add(host + " CDN=" + cr.p); }
                            addTextLine(cr.d ? "[CDN] " + host + " " + cr.p + " " + cr.ms + "ms" : "[NO] " + host + " " + cr.ms + "ms", cr.d ? C_WARN : C_OK);
                            sessionLines.add((cr.d ? "CDN" : "NONE") + " " + host + " " + cr.p + " " + cr.ms + "ms");
                        } else if ("HEADERS".equals(mode)) {
                            ScanEngine.SecR sr = ScanEngine.sec(host, to);
                            scanned++;
                            if (sr.score >= 50) bugs++;
                            addTextLine("[" + sr.score + "%] " + host + " " + sr.p.size() + "/" + (sr.p.size() + sr.m.size()), sr.score < 40 ? C_WARN : C_OK);
                            sessionLines.add("HDR " + sr.score + "% " + host + " " + sr.ms + "ms");
                        } else if ("HTTP_VER".equals(mode)) {
                            ScanEngine.HvR hv = ScanEngine.httpVer(host, to);
                            scanned++;
                            addTextLine("[..] " + host + " 1.1=" + yn(hv.b1) + " 2=" + yn(hv.b2) + " 3=" + yn(hv.b3) + " " + hv.ms + "ms", C_MUTED);
                            sessionLines.add("VER " + host + " h11=" + yn(hv.b1) + " h2=" + yn(hv.b2) + " h3=" + yn(hv.b3));
                        } else if ("DNS".equals(mode)) {
                            ScanEngine.DnsR dns = ScanEngine.dns(host);
                            scanned++;
                            addTextLine("[..] " + host + " A:" + dns.a.size() + " CNAME:" + dns.cn.size(), C_MUTED);
                            sessionLines.add("DNS " + host + " A:" + dns.a.size() + " CNAME:" + dns.cn.size());
                        } else if ("PING".equals(mode)) {
                            int cnt = 4;
                            try { cnt = Integer.parseInt(sni); if (cnt < 1) cnt = 1; if (cnt > 20) cnt = 20; } catch (Exception x) {}
                            ScanEngine.PingR pr = ScanEngine.ping(host, cnt, to);
                            scanned++;
                            if (pr.ok) { bugs++; sessionHits.add(host + " ping=" + pr.avgMs + "ms"); }
                            addTextLine(pr.ok ? "[OK] " + host + " ping=" + pr.avgMs + "ms" : "[--] " + host + " timeout", pr.ok ? C_OK : C_WARN);
                            sessionLines.add((pr.ok ? "OK" : "FAIL") + " " + host + " ping=" + pr.avgMs + "ms");
                        } else if ("CERT".equals(mode)) {
                            ScanEngine.CertR cr = ScanEngine.sslCert(host, to);
                            scanned++;
                            if (cr.ok) { bugs++; sessionHits.add(host + " cert=" + cr.daysLeft + "d"); }
                            addTextLine(cr.ok ? "[OK] " + host + " cert=" + cr.daysLeft + "d" : "[--] " + host + " (" + cr.e + ")", cr.ok ? C_OK : C_WARN);
                            sessionLines.add((cr.ok ? "OK" : "FAIL") + " " + host + " cert=" + cr.daysLeft + "d");
                        } else if ("REDIRECT".equals(mode)) {
                            int hops = 10;
                            try { hops = Integer.parseInt(sni); if (hops < 1) hops = 1; if (hops > 50) hops = 50; } catch (Exception x) {}
                            ScanEngine.RedR rr = ScanEngine.traceRedirect(host, hops, to);
                            scanned++;
                            if (rr.hops > 0) { bugs++; sessionHits.add(host + " redirects=" + rr.hops); }
                            addTextLine("[..] " + host + " hops=" + rr.hops + " final=" + rr.finalUrl, C_MUTED);
                            sessionLines.add("REDIR " + host + " hops=" + rr.hops);
                        } else if ("GEO".equals(mode)) {
                            ScanEngine.GeoR gr = ScanEngine.geo(host);
                            scanned++;
                            if (gr.ok) { bugs++; sessionHits.add(host + " " + gr.country + "/" + gr.city); }
                            addTextLine(gr.ok ? "[OK] " + host + " " + gr.country + ", " + gr.city + " (" + gr.isp + ")" : "[--] " + host + " (" + gr.e + ")", gr.ok ? C_OK : C_WARN);
                            sessionLines.add((gr.ok ? "OK" : "FAIL") + " " + host + " " + gr.country + " " + gr.city);
                        } else if ("WHOIS".equals(mode)) {
                            ScanEngine.WhoR wr = ScanEngine.whois(host);
                            scanned++;
                            if (wr.ok) { bugs++; sessionHits.add(host + " whois=OK"); }
                            addTextLine(wr.ok ? "[OK] " + host + " whois retrieved" : "[--] " + host + " (" + wr.e + ")", wr.ok ? C_OK : C_WARN);
                            sessionLines.add((wr.ok ? "OK" : "FAIL") + " " + host + " whois");
                        } else scanned++;
                        if (scanned % 5 == 0) updateProg(scanned, bugs, host);
                    }
                } catch (Exception e) { addTextLine("[!] " + shortM(e), C_WARN); }
                finally {
                    try { if (br != null) br.close(); } catch (Exception x) {}
                    try { if (is != null) is.close(); } catch (Exception x) {}
                }
                if ("BUGHOST".equals(mode) && !scores.isEmpty()) {
                    Collections.sort(scores, new Comparator<ScanEngine.BugS>() {
                        @Override public int compare(ScanEngine.BugS a, ScanEngine.BugS b) { return b.sc - a.sc; }
                    });
                    showBugResults(scores, scanned);
                }
                if (!sessionHits.isEmpty() || !sessionLines.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < sessionHits.size(); i++) sb.append(sessionHits.get(i)).append("\n");
                    for (int i = 0; i < sessionLines.size(); i++) sb.append(sessionLines.get(i)).append("\n");
                    saveFile("scan_" + mode + "_" + System.currentTimeMillis() + ".txt", sb.toString());
                }
                finishRun("file", scanned, bugs);
            }
        }).start();
    }

    private void startDeep() {
        if (running) { toast("Busy"); return; }
        final String d = san(etTarget.getText().toString());
        if (d.length() == 0) { toast("Enter domain"); return; }
        clearRes();
        beginRun("Deep enum: " + d + " ...");
        addTextLine("Deep enum: " + d + " [4 sources]", C_INFO);
        new Thread(new Runnable() {
            @Override public void run() {
                ScanEngine.HCB cb = new ScanEngine.HCB() {
                    @Override public void st(String m) { addTextLine("[..] " + m, C_MUTED); }
                    @Override public void pr(String h, int dn, int t) {}
                };
                ScanEngine.SubR r = ScanEngine.deepEnum(d, cb);
                addTextLine("crt.sh: " + r.crt + "  certspotter: " + r.cs + "  alienvault: " + r.av + "  HT: " + r.ht, C_INFO);
                addTextLine("Total unique: " + r.tu, C_OK);
                for (int i = 0; i < r.s.size(); i++) {
                    addTextLine("  " + r.s.get(i), C_MUTED);
                    sessionLines.add(r.s.get(i));
                }
                finishRun(d, r.tu, r.tu);
            }
        }).start();
    }

    private void startTk() {
        if (running) { toast("Busy"); return; }
        final String d = san(etTarget.getText().toString());
        if (d.length() == 0) { toast("Enter domain"); return; }
        clearRes();
        beginRun("Takeover check: " + d + " ...");
        new Thread(new Runnable() {
            @Override public void run() {
                ScanEngine.HCB cb = new ScanEngine.HCB() {
                    @Override public void st(String m) { addTextLine("[..] " + m, C_MUTED); }
                    @Override public void pr(String h, int dn, int t) {}
                };
                ScanEngine.SubR r = ScanEngine.deepEnum(d, cb);
                int vuln = 0;
                for (int i = 0; i < r.s.size(); i++) {
                    String h = r.s.get(i);
                    ScanEngine.TkR tk = ScanEngine.takeover(h, Prefs.getTimeoutMs(ScannerActivity.this));
                    if (tk.v) {
                        vuln++;
                        addTextLine("[!!] " + h + " -> " + tk.sv + " TAKEOVER RISK", C_DANGER);
                        sessionHits.add(h + " TAKEOVER " + tk.sv);
                    } else if (tk.sv.length() > 0) {
                        addTextLine("[OK] " + h + " -> " + tk.sv + " (safe)", C_OK);
                    } else {
                        addTextLine("[OK] " + h, C_MUTED);
                    }
                }
                finishRun(d, r.s.size(), vuln);
            }
        }).start();
    }

    private void startEp() {
        if (running) { toast("Busy"); return; }
        final String d = san(etTarget.getText().toString());
        if (d.length() == 0) { toast("Enter domain"); return; }
        clearRes();
        beginRun("Endpoint fuzz: " + d + " ...");
        new Thread(new Runnable() {
            @Override public void run() {
                List<ScanEngine.EpR> res = ScanEngine.fuzzEndpoints(d, Prefs.getTimeoutMs(ScannerActivity.this));
                int found = 0;
                for (int i = 0; i < res.size(); i++) {
                    ScanEngine.EpR er = res.get(i);
                    boolean hit = er.c == 200 || er.c == 301 || er.c == 302 || er.c == 401 || er.c == 403 || er.c == 407;
                    if (hit) {
                        found++;
                        addTextLine("[HIT] " + er.url + " -> " + er.c + " " + er.ms + "ms", C_WARN);
                        sessionHits.add(er.url + " " + er.c);
                    } else {
                        addTextLine("[--] " + er.url + " -> " + er.c + " " + er.ms + "ms", C_MUTED);
                    }
                    sessionLines.add(er.url + " " + er.c + " " + er.ms + "ms");
                }
                finishRun(d, res.size(), found);
            }
        }).start();
    }

    private void startHosts() {
        if (running) { toast("Busy"); return; }
        final String tld = san(etTarget.getText().toString());
        if (tld.length() == 0) { toast("Enter TLD"); return; }
        int limit = 100;
        try { limit = Integer.parseInt(getSni()); if (limit < 1) limit = 100; if (limit > 5000) limit = 5000; } catch (Exception x) {}
        clearRes();
        beginRun("Hosts find: ." + tld + " ...");
        final int lim = limit;
        new Thread(new Runnable() {
            @Override public void run() {
                ScanEngine.HCB cb = new ScanEngine.HCB() {
                    @Override public void st(String m) { addTextLine("[..] " + m, C_MUTED); }
                    @Override public void pr(String h, int dn, int t) { addTextLine("[" + dn + "/" + t + "] " + h, C_MUTED); }
                };
                List<String> res = ScanEngine.hostsFind(tld, lim, false, Prefs.getTimeoutMs(ScannerActivity.this), cb);
                for (int i = 0; i < res.size(); i++) {
                    addTextLine(res.get(i), C_MUTED);
                    sessionLines.add(res.get(i));
                }
                finishRun(tld, res.size(), res.size());
            }
        }).start();
    }

    private void pickFile() {
        if ("SPLIT".equals(mode)) {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            startActivityForResult(intent, REQ_PICK);
            return;
        }
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        startActivityForResult(intent, REQ_PICK);
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_PICK && res == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri == null) return;
            if ("SPLIT".equals(mode)) { doSplit(uri); return; }
            startFile(uri);
        }
    }

    private void doSplit(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            if (is == null) return;
            BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            String line;
            int part = 1, count = 0;
            StringBuilder sb = new StringBuilder();
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
                count++;
                if (count >= SPLIT_CHUNK) {
                    saveFile("part_" + part + ".txt", sb.toString());
                    part++;
                    count = 0;
                    sb = new StringBuilder();
                }
            }
            br.close();
            if (sb.length() > 0) saveFile("part_" + part + ".txt", sb.toString());
            toast("Split into " + part + " part(s)");
        } catch (Exception e) { toast("Split error: " + e.getMessage()); }
    }

    private void addWsCard(final ScanEngine.WsR wr, final String host) {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                int bg = Prefs.isLight(ScannerActivity.this) ? Color.WHITE : Prefs.card(ScannerActivity.this);
                int tx = Prefs.text(ScannerActivity.this), mu = C_MUTED;
                LinearLayout card = new LinearLayout(ScannerActivity.this);
                card.setOrientation(LinearLayout.VERTICAL);
                GradientDrawable gd = new GradientDrawable();
                gd.setColor(bg);
                gd.setCornerRadius(dp(12));
                gd.setStroke(dp(1), Prefs.stroke(ScannerActivity.this));
                card.setBackgroundDrawable(gd);
                card.setPadding(dp(14), dp(14), dp(14), dp(14));
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                clp.bottomMargin = dp(10);
                card.setLayoutParams(clp);

                LinearLayout hdr = new LinearLayout(ScannerActivity.this);
                hdr.setOrientation(LinearLayout.HORIZONTAL);
                hdr.setGravity(Gravity.CENTER_VERTICAL);
                TextView badge = new TextView(ScannerActivity.this);
                badge.setText(wr.ok ? "101" : " " + wr.c);
                badge.setTextColor(Color.WHITE);
                badge.setTextSize(16f);
                badge.setTypeface(null, Typeface.BOLD);
                badge.setGravity(Gravity.CENTER);
                GradientDrawable bgd = new GradientDrawable();
                bgd.setColor(wr.ok ? C_OK : C_DANGER);
                bgd.setShape(GradientDrawable.OVAL);
                badge.setBackgroundDrawable(bgd);
                int sz = dp(44);
                badge.setLayoutParams(new LinearLayout.LayoutParams(sz, sz));
                hdr.addView(badge);

                LinearLayout info = new LinearLayout(ScannerActivity.this);
                info.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                ilp.leftMargin = dp(12);
                info.setLayoutParams(ilp);
                TextView st = new TextView(ScannerActivity.this);
                st.setText(wr.ok ? "UPGRADE ACCEPTED" : "UPGRADE REFUSED");
                st.setTextColor(wr.ok ? C_OK : C_DANGER);
                st.setTextSize(14f);
                st.setTypeface(null, Typeface.BOLD);
                info.addView(st);
                TextView hn = new TextView(ScannerActivity.this);
                hn.setText(host);
                hn.setTextColor(tx);
                hn.setTextSize(13f);
                info.addView(hn);
                hdr.addView(info);

                TextView tm = new TextView(ScannerActivity.this);
                tm.setText(wr.ms + "ms");
                tm.setTextColor(mu);
                tm.setTextSize(12f);
                hdr.addView(tm);
                card.addView(hdr);

                TextView dt = new TextView(ScannerActivity.this);
                dt.setPadding(0, dp(8), 0, 0);
                dt.setTextColor(mu);
                dt.setTextSize(12f);
                dt.setTypeface(Typeface.MONOSPACE);
                dt.setText("HTTP " + wr.c + " " + wr.st + "\nHeaders: " + wr.hdr);
                card.addView(dt);
                resultsContainer.addView(card);
            }
        });
    }

    private void addPingCard(final ScanEngine.PingR pr, final String host) {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                int bg = Prefs.isLight(ScannerActivity.this) ? Color.WHITE : Prefs.card(ScannerActivity.this);
                int tx = Prefs.text(ScannerActivity.this), mu = C_MUTED;
                LinearLayout card = new LinearLayout(ScannerActivity.this);
                card.setOrientation(LinearLayout.VERTICAL);
                GradientDrawable gd = new GradientDrawable();
                gd.setColor(bg);
                gd.setCornerRadius(dp(12));
                gd.setStroke(dp(1), Prefs.stroke(ScannerActivity.this));
                card.setBackgroundDrawable(gd);
                card.setPadding(dp(14), dp(14), dp(14), dp(14));
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                clp.bottomMargin = dp(10);
                card.setLayoutParams(clp);

                LinearLayout hdr = new LinearLayout(ScannerActivity.this);
                hdr.setOrientation(LinearLayout.HORIZONTAL);
                hdr.setGravity(Gravity.CENTER_VERTICAL);
                TextView badge = new TextView(ScannerActivity.this);
                badge.setText("@");
                badge.setTextColor(Color.WHITE);
                badge.setTextSize(16f);
                badge.setTypeface(null, Typeface.BOLD);
                badge.setGravity(Gravity.CENTER);
                GradientDrawable bgd = new GradientDrawable();
                bgd.setColor(pr.ok ? C_OK : C_DANGER);
                bgd.setShape(GradientDrawable.OVAL);
                badge.setBackgroundDrawable(bgd);
                int sz = dp(44);
                badge.setLayoutParams(new LinearLayout.LayoutParams(sz, sz));
                hdr.addView(badge);

                LinearLayout info = new LinearLayout(ScannerActivity.this);
                info.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                ilp.leftMargin = dp(12);
                info.setLayoutParams(ilp);
                TextView st = new TextView(ScannerActivity.this);
                st.setText(pr.ok ? "REACHABLE" : "UNREACHABLE");
                st.setTextColor(pr.ok ? C_OK : C_DANGER);
                st.setTextSize(14f);
                st.setTypeface(null, Typeface.BOLD);
                info.addView(st);
                TextView hn = new TextView(ScannerActivity.this);
                hn.setText(host + (pr.ip.length() > 0 ? " (" + pr.ip + ")" : ""));
                hn.setTextColor(tx);
                hn.setTextSize(13f);
                info.addView(hn);
                hdr.addView(info);
                card.addView(hdr);

                if (pr.ok) {
                    LinearLayout stats = new LinearLayout(ScannerActivity.this);
                    stats.setOrientation(LinearLayout.HORIZONTAL);
                    stats.setPadding(0, dp(8), 0, 0);
                    stats.addView(pingStat("Sent", String.valueOf(pr.sent)));
                    stats.addView(pingStat("Recv", String.valueOf(pr.recv)));
                    stats.addView(pingStat("Avg", pr.avgMs + "ms"));
                    stats.addView(pingStat("Min", pr.minMs + "ms"));
                    stats.addView(pingStat("Max", pr.maxMs + "ms"));
                    card.addView(stats);
                }
                resultsContainer.addView(card);
            }
        });
    }

    private View pingStat(String label, String value) {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Prefs.card(this));
        g.setCornerRadius(dp(6));
        b.setBackgroundDrawable(g);
        b.setPadding(dp(8), dp(6), dp(8), dp(6));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        blp.rightMargin = dp(4);
        b.setLayoutParams(blp);
        TextView lv = new TextView(this);
        lv.setText(label);
        lv.setTextColor(C_MUTED);
        lv.setTextSize(9f);
        b.addView(lv);
        TextView vv = new TextView(this);
        vv.setText(value);
        vv.setTextColor(Prefs.text(this));
        vv.setTextSize(13f);
        vv.setTypeface(null, Typeface.BOLD);
        b.addView(vv);
        return b;
    }

    private void addCertCard(final ScanEngine.CertR cr, final String host) {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                int bg = Prefs.isLight(ScannerActivity.this) ? Color.WHITE : Prefs.card(ScannerActivity.this);
                int tx = Prefs.text(ScannerActivity.this), mu = C_MUTED;
                LinearLayout card = new LinearLayout(ScannerActivity.this);
                card.setOrientation(LinearLayout.VERTICAL);
                GradientDrawable gd = new GradientDrawable();
                gd.setColor(bg);
                gd.setCornerRadius(dp(12));
                gd.setStroke(dp(1), Prefs.stroke(ScannerActivity.this));
                card.setBackgroundDrawable(gd);
                card.setPadding(dp(14), dp(14), dp(14), dp(14));
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                clp.bottomMargin = dp(10);
                card.setLayoutParams(clp);

                LinearLayout hdr = new LinearLayout(ScannerActivity.this);
                hdr.setOrientation(LinearLayout.HORIZONTAL);
                hdr.setGravity(Gravity.CENTER_VERTICAL);
                TextView badge = new TextView(ScannerActivity.this);
                badge.setText("K");
                badge.setTextColor(Color.WHITE);
                badge.setTextSize(16f);
                badge.setTypeface(null, Typeface.BOLD);
                badge.setGravity(Gravity.CENTER);
                int badgeColor = cr.ok ? (cr.daysLeft < 30 ? C_WARN : C_OK) : C_DANGER;
                GradientDrawable bgd = new GradientDrawable();
                bgd.setColor(badgeColor);
                bgd.setShape(GradientDrawable.OVAL);
                badge.setBackgroundDrawable(bgd);
                int sz = dp(44);
                badge.setLayoutParams(new LinearLayout.LayoutParams(sz, sz));
                hdr.addView(badge);

                LinearLayout info = new LinearLayout(ScannerActivity.this);
                info.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                ilp.leftMargin = dp(12);
                info.setLayoutParams(ilp);
                TextView st = new TextView(ScannerActivity.this);
                st.setText(cr.ok ? (cr.daysLeft < 30 ? "EXPIRING SOON" : "VALID") : "FAILED");
                st.setTextColor(badgeColor);
                st.setTextSize(14f);
                st.setTypeface(null, Typeface.BOLD);
                info.addView(st);
                TextView hn = new TextView(ScannerActivity.this);
                hn.setText(host);
                hn.setTextColor(tx);
                hn.setTextSize(13f);
                info.addView(hn);
                hdr.addView(info);
                card.addView(hdr);

                if (cr.ok) {
                    TextView days = new TextView(ScannerActivity.this);
                    days.setPadding(0, dp(8), 0, 0);
                    days.setTextColor(cr.daysLeft < 30 ? C_WARN : mu);
                    days.setTextSize(13f);
                    days.setTypeface(null, Typeface.BOLD);
                    days.setText("Expires in " + cr.daysLeft + " days");
                    card.addView(days);
                    addCertRow(card, "Subject", cr.subject, mu);
                    addCertRow(card, "Issuer", cr.issuer, mu);
                    addCertRow(card, "Serial", cr.serial, mu);
                    addCertRow(card, "Algorithm", cr.sigAlg, mu);
                    addCertRow(card, "Not Before", cr.notBefore, mu);
                    addCertRow(card, "Not After", cr.notAfter, mu);
                    if (cr.sans.length() > 0) addCertRow(card, "SANs", cr.sans.substring(0, Math.min(cr.sans.length(), 300)), mu);
                } else {
                    TextView err = new TextView(ScannerActivity.this);
                    err.setPadding(0, dp(8), 0, 0);
                    err.setTextColor(C_DANGER);
                    err.setTextSize(12f);
                    err.setText(cr.e);
                    card.addView(err);
                }
                resultsContainer.addView(card);
            }
        });
    }

    private void addCertRow(LinearLayout parent, String label, String value, int color) {
        TextView tv = new TextView(this);
        tv.setPadding(0, dp(4), 0, 0);
        tv.setTextColor(color);
        tv.setTextSize(11f);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setText(label + ": " + value);
        parent.addView(tv);
    }

    private void addRedirectCard(final ScanEngine.RedR rr, final String host) {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                int bg = Prefs.isLight(ScannerActivity.this) ? Color.WHITE : Prefs.card(ScannerActivity.this);
                int tx = Prefs.text(ScannerActivity.this), mu = C_MUTED;
                LinearLayout card = new LinearLayout(ScannerActivity.this);
                card.setOrientation(LinearLayout.VERTICAL);
                GradientDrawable gd = new GradientDrawable();
                gd.setColor(bg);
                gd.setCornerRadius(dp(12));
                gd.setStroke(dp(1), Prefs.stroke(ScannerActivity.this));
                card.setBackgroundDrawable(gd);
                card.setPadding(dp(14), dp(14), dp(14), dp(14));
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                clp.bottomMargin = dp(10);
                card.setLayoutParams(clp);

                LinearLayout hdr = new LinearLayout(ScannerActivity.this);
                hdr.setOrientation(LinearLayout.HORIZONTAL);
                hdr.setGravity(Gravity.CENTER_VERTICAL);
                TextView badge = new TextView(ScannerActivity.this);
                badge.setText("R");
                badge.setTextColor(Color.WHITE);
                badge.setTextSize(16f);
                badge.setTypeface(null, Typeface.BOLD);
                badge.setGravity(Gravity.CENTER);
                GradientDrawable bgd = new GradientDrawable();
                bgd.setColor(rr.loop ? C_WARN : C_INFO);
                bgd.setShape(GradientDrawable.OVAL);
                badge.setBackgroundDrawable(bgd);
                int sz = dp(44);
                badge.setLayoutParams(new LinearLayout.LayoutParams(sz, sz));
                hdr.addView(badge);

                LinearLayout info = new LinearLayout(ScannerActivity.this);
                info.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                ilp.leftMargin = dp(12);
                info.setLayoutParams(ilp);
                TextView st = new TextView(ScannerActivity.this);
                st.setText(rr.loop ? "REDIRECT LOOP" : "TRACE COMPLETE");
                st.setTextColor(rr.loop ? C_WARN : C_INFO);
                st.setTextSize(14f);
                st.setTypeface(null, Typeface.BOLD);
                info.addView(st);
                TextView hn = new TextView(ScannerActivity.this);
                hn.setText(host);
                hn.setTextColor(tx);
                hn.setTextSize(13f);
                info.addView(hn);
                hdr.addView(info);
                card.addView(hdr);

                TextView hops = new TextView(ScannerActivity.this);
                hops.setPadding(0, dp(8), 0, 0);
                hops.setTextColor(mu);
                hops.setTextSize(12f);
                hops.setText("Hops: " + rr.hops + (rr.loop ? " (LOOP DETECTED)" : "") + "\nFinal: " + rr.finalUrl);
                card.addView(hops);

                for (int i = 0; i < rr.chain.size(); i++) {
                    TextView step = new TextView(ScannerActivity.this);
                    step.setPadding(dp(8), dp(2), 0, dp(2));
                    step.setTextColor(mu);
                    step.setTextSize(11f);
                    step.setTypeface(Typeface.MONOSPACE);
                    step.setText((i + 1) + ". " + rr.chain.get(i));
                    card.addView(step);
                }
                resultsContainer.addView(card);
            }
        });
    }

    private void addGeoCard(final ScanEngine.GeoR gr, final String host) {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                int bg = Prefs.isLight(ScannerActivity.this) ? Color.WHITE : Prefs.card(ScannerActivity.this);
                int tx = Prefs.text(ScannerActivity.this), mu = C_MUTED;
                LinearLayout card = new LinearLayout(ScannerActivity.this);
                card.setOrientation(LinearLayout.VERTICAL);
                GradientDrawable gd = new GradientDrawable();
                gd.setColor(bg);
                gd.setCornerRadius(dp(12));
                gd.setStroke(dp(1), Prefs.stroke(ScannerActivity.this));
                card.setBackgroundDrawable(gd);
                card.setPadding(dp(14), dp(14), dp(14), dp(14));
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                clp.bottomMargin = dp(10);
                card.setLayoutParams(clp);

                LinearLayout hdr = new LinearLayout(ScannerActivity.this);
                hdr.setOrientation(LinearLayout.HORIZONTAL);
                hdr.setGravity(Gravity.CENTER_VERTICAL);
                TextView badge = new TextView(ScannerActivity.this);
                badge.setText("G");
                badge.setTextColor(Color.WHITE);
                badge.setTextSize(16f);
                badge.setTypeface(null, Typeface.BOLD);
                badge.setGravity(Gravity.CENTER);
                GradientDrawable bgd = new GradientDrawable();
                bgd.setColor(gr.ok ? C_OK : C_DANGER);
                bgd.setShape(GradientDrawable.OVAL);
                badge.setBackgroundDrawable(bgd);
                int sz = dp(44);
                badge.setLayoutParams(new LinearLayout.LayoutParams(sz, sz));
                hdr.addView(badge);

                LinearLayout info = new LinearLayout(ScannerActivity.this);
                info.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                ilp.leftMargin = dp(12);
                info.setLayoutParams(ilp);
                TextView st = new TextView(ScannerActivity.this);
                st.setText(gr.ok ? "FOUND" : "FAILED");
                st.setTextColor(gr.ok ? C_OK : C_DANGER);
                st.setTextSize(14f);
                st.setTypeface(null, Typeface.BOLD);
                info.addView(st);
                TextView hn = new TextView(ScannerActivity.this);
                hn.setText(host);
                hn.setTextColor(tx);
                hn.setTextSize(13f);
                info.addView(hn);
                hdr.addView(info);
                card.addView(hdr);

                if (gr.ok) {
                    LinearLayout grid = new LinearLayout(ScannerActivity.this);
                    grid.setOrientation(LinearLayout.VERTICAL);
                    grid.setPadding(0, dp(8), 0, 0);
                    addGeoRow(grid, "Country", gr.country, mu);
                    addGeoRow(grid, "City", gr.city, mu);
                    addGeoRow(grid, "ISP", gr.isp, mu);
                    addGeoRow(grid, "Organization", gr.org, mu);
                    addGeoRow(grid, "ASN", gr.asn, mu);
                    addGeoRow(grid, "Latitude", gr.lat, mu);
                    addGeoRow(grid, "Longitude", gr.lon, mu);
                    card.addView(grid);
                } else {
                    TextView err = new TextView(ScannerActivity.this);
                    err.setPadding(0, dp(8), 0, 0);
                    err.setTextColor(C_DANGER);
                    err.setTextSize(12f);
                    err.setText(gr.e);
                    card.addView(err);
                }
                resultsContainer.addView(card);
            }
        });
    }

    private void addGeoRow(LinearLayout parent, String label, String value, int color) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(3), 0, dp(3));
        TextView lv = new TextView(this);
        lv.setText(label + ": ");
        lv.setTextColor(color);
        lv.setTextSize(12f);
        lv.setTypeface(null, Typeface.BOLD);
        row.addView(lv);
        TextView vv = new TextView(this);
        vv.setText(value);
        vv.setTextColor(Prefs.text(this));
        vv.setTextSize(12f);
        row.addView(vv);
        parent.addView(row);
    }

    private void addWhoisCard(final ScanEngine.WhoR wr, final String host) {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                int bg = Prefs.isLight(ScannerActivity.this) ? Color.WHITE : Prefs.card(ScannerActivity.this);
                int tx = Prefs.text(ScannerActivity.this), mu = C_MUTED;
                LinearLayout card = new LinearLayout(ScannerActivity.this);
                card.setOrientation(LinearLayout.VERTICAL);
                GradientDrawable gd = new GradientDrawable();
                gd.setColor(bg);
                gd.setCornerRadius(dp(12));
                gd.setStroke(dp(1), Prefs.stroke(ScannerActivity.this));
                card.setBackgroundDrawable(gd);
                card.setPadding(dp(14), dp(14), dp(14), dp(14));
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                clp.bottomMargin = dp(10);
                card.setLayoutParams(clp);

                LinearLayout hdr = new LinearLayout(ScannerActivity.this);
                hdr.setOrientation(LinearLayout.HORIZONTAL);
                hdr.setGravity(Gravity.CENTER_VERTICAL);
                TextView badge = new TextView(ScannerActivity.this);
                badge.setText("O");
                badge.setTextColor(Color.WHITE);
                badge.setTextSize(16f);
                badge.setTypeface(null, Typeface.BOLD);
                badge.setGravity(Gravity.CENTER);
                GradientDrawable bgd = new GradientDrawable();
                bgd.setColor(wr.ok ? C_OK : C_DANGER);
                bgd.setShape(GradientDrawable.OVAL);
                badge.setBackgroundDrawable(bgd);
                int sz = dp(44);
                badge.setLayoutParams(new LinearLayout.LayoutParams(sz, sz));
                hdr.addView(badge);

                LinearLayout info = new LinearLayout(ScannerActivity.this);
                info.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                ilp.leftMargin = dp(12);
                info.setLayoutParams(ilp);
                TextView st = new TextView(ScannerActivity.this);
                st.setText(wr.ok ? "RETRIEVED" : "FAILED");
                st.setTextColor(wr.ok ? C_OK : C_DANGER);
                st.setTextSize(14f);
                st.setTypeface(null, Typeface.BOLD);
                info.addView(st);
                TextView hn = new TextView(ScannerActivity.this);
                hn.setText(host);
                hn.setTextColor(tx);
                hn.setTextSize(13f);
                info.addView(hn);
                hdr.addView(info);
                card.addView(hdr);

                if (wr.ok) {
                    ScrollView sv = new ScrollView(ScannerActivity.this);
                    sv.setPadding(0, dp(8), 0, 0);
                    TextView raw = new TextView(ScannerActivity.this);
                    raw.setText(wr.raw);
                    raw.setTextColor(mu);
                    raw.setTextSize(10f);
                    raw.setTypeface(Typeface.MONOSPACE);
                    sv.addView(raw);
                    card.addView(sv);
                } else {
                    TextView err = new TextView(ScannerActivity.this);
                    err.setPadding(0, dp(8), 0, 0);
                    err.setTextColor(C_DANGER);
                    err.setTextSize(12f);
                    err.setText(wr.e);
                    card.addView(err);
                }
                resultsContainer.addView(card);
            }
        });
    }

    private void showBugResults(final List<ScanEngine.BugS> scores, final int total) {
        switchTab(0);
        runOnUiThread(new Runnable() {
            @Override public void run() {
                resultsContainer.removeAllViews();
                if (!scores.isEmpty()) {
                    int bugs = 0, sum = 0, top = 0;
                    for (int i = 0; i < scores.size(); i++) {
                        ScanEngine.BugS s = scores.get(i);
                        if (s.sc >= 21) bugs++;
                        sum += s.sc;
                        if (s.sc > top) top = s.sc;
                    }
                    addSummary(total, bugs, scores.size() > 0 ? sum / scores.size() : 0, top);
                }
                int show = Math.min(scores.size(), 80);
                for (int i = 0; i < show; i++) resultsContainer.addView(buildBugCard(scores.get(i)));
                if (scores.size() > 80) {
                    TextView more = new TextView(ScannerActivity.this);
                    more.setText("...and " + (scores.size() - 80) + " more (saved to Downloads/BugScanner/)");
                    more.setTextColor(C_MUTED);
                    more.setTextSize(12f);
                    more.setPadding(dp(4), dp(8), dp(4), dp(4));
                    resultsContainer.addView(more);
                }
            }
        });
    }

    private void addBugCard(final ScanEngine.BugS s) {
        runOnUiThread(new Runnable() {
            @Override public void run() { resultsContainer.addView(buildBugCard(s)); }
        });
    }

    private void addResultCard(final View card) {
        runOnUiThread(new Runnable() {
            @Override public void run() { resultsContainer.addView(card); }
        });
    }

    private void addTextLine(final String text, final int color) {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                TextView tv = new TextView(ScannerActivity.this);
                tv.setText(text);
                tv.setTextColor(color);
                tv.setTextSize(12f);
                tv.setTypeface(Typeface.MONOSPACE);
                tv.setPadding(0, dp(2), 0, dp(2));
                liveContainer.addView(tv);
                svLive.post(new Runnable() {
                    @Override public void run() { svLive.fullScroll(View.FOCUS_DOWN); }
                });
            }
        });
    }

    private void addSummary(int scanned, int bugs, int avg, int top) {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(0, 0, 0, dp(10));
        bar.addView(statBox("Scanned", "" + scanned));
        bar.addView(statBox("Bugs", "" + bugs));
        bar.addView(statBox("Avg", "" + avg));
        bar.addView(statBox("Top", "" + top));
        resultsContainer.addView(bar);
    }

    private View statBox(String label, String value) {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Prefs.card(this));
        g.setCornerRadius(dp(8));
        g.setStroke(dp(1), Prefs.stroke(this));
        b.setBackgroundDrawable(g);
        b.setPadding(dp(10), dp(8), dp(10), dp(8));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        blp.rightMargin = dp(6);
        b.setLayoutParams(blp);
        TextView lv = new TextView(this);
        lv.setText(label);
        lv.setTextColor(C_MUTED);
        lv.setTextSize(10f);
        b.addView(lv);
        TextView vv = new TextView(this);
        vv.setText(value);
        vv.setTextColor(Prefs.text(this));
        vv.setTextSize(15f);
        vv.setTypeface(null, Typeface.BOLD);
        b.addView(vv);
        return b;
    }

    private View buildBugCard(ScanEngine.BugS s) {
        int sc = Prefs.isLight(this) ? Color.WHITE : Prefs.card(this), tx = Prefs.text(this), mu = C_MUTED;
        int scoreColor;
        String lvl;
        if (s.sc >= 61) { scoreColor = C_DANGER; lvl = "CRITICAL"; }
        else if (s.sc >= 41) { scoreColor = C_WARN; lvl = "HIGH"; }
        else if (s.sc >= 21) { scoreColor = 0xFF3B82F6; lvl = "MEDIUM"; }
        else { scoreColor = 0xFF64748B; lvl = "LOW"; }
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(sc);
        gd.setCornerRadius(dp(12));
        gd.setStroke(dp(1), Prefs.stroke(this));
        card.setBackgroundDrawable(gd);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.bottomMargin = dp(10);
        card.setLayoutParams(clp);

        LinearLayout r1 = new LinearLayout(this);
        r1.setOrientation(LinearLayout.HORIZONTAL);
        r1.setGravity(Gravity.CENTER_VERTICAL);
        TextView badge = new TextView(this);
        badge.setText(String.valueOf(s.sc));
        badge.setTextColor(Color.WHITE);
        badge.setTextSize(16f);
        badge.setTypeface(null, Typeface.BOLD);
        badge.setGravity(Gravity.CENTER);
        GradientDrawable bgd = new GradientDrawable();
        bgd.setColor(scoreColor);
        bgd.setShape(GradientDrawable.OVAL);
        badge.setBackgroundDrawable(bgd);
        int sz = dp(42);
        badge.setLayoutParams(new LinearLayout.LayoutParams(sz, sz));
        r1.addView(badge);

        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nlp.leftMargin = dp(10);
        names.setLayoutParams(nlp);
        TextView lvt = new TextView(this);
        lvt.setText(lvl + "  " + s.cfStr());
        lvt.setTextColor(scoreColor);
        lvt.setTextSize(11f);
        lvt.setTypeface(null, Typeface.BOLD);
        names.addView(lvt);
        TextView ht = new TextView(this);
        ht.setText(s.ip.length() > 0 ? s.ip : "unreachable");
        ht.setTextColor(tx);
        ht.setTextSize(14f);
        ht.setTypeface(null, Typeface.BOLD);
        names.addView(ht);
        r1.addView(names);

        TextView mt = new TextView(this);
        mt.setText((s.hc > 0 ? "HTTP " + s.hc : "") + "  " + s.ms + "ms  " + s.confidence + "%");
        mt.setTextColor(mu);
        mt.setTextSize(11f);
        r1.addView(mt);
        card.addView(r1);

        LinearLayout meta = new LinearLayout(this);
        meta.setOrientation(LinearLayout.VERTICAL);
        meta.setPadding(0, dp(6), 0, 0);
        if (s.sv.length() > 0) {
            TextView sv2 = new TextView(this);
            sv2.setText("Server: " + s.sv);
            sv2.setTextColor(mu);
            sv2.setTextSize(12f);
            meta.addView(sv2);
        }
        TextView wf2 = new TextView(this);
        wf2.setText("WAF: " + s.wn);
        wf2.setTextColor(s.wf ? C_WARN : C_OK);
        wf2.setTextSize(12f);
        meta.addView(wf2);
        if (s.ws) {
            TextView ws2 = new TextView(this);
            ws2.setText("WebSocket: UPGRADE ACCEPTED  " + s.wss);
            ws2.setTextColor(C_WARN);
            ws2.setTextSize(12f);
            meta.addView(ws2);
        }
        if (s.tk) {
            TextView tk2 = new TextView(this);
            tk2.setText("TAKEOVER: " + s.tks + "  " + s.si.get(Math.min(s.si.size() - 1, 5)));
            tk2.setTextColor(C_DANGER);
            tk2.setTextSize(12f);
            tk2.setTypeface(null, Typeface.BOLD);
            meta.addView(tk2);
        }
        card.addView(meta);

        if (!s.th.isEmpty()) {
            LinearLayout tr = new LinearLayout(this);
            tr.setOrientation(LinearLayout.HORIZONTAL);
            tr.setPadding(0, dp(6), 0, 0);
            for (int i = 0; i < Math.min(s.th.size(), 8); i++) {
                String tech = s.th.get(i);
                TextView pill = new TextView(this);
                pill.setText(tech);
                pill.setTextSize(10f);
                pill.setTypeface(null, Typeface.BOLD);
                GradientDrawable tbg = new GradientDrawable();
                tbg.setColor(0x333D8BFF);
                tbg.setCornerRadius(dp(4));
                pill.setBackgroundDrawable(tbg);
                pill.setTextColor(C_INFO);
                pill.setPadding(dp(5), dp(3), dp(5), dp(3));
                LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (i > 0) tlp.leftMargin = dp(4);
                pill.setLayoutParams(tlp);
                tr.addView(pill);
            }
            card.addView(tr);
        }

        if (!s.tg.isEmpty()) {
            LinearLayout tr2 = new LinearLayout(this);
            tr2.setOrientation(LinearLayout.HORIZONTAL);
            tr2.setPadding(0, dp(8), 0, 0);
            for (int i = 0; i < s.tg.size(); i++) {
                String tag = s.tg.get
scanner_rest = r'''
                String tag = s.tg.get(i);
                boolean bad = tag.contains("NO WAF") || tag.contains("TAKEOVER") || tag.contains("EXPOSED") || tag.contains("WS") || tag.contains("NO HSTS") || tag.contains("HTTP") || tag.contains("RISKY") || tag.contains("PORTS") || tag.contains("ENDPOINT");
                TextView pill = new TextView(this);
                pill.setText(tag);
                pill.setTextSize(10f);
                pill.setTypeface(null, Typeface.BOLD);
                GradientDrawable tbg2 = new GradientDrawable();
                tbg2.setColor(bad ? 0x33FF3B4E : 0x3322C55E);
                tbg2.setCornerRadius(dp(4));
                pill.setBackgroundDrawable(tbg2);
                pill.setTextColor(bad ? C_WARN : C_OK);
                pill.setPadding(dp(5), dp(3), dp(5), dp(3));
                LinearLayout.LayoutParams tlp2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (i > 0) tlp2.leftMargin = dp(4);
                pill.setLayoutParams(tlp2);
                tr2.addView(pill);
            }
            card.addView(tr2);
        }

        if (!s.op.isEmpty()) {
            TextView pt = new TextView(this);
            pt.setPadding(0, dp(6), 0, 0);
            pt.setText("Ports: " + joinI(s.op));
            pt.setTextColor(mu);
            pt.setTextSize(11f);
            card.addView(pt);
        }
        if (s.ep > 0) {
            TextView ep2 = new TextView(this);
            ep2.setPadding(0, dp(4), 0, 0);
            ep2.setText("Endpoints: " + s.ep + " sensitive paths found");
            ep2.setTextColor(s.ep >= 3 ? C_WARN : C_MUTED);
            ep2.setTextSize(11f);
            card.addView(ep2);
        }

        if (!s.si.isEmpty()) {
            LinearLayout sigs = new LinearLayout(this);
            sigs.setOrientation(LinearLayout.VERTICAL);
            sigs.setPadding(0, dp(8), 0, 0);
            for (int i = 0; i < s.si.size(); i++) {
                boolean isRisk = s.si.get(i).contains("NO") || s.si.get(i).contains("TAKEOVER") || s.si.get(i).contains("Weak") || s.si.get(i).contains("HTTP") || s.si.get(i).contains("Risky") || s.si.get(i).contains("WS") || s.si.get(i).contains("endpoint") || s.si.get(i).contains("unusual") || s.si.get(i).contains("open") || s.si.get(i).contains("PORT") || s.si.get(i).contains("EXPOSED");
                TextView sig = new TextView(this);
                sig.setText((isRisk ? " !! " : "    ") + s.si.get(i));
                sig.setTextColor(isRisk ? C_WARN : mu);
                sig.setTextSize(11f);
                sigs.addView(sig);
            }
            card.addView(sigs);
        }
        return card;
    }

    private View buildProbeCard(String host, String sni, int to, String mtd) {
        int bg = Prefs.isLight(this) ? Color.WHITE : Prefs.card(this), tx = Prefs.text(this), mu = C_MUTED, ac = C_INFO;
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(bg);
        gd.setCornerRadius(dp(12));
        gd.setStroke(dp(1), Prefs.stroke(this));
        card.setBackgroundDrawable(gd);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.bottomMargin = dp(10);
        card.setLayoutParams(clp);

        LinearLayout hdr = new LinearLayout(this);
        hdr.setOrientation(LinearLayout.HORIZONTAL);
        hdr.setGravity(Gravity.CENTER_VERTICAL);
        TextView modeTv = new TextView(this);
        modeTv.setText(mode);
        modeTv.setTextColor(ac);
        modeTv.setTextSize(11f);
        modeTv.setTypeface(null, Typeface.BOLD);
        hdr.addView(modeTv);
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        hdr.addView(spacer);
        TextView hostTv = new TextView(this);
        hostTv.setText(host);
        hostTv.setTextColor(tx);
        hostTv.setTextSize(14f);
        hostTv.setTypeface(null, Typeface.BOLD);
        hdr.addView(hostTv);
        card.addView(hdr);

        TextView body = new TextView(this);
        body.setPadding(0, dp(8), 0, 0);
        body.setTextColor(mu);
        body.setTextSize(12f);
        body.setTypeface(Typeface.MONOSPACE);
        if ("TLS".equals(mode)) {
            ScanEngine.Result r = ScanEngine.tls(host, to, mtd);
            body.setText(r.ok ? "[OK] HTTP " + r.c + "  " + r.s + "  " + r.ip + "  " + r.ms + "ms" : "[--] " + host + "  (" + r.e + ")");
            body.setTextColor(r.ok ? C_OK : C_WARN);
        } else if ("SNI".equals(mode)) {
            String fi = (sni == null) ? "" : sni.trim();
            ScanEngine.Result r = ScanEngine.sni(host, fi, to, mtd);
            body.setText(r.ok ? "[OK] SNI accepted  " + r.ms + "ms  code=" + r.c : "[--] " + r.e);
            body.setTextColor(r.ok ? C_OK : C_WARN);
        } else if ("PROXY".equals(mode)) {
            ScanEngine.Result r = ScanEngine.proxy(host, sni, to, mtd);
            body.setText(r.ok ? "[OK] code=" + r.c + "  " + r.ms + "ms" : "[--] " + r.e);
            body.setTextColor(r.ok ? C_OK : C_WARN);
        } else if ("PORT".equals(mode)) {
            int[] ports = Prefs.getPortsArray(ScannerActivity.this);
            StringBuilder op = new StringBuilder();
            for (int i = 0; i < ports.length; i++)
                if (ScanEngine.port(host, ports[i], Math.min(to, 1200)))
                    op.append(op.length() > 0 ? "," : "").append(ports[i] + "(" + ScanEngine.portService(ports[i]) + ")");
            body.setText(op.length() > 0 ? "[OK] open: " + op : "[--] no open ports");
            body.setTextColor(op.length() > 0 ? C_OK : C_WARN);
        } else if ("DPI".equals(mode)) {
            ScanEngine.DpiR d = ScanEngine.dpi(host, to);
            body.setText(d.ok ? "[VULN] " + d.r : "[SAFE] " + d.r);
            body.setTextColor(d.ok ? C_DANGER : C_OK);
        } else if ("CDN".equals(mode)) {
            ScanEngine.CdnR c = ScanEngine.cdn(host, to);
            body.setText(c.d ? "[CDN] " + c.p + "  " + c.ms + "ms" : "[NO CDN] " + c.ms + "ms");
            body.setTextColor(c.d ? C_WARN : C_OK);
        } else if ("HEADERS".equals(mode)) {
            ScanEngine.SecR sr = ScanEngine.sec(host, to);
            body.setText("Score: " + sr.score + "%  Present: " + sr.p.size() + "/" + (sr.p.size() + sr.m.size()) + "  " + sr.ms + "ms");
            body.setTextColor(sr.score < 40 ? C_WARN : sr.score < 70 ? C_WARN : C_OK);
        } else if ("HTTP_VER".equals(mode)) {
            ScanEngine.HvR hv = ScanEngine.httpVer(host, to);
            body.setText("HTTP/1.1=" + yn(hv.b1) + "  2=" + yn(hv.b2) + "  3=" + yn(hv.b3) + "  " + hv.ms + "ms");
        } else if ("DNS".equals(mode)) {
            ScanEngine.DnsR dns = ScanEngine.dns(host);
            StringBuilder sb = new StringBuilder();
            if (!dns.a.isEmpty()) sb.append("A: ").append(join(dns.a)).append("\n");
            if (!dns.cn.isEmpty()) sb.append("CNAME: ").append(join(dns.cn)).append("\n");
            if (!dns.mx.isEmpty()) sb.append("MX: ").append(join(dns.mx)).append("\n");
            if (!dns.ns.isEmpty()) sb.append("NS: ").append(join(dns.ns));
            body.setText(sb.toString());
        } else if ("TECH".equals(mode)) {
            ScanEngine.TechR tr = ScanEngine.tech(host, to);
            StringBuilder sb = new StringBuilder();
            sb.append("HTTP ").append(tr.c).append("  ").append(tr.ms).append("ms");
            if (tr.sv.length() > 0) sb.append("\nServer: ").append(tr.sv);
            if (tr.p.length() > 0) sb.append("\nPowered: ").append(tr.p);
            if (!tr.t.isEmpty()) sb.append("\nTech: ").append(join(tr.t));
            body.setText(sb.toString());
        } else if ("TAKEOVER".equals(mode)) {
            ScanEngine.TkR tk = ScanEngine.takeover(host, to);
            body.setText(tk.v ? "[!!] " + tk.dt : "[OK] No takeover risk");
            body.setTextColor(tk.v ? C_DANGER : C_OK);
        } else if ("WAYBACK".equals(mode)) {
            int max = 5000;
            if (sni != null && sni.length() > 0) try { max = Integer.parseInt(sni); } catch (Exception x) {}
            ScanEngine.WbR wb = ScanEngine.wayback(host, max);
            StringBuilder sb = new StringBuilder();
            sb.append("Total: ").append(wb.t).append("  Juicy: ").append(wb.in);
            for (int i = 0; i < Math.min(wb.iu.size(), 20); i++) sb.append("\n  ").append(wb.iu.get(i));
            body.setText(sb.toString());
        } else if ("SUBDOMAIN".equals(mode)) {
            ScanEngine.SubR sub = ScanEngine.deepEnum(host, new ScanEngine.HCB() {
                @Override public void st(String m) {}
                @Override public void pr(String h, int d, int t) {}
            });
            body.setText("Found: " + sub.tu + " subdomains\ncrt.sh: " + sub.crt + "  certspotter: " + sub.cs + "\nalienvault: " + sub.av + "  hackertarget: " + sub.ht);
        } else if ("REVIP".equals(mode)) {
            List<String> rev = ScanEngine.revIp(host);
            body.setText("Found: " + rev.size() + " domains\n" + join(rev));
        }
        card.addView(body);
        return card;
    }

    private void beginRun(String status) {
        running = true;
        cancelled = false;
        startTime = System.currentTimeMillis();
        setStatus(status, C_INFO);
        runOnUiThread(new Runnable() {
            @Override public void run() {
                tvProgress.setVisibility(View.VISIBLE);
                tvEta.setVisibility(View.VISIBLE);
                progressHorizontal.setProgress(0);
                findViewById(R.id.progressBar).setVisibility(View.VISIBLE);
            }
        });
    }

    private void finishRun(final String target, final int scanned, final int found) {
        running = false;
        final long elapsed = System.currentTimeMillis() - startTime;
        runOnUiThread(new Runnable() {
            @Override public void run() {
                tvProgress.setVisibility(View.GONE);
                tvEta.setVisibility(View.GONE);
                findViewById(R.id.progressBar).setVisibility(View.GONE);
                progressHorizontal.setProgress(100);
                setStatus("Done: " + scanned + " scanned, " + found + " hits  (" + elapsed / 1000 + "s)", found > 0 ? C_OK : C_MUTED);
            }
        });
        if (scanned > 0) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < sessionHits.size() && i < 5; i++) sb.append(sessionHits.get(i)).append("\n");
            HistoryStore.add(this, mode, target, scanned, found, sb.toString());
        }
    }

    private void updateProg(final int done, final int found, final String current) {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                tvProgress.setText(done + " done");
                if (totalCount > 0) {
                    int pct = (done * 100) / totalCount;
                    progressHorizontal.setProgress(pct);
                    long elapsed = System.currentTimeMillis() - startTime;
                    long eta = (long) ((double) elapsed / done * (totalCount - done));
                    tvEta.setText(formatMs(eta) + " left");
                }
                setStatus("Scanning " + current + " ...  (" + found + " hits)", C_INFO);
            }
        });
    }

    private void setStatus(final String msg, final int color) {
        runOnUiThread(new Runnable() {
            @Override public void run() { tvStatus.setText(msg); tvStatus.setTextColor(color); }
        });
    }

    private void clearRes() {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                resultsContainer.removeAllViews();
                liveContainer.removeAllViews();
                hitsContainer.removeAllViews();
                sessionHits.clear();
                sessionLines.clear();
                progressHorizontal.setProgress(0);
            }
        });
    }

    private void toast(final String msg) {
        runOnUiThread(new Runnable() {
            @Override public void run() { Toast.makeText(ScannerActivity.this, msg, Toast.LENGTH_SHORT).show(); }
        });
    }

    private String san(String s) {
        if (s == null) return "";
        s = s.trim();
        if (s.startsWith("http://")) s = s.substring(7);
        if (s.startsWith("https://")) s = s.substring(8);
        if (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    private String getSni() {
        String s = etSni.getText().toString().trim();
        return s;
    }

    private String yn(boolean b) { return b ? "YES" : "NO"; }

    private String join(List<String> l) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l.size(); i++) { if (i > 0) sb.append(", "); sb.append(l.get(i)); }
        return sb.toString();
    }

    private String joinI(List<Integer> l) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l.size(); i++) { if (i > 0) sb.append(", "); sb.append(l.get(i)); }
        return sb.toString();
    }

    private String shortM(Exception e) {
        String m = e.getMessage();
        return m != null && m.length() > 60 ? m.substring(0, 60) + "..." : m;
    }

    private String formatMs(long ms) {
        if (ms < 1000) return ms + "ms";
        if (ms < 60000) return (ms / 1000) + "s";
        return (ms / 60000) + "m" + ((ms % 60000) / 1000) + "s";
    }

    private void saveFile(String name, String content) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Downloads.DISPLAY_NAME, name);
                cv.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
                cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/BugScanner");
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                if (uri != null) {
                    OutputStream os = getContentResolver().openOutputStream(uri);
                    if (os != null) { os.write(content.getBytes("UTF-8")); os.close(); }
                }
            } else {
                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "BugScanner");
                if (!dir.exists()) dir.mkdirs();
                File f = new File(dir, name);
                FileOutputStream fos = new FileOutputStream(f);
                fos.write(content.getBytes("UTF-8"));
                fos.close();
            }
            toast("Saved: " + name);
        } catch (Exception e) { toast("Save failed: " + e.getMessage()); }
    }
}
'
