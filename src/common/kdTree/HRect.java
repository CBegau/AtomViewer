package common.kdTree;

import common.Vec3;

/**
 * Original code released under the Gnu Lesser General Public License:
 * http://home.wlu.edu/~levys/software/kd/
 */
class HRect<T extends Vec3> {

	protected float minX = 0,minY = 0, minZ = 0;
	protected float maxX = 0,maxY = 0, maxZ = 0;

	/**
	 * Creates HRect of infinite size
	 */
	protected HRect() {
		maxX = Float.POSITIVE_INFINITY;
		maxY = Float.POSITIVE_INFINITY;
		maxZ = Float.POSITIVE_INFINITY;
		
		minX = Float.NEGATIVE_INFINITY;
		minY = Float.NEGATIVE_INFINITY;
		minZ = Float.NEGATIVE_INFINITY;
	}

	protected HRect(HRect<T> h) {
		minX = h.minX; minY = h.minY; minZ = h.minZ;
		maxX = h.maxX; maxY = h.maxY; maxZ = h.maxZ;
	}

	protected HRect<T> clone() {
		return new HRect<T>(this);
	}

	protected Vec3 closest(Vec3 t) {
		Vec3 p = new Vec3();

		if (t.x <= minX) p.x = minX;
		else if (t.x >= maxX) p.x = maxX;
		else p.x = t.x;
				
		if (t.y <= minY) p.y = minY;
		else if (t.y >= maxY) p.y = maxY;
		else p.y = t.y;
		
		if (t.z <= minZ) p.z = minZ;
		else if (t.z >= maxZ) p.z = maxZ;
		else p.z = t.z;

		return p;
	}
}
