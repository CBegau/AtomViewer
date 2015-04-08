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

package model.dataContainer.dislocationDensity;

import gui.JLogPanel;

import java.util.ArrayList;

import model.*;
import common.MatrixOps;
import common.Tupel;
import common.Vec3;
import common.Vec3Double;
import crystalStructures.CrystalStructure;

public class CuboidSectorDensityTensorBuilder {
	
	private final Vec3Double[] neighPerf;
	private final double[] pnl;
	
	private AtomData data;
	private int rasterX, rasterY, rasterZ;
	
	public CuboidSectorDensityTensorBuilder(AtomData data, int rasterX, int rasterY, int rasterZ) {
		this.data = data;
		
		this.rasterX = rasterX;
		this.rasterY = rasterY;
		this.rasterZ = rasterZ;
		
		if (!data.getBox().isOrtho())
			JLogPanel.getJLogPanel().addLog("Box is non-orthogonal, dislocation densities are inaccurate");
		
		CrystalStructure cs = data.getCrystalStructure();
		float[][] rot = data.getCrystalRotation().getDefaultRotationMatrix();
		Vec3[] perf = cs.getPerfectNearestNeighborsUnrotated();
		neighPerf = new Vec3Double[perf.length];
		for (int i=0; i<neighPerf.length; i++){
			double[] d = new double[3];
			d[0] = (perf[i].x*rot[0][0] + perf[i].y*rot[1][0] + perf[i].z*rot[2][0]);
			d[1] = (perf[i].x*rot[0][1] + perf[i].y*rot[1][1] + perf[i].z*rot[2][1]);
			d[2] = (perf[i].x*rot[0][2] + perf[i].y*rot[1][2] + perf[i].z*rot[2][2]);
			neighPerf[i] = new Vec3Double();
			neighPerf[i].x = d[0] * cs.getLatticeConstant();
			neighPerf[i].y = d[1] * cs.getLatticeConstant();
			neighPerf[i].z = d[2] * cs.getLatticeConstant();
		}

		this.pnl = new double[neighPerf.length];
		for (int i=0; i<this.pnl.length; i++){
			this.pnl[i] = neighPerf[i].getLength();
		}
	}
	
	public DislocationDensityTensor[][][] createCuboids() {
		NearestNeighborBuilder<Vec3> nnb = new NearestNeighborBuilder<Vec3>(data.getBox(),
				data.getCrystalStructure().getNearestNeighborSearchRadius());
		
		Vec3 bounds = data.getBox().getHeight();
		
		for (int i=0; i<data.getAtoms().size(); i++){
			nnb.add(data.getAtoms().get(i));
		}
		ArrayList<Tupel<Vec3, double[][]>> points = new ArrayList<Tupel<Vec3,double[][]>>();
		
		@SuppressWarnings("unchecked")
		Tupel<Vec3,double[][]>[][][] raster = new Tupel[rasterX+1][rasterY+1][rasterZ+1];
		
		for (int i=0; i<=rasterX; ++i){
			for (int j=0; j<=rasterY; ++j){
				for (int k=0; k<=rasterZ; ++k){
					Vec3 p = new Vec3();
					p.x = (bounds.x / rasterX) * i;
					p.y = (bounds.y / rasterY) * j;
					p.z = (bounds.z / rasterZ) * k;
					Tupel<Vec3, double[][]> tupel = new Tupel<Vec3, double[][]>(p, null); 
					raster[i][j][k] = tupel;
					points.add(tupel);
				}
			}	
		}

		//Create cuboids
		Cuboid[][][] cuboids = new CuboidSectorDensityTensorBuilder.Cuboid[rasterX][rasterY][rasterZ];
		
		float xFrac = bounds.x / rasterX;
		float yFrac = bounds.y / rasterY;
		float zFrac = bounds.z / rasterZ;
		
		for (int i=0; i<rasterX; ++i){
			for (int j=0; j<rasterY; ++j){
				for (int k=0; k<rasterZ; ++k){
					Vec3 p = new Vec3();
					p.x = (xFrac) * (i+0.5f);
					p.y = (yFrac) * (j+0.5f);
					p.z = (zFrac) * (k+0.5f);
					Tupel<Vec3, double[][]> tupel = new Tupel<Vec3, double[][]>(p, null); 
					points.add(tupel);
					@SuppressWarnings("unchecked")
					Tupel<Vec3, double[][]>[] corners = new Tupel[8];
					corners[0] = raster[i+0][j+0][k+0];
					corners[1] = raster[i+0][j+0][k+1];
					corners[2] = raster[i+0][j+1][k+0];
					corners[3] = raster[i+0][j+1][k+1];
					corners[4] = raster[i+1][j+0][k+0];
					corners[5] = raster[i+1][j+0][k+1];
					corners[6] = raster[i+1][j+1][k+0];
					corners[7] = raster[i+1][j+1][k+1];
					cuboids[i][j][k] = new Cuboid(corners, tupel);
				}
			}	
		}

		
		for (Tupel<Vec3, double[][]> t : points){
			double[][] dm = getDeformationMatrix(t.o1, nnb);
			t.o2 = dm;
		}
		
		DislocationDensityTensor[][][] cuboidsDens = new DislocationDensityTensor[rasterX][rasterY][rasterZ];
		
		for (int i = 0; i < rasterX; ++i) {
			for (int j = 0; j < rasterY; ++j) {
				for (int k = 0; k < rasterZ; ++k) {
					Cuboid c = cuboids[i][j][k];
					@SuppressWarnings("unchecked")
					Tupel<Vec3, double[][]>[] cornersExt = new Tupel[14];
					for (int l = 0; l < 8; l++)
						cornersExt[l] = c.corners[l];

					Vec3 ce = c.center.o1;

					Vec3 p = new Vec3(ce.x + xFrac, ce.y, ce.z);
					cornersExt[8] = new Tupel<Vec3, double[][]>(p, getDeformationMatrix(p, nnb));
					p = new Vec3(ce.x - xFrac, ce.y, ce.z);
					cornersExt[9] = new Tupel<Vec3, double[][]>(p, getDeformationMatrix(p, nnb));
					p = new Vec3(ce.x, ce.y + yFrac, ce.z);
					cornersExt[10] = new Tupel<Vec3, double[][]>(p, getDeformationMatrix(p, nnb));
					p = new Vec3(ce.x, ce.y - yFrac, ce.z);
					cornersExt[11] = new Tupel<Vec3, double[][]>(p, getDeformationMatrix(p, nnb));
					p = new Vec3(ce.x, ce.y, ce.z + zFrac);
					cornersExt[12] = new Tupel<Vec3, double[][]>(p, getDeformationMatrix(p, nnb));
					p = new Vec3(ce.x, ce.y, ce.z - zFrac);
					cornersExt[13] = new Tupel<Vec3, double[][]>(p, getDeformationMatrix(p, nnb));

					double[][] nye = calculateNye(c.center, cornersExt);
					// double[][] nye = calculateNye(c.center, c.corners);
					CuboidVolumeElement cs = new CuboidVolumeElement(c.corners[7].o1, c.corners[0].o1);
					cuboidsDens[i][j][k] = 
							new DislocationDensityTensor(data.getCrystalStructure().getPerfectBurgersVectorLength(), nye, cs);
				}
			}
		}
		
		return cuboidsDens;
	}
	
