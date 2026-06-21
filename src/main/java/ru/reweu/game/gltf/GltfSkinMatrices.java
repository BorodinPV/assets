package ru.reweu.game.gltf;

import de.javagl.jgltf.model.NodeModel;
import de.javagl.jgltf.model.SkinModel;
import java.nio.FloatBuffer;
import org.joml.Matrix4f;

/**
 * glTF 2.0 joint matrices: {@code inverse(meshGlobal) * jointGlobal * inverseBindMatrix}.
 */
public final class GltfSkinMatrices {

    public static final int MAX_JOINTS = 64;

    /** Column-major identity; reused for empty joint slots (no per-draw allocation). */
    private static final Matrix4f JOINT_IDENTITY = new Matrix4f();

    private static final float[] SCRATCH_MESH_GLOBAL = new float[16];
    private static final float[] SCRATCH_JOINT_GLOBAL = new float[16];
    private static final float[] SCRATCH_INVERSE_BIND = new float[16];
    private static final Matrix4f SCR_MESH = new Matrix4f();
    private static final Matrix4f SCR_INV = new Matrix4f();
    private static final Matrix4f SCR_JOINT = new Matrix4f();
    private static final Matrix4f SCR_IBM = new Matrix4f();
    private static final Matrix4f SCR_COMBINED = new Matrix4f();

    private GltfSkinMatrices() {
    }

    /**
     * Writes 64 {@code mat4} in order into {@code fb} (std140-compatible column-major layout)
     * for {@code JointBlock} in {@code pbr_gltf.vert}. Resets {@code fb} position to 0.
     *
     * @return number of joints with real skinning matrices (capped); 0 if no skin.
     */
    public static int fillJointBuffer(SkinModel skin, NodeModel meshNode, FloatBuffer fb) {
        fb.clear();
        if (skin == null || meshNode == null) {
            for (int j = 0; j < MAX_JOINTS; j++) {
                JOINT_IDENTITY.get(fb);
            }
            fb.rewind();
            return 0;
        }
        meshNode.computeGlobalTransform(SCRATCH_MESH_GLOBAL);
        SCR_MESH.set(SCRATCH_MESH_GLOBAL);
        SCR_INV.set(SCR_MESH).invert();
        int n = skin.getJoints().size();
        int count = Math.min(n, MAX_JOINTS);
        for (int j = 0; j < count; j++) {
            NodeModel jointNode = skin.getJoints().get(j);
            jointNode.computeGlobalTransform(SCRATCH_JOINT_GLOBAL);
            SCR_JOINT.set(SCRATCH_JOINT_GLOBAL);
            skin.getInverseBindMatrix(j, SCRATCH_INVERSE_BIND);
            SCR_IBM.set(SCRATCH_INVERSE_BIND);
            SCR_COMBINED.set(SCR_INV).mul(SCR_JOINT).mul(SCR_IBM);
            SCR_COMBINED.get(fb);
        }
        for (int j = count; j < MAX_JOINTS; j++) {
            JOINT_IDENTITY.get(fb);
        }
        fb.rewind();
        return count;
    }

    /**
     * Fills {@code out} with {@code jointCount} matrices (column-major); returns actual count (capped).
     */
    public static int computeJointMatrices(SkinModel skin, NodeModel meshNode, Matrix4f[] out) {
        if (skin == null || meshNode == null || out == null) {
            return 0;
        }
        meshNode.computeGlobalTransform(SCRATCH_MESH_GLOBAL);
        SCR_MESH.set(SCRATCH_MESH_GLOBAL);
        SCR_INV.set(SCR_MESH).invert();
        int n = skin.getJoints().size();
        int count = Math.min(n, Math.min(out.length, MAX_JOINTS));
        for (int j = 0; j < count; j++) {
            NodeModel jointNode = skin.getJoints().get(j);
            jointNode.computeGlobalTransform(SCRATCH_JOINT_GLOBAL);
            SCR_JOINT.set(SCRATCH_JOINT_GLOBAL);
            skin.getInverseBindMatrix(j, SCRATCH_INVERSE_BIND);
            SCR_IBM.set(SCRATCH_INVERSE_BIND);
            SCR_COMBINED.set(SCR_INV).mul(SCR_JOINT).mul(SCR_IBM);
            out[j] = new Matrix4f(SCR_COMBINED);
        }
        return count;
    }
}
