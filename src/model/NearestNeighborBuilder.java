// Part of AtomViewer: AtomViewer is a tool to display and analyse
// atomistic simulations
//
// Copyright (C) 2014  ICAMS, Ruhr-Universität Bochum
//
// AtomViewer is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// AtomViewer is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License along
// with AtomViewer. If not, see <http://www.gnu.org/licenses/> 

package model;

import java.util.*;
import java.util.concurrent.Callable;

import common.ThreadPool;
import common.Tupel;
import common.Vec3;

/**
 * The NearestNeighborBuilder provides an efficient data structure to identify
 * all neighbors within a specified distance in Cartesian space. The cut-off radius and a 
 * bounding box size must be defined at creation of the builder. 
 * After creation the data-structure must first be filled 
 * with a set of elements which are consider neighbors later using the {@link #add(Vec3)} method.
 * The element to find neighbors for is not required to be inserted before.
 * 
 * After this initialization is a list of all neighbors in the cutoff-radius can be retrieved
 * using different getNeigh-methods.
 * The nearest neighbor builder produces correct results for the case of non-orthogonal and periodic
 * domains. In case of non-periodic systems, correct results are produced if elements are placed
 * outside the defined bounding box, but this may have a negative impact on performance. 
 * Internally a linked-cell scheme is used to detect neighbors, thus the runtime does not depend on the size
 * of the simulation box. The getNeigh methods are thread safe. The thread-safety of add and remove can be requested
 * during construction.
 * @param <T> The nearest neighbor builder can be used for any classes that is derived from {@link common.Vec3}
 */
public class NearestNeighborBuilder<T extends Vec3> {
	private BoxParameter box;
	private final int dimX, dimY, dimZ, dimYZ;
	private final boolean pbcX, pbcY, pbcZ;
	private final float sqrCutoff;
	private final float cutoff; 
	
	private final List<T>[] cells;
	private final int[] cellOffsets = new int[27];
	private boolean threadSafeAdd = false;
	private Object mutex = new Object();
	
	/**
	 * Creates a nearest neighbor builder with a given bounding box and periodicity
	 * Add and remove methods are not thread safe using this constructor.
	 * @param box Geometry of the bounding box 
	 * @param cutoffRadius The cut-off radius for neighbors
	 * @param pbc A three dimensional array indicating in which directions periodicity is enabled
	 */
	public NearestNeighborBuilder(BoxParameter box, float cutoffRadius) {
		this(box, cutoffRadius, false);
	}
	
	
	/**
	 * Creates a nearest neighbor builder with a given bounding box and periodicity
	 * @param box Geometry of the bounding box 
	 * @param cutoffRadius The cut-off radius for neighbors
	 * @param threadSafeAdd if set to true, adding and removing elements is threadsafe
	 */
	@SuppressWarnings("unchecked")
	public NearestNeighborBuilder(BoxParameter box, float cutoffRadius, boolean threadSafeAdd) {
		this.box = box;
		this.threadSafeAdd = threadSafeAdd;
		
		this.sqrCutoff = cutoffRadius*cutoffRadius;
		this.cutoff = cutoffRadius;
		Vec3 dim = box.getCellDim(cutoffRadius);
		
		dimX = ((int)(dim.x)) == 0 ? 1 : (int)(dim.x);
		dimY = ((int)(dim.y)) == 0 ? 1 : (int)(dim.y);
		dimZ = ((int)(dim.z)) == 0 ? 1 : (int)(dim.z);
		dimYZ = dimY*dimZ;
		         
		cells = new ArrayList[dimX*dimY*dimZ];
		
		this.pbcX = box.getPbc()[0];
		this.pbcY = box.getPbc()[1];
		this.pbcZ = box.getPbc()[2];
		
		//Access pattern of linked cells in the linear array
		int l=0;
		for (int i=-1; i<=1; i++)	
			for (int j=-1; j<=1; j++)
				for (int k=-1; k<=1; k++)
					cellOffsets[l++] = i*dimYZ + j*dimZ + k;
	}
	
