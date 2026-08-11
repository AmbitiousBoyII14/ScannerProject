package myscanne.com;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Dashboard with Offline / Online / Utilities sections.
 * Java 7.
 */
public class MainActivity extends Activity {

    private TextView tvTotalScans, tvTotalHits;
    private LinearLayout menuContainer;

    private static final String[][] MENU = {
        // -- OFFLINE (no API keys needed) --
        {"BugHost Probe",    "Score 0-100: WAF+tech+takeover+WS",       "BUGHOST","offline"},
        {"DPI Scanner",      "Fragmented WS DPI bypass (101 detect)",    "DPI","offline"},{"WS Tester",        "WebSocket upgrade test (show status)",       "WS","offline"},
        {"SNI Scanner",      "Direct SSL to Google IP + SNI host",       "SNI","offline"},
        {"TLS Scanner",      "HTTP status + server banner over TLS",     "TLS","offline"},
        {"Proxy Scanner",    "Check host as proxy (custom SNI)",         "PROXY","offline"},
        {"Port Checker",     "Scan custom TCP ports",                    "PORT","offline"},
        {"Tech Fingerprint", "Server, framework, CMS, CDN detection",    "TECH","offline"},
        {"DNS Lookup",       "A, AAAA, CNAME, MX, NS, TXT records",      "DNS","offline"},
        {"Security Headers", "Score HTTP security response headers",      "HEADERS","offline"},
        {"HTTP Version",     "Probe HTTP/1.1, HTTP/2, HTTP/3 support",    "HTTP_VER","offline"},
        {"CDN Checker",      "Detect CDN provider from headers",          "CDN","offline"},
        // -- ONLINE (needs internet / external APIs) --
        {"Deep Enumeration", "4-source: crt.sh+certspotter+alienvault+HT","DEEPENUM","online"},
        {"Takeover Check",   "Deep enum + CNAME takeover detection",     "TAKEOVER","online"},
        {"Endpoint Fuzzer",  "Fuzz 36 paths (.git,.env,admin,etc)",      "ENDPOINT","online"},
        {"Wayback URLs",     "Fetch historical URLs + jucy filter",       "WAYBACK","online"},
        {"Subdomain Finder", "crt.sh CT logs + brute-force",              "SUBDOMAIN","online"},
        {"Hosts Finder",     "Find domains by TLD via crt.sh",            "HOSTS","online"},
        {"Reverse IP",       "PTR + hackertarget reverse lookup",         "REVIP","online"},
        // -- UTILITIES --
        {"TXT Splitter",     "Split lists into 25k-line parts",           "SPLIT","util"},
    };

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_main);

        tvTotalScans=(TextView)findViewById(R.id.tvTotalScans);
        tvTotalHits=(TextView)findViewById(R.id.tvTotalHits);
        menuContainer=(LinearLayout)findViewById(R.id.menuContainer);

        Button btnHistory=(Button)findViewById(R.id.btnHistory);
        btnHistory.setOnClickListener(new View.OnClickListener(){
            @Override public void onClick(View v){
                startActivity(new Intent(MainActivity.this,HistoryActivity.class)); }});

        Button btnCustomize=(Button)findViewById(R.id.btnCustomize);
        btnCustomize.setOnClickListener(new View.OnClickListener(){
            @Override public void onClick(View v){
                startActivity(new Intent(MainActivity.this,SettingsActivity.class)); }});
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
        View root=findViewById(R.id.mainRoot);
        if (root!=null) root.setBackgroundColor(Prefs.bg(this));

        TextView tvHeader=(TextView)findViewById(R.id.tvHeader);
        TextView tvSub=(TextView)findViewById(R.id.tvSub);
        View statScans=findViewById(R.id.statScans), statHits=findViewById(R.id.statHits);
        if (tvHeader!=null) tvHeader.setTextColor(Prefs.accent(this));
        if (tvSub!=null) tvSub.setTextColor(Prefs.muted(this));
        if (statScans!=null) statScans.setBackgroundDrawable(Theme.card(this));
        if (statHits!=null) statHits.setBackgroundDrawable(Theme.card(this));
        tvTotalScans.setTextColor(Prefs.info(this));
        tvTotalHits.setTextColor(0xFF22C55E);

        Button btnHistory=(Button)findViewById(R.id.btnHistory);
        Button btnCustomize=(Button)findViewById(R.id.btnCustomize);
        btnHistory.setBackgroundDrawable(Theme.outline(this,Prefs.info(this)));
        btnHistory.setTextColor(Prefs.info(this));
        btnCustomize.setBackgroundDrawable(Theme.filled(this,Prefs.accent(this)));
        btnCustomize.setTextColor(Theme.onColor(Prefs.accent(this)));
    }

    private void buildMenu() {
        menuContainer.removeAllViews();
        if (Prefs.isGrid(this)) buildGrid();
        else buildList();
    }

    private int dp(int v){ return (int)(v*getResources().getDisplayMetrics().density+0.5f); }

    // ==== LIST ====
    private void buildList() {
        int padV=Prefs.isCompact(this)?dp(8):dp(12);
        String curSection="";

        for (int i=0; i<MENU.length; i++) {
            String section;
            if ("offline".equals(MENU[i][3])) section="OFFLINE SCANNERS";
            else if ("online".equals(MENU[i][3])) section="ONLINE (API-based)";
            else section="UTILITIES";

            if (!section.equals(curSection)) {
                curSection=section;
                TextView tv=new TextView(this);
                tv.setText(section); tv.setTextColor(Prefs.accent(this));
                tv.setTextSize(10f); tv.setTypeface(null,Typeface.BOLD);
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.topMargin=dp(14); lp.bottomMargin=dp(2); tv.setLayoutParams(lp);
                menuContainer.addView(tv);
            }

            final String title=MENU[i][0], subtitle=MENU[i][1], mode=MENU[i][2];

            LinearLayout card=new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setBackgroundDrawable(Theme.card(this));
            card.setPadding(dp(14),padV,dp(14),padV);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin=dp(5); card.setLayoutParams(lp);
            card.setClickable(true);

            LinearLayout texts=new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT,1f));

            TextView tvT=new TextView(this);
            tvT.setText(title); tvT.setTextColor(Prefs.text(this));
            tvT.setTextSize(14f); tvT.setTypeface(null,Typeface.BOLD);
            texts.addView(tvT);

            if (!Prefs.isCompact(this)) {
                TextView tvS=new TextView(this);
                tvS.setText(subtitle); tvS.setTextColor(Prefs.muted(this));
                tvS.setTextSize(11f); texts.addView(tvS);
            }
            card.addView(texts);

            TextView arrow=new TextView(this);
            arrow.setText(">"); arrow.setTextColor(Prefs.info(this));
            arrow.setTextSize(20f); card.addView(arrow);

            card.setOnClickListener(new View.OnClickListener(){
                @Override public void onClick(View v){ open(mode,title); }});
            menuContainer.addView(card);
        }
    }

    // ==== GRID ====
    private void buildGrid() {
        int cols=2; String curSection=""; LinearLayout row=null; int ri=0;

        for (int i=0; i<MENU.length; i++) {
            String section;
            if ("offline".equals(MENU[i][3])) section="OFFLINE";
            else if ("online".equals(MENU[i][3])) section="ONLINE";
            else section="UTIL";

            if (!section.equals(curSection)) {
                curSection=section; ri=0;
                TextView tv=new TextView(this);
                tv.setText(section); tv.setTextColor(Prefs.accent(this));
                tv.setTextSize(10f); tv.setTypeface(null,Typeface.BOLD);
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.topMargin=dp(14); tv.setLayoutParams(lp);
                menuContainer.addView(tv);
            }

            if (ri%cols==0) {
                row=new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rlp=new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);
                rlp.topMargin=dp(5); row.setLayoutParams(rlp);
                menuContainer.addView(row);
            }

            final String title=MENU[i][0], mode=MENU[i][2];
            LinearLayout tile=new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER_VERTICAL);
            tile.setBackgroundDrawable(Theme.card(this));
            tile.setPadding(dp(12),dp(14),dp(12),dp(14));
            LinearLayout.LayoutParams tlp=new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT,1f);
            if (ri%cols==0) tlp.rightMargin=dp(4); else tlp.leftMargin=dp(4);
            tile.setLayoutParams(tlp); tile.setClickable(true);

            TextView tvT=new TextView(this);
            tvT.setText(title); tvT.setTextColor(Prefs.text(this));
            tvT.setTextSize(12f); tvT.setTypeface(null,Typeface.BOLD);
            tile.addView(tvT);

            tile.setOnClickListener(new View.OnClickListener(){
                @Override public void onClick(View v){ open(mode,title); }});
            row.addView(tile);
            ri++;
        }
    }

    private void open(String mode, String title) {
        Intent it=new Intent(MainActivity.this,ScannerActivity.class);
        it.putExtra("mode",mode); it.putExtra("title",title);
        startActivity(it);
    }
}
