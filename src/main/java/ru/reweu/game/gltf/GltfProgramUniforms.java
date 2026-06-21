package ru.reweu.game.gltf;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import ru.reweu.game.render.ShaderProgram;

/** Кэш location'ов glTF/PBR и depth-шейдеров (на программу). */
final class GltfProgramUniforms {

    private static final Map<Integer, GltfProgramUniforms> BY_PROGRAM = new ConcurrentHashMap<>();

    final int programId;

    final int uInstancedBatchCount;
    final int uUseSkinning;
    final int uMorphWeightsA;
    final int uMorphCount;
    final int uBaseColorFactor;
    final int uBaseColorTexCoord;
    final int uMetallicRoughnessTexCoord;
    final int uNormalTexCoord;
    final int uOcclusionTexCoord;
    final int uEmissiveTexCoord;
    final int uMetallicFactor;
    final int uRoughnessFactor;
    final int uNormalScale;
    final int uOcclusionStrength;
    final int uEmissiveFactor;
    final int uAlphaMode;
    final int uAlphaCutoff;
    final int uDoubleSided;
    final int uThinGlass;
    final int uDebugVisualizeMode;
    final int uEnableNormalTexTransform;
    final int uNormalTexTransformOffset;
    final int uNormalTexTransformScale;
    final int uNormalTexTransformRotation;
    final int uBaseColor;
    final int uHasBaseColor;
    final int uMetallicRoughness;
    final int uHasMetallicRoughness;
    final int uNormal;
    final int uHasNormal;
    final int uOcclusion;
    final int uHasOcclusion;
    final int uEmissive;
    final int uHasEmissive;
    final int model;
    final int view;
    final int projection;
    final int lightSpaceMatrix;
    final int[] uModelBatch;

    private GltfProgramUniforms(ShaderProgram shader) {
        programId = shader.getProgramId();
        uModelBatch = shader.uniformMat4ArrayLocations("u_modelBatch", GltfPbrRenderer.MAX_GLTF_INSTANCED_BATCH);
        uInstancedBatchCount = loc(shader, "u_instancedBatchCount");
        uUseSkinning = loc(shader, "u_useSkinning");
        uMorphWeightsA = loc(shader, "u_morphWeightsA");
        uMorphCount = loc(shader, "u_morphCount");
        uBaseColorFactor = loc(shader, "u_baseColorFactor");
        uBaseColorTexCoord = loc(shader, "u_baseColorTexCoord");
        uMetallicRoughnessTexCoord = loc(shader, "u_metallicRoughnessTexCoord");
        uNormalTexCoord = loc(shader, "u_normalTexCoord");
        uOcclusionTexCoord = loc(shader, "u_occlusionTexCoord");
        uEmissiveTexCoord = loc(shader, "u_emissiveTexCoord");
        uMetallicFactor = loc(shader, "u_metallicFactor");
        uRoughnessFactor = loc(shader, "u_roughnessFactor");
        uNormalScale = loc(shader, "u_normalScale");
        uOcclusionStrength = loc(shader, "u_occlusionStrength");
        uEmissiveFactor = loc(shader, "u_emissiveFactor");
        uAlphaMode = loc(shader, "u_alphaMode");
        uAlphaCutoff = loc(shader, "u_alphaCutoff");
        uDoubleSided = loc(shader, "u_doubleSided");
        uThinGlass = loc(shader, "u_thinGlass");
        uDebugVisualizeMode = loc(shader, "u_debugVisualizeMode");
        uEnableNormalTexTransform = loc(shader, "u_enableNormalTexTransform");
        uNormalTexTransformOffset = loc(shader, "u_normalTexTransformOffset");
        uNormalTexTransformScale = loc(shader, "u_normalTexTransformScale");
        uNormalTexTransformRotation = loc(shader, "u_normalTexTransformRotation");
        uBaseColor = loc(shader, "u_baseColor");
        uHasBaseColor = loc(shader, "u_hasBaseColor");
        uMetallicRoughness = loc(shader, "u_metallicRoughness");
        uHasMetallicRoughness = loc(shader, "u_hasMetallicRoughness");
        uNormal = loc(shader, "u_normal");
        uHasNormal = loc(shader, "u_hasNormal");
        uOcclusion = loc(shader, "u_occlusion");
        uHasOcclusion = loc(shader, "u_hasOcclusion");
        uEmissive = loc(shader, "u_emissive");
        uHasEmissive = loc(shader, "u_hasEmissive");
        model = loc(shader, "model");
        view = loc(shader, "view");
        projection = loc(shader, "projection");
        lightSpaceMatrix = loc(shader, "lightSpaceMatrix");
    }

    int programId() {
        return programId;
    }

    static GltfProgramUniforms forProgram(ShaderProgram shader) {
        return BY_PROGRAM.computeIfAbsent(shader.getProgramId(), id -> new GltfProgramUniforms(shader));
    }

    static void removeForProgram(int programId) {
        BY_PROGRAM.remove(programId);
    }

    private static int loc(ShaderProgram shader, String name) {
        return shader.uniformLocation(name);
    }
}
