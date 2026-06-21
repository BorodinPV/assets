package ru.reweu.game.gltf;

import org.joml.Vector3f;

/**
 * Per-triangle tangent accumulation and Gram–Schmidt orthonormalization (glTF-friendly).
 */
public final class TangentSpace {

    private TangentSpace() {
    }

    /**
     * @param outTangent4 per-vertex xyz + handedness sign in w
     */
    public static void computeTangentSpace(
        float[] positions,
        float[] normals,
        float[] texCoords,
        int vertexCount,
        int[] indices,
        float[] outTangent4
    ) {
        float[] tan1 = new float[vertexCount * 3];
        float[] tan2 = new float[vertexCount * 3];
        Vector3f p0 = new Vector3f();
        Vector3f p1 = new Vector3f();
        Vector3f p2 = new Vector3f();
        Vector3f q1 = new Vector3f();
        Vector3f q2 = new Vector3f();
        Vector3f sdir = new Vector3f();
        Vector3f tdir = new Vector3f();

        for (int i = 0; i < indices.length; i += 3) {
            int i0 = indices[i];
            int i1 = indices[i + 1];
            int i2 = indices[i + 2];
            p0.set(positions[i0 * 3], positions[i0 * 3 + 1], positions[i0 * 3 + 2]);
            p1.set(positions[i1 * 3], positions[i1 * 3 + 1], positions[i1 * 3 + 2]);
            p2.set(positions[i2 * 3], positions[i2 * 3 + 1], positions[i2 * 3 + 2]);
            q1.set(p1).sub(p0);
            q2.set(p2).sub(p0);

            float s1 = texCoords[i1 * 2] - texCoords[i0 * 2];
            float t1 = texCoords[i1 * 2 + 1] - texCoords[i0 * 2 + 1];
            float s2 = texCoords[i2 * 2] - texCoords[i0 * 2];
            float t2 = texCoords[i2 * 2 + 1] - texCoords[i0 * 2 + 1];

            float r = s1 * t2 - s2 * t1;
            if (Math.abs(r) < 1e-20f) {
                continue;
            }
            r = 1.0f / r;
            sdir.x = (q1.x * t2 - q2.x * t1) * r;
            sdir.y = (q1.y * t2 - q2.y * t1) * r;
            sdir.z = (q1.z * t2 - q2.z * t1) * r;
            tdir.x = (s1 * q2.x - s2 * q1.x) * r;
            tdir.y = (s1 * q2.y - s2 * q1.y) * r;
            tdir.z = (s1 * q2.z - s2 * q1.z) * r;

            accumulate(tan1, i0, sdir);
            accumulate(tan1, i1, sdir);
            accumulate(tan1, i2, sdir);
            accumulate(tan2, i0, tdir);
            accumulate(tan2, i1, tdir);
            accumulate(tan2, i2, tdir);
        }

        Vector3f n = new Vector3f();
        Vector3f t = new Vector3f();
        Vector3f tmp = new Vector3f();
        Vector3f b = new Vector3f();
        Vector3f t2v = new Vector3f();
        for (int i = 0; i < vertexCount; i++) {
            n.set(normals[i * 3], normals[i * 3 + 1], normals[i * 3 + 2]);
            t.set(tan1[i * 3], tan1[i * 3 + 1], tan1[i * 3 + 2]);
            if (t.lengthSquared() < 1e-20f) {
                outTangent4[i * 4] = 1f;
                outTangent4[i * 4 + 1] = 0f;
                outTangent4[i * 4 + 2] = 0f;
                outTangent4[i * 4 + 3] = 1f;
                continue;
            }
            t.normalize();
            tmp.set(n).mul(n.dot(t));
            t.sub(tmp).normalize();
            b.set(n).cross(t);
            t2v.set(tan2[i * 3], tan2[i * 3 + 1], tan2[i * 3 + 2]);
            float w = (b.dot(t2v) < 0.0f) ? -1.0f : 1.0f;
            outTangent4[i * 4] = t.x;
            outTangent4[i * 4 + 1] = t.y;
            outTangent4[i * 4 + 2] = t.z;
            outTangent4[i * 4 + 3] = w;
        }
    }

    private static void accumulate(float[] arr, int vi, Vector3f v) {
        arr[vi * 3] += v.x;
        arr[vi * 3 + 1] += v.y;
        arr[vi * 3 + 2] += v.z;
    }
}
