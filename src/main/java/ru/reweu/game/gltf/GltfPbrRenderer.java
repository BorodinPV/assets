package ru.reweu.game.gltf;

import static org.lwjgl.opengl.GL11.GL_CCW;
import static org.lwjgl.opengl.GL11.GL_CULL_FACE;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glFrontFace;
import static org.lwjgl.opengl.GL11.glIsEnabled;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL31.GL_UNIFORM_BUFFER;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferSubData;
import static org.lwjgl.opengl.GL20.glUniform1f;
import static org.lwjgl.opengl.GL20.glUniform1i;
import static org.lwjgl.opengl.GL20.glUniform2f;
import static org.lwjgl.opengl.GL20.glUniform3f;
import static org.lwjgl.opengl.GL20.glUniform4f;
import static org.lwjgl.opengl.GL30.glBindBufferBase;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glDrawElements;
import static org.lwjgl.opengl.GL31.glDrawElementsInstanced;
import static org.lwjgl.opengl.GL31.glGetUniformBlockIndex;
import static org.lwjgl.opengl.GL31.glUniformBlockBinding;
import static org.lwjgl.system.MemoryStack.stackPush;

import de.javagl.jgltf.model.GltfModel;
import de.javagl.jgltf.model.MeshModel;
import de.javagl.jgltf.model.NodeModel;
import de.javagl.jgltf.model.SkinModel;
import de.javagl.jgltf.model.v2.MaterialModelV2;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import ru.reweu.game.gltf.GltfPrimitiveBuilder.GltfMeshDraw;
import ru.reweu.game.loader.Texture;
import ru.reweu.game.render.ShaderProgram;

/**
 * Отрисовка примитива glTF: скиннинг (UBO), морфинг, материал PBR (§3.9).
 * Per-frame солнце/камера/IBL/тени задаются до вызова {@link #draw} через
 * {@link ru.reweu.game.render.WorldRenderer#prepareFrameFor}.
 */
public final class GltfPbrRenderer {

    /** Совпадает с {@code u_modelBatch[64]} в шейдерах. */
    public static final int MAX_GLTF_INSTANCED_BATCH = 64;

    private static final class LastMaterial {
        int programId = -1;
        int materialIndex = Integer.MIN_VALUE;
    }

    private static final LastMaterial LAST_MATERIAL = new LastMaterial();

    private GltfPbrRenderer() {
    }

    public static void removeUniformCacheForProgram(int programId) {
        GltfProgramUniforms.removeForProgram(programId);
    }

    public static void initJointBlock(ShaderProgram shader) {
        int pid = shader.getProgramId();
        int blockIndex = glGetUniformBlockIndex(pid, "JointBlock");
        if (blockIndex != -1) {
            glUniformBlockBinding(pid, blockIndex, 0);
        }
    }

    public static void draw(
        ShaderProgram shader,
        GltfTextureRegistry textures,
        int jointUbo,
        GltfMeshDraw mesh,
        NodeModel node,
        MeshModel meshModel,
        Matrix4f modelMatrix,
        Matrix4f view,
        Matrix4f projection,
        float[] morphWeightsOverride,
        GltfModel gltfModel,
        GltfMaterialExtensionFlags materialExtensionFlags,
        GltfRenderConfig config
    ) {
        GltfProgramUniforms u = GltfProgramUniforms.forProgram(shader);
        shader.use();
        setInt(u.uInstancedBatchCount, 0);
        uploadJointBufferMorphAndSkin(u, jointUbo, mesh, node, meshModel, morphWeightsOverride);
        shader.setUniformMat4At(u.model, modelMatrix);
        shader.setUniformMat4At(u.view, view);
        shader.setUniformMat4At(u.projection, projection);
        bindMaterial(u, textures, mesh.material, mesh.materialModelIndex, gltfModel, materialExtensionFlags, config);
        drawIndexedDoubleSided(mesh);
    }

