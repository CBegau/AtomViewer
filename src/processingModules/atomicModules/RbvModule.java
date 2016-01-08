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

package processingModules.atomicModules;

import gui.JPrimitiveVariablesPropertiesDialog;
import gui.ProgressMonitor;
import gui.PrimitiveProperty.*;

import java.util.*;
import java.util.concurrent.*;

import javax.swing.JFrame;
import javax.swing.JSeparator;

import processingModules.DataContainer;
import processingModules.ClonableProcessingModule;
import processingModules.ProcessingResult;
import processingModules.otherModules.VacancyDetectionModule.VacancyDataContainer;
import processingModules.toolchain.Toolchainable.ExportableValue;
import processingModules.toolchain.Toolchainable.ToolchainSupport;
import model.Atom;
import model.AtomData;
import model.DataColumnInfo;
import model.Filter;
import model.NearestNeighborBuilder;
import model.RBVStorage;
import model.polygrain.Grain;
import common.*;
import crystalStructures.CrystalStructure;

/**
 * Computing numerically resultant Burgers vectors
 * Reference implementation for the algorithm published in 
 * 
 * C. Begau, J. Hua, A. Hartmaier 
 * A novel approach to study dislocation density tensors and lattice rotation patterns in atomistic simulations
 * Journal of the Mechanics and Physics of Solids, vol. 60, pp. 711-722, (2012)
 * 
 * Computing the lattice correspondence and the Nye tensor is implemented as described in
 * 
 * C. Hartley and Y. Mishin
 * Characterization and visualization of the lattice misfit associated with dislocation cores
 * Acta Mater., vol. 53, no. 5, pp. 1313–1321, (2005)
 *  
 * @author Christoph Begau
 *
 */

@ToolchainSupport
public class RbvModule extends ClonableProcessingModule{
	
	/**
	 * Maximum deviation allowed to detect matching bonds (=20°)
	 */
	private static final double PHI_MAX = Math.cos(20*Math.PI/180.);
	
	private final Vec3[] neighPerf;
	
	//Icoseader data
	private static final float icoConstX  = 1f;
	private static final float icoConstZ  = 1.6180339887f;
	private final Vec3[] icoVertices;
	
	private final Vec3[] icoNormals;
	
	private static final int[] icoFaces = {
		0,4,1,  0,9,4,  9,5,4,  4,5,8,  4,8,1,
		8,10,1, 8,3,10, 5,3,8,  5,2,3,  2,7,3,
		7,10,3, 7,6,10, 7,11,6, 11,0,6, 0,1,6,
		6,1,10, 9,0,11, 9,11,2, 9,2,5,  7,2,11
	};
	
	@ExportableValue
	private float acceptanceThreshold = 0.14f;
	
	@ExportableValue
	private boolean defectsOnly = true;
	
	private float[] pnl;
	private float perfectBurgersVectorLength, rbvCorrectionFactor;
	private float nnbDist;
	
	private ConcurrentHashMap<Vec3,RbvInfo<?>> atomToRbvInfoMap = new ConcurrentHashMap<Vec3, RbvInfo<?>>(16, 0.75f, ThreadPool.availProcessors());
	
	private NearestNeighborBuilder<Vec3> nnb;
	
	public RbvModule(){
		icoNormals = null;
		neighPerf = null;
		icoVertices = null;
	}
	
