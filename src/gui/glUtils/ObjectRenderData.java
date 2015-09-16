// Part of AtomViewer: AtomViewer is a tool to display and analyse
// atomistic simulations
//
// Copyright (C) 2013  ICAMS, Ruhr-Universität Bochum
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

package gui.glUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.Comparator;
import java.util.Vector;
import java.util.concurrent.Callable;

import common.ThreadPool;
import common.Vec3;
import model.BoxParameter;
import model.Pickable;

/**
 * Creates a domain decomposition of a list of objects in three dimensional space
 * The objects are decomposed into cuboidal cells that can be sorted front to back
 * @param <T>
 */
public class ObjectRenderData<T extends Vec3 & Pickable> {
	public final static int MAX_ELEMENTS_PER_CELL = 6000;
	public final static int APPROXIMATE_ELEMENTS_PER_INITIAL_CELL = 8*MAX_ELEMENTS_PER_CELL;
	private int runningTasks = 0;	//Counting the number of cells waiting or running in the ThreadPool
	
	private final CellComparator cellComparator = new CellComparator();
	Vector<Cell> allCells = new Vector<ObjectRenderData<T>.Cell>();
	private boolean subdivided = false;
	
	
	public ObjectRenderData(List<T> objects, boolean subdivide, BoxParameter box) {
//		long time = System.nanoTime();
		
		final Cell rootCell;
		
		if (objects.size() == 0){
			Vec3 size = box.getHeight();
			rootCell = new Cell(size.multiplyClone(0.5f), size);
		} else {
			Vec3 min = new Vec3(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY);
			Vec3 max = new Vec3(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY);
			
			for (T o : objects){
				if (o.x < min.x) min.x = o.x;
				if (o.y < min.y) min.y = o.y;
				if (o.z < min.z) min.z = o.z;
				
				if (o.x > max.x) max.x = o.x;
				if (o.y > max.y) max.y = o.y;
				if (o.z > max.z) max.z = o.z;
			}
			Vec3 size = max.subClone(min);
			rootCell = new Cell(min.add(size.multiplyClone(0.5f)), size);
		}
		
		this.subdivided = subdivide;
	
		if (objects instanceof ArrayList<?>)
			rootCell.objects = (ArrayList<T>)objects;
		else rootCell.objects = new ArrayList<T>(objects);
		
		if (objects.size()<MAX_ELEMENTS_PER_CELL)
			subdivide = false;
		
		if(subdivide)
			submitTask(rootCell);
		else {
			allCells.add(rootCell);
			rootCell.finalizeCellForRendering();
		}
		
		getRenderableCells();
//		long time2 = System.nanoTime();
//		System.out.println("Create "+(time2-time)/1000000);
	}
	
	public void reinitUpdatedCells(){
		Vector<Callable<Void>> tasks = new Vector<Callable<Void>>();
		for (int i=0; i<ThreadPool.availProcessors(); i++){
			final int start = (int)(((long)allCells.size() * i)/ThreadPool.availProcessors());
			final int end = (int)(((long)allCells.size() * (i+1))/ThreadPool.availProcessors());
			tasks.add(new Callable<Void>() {
				@Override
				public Void call() throws Exception {
					for (int i=start; i<end; i++){
						allCells.get(i).prepareRendering();
					}
					return null;
				}
			});
		}
		ThreadPool.executeParallelSecondLevel(tasks);
	}
	
	public boolean isSubdivided() {
		return subdivided;
	}
	
	private synchronized void submitTask(Cell cell){
		runningTasks++;
		ThreadPool.submit(cell);
	}
	
	private synchronized void finishRunningTasks(){
		runningTasks--;
	}
	
