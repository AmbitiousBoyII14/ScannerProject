package myscanne.com;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Customisation screen with three tabs: "UI", "Theme", and "Scan".
 * The Scan tab exposes timeout (seconds), ports (comma list), HTTP method, and thread count.
 * All choices persist immediately via Prefs.
 * Java 7 – anonymous inner classes only.
 */
public class SettingsActivity extends Activity {

    private LinearLayout root, header, container;
    private Button tabUi, tabTheme, tabScan;
    private TextView tvTitle, btnBack;

    private int currentTab = 0; // 0=UI  1=Theme  2=Scan

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        root      = (LinearLayout) findViewById(R.id.settingsRoot);
        header    = (LinearLayout) findViewById(R.id.settingsHeader);
        container = (LinearLayout) findViewById(R.id.settingsContainer);
        tabUi     = (Button)   findViewById(R.id.tabUi);
        tabTheme  = (Button)   findViewById(R.id.tabTheme);
        tabScan   = (Button)   findViewById(R.id.tabScan);
        tvTitle   = (TextView) findViewById(R.id.tvTitle);
        btnBack   = (TextView) findViewById(R.id.btnBack);

        btnBack.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { finish(); }
			});
        tabUi.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { currentTab = 0; render(); }
			});
        tabTheme.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { currentTab = 1; render(); }
			});
        tabScan.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { currentTab = 2; render(); }
			});

        render();
    }

    private void render() {
        root.setBackgroundColor(Prefs.bg(this));
        header.setBackgroundColor(Prefs.card(this));
        tvTitle.setTextColor(Prefs.text(this));
        btnBack.setTextColor(Prefs.accent(this));

        styleTab(tabUi,    currentTab == 0);
        styleTab(tabTheme, currentTab == 1);
        styleTab(tabScan,  currentTab == 2);

        container.removeAllViews();
        if      (currentTab == 0) buildUiTab();
        else if (currentTab == 1) buildThemeTab();
        else                      buildScanTab();
    }

    private void styleTab(Button b, boolean active) {
        if (active) {
            b.setBackgroundDrawable(Theme.filled(this, Prefs.accent(this)));
            b.setTextColor(Theme.onColor(Prefs.accent(this)));
        } else {
            b.setBackgroundDrawable(Theme.outline(this, Prefs.stroke(this)));
            b.setTextColor(Prefs.muted(this));
        }
    }

    // ================= UI TAB =================
    private void buildUiTab() {
        addSectionLabel("APPEARANCE");

        addSegment("Font", new String[]{"Monospace", "Sans"}, Prefs.font(this),
            new OnPick() { @Override public void pick(int i) { Prefs.setFont(SettingsActivity.this, i); render(); } });

        addSegment("Density", new String[]{"Comfortable", "Compact"}, Prefs.density(this),
            new OnPick() { @Override public void pick(int i) { Prefs.setDensity(SettingsActivity.this, i); render(); } });

        addSegment("Menu Layout", new String[]{"List", "Grid"}, Prefs.menu(this),
            new OnPick() { @Override public void pick(int i) { Prefs.setMenu(SettingsActivity.this, i); render(); } });

        addHint("Changes apply across the app instantly. Open a scanner or the home screen to see them.");
    }

    // ================= THEME TAB =================
    private void buildThemeTab() {
        addSectionLabel("COLOR THEME");

        int selected = Prefs.theme(this);
        for (int i = 0; i < Prefs.TN.length; i++) {
            final int index = i;
            int[] pal = Prefs.palette(i);
            boolean active = i == selected;

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundDrawable(makeCardWith(pal[1], active ? pal[3] : pal[2], active ? 2 : 1));
            row.setPadding(dp(14), dp(14), dp(14), dp(14));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(10);
            row.setLayoutParams(lp);
            row.setClickable(true);

            // swatch cluster (accent, info, bg)
            row.addView(swatch(pal[3]));
            row.addView(swatch(pal[4]));
            row.addView(swatch(pal[0]));

            TextView name = new TextView(this);
            name.setText(Prefs.TN[i]);
            name.setTextColor(pal[5]);
            name.setTextSize(16f);
            name.setTypeface(null, Typeface.BOLD);
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(0,
																		  LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            nlp.leftMargin = dp(12);
            name.setLayoutParams(nlp);
            row.addView(name);

            TextView check = new TextView(this);
            check.setText(active ? "✓" : "");
            check.setTextColor(pal[3]);
            check.setTextSize(20f);
            check.setTypeface(null, Typeface.BOLD);
            row.addView(check);

            row.setOnClickListener(new View.OnClickListener() {
					@Override public void onClick(View v) {
						Prefs.setTheme(SettingsActivity.this, index);
						render();
					}
				});
            container.addView(row);
        }

        addHint("Themes recolor backgrounds, cards, accents and text everywhere in the app.");
    }

    // ================= SCAN TAB =================
    private void buildScanTab() {
        addSectionLabel("SCAN DEFAULTS");

        // Timeout – now in seconds (converted to ms internally)
        double currentTimeoutSec = Prefs.getTimeoutMs(this) / 1000.0;
        // Show a nice decimal string (e.g. "2.5")
        final EditText etTimeout = addEditRow(
			"Timeout (s)",                                      // label changed
			String.valueOf(currentTimeoutSec).replaceAll("\\.0$", ""), // avoid ".0" for whole numbers
			"0.2 - 60  (default 1.0)",                         // hint in seconds
			InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);

        // Ports
        final EditText etPorts = addEditRow(
			"Ports",
			Prefs.getPorts(this),
			"comma-separated, e.g. 443,80,8080",
			InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);

        // HTTP Method  (segment saves immediately; no Save needed)
        addSegment("HTTP Method", new String[]{"GET", "HEAD"}, Prefs.getMethod(this),
			new OnPick() {
				@Override public void pick(int i) {
					Prefs.setMethod(SettingsActivity.this, i);
					// re-render to reflect new active state without losing field values
					currentTab = 2; render();
				}
			});

        // Threads
        final EditText etThreads = addEditRow(
			"Threads (worker pool)",
			String.valueOf(Prefs.getThreads(this)),
			"1 - 500  (default 50)",
			InputType.TYPE_CLASS_NUMBER);

        // Save button (applies timeout, ports, threads)
        Button btnSave = new Button(this);
        btnSave.setText("SAVE SCAN SETTINGS");
        btnSave.setAllCaps(false);
        btnSave.setTextSize(14f);
        btnSave.setTypeface(null, Typeface.BOLD);
        btnSave.setBackgroundDrawable(Theme.filled(this, Prefs.accent(this)));
        btnSave.setTextColor(Theme.onColor(Prefs.accent(this)));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
			LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        slp.topMargin = dp(20);
        btnSave.setLayoutParams(slp);

        // capture finals for use inside listener
        final EditText fTimeout = etTimeout;
        final EditText fPorts   = etPorts;
        final EditText fThreads = etThreads;

        btnSave.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) {
					boolean ok = true;

					// --- timeout (seconds → ms) ---
					try {
						double sec = Double.parseDouble(fTimeout.getText().toString().trim());
						int ms = (int) Math.round(sec * 1000);
						if (ms < 200 || ms > 60000) throw new IllegalArgumentException();
						Prefs.setTimeoutMs(SettingsActivity.this, ms);
					} catch (Exception e) {
						fTimeout.setError("Enter a value between 0.2 and 60 seconds");
						ok = false;
					}

					// --- ports ---
					String portStr = fPorts.getText().toString().trim();
					if (portStr.length() == 0) portStr = "443,80";
					boolean portsOk = true;
					for (String p : portStr.split(",")) {
						try {
							int pn = Integer.parseInt(p.trim());
							if (pn < 1 || pn > 65535) { portsOk = false; break; }
						} catch (Exception e) { portsOk = false; break; }
					}
					if (!portsOk) {
						fPorts.setError("Use comma-separated numbers 1–65535");
						ok = false;
					} else {
						Prefs.setPorts(SettingsActivity.this, portStr);
					}

					// --- threads ---
					try {
						int t = Integer.parseInt(fThreads.getText().toString().trim());
						if (t < 1 || t > 500) throw new IllegalArgumentException();
						Prefs.setThreads(SettingsActivity.this, t);
					} catch (Exception e) {
						fThreads.setError("Enter a value between 1 and 500");
						ok = false;
					}

					if (ok) {
						Toast.makeText(SettingsActivity.this,
									   "Scan settings saved", Toast.LENGTH_SHORT).show();
					}
				}
			});
        container.addView(btnSave);

        // --- Copyright line (centered, with border) ---
        addCopyright("Syamthanda : Telegram @Treacky_1");
    }

    // ================= BUILDING BLOCKS =================
    private interface OnPick { void pick(int index); }

    /**
     * Adds a labeled card with a single EditText and returns it so the
     * caller can read its value on Save.
     */
    private EditText addEditRow(String label, String value, String hint, int inputType) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setBackgroundDrawable(Theme.card(this));
        wrap.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
			LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        wlp.topMargin = dp(10);
        wrap.setLayoutParams(wlp);

        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextColor(Prefs.text(this));
        tvLabel.setTextSize(14f);
        tvLabel.setTypeface(null, Typeface.BOLD);
        wrap.addView(tvLabel);

        EditText et = new EditText(this);
        et.setText(value);
        et.setHint(hint);
        et.setInputType(inputType);
        et.setTextColor(Prefs.text(this));
        et.setHintTextColor(Prefs.muted(this));
        et.setBackgroundDrawable(Theme.input(this));
        et.setTextSize(14f);
        et.setPadding(dp(10), dp(8), dp(10), dp(8));
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
			LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        elp.topMargin = dp(6);
        et.setLayoutParams(elp);
        wrap.addView(et);
        container.addView(wrap);
        return et;
    }

    private void addSectionLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Prefs.muted(this));
        tv.setTextSize(12f);
        tv.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
			LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        lp.bottomMargin = dp(2);
        tv.setLayoutParams(lp);
        container.addView(tv);
    }

    private void addSegment(String label, String[] options, int selected, final OnPick cb) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setBackgroundDrawable(Theme.card(this));
        wrap.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
			LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        wlp.topMargin = dp(10);
        wrap.setLayoutParams(wlp);

        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextColor(Prefs.text(this));
        tvLabel.setTextSize(14f);
        tvLabel.setTypeface(null, Typeface.BOLD);
        wrap.addView(tvLabel);

        LinearLayout seg = new LinearLayout(this);
        seg.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
			LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        slp.topMargin = dp(8);
        seg.setLayoutParams(slp);

        for (int i = 0; i < options.length; i++) {
            final int idx = i;
            boolean active = i == selected;
            Button b = new Button(this);
            b.setText(options[i]);
            b.setAllCaps(false);
            b.setTextSize(13f);
            if (active) {
                b.setBackgroundDrawable(Theme.filled(this, Prefs.accent(this)));
                b.setTextColor(Theme.onColor(Prefs.accent(this)));
            } else {
                b.setBackgroundDrawable(Theme.outline(this, Prefs.stroke(this)));
                b.setTextColor(Prefs.muted(this));
            }
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0,
																		  LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) blp.leftMargin = dp(6);
            b.setLayoutParams(blp);
            seg.addView(b);
            b.setOnClickListener(new View.OnClickListener() {
					@Override public void onClick(View v) {
						cb.pick(idx);
					}
				});
        }
        wrap.addView(seg);
        container.addView(wrap);
    }

    private void addHint(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Prefs.muted(this));
        tv.setTextSize(12f);
        tv.setPadding(dp(4), dp(12), dp(4), dp(4));
        tv.setLineSpacing(dp(4), 1f);
        container.addView(tv);
    }

    /**
     * Adds a centered copyright line with a thin border matching the muted text color.
     */
    private void addCopyright(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Prefs.muted(this));
        tv.setTextSize(12f);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(12), dp(8), dp(12), dp(8));

        // Border with same color as text
        GradientDrawable border = new GradientDrawable();
        border.setCornerRadius(dp(8));
        border.setStroke(dp(1), Prefs.muted(this));
        border.setColor(android.graphics.Color.TRANSPARENT);
        tv.setBackgroundDrawable(border);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
			LinearLayout.LayoutParams.MATCH_PARENT,
			LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(16);
        lp.bottomMargin = dp(8);
        lp.leftMargin = dp(16);
        lp.rightMargin = dp(16);
        tv.setLayoutParams(lp);

        container.addView(tv);
    }

    /**
     * Helper to create a simple card background (rounded rect) with a given fill and stroke.
     * In production this would use a pre-made drawable; here a minimal placeholder.
     */
    private GradientDrawable makeCardWith(int bg, int stroke, int strokeWidth) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(bg);
        gd.setStroke(dp(strokeWidth), stroke);
        gd.setCornerRadius(dp(8));
        return gd;
    }

    private View swatch(int color) {
        View v = new View(this);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        gd.setShape(GradientDrawable.OVAL);
        v.setBackgroundDrawable(gd);
        int size = dp(24);
        v.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        return v;
    }

    private int dp(int v) {
        return (int)(v * getResources().getDisplayMetrics().density + 0.5f);
    }
}

