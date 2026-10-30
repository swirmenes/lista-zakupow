package pl.listazakupow;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.util.Arrays;
import java.util.List;
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

        View camera = findViewById(R.id.camera);
        camera.setOnClickListener(v -> onCameraClick());
        camera.setOnLongClickListener(v -> {
            askForKey(null);
            return true;
        });
        if (savedInstanceState != null) {
            String p = savedInstanceState.getString("photo");
            if (p != null) photoFile = new File(p);
        }

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

    // ---------- Zdjęcie listy ----------

    private static final int REQ_CAMERA = 10;
    private static final int REQ_GALLERY = 11;
    private File photoFile;

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (photoFile != null) out.putString("photo", photoFile.getAbsolutePath());
    }

    private void onCameraClick() {
        hideKeyboard();
        if (ListStore.apiKey(this).isEmpty()) {
            askForKey(this::onCameraClick);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.photo_title)
                .setItems(new CharSequence[]{getString(R.string.photo_take), getString(R.string.photo_pick)},
                        (d, which) -> {
                            if (which == 0) takePhoto();
                            else pickPhoto();
                        })
                .show();
    }

    private void takePhoto() {
        try {
            File dir = new File(getCacheDir(), "photos");
            dir.mkdirs();
            photoFile = new File(dir, "lista.jpg");
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".files", photoFile);
            Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            i.putExtra(MediaStore.EXTRA_OUTPUT, uri);
            i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(i, REQ_CAMERA);
        } catch (Exception e) {
            Toast.makeText(this, R.string.no_camera, Toast.LENGTH_LONG).show();
        }
    }

    private void pickPhoto() {
        try {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("image/*");
            startActivityForResult(i, REQ_GALLERY);
        } catch (Exception e) {
            Toast.makeText(this, R.string.no_gallery, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) return;
        Uri uri = null;
        if (requestCode == REQ_CAMERA && photoFile != null) uri = Uri.fromFile(photoFile);
        else if (requestCode == REQ_GALLERY && data != null) uri = data.getData();
        if (uri != null) readPhoto(uri);
    }

    private void readPhoto(Uri uri) {
        final boolean[] cancelled = {false};
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(24), dp(24), dp(24), dp(16));
        box.addView(new ProgressBar(this));
        TextView t = new TextView(this);
        t.setText(R.string.reading);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        t.setTextColor(getColor(R.color.fg));
        t.setPadding(dp(16), 0, 0, 0);
        box.addView(t);
        AlertDialog progress = new AlertDialog.Builder(this)
                .setView(box)
                .setCancelable(false)
                .setNegativeButton(R.string.cancel, (d, w) -> cancelled[0] = true)
                .show();

        String key = ListStore.apiKey(this);
        new Thread(() -> {
            List<String> found = null;
            PhotoReader.ReadException error = null;
            try {
                found = PhotoReader.read(getApplicationContext(), uri, key);
            } catch (PhotoReader.ReadException e) {
                error = e;
            }
            final List<String> f = found;
            final PhotoReader.ReadException err = error;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || cancelled[0]) return;
                progress.dismiss();
                if (err != null) showReadError(err);
                else if (f == null || f.isEmpty()) showMessage(R.string.nothing_found);
                else showFound(f);
            });
        }).start();
    }

    private void showFound(List<String> found) {
        CharSequence[] names = found.toArray(new CharSequence[0]);
        boolean[] checked = new boolean[names.length];
        Arrays.fill(checked, true);
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.found_title, names.length))
                .setMultiChoiceItems(names, checked, (d, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton(R.string.add_checked, (d, w) -> {
                    for (int i = 0; i < names.length; i++) {
                        if (checked[i]) items.add(new ListStore.Item(names[i].toString()));
                    }
                    changed();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showReadError(PhotoReader.ReadException e) {
        int msg;
        switch (e.kind) {
            case PhotoReader.ReadException.BAD_KEY: msg = R.string.err_key; break;
            case PhotoReader.ReadException.NO_CREDIT: msg = R.string.err_credit; break;
            case PhotoReader.ReadException.BUSY: msg = R.string.err_busy; break;
            case PhotoReader.ReadException.NETWORK: msg = R.string.err_network; break;
            case PhotoReader.ReadException.IMAGE: msg = R.string.err_image; break;
            default: msg = R.string.err_other;
        }
        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(R.string.err_title)
                .setMessage(msg)
                .setPositiveButton(R.string.ok, null);
        if (e.kind == PhotoReader.ReadException.BAD_KEY) {
            b.setNeutralButton(R.string.change_key, (d, w) -> askForKey(null));
        }
        b.show();
    }

    private void showMessage(int msg) {
        new AlertDialog.Builder(this).setMessage(msg).setPositiveButton(R.string.ok, null).show();
    }

    /** Okno do wklejenia klucza Claude API. Klucz zostaje tylko w tym telefonie. */
    private void askForKey(Runnable then) {
        EditText field = new EditText(this);
        field.setHint("sk-ant-…");
        field.setSingleLine(true);
        field.setText(ListStore.apiKey(this));
        field.setTextColor(getColor(R.color.fg));
        LinearLayout wrap = new LinearLayout(this);
        wrap.setPadding(dp(20), dp(8), dp(20), 0);
        wrap.addView(field, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(R.string.key_title)
                .setMessage(R.string.key_message)
                .setView(wrap)
                .setPositiveButton(R.string.save, (d, w) -> {
                    String k = field.getText().toString().trim();
                    ListStore.setApiKey(this, k);
                    if (!k.isEmpty() && then != null) then.run();
                })
                .setNegativeButton(R.string.cancel, null);
        if (!ListStore.apiKey(this).isEmpty()) {
            b.setNeutralButton(R.string.remove_key, (d, w) -> ListStore.setApiKey(this, ""));
        }
        b.show();
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
