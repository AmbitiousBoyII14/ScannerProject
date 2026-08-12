package myscanne.com;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Locale;

public class MainActivity extends Activity {

    private TextView tvTotalScans, tvTotalHits, tvHeader, tvSub;
    private LinearLayout menuContainer, statsRow;
    private ScrollView scrollRoot;

    private static final String[][] MENU = {
        {"BugHost Probe","Score 0-100: WAF+tech+takeover+WS+endpoints+ports+cert","BUGHOST","offline"},
        {"DPI Scanner","Fragmented WS DPI bypass detection","DPI","offline"},
        {"WS Tester","WebSocket upgrade test","WS","offline"},
        {"SNI Scanner","Direct SSL to Google IP + SNI host","SNI","offline"},
        {"TLS Scanner","HTTP status + server banner over TLS","TLS","offline"},
        {"Proxy Scanner","Check host as proxy (custom SNI)","PROXY","offline"},
        {"Port Checker","Scan TCP ports with service detection","PORT","offline"},
        {"Tech Fingerprint","Server, framework, CMS, CDN detection","TECH","offline"},
        {"DNS Lookup","A, AAAA, CNAME, MX, NS, TXT records","DNS","offline"},
        {"Security Headers","Score HTTP security response headers","HEADERS","offline"},
        {"HTTP Version","Probe HTTP/1.1, HTTP/2, HTTP/3 support","HTTP_VER","offline"},
        {"CDN Checker","Detect CDN provider from headers","CDN","offline"},
        {"Ping Test","TCP ping with latency stats","PING","offline"},
        {"SSL Certificate","View cert info, expiry, SANs","CERT","offline"},
        {"Redirect Tracer","Trace HTTP redirect chain","REDIRECT","offline"},
        {"Deep Enumeration","4-source: crt.sh+certspotter+alienvault+HT","DEEPENUM","online"},
        {"Takeover Check","Deep enum + CNAME takeover detection","TAKEOVER","online"},
        {"Endpoint Fuzzer","Fuzz "+ScanEngine.COMMON_PATHS.length+" sensitive paths","ENDPOINT","online"},
        {"Wayback URLs","Fetch historical URLs + juicy filter","WAYBACK","online"},
        {"Subdomain Finder","crt.sh CT logs + brute-force","SUBDOMAIN","online"},
        {"Hosts Finder","Find domains by TLD via crt.sh","HOSTS","online"},
        {"Reverse IP","PTR + hackertarget reverse lookup","REVIP","online"},
        {"IP Geolocation","Country, city, ISP, ASN lookup","GEO","online"},
        {"Whois Lookup","RDAP domain registration data","WHOIS","online"},
        {"TXT Splitter","Split lists into 25k-line parts","SPLIT","util"},
    };

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_main);

        scrollRoot = (ScrollView) findViewById(R.id.mainRoot);
        tvHeader = (TextView) findViewById(R.id.tvHeader);
        tvSub = (TextView) findViewById(R.id.tvSub);
        tvTotalScans = (TextView) findViewById(R.id.tvTotalScans);
        tvTotalHits = (TextView) findViewById(R.id.tvTotalHits);
        menuContainer = (LinearLayout) findViewById(R.id.menuContainer);
        statsRow = (LinearLayout) findViewById(R.id.statsRow);

        Button btnHistory = (Button) findViewById(R.id.btnHistory);
        btnHistory.setOnClickListener(new View.OnClickListener(){
            @Override public void onClick(View v){
                startActivity(new Intent(MainActivity.this, HistoryActivity.class));
            }
        });

        Button btnCustomize = (Button) findViewById(R.id.btnCustomize);
        btnCustomize.setOnClickListener(new View.OnClickListener(){
            @Override public void onClick(View v){
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyTheme();
        tvTotalScans.setText(String.valueOf(HistoryStore.totalScans(this)));
        tvTotalHits.setText(String.valueOf(HistoryStore.totalHits(this)));
        buildMenu();
    }

    private void applyTheme() {
        int bg = Prefs.bg(this);
        int card = Prefs.card(this);
        int accent = Prefs.accent(this);
        int text = Prefs.text(this);
        int muted = Prefs.muted(this);
        int info = Prefs.info(this);

        scrollRoot.setBackgroundColor(bg);
        tvHeader.setTextColor(accent);
        tvSub.setTextColor(muted);

        statsRow.setBackgroundDrawable(makeCard(card, Prefs.stroke(this)));
        tvTotalScans.setTextColor(info);
        tvTotalHits.setTextColor(0xFF22C55E);

        Button btnHistory = (Button) findViewById(R.id.btnHistory);
        Button btnCustomize = (Button) findViewById(R.id.btnCustomize);
        btnHistory.setBackgroundDrawable(Theme.outline(this, info));
        btnHistory.setTextColor(info);
        btnCustomize.setBackgroundDrawable(Theme.filled(this, accent));
        btnCustomize.setTextColor(Theme.onColor(accent));
    }

    private void buildMenu() {
        menuContainer.removeAllViews();
        if (Prefs.isGrid(this)) buildGrid();
        else buildList();
    }

    private int dp(int v){ return (int)(v*getResources().getDisplayMetrics().density+0.5f); }

    private void buildList() {
        int padV = Prefs.isCompact(this) ? dp(8) : dp(12);
        String curSection = "";

        for (int i = 0; i < MENU.length; i++) {
            String section;
            if ("offline".equals(MENU[i][3])) section = "OFFLINE SCANNERS";
            else if ("online".equals(MENU[i][3])) section = "ONLINE TOOLS";
            else section = "UTILITIES";

            if (!section.equals(curSection)) {
                curSection = section;
                TextView tv = new TextView(this);
                tv.setText(section);
                tv.setTextColor(Prefs.accent(this));
                tv.setTextSize(11f);
                tv.setTypeface(null, Typeface.BOLD);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.topMargin = dp(18);
                lp.bottomMargin = dp(6);
                tv.setLayoutParams(lp);
                menuContainer.addView(tv);

                View div = new View(this);
                div.setBackgroundColor(Prefs.stroke(this));
                LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
                dlp.bottomMargin = dp(8);
                div.setLayoutParams(dlp);
                menuContainer.addView(div);
            }

            final String title = MENU[i][0];
            final String desc = MENU[i][1];
            final String mode = MENU[i][2];
            final String type = MENU[i][3];

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundDrawable(makeCard(Prefs.card(this), Prefs.stroke(this)));
            card.setPadding(dp(14), padV, dp(14), padV);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            clp.bottomMargin = dp(8);
            card.setLayoutParams(clp);
            card.setClickable(true);

            LinearLayout top = new LinearLayout(this);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);

            TextView badge = new TextView(this);
            badge.setText(getIconForMode(mode));
            badge.setTextSize(18f);
            badge.setGravity(Gravity.CENTER);
            int badgeSize = dp(36);
            badge.setLayoutParams(new LinearLayout.LayoutParams(badgeSize, badgeSize));
            GradientDrawable bgd = new GradientDrawable();
            bgd.setColor(getBadgeColor(mode, type));
            bgd.setShape(GradientDrawable.OVAL);
            badge.setBackgroundDrawable(bgd);
            badge.setTextColor(Color.WHITE);
            top.addView(badge);

            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tlp.leftMargin = dp(12);
            texts.setLayoutParams(tlp);

            TextView tvTitle = new TextView(this);
            tvTitle.setText(title);
            tvTitle.setTextColor(Prefs.text(this));
            tvTitle.setTextSize(14f);
            tvTitle.setTypeface(null, Typeface.BOLD);
            texts.addView(tvTitle);

            TextView tvDesc = new TextView(this);
            tvDesc.setText(desc);
            tvDesc.setTextColor(Prefs.muted(this));
            tvDesc.setTextSize(11f);
            tvDesc.setPadding(0, dp(2), 0, 0);
            texts.addView(tvDesc);

            top.addView(texts);

            TextView pill = new TextView(this);
            pill.setText(type.toUpperCase(Locale.US));
            pill.setTextSize(9f);
            pill.setTypeface(null, Typeface.BOLD);
            pill.setTextColor(Prefs.muted(this));
            pill.setPadding(dp(6), dp(2), dp(6), dp(2));
            GradientDrawable pbg = new GradientDrawable();
            pbg.setColor(Prefs.stroke(this));
            pbg.setCornerRadius(dp(4));
            pill.setBackgroundDrawable(pbg);
            top.addView(pill);

            card.addView(top);

            card.setOnClickListener(new View.OnClickListener(){
                @Override public void onClick(View v){
                    Intent in = new Intent(MainActivity.this, ScannerActivity.class);
                    in.putExtra("mode", mode);
                    in.putExtra("title", title);
                    startActivity(in);
                }
            });

            menuContainer.addView(card);
        }
    }

    private void buildGrid() {
        int cols = 2;
        LinearLayout row = null;
        for (int i = 0; i < MENU.length; i++) {
            if (i % cols == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                menuContainer.addView(row);
            }
            final String title = MENU[i][0];
            final String mode = MENU[i][2];
            final String type = MENU[i][3];

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER);
            card.setBackgroundDrawable(makeCard(Prefs.card(this), Prefs.stroke(this)));
            card.setPadding(dp(10), dp(12), dp(10), dp(12));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            clp.bottomMargin = dp(8);
            if (i % cols == 0) clp.rightMargin = dp(4);
            else clp.leftMargin = dp(4);
            card.setLayoutParams(clp);
            card.setClickable(true);

            TextView badge = new TextView(this);
            badge.setText(getIconForMode(mode));
            badge.setTextSize(22f);
            badge.setGravity(Gravity.CENTER);
            int bs = dp(40);
            badge.setLayoutParams(new LinearLayout.LayoutParams(bs, bs));
            GradientDrawable bgd = new GradientDrawable();
            bgd.setColor(getBadgeColor(mode, type));
            bgd.setShape(GradientDrawable.OVAL);
            badge.setBackgroundDrawable(bgd);
            badge.setTextColor(Color.WHITE);
            card.addView(badge);

            TextView tvTitle = new TextView(this);
            tvTitle.setText(title);
            tvTitle.setTextColor(Prefs.text(this));
            tvTitle.setTextSize(12f);
            tvTitle.setTypeface(null, Typeface.BOLD);
            tvTitle.setGravity(Gravity.CENTER);
            tvTitle.setPadding(0, dp(6), 0, 0);
            card.addView(tvTitle);

            card.setOnClickListener(new View.OnClickListener(){
                @Override public void onClick(View v){
                    Intent in = new Intent(MainActivity.this, ScannerActivity.class);
                    in.putExtra("mode", mode);
                    in.putExtra("title", title);
                    startActivity(in);
                }
            });

            row.addView(card);
        }
    }

    private GradientDrawable makeCard(int bg, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(bg);
        g.setCornerRadius(dp(12));
        g.setStroke(dp(1), stroke);
        return g;
    }

    private String getIconForMode(String mode) {
        if ("BUGHOST".equals(mode)) return "B";
        if ("DPI".equals(mode)) return "D";
        if ("WS".equals(mode)) return "W";
        if ("SNI".equals(mode)) return "S";
        if ("TLS".equals(mode)) return "T";
        if ("PROXY".equals(mode)) return "P";
        if ("PORT".equals(mode)) return "#";
        if ("TECH".equals(mode)) return "F";
        if ("DNS".equals(mode)) return "N";
        if ("HEADERS".equals(mode)) return "H";
        if ("HTTP_VER".equals(mode)) return "V";
        if ("CDN".equals(mode)) return "C";
        if ("PING".equals(mode)) return "@";
        if ("CERT".equals(mode)) return "K";
        if ("REDIRECT".equals(mode)) return "R";
        if ("DEEPENUM".equals(mode)) return "E";
        if ("TAKEOVER".equals(mode)) return "!";
        if ("ENDPOINT".equals(mode)) return "?";
        if ("WAYBACK".equals(mode)) return "A";
        if ("SUBDOMAIN".equals(mode)) return "U";
        if ("HOSTS".equals(mode)) return "M";
        if ("REVIP".equals(mode)) return "I";
        if ("GEO".equals(mode)) return "G";
        if ("WHOIS".equals(mode)) return "O";
        if ("SPLIT".equals(mode)) return "/";
        return "*";
    }

    private int getBadgeColor(String mode, String type) {
        if ("offline".equals(type)) return Prefs.info(this);
        if ("online".equals(type)) return Prefs.accent(this);
        return Prefs.muted(this);
    }
}
