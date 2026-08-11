package myscanne.com;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * Shows every saved scan session. Tap a row to view the full result log.
 * Java 7 – no lambdas.
 */
public class HistoryActivity extends Activity {

    private LinearLayout container;
    private TextView tvEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        container = (LinearLayout) findViewById(R.id.historyContainer);
        tvEmpty = (TextView) findViewById(R.id.tvEmpty);

        findViewById(R.id.btnBack).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });

        findViewById(R.id.btnClear).setOnClickListener(new View.OnClickListener() {
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
        container.removeAllViews();
        List<HistoryStore.Entry> entries = HistoryStore.getAll(this);
        tvEmpty.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);

        for (int i = 0; i < entries.size(); i++) {
            final HistoryStore.Entry e = entries.get(i);

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundResource(R.drawable.card_dark);
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
            tag.setTextColor(0xFFFF3B4E);
            tag.setTextSize(13f);
            tag.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            top.addView(tag);

            TextView found = new TextView(this);
            found.setText("  " + e.found + "/" + e.total + " found");
            found.setTextColor(0xFF22C55E);
            found.setTextSize(12f);
            LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            found.setLayoutParams(flp);
            top.addView(found);

            TextView date = new TextView(this);
            date.setText(e.date);
            date.setTextColor(0xFF8A93A6);
            date.setTextSize(11f);
            top.addView(date);

            card.addView(top);

            TextView target = new TextView(this);
            target.setText(e.target);
            target.setTextColor(0xFFF2F5FA);
            target.setTextSize(14f);
            target.setPadding(0, dp(4), 0, 0);
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
        tv.setTextColor(0xFFF2F5FA);
        tv.setTextSize(12f);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setPadding(dp(16), dp(16), dp(16), dp(16));
        tv.setTextIsSelectable(true);
        sv.addView(tv);

        new AlertDialog.Builder(this)
                .setTitle(e.type + " · " + e.target)
                .setView(sv)
                .setPositiveButton("Close", null)
                .show();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
