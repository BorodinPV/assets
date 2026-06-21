package ru.reweu.game.loader;

import static org.lwjgl.opengl.GL30.GL_BGRA;
import static org.lwjgl.opengl.GL30.GL_LINEAR;
import static org.lwjgl.opengl.GL30.GL_LINEAR_MIPMAP_LINEAR;
import static org.lwjgl.opengl.GL30.GL_REPEAT;
import static org.lwjgl.opengl.GL30.GL_RGBA;
import static org.lwjgl.opengl.GL30.GL_SRGB8_ALPHA8;
import static org.lwjgl.opengl.GL30.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL30.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL30.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL30.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL30.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL30.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL30.glBindTexture;
import static org.lwjgl.opengl.GL30.glDeleteTextures;
import static org.lwjgl.opengl.GL30.glGenTextures;
import static org.lwjgl.opengl.GL30.glGenerateMipmap;
import static org.lwjgl.opengl.GL30.glTexImage2D;
import static org.lwjgl.opengl.GL30.glTexParameteri;
import static org.lwjgl.stb.STBImage.stbi_load;
import static org.lwjgl.stb.STBImage.stbi_load_from_memory;
import static org.lwjgl.system.MemoryStack.stackPush;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Paths;
import org.lwjgl.assimp.AITexture;
import org.lwjgl.assimp.AITexel;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import ru.reweu.game.render.RenderErrorLog;

public class Texture {

    /**
     * STB native code reads the buffer address; LWJGL requires a direct buffer. jgltf/Assimp often supply heap buffers.
     * Copies only {@link ByteBuffer#remaining()} bytes (do not use {@link ByteBuffer#clear()} on a slice — it widens to capacity).
     */
    private static ByteBuffer copyToDirectForStb(ByteBuffer src) {
        ByteBuffer dup = src.duplicate();
        int n = dup.remaining();
        if (n == 0) {
            return dup;
        }
        if (dup.isDirect()) {
            return dup;
        }
        ByteBuffer direct = ByteBuffer.allocateDirect(n);
        direct.put(dup);
        direct.flip();
        return direct;
    }

    /**
     * Общий путь: декодирование STB из памяти и загрузка в привязанный GL_TEXTURE_2D.
     * @return {@code true} если изображение имеет альфа-канал (4 канала)
     */
    private static boolean decodeAndUploadStb(ByteBuffer compressedData, int internalFormat, String label) {
        try (MemoryStack stack = stackPush()) {
            IntBuffer wb = stack.mallocInt(1);
            IntBuffer hb = stack.mallocInt(1);
            IntBuffer cb = stack.mallocInt(1);
            ByteBuffer data = stbi_load_from_memory(compressedData, wb, hb, cb, 4);
            if (data == null) {
                throw new RuntimeException("stbi_load_from_memory failed for " + label + ": " + STBImage.stbi_failure_reason());
            }
            int w = wb.get(0);
            int h = hb.get(0);
            boolean hasAlpha = (cb.get(0) == 4);
            glTexImage2D(GL_TEXTURE_2D, 0, internalFormat, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, data);
            STBImage.stbi_image_free(data);
            return hasAlpha;
        }
    }

    public enum LoadMode {
        /** Mipmap + LINEAR_MIPMAP_LINEAR, REPEAT — модели и ландшафт */
        MODEL_WITH_MIPMAPS,
        /** Без mipmaps, LINEAR — skybox / простые квады */
        FLAT_NO_MIPMAPS
    }

    private final int id;
    private final boolean hasAlpha;
    private final String image;

    /** После загрузки пикселей в привязанный {@link GL_TEXTURE_2D}. */
    private static void configureTexture2dSampler(LoadMode mode) {
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);