	public void addAll(final List<? extends T> c){
		this.addAll(c, null);
	}
	
	public void addAll(final List<? extends T> c, final Filter<T> filter){
		if (threadSafeAdd){
			Vector<Callable<Void>> parallelTasks = new Vector<Callable<Void>>();
			for (int i=0; i<ThreadPool.availProcessors(); i++){
				final int j = i;
				parallelTasks.add(new Callable<Void>() {
					@Override
					public Void call() throws Exception {
						final int start = (int)(((long)c.size() * j)/ThreadPool.availProcessors());
						final int end = (int)(((long)c.size() * (j+1))/ThreadPool.availProcessors());
						
						if (filter == null){
							for (int i=start; i<end; i++){
								add(c.get(i));
							}
						} else {
							for (int i=start; i<end; i++){
								T t = c.get(i);
								if (filter.accept(t))
									add(t);
							}
						}
						
						return null;
					}
				});
			}
			ThreadPool.executeParallel(parallelTasks);
		} else {
			if (filter == null){
				for (int i=0; i<c.size(); i++){
					add(c.get(i));
				}
			} else {
				for (int i=0; i<c.size(); i++){
					T t = c.get(i);
					if (filter.accept(t))
						add(t);
				}
			}
		}
	}

	/**
	 * Add an element that is later considered in the getNeigh methods.
	 * It is not checked if an equal element is added already.
	 * This method is only thread-safe if requested during construction of the NearestNeighborBuilder
	 * @param c Element to add
	 */
	public void add(T c){
		int x = (int) (dimX * c.dot(box.getTBoxSize()[0]));
		int y = (int) (dimY * c.dot(box.getTBoxSize()[1]));
		int z = (int) (dimZ * c.dot(box.getTBoxSize()[2]));
		
		if (x<0) x=0;
		else if (x>=dimX) x=dimX-1;
		if (y<0) y=0;
		else if (y>=dimY) y=dimY-1;
		if (z<0) z=0;
		else if (z>=dimZ) z=dimZ-1;
		
		int p = x*dimYZ+y*dimZ+z;
		if (threadSafeAdd){
			if (cells[p] == null){
				//Create a list. This operation must be threadsafe
				synchronized (mutex) {
					// Test again, another thread might have created the array while
					// waiting for the lock
					//Create a wrapped array list that synchronizes only add and remove
					if (cells[p] == null) 
						cells[p] = new LimitedSynchronizedList<T>(5);
				}
			}
		} else {
			if (cells[p] == null)
				cells[p] = new ArrayList<T>(5);
		}
		
		cells[p].add(c);
	}
	
	/**
	 * Tries to remove a given element from the nearest neighbor builder.
	 * It will only remove the first element that is equal to the given argument.
	 * This method is only thread-safe if requested during construction of the NearestNeighborBuilder
	 * @param c An element that should be equal to the element to be removed 
	 * @return True if an equal element has been removed. Otherwise false is returned. 
	 */
	public boolean remove(T c) {
		int x = (int) (dimX * c.dot(box.getTBoxSize()[0]));
		int y = (int) (dimY * c.dot(box.getTBoxSize()[1]));
		int z = (int) (dimZ * c.dot(box.getTBoxSize()[2]));
		
		if (x<0) x=0;
		else if (x>=dimX) x=dimX-1;
		if (y<0) y=0;
		else if (y>=dimY) y=dimY-1;
		if (z<0) z=0;
		else if (z>=dimZ) z=dimZ-1;
		
		int p = x*dimYZ+y*dimZ+z;
		
		if (cells[p] != null)
			return cells[p].remove(c);
		return false;
	}
	
	public float getCutoff() {
		return cutoff;
	}
	
