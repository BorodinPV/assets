package ru.reweu.game.gltf;

import static org.lwjgl.opengl.GL30.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL30.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL30.GL_FLOAT;
import static org.lwjgl.opengl.GL30.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL30.GL_TRIANGLES;
import static org.lwjgl.opengl.GL30.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL30.GL_UNSIGNED_SHORT;
import static org.lwjgl.opengl.GL30.glBindBuffer;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glBufferData;
import static org.lwjgl.opengl.GL30.glDeleteBuffers;
import static org.lwjgl.opengl.GL30.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL30.glGenBuffers;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;
import static org.lwjgl.opengl.GL30.glVertexAttribPointer;

import de.javagl.jgltf.model.AccessorModel;
import de.javagl.jgltf.model.MaterialModel;
import de.javagl.jgltf.model.MeshPrimitiveModel;
import java.util.IdentityHashMap;
import de.javagl.jgltf.model.v2.MaterialModelV2;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.lwjgl.system.MemoryUtil;

/**
 * Builds interleaved VAO from {@link MeshPrimitiveModel} (tangents, optional morph deltas for first 4 targets).
 */
public final class GltfPrimitiveBuilder {

    public static final int MAX_MORPH_TARGETS = 4;
    private static final int FLOATS_BASE = 3 + 3 + 4 + 2 + 2 + 4 + 4 + 4;
    private static final int FLOATS_MORPH = MAX_MORPH_TARGETS * 3;

    private GltfPrimitiveBuilder() {
    }

    /**
     * Один VAO на {@link MeshPrimitiveModel} (общие узлы/меши в glTF). Счётчик ссылок в {@link GltfMeshDraw#shareCount}.
     */
    private static final IdentityHashMap<MeshPrimitiveModel, GltfMeshDraw> PRIMITIVE_GPU_CACHE = new IdentityHashMap<>();

    /**
     * @param materialModelIndex индекс в {@link de.javagl.jgltf.model.GltfModel#getMaterialModels()} или {@code -1}
     */
    public static GltfMeshDraw build(MeshPrimitiveModel prim, int materialModelIndex) {
        synchronized (PRIMITIVE_GPU_CACHE) {
            GltfMeshDraw cached = PRIMITIVE_GPU_CACHE.get(prim);
            if (cached != null) {
                cached.shareCount++;
                return cached;
            }
            GltfMeshDraw d = buildNew(prim, materialModelIndex);
            PRIMITIVE_GPU_CACHE.put(prim, d);
            return d;
        }
    }