	private RbvModule(AtomData data, List<Atom> atoms, final CrystalStructure s, Grain g, RbvModule parent) {
		this.nnb = new NearestNeighborBuilder<Vec3>(data.getBox(), s.getNearestNeighborSearchRadius(), true);
		this.defectsOnly = parent.defectsOnly;
		this.acceptanceThreshold = parent.acceptanceThreshold;
		
		Vec3[] perfNeighbors;
		if (g == null)
			perfNeighbors = s.getPerfectNearestNeighbors(data.getCrystalRotation());
		else 
			perfNeighbors = s.getPerfectNearestNeighbors(g);
		
		this.perfectBurgersVectorLength = s.getPerfectBurgersVectorLength();
		this.rbvCorrectionFactor = 1f/(s.getRBVIntegrationRadius()/this.perfectBurgersVectorLength);
		this.nnbDist = s.getNearestNeighborSearchRadius();
		
		//Add vacancy markers as pseudo-particles if existing
		DataContainer dc = data.getDataContainer(VacancyDataContainer.class);
		if (dc != null){
			VacancyDataContainer vcd = (VacancyDataContainer)dc;
			nnb.addAll(vcd.getParticles());
		}
		
		nnb.addAll(atoms, new Filter<Vec3>() {
			@Override
			public boolean accept(Vec3 v) {
				Atom a = (Atom)v;
				return a.getGrain() != Atom.IGNORED_GRAIN && 
						s.considerAtomAsNeighborDuringRBVCalculation(a);
			}
		});
		
		
		this.pnl = new float[perfNeighbors.length];
		for (int i=0; i<this.pnl.length; i++){
			this.pnl[i] = perfNeighbors[i].getLength();
		}
		this.neighPerf = perfNeighbors;

		//Create icoseader vertices to approximate a sphere with a radius of the integration radius
		//Icosaeder edge length
		float r = (float)((2*s.getRBVIntegrationRadius())/Math.sqrt(10+2*Math.sqrt(5)));
		
		icoVertices = new Vec3[] {
			new Vec3(-icoConstX*r, 0f, icoConstZ*r), new Vec3(icoConstX*r, 0f, icoConstZ*r),
			new Vec3(-icoConstX*r, 0f, -icoConstZ*r),new Vec3(icoConstX*r, 0f, -icoConstZ*r),
			new Vec3(0f, icoConstZ*r, icoConstX*r),  new Vec3(0f, icoConstZ*r, -icoConstX*r),
			new Vec3(0f, -icoConstZ*r, icoConstX*r), new Vec3(0f, -icoConstZ*r, -icoConstX*r),
			new Vec3(icoConstZ*r, icoConstX*r, 0f),  new Vec3(-icoConstZ*r, icoConstX*r, 0f),
			new Vec3(icoConstZ*r, -icoConstX*r, 0f), new Vec3(-icoConstZ*r, -icoConstX*r, 0f)
		};
		
		icoNormals = new Vec3[icoVertices.length];
		for (int i=0; i<icoVertices.length; i++){
			icoNormals[i] = icoVertices[i].normalizeClone();
		}
		
				
		List<RbvInfo<Atom>> infosList = new ArrayList<RbvInfo<Atom>>();
		
		for (int i=0; i<atoms.size(); i++){
			Atom a = atoms.get(i);
			
			boolean include = false;
			//Identify for which atoms RBVs are to be computed
			if (defectsOnly && s.isRBVToBeCalculated(a)) include = true; 
			else if(!defectsOnly && s.considerAtomAsNeighborDuringRBVCalculation(a)) include = true;
			
			if (include){
				RbvInfo<Atom> info = new RbvInfo<Atom>();
				info.atom = atoms.get(i);
				atomToRbvInfoMap.put(atoms.get(i), info);
				infosList.add(info);
			}
		}

		ProgressMonitor.getProgressMonitor().start(infosList.size());
		
		Vector<AnalyseCallable> tasks = new Vector<AnalyseCallable>();
		for (int i = 0; i < ThreadPool.availProcessors(); i++) {
			int start = (int)(((long)infosList.size() * i)/ThreadPool.availProcessors());
			int end = (int)(((long)infosList.size() * (i+1))/ThreadPool.availProcessors());
			tasks.add(new AnalyseCallable(start, end, infosList, data.getRbvStorage()));
		}
		
		ThreadPool.executeParallel(tasks);
		
		ProgressMonitor.getProgressMonitor().stop();
	}