	/**
	 * Creates a list of nearest neighbors within the cut-off radius and are
	 * not equal to the given argument c
	 * @param c an element defining the center of the sphere in which neighbors are found
	 * @return a ArrayList containing all neighbors around the vicinity of c
	 */
	public ArrayList<T> getNeigh(Vec3 c){
		int x = (int) (dimX * c.dot(box.getTBoxSize()[0]));
		int y = (int) (dimY * c.dot(box.getTBoxSize()[1]));
		int z = (int) (dimZ * c.dot(box.getTBoxSize()[2]));
		
		if (x < 0) x = 0;
		else if (x >= dimX) x = dimX - 1;
		if (y < 0) y = 0;
		else if (y >= dimY) y = dimY - 1;
		if (z < 0) z = 0;
		else if (z >= dimZ) z = dimZ - 1;
		
		boolean safeAccess = (x>0 && y>0 && z>0 && x<dimX-1 && y<dimY-1 && z<dimZ-1);
		
		ArrayList<T> neigh = new ArrayList<T>(15);
		
		if (safeAccess){
			//No need to handle boundary conditions
			int p = x*dimYZ + y*dimZ + z;
			for (int i=0; i<27; i++){				
				List<T> possibleNeigh = cells[p+cellOffsets[i]];
				if (possibleNeigh!=null){
					for (int l=0; l<possibleNeigh.size(); l++){
						T s = possibleNeigh.get(l);
						if (!s.equals(c) && c.getSqrDistTo(s)<=sqrCutoff) neigh.add(s);
					}
				}
			}
		} else {
			//Access with handling boundary conditions
			Vec3 v = new Vec3();
			for (int i=-1; i<=1; i++){
				Vec3 pbcCorrectionX = new Vec3();
				if (pbcX) {
					if (x + i < 0) pbcCorrectionX.sub(box.getBoxSize()[0]);
					else if (x + i >= this.dimX) pbcCorrectionX.add(box.getBoxSize()[0]);
				} 
				
				for (int j=-1; j<=1; j++){
					Vec3 pbcCorrectionY = pbcCorrectionX.clone();
					if (pbcY) {
						if (y + j < 0) pbcCorrectionY.sub(box.getBoxSize()[1]);
						else if (y + j >= this.dimY) pbcCorrectionY.add(box.getBoxSize()[1]);
					}
					
					for (int k=-1; k<=1; k++){
						Vec3 pbcCorrection = pbcCorrectionY.clone();
						if (pbcZ) {
							if (z + k < 0) pbcCorrection.sub(box.getBoxSize()[2]);
							else if (z + k >= this.dimZ) pbcCorrection.add(box.getBoxSize()[2]);
						}
						v.setTo(c).sub(pbcCorrection);
						
						List<T> possibleNeigh = accessCell(x+i, y+j, z+k);
						if (possibleNeigh!=null){
							for (int l=0; l<possibleNeigh.size(); l++){
								T s = possibleNeigh.get(l);
								if (!s.equals(c) && v.getSqrDistTo(s)<=sqrCutoff) neigh.add(s);
							}
						}
					}	
				}	
			}
		}
		
		return neigh;
	}
	
