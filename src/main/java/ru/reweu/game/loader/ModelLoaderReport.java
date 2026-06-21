package ru.reweu.game.loader;

import static org.lwjgl.assimp.Assimp.AI_MATKEY_BASE_COLOR;
import static org.lwjgl.assimp.Assimp.AI_MATKEY_COLOR_DIFFUSE;
import static org.lwjgl.assimp.Assimp.AI_MATKEY_GLTF_ALPHACUTOFF;
import static org.lwjgl.assimp.Assimp.AI_MATKEY_GLTF_ALPHAMODE;
import static org.lwjgl.assimp.Assimp.AI_MATKEY_METALLIC_FACTOR;
import static org.lwjgl.assimp.Assimp.AI_MATKEY_NAME;
import static org.lwjgl.assimp.Assimp.AI_MATKEY_OPACITY;
import static org.lwjgl.assimp.Assimp.AI_MATKEY_ROUGHNESS_FACTOR;
import static org.lwjgl.assimp.Assimp.aiGetMaterialColor;
import static org.lwjgl.assimp.Assimp.aiGetMaterialFloatArray;
import static org.lwjgl.assimp.Assimp.aiGetErrorString;
import static org.lwjgl.assimp.Assimp.aiGetMaterialTexture;
import static org.lwjgl.assimp.Assimp.aiGetMaterialTextureCount;
import static org.lwjgl.assimp.Assimp.aiGetMaterialString;
import static org.lwjgl.assimp.Assimp.aiImportFileEx;
import static org.lwjgl.assimp.Assimp.aiReleaseImport;
import static org.lwjgl.assimp.Assimp.aiReturn_SUCCESS;
import static org.lwjgl.assimp.Assimp.aiTextureType_AMBIENT;
import static org.lwjgl.assimp.Assimp.aiTextureType_AMBIENT_OCCLUSION;
import static org.lwjgl.assimp.Assimp.aiTextureType_BASE_COLOR;
import static org.lwjgl.assimp.Assimp.aiTextureType_DIFFUSE;
import static org.lwjgl.assimp.Assimp.aiTextureType_DIFFUSE_ROUGHNESS;
import static org.lwjgl.assimp.Assimp.aiTextureType_EMISSIVE;
import static org.lwjgl.assimp.Assimp.aiTextureType_METALNESS;
import static org.lwjgl.assimp.Assimp.aiTextureType_NONE;
import static org.lwjgl.assimp.Assimp.aiTextureType_NORMALS;
import static org.lwjgl.assimp.Assimp.aiTextureType_OPACITY;
import static org.lwjgl.assimp.Assimp.aiTextureType_SHININESS;
import static org.lwjgl.assimp.Assimp.aiTextureType_SPECULAR;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.file.Paths;
import java.util.Locale;
import org.lwjgl.assimp.AIColor4D;
import org.lwjgl.assimp.AIMaterial;
import org.lwjgl.assimp.AIMesh;
import org.lwjgl.assimp.AIScene;
import org.lwjgl.assimp.AIString;
import org.lwjgl.system.MemoryStack;

/**
 * Отладочные отчёты по Assimp-сцене и загруженным мешам (без GPU / OpenGL).
 */
public final class ModelLoaderReport {

    private ModelLoaderReport() {
    }

    private static int postProcessFlagsForPath(String resourcePath) {
        String n = resourcePath.toLowerCase(Locale.ROOT);
        if (n.endsWith(".glb") || n.endsWith(".gltf")) {
            return ModelLoader.POST_PROCESS_GLTF;
        }
        return ModelLoader.POST_PROCESS_DEFAULT;
    }