	/**
	 * Calculate the volume of an hexaedron
	 * Integrate the nye tensor over a triangle by giving the triangle vertices as p0-p2
	 * for p3-p4 provide p0+normal*nyeTensor
	 * the returned volume is the integrated nye tensor for the triangle
	 * @param p0
	 * @param p1
	 * @param p2
	 * @param p3
	 * @param p4
	 * @param p5
	 * @return
	 */
	private static double integral(Vec3 p0, Vec3 p1, Vec3 p2,
			Vec3 p3,Vec3 p4, Vec3 p5){
		//The integral is the volume of an irregular hexahedron
		//Decompose this hexahedron into three tetrahedrons and sum their volumes
		double volume = tetraederVolume(p3, p5, p1, p4);
		volume += tetraederVolume(p3, p5, p2, p1);
		volume += tetraederVolume(p3, p2, p0, p1);
		
		return volume;
	}

	private static double tetraederVolume(Vec3 p1, Vec3 p2, Vec3 p3, Vec3 p4){
//		double[] dir1 = VectorOps.sub(p2, p1);
//		double[] dir2 = VectorOps.sub(p3, p1);
//		double[] dir3 = VectorOps.sub(p4, p1);
//		double[] dir2CrossDir3 = VectorOps.crossProduct(dir2, dir3);
//
//		return VectorOps.product(dir1, dir2CrossDir3)/6.;
		
		return ((p2.x-p1.x)*((p3.y-p1.y)*(p4.z-p1.z)-(p3.z-p1.z)*(p4.y-p1.y))
		          +(p2.y-p1.y)*((p3.z-p1.z)*(p4.x-p1.x)-(p3.x-p1.x)*(p4.z-p1.z))
		          +(p2.z-p1.z)*((p3.x-p1.x)*(p4.y-p1.y)-(p3.y-p1.y)*(p4.x-p1.x)))*0.16666667;
	}
	
	private class RbvInfo<T extends Vec3>{
		T atom;
		//Packed arrays
		// [0 1 2]
		// [3 4 5]
		// [6 7 8]
		float[] nyeTensor;
		float[] lcm;
		ArrayList<Tupel<Vec3,Vec3>> neighAndVec = null;
		
		private synchronized void makeNyeTensor() {
			if (lcm==null) calculateLcm();
			if (nyeTensor==null) calculateNye();
		}
		
		private double[] interpolateNye(Vec3 p, Vec3 norm){
			ArrayList<Tupel<Vec3,Vec3>> possibleNeighbors = nnb.getNeighAndNeighVec(p);
			
			Vec3[] a = new Vec3[possibleNeighbors.size()];
			double[] weighting = new double[possibleNeighbors.size()];
			
			int numNeight = 0;
			double sum = 0.;
			//Determine weighing factors for interpolation using inverse squared distance weighting
			for (int i=0; i<possibleNeighbors.size();i++){
				float dis = possibleNeighbors.get(i).o2.getLengthSqr();
				if (dis<nnbDist*nnbDist){
					a[numNeight] = possibleNeighbors.get(i).o1;
					weighting[numNeight] = 1./dis;
					sum += weighting[numNeight];
					numNeight++;
				}
			}
			
			double[] nyeInter = new double[3];
			if (numNeight>0) {
				for (int i=0; i<numNeight; i++){
					float[] nyeTensor = atomToRbvInfoMap.get(a[i]).nyeTensor;
					if (nyeTensor == null){
						atomToRbvInfoMap.get(a[i]).makeNyeTensor();
						nyeTensor = atomToRbvInfoMap.get(a[i]).nyeTensor;
					}
					
					nyeInter[0] += (norm.x*nyeTensor[0] + norm.y*nyeTensor[3] + norm.z*nyeTensor[6]) * weighting[i]; 
					nyeInter[1] += (norm.x*nyeTensor[1] + norm.y*nyeTensor[4] + norm.z*nyeTensor[7]) * weighting[i];
					nyeInter[2] += (norm.x*nyeTensor[2] + norm.y*nyeTensor[5] + norm.z*nyeTensor[8]) * weighting[i];
				}
				double d = 1./sum;
				nyeInter[0] *= d;
				nyeInter[1] *= d;
				nyeInter[2] *= d;
			} else return null;
			return nyeInter;
		}
		