	/**
	 * Creates a list of vectors towards nearest neighbors within the cut-off radius and are
	 * not equal to the given argument c. The returned vectors provide the minimal direction
	 * to the nearest neighbors from the given coordinate considering periodicity. The length of each returned vector is 
	 * less or equal to the cut-off radius.
	 * @param c an element defining the center of the sphere in which neighbors are found 
	 * @return a ArrayList containing the vectors to neighbors around the vicinity of c
	 */
	public ArrayList<Vec3> getNeighVec(Vec3 c){
		int x = (int) (dimX * c.dot(box.getTBoxSize()[0]));
		int y = (int) (dimY * c.dot(box.getTBoxSize()[1]));
		int z = (int) (dimZ * c.dot(box.getTBoxSize()[2]));
		
		if (x < 0) x = 0;
		else if (x >= dimX) x = dimX - 1;
		if (y < 0) y = 0;
		else if (y >= dimY) y = dimY - 1;
		if (z < 0) z = 0;
		else if (z >= dimZ) z = dimZ - 1;
		
		boolean safeAccess = (x>0 && y>0 && z>0 && x<dimX-1 && y<dimY-1 && z<dimZ-1);
		
		ArrayList<Vec3> neigh = new ArrayList<Vec3>(15);
		
		if (safeAccess){
			//No need to handle boundary conditions
			int p = x*dimYZ + y*dimZ + z;
			for (int i=0; i<27; i++){				
				List<T> possibleNeigh = cells[p+cellOffsets[i]];
				if (possibleNeigh!=null){
					for (int l=0; l<possibleNeigh.size(); l++){
						T s = possibleNeigh.get(l);
						Vec3 t = s.subClone(c);
						if (!s.equals(c) && t.getLengthSqr()<=sqrCutoff) neigh.add(t);
					}
				}
			}
		} else {
			//Access with handling boundary conditions
			for (int i=-1; i<=1; i++){
				Vec3 pbcCorrectionX = new Vec3();
				if (pbcX) {
					if (x + i < 0) pbcCorrectionX.sub(box.getBoxSize()[0]);
					else if (x + i >= this.dimX) pbcCorrectionX.add(box.getBoxSize()[0]);
				} 
				
				for (int j=-1; j<=1; j++){
					Vec3 pbcCorrectionY = pbcCorrectionX.clone();
					if (pbcY) {
						if (y + j < 0) pbcCorrectionY.sub(box.getBoxSize()[1]);
						else if (y + j >= this.dimY) pbcCorrectionY.add(box.getBoxSize()[1]);
					}
					
					for (int k=-1; k<=1; k++){
						Vec3 pbcCorrection = pbcCorrectionY.clone();
						if (pbcZ) {
							if (z + k < 0) pbcCorrection.sub(box.getBoxSize()[2]);
							else if (z + k >= this.dimZ) pbcCorrection.add(box.getBoxSize()[2]);
						}
						Vec3 cclone = c.clone().sub(pbcCorrection);
						
						List<T> possibleNeigh = accessCell(x + i, y + j, z + k);
						if (possibleNeigh!=null){
							for (int l=0; l<possibleNeigh.size(); l++){
								T s = possibleNeigh.get(l);
								Vec3 t = s.subClone(cclone);
								if (!s.equals(c) && t.getLengthSqr()<=sqrCutoff) neigh.add(t);
							}
						}
					}	
				}	
			}
		}
		
		return neigh;
	}
	