    /** Проход глубины для карты теней (без материалов). */
    public static void drawShadowDepth(
        ShaderProgram depthShader,
        int jointUbo,
        GltfMeshDraw mesh,
        NodeModel node,
        MeshModel meshModel,
        Matrix4f modelMatrix,
        Matrix4f lightSpaceMatrix,
        float[] morphWeightsOverride
    ) {
        GltfProgramUniforms u = GltfProgramUniforms.forProgram(depthShader);
        depthShader.use();
        setInt(u.uInstancedBatchCount, 0);
        uploadJointBufferMorphAndSkin(u, jointUbo, mesh, node, meshModel, morphWeightsOverride);
        depthShader.setUniformMat4At(u.model, modelMatrix);
        depthShader.setUniformMat4At(u.lightSpaceMatrix, lightSpaceMatrix);
        drawIndexedDoubleSided(mesh);
    }

    public static void drawInstancedForward(
        ShaderProgram shader,
        GltfTextureRegistry textures,
        int jointUbo,
        GltfMeshDraw mesh,
        NodeModel firstNode,
        MeshModel firstMeshModel,
        Matrix4f[] modelMatrices,
        int count,
        Matrix4f view,
        Matrix4f projection,
        GltfModel gltfModel,
        GltfMaterialExtensionFlags materialExtensionFlags,
        GltfRenderConfig config
    ) {
        if (count <= 0 || count > MAX_GLTF_INSTANCED_BATCH) {
            return;
        }
        GltfProgramUniforms u = GltfProgramUniforms.forProgram(shader);
        shader.use();
        setInt(u.uInstancedBatchCount, 0);
        uploadJointBufferMorphAndSkin(u, jointUbo, mesh, firstNode, firstMeshModel, null);
        shader.setUniformMat4At(u.view, view);
        shader.setUniformMat4At(u.projection, projection);
        bindMaterial(u, textures, mesh.material, mesh.materialModelIndex, gltfModel, materialExtensionFlags, config);
        setInt(u.uInstancedBatchCount, count);
        shader.setUniformMat4ArrayCached(u.uModelBatch, modelMatrices, count);
        drawIndexedDoubleSidedInstanced(mesh, count);
        setInt(u.uInstancedBatchCount, 0);
    }

    public static void drawShadowDepthInstanced(
        ShaderProgram depthShader,
        int jointUbo,
        GltfMeshDraw mesh,
        NodeModel firstNode,
        MeshModel firstMeshModel,
        Matrix4f[] modelMatrices,
        int count,
        Matrix4f lightSpaceMatrix
    ) {
        if (count <= 0 || count > MAX_GLTF_INSTANCED_BATCH) {
            return;
        }
        GltfProgramUniforms u = GltfProgramUniforms.forProgram(depthShader);
        depthShader.use();
        setInt(u.uInstancedBatchCount, 0);
        uploadJointBufferMorphAndSkin(u, jointUbo, mesh, firstNode, firstMeshModel, null);
        depthShader.setUniformMat4At(u.lightSpaceMatrix, lightSpaceMatrix);
        setInt(u.uInstancedBatchCount, count);
        depthShader.setUniformMat4ArrayCached(u.uModelBatch, modelMatrices, count);
        drawIndexedDoubleSidedInstanced(mesh, count);
        setInt(u.uInstancedBatchCount, 0);
    }

    private static void uploadJointBufferMorphAndSkin(
        GltfProgramUniforms u,
        int jointUbo,
        GltfMeshDraw mesh,
        NodeModel node,
        MeshModel meshModel,
        float[] morphWeightsOverride
    ) {
        SkinModel skin = node.getSkinModel();

        int useSkin;
        try (MemoryStack stack = stackPush()) {
            var fb = stack.mallocFloat(64 * 16);
            int jointCount = GltfSkinMatrices.fillJointBuffer(skin, node, fb);
            useSkin = jointCount > 0 ? 1 : 0;
            glBindBuffer(GL_UNIFORM_BUFFER, jointUbo);
            glBufferSubData(GL_UNIFORM_BUFFER, 0, fb);
            glBindBufferBase(GL_UNIFORM_BUFFER, 0, jointUbo);
        }

        setInt(u.uUseSkinning, useSkin);

        float[] mw = meshModel != null ? meshModel.getWeights() : null;
        if (mw == null) {
            mw = node.getWeights();
        }
        if (morphWeightsOverride != null) {
            mw = morphWeightsOverride;
        }
        int morphN = mesh.morphGpuCount;
        if (mw != null && morphN > 0) {
            setInt(u.uMorphCount, morphN);
            glUniform4f(
                u.uMorphWeightsA,
                mw.length > 0 ? mw[0] : 0f,
                mw.length > 1 ? mw[1] : 0f,
                mw.length > 2 ? mw[2] : 0f,
                mw.length > 3 ? mw[3] : 0f
            );
        } else {
            setInt(u.uMorphCount, 0);
            glUniform4f(u.uMorphWeightsA, 0f, 0f, 0f, 0f);
        }
    }