		private double[] interpolateLineDirection(Vec3 p){
			ArrayList<Tupel<Vec3,Vec3>> possibleNeighbors = nnb.getNeighAndNeighVec(p);
			
			Vec3[] a = new Vec3[possibleNeighbors.size()];
			double[] weightings = new double[possibleNeighbors.size()];
			
			int numNeight = 0;
			double sum = 0.;
			//Determine weighing factors for interpolation using inverse squared distance weighting
			for (int i=0; i<possibleNeighbors.size();i++){
				float dis = possibleNeighbors.get(i).o2.getLengthSqr();
				if (dis<nnbDist*nnbDist){
					a[numNeight] = possibleNeighbors.get(i).o1;
					weightings[numNeight] = 1./dis;
					sum += weightings[numNeight];
					numNeight++;
				}
			}
			
			double[] ls = new double[9];
			
			if (numNeight>0){
				for (int i=0; i<numNeight; i++){
					RbvInfo<?> info = atomToRbvInfoMap.get(a[i]);
					
					if (info == null){
						RbvInfo<Vec3> infov = new RbvInfo<Vec3>();
						infov.atom = a[i];
						atomToRbvInfoMap.put(a[i], infov);
						info = infov;
					}
					
					float[] nyeTensor = info.nyeTensor;
					if (nyeTensor == null){
						info.makeNyeTensor();
						nyeTensor = info.nyeTensor;
					}
					ls[0] += (nyeTensor[0]) * weightings[i]; 
					ls[1] += (nyeTensor[3]) * weightings[i];
					ls[2] += (nyeTensor[6]) * weightings[i];
					
					ls[3] += (nyeTensor[1]) * weightings[i]; 
					ls[4] += (nyeTensor[4]) * weightings[i];
					ls[5] += (nyeTensor[7]) * weightings[i];
					
					ls[6] += (nyeTensor[2]) * weightings[i]; 
					ls[7] += (nyeTensor[5]) * weightings[i];
					ls[8] += (nyeTensor[8]) * weightings[i];
				}
				double d = 1./sum;
				ls[0] *= d; ls[3] *= d; ls[6] *= d;
				ls[1] *= d; ls[4] *= d; ls[7] *= d;
				ls[2] *= d; ls[5] *= d; ls[8] *= d;
			} else return null;
			return ls;
		}
		
		public Tupel<Vec3, Vec3> calculateBurgersVector() {
			Vec3 bv;
			Vec3 lineDirection;
			
			//Test assumed Burgers vector (1,0,0) (in cartesian space!)
			lineDirection = calculateLineDirection(new int[]{1,0,0});
			
			if (lineDirection == null) return null;
			
			//Test alternating assumed Burgers vector if vector is too small 
			if (lineDirection.getLengthSqr()<0.1f)
				lineDirection = calculateLineDirection(new int[]{0,1,0});
			
			//Test alternating assumed Burgers vector if vector is too small 
			if (lineDirection.getLengthSqr()<0.1f)
				lineDirection = calculateLineDirection(new int[]{0,0,1});
			
			lineDirection.normalize();
			
			bv = calculateBurgersVector(lineDirection);
			if (bv == null) return null;
			
			if (bv.getLength() > perfectBurgersVectorLength*acceptanceThreshold)
				return new Tupel<Vec3,Vec3>(bv, lineDirection);
				//atom.setRBV(bv, lineDirection);
			else return null;
			
			//local correspondence matrix is needed to calc the nye tensor.
			//because the nye tensors of all neighbors has been calculated
			//the lcm is not needed anymore
//			lcm = null;
		}
		