	/**
	 * Creates a list with Tupels of values containing the nearest neighbors and the direction vectors
	 * from the given coordinate to them.
	 * It combines the data provided by {@linkplain #getNeigh(Vec3)} and {@linkplain #getNeighVec(Vec3)} 
	 * @param c an element defining the center of the sphere in which neighbors are found  
	 * @return a ArrayList containing the neighbors and the vectors to neighbors as Tupel around the vicinity of c
	 */
	public ArrayList<Tupel<T, Vec3>> getNeighAndNeighVec(Vec3 c){
		int x = (int) (dimX * c.dot(box.getTBoxSize()[0]));
		int y = (int) (dimY * c.dot(box.getTBoxSize()[1]));
		int z = (int) (dimZ * c.dot(box.getTBoxSize()[2]));
		
		if (x < 0) x = 0;
		else if (x >= dimX) x = dimX - 1;
		if (y < 0) y = 0;
		else if (y >= dimY) y = dimY - 1;
		if (z < 0) z = 0;
		else if (z >= dimZ) z = dimZ - 1;
		
		//No need to handle boundary conditions
		boolean safeAccess = (x>0 && y>0 && z>0 && x<dimX-1 && y<dimY-1 && z<dimZ-1);
		
		ArrayList<Tupel<T, Vec3>> neigh = new ArrayList<Tupel<T, Vec3>>(15);
		
		if (safeAccess){
			//No need to handle boundary conditions
			int p = x*dimYZ + y*dimZ + z;
			for (int i=0; i<27; i++){				
				List<T> possibleNeigh = cells[p+cellOffsets[i]];
				if (possibleNeigh!=null){
					for (int l=0; l<possibleNeigh.size(); l++){
						T s = possibleNeigh.get(l);
						Vec3 t = s.subClone(c);
						if (!s.equals(c) && t.getLengthSqr()<= sqrCutoff)
							neigh.add(new Tupel<T, Vec3>(s, t));
					}	
				}	
			}
		} else {
			//Access with handling boundary conditions
			for (int i=-1; i<=1; i++){
				Vec3 pbcCorrectionX = new Vec3();
				if (pbcX) {
					if (x + i < 0) pbcCorrectionX.sub(box.getBoxSize()[0]);
					else if (x + i >= this.dimX) pbcCorrectionX.add(box.getBoxSize()[0]);
				} 
				
				for (int j=-1; j<=1; j++){
					Vec3 pbcCorrectionY = pbcCorrectionX.clone();
					if (pbcY) {
						if (y + j < 0) pbcCorrectionY.sub(box.getBoxSize()[1]);
						else if (y + j >= this.dimY) pbcCorrectionY.add(box.getBoxSize()[1]);
					}
					
					for (int k=-1; k<=1; k++){
						Vec3 pbcCorrection = pbcCorrectionY.clone();
						if (pbcZ) {
							if (z + k < 0) pbcCorrection.sub(box.getBoxSize()[2]);
							else if (z + k >= this.dimZ) pbcCorrection.add(box.getBoxSize()[2]);
						}
						Vec3 cclone = c.clone().sub(pbcCorrection);
						
						List<T> possibleNeigh = accessCell(x + i, y + j, z + k);
						if (possibleNeigh!=null){
							for (int l=0; l<possibleNeigh.size(); l++){
								T s = possibleNeigh.get(l);
								Vec3 t = s.subClone(cclone);
								if (!s.equals(c) && t.getLengthSqr()<= sqrCutoff)
									neigh.add(new Tupel<T, Vec3>(s, t));
							}
						}
					}
				}	
			}
		}
		
		return neigh;
	}
	
	/**
	 * Creates a list of nearest neighbors within the cut-off radius and are
	 * not equal to the given argument c
	 * If more than maxNeigh neighbors are found, the farthest once are discarded.
	 * @param c an element defining the center of the sphere in which neighbors are found
	 * @param maxNeigh the maximum number of neighbors to be returned.
	 * @return a ArrayList containing all neighbors around the vicinity of c
	 */
	public ArrayList<T> getNeigh(Vec3 c, int maxNeigh){
		ArrayList<Tupel<T,Vec3>> n = getNeighAndNeighVec(c, maxNeigh);
		ArrayList<T> nb = new ArrayList<T>();
		for (Tupel<T,Vec3> t : n)
			nb.add(t.o1);
		
		return nb;
	}
	
	/**
	 * Creates a list with Tupels of values containing the nearest neighbors and the direction vectors
	 * from the given coordinate to them.
	 * It combines the data provided by {@linkplain #getNeigh(Vec3, int)} and {@linkplain #getNeighVec(Vec3, int)}
	 * If more than maxNeigh neighbors are found, the farthest once are discarded. 
	 * @param c an element defining the center of the sphere in which neighbors are found
	 * @param maxNeigh the maximum number of neighbors to be returned.
	 * @return a ArrayList containing the nearest neighbors and the vectors to neighbors as Tupel around the vicinity of c
	 */
	public ArrayList<Tupel<T, Vec3>> getNeighAndNeighVec(Vec3 c, int maxNeigh){
		ArrayList<Tupel<T,Vec3>> n = getNeighAndNeighVec(c);
		if (n.size()<=maxNeigh) return n;
		
		Collections.sort(n, new Comparator<Tupel<T,Vec3>>() {
			@Override
			public int compare(Tupel<T,Vec3> o1, Tupel<T,Vec3> o2) {
				return (int)Math.signum(o1.getO2().getLengthSqr()-o2.getO2().getLengthSqr());
			}
		});
		ArrayList<Tupel<T,Vec3>> n2 = new ArrayList<Tupel<T,Vec3>>();
		for (int i=0; i<maxNeigh; i++)
			n2.add(n.get(i));
		return n2;
	}
	
