package ru.reweu.game.gltf;

import de.javagl.jgltf.model.v2.MaterialModelV2;

/**
 * Разбиение glTF по проходам opaque / blend.
 * <p>
 * Часть ассетов помечает кузов как {@link MaterialModelV2.AlphaMode#BLEND} при {@code alpha=1} без
 * альфы в текстуре — их удобно рисовать в opaque-проходе (см. ниже). Стёкла часто тоже {@code BLEND}
 * с тем же фактором, но прозрачность задаётся <em>альфой baseColor-текстуры</em>: такие материалы
 * должны идти во второй проход с {@link org.lwjgl.opengl.GL11C#GL_BLEND}, иначе окна остаются
 * «глухими».
 */
public final class GltfMaterialPasses {

    /** Порог: BLEND без baseColor-текстуры с baseColorFactor.a не ниже — считаем непрозрачным. */
    public static final float BLEND_OPAQUE_FACTOR_EPS = 0.995f;

    private GltfMaterialPasses() {
    }

    /**
     * Первый проход glTF: OPAQUE, MASK; либо BLEND только если нет baseColor-текстуры и alpha фактора
     * достаточно (ошибочный BLEND у однотонных материалов).
     */
    public static boolean drawnInOpaquePass(MaterialModelV2 m) {
        MaterialModelV2.AlphaMode am = m.getAlphaMode();
        if (am == MaterialModelV2.AlphaMode.OPAQUE || am == MaterialModelV2.AlphaMode.MASK) {
            return true;
        }
        if (am == MaterialModelV2.AlphaMode.BLEND) {
            if (m.getBaseColorTexture() != null) {
                return false;
            }
            float[] bcf = m.getBaseColorFactor();
            float a = (bcf != null && bcf.length >= 4) ? bcf[3] : 1f;
            return a >= BLEND_OPAQUE_FACTOR_EPS;
        }
        return true;
    }
}
