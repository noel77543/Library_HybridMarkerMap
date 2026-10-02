package com.noelsung.hybridmarkermap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class MapStyleTest {

    //單元測試的工作目錄為 module 根目錄
    private static final File RAW_DIR = new File("src/main/res/raw");

    @Test
    public void defaultStyle_hasNoResource() {
        assertEquals(0, MapStyle.DEFAULT.styleRes);
    }

    @Test
    public void customStyles_haveDistinctResources() {
        Set<Integer> resources = new HashSet<>();
        for (MapStyle style : MapStyle.values()) {
            if (style == MapStyle.DEFAULT) {
                continue;
            }
            assertNotEquals(style + " 未設定樣式資源", 0, style.styleRes);
            assertTrue(style + " 與其他樣式共用資源", resources.add(style.styleRes));
        }
    }

    /***
     * 每個樣式對應 res/raw/hmm_map_style_<名稱>.json，且為 Google Maps 可接受的樣式格式
     */
    @Test
    public void customStyles_areValidStyleJson() throws IOException {
        for (MapStyle style : MapStyle.values()) {
            if (style == MapStyle.DEFAULT) {
                continue;
            }
            File file = new File(RAW_DIR, "hmm_map_style_" + style.name().toLowerCase(Locale.ROOT) + ".json");
            assertTrue("找不到 " + file, file.isFile());

            JsonArray rules = JsonParser.parseString(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)).getAsJsonArray();
            assertTrue(file.getName() + " 沒有任何規則", rules.size() > 0);
            for (JsonElement element : rules) {
                JsonObject rule = element.getAsJsonObject();
                assertTrue(file.getName() + " 的規則缺少 stylers: " + rule, rule.has("stylers"));
                assertTrue(file.getName() + " 的 stylers 須為陣列: " + rule, rule.get("stylers").isJsonArray());
            }
        }
    }
}
