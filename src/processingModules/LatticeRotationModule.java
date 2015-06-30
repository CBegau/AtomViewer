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

package processingModules;

import gui.ProgressMonitor;

import java.util.*;
import java.util.concurrent.Callable;

import javax.swing.JFrame;

import model.Atom;
import model.AtomData;
import model.DataColumnInfo;
import model.NearestNeighborBuilder;
import model.polygrain.Grain;
import processingModules.Toolchainable.ToolchainSupport;
import Jama.Matrix;
import Jama.SingularValueDecomposition;
import common.MatrixOps;
import common.ThreadPool;
import common.Vec3;
import crystalStructures.CrystalStructure;

@ToolchainSupport()
public class LatticeRotationModule implements ProcessingModule {
	
	private static DataColumnInfo[] cci = new DataColumnInfo[]{
		new DataColumnInfo("Rotation_X-Axis", "rotX", "", -8f, +8f, false),
		new DataColumnInfo("Rotation_Y-Axis", "rotY", "", -8f, +8f, false),
		new DataColumnInfo("Rotation_Z-Axis", "rotZ", "", -8f, +8f, false),
		new DataColumnInfo("Lattice_tilt", "tilt", "", 0f, +10f, false)
	};
	
	
	@Override
	public boolean showConfigurationDialog(JFrame frame, AtomData data) {
		return true;
	}
	
