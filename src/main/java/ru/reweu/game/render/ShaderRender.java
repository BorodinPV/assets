package ru.reweu.game.render;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import ru.reweu.game.loader.Mesh;

public class ShaderRender {

    private static final Vector3f TMP_SORT_POS = new Vector3f();
    private static float[] transparentDistScratch;
    private static int[] transparentOrderScratch;

    /** Упорядочить меши по «состоянию материала» — меньше переключений текстур в одном батче. */
    public static void sortMeshesByMaterialState(Mesh[] meshes) {
        if (meshes == null || meshes.length <= 1) {
            return;
        }
        Arrays.sort(meshes, Comparator.comparingLong(Mesh::materialStateKey));
    }

    /** Карта теней: только depth. */
    public static void renderWorldMeshesDepth(
        ShaderProgram depthShader,
        Mesh[] meshes,
        Matrix4f model,
        Matrix4f lightSpaceMatrix
    ) {
        depthShader.use();
        depthShader.setUniform("lightSpaceMatrix", lightSpaceMatrix);
        depthShader.setUniform("model", model);
        for (Mesh meshRender : meshes) {
            meshRender.renderDepthOnly();
        }
    }

    /** Шейдер мира (fragment_shader.glsl); свет задаётся в {@link WorldRenderer}. */
    public static void renderWorldMeshes(
        ShaderProgram shaderProgram,
        Mesh[] meshes,
        Matrix4f model,
        Matrix4f view,
        Matrix4f projection
    ) {
        renderWorldMeshes(shaderProgram, meshes, model, view, projection, false);
    }

    /**
     * @param opaqueGeometryPass см. {@link Mesh#render(boolean)}
     */
    public static void renderWorldMeshes(
        ShaderProgram shaderProgram,
        Mesh[] meshes,
        Matrix4f model,
        Matrix4f view,
        Matrix4f projection,
        boolean opaqueGeometryPass
    ) {
        shaderProgram.use();
        if (view != null) {
            shaderProgram.setUniform("view", view);
        }
        if (projection != null) {
            shaderProgram.setUniform("projection", projection);
        }
        shaderProgram.setUniform("model", model);
        for (Mesh meshRender : meshes) {
            meshRender.render(opaqueGeometryPass);
        }
    }

    /** Прозрачный оверлей (стекло): дальше от камеры раньше, чем ближнее. */
    public static void renderWorldMeshesTransparentSorted(
        ShaderProgram shaderProgram,
        List<Mesh> meshes,
        Matrix4f model,
        Matrix4f view,
        Matrix4f projection,
        Vector3f cameraWorldPos
    ) {
        if (meshes.isEmpty()) {
            return;
        }
        int n = meshes.size();
        ensureTransparentScratch(n);
        float[] distSq = transparentDistScratch;
        int[] order = transparentOrderScratch;
        for (int i = 0; i < n; i++) {
            // Use getLocalCenterDirect to avoid allocation per mesh
            TMP_SORT_POS.set(meshes.get(i).getLocalCenterDirect());
            model.transformPosition(TMP_SORT_POS);
            distSq[i] = TMP_SORT_POS.distanceSquared(cameraWorldPos);
        }
        sortIndicesDescendingByFloatKey(distSq, order, n);
        shaderProgram.use();
        if (view != null) {
            shaderProgram.setUniform("view", view);
        }
        if (projection != null) {
            shaderProgram.setUniform("projection", projection);
        }
        shaderProgram.setUniform("model", model);
        for (int i = 0; i < n; i++) {
            meshes.get(order[i]).render(false);
        }
    }

    private static void ensureTransparentScratch(int n) {
        if (transparentDistScratch == null || transparentDistScratch.length < n) {
            int cap = Math.max(n, transparentDistScratch == null ? 16 : transparentDistScratch.length * 2);
            transparentDistScratch = new float[cap];
            transparentOrderScratch = new int[cap];
        }
    }

    /** Сортирует {@code indices[0..n)} — перестановка: {@code keys[indices[i]]} по убыванию. */
    private static void sortIndicesDescendingByFloatKey(float[] keys, int[] indices, int n) {
        // Build boxed index array for Arrays.sort (avoids autoboxing in comparator)
        Integer[] boxed = new Integer[n];
        for (int i = 0; i < n; i++) {
            boxed[i] = i;
        }
        Arrays.sort(boxed, (a, b) -> Float.compare(keys[b], keys[a]));
        for (int i = 0; i < n; i++) {
            indices[i] = boxed[i];
        }
    }
}
