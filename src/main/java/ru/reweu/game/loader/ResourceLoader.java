package ru.reweu.game.loader;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;

import static org.lwjgl.system.MemoryUtil.memAlloc;

public class ResourceLoader {
    public static ByteBuffer loadResourceAsByteBuffer(String resourcePath) {
        try (InputStream inputStream = ResourceLoader.class.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new RuntimeException("Resource not found: " + resourcePath);
            }

            byte[] data = inputStream.readAllBytes();
            ByteBuffer buffer = memAlloc(data.length);
            buffer.put(data).flip();
            return buffer;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load resource: " + resourcePath, e);
        }
    }

    /**
     * Путь к файлу ресурса на диске (только если ресурс реально есть в classpath и доступен как {@code file:} URL).
     */
    public static java.io.File loadResourceAsFile(String resourcePath) {
        URL url = ResourceLoader.class.getResource(resourcePath);
        if (url == null) {
            throw new RuntimeException("Resource not found: " + resourcePath);
        }
        try {
            return new java.io.File(url.toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException("Failed to resolve resource as file: " + resourcePath, e);
        }
    }

    /**
     * Как {@link #loadResourceAsFile(String)}, но без исключения, если пути нет в classpath (например опциональный HDR).
     */
    public static java.io.File tryLoadResourceAsFile(String resourcePath) {
        URL url = ResourceLoader.class.getResource(resourcePath);
        if (url == null) {
            return null;
        }
        try {
            return new java.io.File(url.toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException("Failed to resolve resource as file: " + resourcePath, e);
        }
    }
}
