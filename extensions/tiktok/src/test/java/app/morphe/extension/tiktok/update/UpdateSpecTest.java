package app.morphe.extension.tiktok.update;

import static org.junit.Assert.*;
import java.net.URL;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class UpdateSpecTest {
    private JSONObject asset(String name) throws Exception {
        return new JSONObject().put("url", UpdateSpec.REPOSITORY + "/releases/download/v1/" + name + ".apk")
                .put("sha256", "a".repeat(64)).put("size", 500_000_000);
    }

    private JSONObject release() throws Exception {
        return new JSONObject().put("build", 2).put("version", "1.1").put("changes", "Исправления")
                .put("assets", new JSONObject().put("arm64-v8a", asset("v8"))
                        .put("armeabi-v7a", asset("v7")).put("universal", asset("universal")));
    }

    @Test public void selectsCompatibleAbiInDeviceOrder() throws Exception {
        assertTrue(new UpdateSpec(release(), new String[]{"arm64-v8a", "armeabi-v7a"}).url.endsWith("/v8.apk"));
        assertTrue(new UpdateSpec(release(), new String[]{"armeabi-v7a"}).url.endsWith("/v7.apk"));
        assertThrows(Exception.class, () -> new UpdateSpec(release(), new String[]{"x86_64", "x86"}));
    }

    @Test public void fallsBackOnlyToAnArmUniversalApk() throws Exception {
        JSONObject json = release();
        json.getJSONObject("assets").remove("arm64-v8a");
        assertTrue(new UpdateSpec(json, new String[]{"arm64-v8a"}).url.endsWith("/universal.apk"));
    }

    @Test public void refusesMissingHashesWrongRepositoriesAndOversizedFiles() throws Exception {
        for (String field : new String[]{"url", "sha256", "size"}) {
            JSONObject json = release();
            JSONObject selected = json.getJSONObject("assets").getJSONObject("arm64-v8a");
            selected.put(field, field.equals("url") ? "https://github.com/other/repo/releases/download/v1/file.apk"
                    : field.equals("sha256") ? "" : UpdateSpec.MAX_APK_BYTES + 1);
            assertThrows(field, Exception.class, () -> new UpdateSpec(json, new String[]{"arm64-v8a"}));
        }
    }

    @Test public void redirectBoundaryRejectsCleartextCredentialsAndOtherHosts() throws Exception {
        for (String url : new String[]{"http://github.com/file", "https://github.com.evil.example/file",
                "https://user:password@github.com/file", "https://github.com:444/file", "https://127.0.0.1/file"}) {
            assertThrows(url, Exception.class, () -> UpdateSpec.requireGitHub(new URL(url)));
        }
        UpdateSpec.requireGitHub(new URL("https://release-assets.githubusercontent.com/file?signature=test"));
    }

    @Test public void metadataComesOnlyFromThisRepositoryMainBranch() throws Exception {
        assertEquals("https://raw.githubusercontent.com/ttrroyy/tiktok-mod/main/update.json", UpdateSpec.MANIFEST_URL);
        UpdateSpec.requireGitHub(new URL(UpdateSpec.MANIFEST_URL));
        for (String url : new String[]{
                "https://raw.githubusercontent.com/other/repo/main/update.json",
                "https://raw.githubusercontent.com/ttrroyy/tiktok-mod/other/update.json",
                "https://raw.githubusercontent.com/ttrroyy/tiktok-mod/main/file.apk",
                UpdateSpec.MANIFEST_URL + "?token=test", UpdateSpec.MANIFEST_URL + "#other"}) {
            assertThrows(url, Exception.class, () -> UpdateSpec.requireGitHub(new URL(url)));
        }
        JSONObject json = release();
        json.getJSONObject("assets").getJSONObject("arm64-v8a").put("url", UpdateSpec.MANIFEST_URL);
        assertThrows(Exception.class, () -> new UpdateSpec(json, new String[]{"arm64-v8a"}));
    }
}
