package ru.reweu.game.loader;

import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.GL_TEXTURE1;
import static org.lwjgl.opengl.GL13.GL_TEXTURE2;
import static org.lwjgl.opengl.GL13.GL_TEXTURE3;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL20.glUniform1f;
import static org.lwjgl.opengl.GL20.glUniform1i;
import static org.lwjgl.opengl.GL20.glUniform3f;
import static org.lwjgl.opengl.GL20.glUniform4f;
import static org.lwjgl.opengl.GL30.GL_TRIANGLES;
import static org.lwjgl.opengl.GL30.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glDrawElements;

import ru.reweu.game.render.ShaderProgram;

/**
 * Рендеринг {@link Mesh}: привязка текстур, установка униформ, draw call.
 * Отделён от данных и GPU-ресурсов меша (SRP).
 */
final class MeshRenderer {

    private MeshRenderer() {
    }

    static void render(Mesh mesh, boolean opaqueGeometryPass) {
        int shaderProgram = ShaderProgram.getActiveProgramId();
        int opaqueGeomLoc = ShaderProgram.uniformLocation(shaderProgram, "opaqueGeometryPass");
        if (opaqueGeomLoc != -1) {
            glUniform1i(opaqueGeomLoc, opaqueGeometryPass ? 1 : 0);
        }
        int textureScaleLocation = ShaderProgram.uniformLocation(shaderProgram, "textureScale");
        if (textureScaleLocation != -1) {
            float ts = mesh.usePlanarUv ? Mesh.landscapeTextureScale : 1.0f;
            glUniform1f(textureScaleLocation, ts);
        }
        int useDiffuseTexLoc = ShaderProgram.uniformLocation(shaderProgram, "useDiffuseTexture");
        int useSpecularTexLoc = ShaderProgram.uniformLocation(shaderProgram, "useSpecularTexture");
        int useNormalTexLoc = ShaderProgram.uniformLocation(shaderProgram, "useNormalTexture");
        int useAlphaTexLoc = ShaderProgram.uniformLocation(shaderProgram, "useAlphaTexture");
        int materialColorLoc = ShaderProgram.uniformLocation(shaderProgram, "diffuseColor");
        int materialAlphaLoc = ShaderProgram.uniformLocation(shaderProgram, "materialAlpha");

        applyMaterialAlpha(mesh, materialAlphaLoc);
        bindDiffuse(mesh, shaderProgram, useDiffuseTexLoc, materialColorLoc);
        bindOptionalTextures(mesh, shaderProgram, useSpecularTexLoc, useNormalTexLoc, useAlphaTexLoc);
        setPlanarUvUniforms(mesh, shaderProgram);

        glBindVertexArray(mesh.vaoId);
        glDrawElements(GL_TRIANGLES, mesh.vertexCount, GL_UNSIGNED_INT, 0);
    }

    static void renderDepthOnly(Mesh mesh) {
        setPlanarUvUniforms(mesh, ShaderProgram.getActiveProgramId());
        glBindVertexArray(mesh.vaoId);
        glDrawElements(GL_TRIANGLES, mesh.vertexCount, GL_UNSIGNED_INT, 0);
    }

    private static void applyMaterialAlpha(Mesh mesh, int loc) {
        if (loc != -1) {
            glUniform1f(loc, mesh.materialAlpha);
        }
    }

    private static void bindDiffuse(Mesh mesh, int shaderProgram, int useDiffuseTexLoc, int materialColorLoc) {
        Texture diffuse = mesh.diffuseTexture;
        if (diffuse != null) {
            glActiveTexture(GL_TEXTURE0);
            diffuse.bind();
            int texture1Loc = ShaderProgram.uniformLocation(shaderProgram, "texture1");
            if (texture1Loc != -1) {
                glUniform1i(texture1Loc, 0);
            }
            int diffuseSamplerLoc = ShaderProgram.uniformLocation(shaderProgram, "diffuseTexture");
            if (diffuseSamplerLoc != -1) {
                glUniform1i(diffuseSamplerLoc, 0);
            }
            if (useDiffuseTexLoc != -1) {
                glUniform1i(useDiffuseTexLoc, 1);
            }
            if (materialColorLoc != -1) {
                float len2 = mesh.color.x * mesh.color.x + mesh.color.y * mesh.color.y + mesh.color.z * mesh.color.z;
                if (len2 < 1e-8f) {
                    glUniform3f(materialColorLoc, 1f, 1f, 1f);
                } else {
                    glUniform3f(materialColorLoc, mesh.color.x, mesh.color.y, mesh.color.z);
                }
            }
        } else {
            if (useDiffuseTexLoc != -1) {
                glUniform1i(useDiffuseTexLoc, 0);
            }
            if (materialColorLoc != -1) {
                float len2 = mesh.color.x * mesh.color.x + mesh.color.y * mesh.color.y + mesh.color.z * mesh.color.z;
                if (len2 < 1e-8f) {
                    glUniform3f(materialColorLoc, 0.75f, 0.75f, 0.75f);
                } else {
                    glUniform3f(materialColorLoc, mesh.color.x, mesh.color.y, mesh.color.z);
                }
            }
        }
    }

    private static void bindOptionalTextures(
        Mesh mesh, int shaderProgram,
        int useSpecularTexLoc, int useNormalTexLoc, int useAlphaTexLoc
    ) {
        int mrLoc = ShaderProgram.uniformLocation(shaderProgram, "textureMetallicRoughness");
        if (useSpecularTexLoc != -1) {
            if (mesh.specularTexture != null) {
                glActiveTexture(GL_TEXTURE1);
                mesh.specularTexture.bind();
                glUniform1i(useSpecularTexLoc, 1);
                if (mrLoc != -1) {
                    glUniform1i(mrLoc, 1);
                }
            } else {
                glUniform1i(useSpecularTexLoc, 0);
            }
        }
        int nLoc = ShaderProgram.uniformLocation(shaderProgram, "textureNormal");
        if (useNormalTexLoc != -1) {
            if (mesh.normalTexture != null) {
                glActiveTexture(GL_TEXTURE2);
                mesh.normalTexture.bind();
                glUniform1i(useNormalTexLoc, 1);
                if (nLoc != -1) {
                    glUniform1i(nLoc, 2);
                }
            } else {
                glUniform1i(useNormalTexLoc, 0);
            }
        }
        int aLoc = ShaderProgram.uniformLocation(shaderProgram, "textureOpacity");
        if (useAlphaTexLoc != -1) {
            if (mesh.alphaTexture != null) {
                glActiveTexture(GL_TEXTURE3);
                mesh.alphaTexture.bind();
                glUniform1i(useAlphaTexLoc, 1);
                if (aLoc != -1) {
                    glUniform1i(aLoc, 3);
                }
            } else {
                glUniform1i(useAlphaTexLoc, 0);
            }
        }
        glActiveTexture(GL_TEXTURE0);
    }

    private static void setPlanarUvUniforms(Mesh mesh, int shaderProgram) {
        int planarLoc = ShaderProgram.uniformLocation(shaderProgram, "usePlanarUv");
        if (planarLoc != -1) {
            glUniform1i(planarLoc, mesh.usePlanarUv ? 1 : 0);
        }
        int uvBoundsLoc = ShaderProgram.uniformLocation(shaderProgram, "uvBounds");
        if (uvBoundsLoc != -1) {
            glUniform4f(uvBoundsLoc, mesh.planarMinX, mesh.planarMinZ, mesh.planarRangeX, mesh.planarRangeZ);
        }
    }
}
