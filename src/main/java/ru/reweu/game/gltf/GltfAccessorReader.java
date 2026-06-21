package ru.reweu.game.gltf;

import de.javagl.jgltf.model.AccessorByteData;
import de.javagl.jgltf.model.AccessorData;
import de.javagl.jgltf.model.AccessorFloatData;
import de.javagl.jgltf.model.AccessorIntData;
import de.javagl.jgltf.model.AccessorModel;
import de.javagl.jgltf.model.AccessorShortData;
import de.javagl.jgltf.model.AccessorDatas;
import de.javagl.jgltf.model.ElementType;

/**
 * Dense accessor data (jgltf expands sparse accessors in {@link AccessorModel#getAccessorData()}).
 */
public final class GltfAccessorReader {

    private GltfAccessorReader() {
    }

    public static float[] readFloats(AccessorModel am) {
        if (am == null) {
            return null;
        }
        AccessorData ad = AccessorDatas.create(am);
        if (!(ad instanceof AccessorFloatData)) {
            return null;
        }
        AccessorFloatData d = (AccessorFloatData) ad;
        int n = d.getNumElements();
        int c = d.getNumComponentsPerElement();
        float[] out = new float[n * c];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < c; j++) {
                out[i * c + j] = d.get(i, j);
            }
        }
        return out;
    }

    public static int[] readIndices(AccessorModel am) {
        if (am == null) {
            return null;
        }
        AccessorData ad = AccessorDatas.create(am);
        ElementType et = am.getElementType();
        if (et != ElementType.SCALAR) {
            return null;
        }
        int count = ad.getNumElements();
        int[] out = new int[count];
        if (ad instanceof AccessorIntData) {
            AccessorIntData d = (AccessorIntData) ad;
            for (int i = 0; i < count; i++) {
                out[i] = d.get(i);
            }
        } else if (ad instanceof AccessorShortData) {
            AccessorShortData d = (AccessorShortData) ad;
            for (int i = 0; i < count; i++) {
                out[i] = d.get(i) & 0xFFFF;
            }
        } else if (ad instanceof AccessorByteData) {
            AccessorByteData d = (AccessorByteData) ad;
            for (int i = 0; i < count; i++) {
                out[i] = d.get(i) & 0xFF;
            }
        } else {
            return null;
        }
        return out;
    }

    /** Joint indices as float for VBO (small integer joint indices). */
    public static float[] readJointsAsFloats(AccessorModel am) {
        if (am == null) {
            return null;
        }
        AccessorData ad = AccessorDatas.create(am);
        int n = ad.getNumElements();
        int c = ad.getNumComponentsPerElement();
        float[] out = new float[n * c];
        if (ad instanceof AccessorFloatData) {
            AccessorFloatData d = (AccessorFloatData) ad;
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < c; j++) {
                    out[i * c + j] = d.get(i, j);
                }
            }
        } else if (ad instanceof AccessorShortData) {
            AccessorShortData d = (AccessorShortData) ad;
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < c; j++) {
                    out[i * c + j] = d.get(i, j) & 0xFFFF;
                }
            }
        } else if (ad instanceof AccessorByteData) {
            AccessorByteData d = (AccessorByteData) ad;
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < c; j++) {
                    out[i * c + j] = d.get(i, j) & 0xFF;
                }
            }
        } else {
            return null;
        }
        return out;
    }
}