	/**
	 * Sorts cells from front to back based on the coordinate 
	 * the cells' corner in front depending on the current
	 * modelview matrix.
	 * Sorted cells can be rendered from front to back
	 * and rendering performance is improve using occlusion culling 
	 * @param modelViewMatrix the current modelview matrix
	 */
	public void sortCells(final GLMatrix modelViewMatrix){
		//Extract the column to compute z-coordinate 
		float mvmMatrix1 = modelViewMatrix.getMatrix().get(2);
		float mvmMatrix2 = modelViewMatrix.getMatrix().get(6);
		float mvmMatrix3 = modelViewMatrix.getMatrix().get(10);
		
		if (allCells.size() == 0) return;
		
		//Test which of the eight corners is in front in an arbitrary cell
		//This corner will be in front in any other cell as well
		Cell c = allCells.get(0);
		c.visibleZ = Float.POSITIVE_INFINITY;
		
		int cornerInFront = 0;
		Vec3 v = new Vec3();
		for (int i=0; i<8; i++){
			v.x = i%2 == 1 ? -0.5f : 0.5f;
			v.y = (i>>1)%2 == 1 ? -0.5f : 0.5f;
			v.z = (i>>2)%2 == 1 ? -0.5f : 0.5f;
			
			float z1 = mvmMatrix1 * (c.x + v.x*c.size.x) 
					 + mvmMatrix2 * (c.y + v.y*c.size.y) 
					 + mvmMatrix3 * (c.z + v.z*c.size.z);
			if (z1 < c.visibleZ) cornerInFront = i;
		}
		
		//The corner in the front is known
		//Compute the z-coordinate of this corner in
		//all cells to sort them
		v.x = cornerInFront%2 == 1 ? -0.5f : 0.5f;
		v.y = (cornerInFront>>1)%2 == 1 ? -0.5f : 0.5f;
		v.z = (cornerInFront>>2)%2 == 1 ? -0.5f : 0.5f;
		
		for (int j=0; j<allCells.size(); j++){
			c = allCells.get(j);
			c.visibleZ = mvmMatrix1 * (c.x + v.x*c.size.x) 
					   + mvmMatrix2 * (c.y + v.y*c.size.y) 
					   + mvmMatrix3 * (c.z + v.z*c.size.z);
		}
		
		Collections.sort(allCells, cellComparator);
	}
	
