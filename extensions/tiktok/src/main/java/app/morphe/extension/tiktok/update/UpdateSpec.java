/* Copyright 2026 ttrroyy. https://github.com/ttrroyy/tiktok-mod */
package app.morphe.extension.tiktok.update;

import org.json.JSONObject;
import java.io.IOException;
import java.net.URL;

final class UpdateSpec {
    static final int CURRENT_BUILD = 9;
    static final String REPOSITORY = "https://github.com/ttrroyy/tiktok-mod";
    static final String MANIFEST_URL = "https://raw.githubusercontent.com/ttrroyy/tiktok-mod/main/update.json";
    static final long MAX_APK_BYTES = 900L * 1024 * 1024;
    final int build;
    final String version, changes, url, hash;
    final long size;

    UpdateSpec(JSONObject json, String[] abis) throws Exception {
        build = json.getInt("build");
        version = json.getString("version");
        changes = json.optString("changes");
        if (build < 1 || version.isEmpty() || version.length() > 80 || changes.length() > 8000)
            throw new IOException("Некорректные сведения о версии");
        JSONObject assets = json.getJSONObject("assets");
        JSONObject selected = null;
        boolean arm = false;
        for (String abi : abis) {
            if ("arm64-v8a".equals(abi) || "armeabi-v7a".equals(abi)) {
                arm = true;
                selected = assets.optJSONObject(abi);
                if (selected != null) break;
            }
        }
        if (selected == null && arm) selected = assets.optJSONObject("universal");
        if (selected == null) throw new IOException("Нет APK для архитектуры этого устройства");
        url = selected.getString("url");
        hash = selected.getString("sha256");
        size = selected.getLong("size");
        URL parsed = new URL(url);
        requireGitHub(parsed);
        if (!"github.com".equals(parsed.getHost())
                || !parsed.getPath().startsWith("/ttrroyy/tiktok-mod/releases/download/")
                || !hash.matches("[0-9a-f]{64}") || size < 1 || size > MAX_APK_BYTES)
            throw new IOException("Некорректный файл обновления");
    }

    static void requireGitHub(URL url) throws IOException {
        String host = url.getHost();
        if (!"https".equals(url.getProtocol()) || url.getUserInfo() != null
                || url.getPort() != -1 && url.getPort() != 443
                || !("github.com".equals(host) || "release-assets.githubusercontent.com".equals(host)
                || "objects.githubusercontent.com".equals(host) || "github-releases.githubusercontent.com".equals(host)
                || "raw.githubusercontent.com".equals(host) && "/ttrroyy/tiktok-mod/main/update.json".equals(url.getPath())
                        && url.getQuery() == null && url.getRef() == null))
            throw new IOException("Недопустимый адрес обновления");
    }
}
