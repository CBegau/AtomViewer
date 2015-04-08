package model;

import common.Vec3;

public class BoxParameter {

	final private Vec3 boxSize[] = new Vec3[3];
	final private Vec3 tBoxSize[] = new Vec3[3];
	final private Vec3 height = new Vec3();
	final private Vec3 offset = new Vec3();
	final private boolean pbc[] = new boolean[3];
	final private float volume;
	
	public BoxParameter(Vec3 x, Vec3 y, Vec3 z, boolean pbc_x, boolean pbc_y, boolean pbc_z){
		boxSize[0] = x.clone();
		boxSize[1] = y.clone();
		boxSize[2] = z.clone();
		
		pbc[0] = pbc_x;
		pbc[1] = pbc_y;
		pbc[2] = pbc_z;
		
		tBoxSize[0] = boxSize[1].cross(boxSize[2]);
		tBoxSize[1] = boxSize[2].cross(boxSize[0]);
		tBoxSize[2] = boxSize[0].cross(boxSize[1]);
		volume = boxSize[0].dot(tBoxSize[0]);
		tBoxSize[0].divide(volume);
		tBoxSize[1].divide(volume);
		tBoxSize[2].divide(volume);
		
		if (isOrtho()){ 
			height.x = boxSize[0].x;
			height.y = boxSize[1].y;
			height.z = boxSize[2].z;
		} else {
			height.x = (float)Math.sqrt(1.0f / tBoxSize[0].dot(tBoxSize[0]));
			height.y = (float)Math.sqrt(1.0f / tBoxSize[1].dot(tBoxSize[1]));
			height.z = (float)Math.sqrt(1.0f / tBoxSize[2].dot(tBoxSize[2]));
		}
		
		assert (volume != 0) : "AtomData bounding box has size 0";
	}
	
	public boolean[] getPbc() {
		return pbc;
	}
	
	public boolean isOrtho(){
		return (boxSize[0].y == 0f && boxSize[0].z == 0f &&
				boxSize[1].x == 0f && boxSize[1].z == 0f &&
				boxSize[2].x == 0f && boxSize[2].y == 0f);
	}
	
	public float getVolume() {
		return volume;
	}
	
	public void setOffset(Vec3 offset) {
		this.offset.setTo(offset);
	}
	
	public Vec3 getHeight() {
		return height;
	}
	
	/**
	 * The coordinate system of AtomViewer does not support
	 * negative coordinates with periodic boundary conditions.
	 * If such an file is to be imported, all atoms must be shifted.
	 * The offset must be stored in this array. Printing coordinates of
	 * atoms will be corrected to match the input data.
	 * This is primarily used to import the LAMMPS format
	 */
	public Vec3 getOffset() {
		return offset;
	}
	
	public Vec3[] getBoxSize() {
		return boxSize;
	}
	
	public Vec3[] getTBoxSize() {
		return tBoxSize;
	}
	
	public Vec3 getCellDim(float minSize) {
		Vec3 cellSize = new Vec3();
		Vec3 cellDim = new Vec3();
		cellSize.x = (float) Math.sqrt((minSize*minSize) / (height.x*height.x));
		cellSize.y = (float) Math.sqrt((minSize*minSize) / (height.y*height.y));
		cellSize.z = (float) Math.sqrt((minSize*minSize) / (height.z*height.z));

		cellDim.x = (int) (1.0f / cellSize.x);
		cellDim.y = (int) (1.0f / cellSize.y);
		cellDim.z = (int) (1.0f / cellSize.z);

		return cellDim;
	}
	
	public Vec3 getPbcCorrectedDirection(Vec3 c1, Vec3 c2){
		Vec3 dir = c1.subClone(c2);
		if (pbc[0]){
			if (dir.x > height.x * 0.5f) dir.sub(boxSize[0]);
			else if (dir.x < -height.x * 0.5f) dir.add(boxSize[0]);
		}
		if (pbc[1]){
			if (dir.y > height.y * 0.5f) dir.sub(boxSize[1]);
			else if (dir.y < -height.y * 0.5f) dir.add(boxSize[1]);
		}
		if (pbc[2]){
			if (dir.z > height.z * 0.5f) dir.sub(boxSize[2]);
			else if (dir.z < -height.z * 0.5f) dir.add(boxSize[2]);
		}
		return dir;
	}
	
	public boolean isVectorInPBC(Vec3 v){
		return (!(pbc[0] && Math.abs(v.x) > height.x * 0.5f)
				&& !(pbc[1] && Math.abs(v.y) > height.y * 0.5f)
				&& !(pbc[2] && Math.abs(v.z) > height.z * 0.5f));
	}
	
	/**
	 * Moves a vector back into the simulation box in case of PBC 
	 * @param pos
	 */
	public final void backInBox(Vec3 pos){
		if (pbc[0]) {
			float a = pos.dot(tBoxSize[0]);
			int i = (int)a;
			if (a < 0f) i--;
			pos.x -= i * boxSize[0].x;
			pos.y -= i * boxSize[0].y;
			pos.z -= i * boxSize[0].z;
		}

		if (pbc[1]) {
			float a = pos.dot(tBoxSize[1]);
			int i = (int)a;
			if (a < 0f) i--;
			pos.x -= i * boxSize[1].x;
			pos.y -= i * boxSize[1].y;
			pos.z -= i * boxSize[1].z;
		}

		if (pbc[2]) {
			float a = pos.dot(tBoxSize[2]);
			int i = (int)a;
			if (a < 0f) i--;
			pos.x -= i * boxSize[2].x;
			pos.y -= i * boxSize[2].y;
			pos.z -= i * boxSize[2].z;
		}
	}
}
