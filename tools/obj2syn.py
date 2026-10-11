#!/usr/bin/env python3
"""OBJ -> Rally3D .syn converter for add-on cars:   python tools/obj2syn.py model.obj car.syn [--scale S] [--flip-z] [--no-snap]

Put the result into your add-on zip as car.syn (or wheel.syn for a wheel model).
Model space: +Z = front of the car, +Y = up, +X = left/right, 1 unit = 1 metre-ish (stock car: 2.7 wide, 5.4 long, 1.5 tall).
 --scale S    multiply all coordinates (stock cars are about 5.4 long)
 --flip-z     mirror the model along Z (use if your car drives backwards)
 --no-snap    do not move the car so its lowest point sits at Y=0.5 like the stock bodies (wheels use their own centre, keep it for wheel.syn)
Limits: coordinates within +-32 units, at most 65535 vertices / 20000 triangles. UV: v is flipped for you (OBJ v=0 is the bottom).
The triangle winding is checked against the stock cars (closed mesh assumed) and flipped automatically if the model would be inside-out.
"""
import struct, sys

def parse(path):
    V, T, tris = [], [], []
    for line in open(path, encoding='utf-8', errors='replace'):
        t = line.split()
        if not t: continue
        if t[0] == 'v': V.append(tuple(float(x) for x in t[1:4]))
        elif t[0] == 'vt': T.append((float(t[1]), float(t[2]) if len(t) > 2 else 0.0))
        elif t[0] == 'f':
            face = []
            for tok in t[1:]:
                p = tok.split('/')
                vi = int(p[0]); vi = vi - 1 if vi > 0 else len(V) + vi
                ti = None
                if len(p) > 1 and p[1]:
                    ti = int(p[1]); ti = ti - 1 if ti > 0 else len(T) + ti
                face.append((vi, ti))
            for k in range(1, len(face) - 1): tris.append((face[0], face[k], face[k + 1]))
    return V, T, tris

def main():
    a = sys.argv[1:]
    if len(a) < 2 or a[0].startswith('-'):
        print(__doc__); sys.exit(1)
    src, dst = a[0], a[1]
    scale = 1.0; flip_z = '--flip-z' in a; snap = '--no-snap' not in a
    if '--scale' in a: scale = float(a[a.index('--scale') + 1])
    V, T, tris = parse(src)
    if not tris: sys.exit('no faces in OBJ')
    V = [(x * scale, y * scale, (-z if flip_z else z) * scale) for x, y, z in V]
    if flip_z: tris = [(c, b, aa) for aa, b, c in tris]
    if snap:
        dy = 0.5 - min(v[1] for v in V)
        V = [(x, y + dy, z) for x, y, z in V]
    index, verts, uvs, idx = {}, [], [], []
    for tri in tris:
        for vi, ti in tri:
            key = (vi, ti)
            if key not in index:
                index[key] = len(verts)
                verts.append(V[vi])
                u, v = T[ti] if ti is not None and T else (0.0, 0.0)
                uvs.append((u, 1.0 - v))
            idx.append(index[key])
    # winding: signed volume must have the same sign as the stock cars (stock: computed once from a known-good layout = positive)
    vol = 0.0
    for i in range(0, len(idx), 3):
        p0, p1, p2 = (verts[idx[i + k]] for k in range(3))
        vol += (p0[0] * (p1[1] * p2[2] - p1[2] * p2[1]) - p0[1] * (p1[0] * p2[2] - p1[2] * p2[0]) + p0[2] * (p1[0] * p2[1] - p1[1] * p2[0])) / 6.0
    if vol * STOCK_SIGN < 0:
        for i in range(0, len(idx), 3): idx[i + 1], idx[i + 2] = idx[i + 2], idx[i + 1]
        print('winding flipped (model looked inside-out)')
    n = len(verts)
    if n > 65535 or len(idx) > 60000: sys.exit('too big: %d vertices, %d triangles (max 65535 / 20000)' % (n, len(idx) // 3))
    for v in verts:
        if max(abs(c) for c in v) > 32: sys.exit('coordinate out of +-32 range: %s (use --scale)' % (v,))
    out = bytearray(struct.pack('<5I', 24, 24 + 12 * n, 0, 0, 0))
    out += struct.pack('<HH', n, 0)
    for x, y, z in verts: out += struct.pack('<3i', int(round(x * 16000)), int(round(y * 16000)), int(round(z * 16000)))
    for u, v in uvs: out += struct.pack('<2H', max(0, min(1016, int(round(u * 1024)))), max(0, min(1016, int(round(v * 1024)))))
    out += struct.pack('<I', len(idx))
    out += struct.pack('<%dH' % len(idx), *idx)
    open(dst, 'wb').write(out)
    print('%s: %d vertices, %d triangles, %d bytes' % (dst, n, len(idx) // 3, len(out)))

STOCK_SIGN = 1
if __name__ == '__main__': main()
