package ru.reweu.game.gltf;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Читает JSON-чанк из бинарного GLB (glTF 2.0).
 */
public final class GltfGlbJson {

    private GltfGlbJson() {
    }

    public static String readJsonChunkUtf8(Path glbPath) throws IOException {
        byte[] file = Files.readAllBytes(glbPath);
        if (file.length < 20) {
            throw new IOException("GLB too small");
        }
        ByteBuffer buf = ByteBuffer.wrap(file).order(ByteOrder.LITTLE_ENDIAN);
        byte[] magic = new byte[4];
        buf.get(magic);
        if (!Arrays.equals(magic, new byte[] { 'g', 'l', 'T', 'F' })) {
            throw new IOException("Not a GLB file");
        }
        buf.getInt();
        buf.getInt();
        int chunkLength = buf.getInt();
        byte[] chunkType = new byte[4];
        buf.get(chunkType);
        if (!Arrays.equals(chunkType, new byte[] { 'J', 'S', 'O', 'N' })) {
            throw new IOException("First chunk is not JSON");
        }
        if (chunkLength < 0 || buf.position() + chunkLength > file.length) {
            throw new IOException("Invalid JSON chunk length");
        }
        byte[] jsonBytes = new byte[chunkLength];
        buf.get(jsonBytes);
        return new String(jsonBytes, StandardCharsets.UTF_8);
    }
}
