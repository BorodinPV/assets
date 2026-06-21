package ru.reweu.game.gltf;

import java.io.StringReader;
import java.nio.file.Path;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonValue;
import ru.reweu.game.render.RenderErrorLog;

/**
 * Разбор {@code KHR_materials_clearcoat} / {@code KHR_materials_transmission} из JSON GLB
 * (jgltf {@code MaterialModelV2} эти поля не отдаёт).
 */
public final class GltfMaterialExtensions {

    private GltfMaterialExtensions() {
    }

    public static GltfMaterialExtensionFlags readFromGlb(Path glbPath) {
        try {
            String json = GltfGlbJson.readJsonChunkUtf8(glbPath);
            return parseMaterialsJson(json);
        } catch (Exception e) {
            RenderErrorLog.warn("glTF: could not read material extensions from GLB: " + glbPath, e);
            return GltfMaterialExtensionFlags.empty();
        }
    }

    static GltfMaterialExtensionFlags parseMaterialsJson(String json) {
        try (JsonReader reader = Json.createReader(new StringReader(json))) {
            JsonObject root = reader.readObject();
            JsonArray materials = root.getJsonArray("materials");
            if (materials == null || materials.isEmpty()) {
                return GltfMaterialExtensionFlags.empty();
            }
            int n = materials.size();
            boolean[] clearcoat = new boolean[n];
            boolean[] transmission = new boolean[n];
            for (int i = 0; i < n; i++) {
                JsonValue mv = materials.get(i);
                if (mv.getValueType() != JsonValue.ValueType.OBJECT) {
                    continue;
                }
                JsonObject mat = mv.asJsonObject();
                JsonObject ext = mat.getJsonObject("extensions");
                if (ext == null) {
                    continue;
                }
                if (ext.containsKey("KHR_materials_clearcoat")) {
                    clearcoat[i] = true;
                }
                if (ext.containsKey("KHR_materials_transmission")) {
                    transmission[i] = true;
                }
            }
            return new GltfMaterialExtensionFlags(clearcoat, transmission);
        } catch (Exception e) {
            RenderErrorLog.warn("glTF: parse materials JSON failed", e);
            return GltfMaterialExtensionFlags.empty();
        }
    }
}
