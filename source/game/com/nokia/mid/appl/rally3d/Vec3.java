package com.nokia.mid.appl.rally3d;

class Vec3 {
    float[] v = new float[4];

    Vec3() {
        this.v[3] = 1.0f;
    }

    Vec3(float f, float f2, float f3) {
        this.v[0] = f;
        this.v[1] = f2;
        this.v[2] = f3;
        this.v[3] = 1.0f;
    }

    Vec3(Vec3 vec3) {
        this.v[0] = vec3.v[0];
        this.v[1] = vec3.v[1];
        this.v[2] = vec3.v[2];
        this.v[3] = vec3.v[3];
    }

    void set(Vec3 vec3) {
        this.v[0] = vec3.v[0];
        this.v[1] = vec3.v[1];
        this.v[2] = vec3.v[2];
        this.v[3] = vec3.v[3];
    }

    void add(Vec3 vec3, Vec3 vec32) {
        this.v[0] = vec3.v[0] + vec32.v[0];
        this.v[1] = vec3.v[1] + vec32.v[1];
        this.v[2] = vec3.v[2] + vec32.v[2];
    }

    void sub(Vec3 vec3, Vec3 vec32) {
        this.v[0] = vec3.v[0] - vec32.v[0];
        this.v[1] = vec3.v[1] - vec32.v[1];
        this.v[2] = vec3.v[2] - vec32.v[2];
    }

    float dot(Vec3 vec3) {
        return this.v[0] * vec3.v[0] + this.v[1] * vec3.v[1] + this.v[2] * vec3.v[2];
    }

    void cross(Vec3 vec3, Vec3 vec32) {
        this.v[0] = vec3.v[1] * vec32.v[2] - vec3.v[2] * vec32.v[1];
        this.v[1] = vec3.v[2] * vec32.v[0] - vec3.v[0] * vec32.v[2];
        this.v[2] = vec3.v[0] * vec32.v[1] - vec3.v[1] * vec32.v[0];
    }

    void normalize() {
        float f = (float)Math.sqrt(this.v[0] * this.v[0] + this.v[1] * this.v[1] + this.v[2] * this.v[2]);
        this.v[0] = this.v[0] / f;
        this.v[1] = this.v[1] / f;
        this.v[2] = this.v[2] / f;
    }
}