		private Vec3 calculateBurgersVector(Vec3 lineSense) {
						
			double[][] nyeInter = new double[icoVertices.length][3];
			for (int i = 0; i<icoVertices.length; i++){
				nyeInter[i] = interpolateNye(atom.addClone(icoVertices[i]), lineSense);
				if (nyeInter[i] == null) return null;
			}
			Vec3 bv = new Vec3();
			
			for (int i = 0; i<icoFaces.length/3; i++){
				int v1 = icoFaces[i*3+0];
				int v2 = icoFaces[i*3+1];
				int v3 = icoFaces[i*3+2];
				bv.x += integral(icoVertices[v1], icoVertices[v2], icoVertices[v3],
				        icoVertices[v1].addClone(icoNormals[v1].multiplyClone((float)nyeInter[v1][0])),
				        icoVertices[v2].addClone(icoNormals[v2].multiplyClone((float)nyeInter[v2][0])),
				        icoVertices[v3].addClone(icoNormals[v3].multiplyClone((float)nyeInter[v3][0]))
				);
				bv.y += integral(icoVertices[v1], icoVertices[v2], icoVertices[v3],
						icoVertices[v1].addClone(icoNormals[v1].multiplyClone((float)nyeInter[v1][1])),
				        icoVertices[v2].addClone(icoNormals[v2].multiplyClone((float)nyeInter[v2][1])),
				        icoVertices[v3].addClone(icoNormals[v3].multiplyClone((float)nyeInter[v3][1]))
		        );
				bv.z += integral(icoVertices[v1], icoVertices[v2], icoVertices[v3],
						icoVertices[v1].addClone(icoNormals[v1].multiplyClone((float)nyeInter[v1][2])),
				        icoVertices[v2].addClone(icoNormals[v2].multiplyClone((float)nyeInter[v2][2])),
				        icoVertices[v3].addClone(icoNormals[v3].multiplyClone((float)nyeInter[v3][2]))
		        );
			}
			
			bv.multiply(rbvCorrectionFactor);
			
			return bv;
		}
		
		private Vec3 calculateLineDirection(int[] dir) {
			double[][] lineDirectionInter = new double[icoVertices.length][];
			for (int i = 0; i<icoVertices.length; i++){ 
				lineDirectionInter[i] = interpolateLineDirection(
						atom.addClone(icoVertices[i]));
				if (lineDirectionInter[i] == null) return null;
			}
			
			Vec3 ld = new Vec3();
			Vec3 i1 = null;
			Vec3 i2 = null;
			Vec3 i3 = null;
			
			for (int i = 0; i<icoFaces.length/3; i++){
				int v1 = icoFaces[i*3+0];
				int v2 = icoFaces[i*3+1];
				int v3 = icoFaces[i*3+2];
				
				float m = (float)(dir[0]*lineDirectionInter[v1][0]+dir[1]*lineDirectionInter[v1][3]+dir[2]*lineDirectionInter[v1][6]);
				i1 = icoNormals[v1].multiplyClone(m).add(icoVertices[v1]);
				i2 = icoNormals[v2].multiplyClone(m).add(icoVertices[v2]);
				i3 = icoNormals[v3].multiplyClone(m).add(icoVertices[v3]);
				ld.x += integral(icoVertices[v1], icoVertices[v2], icoVertices[v3],i1, i2, i3);
				
				m = (float)(dir[0]*lineDirectionInter[v1][1]+dir[1]*lineDirectionInter[v1][4]+dir[2]*lineDirectionInter[v1][7]);
				i1 = icoNormals[v1].multiplyClone(m).add(icoVertices[v1]);
				i2 = icoNormals[v2].multiplyClone(m).add(icoVertices[v2]);
				i3 = icoNormals[v3].multiplyClone(m).add(icoVertices[v3]);
				ld.y += integral(icoVertices[v1], icoVertices[v2], icoVertices[v3],i1, i2, i3);
				
				m = (float)(dir[0]*lineDirectionInter[v1][2]+dir[1]*lineDirectionInter[v1][5]+dir[2]*lineDirectionInter[v1][8]);
				i1 = icoNormals[v1].multiplyClone(m).add(icoVertices[v1]);
				i2 = icoNormals[v2].multiplyClone(m).add(icoVertices[v2]);
				i3 = icoNormals[v3].multiplyClone(m).add(icoVertices[v3]);
				ld.z += integral(icoVertices[v1], icoVertices[v2], icoVertices[v3],i1, i2, i3);
			}

			return ld;
		}
		
