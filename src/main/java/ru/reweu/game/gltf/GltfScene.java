package ru.reweu.game.gltf;

import static org.lwjgl.opengl.GL15.GL_DYNAMIC_DRAW;
import static org.lwjgl.opengl.GL31.GL_UNIFORM_BUFFER;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glDeleteBuffers;
import static org.lwjgl.opengl.GL15.glGenBuffers;

import de.javagl.jgltf.model.GltfModel;
import de.javagl.jgltf.model.MaterialModel;
import de.javagl.jgltf.model.MeshModel;
import de.javagl.jgltf.model.MeshPrimitiveModel;
import de.javagl.jgltf.model.NodeModel;
import de.javagl.jgltf.model.SceneModel;
import de.javagl.jgltf.model.animation.AnimationManager;
import de.javagl.jgltf.model.io.GltfModelReader;
import de.javagl.jgltf.model.GltfAnimations;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import ru.reweu.game.gltf.GltfPrimitiveBuilder.GltfMeshDraw;
import ru.reweu.game.render.ShaderProgram;

/**
 * glTF 2.0 scene: native jgltf graph, GPU primitives, optional skinning/morph/animation.
 */
public final class GltfScene {

    /**
     * jgltf 2.0.4 exposes stepping only via package-private {@code performStep(long)}.
     * Resolved once; if the API changes, animations are skipped instead of crashing the JVM.
     */
    private static final Method ANIMATION_PERFORM_STEP = resolveAnimationPerformStep();