    public static void dumpAssimpSceneReport(String resourcePath) {
        URL resourceUrl = ModelLoaderReport.class.getResource(resourcePath);
        if (resourceUrl == null) {
            System.err.println("dumpAssimpSceneReport: resource not found: " + resourcePath);
            return;
        }
        String filePath;
        try {
            filePath = Paths.get(resourceUrl.toURI()).toString();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        int flags = postProcessFlagsForPath(resourcePath);
        AIScene scene = aiImportFileEx(filePath, flags, null);
        if (scene == null) {
            System.err.println("dumpAssimpSceneReport: " + aiGetErrorString());
            return;
        }
        try {
            System.out.println("=== Assimp dump (no OpenGL): " + resourcePath + " ===");
            System.out.println("postProcessFlags=0x" + Integer.toHexString(flags));
            System.out.println("mNumMeshes=" + scene.mNumMeshes() + " mNumMaterials=" + scene.mNumMaterials());
            for (int i = 0; i < scene.mNumMeshes(); i++) {
                AIMesh mesh = AIMesh.create(scene.mMeshes().get(i));
                String meshName = mesh.mName().dataString();
                int matIdx = mesh.mMaterialIndex();
                System.out.println(
                    "  mesh[" + i + "] name=\"" + meshName + "\" mMaterialIndex=" + matIdx
                        + " vertices=" + mesh.mNumVertices());
                dumpMeshVertexColorSummary(mesh);
            }
            for (int m = 0; m < scene.mNumMaterials(); m++) {
                AIMaterial mat = AIMaterial.create(scene.mMaterials().get(m));
                System.out.println("--- material " + m + " ---");
                dumpMaterialAssimpDetails(mat);
            }
        } finally {
            aiReleaseImport(scene);
        }
    }

    private static void dumpMeshVertexColorSummary(AIMesh mesh) {
        AIColor4D.Buffer buf = mesh.mColors(0);
        if (buf == null) {
            System.out.println("    COLOR_0: absent");
            return;
        }
        int n = mesh.mNumVertices();
        int lim = Math.min(n, buf.capacity());
        if (lim <= 0) {
            System.out.println("    COLOR_0: buffer empty");
            return;
        }
        float minR = 1f, minG = 1f, minB = 1f;
        float maxR = 0f, maxG = 0f, maxB = 0f;
        boolean allWhite = true;
        for (int i = 0; i < lim; i++) {
            AIColor4D c = buf.get(i);
            float r = c.r(), g = c.g(), b = c.b();
            minR = Math.min(minR, r); minG = Math.min(minG, g); minB = Math.min(minB, b);
            maxR = Math.max(maxR, r); maxG = Math.max(maxG, g); maxB = Math.max(maxB, b);
            if (Math.abs(r - 1f) > 0.02f || Math.abs(g - 1f) > 0.02f || Math.abs(b - 1f) > 0.02f) {
                allWhite = false;
            }
        }
        System.out.println(
            "    COLOR_0: present, vertices=" + n + " sampled=" + lim + " rgb min=(" + minR + "," + minG + ","
                + minB + ") max=(" + maxR + "," + maxG + "," + maxB + ") allWhite≈" + allWhite);
    }

    private static void dumpMaterialAssimpDetails(AIMaterial mat) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            AIString s = AIString.calloc(stack);
            if (aiGetMaterialString(mat, AI_MATKEY_NAME, aiTextureType_NONE, 0, s) == aiReturn_SUCCESS) {
                System.out.println("  matName: " + s.dataString());
            }
            dumpMaterialTextureSlotCounts(mat);
            FloatBuffer fb = stack.mallocFloat(1);
            if (aiGetMaterialFloatArray(mat, AI_MATKEY_METALLIC_FACTOR, aiTextureType_NONE, 0, fb, null)
                == aiReturn_SUCCESS) {
                System.out.println("  metallicFactor (Assimp): " + fb.get(0));
            }
            if (aiGetMaterialFloatArray(mat, AI_MATKEY_ROUGHNESS_FACTOR, aiTextureType_NONE, 0, fb, null)
                == aiReturn_SUCCESS) {
                System.out.println("  roughnessFactor (Assimp): " + fb.get(0));
            }
            if (aiGetMaterialFloatArray(mat, AI_MATKEY_OPACITY, aiTextureType_NONE, 0, fb, null)
                == aiReturn_SUCCESS) {
                System.out.println("  opacity: " + fb.get(0));
            }
            if (aiGetMaterialFloatArray(mat, AI_MATKEY_GLTF_ALPHACUTOFF, aiTextureType_NONE, 0, fb, null)
                == aiReturn_SUCCESS) {
                System.out.println("  glTF alphaCutoff: " + fb.get(0));
            }
            if (aiGetMaterialString(mat, AI_MATKEY_GLTF_ALPHAMODE, aiTextureType_NONE, 0, s) == aiReturn_SUCCESS) {
                System.out.println("  glTF alphaMode: " + s.dataString());
            }
            System.out.println("  texture paths (first slot per type):");
            dumpTextureLine(mat, stack, aiTextureType_BASE_COLOR, "BASE_COLOR");
            dumpTextureLine(mat, stack, aiTextureType_DIFFUSE, "DIFFUSE");
            dumpTextureLine(mat, stack, aiTextureType_AMBIENT, "AMBIENT");
            dumpTextureLine(mat, stack, aiTextureType_SPECULAR, "SPECULAR");
            dumpTextureLine(mat, stack, aiTextureType_METALNESS, "METALNESS");
            dumpTextureLine(mat, stack, aiTextureType_DIFFUSE_ROUGHNESS, "DIFFUSE_ROUGHNESS (glTF MR)");
            dumpTextureLine(mat, stack, aiTextureType_NORMALS, "NORMALS");
            dumpTextureLine(mat, stack, aiTextureType_OPACITY, "OPACITY");
            dumpTextureLine(mat, stack, aiTextureType_SHININESS, "SHININESS");
            dumpTextureLine(mat, stack, aiTextureType_EMISSIVE, "EMISSIVE");
            dumpTextureLine(mat, stack, aiTextureType_AMBIENT_OCCLUSION, "AMBIENT_OCCLUSION");
        }
    }

    private static void dumpMaterialTextureSlotCounts(AIMaterial mat) {
        System.out.println(
            "  texture slot counts: BASE_COLOR=" + aiGetMaterialTextureCount(mat, aiTextureType_BASE_COLOR)
                + " DIFFUSE=" + aiGetMaterialTextureCount(mat, aiTextureType_DIFFUSE)
                + " METALNESS=" + aiGetMaterialTextureCount(mat, aiTextureType_METALNESS)
                + " DIFFUSE_ROUGHNESS=" + aiGetMaterialTextureCount(mat, aiTextureType_DIFFUSE_ROUGHNESS)
                + " NORMALS=" + aiGetMaterialTextureCount(mat, aiTextureType_NORMALS));
    }

    private static void dumpTextureLine(AIMaterial mat, MemoryStack stack, int textureType, String label) {
        AIString path = AIString.calloc(stack);
        int result =
            aiGetMaterialTexture(mat, textureType, 0, path, (IntBuffer) null, null, null, null, null, null);
        if (result != aiReturn_SUCCESS) {
            return;
        }
        String p = path.dataString().trim();
        if (!p.isEmpty()) {
            System.out.println("    " + label + ": " + p);
        }
    }

    public static void dumpLoadedMeshesSummary(Mesh[] meshes) {
        System.out.println("=== Loaded Mesh[] vs ModelLoader / fragment_shader.glsl ===");
        System.out.println(
            "ModelLoader: DIFFUSE|BASE_COLOR→texture1; MR: SPECULAR|METALNESS|DIFFUSE_ROUGHNESS; COLOR_0→albedo/alpha; "
                + "NORMALS/OPACITY — биндинг без полного шейдера.");
        for (int i = 0; i < meshes.length; i++) {
            Mesh m = meshes[i];
            System.out.println("  [" + i + "] \"" + m.getName() + "\"");
            System.out.println("      vertexColor (glTF COLOR_0, non-default): " + m.hasNonDefaultVertexColor());
            System.out.println("      diffuse (unit0 texture1): " + m.hasDiffuseTexture());
            System.out.println("      ambient (загружено, не в fragment_shader): " + m.hasAmbientTexture());
            System.out.println("      MR/spec (unit1 textureMetallicRoughness): " + m.hasSpecularTexture());
            System.out.println("      normal (unit2, uniform не в fragment_shader): " + m.hasNormalTexture());
            System.out.println("      opacity tex (unit3, не сэмплируется во fragment): " + m.hasAlphaTexture());
            System.out.println(
                "      shininess map (загружено, не в fragment_shader): " + m.hasSpecularHighlightTexture());
            System.out.println(
                "      overlayPass (прозрачный слой / стекло): " + m.isTransparentOverlayPass()
                    + " | glassNameHeuristic=" + m.isGlassNameMatch()
                    + " | materialAlpha<1=" + m.isTransparent());
        }
    }
}