		private synchronized void calculateLcm(){
			if (lcm!=null) return;
			if (neighAndVec == null) neighAndVec = nnb.getNeighAndNeighVec(atom);
			
//			Matrix a = new Matrix(3, 3, 0.);
//			Matrix b = new Matrix(3, 3, 0.);
			
			double[] a = new double[9];
			double[] b = new double[9];

			for (int i=0; i<neighAndVec.size(); i++){
				float bestAngle = -1;
				int best = 0;
				Vec3 n = neighAndVec.get(i).o2;
				float l = n.getLength();
				for (int j=0; j<neighPerf.length; j++){
					float angle = (n.dot(neighPerf[j])) / (l* pnl[j]);
					if (angle>bestAngle){
						best = j;
						bestAngle = angle;
					}
				}
				if (bestAngle>PHI_MAX){
//					Matrix nm = new Matrix(new double[][]{{n[0],n[1],n[2]}});
//					Matrix p = new Matrix(new double[][] { { neighPerf.get(best, 0), neighPerf.get(best, 1),
//							neighPerf.get(best, 2) } });
//					b.plusEquals(nm.transpose().times(p));
//					a.plusEquals(nm.transpose().times(nm));
					
					a[0] += n.x * neighPerf[best].x; a[1] += n.x * neighPerf[best].y; a[2] += n.x * neighPerf[best].z;
					a[3] += n.y * neighPerf[best].x; a[4] += n.y * neighPerf[best].y; a[5] += n.y * neighPerf[best].z;
					a[6] += n.z * neighPerf[best].x; a[7] += n.z * neighPerf[best].y; a[8] += n.z * neighPerf[best].z;
					
					b[0] += n.x * n.x; b[1] += n.x * n.y; b[2] += n.x * n.z;
					b[3] += n.y * n.x; b[4] += n.y * n.y; b[5] += n.y * n.z;
					b[6] += n.z * n.x; b[7] += n.z * n.y; b[8] += n.z * n.z;
				}
			}
			
//			lcm = a.inverse().times(b);
			if (MatrixOps.invert3x3matrix(a, 0.001)){
				//lcm = new Matrix(a).times(new Matrix(b)).getArray();
				
				lcm = new float[9];
				lcm[0] = (float)(a[0] * b[0] + a[1] * b[3] +a[2] * b[6]);
				lcm[1] = (float)(a[0] * b[1] + a[1] * b[4] +a[2] * b[7]);
				lcm[2] = (float)(a[0] * b[2] + a[1] * b[5] +a[2] * b[8]);
				         
				lcm[3] = (float)(a[3] * b[0] + a[4] * b[3] +a[5] * b[6]);
				lcm[4] = (float)(a[3] * b[1] + a[4] * b[4] +a[5] * b[7]);
				lcm[5] = (float)(a[3] * b[2] + a[4] * b[5] +a[5] * b[8]);
				         
				lcm[6] = (float)(a[6] * b[0] + a[7] * b[3] +a[8] * b[6]);
				lcm[7] = (float)(a[6] * b[1] + a[7] * b[4] +a[8] * b[7]);
				lcm[8] = (float)(a[6] * b[2] + a[7] * b[5] +a[8] * b[8]);
			}
			else lcm = new float[]{1f,0f,0f,0f,1f,0f,0f,0f,1f};
		}
		