        if (mode == LoadMode.MODEL_WITH_MIPMAPS) {
            glGenerateMipmap(GL_TEXTURE_2D);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        } else {
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        }
    }

    private static int albedoInternalFormat(boolean srgbAlbedo) {
        return srgbAlbedo ? GL_SRGB8_ALPHA8 : GL_RGBA;
    }

    /**
     * Встроенная текстура из glTF/GLB (Assimp: путь {@code *index} в {@link org.lwjgl.assimp.AIScene#mTextures()}).
     * @param srgbAlbedo {@code true} для baseColor/diffuse (цвета как в редакторе); {@code false} для MR/normal.
     */
    public static Texture tryFromAssimpEmbedded(AITexture aiTex, String label, LoadMode mode, boolean srgbAlbedo) {
        if (aiTex == null) {
            TextureLoadLog.warn("tryFromAssimpEmbedded(" + label + "): AITexture is null");
            return null;
        }
        int internal = albedoInternalFormat(srgbAlbedo);
        int w = aiTex.mWidth();
        int h = aiTex.mHeight();
        int id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, id);
        boolean hasAlpha;
        try (MemoryStack stack = stackPush()) {
            if (w != 0 && h != 0) {
                AITexel.Buffer texels = aiTex.pcData();
                int nbytes = w * h * AITexel.SIZEOF;
                ByteBuffer slice = MemoryUtil.memByteBuffer(texels.address(), nbytes);
                glTexImage2D(GL_TEXTURE_2D, 0, internal, w, h, 0, GL_BGRA, GL_UNSIGNED_BYTE, slice);
                hasAlpha = true;
            } else {
                ByteBuffer raw = aiTex.pcDataCompressed();
                if (raw == null) {
                    glDeleteTextures(id);
                    TextureLoadLog.warn(
                        "tryFromAssimpEmbedded(" + label + "): compressed buffer null — embedded image missing");
                    return null;
                }
                raw = copyToDirectForStb(raw);
                try {
                    hasAlpha = decodeAndUploadStb(raw, internal, label);
                } catch (RuntimeException e) {
                    glDeleteTextures(id);
                    TextureLoadLog.warn("tryFromAssimpEmbedded(" + label + "): " + e.getMessage());
                    return null;
                }
            }
        } catch (RuntimeException e) {
            glDeleteTextures(id);
            TextureLoadLog.warn("tryFromAssimpEmbedded(" + label + "): " + e.getMessage());
            return null;
        }
        configureTexture2dSampler(mode);
        return new Texture(id, label != null ? label : "embedded", hasAlpha);
    }

    /**
     * Диагностика без повторной загрузки в GPU: причина, если {@link #tryFromAssimpEmbedded} вернул {@code null}.
     */
    public static String describeEmbeddedDecodeFailure(AITexture aiTex) {
        if (aiTex == null) {
            return "AITexture null";
        }
        int w = aiTex.mWidth();
        int h = aiTex.mHeight();
        if (w != 0 && h != 0) {
            return "uncompressed BGRA path failed (size " + w + "x" + h + ")";
        }
        ByteBuffer raw = aiTex.pcDataCompressed();
        if (raw == null) {
            return "compressed buffer null (embedded image missing?)";
        }
        return "compressed decode failed (size " + raw.remaining() + " bytes)";
    }

    public static Texture tryFromAssimpEmbedded(AITexture aiTex, String label, LoadMode mode) {
        return tryFromAssimpEmbedded(aiTex, label, mode, false);
    }

    public static Texture tryFromAssimpEmbedded(AITexture aiTex, String label) {
        return tryFromAssimpEmbedded(aiTex, label, LoadMode.MODEL_WITH_MIPMAPS, false);
    }

    /** Уже настроенная через {@link #configureTexture2dSampler(LoadMode)}. */
    private Texture(int glTextureId, String image, boolean hasAlpha) {
        this.id = glTextureId;
        this.image = image;
        this.hasAlpha = hasAlpha;
    }

    public Texture(String resourcePath) {
        this(resourcePath, LoadMode.MODEL_WITH_MIPMAPS, true);
    }

    public Texture(String resourcePath, LoadMode mode) {
        this(resourcePath, mode, mode == LoadMode.MODEL_WITH_MIPMAPS);
    }

    public Texture(String resourcePath, LoadMode mode, boolean srgbAlbedo) {
        String filePath = resolveClasspathPath(resourcePath);
        id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, id);
        image = resourcePath;
        int internal = albedoInternalFormat(srgbAlbedo);

        try (MemoryStack stack = stackPush()) {
            IntBuffer widthBuffer = stack.mallocInt(1);
            IntBuffer heightBuffer = stack.mallocInt(1);
            IntBuffer channelsBuffer = stack.mallocInt(1);

            ByteBuffer data = stbi_load(filePath, widthBuffer, heightBuffer, channelsBuffer, 4);
            if (data == null) {
                glDeleteTextures(id);
                String reason = STBImage.stbi_failure_reason();
                RenderErrorLog.warn("Texture load failed (" + resourcePath + "): " + reason);
                throw new RuntimeException("Failed to load texture: " + reason);
            }
            int width = widthBuffer.get(0);
            int height = heightBuffer.get(0);
            int channels = channelsBuffer.get();
            hasAlpha = (channels == 4);

            glTexImage2D(GL_TEXTURE_2D, 0, internal, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, data);
            STBImage.stbi_image_free(data);
        }

        configureTexture2dSampler(mode);
    }

    /** Загрузка с classpath; при отсутствии файла или ошибке декодирования — {@code null}, без исключения. */
    public static Texture tryLoadClasspath(String resourcePath, LoadMode mode, boolean srgbAlbedo) {
        if (resolveClasspathPathOrNull(resourcePath) == null) {
            TextureLoadLog.warn("tryLoadClasspath: resource not on classpath: " + resourcePath);
            return null;
        }
        try {
            return new Texture(resourcePath, mode, srgbAlbedo);
        } catch (RuntimeException e) {
            TextureLoadLog.warn("tryLoadClasspath: " + resourcePath + " — " + e.getMessage());
            return null;
        }
    }

    public static Texture tryLoadClasspath(String resourcePath, LoadMode mode) {
        return tryLoadClasspath(resourcePath, mode, mode == LoadMode.MODEL_WITH_MIPMAPS);
    }

    public static Texture tryLoadClasspath(String resourcePath) {
        return tryLoadClasspath(resourcePath, LoadMode.MODEL_WITH_MIPMAPS);
    }

    /**
     * Декодирование PNG/JPEG из памяти (glTF {@code Image} buffer/bufferView).
     */
    public static Texture tryFromMemory(ByteBuffer imageBytes, String label, LoadMode mode, boolean srgbAlbedo) {
        if (imageBytes == null || !imageBytes.hasRemaining()) {
            TextureLoadLog.warn("tryFromMemory(" + label + "): buffer is null or empty");
            return null;
        }
        imageBytes = copyToDirectForStb(imageBytes);
        int id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, id);
        int internal = albedoInternalFormat(srgbAlbedo);
        boolean hasAlpha;
        try {
            hasAlpha = decodeAndUploadStb(imageBytes, internal, label);
        } catch (RuntimeException e) {
            glDeleteTextures(id);
            TextureLoadLog.warn("tryFromMemory(" + label + "): " + e.getMessage());
            return null;
        }
        configureTexture2dSampler(mode);
        return new Texture(id, label != null ? label : "memory", hasAlpha);
    }

    private static String resolveClasspathPath(String resourcePath) {
        String path = resolveClasspathPathOrNull(resourcePath);
        if (path == null) {
            RenderErrorLog.warn("Texture resource not on classpath: " + resourcePath);
            throw new RuntimeException("Resource not found: " + resourcePath);
        }
        return path;
    }

    private static String resolveClasspathPathOrNull(String resourcePath) {
        URL resourceUrl = Texture.class.getResource(resourcePath);
        if (resourceUrl == null) {
            return null;
        }
        try {
            return Paths.get(resourceUrl.toURI()).toString();
        } catch (URISyntaxException e) {
            return null;
        }
    }

    public String getImage() {
        return image;
    }

    public boolean hasAlpha() {
        return hasAlpha;
    }

    public void bind() {
        glBindTexture(GL_TEXTURE_2D, id);
    }

    public void cleanup() {
        glDeleteTextures(id);
    }
}
