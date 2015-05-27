package delaunay;

import common.Vec3;

class Line {
    public Vec3 start, end;
    public Line(Vec3 start, Vec3 end) {
        this.start = start;
        this.end = end;
    }
 
    public void reverse() {
    	Vec3 tmp = this.start;
        this.start = this.end;
        this.end = tmp;
    }
 
    // 同じかどうか
    public boolean equals(Line l) {
        if ((this.start == l.start && this.end == l.end)
                || (this.start == l.end && this.end == l.start))
            return true;
        return false;
    }
}