    private static Method resolveAnimationPerformStep() {
        try {
            Method m = AnimationManager.class.getDeclaredMethod("performStep", long.class);
            m.setAccessible(true);
            return m;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private final GltfModel gltfModel;
    private final GltfMaterialExtensionFlags materialExtensionFlags;
    /** Индекс материала по ссылке (jgltf), без {@code List#indexOf} при сортировке примитивов. */
    private final Map<MaterialModel, Integer> materialIndexByIdentity;
    private final GltfTextureRegistry textures;
    private final List<GltfDrawableInstance> instances = new ArrayList<>();
    private final int jointUbo;
    private final AnimationManager animationManager;
    private boolean cleanedUp;

    private final float[] nodeGlobalArr = new float[16];
    private final Matrix4f tmpWorld = new Matrix4f();
    private final Matrix4f tmpModel = new Matrix4f();
    private final Matrix4f[] instBatchMatrices;

    public GltfScene(GltfModel model, GltfMaterialExtensionFlags materialExtensionFlags) {
        this.instBatchMatrices = new Matrix4f[GltfPbrRenderer.MAX_GLTF_INSTANCED_BATCH];
        for (int i = 0; i < instBatchMatrices.length; i++) {
            instBatchMatrices[i] = new Matrix4f();
        }
        this.gltfModel = model;
        this.materialExtensionFlags = materialExtensionFlags != null
            ? materialExtensionFlags
            : GltfMaterialExtensionFlags.empty();
        List<MaterialModel> matList = model.getMaterialModels();
        this.materialIndexByIdentity = new IdentityHashMap<>(Math.max(16, matList.size() * 2));
        for (int i = 0; i < matList.size(); i++) {
            this.materialIndexByIdentity.put(matList.get(i), i);
        }
        this.textures = new GltfTextureRegistry(model);
        this.jointUbo = glGenBuffers();
        glBindBuffer(GL_UNIFORM_BUFFER, jointUbo);
        glBufferData(GL_UNIFORM_BUFFER, 64 * 4 * 16, GL_DYNAMIC_DRAW);
        glBindBuffer(GL_UNIFORM_BUFFER, 0);

        for (SceneModel sm : model.getSceneModels()) {
            for (NodeModel root : sm.getNodeModels()) {
                collectPrimitives(root);
            }
        }

        instances.sort(
            Comparator
                .comparingInt((GltfDrawableInstance inst) ->
                    inst.mesh.materialModelIndex >= 0 ? inst.mesh.materialModelIndex : Integer.MAX_VALUE)
                .thenComparingInt(inst -> System.identityHashCode(inst.mesh)));

        this.animationManager = GltfAnimations.createAnimationManager(AnimationManager.AnimationPolicy.LOOP);
        this.animationManager.addAnimations(GltfAnimations.createModelAnimations(model.getAnimationModels()));

        this.textures.preflightDecodeAll();
    }

    public static GltfScene load(Path path) throws IOException {
        GltfModel m = new GltfModelReader().read(path);
        GltfMaterialExtensionFlags ext = GltfMaterialExtensions.readFromGlb(path);
        return new GltfScene(m, ext);
    }

    /**
     * Наибольшая длина ребра AABB геометрии (bind pose, глобальные матрицы узлов).
     * Нужно, чтобы подобрать {@code root scale} так, чтобы разные GLB визуально совпадали по габариту.
     */
    public static float boundingMaxEdgeLength(GltfScene scene) {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        float[] ng = new float[16];
        Matrix4f nodeMat = new Matrix4f();
        Vector3f v = new Vector3f();
        for (GltfDrawableInstance inst : scene.getInstances()) {
            float[] pos = inst.mesh.cpuPositions;
            if (pos == null || pos.length < 3) {
                continue;
            }
            inst.node.computeGlobalTransform(ng);
            nodeMat.set(ng);
            for (int i = 0; i + 2 < pos.length; i += 3) {
                v.set(pos[i], pos[i + 1], pos[i + 2]);
                nodeMat.transformPosition(v);
                minX = Math.min(minX, v.x);
                minY = Math.min(minY, v.y);
                minZ = Math.min(minZ, v.z);
                maxX = Math.max(maxX, v.x);
                maxY = Math.max(maxY, v.y);
                maxZ = Math.max(maxZ, v.z);
            }
        }
        if (!Float.isFinite(minX)) {
            return 1f;
        }
        float dx = maxX - minX;
        float dy = maxY - minY;
        float dz = maxZ - minZ;
        return Math.max(dx, Math.max(dy, dz));
    }

    private void collectPrimitives(NodeModel node) {
        for (MeshModel mesh : node.getMeshModels()) {
            for (MeshPrimitiveModel prim : mesh.getMeshPrimitiveModels()) {
                MaterialModel pmat = prim.getMaterialModel();
                Integer mix = pmat != null ? materialIndexByIdentity.get(pmat) : null;
                int matIx = mix != null ? mix : -1;
                GltfMeshDraw draw = GltfPrimitiveBuilder.build(prim, matIx);
                instances.add(new GltfDrawableInstance(draw, node, mesh));
            }
        }
        for (NodeModel c : node.getChildren()) {
            collectPrimitives(c);
        }
    }

    /** Карта теней: отрисовка глубины glTF. */
    public void renderShadowDepth(ShaderProgram depthShader, Matrix4f rootModel, Matrix4f lightSpaceMatrix) {
        int i = 0;
        int n = instances.size();
        while (i < n) {
            GltfDrawableInstance inst = instances.get(i);
            if (!canBatchInstancing(inst)) {
                drawOneShadow(depthShader, rootModel, lightSpaceMatrix, inst);
                i++;
                continue;
            }
            GltfMeshDraw mesh0 = inst.mesh;
            int j = i + 1;
            while (j < n) {
                GltfDrawableInstance next = instances.get(j);
                if (next.mesh != mesh0 || !canBatchInstancing(next)) {
                    break;
                }
                if (j - i >= GltfPbrRenderer.MAX_GLTF_INSTANCED_BATCH) {
                    break;
                }
                j++;
            }
            int batchLen = j - i;
            if (batchLen < 2) {
                drawOneShadow(depthShader, rootModel, lightSpaceMatrix, inst);
                i++;
            } else {
                for (int k = 0; k < batchLen; k++) {
                    GltfDrawableInstance di = instances.get(i + k);
                    di.node.computeGlobalTransform(nodeGlobalArr);
                    tmpWorld.set(nodeGlobalArr);
                    instBatchMatrices[k].set(rootModel).mul(tmpWorld);
                }
                GltfDrawableInstance first = instances.get(i);
                GltfPbrRenderer.drawShadowDepthInstanced(
                    depthShader,
                    jointUbo,
                    first.mesh,
                    first.node,
                    first.meshModel,
                    instBatchMatrices,
                    batchLen,
                    lightSpaceMatrix
                );
                i = j;
            }
        }
    }

    private void drawOneShadow(
        ShaderProgram depthShader,
        Matrix4f rootModel,
        Matrix4f lightSpaceMatrix,
        GltfDrawableInstance inst
    ) {
        inst.node.computeGlobalTransform(nodeGlobalArr);
        tmpWorld.set(nodeGlobalArr);
        tmpModel.set(rootModel).mul(tmpWorld);
        GltfPbrRenderer.drawShadowDepth(
            depthShader,
            jointUbo,
            inst.mesh,
            inst.node,
            inst.meshModel,
            tmpModel,
            lightSpaceMatrix,
            null
        );
    }

    public void updateAnimation(float deltaTimeSeconds) {
        Method step = ANIMATION_PERFORM_STEP;
        if (step == null) {
            return;
        }
        long deltaNs = (long) (deltaTimeSeconds * 1_000_000_000L);
        try {
            step.invoke(animationManager, deltaNs);
        } catch (ReflectiveOperationException ignored) {
            /* Broken jgltf build or security manager: freeze pose rather than abort the frame. */
        }
    }

    /**
     * Требует предварительного {@link ru.reweu.game.render.WorldRenderer#prepareFrameFor} с тем же
     * {@link ru.reweu.game.render.LightingFrame} на кадр и тем же {@code shader} (свет, камера, тени, IBL).
     *
     * @param opaquePassOnly {@code true}: OPAQUE + MASK; {@code false}: BLEND only
     */
    public void render(
        ShaderProgram shader,
        Matrix4f rootModel,
        Matrix4f view,
        Matrix4f projection,
        boolean opaquePassOnly
    ) {
        int i = 0;
        int n = instances.size();
        while (i < n) {
            GltfDrawableInstance inst = instances.get(i);
            if (!instancePassMatches(opaquePassOnly, inst)) {
                i++;
                continue;
            }
            if (!canBatchInstancing(inst)) {
                drawOneForward(shader, rootModel, view, projection, inst);
                i++;
                continue;
            }
            GltfMeshDraw mesh0 = inst.mesh;
            int j = i + 1;
            while (j < n) {
                GltfDrawableInstance next = instances.get(j);
                if (!instancePassMatches(opaquePassOnly, next)) {
                    break;
                }
                if (next.mesh != mesh0 || !canBatchInstancing(next)) {
                    break;
                }
                if (j - i >= GltfPbrRenderer.MAX_GLTF_INSTANCED_BATCH) {
                    break;
                }
                j++;
            }
            int batchLen = j - i;
            if (batchLen < 2) {
                drawOneForward(shader, rootModel, view, projection, inst);
                i++;
            } else {
                for (int k = 0; k < batchLen; k++) {
                    GltfDrawableInstance di = instances.get(i + k);
                    di.node.computeGlobalTransform(nodeGlobalArr);
                    tmpWorld.set(nodeGlobalArr);
                    instBatchMatrices[k].set(rootModel).mul(tmpWorld);
                }
                GltfDrawableInstance first = instances.get(i);
                GltfPbrRenderer.drawInstancedForward(
                    shader,
                    textures,
                    jointUbo,
                    first.mesh,
                    first.node,
                    first.meshModel,
                    instBatchMatrices,
                    batchLen,
                    view,
                    projection,
                    gltfModel,
                    materialExtensionFlags
                );
                i = j;
            }
        }
    }

    private static boolean instancePassMatches(boolean opaquePassOnly, GltfDrawableInstance inst) {
        boolean opaquePass = GltfMaterialPasses.drawnInOpaquePass(inst.mesh.material);
        if (opaquePassOnly && !opaquePass) {
            return false;
        }
        if (!opaquePassOnly && opaquePass) {
            return false;
        }
        return true;
    }

    private static boolean canBatchInstancing(GltfDrawableInstance inst) {
        return inst.mesh.morphGpuCount == 0 && inst.node.getSkinModel() == null;
    }

    private void drawOneForward(
        ShaderProgram shader,
        Matrix4f rootModel,
        Matrix4f view,
        Matrix4f projection,
        GltfDrawableInstance inst
    ) {
        inst.node.computeGlobalTransform(nodeGlobalArr);
        tmpWorld.set(nodeGlobalArr);
        tmpModel.set(rootModel).mul(tmpWorld);
        GltfPbrRenderer.draw(
            shader,
            textures,
            jointUbo,
            inst.mesh,
            inst.node,
            inst.meshModel,
            tmpModel,
            view,
            projection,
            null,
            gltfModel,
            materialExtensionFlags
        );
    }

    public void cleanup() {
        if (cleanedUp) {
            return;
        }
        cleanedUp = true;
        for (GltfDrawableInstance i : instances) {
            GltfPrimitiveBuilder.delete(i.mesh);
        }
        textures.cleanup();
        glDeleteBuffers(jointUbo);
    }

    public List<GltfDrawableInstance> getInstances() {
        return instances;
    }

    public static final class GltfDrawableInstance {
        public final GltfMeshDraw mesh;
        public final NodeModel node;
        public final MeshModel meshModel;

        GltfDrawableInstance(GltfMeshDraw mesh, NodeModel node, MeshModel meshModel) {
            this.mesh = mesh;
            this.node = node;
            this.meshModel = meshModel;
        }
    }
}
