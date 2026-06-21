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
import static org.lwjgl.assimp.Assimp.aiProcess_CalcTangentSpace;
import static org.lwjgl.assimp.Assimp.aiProcess_FlipUVs;
import static org.lwjgl.assimp.Assimp.aiProcess_GenSmoothNormals;
import static org.lwjgl.assimp.Assimp.aiProcess_JoinIdenticalVertices;
import static org.lwjgl.assimp.Assimp.aiProcess_LimitBoneWeights;
import static org.lwjgl.assimp.Assimp.aiProcess_Triangulate;
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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.AIColor4D;
import org.lwjgl.assimp.AIFace;
import org.lwjgl.assimp.AIMaterial;
import org.lwjgl.assimp.AIMatrix4x4;
import org.lwjgl.assimp.AIMesh;
import org.lwjgl.assimp.AINode;
import org.lwjgl.assimp.AIScene;
import org.lwjgl.assimp.AIString;
import org.lwjgl.assimp.AITexture;
import org.lwjgl.system.MemoryStack;

/**
 * Загрузка сеток через Assimp (LWJGL). Поддерживаются {@code .obj}, {@code .glb}, {@code .gltf} и другие форматы.
 * Скелет/анимации не обрабатываются — только статическая геометрия.
 * Иерархия узлов (glTF/GLB): {@link AINode#mTransformation()} накапливается и применяется к вершинам и нормалям.
 * Текстуры: относительно папки модели либо встроенные в glTF/GLB (Assimp {@code *index}).
 */
public class ModelLoader {

    /** Базовые флаги (OBJ и прочие). */
    static final int POST_PROCESS_DEFAULT =
        aiProcess_Triangulate | aiProcess_FlipUVs | aiProcess_JoinIdenticalVertices | aiProcess_GenSmoothNormals;

    /**
     * glTF/GLB: тангенты (normal maps), веса костей — Assimp для статики безопасно.
     */
    static final int POST_PROCESS_GLTF =
        POST_PROCESS_DEFAULT | aiProcess_CalcTangentSpace | aiProcess_LimitBoneWeights;

    public static Mesh[] loadModel(String resourcePath, float scale) {
        return loadModel(resourcePath, scale, postProcessFlagsForPath(resourcePath));
    }

    private static int postProcessFlagsForPath(String resourcePath) {
        String n = resourcePath.toLowerCase(Locale.ROOT);
        if (n.endsWith(".glb") || n.endsWith(".gltf")) {
            return POST_PROCESS_GLTF;
        }
        return POST_PROCESS_DEFAULT;
    }

