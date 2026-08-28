// icosahedron_math.js
// Port of Icosahedron.kt chiral rotation matrix generator and H3 normal vector calculation

const PHI = (1.0 + Math.sqrt(5.0)) / 2.0;

class Vec3 {
  constructor(x, y, z) {
    this.x = x; this.y = y; this.z = z;
  }
  length() { return Math.sqrt(this.x * this.x + this.y * this.y + this.z * this.z); }
  normalize() {
    const l = this.length();
    return l > 1e-7 ? new Vec3(this.x / l, this.y / l, this.z / l) : new Vec3(0, 0, 0);
  }
  dot(o) { return this.x * o.x + this.y * o.y + this.z * o.z; }
}

class Mat3 {
  constructor(m00, m01, m02, m10, m11, m12, m20, m21, m22) {
    this.m00 = m00; this.m01 = m01; this.m02 = m02;
    this.m10 = m10; this.m11 = m11; this.m12 = m12;
    this.m20 = m20; this.m21 = m21; this.m22 = m22;
  }
  static identity() {
    return new Mat3(1, 0, 0, 0, 1, 0, 0, 0, 1);
  }
  static fromAxisAngle(axis, angle) {
    const n = axis.normalize();
    const c = Math.cos(angle);
    const s = Math.sin(angle);
    const t = 1.0 - c;
    return new Mat3(
      t * n.x * n.x + c,       t * n.x * n.y - s * n.z, t * n.x * n.z + s * n.y,
      t * n.x * n.y + s * n.z, t * n.y * n.y + c,       t * n.y * n.z - s * n.x,
      t * n.x * n.z - s * n.y, t * n.y * n.z + s * n.x, t * n.z * n.z + c
    );
  }
  times(o) {
    return new Mat3(
      this.m00 * o.m00 + this.m01 * o.m10 + this.m02 * o.m20,
      this.m00 * o.m01 + this.m01 * o.m11 + this.m02 * o.m21,
      this.m00 * o.m02 + this.m01 * o.m12 + this.m02 * o.m22,

      this.m10 * o.m00 + this.m11 * o.m10 + this.m12 * o.m20,
      this.m10 * o.m01 + this.m11 * o.m11 + this.m12 * o.m21,
      this.m10 * o.m02 + this.m11 * o.m12 + this.m12 * o.m22,

      this.m20 * o.m00 + this.m21 * o.m10 + this.m22 * o.m20,
      this.m20 * o.m01 + this.m21 * o.m11 + this.m22 * o.m21,
      this.m20 * o.m02 + this.m21 * o.m12 + this.m22 * o.m22
    );
  }
  distanceSquared(o) {
    const d00 = this.m00 - o.m00, d01 = this.m01 - o.m01, d02 = this.m02 - o.m02;
    const d10 = this.m10 - o.m10, d11 = this.m11 - o.m11, d12 = this.m12 - o.m12;
    const d20 = this.m20 - o.m20, d21 = this.m21 - o.m21, d22 = this.m22 - o.m22;
    return d00*d00 + d01*d01 + d02*d02 + d10*d10 + d11*d11 + d12*d12 + d20*d20 + d21*d21 + d22*d22;
  }
}

const pole3 = new Vec3(1, 1, 1).normalize();
const pole5 = new Vec3(0, 1, PHI).normalize();

function generateIcosahedralRotations() {
  const angle5 = (2.0 * Math.PI) / 5.0;
  const angle3 = (2.0 * Math.PI) / 3.0;

  const axes5 = [
    new Vec3(0, 1, PHI).normalize(),
    new Vec3(0, -1, PHI).normalize(),
    new Vec3(PHI, 0, 1).normalize(),
    new Vec3(-PHI, 0, 1).normalize(),
    new Vec3(1, PHI, 0).normalize(),
    new Vec3(-1, PHI, 0).normalize()
  ];

  const axes3 = [
    new Vec3(1, 1, 1).normalize(),
    new Vec3(-1, 1, 1).normalize(),
    new Vec3(1, -1, 1).normalize(),
    new Vec3(1, 1, -1).normalize()
  ];

  const baseGenerators = [];
  for (const a of axes5) {
    baseGenerators.push(Mat3.fromAxisAngle(a, angle5));
    baseGenerators.push(Mat3.fromAxisAngle(a, -angle5));
  }
  for (const a of axes3) {
    baseGenerators.push(Mat3.fromAxisAngle(a, angle3));
    baseGenerators.push(Mat3.fromAxisAngle(a, -angle3));
  }

  const result = [Mat3.identity()];
  const queue = [Mat3.identity()];

  function isNew(m) {
    for (const ex of result) {
      if (ex.distanceSquared(m) < 1e-4) return false;
    }
    return true;
  }

  while (queue.length > 0 && result.length < 60) {
    const curr = queue.shift();
    for (const g of baseGenerators) {
      const next = curr.times(g);
      if (isNew(next)) {
        result.push(next);
        queue.push(next);
        if (result.length === 60) break;
      }
    }
  }

  return result;
}

const rotationMatrices = generateIcosahedralRotations();

function slerp(p0, p1, t) {
  const clampedT = Math.max(0, Math.min(1, t));
  const dot = Math.max(-1, Math.min(1, p0.dot(p1)));
  const theta = Math.acos(dot);
  const sinTheta = Math.sin(theta);
  if (Math.abs(sinTheta) < 1e-5) {
    return new Vec3(
      p0.x * (1 - clampedT) + p1.x * clampedT,
      p0.y * (1 - clampedT) + p1.y * clampedT,
      p0.z * (1 - clampedT) + p1.z * clampedT
    ).normalize();
  }
  const w0 = Math.sin((1 - clampedT) * theta) / sinTheta;
  const w1 = Math.sin(clampedT * theta) / sinTheta;
  return new Vec3(
    p0.x * w0 + p1.x * w1,
    p0.y * w0 + p1.y * w1,
    p0.z * w0 + p1.z * w1
  ).normalize();
}

const targetNormalsArray = new Float32Array(180);

export function generateH3Normals(controlY) {
  const gen = slerp(pole3, pole5, controlY);
  let uniqueCount = 0;

  for (let i = 0; i < 60; i++) {
    const m = rotationMatrices[i];
    const vx = m.m00 * gen.x + m.m01 * gen.y + m.m02 * gen.z;
    const vy = m.m10 * gen.x + m.m11 * gen.y + m.m12 * gen.z;
    const vz = m.m20 * gen.x + m.m21 * gen.y + m.m22 * gen.z;

    const len = Math.sqrt(vx * vx + vy * vy + vz * vz);
    const invLen = len > 1e-7 ? 1.0 / len : 1.0;
    const nx = vx * invLen;
    const ny = vy * invLen;
    const nz = vz * invLen;

    let isDup = false;
    for (let j = 0; j < uniqueCount; j++) {
      const dx = targetNormalsArray[j * 3] - nx;
      const dy = targetNormalsArray[j * 3 + 1] - ny;
      const dz = targetNormalsArray[j * 3 + 2] - nz;
      if (dx * dx + dy * dy + dz * dz < 1e-9) {
        isDup = true;
        break;
      }
    }

    if (!isDup) {
      targetNormalsArray[uniqueCount * 3] = nx;
      targetNormalsArray[uniqueCount * 3 + 1] = ny;
      targetNormalsArray[uniqueCount * 3 + 2] = nz;
      uniqueCount++;
    }
  }

  for (let k = uniqueCount * 3; k < 180; k++) {
    targetNormalsArray[k] = 0;
  }

  return { planeCount: uniqueCount, normals: targetNormalsArray };
}
