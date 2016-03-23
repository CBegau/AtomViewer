package common.kdTree;

/**
 * Original code released under the Gnu Lesser General Public License:
 * http://home.wlu.edu/~levys/software/kd/
 */
class NearestNeighbor<T> {
    T data;
    float value;

    public NearestNeighbor() {
        data = null;
        value = Float.POSITIVE_INFINITY;
    }

	public void set(T object, float priority) {
		this.data = object;
		this.value = priority;
	}
}