    private static void drawIndexedDoubleSided(GltfMeshDraw mesh) {
        boolean doubleSided = mesh.material.isDoubleSided();
        boolean cullWasEnabled = false;
        if (doubleSided) {
            cullWasEnabled = glIsEnabled(GL_CULL_FACE);
            glDisable(GL_CULL_FACE);
        }

        glBindVertexArray(mesh.vao);
        glDrawElements(GL_TRIANGLES, mesh.indexCount, mesh.indexGlType, 0);
        glBindVertexArray(0);

        if (doubleSided) {
            if (cullWasEnabled) {
                glEnable(GL_CULL_FACE);
                glFrontFace(GL_CCW);
            } else {
                glDisable(GL_CULL_FACE);
            }
        }
    }

    private static void drawIndexedDoubleSidedInstanced(GltfMeshDraw mesh, int instanceCount) {
        boolean doubleSided = mesh.material.isDoubleSided();
        boolean cullWasEnabled = false;
        if (doubleSided) {
            cullWasEnabled = glIsEnabled(GL_CULL_FACE);
            glDisable(GL_CULL_FACE);
        }

        glBindVertexArray(mesh.vao);
        glDrawElementsInstanced(GL_TRIANGLES, mesh.indexCount, mesh.indexGlType, 0, instanceCount);
        glBindVertexArray(0);

        if (doubleSided) {
            if (cullWasEnabled) {
                glEnable(GL_CULL_FACE);
                glFrontFace(GL_CCW);
            } else {
                glDisable(GL_CULL_FACE);
            }
        }
    }

