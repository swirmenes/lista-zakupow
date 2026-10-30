package pl.listazakupow;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Paint;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.inputmethod.InputMethodManager;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;

public class MainActivity extends Activity {
    private ArrayList<ListStore.Item> items = new ArrayList<>();
    private LinearLayout todoBox;
    private LinearLayout doneBox;
    private View doneSection;
    private TextView emptyView;
    private TextView countView;
    private EditText input;
    private Switch lockSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        todoBox = findViewById(R.id.todo);
        doneBox = findViewById(R.id.done);
        doneSection = findViewById(R.id.done_section);
        emptyView = findViewById(R.id.empty);
        countView = findViewById(R.id.count);
        input = findViewById(R.id.input);
        Button add = findViewById(R.id.add);
        Button clearDone = findViewById(R.id.clear_done);
        // Dotknięcie listy chowa klawiaturę.
        findViewById(R.id.scroll).setOnTouchListener((v, ev) -> {
            if (ev.getAction() == MotionEvent.ACTION_DOWN && input.hasFocus()) hideKeyboard();
            return false;
        });
        lockSwitch = findViewById(R.id.lock_switch);
        lockSwitch.setOnClickListener(v -> {
            ListStore.setShowOnLock(this, lockSwitch.isChecked());
            ListNotification.update(this);
        });

        add.setOnClickListener(v -> addFromInput());
        input.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId == EditorInfo.IME_ACTION_DONE || enter) {
                addFromInput();
                return true;
            }
            return false;
        });
        clearDone.setOnClickListener(v -> {
            items.removeIf(it -> it.done);
            changed();
        });

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        items = ListStore.load(this);
        lockSwitch.setChecked(ListStore.showOnLock(this));
        render();
        // Przywraca powiadomienie (jeśli funkcja jest włączona), gdy zostało zamknięte.
        ListNotification.update(this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        ListNotification.update(this);
    }

    private void addFromInput() {
        String raw = input.getText().toString();
        boolean added = false;
        for (String part : raw.split("[,;\\n]")) {
            String t = part.trim();
            if (!t.isEmpty()) {
                items.add(new ListStore.Item(t));
                added = true;
            }
        }
        if (added) {
            input.setText("");
            changed();
        } else {
            // Puste pole + "Dodaj"/Enter = koniec wpisywania, chowamy klawiaturę.
            hideKeyboard();
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        input.clearFocus();
    }

    private void changed() {
        ListStore.save(this, items);
        render();
        ListNotification.update(this);
    }

    private void render() {
        todoBox.removeAllViews();
        doneBox.removeAllViews();
        ArrayList<ListStore.Item> done = new ArrayList<>();
        int todoCount = 0;
        for (ListStore.Item it : items) {
            if (it.done) {
                done.add(it);
            } else {
                todoBox.addView(row(it));
                todoCount++;
            }
        }
        Collections.sort(done, (a, b) -> Long.compare(b.doneAt, a.doneAt));
        for (ListStore.Item it : done) doneBox.addView(row(it));

        emptyView.setVisibility(todoCount == 0 ? View.VISIBLE : View.GONE);
        doneSection.setVisibility(done.isEmpty() ? View.GONE : View.VISIBLE);
        countView.setText(items.isEmpty() ? ""
                : getString(R.string.count, done.size(), items.size()));
    }

    private View row(ListStore.Item it) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.row_divider);

        CheckBox cb = new CheckBox(this);
        cb.setText(it.text);
        cb.setChecked(it.done);
        cb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
        cb.setTextColor(getColor(it.done ? R.color.done : R.color.fg));
        cb.setPadding(dp(10), dp(14), dp(8), dp(14));
        if (it.done) {
            cb.setPaintFlags(cb.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        }
        cb.setOnClickListener(v -> {
            it.done = !it.done;
            it.doneAt = it.done ? System.currentTimeMillis() : 0L;
            changed();
        });
        row.addView(cb, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView del = new TextView(this);
        del.setText("×");
        del.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        del.setTextColor(getColor(R.color.muted));
        del.setGravity(Gravity.CENTER);
        del.setContentDescription(getString(R.string.delete_item, it.text));
        del.setOnClickListener(v -> {
            items.remove(it);
            changed();
        });
        row.addView(del, new LinearLayout.LayoutParams(dp(48), dp(48)));
        return row;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
