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

package crystalStructures;

import java.util.*;
import java.util.concurrent.Callable;

import javax.swing.JFrame;

import processingModules.ProcessingModule;
import processingModules.ProcessingResult;
import processingModules.toolchain.Toolchainable.ToolchainSupport;
import Jama.Matrix;
import model.Atom;
import model.AtomData;
import model.DataColumnInfo;
import model.NearestNeighborBuilder;
import common.ThreadPool;
import common.Vec3;
import common.ColorTable.ColorBarScheme;

@ToolchainSupport
public final class MonoclinicNiTi extends B2NiTi implements ProcessingModule{
	public static final int IGNORED_VARIANT = -1;
	public static final int UNKNOWN_VARIANT = 13;
	
	private static DataColumnInfo variantColumn = new DataColumnInfo("Variant", "variant", "",  
			0f, 13f, true, ColorBarScheme.MARTENSITE_COLORING);
	
	//Both in <100>/<010>/<001> orientation
//	private static double[][] neighPerfTiTi = new double[][]{
//			{1.7999935 , 2.3601036, 0.0},
//			{-2.5458164, 2.3601036, 0.0},
//			{2.172903  ,-1.974308 , 0.0},
//			{-2.172905 ,-1.974308 , 0.0},
//			{0.0       , 0.0      , 2.8998308},
//			{0.0       , 0.0      ,-2.8998299}
//		};
	
	private static Vec3[] neighPerfNiNi = new Vec3[]{
			new Vec3(1.7925072f , 1.9250374f , 0.0f),
			new Vec3(-2.5533028f, 1.9250374f , 0.0f),
			new Vec3(2.172905f  ,-2.4093723f , 0.0f),
			new Vec3(-2.172903f ,-2.4093723f , 0.0f),
			new Vec3(0.0f       , 0.0f       , 2.8998299f),
			new Vec3(0.0f       , 0.0f       ,-2.8998299f)
		};
	
	private static Matrix neighPerfNiNiMatrix; 
	
	static {
		//Rotate this coordinate system to the orientation of the comparing patters
		float[][] defRot = new float[][]{{0.70710677f, -0.70710677f, 0.0f}, {0.70710677f, 0.70710677f, 0.0f}, {0.0f, 0.0f, 1.0f}};
		for (int i=0; i<neighPerfNiNi.length; i++){
			Vec3 d = new Vec3();
			d.x = (neighPerfNiNi[i].x*defRot[0][0] + neighPerfNiNi[i].y*defRot[1][0] + neighPerfNiNi[i].z*defRot[2][0]);
			d.y = (neighPerfNiNi[i].x*defRot[0][1] + neighPerfNiNi[i].y*defRot[1][1] + neighPerfNiNi[i].z*defRot[2][1]);
			d.z = (neighPerfNiNi[i].x*defRot[0][2] + neighPerfNiNi[i].y*defRot[1][2] + neighPerfNiNi[i].z*defRot[2][2]);
			neighPerfNiNi[i] = d;
		}
		neighPerfNiNi = sortBondsNi(neighPerfNiNi);
		//neighPerfTiTi = sortBondsNi(neighPerfTiTi);
		
		double[][] neighPerfNiNiM = new double[neighPerfNiNi.length][3];
		for (int i=0; i<neighPerfNiNi.length; i++){
			neighPerfNiNiM[i][0] = neighPerfNiNi[i].x;
			neighPerfNiNiM[i][1] = neighPerfNiNi[i].y;
			neighPerfNiNiM[i][2] = neighPerfNiNi[i].z;
		}
		neighPerfNiNiMatrix = new Matrix(neighPerfNiNiM);
	}
	
	public MonoclinicNiTi(){};
	
	@Override
	protected String getIDName() {
		return "MonoclinicNiTi";
	}
	
	protected MonoclinicNiTi(float latticeConstant, float nearestNeighborSearchRadius) {
		super();
		this.latticeConstant = latticeConstant;
		this.nearestNeighborSearchRadius = nearestNeighborSearchRadius;
	}
	
	@Override
	public DataColumnInfo[] getDataColumnsInfo() {
		return new DataColumnInfo[]{variantColumn};
	}
	
