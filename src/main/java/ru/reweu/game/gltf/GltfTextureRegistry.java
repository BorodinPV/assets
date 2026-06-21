package ru.reweu.game.gltf;

import de.javagl.jgltf.model.GltfModel;
import de.javagl.jgltf.model.ImageModel;
import de.javagl.jgltf.model.TextureModel;
import java.util.List;
import ru.reweu.game.loader.Texture;
import ru.reweu.game.loader.TextureLoadLog;

/**
 * Соответствие jgltf {@link TextureModel} → OpenGL 2D-текстуры.
 * <p>Один и тот же {@link ImageModel} может быть загружен дважды: {@code GL_SRGB8_ALPHA8} (baseColor, emissive)
 * и {@code GL_RGBA} (MR, normal, occlusion) — как в glTF §3.9.2. Кэш: {@code [textureIndex][linear|srgb]}.
 */
public final class GltfTextureRegistry {

    private final List<TextureModel> textureModels;
    /** [textureIndex][0]=linear, [1]=srgb — создано лениво по слоту */
    private final Texture[][] cache;
    /** Чтобы не дергать декодер и не спамить лог каждый кадр при ошибке */
    private final boolean[][] loadAttempted;

    public GltfTextureRegistry(GltfModel model) {
        this.textureModels = model.getTextureModels();
        int n = textureModels.size();
        this.cache = new Texture[n][2];
        this.loadAttempted = new boolean[n][2];
    }

    public Texture get(TextureModel tm, boolean srgb) {
        if (tm == null) {
            return null;
        }
        int idx = textureModels.indexOf(tm);
        if (idx < 0) {
            TextureLoadLog.warn("gltf: TextureModel не из этого GltfModel (indexOf=-1)");
            return null;
        }
        int slot = srgb ? 1 : 0;
        if (loadAttempted[idx][slot]) {
            return cache[idx][slot];
        }
        loadAttempted[idx][slot] = true;
        ImageModel im = tm.getImageModel();
        if (im == null) {
            TextureLoadLog.warn("gltf tex #" + idx + " (" + (srgb ? "srgb" : "linear") + "): ImageModel is null");
            return null;
        }
        cache[idx][slot] = Texture.tryFromMemory(
            im.getImageData(),
            "gltf_tex_" + idx + "_" + (srgb ? "srgb" : "lin"),
            Texture.LoadMode.MODEL_WITH_MIPMAPS,
            srgb
        );
        return cache[idx][slot];
    }

    /**
     * Декодирует все слоты (linear + sRGB) для каждого индекса текстуры; в конце — сводка в {@link TextureLoadLog}.
     */
    public void preflightDecodeAll() {
        int n = textureModels.size();
        int ok = 0;
        int fail = 0;
        for (int i = 0; i < n; i++) {
            TextureModel tm = textureModels.get(i);
            for (boolean srgb : new boolean[] {false, true}) {
                Texture t = get(tm, srgb);
                if (t != null) {
                    ok++;
                } else {
                    fail++;
                }
            }
        }
        TextureLoadLog.info(
            "glTF: " + n + " texture index(es), " + (2 * n) + " decode slot(s), ok=" + ok + " fail=" + fail);
    }

    public void cleanup() {
        for (Texture[] pair : cache) {
            for (Texture t : pair) {
                if (t != null) {
                    t.cleanup();
                }
            }
        }
    }
}
