package model.polygrain.kdTree;

import common.Vec3;

/**
 * Original code released under the Gnu Lesser General Public License:
 * http://home.wlu.edu/~levys/software/kd/
 */
class KDNode<T extends Vec3> {
	T value;
	protected KDNode<T> left, right;

	protected  void insert(T val, int lev) {
		KDNode<T> next_node = null;

		float p,q;
		if (lev == 0){
			p = val.x;
			q = this.value.x;
		} else if (lev == 1){
			p = val.y;
			q = this.value.y;
		} else {
			p = val.z;
			q = this.value.z;
		}
		
		if (p > q) {
			next_node = this.right;
			if (next_node == null) {
				this.right = new KDNode<T>(val);
				return;
			}
		} else {
			next_node = this.left;
			if (next_node == null) {
				this.left = new KDNode<T>(val);
				return;
			}
		}

		int next_lev = (lev + 1) % 3;
		next_node.insert(val, next_lev);
	}

	protected static <T extends Vec3> void nnbr(KDNode<T> kd, Vec3 target, HRect<T> hr, int lev, NearestNeighbor<KDNode<T>> nnl) {
		if (kd == null)
			return;

		//level%3 simplified
		if (lev == 3) lev = 0;

		float sqrDistToTarget = kd.value.getSqrDistTo(target);

		// 4. Cut hr into to sub-hyperrectangles left-hr and right-hr.
		// The cut plane is through pivot and perpendicular to the s
		// dimension.
		HRect<T> left_hr = hr;
		HRect<T> right_hr = hr.clone();
		float p, q;
		
		if (lev == 0){
			left_hr.maxX = kd.value.x;
			right_hr.minX = kd.value.x;
			q = kd.value.x;
			p = target.x;
		} else if (lev == 1){
			left_hr.maxY = kd.value.y;
			right_hr.minY = kd.value.y;
			q = kd.value.y;
			p = target.y;
		} else {
			left_hr.maxZ = kd.value.z;
			right_hr.minZ = kd.value.z;
			q = kd.value.z;
			p = target.z;
		}
		KDNode<T> further_kd;
		HRect<T> further_hr;

		// 6. if target-in-left then
		// 6.1. nearer-kd := left field of kd and nearer-hr := left-hr
		// 6.2. further-kd := right field of kd and further-hr := right-hr
		if (p < q) {
			// 8. Recursively call Nearest Neighbor with paramters
			// (nearer-kd, target, nearer-hr, max-dist-sqd), storing the
			// results in nearest and dist-sqd
			nnbr(kd.left, target, left_hr, lev + 1, nnl);
			further_kd = kd.right;
			further_hr = right_hr;
		} else {
			// 7. if not target-in-left then
			// 7.1. nearer-kd := right field of kd and nearer-hr := right-hr
			// 7.2. further-kd := left field of kd and further-hr := left-hr
			nnbr(kd.right, target, right_hr, lev + 1, nnl);
			further_kd = kd.left;
			further_hr = left_hr;
		}

		// 10. A nearer point could only lie in further-kd if there were some
		// part of further-hr within distance max-dist-sqd of
		// target.
		Vec3 closest = further_hr.closest(target);
		if (closest.getSqrDistTo(target) < nnl.value) {
			if (sqrDistToTarget < nnl.value)
				nnl.set(kd, sqrDistToTarget);
			
			// 10.2 Recursively call Nearest Neighbor with parameters
			// (further-kd, target, further-hr, max-dist_sqd),
			// storing results in temp-nearest and temp-dist-sqd
			nnbr(further_kd, target, further_hr, lev + 1, nnl);
		}
	}

	protected KDNode(T val) {
		value = val;
		left = null;
		right = null;
	}
}
