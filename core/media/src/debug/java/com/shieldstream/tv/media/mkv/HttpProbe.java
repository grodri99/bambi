package com.shieldstream.tv.media.mkv;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONArray;
import org.json.JSONObject;

public final class HttpProbe {
    public static void main(String[] args) throws Exception {
        int show = Integer.parseInt(args[0]);
        for (int a = 1; a < args.length; a++) {
            String url = args[a];
            try {
                String post = null;
                if (url.startsWith("POST:")) { int bar = url.indexOf('|'); post = url.substring(bar + 1); url = url.substring(5, bar); }
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setRequestProperty("User-Agent", "Mozilla/5.0");
                if (post != null) {
                    c.setRequestMethod("POST");
                    c.setDoOutput(true);
                    c.setRequestProperty("Content-Type", "application/json");
                    c.getOutputStream().write(post.getBytes("UTF-8"));
                }
                c.setConnectTimeout(20000);
                c.setReadTimeout(40000);
                int code = c.getResponseCode();
                InputStream in = code < 400 ? c.getInputStream() : c.getErrorStream();
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[65536];
                int n;
                while (in != null && (n = in.read(buf)) > 0) out.write(buf, 0, n);
                String body = out.toString("UTF-8");
                System.out.println("== " + url.replaceAll("realdebrid=[^|/]+", "realdebrid=***") + " -> HTTP " + code);
                if (!body.trim().startsWith("{")) {
                    System.out.println("   (no es JSON) " + body.substring(0, Math.min(200, body.length())).replace("\n", " "));
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("magnet:\\?xt=urn:btih:[a-fA-F0-9]{40}[^\"'<>\\s]*?dn=([^&\"]+)").matcher(body);
                    int magnets = 0;
                    while (m.find()) { if (magnets++ < Math.max(show, 3)) System.out.println("   magnet: " + java.net.URLDecoder.decode(m.group(1).replace("&amp;", "&"), "UTF-8")); }
                    System.out.println("   magnets: " + magnets);
                    continue;
                }
                JSONArray streams = new JSONObject(body).optJSONArray("streams");
                if (streams == null) System.out.println("   " + body.substring(0, Math.min(300, body.length())).replace("\n", " "));
                int count = streams == null ? 0 : streams.length();
                System.out.println("   fuentes: " + count);
                for (int i = 0; i < Math.min(show, count); i++) {
                    JSONObject s = streams.getJSONObject(i);
                    String title = s.optString("title", s.optString("description", ""));
                    System.out.println("   - " + s.optString("name", "").replace("\n", " ") + " | " + title.replace("\n", " | "));
                }
            } catch (Throwable t) {
                System.out.println("== " + url + " -> ERROR " + t);
            }
        }
    }
}