	private double[][] getDeformationMatrix(Vec3 p, NearestNeighborBuilder<Vec3> nnb){
		ArrayList<Vec3> neigh = nnb.getNeigh(p);
		
		double[][] avDeform = new double[3][3];
		for (Vec3 c : neigh){
			double[][] d = getDeformationMatrix(nnb.getNeighVec(c));
//			float[][] d = CrystalRotationTools.getRotationMatrixFromEulerAngles(((Atom)c).getEulerAngles());
			
			for (int i=0; i<3; i++){
				for (int j=0; j<3; j++){
					avDeform[i][j] += d[i][j];
				}	
			}
		}
		
		if (neigh.size()>0)
			for (int i=0; i<3; i++){
				for (int j=0; j<3; j++){
					avDeform[i][j] /= neigh.size();
				}	
			}
		else avDeform = new double[][]{{1,0,0},{0,1,0},{0,0,1}};
		
		return avDeform;
	}
	
	
	private double[][] getDeformationMatrix(ArrayList<Vec3> neighVec){
		double[][] lcm = new double[3][3];
		Vec3[] neigh = new Vec3[neighVec.size()];
		
		for (int i=0; i<neighVec.size(); i++){
			neigh[i] = neighVec.get(i); 
		}
				
		double[] a = new double[9];
		double[][] b = new double[3][3];

		for (int i=0; i<neigh.length; i++){
			double bestAngle = -1;
			int best = 0;
			Vec3 n = neigh[i];
			double l = Math.sqrt(n.getLengthSqr());
			for (int j=0; j<neighPerf.length; j++){
				Vec3Double v = neighPerf[j];
				double angle = (n.x*v.x+n.y*v.y+n.z*v.z) / (l* pnl[j]);
				if (angle>bestAngle){
					best = j;
					bestAngle = angle;
				}
			}
			if (bestAngle>Math.cos(20*Math.PI/180.)){	
			a[0] += n.x * neighPerf[best].x; a[1] += n.x * neighPerf[best].y; a[2] += n.x * neighPerf[best].z;
			a[3] += n.y * neighPerf[best].x; a[4] += n.y * neighPerf[best].y; a[5] += n.y * neighPerf[best].z;
			a[6] += n.z * neighPerf[best].x; a[7] += n.z * neighPerf[best].y; a[8] += n.z * neighPerf[best].z;
			
			b[0][0] += n.x * n.x; b[0][1] += n.x * n.y; b[0][2] += n.x * n.z;
			b[1][0] += n.y * n.x; b[1][1] += n.y * n.y; b[1][2] += n.y * n.z;
			b[2][0] += n.z * n.x; b[2][1] += n.z * n.y; b[2][2] += n.z * n.z;
			}
		}
		
		if (MatrixOps.invert3x3matrix(a, 0.001)){
			lcm = new double[3][3];
			lcm[0][0] = a[0] * b[0][0] + a[1] * b[1][0] +a[2] * b[2][0];
			lcm[0][1] = a[0] * b[0][1] + a[1] * b[1][1] +a[2] * b[2][1];
			lcm[0][2] = a[0] * b[0][2] + a[1] * b[1][2] +a[2] * b[2][2];
			
			lcm[1][0] = a[3] * b[0][0] + a[4] * b[1][0] +a[5] * b[2][0];
			lcm[1][1] = a[3] * b[0][1] + a[4] * b[1][1] +a[5] * b[2][1];
			lcm[1][2] = a[3] * b[0][2] + a[4] * b[1][2] +a[5] * b[2][2];
			
			lcm[2][0] = a[6] * b[0][0] + a[7] * b[1][0] +a[8] * b[2][0];
			lcm[2][1] = a[6] * b[0][1] + a[7] * b[1][1] +a[8] * b[2][1];
			lcm[2][2] = a[6] * b[0][2] + a[7] * b[1][2] +a[8] * b[2][2];
		}
		else lcm = new double[][]{{1.,0.,0.},{0.,1.,0.},{0.,0.,1.}};
		return lcm;
	}
	
	
	private double[][] calculateNye(Tupel<Vec3, double[][]> central, Tupel<Vec3, double[][]>[] inter ){
		double[][] neigh = new double[inter.length][3];
		
		
		for (int i=0; i<inter.length; i++){
			Vec3 dir = data.getBox().getPbcCorrectedDirection(inter[i].o1, central.o1);
			
			neigh[i][0] = dir.x; 
			neigh[i][1] = dir.y;
			neigh[i][2] = dir.z;
		}
		
		double[][][] neighT = new double[inter.length][3][3];
		for (int i=0; i<inter.length; i++){
			//neighT[i]= neigh[i].transpose().times(neigh[i]);
			
			neighT[i][0][0] = neigh[i][0] * neigh[i][0]; 
			neighT[i][0][1] = neigh[i][0] * neigh[i][1]; 
			neighT[i][0][2] = neigh[i][0] * neigh[i][2];
			
			neighT[i][1][0] = neigh[i][1] * neigh[i][0]; 
			neighT[i][1][1] = neigh[i][1] * neigh[i][1];
			neighT[i][1][2] = neigh[i][1] * neigh[i][2];
			
			neighT[i][2][0] = neigh[i][2] * neigh[i][0];
			neighT[i][2][1] = neigh[i][2] * neigh[i][1];
			neighT[i][2][2] = neigh[i][2] * neigh[i][2];
		}
		
		
		double[][][] grd = new double[3][3][3];
		
		for (int i=0; i<3; i++){
			for (int j=0; j<3; j++){
				double[] a = new double[9];
				double[] c = new double[3];
				double e0 = central.o2[i][j];
				for (int k=0; k<inter.length; k++){
					a[0] += neighT[k][0][0];
					a[1] += neighT[k][0][1];
					a[2] += neighT[k][0][2];
					
					a[3] += neighT[k][1][0];
					a[4] += neighT[k][1][1];
					a[5] += neighT[k][1][2];
					
					a[6] += neighT[k][2][0];
					a[7] += neighT[k][2][1];
					a[8] += neighT[k][2][2];
					
					double de = inter[k].o2[i][j] - e0;
					c[0] += neigh[k][0]*de;
					c[1] += neigh[k][1]*de;
					c[2] += neigh[k][2]*de;
				}
				
				MatrixOps.invert3x3matrix(a,0.001);
				grd[i][j][0] = c[0]*a[0] + c[1]*a[1] + c[2]*a[2];
				grd[i][j][1] = c[0]*a[3] + c[1]*a[4] + c[2]*a[5];
				grd[i][j][2] = c[0]*a[6] + c[1]*a[7] + c[2]*a[8];
			}
		}
		
		double[][] nyeTensor = new double[3][3];
		for (int i=0; i<3; i++){
			nyeTensor[0][i] = -grd[2][i][1] + grd[1][i][2];
			nyeTensor[1][i] = -grd[0][i][2] + grd[2][i][0];
			nyeTensor[2][i] = -grd[1][i][0] + grd[0][i][1];
		}
		return nyeTensor;
	}
	
	private class Cuboid{
		public Tupel<Vec3, double[][]>[] corners;
		public Tupel<Vec3, double[][]> center;
		public Cuboid(Tupel<Vec3, double[][]>[] corners, Tupel<Vec3, double[][]> center) {
			super();
			this.corners = corners;
			this.center = center;
		}
	}
}