	@Override
	public ProcessingResult process(final AtomData data) throws Exception {
		final int latticeRotationColumn = data.getIndexForCustomColumn(cci[0]);
		
//		final double PHI_MAX = Math.cos(30*Math.PI/180.);
		
		final HashMap<Integer, Vec3[]> p = new HashMap<Integer, Vec3[]>();
		final HashMap<Integer, float[]> p_l = new HashMap<Integer, float[]>();
		
		CrystalStructure cs = data.getCrystalStructure();
		float[][] rot = data.getCrystalRotation().getDefaultRotationMatrix();
		Vec3[] perf_0 = cs.getPerfectNearestNeighborsUnrotated();
		final int numPerf = perf_0.length;
		
		//Adding the default grain orientation
		Vec3[] p_0 = new Vec3[perf_0.length];
		for (int i=0; i<p_0.length; i++){
			p_0[i] = new Vec3(
				(perf_0[i].x*rot[0][0] + perf_0[i].y*rot[1][0] + perf_0[i].z*rot[2][0]),
				(perf_0[i].x*rot[0][1] + perf_0[i].y*rot[1][1] + perf_0[i].z*rot[2][1]),
				(perf_0[i].x*rot[0][2] + perf_0[i].y*rot[1][2] + perf_0[i].z*rot[2][2]));
			p_0[i].multiply(data.getCrystalStructure().getLatticeConstant());
		}
		float[] p_l_0 = new float[p_0.length];
		for (int i=0; i<p_0.length; i++) p_l_0[i] = p_0[i].getLength();
		p.put(Atom.DEFAULT_GRAIN, p_0);
		p_l.put(Atom.DEFAULT_GRAIN, p_l_0);
		
		//Add different grains for polycrystals
		if (data.getGrains()!=null){
			for (Grain g : data.getGrains()){
				rot = g.getCystalRotationTools().getDefaultRotationMatrix();
				perf_0 = g.getCrystalStructure().getPerfectNearestNeighborsUnrotated();
				
				p_0 = new Vec3[perf_0.length];
				for (int i=0; i<p_0.length; i++){
					p_0[i] = new Vec3(
						(perf_0[i].x*rot[0][0] + perf_0[i].y*rot[1][0] + perf_0[i].z*rot[2][0]),
						(perf_0[i].x*rot[0][1] + perf_0[i].y*rot[1][1] + perf_0[i].z*rot[2][1]),
						(perf_0[i].x*rot[0][2] + perf_0[i].y*rot[1][2] + perf_0[i].z*rot[2][2]));
					p_0[i].multiply(data.getCrystalStructure().getLatticeConstant());
				}
				p_l_0 = new float[p_0.length];
				for (int i=0; i<p_0.length; i++) p_l_0[i] = p_0[i].getLength();
				p.put(g.getGrainNumber(), p_0);
				p_l.put(g.getGrainNumber(), p_l_0);
			}
		}
		
		ProgressMonitor.getProgressMonitor().start(data.getAtoms().size());
		
		final NearestNeighborBuilder<Atom> nnb = new NearestNeighborBuilder<Atom>(data.getBox(), 
				cs.getNearestNeighborSearchRadius(), true);
		nnb.addAll(data.getAtoms());
		
		Vector<Callable<Void>> parallelTasks = new Vector<Callable<Void>>();
		for (int k=0; k<ThreadPool.availProcessors(); k++){
			final int l = k;
			parallelTasks.add(new Callable<Void>() {
				@Override
				public Void call() throws Exception {
					List<Atom> atoms = data.getAtoms();
					int start = (int)(((long)atoms.size() * l)/ThreadPool.availProcessors());
					int end = (int)(((long)atoms.size() * (l+1))/ThreadPool.availProcessors());

					double[][] lcm = new double[3][3];
					double[] a = new double[9];
					double[] b = new double[9];
					Matrix lcmMatrix = new Matrix(lcm);
					
					for (int k=start; k<end; k++){
						if ((k-start)%1000 == 0)
							ProgressMonitor.getProgressMonitor().addToCounter(1000);
						
						if (Thread.interrupted()) return null;
						
						for (int i=0;i<9;i++){
							a[i] = 0f; b[i] = 0f;
						}
						
						Atom atom = atoms.get(k);
						int grain = atom.getGrain();
						if (grain == Atom.IGNORED_GRAIN) continue;
						
						//Pick the current grain data
						Vec3[] p_0 = p.get(grain);
						float[] p_l_0 = p_l.get(grain);
						
						ArrayList<Vec3> neighVecList = nnb.getNeighVec(atom,numPerf);
						
						for (int i=0; i<neighVecList.size(); i++){
							float bestAngle = -1;
							int best = 0;
							Vec3 n = neighVecList.get(i);
							float l = n.getLength();
							for (int j=0; j<p_0.length; j++){
								float angle = n.dot(p_0[j]) / (l* p_l_0[j]);
								if (angle>bestAngle){
									best = j;
									bestAngle = angle;
								}
							}
							//if (bestAngle>PHI_MAX){
								a[0] += n.x * p_0[best].x; a[1] += n.x * p_0[best].y; a[2] += n.x * p_0[best].z;
								a[3] += n.y * p_0[best].x; a[4] += n.y * p_0[best].y; a[5] += n.y * p_0[best].z;
								a[6] += n.z * p_0[best].x; a[7] += n.z * p_0[best].y; a[8] += n.z * p_0[best].z;
								
								b[0] += n.x * n.x; b[1] += n.x * n.y; b[2] += n.x * n.z;
								b[3] += n.y * n.x; b[4] += n.y * n.y; b[5] += n.y * n.z;
								b[6] += n.z * n.x; b[7] += n.z * n.y; b[8] += n.z * n.z;
//							}
						}
						
						if (MatrixOps.invert3x3matrix(a, 0.00001)){
							lcm[0][0] = a[0] * b[0] + a[1] * b[3] +a[2] * b[6];
							lcm[1][0] = a[0] * b[1] + a[1] * b[4] +a[2] * b[7];
							lcm[2][0] = a[0] * b[2] + a[1] * b[5] +a[2] * b[8];
							         
							lcm[0][1] = a[3] * b[0] + a[4] * b[3] +a[5] * b[6];
							lcm[1][1] = a[3] * b[1] + a[4] * b[4] +a[5] * b[7];
							lcm[2][1] = a[3] * b[2] + a[4] * b[5] +a[5] * b[8];
							         
							lcm[0][2] = a[6] * b[0] + a[7] * b[3] +a[8] * b[6];
							lcm[1][2] = a[6] * b[1] + a[7] * b[4] +a[8] * b[7];
							lcm[2][2] = a[6] * b[2] + a[7] * b[5] +a[8] * b[8];
						}
						else continue;
						
						SingularValueDecomposition svd = new SingularValueDecomposition(lcmMatrix);
						Matrix r = svd.getU().times(svd.getV().transpose());
						if (r!=null){
							float[][] foo = new float[3][3];
							foo[0][0] = (float)r.get(0, 0); foo[0][1] = (float)r.get(0, 1); foo[0][2] = (float)r.get(0, 2);
							foo[1][0] = (float)r.get(1, 0); foo[1][1] = (float)r.get(1, 1); foo[1][2] = (float)r.get(1, 2);
							foo[2][0] = (float)r.get(2, 0); foo[2][1] = (float)r.get(2, 1); foo[2][2] = (float)r.get(2, 2);
							
							float[] angles = getAxisRotationAndTilt(getEulerAngles(foo));
							
							int index = latticeRotationColumn;
							atom.setData(angles[0], index);
							atom.setData(angles[1], index+1);
							atom.setData(angles[2], index+2);
							atom.setData(angles[3], index+3);
						}
					}
					
					ProgressMonitor.getProgressMonitor().addToCounter(end-start%1000);
					return null;
				}
			});
		}
		ThreadPool.executeParallel(parallelTasks);
		
		ProgressMonitor.getProgressMonitor().stop();
		return null;
	}
	