		private void calculateNye(){
			double[][] neigh = new double[neighAndVec.size()][3];
			
			for (int i=0; i<neighAndVec.size(); i++){
				neigh[i][0] = neighAndVec.get(i).o2.x; 
				neigh[i][1] = neighAndVec.get(i).o2.y;
				neigh[i][2] = neighAndVec.get(i).o2.z;
			}
			
			double[][] neighT = new double[neighAndVec.size()][9];
			for (int i = 0; i < neighAndVec.size(); i++) {
				//neighT[i]= neigh[i].transpose().times(neigh[i]);
				
				neighT[i][0] = neigh[i][0] * neigh[i][0]; 
				neighT[i][1] = neigh[i][0] * neigh[i][1]; 
				neighT[i][2] = neigh[i][0] * neigh[i][2];
				
				neighT[i][3] = neigh[i][1] * neigh[i][0]; 
				neighT[i][4] = neigh[i][1] * neigh[i][1];
				neighT[i][5] = neigh[i][1] * neigh[i][2];
				
				neighT[i][6] = neigh[i][2] * neigh[i][0];
				neighT[i][7] = neigh[i][2] * neigh[i][1];
				neighT[i][8] = neigh[i][2] * neigh[i][2];
			}
			
			
			double[] grd = new double[27];
			double[] a = new double[9];
			
			for (int i=0; i<3; i++){
				for (int j=0; j<3; j++){
					Arrays.fill(a, 0.);
					double c0 = 0., c1 = 0., c2 = 0.;
					double e0 = -lcm[i*3+j];
					for (int k = 0; k < neighAndVec.size(); k++) {
//						a.plusEquals(neigh[k].transpose().times(neigh[k]));
						//a.plusEquals(neighT[k]);
						a[0] += neighT[k][0];
						a[1] += neighT[k][1];
						a[2] += neighT[k][2];
						                   
						a[3] += neighT[k][3];
						a[4] += neighT[k][4];
						a[5] += neighT[k][5];
						                   
						a[6] += neighT[k][6];
						a[7] += neighT[k][7];
						a[8] += neighT[k][8];
						RbvInfo<?> info = atomToRbvInfoMap.get(neighAndVec.get(k).o1);
						if (info == null){
							Vec3 b = neighAndVec.get(k).o1;
							RbvInfo<Vec3> infov = new RbvInfo<Vec3>();
							infov.atom = b;
							atomToRbvInfoMap.put(b, infov);
							info = infov;
						}
						if (info.lcm == null) info.calculateLcm();
						double de = -info.lcm[i*3+j] - e0;
//						c.plusEquals(neigh[k].times(de));
						c0 += neigh[k][0]*de;
						c1 += neigh[k][1]*de;
						c2 += neigh[k][2]*de;
					}
									
//					Matrix aa = new Matrix(new double[][]{c}).times(new Matrix(a).inverse());
//					grd[i][j][0] = aa.get(0, 0);
//					grd[i][j][1] = aa.get(0, 1);
					
					MatrixOps.invert3x3matrix(a, 0.001);
					grd[i*9 + j*3 + 0] = c0*a[0] + c1*a[1] + c2*a[2];
					grd[i*9 + j*3 + 1] = c0*a[3] + c1*a[4] + c2*a[5];
					grd[i*9 + j*3 + 2] = c0*a[6] + c1*a[7] + c2*a[8];
				}
			}
			
			nyeTensor = new float[9];
//			for (int i=0; i<3; i++){
//				nyeTensor[i] =   (float)(-grd[18 + i*3 + 1] + grd[9 + i*3 + 2]);
//				nyeTensor[3+i] = (float)(-grd[ 0 + i*3 + 2] + grd[18+ i*3 + 0]);
//				nyeTensor[6+i] = (float)(-grd[ 9 + i*3 + 0] + grd[0 + i*3 + 1]);
//		    }
			
			nyeTensor[0] = (float)(grd[11] - grd[19]);
			nyeTensor[1] = (float)(grd[14] - grd[22]);
			nyeTensor[2] = (float)(grd[17] - grd[25]);
			nyeTensor[3] = (float)(grd[18] - grd[2] );
			nyeTensor[4] = (float)(grd[21] - grd[5] );
			nyeTensor[5] = (float)(grd[24] - grd[8] );
			nyeTensor[6] = (float)(grd[1]  - grd[9] );
			nyeTensor[7] = (float)(grd[4]  - grd[12]);
			nyeTensor[8] = (float)(grd[7]  - grd[15]);
			
			neighAndVec = null; //Let the GC do its work
		}
	}
	