    private static GltfMeshDraw buildNew(MeshPrimitiveModel prim, int materialModelIndex) {
        AccessorModel posAcc = prim.getAttributes().get("POSITION");
        if (posAcc == null) {
            throw new IllegalArgumentException("POSITION required");
        }
        float[] positions = GltfAccessorReader.readFloats(posAcc);
        int vertexCount = posAcc.getCount();

        float[] normals = GltfAccessorReader.readFloats(prim.getAttributes().get("NORMAL"));
        if (normals == null || normals.length < vertexCount * 3) {
            normals = new float[vertexCount * 3];
        }

        float[] tangents = GltfAccessorReader.readFloats(prim.getAttributes().get("TANGENT"));
        if (tangents == null || tangents.length < vertexCount * 4) {
            tangents = new float[vertexCount * 4];
        }

        float[] uv0 = GltfAccessorReader.readFloats(prim.getAttributes().get("TEXCOORD_0"));
        if (uv0 == null || uv0.length < vertexCount * 2) {
            uv0 = new float[vertexCount * 2];
        }
        float[] uv1 = GltfAccessorReader.readFloats(prim.getAttributes().get("TEXCOORD_1"));
        if (uv1 == null || uv1.length < vertexCount * 2) {
            uv1 = new float[vertexCount * 2];
        }

        float[] colors = GltfAccessorReader.readFloats(prim.getAttributes().get("COLOR_0"));
        if (colors == null || colors.length < vertexCount * 4) {
            colors = new float[vertexCount * 4];
            for (int i = 0; i < vertexCount; i++) {
                colors[i * 4] = 1f;
                colors[i * 4 + 1] = 1f;
                colors[i * 4 + 2] = 1f;
                colors[i * 4 + 3] = 1f;
            }
        }

        float[] joints = GltfAccessorReader.readJointsAsFloats(prim.getAttributes().get("JOINTS_0"));
        if (joints == null || joints.length < vertexCount * 4) {
            joints = new float[vertexCount * 4];
        }
        float[] weights = GltfAccessorReader.readFloats(prim.getAttributes().get("WEIGHTS_0"));
        if (weights == null || weights.length < vertexCount * 4) {
            weights = new float[vertexCount * 4];
            for (int i = 0; i < vertexCount; i++) {
                weights[i * 4] = 1f;
            }
        }

        int[] indices = GltfAccessorReader.readIndices(prim.getIndices());
        if (indices == null) {
            throw new IllegalArgumentException("indices required");
        }

        if (normals == null || allZeroNormals(normals, vertexCount)) {
            GltfNormals.computeSmoothNormals(positions, indices, vertexCount, normals);
        }

        if (allZeroTangents(tangents, vertexCount)) {
            TangentSpace.computeTangentSpace(positions, normals, uv0, vertexCount, indices, tangents);
        }

        List<Map<String, AccessorModel>> targets = prim.getTargets();
        int morphCount = targets == null ? 0 : Math.min(MAX_MORPH_TARGETS, targets.size());
        float[][] morphDeltas = new float[MAX_MORPH_TARGETS][];
        for (int t = 0; t < morphCount; t++) {
            AccessorModel deltaPos = targets.get(t).get("POSITION");
            float[] d = GltfAccessorReader.readFloats(deltaPos);
            if (d == null || d.length < vertexCount * 3) {
                morphDeltas[t] = new float[vertexCount * 3];
            } else {
                morphDeltas[t] = d;
            }
        }
        for (int t = morphCount; t < MAX_MORPH_TARGETS; t++) {
            morphDeltas[t] = new float[vertexCount * 3];
        }

        int strideFloats = FLOATS_BASE + FLOATS_MORPH;
        float[] interleaved = new float[vertexCount * strideFloats];
        for (int v = 0; v < vertexCount; v++) {
            int o = v * strideFloats;
            copy3(interleaved, o, positions, v);
            copy3(interleaved, o + 3, normals, v);
            copy4(interleaved, o + 6, tangents, v);
            copy2(interleaved, o + 10, uv0, v);
            copy2(interleaved, o + 12, uv1, v);
            copy4(interleaved, o + 14, colors, v);
            copy4(interleaved, o + 18, joints, v);
            copy4(interleaved, o + 22, weights, v);
            int mo = o + 26;
            for (int t = 0; t < MAX_MORPH_TARGETS; t++) {
                copy3(interleaved, mo + t * 3, morphDeltas[t], v);
            }
        }

        int maxIndex = 0;
        for (int idx : indices) {
            maxIndex = Math.max(maxIndex, idx);
        }
        int indexGlType = maxIndex > 65535 ? GL_UNSIGNED_INT : GL_UNSIGNED_SHORT;

        int vao = glGenVertexArrays();
        int vbo = glGenBuffers();
        int ebo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        FloatBuffer fb = MemoryUtil.memAllocFloat(interleaved.length);
        fb.put(interleaved).flip();
        glBufferData(GL_ARRAY_BUFFER, fb, GL_STATIC_DRAW);
        MemoryUtil.memFree(fb);

        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        if (indexGlType == GL_UNSIGNED_INT) {
            java.nio.IntBuffer ib = MemoryUtil.memAllocInt(indices.length);
            ib.put(indices).flip();
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, ib, GL_STATIC_DRAW);
            MemoryUtil.memFree(ib);
        } else {
            java.nio.ShortBuffer sb = MemoryUtil.memAllocShort(indices.length);
            for (int idx : indices) {
                sb.put((short) (idx & 0xFFFF));
            }
            sb.flip();
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, sb, GL_STATIC_DRAW);
            MemoryUtil.memFree(sb);
        }

        int strideBytes = strideFloats * Float.BYTES;
        int offset = 0;
        glVertexAttribPointer(0, 3, GL_FLOAT, false, strideBytes, offset);
        glEnableVertexAttribArray(0);
        offset += 3 * Float.BYTES;
        glVertexAttribPointer(1, 3, GL_FLOAT, false, strideBytes, offset);
        glEnableVertexAttribArray(1);
        offset += 3 * Float.BYTES;
        glVertexAttribPointer(2, 4, GL_FLOAT, false, strideBytes, offset);
        glEnableVertexAttribArray(2);
        offset += 4 * Float.BYTES;
        glVertexAttribPointer(3, 2, GL_FLOAT, false, strideBytes, offset);
        glEnableVertexAttribArray(3);
        offset += 2 * Float.BYTES;
        glVertexAttribPointer(4, 2, GL_FLOAT, false, strideBytes, offset);
        glEnableVertexAttribArray(4);
        offset += 2 * Float.BYTES;
        glVertexAttribPointer(5, 4, GL_FLOAT, false, strideBytes, offset);
        glEnableVertexAttribArray(5);
        offset += 4 * Float.BYTES;
        glVertexAttribPointer(6, 4, GL_FLOAT, false, strideBytes, offset);
        glEnableVertexAttribArray(6);
        offset += 4 * Float.BYTES;
        glVertexAttribPointer(7, 4, GL_FLOAT, false, strideBytes, offset);
        glEnableVertexAttribArray(7);
        offset += 4 * Float.BYTES;
        for (int t = 0; t < MAX_MORPH_TARGETS; t++) {
            glVertexAttribPointer(8 + t, 3, GL_FLOAT, false, strideBytes, offset);
            glEnableVertexAttribArray(8 + t);
            offset += 3 * Float.BYTES;
        }

        glBindVertexArray(0);

        MaterialModel mm = prim.getMaterialModel();
        MaterialModelV2 mat;
        if (mm instanceof MaterialModelV2) {
            mat = (MaterialModelV2) mm;
        } else {
            mat = new MaterialModelV2();
        }

        float[] cpuPos = Arrays.copyOf(positions, positions.length);
        int[] cpuIdx = Arrays.copyOf(indices, indices.length);
        return new GltfMeshDraw(
            vao,
            vbo,
            ebo,
            indices.length,
            indexGlType,
            mat,
            materialModelIndex,
            morphCount,
            targets != null ? targets.size() : 0,
            cpuPos,
            cpuIdx,
            prim
        );
    }

    private static boolean allZeroNormals(float[] n, int vc) {
        for (int i = 0; i < vc * 3; i++) {
            if (Math.abs(n[i]) > 1e-6f) {
                return false;
            }
        }
        return true;
    }

    private static boolean allZeroTangents(float[] t, int vc) {
        for (int i = 0; i < vc * 4; i++) {
            if (Math.abs(t[i]) > 1e-6f) {
                return false;
            }
        }
        return true;
    }

    private static void copy3(float[] dst, int dstOff, float[] src, int vi) {
        dst[dstOff] = src[vi * 3];
        dst[dstOff + 1] = src[vi * 3 + 1];
        dst[dstOff + 2] = src[vi * 3 + 2];
    }

    private static void copy4(float[] dst, int dstOff, float[] src, int vi) {
        dst[dstOff] = src[vi * 4];
        dst[dstOff + 1] = src[vi * 4 + 1];
        dst[dstOff + 2] = src[vi * 4 + 2];
        dst[dstOff + 3] = src[vi * 4 + 3];
    }

    private static void copy2(float[] dst, int dstOff, float[] src, int vi) {
        dst[dstOff] = src[vi * 2];
        dst[dstOff + 1] = src[vi * 2 + 1];
    }

    public static void delete(GltfMeshDraw d) {
        if (d == null) {
            return;
        }
        synchronized (PRIMITIVE_GPU_CACHE) {
            if (d.sharedPrimitiveKey != null) {
                d.shareCount--;
                if (d.shareCount > 0) {
                    return;
                }
                PRIMITIVE_GPU_CACHE.remove(d.sharedPrimitiveKey);
            }
            glDeleteVertexArrays(d.vao);
            glDeleteBuffers(d.vbo);
            glDeleteBuffers(d.ebo);
        }
    }

    public static final class GltfMeshDraw {
        public final int vao;
        public final int vbo;
        public final int ebo;
        public final int indexCount;
        public final int indexGlType;
        public final MaterialModelV2 material;
        /** Индекс в {@code model.getMaterialModels()} для расширений/эвристик без {@code indexOf} на кадр. */
        public final int materialModelIndex;
        /** Number of morph targets with GPU attributes (0–4). */
        public final int morphGpuCount;
        /** Total morph targets in primitive (may exceed GPU cap). */
        public final int morphTotalCount;

        /**
         * Копия POSITION и индексов примитива (bind pose) для CPU-трассировки.
         * Скиннинг в RT не учитывается — только мировая матрица узла.
         */
        public final float[] cpuPositions;
        public final int[] cpuIndices;

        /** Ключ кэша GPU; один объект примитива — один VAO. */
        public final MeshPrimitiveModel sharedPrimitiveKey;
        /** Число владельцев ({@link GltfScene.GltfDrawableInstance}); уменьшается в {@link GltfPrimitiveBuilder#delete}. */
        public int shareCount;

        GltfMeshDraw(
            int vao,
            int vbo,
            int ebo,
            int indexCount,
            int indexGlType,
            MaterialModelV2 material,
            int materialModelIndex,
            int morphGpuCount,
            int morphTotalCount,
            float[] cpuPositions,
            int[] cpuIndices,
            MeshPrimitiveModel sharedPrimitiveKey
        ) {
            this.vao = vao;
            this.vbo = vbo;
            this.ebo = ebo;
            this.indexCount = indexCount;
            this.indexGlType = indexGlType;
            this.material = material;
            this.materialModelIndex = materialModelIndex;
            this.morphGpuCount = morphGpuCount;
            this.morphTotalCount = morphTotalCount;
            this.cpuPositions = cpuPositions;
            this.cpuIndices = cpuIndices;
            this.sharedPrimitiveKey = sharedPrimitiveKey;
            this.shareCount = 1;
        }
    }
}
