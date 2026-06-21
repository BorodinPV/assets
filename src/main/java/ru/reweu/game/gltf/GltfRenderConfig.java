package ru.reweu.game.gltf;

/**
 * Конфигурация glTF PBR рендеринга, специфичная для приложения.
 * Создаётся в game-shader из GameConfig и передаётся в {@link GltfPbrRenderer#setConfig(GltfRenderConfig)}.
 */
public record GltfRenderConfig(
    float extensionFallbackMinRoughness,
    boolean debugDisableNormalMap,
    int debugVisualizeMode
) {
    /** Дефолтные значения (совпадают с GameConfig). */
    public static GltfRenderConfig defaults() {
        return new GltfRenderConfig(0.42f, false, 0);
    }
}