	/**
	 * Get the rotation axis around the coordinate systems axes
	 * @param axis 0: X-axis, 1: Y-axis, 2: Z-axis
	 * @return
	 */
	private float[] getAxisRotationAndTilt(float[] eulerAngles){		
		float[] r = new float[4];
		float euler0 = eulerAngles[0];
		float euler1 = eulerAngles[1];
		float euler2 = eulerAngles[2];
		
		double s0 = Math.sin(euler0);
		double s1 = Math.sin(euler1);
		double s2 = Math.sin(euler2);
		double c0 = Math.sqrt(1.-s0*s0);// Math.cos(euler0);
		double c1 = Math.sqrt(1.-s1*s1);// Math.cos(euler1);
		double c2 = Math.sqrt(1.-s2*s2);// Math.cos(euler2);
		
		double y = -Math.asin(-c1*s0);
		
		//Rotation on X-Axis
		r[0] = (float)Math.toDegrees(Math.asin(s1/Math.cos(y)));
		//Rotation on Y-Axis
		r[1] = (float)Math.toDegrees(y);
		//Rotation on Z-Axis
		r[2] = (float)Math.toDegrees(Math.asin( ((s2*c1) / Math.cos(y))));
		//Lattice Tilt
		double a = (s0*s1*s2 + c0*c1 + c0*c2 + c1*c2 - 1.)*0.5;
		r[3] = (float) Math.toDegrees(Math.acos(a));
		
		return r;
	}
	
	/**
	 * Derive euler angles (XYZ-notation) from a rotation matrix
	 * @param rot
	 * @return
	 */
	public static float[] getEulerAngles(float[][] rot){
		float[] eulerAngles = new float[3];
		eulerAngles[0] = -(float)Math.asin(rot[2][0]);
		double c = 1./Math.cos(eulerAngles[0]);
		eulerAngles[1] = (float)Math.atan2(rot[2][1]*c, rot[2][2]*c);
		eulerAngles[2] = (float)Math.atan2(rot[1][0]*c, rot[0][0]*c);
		return eulerAngles;
	}
	
	/**
	 * Derive rotation matrix from euler angles (XYZ-notation)
	 * @param rot
	 * @return
	 */
	public static float[][] getRotationMatrixFromEulerAngles(float[] euler){
		float s1 = (float) Math.sin(euler[2]); float c1 = (float) Math.cos(euler[2]);
		float s2 = (float) Math.sin(euler[1]); float c2 = (float) Math.cos(euler[1]);
		float s3 = (float) Math.sin(euler[0]); float c3 = (float) Math.cos(euler[0]);
		
		float[][] rot = new float[3][3];
		rot[0][0] = s1*s2*s3+c1*c3; rot[0][1] = c1*s2*s3-s1*c3; rot[0][2] = -s1*s2*c3+c1*s3; 
		rot[1][0] = s1*c2; rot[1][1] = c1*c2; rot[1][2] = -s1*s3-c1*s2*c3;
		rot[2][0] = -c2*s3;   rot[2][1] = s2;          rot[2][2] = c2*c3;
		return rot;
	}

	@Override
	public String getShortName() {
		return "Compute lattice rotation";
	}

	@Override
	public String getFunctionDescription() {
		return "Computes lattice rotations per atom.";
	}

	@Override
	public String getRequirementDescription() {
		return "Requires full atomic data";
	}

	@Override
	public boolean canBeAppliedToMultipleFilesAtOnce() {
		return true;
	}
	
	@Override
	public boolean isApplicable(AtomData data) {
		return true;
	}

	@Override
	public DataColumnInfo[] getDataColumnsInfo() {
		return cci;
	}
	
}