    /**
     * Полный контроль над post-processing Assimp.
     */
    public static Mesh[] loadModel(String resourcePath, float scale, int postProcessFlags) {
        URL resourceUrl = ModelLoader.class.getResource(resourcePath);
        String filePath;
        try {
            filePath = Paths.get(resourceUrl.toURI()).toString();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        AIScene scene = aiImportFileEx(filePath, postProcessFlags, null);

        if (scene == null || scene.mRootNode() == null) {
            throw new RuntimeException("Error loading model: " + aiGetErrorString());
        }

        String modelDir = resourcePath.contains("/")
            ? resourcePath.substring(0, resourcePath.lastIndexOf('/') + 1)
            : "/";

        List<Mesh> meshes = new ArrayList<>();
        processNode(scene.mRootNode(), scene, meshes, scale, modelDir, new Matrix4f());
        return meshes.toArray(new Mesh[0]);
    }

    /**
     * Assimp {@code aiMatrix4x4} хранится в строках; JOML — столбцовый порядок для OpenGL.
     */
    private static Matrix4f aiMatrixToJoml(AIMatrix4x4 ai) {
        return new Matrix4f().set(
            ai.a1(), ai.b1(), ai.c1(), ai.d1(),
            ai.a2(), ai.b2(), ai.c2(), ai.d2(),
            ai.a3(), ai.b3(), ai.c3(), ai.d3(),
            ai.a4(), ai.b4(), ai.c4(), ai.d4()
        );
    }

    private static void processNode(
        AINode node, AIScene scene, List<Mesh> meshes, float scale, String modelDir, Matrix4f parentWorld
    ) {
        Matrix4f world = new Matrix4f(parentWorld).mul(aiMatrixToJoml(node.mTransformation()));

        List<Mesh> meshesStream = IntStream.range(0, node.mNumMeshes())
            .mapToObj(i -> AIMesh.create(scene.mMeshes().get(node.mMeshes().get(i))))
            .map(aiMesh -> processMesh(aiMesh, scene, scale, modelDir, world))
            .collect(Collectors.toList());

        meshes.addAll(meshesStream);

        for (int i = 0; i < node.mNumChildren(); i++) {
            processNode(AINode.create(node.mChildren().get(i)), scene, meshes, scale, modelDir, world);
        }
    }

    private static Mesh processMesh(AIMesh aiMesh, AIScene scene, float scale, String modelDir, Matrix4f worldTransform) {
        String meshName = aiMesh.mName().dataString();
        List<Vector3f> vertices = new ArrayList<>();
        List<Vector3f> normals = new ArrayList<>();
        List<Vector3f> texCoords = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();

        Matrix3f normalMat = new Matrix3f();
        worldTransform.normal(normalMat);

        for (int i = 0; i < aiMesh.mNumVertices(); i++) {
            Vector3f v = new Vector3f(
                aiMesh.mVertices().get(i).x(),
                aiMesh.mVertices().get(i).y(),
                aiMesh.mVertices().get(i).z()
            );
            worldTransform.transformPosition(v);
            vertices.add(v);

            if (aiMesh.mNormals() != null) {
                Vector3f n = new Vector3f(
                    aiMesh.mNormals().get(i).x(),
                    aiMesh.mNormals().get(i).y(),
                    aiMesh.mNormals().get(i).z()
                );
                normalMat.transform(n);
                if (n.lengthSquared() > 1e-12f) {
                    n.normalize();
                }
                normals.add(n);
            } else {
                normals.add(new Vector3f(0.0f, 0.0f, 0.0f));
            }

            if (aiMesh.mTextureCoords(0) != null) {
                texCoords.add(new Vector3f(
                    aiMesh.mTextureCoords(0).get(i).x(),
                    aiMesh.mTextureCoords(0).get(i).y(),
                    0.0f
                ));
            } else {
                texCoords.add(new Vector3f(0.0f, 0.0f, 0.0f));
            }
        }

        List<Vector4f> vertexColors = new ArrayList<>(aiMesh.mNumVertices());
        AIColor4D.Buffer colorBuf = aiMesh.mColors(0);
        int nVert = aiMesh.mNumVertices();
        for (int i = 0; i < nVert; i++) {
            if (colorBuf != null && i < colorBuf.capacity()) {
                AIColor4D c = colorBuf.get(i);
                vertexColors.add(new Vector4f(c.r(), c.g(), c.b(), c.a()));
            } else {
                vertexColors.add(new Vector4f(1f, 1f, 1f, 1f));
            }
        }

        for (int i = 0; i < aiMesh.mNumFaces(); i++) {
            AIFace face = aiMesh.mFaces().get(i);
            for (int j = 0; j < face.mNumIndices(); j++) {
                indices.add(face.mIndices().get(j));
            }
        }

        AIMaterial material = AIMaterial.create(scene.mMaterials().get(aiMesh.mMaterialIndex()));

        Texture diffuseTexture = loadDiffuseTexture(material, modelDir, scene);
        Texture ambientTexture = loadMaterialTexture(material, aiTextureType_AMBIENT, modelDir, scene, true);
        Texture specularTexture = loadSpecularTexture(material, modelDir, scene);
        Texture normalTexture = loadMaterialTexture(material, aiTextureType_NORMALS, modelDir, scene, false);
        Texture alphaTexture = loadMaterialTexture(material, aiTextureType_OPACITY, modelDir, scene, false);
        Texture specularHighlightTexture =
            loadMaterialTexture(material, aiTextureType_SHININESS, modelDir, scene, false);

        Vector3f color = loadMaterialDiffuseColor(material);

        float materialAlpha = loadMaterialAlpha(material) * loadDiffuseBaseColorAlpha(material);
        materialAlpha = Math.min(1f, Math.max(0f, materialAlpha));
        // Возвращаем меш с загруженными текстурами и цветом
        return new Mesh(vertices, normals, texCoords, vertexColors, indices, diffuseTexture, ambientTexture,
            specularTexture,
            normalTexture, alphaTexture, specularHighlightTexture, color, materialAlpha, scale, meshName);
    }

    private static float loadMaterialAlpha(AIMaterial material) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer alphaBuffer = stack.mallocFloat(1);
            int result = aiGetMaterialFloatArray(material, AI_MATKEY_OPACITY, aiTextureType_NONE, 0, alphaBuffer,
                null);

            if (result == aiReturn_SUCCESS) {
                return alphaBuffer.get(0); // Возвращаем значение альфа-канала
            }
        }
        // Если не удалось получить прозрачность, возвращаем 1.0f (полная непрозрачность)
        return 1.0f;
    }

    private static Texture loadMaterialTexture(
        AIMaterial material, int textureType, String modelDir, AIScene scene, boolean srgbColorData
    ) {
        int slotCount = aiGetMaterialTextureCount(material, textureType);
        if (slotCount <= 0) {
            Texture t = loadMaterialTextureAtIndex(material, textureType, 0, modelDir, scene, srgbColorData);
            if (t != null) {
                return t;
            }
            return null;
        }
        for (int ti = 0; ti < slotCount; ti++) {
            Texture t = loadMaterialTextureAtIndex(material, textureType, ti, modelDir, scene, srgbColorData);
            if (t != null) {
                return t;
            }
        }
        return null;
    }

    private static Texture loadMaterialTextureAtIndex(
        AIMaterial material,
        int textureType,
        int textureIndex,
        String modelDir,
        AIScene scene,
        boolean srgbColorData
    ) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            AIString path = AIString.calloc(stack);
            int result =
                aiGetMaterialTexture(material, textureType, textureIndex, path, (IntBuffer) null, null, null, null, null,
                    null);
            if (result == aiReturn_SUCCESS) {
                String texturePath = path.dataString().trim();
                if (!texturePath.isEmpty()) {
                    if (texturePath.startsWith("*")) {
                        Texture embedded = loadEmbeddedAssimpTexture(texturePath, scene, srgbColorData);
                        if (embedded != null) {
                            return embedded;
                        }
                        return null;
                    }
                    if (texturePath.startsWith("/")) {
                        texturePath = texturePath.substring(1);
                    }
                    Path modelDirPath = Paths.get(modelDir.startsWith("/") ? modelDir : "/" + modelDir);
                    Path resolved = modelDirPath.resolve(texturePath).normalize();
                    LinkedHashSet<String> candidates = new LinkedHashSet<>();
                    candidates.add(toClasspathPath(resolved));
                    Path fileName = Paths.get(texturePath).getFileName();
                    if (fileName != null) {
                        candidates.add(toClasspathPath(modelDirPath.resolve(fileName).normalize()));
                    }
                    for (String classpathPath : candidates) {
                        Texture t = Texture.tryLoadClasspath(classpathPath, Texture.LoadMode.MODEL_WITH_MIPMAPS, srgbColorData);
                        if (t != null) {
                            return t;
                        }
                    }
                    TextureLoadLog.warn("нет на classpath (пропуск): " + texturePath + " кандидаты " + candidates);
                }
            }
            return null;
        }
    }

    /**
     * Assimp: встроенное изображение, путь вида {@code *0}, {@code *1} … в {@link AIScene#mTextures()}.
     */
    private static Texture loadEmbeddedAssimpTexture(String asteriskPath, AIScene scene, boolean srgbAlbedo) {
        if (scene == null || scene.mNumTextures() == 0) {
            TextureLoadLog.warn("embedded " + asteriskPath + ": сцена без mTextures");
            return null;
        }
        String digits = asteriskPath.substring(1).trim();
        int idx;
        try {
            idx = Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            TextureLoadLog.warn("embedded " + asteriskPath + ": не числовой индекс");
            return null;
        }
        if (idx < 0 || idx >= scene.mNumTextures()) {
            TextureLoadLog.warn("embedded " + asteriskPath + ": индекс " + idx + " вне [0," + scene.mNumTextures() + ")");
            return null;
        }
        PointerBuffer textures = scene.mTextures();
        long ptr = textures.get(idx);
        if (ptr == 0L) {
            TextureLoadLog.warn("embedded " + asteriskPath + ": нулевой указатель AITexture");
            return null;
        }
        AITexture aiTex = AITexture.create(ptr);
        return Texture.tryFromAssimpEmbedded(aiTex, asteriskPath, Texture.LoadMode.MODEL_WITH_MIPMAPS, srgbAlbedo);
    }

    /** Путь для {@link Class#getResource(String)}: ведущий «/», прямые слэши. */
    private static String toClasspathPath(Path p) {
        String s = p.toString().replace('\\', '/');
        if (!s.startsWith("/")) {
            s = "/" + s;
        }
        return s;
    }

    private static Vector3f loadMaterialDiffuseColor(AIMaterial material) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            AIColor4D color = AIColor4D.calloc(stack);
            if (aiGetMaterialColor(material, AI_MATKEY_BASE_COLOR, aiTextureType_NONE, 0, color) == aiReturn_SUCCESS) {
                return new Vector3f(color.r(), color.g(), color.b());
            }
            if (aiGetMaterialColor(material, AI_MATKEY_COLOR_DIFFUSE, aiTextureType_NONE, 0, color) == aiReturn_SUCCESS) {
                return new Vector3f(color.r(), color.g(), color.b());
            }
            return new Vector3f(1.0f, 1.0f, 1.0f); // Цвет по умолчанию (белый)
        }
    }

    /** glTF baseColorFactor.a — множитель к opacity и к альфе текстуры в шейдере. */
    private static float loadDiffuseBaseColorAlpha(AIMaterial material) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            AIColor4D color = AIColor4D.calloc(stack);
            if (aiGetMaterialColor(material, AI_MATKEY_BASE_COLOR, aiTextureType_NONE, 0, color) == aiReturn_SUCCESS) {
                return color.a();
            }
            if (aiGetMaterialColor(material, AI_MATKEY_COLOR_DIFFUSE, aiTextureType_NONE, 0, color) == aiReturn_SUCCESS) {
                return color.a();
            }
        }
        return 1.0f;
    }

    private static Texture loadDiffuseTexture(AIMaterial material, String modelDir, AIScene scene) {
        Texture t = loadMaterialTexture(material, aiTextureType_DIFFUSE, modelDir, scene, true);
        if (t == null) {
            t = loadMaterialTexture(material, aiTextureType_BASE_COLOR, modelDir, scene, true);
        }
        return t;
    }

    private static Texture loadSpecularTexture(AIMaterial material, String modelDir, AIScene scene) {
        Texture t = loadMaterialTexture(material, aiTextureType_SPECULAR, modelDir, scene, false);
        if (t == null) {
            t = loadMaterialTexture(material, aiTextureType_METALNESS, modelDir, scene, false);
        }
        if (t == null) {
            t = loadMaterialTexture(material, aiTextureType_DIFFUSE_ROUGHNESS, modelDir, scene, false);
        }
        return t;
    }

}