	@Override
	public boolean isApplicable(AtomData data) {
		return data.getCrystalStructure() instanceof B2NiTi;
	}
	
	@Override
	public String getShortName() {
		return "Identify martensite variants";
	}
	
	@Override
	public String getFunctionDescription() {
		return "Identify martensite variants";
	}
	
	@Override
	public String getRequirementDescription() {
		return "";
	}
	
	@Override
	public ProcessingResult process(final AtomData data){
		final NearestNeighborBuilder<Atom> nnb = new NearestNeighborBuilder<Atom>(
				data.getBox(), this.getNearestNeighborSearchRadius());
		
		final float[][] rot = data.getCrystalRotation().getDefaultRotationMatrix();
		final int martColumn = data.getIndexForCustomColumn(variantColumn);
		
		for (Atom a : data.getAtoms()){
			if (a.getElement() % 2 == 1) nnb.add(a);
			else a.setData(IGNORED_VARIANT, martColumn);
		}
		
		Vector<Callable<Void>> parallelTasks = new Vector<Callable<Void>>();
		for (int i=0; i<ThreadPool.availProcessors(); i++){
			final int j = i;
			parallelTasks.add(new Callable<Void>() {
				@Override
				public Void call() throws Exception {
					final int start = (int)(((long)data.getAtoms().size() * j)/ThreadPool.availProcessors());
					final int end = (int)(((long)data.getAtoms().size() * (j+1))/ThreadPool.availProcessors());
					for (int i=start; i<end; i++){
						Atom a = data.getAtoms().get(i);
						if (a.getElement() % 2 == 1 && (a.getType() == 3 || a.getType() == 4)) 
							a.setData(getVariant(a, nnb, rot),martColumn);
						else a.setData(IGNORED_VARIANT, martColumn);
					}
					return null;
				}
			});
		}
		ThreadPool.executeParallel(parallelTasks);
		return null;
	}
	
	private static Vec3[] sortBondsNi(Vec3[] unsortedBonds){
		Vec3[] sort = new Vec3[3];

		//Put all bonds into a list 
		Vector<Vec3> ub = new Vector<Vec3>();
		for (int i=0; i<unsortedBonds.length; i++) ub.add(unsortedBonds[i]);
		//Step 1: pick point closest to center -> sort[0]
		float nearestDist = Float.MAX_VALUE;
		int num = 0;
		for (int i=0; i<ub.size();i++){
			float dist = ub.get(i).getLengthSqr();
			if (nearestDist>dist) {
				nearestDist = dist;
				num = i;
			}
		}
		sort[0] = ub.remove(num);
		
		//Step 2: select closest point to sort[0]
		nearestDist = Float.MAX_VALUE;
		num = 0;
		for (int i = 0; i < ub.size(); i++) {
			float dist =  ub.get(i).getDistTo(sort[0]);
			if (nearestDist>dist) {
				nearestDist = dist;
				num = i;
			}
		}
		sort[1] = ub.remove(num);
		
		Vec3 p = sort[0].cross( sort[1]);
		float largestDist = 0f;
		num = 0;
		for (int i=0; i<ub.size();i++){
			float d = ub.get(i).dot(p);
			if (largestDist<d) {
				largestDist = d;
				num = i;
			}
		}
		sort[2] = ub.remove(num);
		return sort;
	}
	
