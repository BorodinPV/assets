package ru.reweu.game.gltf;

import de.javagl.jgltf.model.GltfModel;
import de.javagl.jgltf.model.v2.MaterialModelV2;
import java.util.Locale;

/**
 * Эвристика «тонкое стекло» ({@code u_thinGlass} в {@code pbr_gltf.frag}): BLEND без альфы в baseColor
 * часто даёт alpha≈1 и «глухие» окна. Подхватываем типичные имена, структуру материала и
 * {@code KHR_materials_transmission} из {@link GltfMaterialExtensions}.
 */
public final class GltfThinGlass {

    /** Сумма emissiveFactor; ниже — считаем, что материал не «светится как фары». */
    private static final float WEAK_EMISSIVE_SUM = 0.18f;

    private GltfThinGlass() {
    }

    /**
     * @param hasEmissiveTexture до подстановки (1,1,1) для пустого фактора при наличии текстуры
     * @param emissiveR,G,B сырые компоненты {@link MaterialModelV2#getEmissiveFactor()}
     */
    public static boolean shouldUseThinGlass(
        MaterialModelV2 m,
        boolean hasEmissiveTexture,
        float emissiveR,
        float emissiveG,
        float emissiveB,
        GltfModel gltfModel,
        GltfMaterialExtensionFlags ext,
        int materialModelIndex
    ) {
        if (m.getAlphaMode() != MaterialModelV2.AlphaMode.BLEND) {
            return false;
        }

        int matIdx = materialModelIndex >= 0
            ? materialModelIndex
            : (gltfModel != null ? gltfModel.getMaterialModels().indexOf(m) : -1);
        if (matIdx >= 0 && ext != null && ext.hasTransmission(matIdx)) {
            return true;
        }

        float emissiveSum = emissiveR + emissiveG + emissiveB;
        boolean weakEmissive = !hasEmissiveTexture && emissiveSum <= WEAK_EMISSIVE_SUM + 1e-5f;

        String name = m.getName();
        if (weakEmissive && nameSuggestsGlass(name)) {
            return true;
        }

        if (weakEmissive && structuralLooksLikeThinGlass(m, name)) {
            return true;
        }

        return false;
    }

    private static boolean nameSuggestsGlass(String raw) {
        if (raw == null || raw.isEmpty()) {
            return false;
        }
        String n = raw.toLowerCase(Locale.ROOT);
        if (containsAny(
            n,
            "glass",
            "glazing",
            "glaz",
            "windshield",
            "windscreen",
            "wind_screen",
            "vitre",
            "vitres",
            "fenster",
            "plexi",
            "plexiglass",
            "acrylic",
            "perspex",
            "polycarb",
            "lens",
            "visor",
            "canopy",
            "cockpit",
            "sideglass",
            "rear_glass",
            "front_glass",
            "window_glass",
            "car_glass",
            "auto_glass",
            "shard",
            "crystal",
            "crystall"
        )) {
            return true;
        }
        if (n.contains("window") && containsAny(n, "glass", "car", "auto", "side", "rear", "front", "door")) {
            return true;
        }
        if (n.contains("mirror") && containsAny(n, "rear", "side", "wing", "door", "glass")) {
            return true;
        }

        Locale ru = Locale.forLanguageTag("ru-RU");
        String r = raw.toLowerCase(ru);
        if (r.contains("стекл")) {
            return true;
        }
        if (r.contains("лобов")) {
            return true;
        }
        if (r.contains("витрин")) {
            return true;
        }
        if (r.contains("остекл")) {
            return true;
        }
        return false;
    }

    /**
     * BLEND + двусторонность + низкий metallic + baseColor-текстура + слабый emissive — часто стекло/лёд
     * у плохих экспортов; отсекаем типичные растительность/ткани по имени.
     */
    private static boolean structuralLooksLikeThinGlass(MaterialModelV2 m, String name) {
        if (m.getBaseColorTexture() == null || !m.isDoubleSided()) {
            return false;
        }
        if (m.getMetallicFactor() > 0.15f) {
            return false;
        }
        return !structuralExcludeByName(name);
    }

    private static boolean structuralExcludeByName(String name) {
        if (name == null) {
            return false;
        }
        String n = name.toLowerCase(Locale.ROOT);
        return containsAny(
            n,
            "leaf",
            "leaves",
            "foliage",
            "tree",
            "grass",
            "bark",
            "plant",
            "fern",
            "flower",
            "ivy",
            "vine",
            "decal",
            "logo",
            "sign",
            "text_",
            "_text",
            "fabric",
            "cloth",
            "cotton",
            "linen",
            "silk",
            "velvet",
            "hair",
            "fur",
            "feather",
            "paper",
            "cardboard",
            "banner",
            "poster"
        );
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String s : needles) {
            if (haystack.contains(s)) {
                return true;
            }
        }
        return false;
    }
}
