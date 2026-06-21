package ru.reweu.game.gltf;

/**
 * Текстурные юниты для {@code pbr_gltf.frag} (материал glTF 2.0). Тени и IBL задаются
 * в {@link ru.reweu.game.render.SceneFrameUniforms} / {@link ru.reweu.game.render.WorldRenderer}
 * (слоты 5–8), см. {@code docs/RENDERING.md}.
 */
public final class GltfShaderTextureUnits {

    public static final int BASE_COLOR = 0;
    public static final int METALLIC_ROUGHNESS = 1;
    public static final int NORMAL = 2;
    public static final int OCCLUSION = 3;
    public static final int EMISSIVE = 4;

    private GltfShaderTextureUnits() {
    }
}
