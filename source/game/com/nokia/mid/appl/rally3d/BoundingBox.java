package com.nokia.mid.appl.rally3d;

class BoundingBox {
    Vec3 min = new Vec3();
    Vec3 max = new Vec3();

    BoundingBox() {
    }

    boolean containsXZ(Vec3 vec3) {
        if (vec3.v[0] < this.min.v[0]) {
            return false;
        }
        if (vec3.v[2] < this.min.v[2]) {
            return false;
        }
        if (vec3.v[0] > this.max.v[0]) {
            return false;
        }
        return !(vec3.v[2] > this.max.v[2]);
    }
}