	/**
	 * Creates a list of vectors towards nearest neighbors within the cut-off radius and are
	 * not equal to the given argument c. The returned vectors provide the minimal direction
	 * to the nearest neighbors from the given coordinate considering periodicity. The length of each returned vector is 
	 * less or equal to the cut-off radius.
	 * If more than maxNeigh neighbors are found, the farthest once are discarded.
	 * @param c an element defining the center of the sphere in which neighbors are found 
	 * @param maxNeigh the maximum number of neighbors to be returned.
	 * @return a ArrayList containing the vectors to neighbors around the vicinity of c
	 */
	public ArrayList<Vec3> getNeighVec(Vec3 c, int maxNeigh){
		ArrayList<Vec3> n = getNeighVec(c);
		if (n.size()<=maxNeigh) return n;
		
		Collections.sort(n, new Comparator<Vec3>() {
			@Override
			public int compare(Vec3 o1, Vec3 o2) {
				return (int)Math.signum(o1.getLengthSqr()-o2.getLengthSqr());
			}
		});
		ArrayList<Vec3> n2 = new ArrayList<Vec3>();
		for (int i=0; i<maxNeigh; i++)
			n2.add(n.get(i));
		return n2;
	}
	
	/**
	 * Returns the distance to the closest object within the cutoff radius
	 * If no object is found in the cutoff radius, null is return
	 * @param c
	 * @return
	 */
	public Vec3 getVectorToNearest(Vec3 c){
		ArrayList<Vec3> n = getNeighVec(c);
		if (n.size() == 0) return null;
		int closest = 0;
		float dist = n.get(0).getLengthSqr();
		
		for (int i=1; i<n.size(); i++){
			float a = n.get(1).getLengthSqr();
			if (a<dist){
				dist = a;
				closest = i;
			}
		}
		
		return n.get(closest);
	}
	
	/**
	 * Returns the closest object within the cutoff radius
	 * If no object is found in the cutoff radius, null is return
	 * @param c
	 * @return
	 */
	public T getNearest(Vec3 c){
		ArrayList<Tupel<T,Vec3>> n = getNeighAndNeighVec(c);
		if (n.size() == 0) return null;
		int closest = 0;
		float dist = n.get(0).o2.getLengthSqr();
		
		for (int i=1; i<n.size(); i++){
			float a = n.get(1).o2.getLengthSqr();
			if (a<dist){
				dist = a;
				closest = i;
			}
		}
		
		return n.get(closest).o1;
	}
	
	private List<T> accessCell(int x, int y, int z){
		if (pbcX){
			if (x>=dimX) x -= dimX;
			else if (x<0) x += dimX;
		}
		if (pbcY){
			if (y>=dimY) y -= dimY;
			else if (y<0) y += dimY;
		}
		if (pbcZ){
			if (z>=dimZ) z -= dimZ;
			else if (z<0) z += dimZ;
		}
		
		int p = x*dimYZ+y*dimZ+z;
		
		if (p>=0 && p<cells.length)
			return cells[p];
		return null;
	}
	
	/**
	 * Extending ArrayList in the way that only the methods add(e) and remove(e)
	 * are threadsafe.  
	 * @param <E>
	 */
	private static class LimitedSynchronizedList<E> extends ArrayList<E> {
		private static final long serialVersionUID = 5901710139190998425L;

		LimitedSynchronizedList(int capacity) {
			super(capacity);
		}

		public boolean add(E e) {
			synchronized (this) {return super.add(e);}
		}

		public boolean remove(Object o) {
			synchronized (this) {return super.remove(o);}
		}
	}
	 
}
