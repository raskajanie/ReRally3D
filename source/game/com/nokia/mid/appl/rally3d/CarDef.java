package com.nokia.mid.appl.rally3d;

import javax.microedition.m3g.Texture2D;

/** One drivable car: stock (0..3) or from an add-on zip (local or received over multiplayer). */
final class CarDef {
    String name = "Car";
    /** Top speed in the stock statSpeed units: km/h shown = speed * 1.5, world speed = speed / 10. */
    int speed = 60;
    /** Handling multipliers, 1.0 = stock physics. */
    float accel = 1.0f, steer = 1.0f, steerMax = 1.0f, grip = 1.0f;
    /** Bars for the car-select screen, 0..100. */
    int barSpeed, barAccel, barGrip;
    Mesh3D mesh;
    Texture2D skin;
    /** null = use the shared stock wheel mesh / stock (or global override) wheel skin. */
    Mesh3D wheelMesh;
    Texture2D wheelSkin;
    /** Add-on this car came from (null for stock cars). */
    AddonPack pack;
    boolean remote;
}