	public List<Cell> getRenderableCells() {
		while (runningTasks != 0){	//make sure that no tasks are currently running
			try {
				Thread.sleep(20l);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		
		return allCells;
	}
		
	public class Cell extends Vec3 implements Callable<Void>{
		//Cell parameters
		private Vec3 size = new Vec3();
		private float visibleZ;
		private float overlap = 0f;
		
		private int visibleObjects = 0;
		private ArrayList<T> objects;
		private float[] color;
		private float[] sizes;
		private boolean[] isObjectVisible;
		
		
		public Cell(Vec3 center, Vec3 size) {
			super();
			this.setTo(center);
			this.size.setTo(size);
		}
		
		private void finalizeCellForRendering(){
			this.objects.trimToSize();
			this.color = new float[this.objects.size()*3];
			this.sizes = new float[this.objects.size()];
			this.isObjectVisible = new boolean[this.objects.size()];
		}
		
		public int getNumObjects() {
			return objects.size();
		}
		
		public int getNumVisibleObjects() {
			return visibleObjects;
		}
		
		public float[] getColorArray() {
			return color;
		}
		
		public float[] getSizeArray() {
			return sizes;
		}
		
		public boolean[] getVisibiltyArray() {
			return isObjectVisible;
		}
		
		public Vec3 getSize(){
			return size.addClone(2f*overlap);
		}
		
		public Vec3 getOffset(){
			return size.multiplyClone(-0.5f).add(this).sub(overlap);
		}
		
		public List<T> getObjects() {
			return objects;
		}
		
		void prepareRendering(){
			float minX = Float.POSITIVE_INFINITY;
			float maxX = Float.NEGATIVE_INFINITY;
			float minY = Float.POSITIVE_INFINITY;
			float maxY = Float.NEGATIVE_INFINITY;
			float minZ = Float.POSITIVE_INFINITY;
			float maxZ = Float.NEGATIVE_INFINITY;
			
			this.visibleObjects = 0;
			this.overlap = 0f;
			
			for (int i = 0; i < objects.size(); i++){
				if (isObjectVisible[i]){
					visibleObjects++;
					if (sizes[i] > this.overlap)
						this.overlap = sizes[i];
					
					if (objects.get(i).x > maxX) maxX = objects.get(i).x;
					if (objects.get(i).x < minX) minX = objects.get(i).x;
					
					if (objects.get(i).y > maxY) maxY = objects.get(i).y;
					if (objects.get(i).y < minY) minY = objects.get(i).y;
					
					if (objects.get(i).z > maxZ) maxZ = objects.get(i).z;
					if (objects.get(i).z < minZ) minZ = objects.get(i).z;
				}
			}
			
			if (visibleObjects > 0){
				this.x = minX + (maxX - minX) * 0.5f;
				this.size.x = maxX - minX;
				this.y = minY + (maxY - minY) * 0.5f;
				this.size.y = maxY - minY;
				this.z = minZ + (maxZ - minZ) * 0.5f;
				this.size.z = maxZ - minZ;
			} else {
				this.size.x = 0f;
				this.size.y = 0f;
				this.size.z = 0f;
			}
		}
		
		private void subdivide(){
			if (this.getNumObjects() <= MAX_ELEMENTS_PER_CELL){
				if (this.getNumObjects()>0){
					allCells.add(this);
					this.finalizeCellForRendering();
				}
				return;
			}
			
			if (this.getNumObjects() > 8*APPROXIMATE_ELEMENTS_PER_INITIAL_CELL)
				subdivideBlocks();
			else 
				subdivideBinary();
		}

		private void subdivideOctree() {
			final ArrayList<Cell> cells = new ArrayList<ObjectRenderData<T>.Cell>(); 
	
			for (int i=0; i<8; i++){
				Vec3 s = this.size.multiplyClone(0.5f);
				
				Vec3 center = new Vec3();
				center.x = this.x + this.size.x * ( (i&1) == 0 ? -0.25f : 0.25f);
				center.y = this.y + this.size.y * ( (i&2) == 0 ? -0.25f : 0.25f);
				center.z = this.z + this.size.z * ( (i&4) == 0 ? -0.25f : 0.25f);
				Cell c = new Cell(center, s);
				c.objects = new ArrayList<T>(objects.size()>>3);
				cells.add(c);
			}
		
			for (int i=0; i<objects.size(); i++){
				int j=0;
				T ra = objects.get(i);
				if (ra.x > this.x) j+=1;
				if (ra.y > this.y) j+=2;
				if (ra.z > this.z) j+=4;
				cells.get(j).objects.add(ra);
			}
			
			this.objects = null;
			
			for (Cell c : cells)
				if (c.objects.size()>0)
					submitTask(c);
		}
		
		private void subdivideBlocks() {
			float targetBlocksPerDir = (float)Math.cbrt(this.objects.size()/APPROXIMATE_ELEMENTS_PER_INITIAL_CELL);
			
			//Currently all the number of blocks is the same in all directions,
			//this could change in future releases
			final int blocks[] = new int[3];
			blocks[0] = (int)targetBlocksPerDir;
			blocks[1] = (int)targetBlocksPerDir;
			blocks[2] = (int)targetBlocksPerDir;
			
			if (blocks[0] == 2){ //Same result, but faster codes
				subdivideOctree();
				return;
			}
			
			if (blocks[0] == 1){
				subdivideBinary();
				return;
			}
			
			final int yz = blocks[2]*blocks[1];
			Vec3 subBlockSize = new Vec3(size.x/blocks[0], size.y/blocks[1], size.z/blocks[2]);
			
			
			final ArrayList<Cell> cells = new ArrayList<ObjectRenderData<T>.Cell>();			
			
			for (int i=0; i<blocks[0]; i++){
				for (int j=0; j<blocks[1]; j++){
					for (int k=0; k<blocks[2]; k++){
						Vec3 center = new Vec3();
						center.x = (this.x - 0.5f*this.size.x) + ((0.5f+i) * subBlockSize.x);
						center.y = (this.y - 0.5f*this.size.y) + ((0.5f+j) * subBlockSize.y);
						center.z = (this.z - 0.5f*this.size.z) + ((0.5f+k) * subBlockSize.z);
						Cell c = new Cell(center, subBlockSize.clone());
						c.objects = new ArrayList<T>(APPROXIMATE_ELEMENTS_PER_INITIAL_CELL>>2);
						cells.add(c);
					}	
				}	
			}
			
			final float invSizeX = 1f/subBlockSize.x;
			final float invSizeY = 1f/subBlockSize.y;
			final float invSizeZ = 1f/subBlockSize.z;
			final Vec3 corner = this.subClone(this.size.multiplyClone(0.5f));
			Vector<Callable<Void>> tasks = new Vector<Callable<Void>>();
			
			for (int i=0; i<ThreadPool.availProcessors(); i++){
				final int start = (int)(((long)objects.size() * i)/ThreadPool.availProcessors());
				final int end = (int)(((long)objects.size() * (i+1))/ThreadPool.availProcessors());

				tasks.add(new Callable<Void>() {
					
					@Override
					public Void call() throws Exception {
						for (int i=start; i<end; i++){
							T ra = objects.get(i);
							int x = (int)((ra.x-corner.x)*invSizeX);
							int y = (int)((ra.y-corner.y)*invSizeY);
							int z = (int)((ra.z-corner.z)*invSizeZ);
							
							if (x < 0) x = 0;
							else if (x >= blocks[0]) x = blocks[0]-1;
							if (y < 0) y = 0;
							else if (y >= blocks[1]) y = blocks[1]-1;
							if (z < 0) z = 0;
							else if (z >= blocks[2]) z = blocks[2]-1;
							
							cells.get(x*yz + y*blocks[2] + z).addElementSynchronized(ra);
						}
						
						return null;
					}
				});
			}
			
			ThreadPool.executeParallelSecondLevel(tasks);
			
			this.objects = null;
			
			for (Cell c : cells)
				if (c.objects.size()>0)
					submitTask(c);
		}
		
		private void subdivideBinary() {
			int longestAxis = 0;
			if (size.x>size.y && size.x>size.z){
				longestAxis = 0;
			} else if(size.y>size.z)
				longestAxis = 1;
			else longestAxis = 2;
			
			float cutPoint=0f;
			if (longestAxis == 0) cutPoint = this.x;
			else if (longestAxis == 1) cutPoint = this.y;
			else if (longestAxis == 2) cutPoint = this.z;
			
			Vec3 s = new Vec3();
			s.x = this.size.x * (longestAxis == 0 ? 0.5f : 1f);
			s.y = this.size.y * (longestAxis == 1 ? 0.5f : 1f);
			s.z = this.size.z * (longestAxis == 2 ? 0.5f : 1f);
			
			Vec3 centerShift = new Vec3();
			centerShift.x = this.size.x * (longestAxis == 0 ? 0.25f : 0f);
			centerShift.y = this.size.y * (longestAxis == 1 ? 0.25f : 0f);
			centerShift.z = this.size.z * (longestAxis == 2 ? 0.25f : 0f);
			
			Cell subCell1 = new Cell(this.subClone(centerShift), s); 
			Cell subCell2 = new Cell(this.addClone(centerShift), s);
			
			subCell1.objects = new ArrayList<T>(objects.size()>>1 + 5);
			subCell2.objects = new ArrayList<T>(objects.size()>>1 + 5);
			
			if (longestAxis == 0){
				for (int i=0; i<objects.size(); i++){
					T ra = objects.get(i);
					if (ra.x < cutPoint) subCell1.objects.add(ra);
					else subCell2.objects.add(ra);
				}
			} else if (longestAxis == 1){
				for (int i=0; i<objects.size(); i++){
					T ra = objects.get(i);
					if (ra.y < cutPoint) subCell1.objects.add(ra);
					else subCell2.objects.add(ra);
				}
			}else if (longestAxis == 2){
				for (int i=0; i<objects.size(); i++){
					T ra = objects.get(i);
					if (ra.z < cutPoint) subCell1.objects.add(ra);
					else subCell2.objects.add(ra);
				}
			}
			
			this.objects = null;
			
			submitTask(subCell1);
			submitTask(subCell2);
		}
		
		private synchronized void addElementSynchronized(T t){
			this.objects.add(t);	
		}

		@Override
		public Void call() throws Exception {
			this.subdivide();
			finishRunningTasks();
			return null;
		}
	}
	
	/**
	 * Comparator to sort the cells with decreasing z-coordinates 
	 * with respect to the current modelViewMatrix
	 * Cells that do not hold visible objects are sorted to the end
	 */
	private class CellComparator implements Comparator<Cell>{
		@Override
		public int compare(Cell c1, Cell c2) {
			if (c1.visibleObjects == 0 && c2.visibleObjects == 0) return 0;
			if (c1.visibleObjects == 0 && c2.visibleObjects > 0) return 1;
			if (c1.visibleObjects > 0 && c2.visibleObjects == 0) return -1;
			
			if (c1.visibleZ<c2.visibleZ) return 1;
			if (c1.visibleZ>c2.visibleZ) return -1;
			return 0;
		}
	}
}