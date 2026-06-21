package ru.reweu.game.gltf;

/**
 * Флаги расширений материалов glTF по индексу в {@code glTF.materials} (из JSON, не из jgltf API).
 */
public final class GltfMaterialExtensionFlags {

    private final boolean[] clearcoat;
    private final boolean[] transmission;

    public GltfMaterialExtensionFlags(boolean[] clearcoat, boolean[] transmission) {
        this.clearcoat = clearcoat != null ? clearcoat : new boolean[0];
        this.transmission = transmission != null ? transmission : new boolean[0];
    }

    public static GltfMaterialExtensionFlags empty() {
        return new GltfMaterialExtensionFlags(new boolean[0], new boolean[0]);
    }

    public boolean hasClearcoat(int materialIndex) {
        return materialIndex >= 0 && materialIndex < clearcoat.length && clearcoat[materialIndex];
    }

    public boolean hasTransmission(int materialIndex) {
        return materialIndex >= 0 && materialIndex < transmission.length && transmission[materialIndex];
    }

    public boolean needsRoughnessFallback(int materialIndex) {
        return hasClearcoat(materialIndex) || hasTransmission(materialIndex);
    }

    public int materialCount() {
        return Math.max(clearcoat.length, transmission.length);
    }
}
