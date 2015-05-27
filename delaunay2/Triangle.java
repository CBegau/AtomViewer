package delaunay;

import common.Vec3;

class Triangle {
    public Vec3 v1, v2, v3;
    public Triangle(Vec3 v1, Vec3 v2, Vec3 v3) {
        this.v1 = v1;
        this.v2 = v2;
        this.v3 = v3;
    }

    public Vec3 getNormal() {
        Vec3 edge1 = new Vec3(v2.x-v1.x, v2.y-v1.y, v2.z-v1.z);
        Vec3 edge2 = new Vec3(v3.x-v1.x, v3.y-v1.y, v3.z-v1.z);
 
        Vec3 normal = edge1.cross(edge2);
        normal.normalize();
        return normal;
    }
 
    public void turnBack() {
    	Vec3 tmp = this.v3;
        this.v3 = this.v1;
        this.v1 = tmp;
    }
 
    public Line[] getLines() {
        Line[] l = {
            new Line(v1, v2),
            new Line(v2, v3),
            new Line(v3, v1)
        };
        return l;
    }
 
    public boolean equals(Triangle t) {
        Line[] lines1 = this.getLines();
        Line[] lines2 = t.getLines();
 
        int cnt = 0;
        for(int i = 0; i < lines1.length; i++) {
            for(int j = 0; j < lines2.length; j++) {
                if (lines1[i].equals(lines2[j]))
                    cnt++;
            }
        }
        if (cnt == 3) return true;
        else return false;
 
    }
}