	private static int getVariant(Atom a, NearestNeighborBuilder<Atom> nnb, float[][] rot){
		if (a.getElement() % 2 == 0) return UNKNOWN_VARIANT;
		ArrayList<Vec3> nn = nnb.getNeighVec(a);
	
		if (nn.size() < 4 ) return -1;
		
		Vec3[] neighVec = new Vec3[nn.size()];
		
		for (int i=0; i<nn.size(); i++){
			neighVec[i] = new Vec3();
			neighVec[i].x = nn.get(i).x*rot[0][0] + nn.get(i).y*rot[0][1] + nn.get(i).z*rot[0][2];
			neighVec[i].y = nn.get(i).x*rot[1][0] + nn.get(i).y*rot[1][1] + nn.get(i).z*rot[1][2];
			neighVec[i].z = nn.get(i).x*rot[2][0] + nn.get(i).y*rot[2][1] + nn.get(i).z*rot[2][2];
		}
		
		neighVec = sortBondsNi(neighVec);
		
		double[][] neighVecM = new double[neighVec.length][3];
		for (int i=0; i<neighVecM.length; i++){
			neighVecM[i][0] = neighVec[i].x;
			neighVecM[i][1] = neighVec[i].y;
			neighVecM[i][2] = neighVec[i].z;
		}
		
		Matrix trans;
		try {
			trans = new Matrix(neighVecM).solve(neighPerfNiNiMatrix);
		} catch (RuntimeException e){
			return UNKNOWN_VARIANT;
		}
		int[] m = new int[9];
		m[0] = Math.abs(trans.get(0, 0))<0.75 ? 0 : (trans.get(0, 0)>0. ? 1:-1);
		m[1] = Math.abs(trans.get(0, 1))<0.75 ? 0 : (trans.get(0, 1)>0. ? 1:-1);
		m[2] = Math.abs(trans.get(0, 2))<0.75 ? 0 : (trans.get(0, 2)>0. ? 1:-1);
		                                                 
		m[3] = Math.abs(trans.get(1, 0))<0.75 ? 0 : (trans.get(1, 0)>0. ? 1:-1);
		m[4] = Math.abs(trans.get(1, 1))<0.75 ? 0 : (trans.get(1, 1)>0. ? 1:-1);
		m[5] = Math.abs(trans.get(1, 2))<0.75 ? 0 : (trans.get(1, 2)>0. ? 1:-1);
		                                                 
		m[6] = Math.abs(trans.get(2, 0))<0.75 ? 0 : (trans.get(2, 0)>0. ? 1:-1);
		m[7] = Math.abs(trans.get(2, 1))<0.75 ? 0 : (trans.get(2, 1)>0. ? 1:-1);
		m[8] = Math.abs(trans.get(2, 2))<0.75 ? 0 : (trans.get(2, 2)>0. ? 1:-1);
		
		int ones = 0;
		for (int i=0; i<9; i++){
			if (m[i] != 0) ones++;
		}
		
		if (ones!=3)
			return -1;
		
		if (m[6] == -1 || m[7] == -1 || m[8] == -1){
			m[3] *=-1; m[4] *=-1; m[5] *=-1;
		}
		
		if (m[0] == 1 && m[4] == 1) return 0;
		if (m[0] == 1 && m[4] ==-1) return 1;
		if (m[0] ==-1 && m[4] ==-1) return 0;
		if (m[0] ==-1 && m[4] == 1) return 1;
		
		if (m[1] == 1 && m[3] == 1) return 3;
		if (m[1] == 1 && m[3] ==-1) return 2;
		if (m[1] ==-1 && m[3] ==-1) return 3;
		if (m[1] ==-1 && m[3] == 1) return 2;
		    
		if (m[1] == 1 && m[5] == 1) return 4;
		if (m[1] == 1 && m[5] ==-1) return 5;
		if (m[1] ==-1 && m[5] ==-1) return 4;
		if (m[1] ==-1 && m[5] == 1) return 5;
		    
		if (m[0] == 1 && m[5] == 1) return 7;
		if (m[0] == 1 && m[5] ==-1) return 6;
		if (m[0] ==-1 && m[5] ==-1) return 7;
		if (m[0] ==-1 && m[5] == 1) return 6;
		    
		if (m[2] == 1 && m[3] == 1) return 8;
		if (m[2] == 1 && m[3] ==-1) return 9;
		if (m[2] ==-1 && m[3] ==-1) return 8;
		if (m[2] ==-1 && m[3] == 1) return 9;
		    
		if (m[2] == 1 && m[4] == 1) return 11;
		if (m[2] == 1 && m[4] ==-1) return 10;
		if (m[2] ==-1 && m[4] ==-1) return 11;
		if (m[2] ==-1 && m[4] == 1) return 10;
		
		return UNKNOWN_VARIANT;
	}
	
	@Override
	public boolean showConfigurationDialog(JFrame frame, AtomData data) {
		return true;
	}

	@Override
	public boolean canBeAppliedToMultipleFilesAtOnce() {
		return true;
	}
}
