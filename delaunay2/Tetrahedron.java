package delaunay;

import common.Vec3;

class Tetrahedron {
	Vec3[] vertices;
    Vec3 o;
    float   r;
 
    public Tetrahedron(Vec3[] v) {
        this.vertices = v;
        getCenterCircumcircle();
    }
 
    public Tetrahedron(Vec3 v1, Vec3 v2, Vec3 v3, Vec3 v4) {
        this.vertices = new Vec3[4];
        vertices[0] = v1;
        vertices[1] = v2;
        vertices[2] = v3;
        vertices[3] = v4;
        getCenterCircumcircle();
    }
 
    public boolean equals(Tetrahedron t) {
        int count = 0;
        for (Vec3 p1 : this.vertices) {
            for (Vec3 p2 : t.vertices) {
//                if (p1.x == p2.x && p1.y == p2.y && p1.z == p2.z) {
            	if (p1 == p2) {
                    count++;
                }
            }
        }
        if (count == 4) return true;
        return false;
    }
 
    public Line[] getLines() {
    	Vec3 v1 = vertices[0];
    	Vec3 v2 = vertices[1];
    	Vec3 v3 = vertices[2];
    	Vec3 v4 = vertices[3];
 
        Line[] lines = new Line[6];
 
        lines[0] = new Line(v1, v2);
        lines[1] = new Line(v1, v3);
        lines[2] = new Line(v1, v4);
        lines[3] = new Line(v2, v3);
        lines[4] = new Line(v2, v4);
        lines[5] = new Line(v3, v4);
        return lines;
    }
 
    // 外接円も求めちゃう
    private void getCenterCircumcircle() {
    	Vec3 v1 = vertices[0];
    	Vec3 v2 = vertices[1];
    	Vec3 v3 = vertices[2];
    	Vec3 v4 = vertices[3];
 
        float[][] A = {
            {v2.x - v1.x, v2.y-v1.y, v2.z-v1.z},
            {v3.x - v1.x, v3.y-v1.y, v3.z-v1.z},
            {v4.x - v1.x, v4.y-v1.y, v4.z-v1.z}
        };
        float[] b = {
            0.5f * (v2.x*v2.x - v1.x*v1.x + v2.y*v2.y - v1.y*v1.y + v2.z*v2.z - v1.z*v1.z),
            0.5f * (v3.x*v3.x - v1.x*v1.x + v3.y*v3.y - v1.y*v1.y + v3.z*v3.z - v1.z*v1.z),
            0.5f * (v4.x*v4.x - v1.x*v1.x + v4.y*v4.y - v1.y*v1.y + v4.z*v4.z - v1.z*v1.z)
        };
        float[] x = new float[3];
        if (gauss(A, b, x) == 0) {
            o = null;
            r = -1;
        } else {
            o = new Vec3((float)x[0], (float)x[1], (float)x[2]);
            r = o.getDistTo(v1);
        }
    }
 
    private float lu(float[][] a, int[] ip) {
//        int n = a.length;
    	final int n = 3;
        float[] weight = new float[n];
 
        for(int k = 0; k < n; k++) {
            ip[k] = k;
            float u = 0;
            for(int j = 0; j < n; j++) {
                float t = Math.abs(a[k][j]);
                if (t > u) u = t;
            }
            if (u == 0) return 0;
            weight[k] = 1/u;
        }
        float det = 1;
        for(int k = 0; k < n; k++) {
            float u = -1;
            int m = 0;
            for(int i = k; i < n; i++) {
                int ii = ip[i];
                float t = Math.abs(a[ii][k]) * weight[ii];
                if(t>u) { u = t; m = i; }
            }
            int ik = ip[m];
            if (m != k) {
                ip[m] = ip[k]; ip[k] = ik;
                det = -det;
            }
            u = a[ik][k]; det *= u;
            if (u == 0) return 0;
            for (int i = k+1; i < n; i++) {
                int ii = ip[i]; float t = (a[ii][k] /= u);
                for(int j = k+1; j < n; j++) a[ii][j] -= t * a[ik][j];
            }
        }
        return det;
    }
    private void solve(float[][] a, float[] b, int[] ip, float[] x) {
//        int n = a.length;
    	final int n = 3;
        for(int i = 0; i < n; i++) {
            int ii = ip[i]; float t = b[ii];
            for (int j = 0; j < i; j++) t -= a[ii][j] * x[j];
            x[i] = t;
        }
        for (int i = n-1; i >= 0; i--) {
            float t = x[i]; int ii = ip[i];
            for(int j = i+1; j < n; j++) t -= a[ii][j] * x[j];
            x[i] = t / a[ii][i];
        }
    }
    private float gauss(float[][] a, float[] b, float[] x) {
//        int n = a.length;
        int n = 3;
        int[] ip = new int[n];
        float det = lu(a, ip);
 
        if(det != 0) { solve(a, b, ip, x);}
        return det;
    }
}