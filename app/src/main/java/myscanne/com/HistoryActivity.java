package myscanne.com;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * Shows every saved scan session. Tap a row to view the full result log.
 * Fully theme-aware – uses Prefs colors and Theme fonts.
 * Java 7 – no lambdas.
 */
public class HistoryActivity extends Activity {

    private LinearLayout container;
    private TextView tvEmpty, btnBack, btnClear;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        container = (LinearLayout) findViewById(R.id.historyContainer);
        tvEmpty   = (TextView) findViewById(R.id.tvEmpty);
        btnBack   = (TextView) findViewById(R.id.btnBack);
        btnClear  = (TextView) findViewById(R.id.btnClear);

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });

        btnClear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                new AlertDialog.Builder(HistoryActivity.this)
                    .setTitle("Clear history?")
                    .setMessage("This removes all saved scan sessions.")
                    .setPositiveButton("Clear", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface d, int w) {
                            HistoryStore.clear(HistoryActivity.this);
                            render();
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        // Theme header and empty-state views
        btnBack.setTextColor(Prefs.accent(this));
        btnClear.setTextColor(Prefs.accent(this));
        tvEmpty.setTextColor(Prefs.muted(this));
        Theme.applyFont(this, tvEmpty, Typeface.NORMAL);

        // Theme the title TextView (it sits between btnBack and btnClear in the header)
        ViewGroup header = (ViewGroup) btnBack.getParent();
        if (header != null) {
            for (int i = 0; i < header.getChildCount(); i++) {
                View child = header.getChildAt(i);
                if (child instanceof TextView
                        && child.getId() != R.id.btnBack
                        && child.getId() != R.id.btnClear) {
                    TextView title = (TextView) child;
                    title.setTextColor(Prefs.text(this));
                    Theme.applyFont(this, title, Typeface.BOLD);
                }
            }
        }

        // Theme root background via content view so it stays current when theme changes
        ViewGroup content = (ViewGroup) findViewById(android.R.id.content);
        if (content != null && content.getChildCount() > 0) {
            content.getChildAt(0).setBackgroundColor(Prefs.bg(this));
        }

        container.removeAllViews();
        List<HistoryStore.Entry> entries = HistoryStore.getAll(this);
        tvEmpty.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);

        for (int i = 0; i < entries.size(); i++) {
            final HistoryStore.Entry e = entries.get(i);

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundDrawable(Theme.card(this));
            card.setPadding(dp(14), dp(12), dp(14), dp(12));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(10);
            card.setLayoutParams(lp);
            card.setClickable(true);

            LinearLayout top = new LinearLayout(this);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);

            TextView tag = new TextView(this);
            tag.setText(e.type);
            tag.setTextColor(Prefs.accent(this));
            tag.setTextSize(13f);
            Theme.applyFont(this, tag, Typeface.BOLD);
            top.addView(tag);

            TextView found = new TextView(this);
            found.setText("  " + e.found + "/" + e.total + " found");
            found.setTextColor(Prefs.info(this));
            found.setTextSize(12f);
            Theme.applyFont(this, found, Typeface.NORMAL);
            LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            found.setLayoutParams(flp);
            top.addView(found);

            TextView date = new TextView(this);
            date.setText(e.date);
            date.setTextColor(Prefs.muted(this));
            date.setTextSize(11f);
            Theme.applyFont(this, date, Typeface.NORMAL);
            top.addView(date);

            card.addView(top);

            TextView target = new TextView(this);
            target.setText(e.target);
            target.setTextColor(Prefs.text(this));
            target.setTextSize(14f);
            target.setPadding(0, dp(4), 0, 0);
            Theme.applyFont(this, target, Typeface.NORMAL);
            card.addView(target);

            card.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { showDetail(e); }
            });

            container.addView(card);
        }
    }

    private void showDetail(HistoryStore.Entry e) {
        ScrollView sv = new ScrollView(this);
        TextView tv = new TextView(this);
        tv.setText(e.results.length() == 0 ? "(no log stored)" : e.results);
        tv.setTextColor(Prefs.text(this));
        tv.setTextSize(12f);
        Theme.applyFont(this, tv, Typeface.NORMAL);
        tv.setPadding(dp(16), dp(16), dp(16), dp(16));
        tv.setTextIsSelectable(true);
        sv.addView(tv);

        new AlertDialog.Builder(this)
            .setTitle(e.type + " \u00B7 " + e.target)
            .setView(sv)
            .setPositiveButton("Close", null)
            .show();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