    private static void bindMaterial(
        GltfProgramUniforms u,
        GltfTextureRegistry tex,
        MaterialModelV2 m,
        int materialModelIndex,
        GltfModel gltfModel,
        GltfMaterialExtensionFlags materialExtensionFlags,
        GltfRenderConfig config
    ) {
        int pid = u.programId();
        if (LAST_MATERIAL.programId == pid && LAST_MATERIAL.materialIndex == materialModelIndex) {
            return;
        }
        LAST_MATERIAL.programId = pid;
        LAST_MATERIAL.materialIndex = materialModelIndex;

        float[] bcf = m.getBaseColorFactor();
        if (bcf == null || bcf.length < 4) {
            glUniform4f(u.uBaseColorFactor, 1f, 1f, 1f, 1f);
        } else {
            glUniform4f(u.uBaseColorFactor, bcf[0], bcf[1], bcf[2], bcf[3]);
        }

        setInt(u.uBaseColorTexCoord, intOrZero(m.getBaseColorTexcoord()));
        setInt(u.uMetallicRoughnessTexCoord, intOrZero(m.getMetallicRoughnessTexcoord()));
        setInt(u.uNormalTexCoord, intOrZero(m.getNormalTexcoord()));
        setInt(u.uOcclusionTexCoord, intOrZero(m.getOcclusionTexcoord()));
        setInt(u.uEmissiveTexCoord, intOrZero(m.getEmissiveTexcoord()));

        glUniform1f(u.uMetallicFactor, m.getMetallicFactor());
        float roughness = m.getRoughnessFactor();
        if (gltfModel != null && materialExtensionFlags != null && materialModelIndex >= 0) {
            if (materialExtensionFlags.needsRoughnessFallback(materialModelIndex)) {
                roughness = Math.max(roughness, config.extensionFallbackMinRoughness());
            }
        }
        glUniform1f(u.uRoughnessFactor, roughness);
        glUniform1f(u.uNormalScale, m.getNormalScale());
        glUniform1f(u.uOcclusionStrength, m.getOcclusionStrength());

        float[] ef = m.getEmissiveFactor();
        boolean hasEmissiveTex = m.getEmissiveTexture() != null;
        float e0 = 0f;
        float e1 = 0f;
        float e2 = 0f;
        if (ef != null && ef.length >= 3) {
            e0 = ef[0];
            e1 = ef[1];
            e2 = ef[2];
        }
        float eRaw0 = e0;
        float eRaw1 = e1;
        float eRaw2 = e2;
        if (hasEmissiveTex && (e0 + e1 + e2) < 1e-5f) {
            e0 = e1 = e2 = 1f;
        }
        glUniform3f(u.uEmissiveFactor, e0, e1, e2);

        MaterialModelV2.AlphaMode am = m.getAlphaMode();
        int alphaMode = 0;
        if (am == MaterialModelV2.AlphaMode.MASK) {
            alphaMode = 1;
        } else if (am == MaterialModelV2.AlphaMode.BLEND) {
            alphaMode = 2;
        }
        setInt(u.uAlphaMode, alphaMode);
        glUniform1f(u.uAlphaCutoff, m.getAlphaCutoff());
        setInt(u.uDoubleSided, m.isDoubleSided() ? 1 : 0);

        boolean thinGlass = GltfThinGlass.shouldUseThinGlass(
            m,
            hasEmissiveTex,
            eRaw0,
            eRaw1,
            eRaw2,
            gltfModel,
            materialExtensionFlags,
            materialModelIndex
        );
        setInt(u.uThinGlass, thinGlass ? 1 : 0);

        bindTex(u, tex, m.getBaseColorTexture(), u.uBaseColor, u.uHasBaseColor, GltfShaderTextureUnits.BASE_COLOR, true);
        bindTex(u, tex, m.getMetallicRoughnessTexture(), u.uMetallicRoughness, u.uHasMetallicRoughness, GltfShaderTextureUnits.METALLIC_ROUGHNESS, false);
        bindTex(u, tex, m.getNormalTexture(), u.uNormal, u.uHasNormal, GltfShaderTextureUnits.NORMAL, false);
        bindTex(u, tex, m.getOcclusionTexture(), u.uOcclusion, u.uHasOcclusion, GltfShaderTextureUnits.OCCLUSION, false);
        bindTex(u, tex, m.getEmissiveTexture(), u.uEmissive, u.uHasEmissive, GltfShaderTextureUnits.EMISSIVE, true);
        if (config.debugDisableNormalMap()) {
            setInt(u.uHasNormal, 0);
        }
        if (u.uDebugVisualizeMode != -1) {
            glUniform1i(u.uDebugVisualizeMode, config.debugVisualizeMode());
        }
        if (u.uEnableNormalTexTransform != -1) {
            glUniform1i(u.uEnableNormalTexTransform, 0);
        }
        if (u.uNormalTexTransformOffset != -1) {
            glUniform2f(u.uNormalTexTransformOffset, 0f, 0f);
        }
        if (u.uNormalTexTransformScale != -1) {
            glUniform2f(u.uNormalTexTransformScale, 1f, 1f);
        }
        if (u.uNormalTexTransformRotation != -1) {
            glUniform1f(u.uNormalTexTransformRotation, 0f);
        }
    }

    private static int intOrZero(Integer v) {
        return v == null ? 0 : v;
    }

    private static void bindTex(
        GltfProgramUniforms u,
        GltfTextureRegistry reg,
        de.javagl.jgltf.model.TextureModel tm,
        int samplerLoc,
        int hasLoc,
        int unit,
        boolean srgb
    ) {
        Texture t = reg.get(tm, srgb);
        int has = t != null ? 1 : 0;
        setInt(hasLoc, has);
        glActiveTexture(GL_TEXTURE0 + unit);
        if (t != null) {
            t.bind();
        } else {
            glBindTexture(GL_TEXTURE_2D, 0);
        }
        if (samplerLoc != -1) {
            glUniform1i(samplerLoc, unit);
        }
    }

    private static void setInt(int loc, int v) {
        if (loc != -1) {
            glUniform1i(loc, v);
        }
    }
}
