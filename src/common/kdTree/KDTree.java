package common.kdTree;

import common.Vec3;

/**
 * KDTree is a class supporting KD-tree insertion and nearest neighbor in three dimensions.
 * Splitting dimension is chosen naively, by depth modulo 3. Semantics are
 * as follows:
 * 
 * <UL>
 * <LI>Two different keys containing identical numbers should retrieve the same
 * value from a given KD-tree. Therefore keys are cloned when a node is
 * inserted. <BR>
 * <BR>
 * <LI>As with Hashtables, values inserted into a KD-tree are <I>not</I> cloned.
 * Modifying a value between insertion and retrieval will therefore modify the
 * value stored in the tree.
 * </UL>
 * 
 * Implements the Nearest Neighbor algorithm (Table 6.4) of
 * 
 * <PRE>
 * &#064;techreport{AndrewMooreNearestNeighbor,
 *   author  = {Andrew Moore},
 *   title   = {An introductory tutorial on kd-trees},
 *   institution = {Robotics Institute, Carnegie Mellon University},
 *   year    = {1991},
 *   number  = {Technical Report No. 209, Computer Laboratory, 
 *              University of Cambridge},
 *   address = {Pittsburgh, PA}
 * }
 * </PRE>
 * 
 *  and insertion uses algorithm translated from 352.ins.c of
 * 
 * <PRE>
 *   &#064;Book{GonnetBaezaYates1991,                                   
 *     author =    {G.H. Gonnet and R. Baeza-Yates},
 *     title =     {Handbook of Algorithms and Data Structures},
 *     publisher = {Addison-Wesley},
 *     year =      {1991}
 *   }
 * </PRE>
 * 
 * @author Simon Levy, Bjoern Heckel
 * @version %I%, %G%
 * @since JDK1.2
 * 
 *        
 *  modified by C. Begau for float-Arrays. Additional modification are made to improve
 *  speed at the cost, that only 3D-coordinates and a fraction of operations are supported now
 *  
 *        
 *  Original code released under the Gnu Lesser General Public License:
 *  http://home.wlu.edu/~levys/software/kd/
 *  
 */
public class KDTree<T extends Vec3> {
	private KDNode<T> root;

	/**
	 * @param value value at that key including the key
	 */
	public void insert(T value) {
		if (null == root) {
			root = new KDNode<T>(value);
			return;
		}
		root.insert(value, 0);
	}
	
	/**
	 * Find KD-tree node whose key is nearest neighbor to key.
	 * @param key key for KD-tree node
	 * @return object at node nearest to key, or null on failure
	 */
	public T getNearest(Vec3 key) {
		NearestNeighbor<KDNode<T>> nnl = new NearestNeighbor<KDNode<T>>();
		if (root != null) {
			// initial call is with infinite hyper-rectangle and max distance
			HRect<T> hr = new HRect<T>();
			KDNode.nnbr(root, key, hr, 0, nnl);
		}

		if (nnl.data == null) return null;
		else return nnl.data.value;
	}
}
