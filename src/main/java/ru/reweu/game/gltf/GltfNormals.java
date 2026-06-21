package ru.reweu.game.gltf;

import org.joml.Vector3f;

/** Smooth vertex normals from positions and triangle indices. */
public final class GltfNormals {

    private GltfNormals() {
    }

    public static void computeSmoothNormals(float[] positions, int[] indices, int vertexCount, float[] outNormals) {
        java.util.Arrays.fill(outNormals, 0f);
        Vector3f p0 = new Vector3f();
        Vector3f p1 = new Vector3f();
        Vector3f p2 = new Vector3f();
        Vector3f e1 = new Vector3f();
        Vector3f e2 = new Vector3f();
        Vector3f fn = new Vector3f();
        for (int i = 0; i < indices.length; i += 3) {
            int i0 = indices[i];
            int i1 = indices[i + 1];
            int i2 = indices[i + 2];
            p0.set(positions[i0 * 3], positions[i0 * 3 + 1], positions[i0 * 3 + 2]);
            p1.set(positions[i1 * 3], positions[i1 * 3 + 1], positions[i1 * 3 + 2]);
            p2.set(positions[i2 * 3], positions[i2 * 3 + 1], positions[i2 * 3 + 2]);
            e1.set(p1).sub(p0);
            e2.set(p2).sub(p0);
            fn.set(e1).cross(e2);
            if (fn.lengthSquared() < 1e-30f) {
                continue;
            }
            fn.normalize();
            accumulate(outNormals, i0, fn);
            accumulate(outNormals, i1, fn);
            accumulate(outNormals, i2, fn);
        }
        Vector3f n = new Vector3f();
        for (int v = 0; v < vertexCount; v++) {
            n.set(outNormals[v * 3], outNormals[v * 3 + 1], outNormals[v * 3 + 2]);
            if (n.lengthSquared() < 1e-20f) {
                n.set(0, 1, 0);
            } else {
                n.normalize();
            }
            outNormals[v * 3] = n.x;
            outNormals[v * 3 + 1] = n.y;
            outNormals[v * 3 + 2] = n.z;
        }
    }

    private static void accumulate(float[] out, int vi, Vector3f n) {
        out[vi * 3] += n.x;
        out[vi * 3 + 1] += n.y;
        out[vi * 3 + 2] += n.z;
    }
}
