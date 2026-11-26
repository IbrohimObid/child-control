package uz.ajib.bolanazorat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Telegram Bot API bilan oddiy ishlash (kutubxonasiz). */
public final class Telegram {
    private final String token;

    public Telegram(String token) { this.token = token; }

    private JSONObject call(String method, Map<String, String> params, int readTimeoutMs) throws Exception {
        URL url = new URL("https://api.telegram.org/bot" + token + "/" + method);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setConnectTimeout(15_000);
        con.setReadTimeout(readTimeoutMs);
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");

        StringBuilder body = new StringBuilder();
        if (params != null) {
            for (Map.Entry<String, String> e : params.entrySet()) {
                if (body.length() > 0) body.append('&');
                body.append(URLEncoder.encode(e.getKey(), "UTF-8")).append('=')
                        .append(URLEncoder.encode(e.getValue(), "UTF-8"));
            }
        }
        try (OutputStream os = con.getOutputStream()) {
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }

        int code = con.getResponseCode();
        InputStream is = code >= 400 ? con.getErrorStream() : con.getInputStream();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        if (is != null) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            is.close();
        }
        con.disconnect();
        String text = bos.toString("UTF-8");
        JSONObject json = text.isEmpty() ? new JSONObject() : new JSONObject(text);
        if (!json.optBoolean("ok", false)) {
            throw new Exception("Telegram xato " + code + ": " + json.optString("description", text));
        }
        return json;
    }

    /** Bot nomini qaytaradi (token to'g'riligini tekshirish uchun). */
    public String getMeUsername() throws Exception {
        return call("getMe", null, 20_000).getJSONObject("result").optString("username");
    }

    public JSONArray getUpdates(long offset, int timeoutSec) throws Exception {
        Map<String, String> p = new java.util.HashMap<>();
        p.put("offset", String.valueOf(offset));
        p.put("timeout", String.valueOf(timeoutSec));
        p.put("allowed_updates", "[\"message\"]");
        return call("getUpdates", p, (timeoutSec + 15) * 1000).getJSONArray("result");
    }

    public void deleteWebhook() {
        try { call("deleteWebhook", null, 20_000); } catch (Exception ignored) {}
    }

    public void send(long chatId, String text, String replyMarkupJson) {
        // Telegram limiti 4096 belgi — bo'lib yuboramiz
        int max = 4000;
        for (int i = 0; i < text.length(); i += max) {
            String part = text.substring(i, Math.min(text.length(), i + max));
            Map<String, String> p = new java.util.HashMap<>();
            p.put("chat_id", String.valueOf(chatId));
            p.put("text", part);
            if (replyMarkupJson != null && i + max >= text.length()) p.put("reply_markup", replyMarkupJson);
            try { call("sendMessage", p, 20_000); } catch (Exception ignored) {}
        }
    }
}