	private class AnalyseCallable implements Callable<Void> {
		private int start, end;
	
		private List<RbvInfo<Atom>> infos;
		private RBVStorage rbvStorage;
		
		public AnalyseCallable(int start, int end, List<RbvInfo<Atom>> atoms, RBVStorage rbvStorage) {
			this.start = start;
			this.end = end;
			this.infos = atoms;
			this.rbvStorage = rbvStorage;
		}

		public Void call() throws Exception{
			for (int i = start; i < end; i++) {
				if (Thread.interrupted()) return null;
				if ((i-start)%1000 == 0)
					ProgressMonitor.getProgressMonitor().addToCounter(1000);
			
				RbvInfo<Atom> info = infos.get(i); 
				Tupel<Vec3,Vec3> rbv = info.calculateBurgersVector();
				if (rbv != null)
					rbvStorage.addRBV(info.atom, rbv.o1, rbv.o2);
			}
			
			ProgressMonitor.getProgressMonitor().addToCounter(end-start%1000);
			return null;
		}
	}

	@Override
	public String getShortName() {
		return "Resultant Burgers vectors";
	}

	@Override
	public String getFunctionDescription() {
		return "Computes resultant Burgers vectors (RBVs)";
	}

	@Override
	public String getRequirementDescription() {
		return "";
	}

	@Override
	public boolean isApplicable(AtomData data) {
		return true;
	}

	@Override
	public boolean showConfigurationDialog(JFrame frame, AtomData data) {
		JPrimitiveVariablesPropertiesDialog dialog = new JPrimitiveVariablesPropertiesDialog(null, getShortName());
		
		dialog.addLabel(getFunctionDescription());
		dialog.add(new JSeparator());	
		
		FloatProperty acceptanceThreshold = dialog.addFloat("acceptanceThreshold", 
				"Fraction of a perfect Burgers vector to accept the computed RBV",
						"<html>If the computed RBV is shorter than this fraction of a perfect Burgers vector, the value is just discarded.<br>"
						+ "<br> Larger values filter more noise, but small details may be lost."
						+ "<br> Min: 0.05, Max: 1.0</html>",
						0.14f, 0.05f, 1f);
		
		BooleanProperty rbvForAllAtoms = dialog.addBoolean("rbvForAllAtoms", 
				"Compute RBVs for all atoms in the lattice, not only for defects",
						"<html>Enabling this options is very time consuming, but may help to identify extended defects in unknown structures<br></html>",
						false);
		
		boolean ok = dialog.showDialog();
		if (ok){
			this.acceptanceThreshold = acceptanceThreshold.getValue();
			this.defectsOnly = !rbvForAllAtoms.getValue();
		}
		return ok;
	}

	@Override
	public DataColumnInfo[] getDataColumnsInfo() {
		return new DataColumnInfo[0];
	}

	@Override
	public ProcessingResult process(AtomData data) throws Exception {
		data.getRbvStorage().clear();
		
		if (data.getGrains() == null || data.getGrains().size() == 0)
			new RbvModule(data, data.getAtoms(), data.getCrystalStructure(), null, this);
		else {
			for (Grain g : data.getGrains())
				new RbvModule(data, g.getAtomsInGrain(), g.getCrystalStructure(), g, this);
		}
		return null;
	}

	@Override
	public boolean canBeAppliedToMultipleFilesAtOnce() {
		return true;
	}
	
}
