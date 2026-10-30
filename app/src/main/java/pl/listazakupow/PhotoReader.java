package pl.listazakupow;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Odczyt odręcznej listy zakupów ze zdjęcia przez Claude API. */
public final class PhotoReader {
    private static final String MODEL = "claude-sonnet-5-5";
    private static final int MAX_SIDE = 1568;

    private static final String PROMPT =
            "Na zdjęciu jest odręcznie napisana lista zakupów, zwykle po polsku. "
            + "Odczytaj każdą pozycję w kolejności z kartki. Zachowaj ilości, jeśli są "
            + "napisane (np. \"2 kg ziemniaków\"). Pomiń pozycje przekreślone, nagłówki, "
            + "daty i podpisy. Popraw tylko oczywiste literówki. Odpowiedz wyłącznie "
            + "tablicą JSON z napisami, np. [\"mleko\",\"chleb\",\"6 jajek\"]. Jeśli na "
            + "zdjęciu nie ma listy zakupów, odpowiedz [].";

    /** Rodzaj błędu, żeby pokazać zrozumiały komunikat. */
    public static final class ReadException extends Exception {
        public static final int BAD_KEY = 1, NO_CREDIT = 2, BUSY = 3, NETWORK = 4, IMAGE = 5, OTHER = 6;
        public final int kind;

        ReadException(int kind, String msg) {
            super(msg);
            this.kind = kind;
        }
    }

    private PhotoReader() {}

    public static List<String> read(Context c, Uri uri, String apiKey) throws ReadException {
        String b64;
        try {
            b64 = loadJpegBase64(c, uri);
        } catch (Exception e) {
            throw new ReadException(ReadException.IMAGE, String.valueOf(e.getMessage()));
        }
        try {
            return callClaude(apiKey, b64);
        } catch (ReadException e) {
            throw e;
        } catch (IOException e) {
            throw new ReadException(ReadException.NETWORK, String.valueOf(e.getMessage()));
        } catch (Exception e) {
            throw new ReadException(ReadException.OTHER, String.valueOf(e.getMessage()));
        }
    }

    private static String loadJpegBase64(Context c, Uri uri) throws IOException {
        ContentResolver cr = c.getContentResolver();

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = cr.openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException("Nieczytelny obraz");

        int sample = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        Bitmap bmp;
        try (InputStream in = cr.openInputStream(uri)) {
            bmp = BitmapFactory.decodeStream(in, null, opts);
        }
        if (bmp == null) throw new IOException("Nieczytelny obraz");

        int rotation = 0;
        try (InputStream in = cr.openInputStream(uri)) {
            if (in != null) {
                int o = new ExifInterface(in).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                if (o == ExifInterface.ORIENTATION_ROTATE_90) rotation = 90;
                else if (o == ExifInterface.ORIENTATION_ROTATE_180) rotation = 180;
                else if (o == ExifInterface.ORIENTATION_ROTATE_270) rotation = 270;
            }
        } catch (Exception ignored) {
        }

        float scale = Math.min(1f, (float) MAX_SIDE / Math.max(bmp.getWidth(), bmp.getHeight()));
        Matrix m = new Matrix();
        m.postScale(scale, scale);
        if (rotation != 0) m.postRotate(rotation);
        Bitmap out = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        out.compress(Bitmap.CompressFormat.JPEG, 85, bos);
        return Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
    }

    private static List<String> callClaude(String apiKey, String b64) throws Exception {
        JSONObject image = new JSONObject()
                .put("type", "image")
                .put("source", new JSONObject()
                        .put("type", "base64")
                        .put("media_type", "image/jpeg")
                        .put("data", b64));
        JSONObject text = new JSONObject().put("type", "text").put("text", PROMPT);
        JSONObject msg = new JSONObject()
                .put("role", "user")
                .put("content", new JSONArray().put(image).put(text));
        JSONObject body = new JSONObject()
                .put("model", MODEL)
                .put("max_tokens", 1024)
                .put("messages", new JSONArray().put(msg));

        HttpURLConnection con = (HttpURLConnection) new URL("https://api.anthropic.com/v1/messages").openConnection();
        con.setRequestMethod("POST");
        con.setConnectTimeout(20000);
        con.setReadTimeout(120000);
        con.setDoOutput(true);
        con.setRequestProperty("content-type", "application/json");
        con.setRequestProperty("x-api-key", apiKey);
        con.setRequestProperty("anthropic-version", "2023-06-01");
        try (OutputStream os = con.getOutputStream()) {
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }

        int code = con.getResponseCode();
        String resp = readAll(code >= 400 ? con.getErrorStream() : con.getInputStream());
        con.disconnect();

        if (code == 401 || code == 403) throw new ReadException(ReadException.BAD_KEY, resp);
        if (code == 429 || code == 529 || code == 503) throw new ReadException(ReadException.BUSY, resp);
        if (code == 400 && resp.toLowerCase().contains("credit")) throw new ReadException(ReadException.NO_CREDIT, resp);
        if (code >= 400) throw new ReadException(ReadException.OTHER, "HTTP " + code + ": " + resp);

        JSONArray content = new JSONObject(resp).getJSONArray("content");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < content.length(); i++) {
            JSONObject block = content.getJSONObject(i);
            if ("text".equals(block.optString("type"))) sb.append(block.optString("text"));
        }
        String s = sb.toString();
        int a = s.indexOf('['), b = s.lastIndexOf(']');
        List<String> items = new ArrayList<>();
        if (a < 0 || b < a) return items;
        JSONArray arr = new JSONArray(s.substring(a, b + 1));
        for (int i = 0; i < arr.length(); i++) {
            String t = String.valueOf(arr.opt(i)).trim();
            if (!t.isEmpty() && !"null".equals(t)) items.add(t);
        }
        return items;
    }

    private static String readAll(InputStream in) throws IOException {
        if (in == null) return "";
        try (InputStream is = in) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toString("UTF-8");
        }
    }
}
