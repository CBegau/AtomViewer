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

import java.awt.event.InputEvent;
import java.util.Collection;

import common.Vec3;
import model.AtomData;
import model.BurgersVector;
import model.Pickable;
import model.skeletonizer.Skeletonizer;
import model.skeletonizer.Dislocation;

public class DislocationDensityTensor implements Pickable{

	private double[][] densityTensor = new double[3][3];
	private VolumeElement volumeElement;
	private double density = 0.;
	private double scalarDensity = 0.;
	private double lb_squared = 0.;	//length of dislocation segment times squared Burgers vector
	
	public DislocationDensityTensor(Skeletonizer skel, VolumeElement ve) {
		this.volumeElement = ve;
		if (!skel.getAtomData().isRbvAvailable()) return;
		
		for (Dislocation d: skel.getDislocations()){
			BurgersVector bv;
			Vec3 bvLocal;
			if (d.getBurgersVectorInfo()!=null && d.getBurgersVectorInfo().getBurgersVector().isFullyDefined()){
				bv = d.getBurgersVectorInfo().getBurgersVector();
				bvLocal = bv.getInXYZ(skel.getAtomData().getCrystalRotation());
			} else {
				bv = skel.getAtomData().getCrystalRotation().rbvToBurgersVector(d.getBurgersVectorInfo().getAverageResultantBurgersVector());
				bvLocal = bv.getInXYZ(skel.getAtomData().getCrystalRotation());
				//Consider the pure values are always too small
				bvLocal.multiply(1.5f);
			}
			
			if (bv.getFraction() == 0) continue;
			
			for (int i=0; i<d.getLine().length-1; i++){
				if (ve.isInVolume(d.getLine()[i])){
					Vec3 dir = skel.getAtomData().getBox().getPbcCorrectedDirection(d.getLine()[i], d.getLine()[i+1]);
					
					densityTensor[0][0] += dir.x*bvLocal.x;
					densityTensor[0][1] += dir.x*bvLocal.y;
					densityTensor[0][2] += dir.x*bvLocal.z;
					                                
					densityTensor[1][0] += dir.y*bvLocal.x;
					densityTensor[1][1] += dir.y*bvLocal.y;
					densityTensor[1][2] += dir.y*bvLocal.z;
					                                
					densityTensor[2][0] += dir.z*bvLocal.x;
					densityTensor[2][1] += dir.z*bvLocal.y;
					densityTensor[2][2] += dir.z*bvLocal.z;
					
					lb_squared += dir.getLength() * bvLocal.getLengthSqr();
					
					if (bvLocal.getLength() > 0f)
						scalarDensity += dir.getLength();
				}
			}
		}
		
		double volume = ve.getVolume();
		volume *= 1e-20;
		densityTensor[0][0] /= volume; densityTensor[1][0] /= volume; densityTensor[2][0] /= volume;    
		densityTensor[0][1] /= volume; densityTensor[1][1] /= volume; densityTensor[2][1] /= volume;
		densityTensor[0][2] /= volume; densityTensor[1][2] /= volume; densityTensor[2][2] /= volume;
		
		for (int i=0; i<3;i++){
			for (int j=0; j<3;j++){
				density += Math.abs(densityTensor[i][j]);
				densityTensor[i][j] /= skel.getAtomData().getCrystalStructure().getPerfectBurgersVectorLength();
			}	
		}
		this.density /= skel.getAtomData().getCrystalStructure().getPerfectBurgersVectorLength();
		this.scalarDensity /= volume;
	}
	
	public DislocationDensityTensor(float burgersVectorLength, double[][] densityTensor, CuboidVolumeElement ve) {
		this.volumeElement = ve;		
		double scale = -1e-20; //From 1/A² to 1/m² + invert the sign (to get same signs as in the dislocation analysis)
		
		densityTensor[0][0] /= scale; densityTensor[1][0] /= scale; densityTensor[2][0] /= scale;    
		densityTensor[0][1] /= scale; densityTensor[1][1] /= scale; densityTensor[2][1] /= scale;
		densityTensor[0][2] /= scale; densityTensor[1][2] /= scale; densityTensor[2][2] /= scale;
		
		for (int i=0; i<3;i++){
			for (int j=0; j<3;j++){
				this.density += Math.abs(densityTensor[i][j]);
			}	
		}
		
		this.densityTensor = densityTensor;
		this.density /= burgersVectorLength;
	}
	
	public double[][] getDensityTensor() {
		return densityTensor;
	}
	
	public VolumeElement getVolumeElement() {
		return volumeElement;
	}
	
	/**
	 * The DDT as a scalar value (sum of the absolute tensor components) 
	 * @return
	 */
	public double getDensity() {
		return density;
	}
	
	/**
	 * The sum of each dislocation segment length multiplied with their
	 * squared Burgers vectors' length
	 * @return
	 */
	public double getDislocationsLengthTimesSquaredBurgersVector(){
		return lb_squared;
	}
	
	/**
	 * The scalar dislocation density ignoring the Burgers vectors, only the total length of dislocations
	 * @return
	 */
	public double getScalarDensity() {
		return scalarDensity;
	}
	
	
	@Override
	public Collection<?> getHighlightedObjects() {
		return null;
	}
	
	@Override
	public boolean isHighlightable() {
		return false;
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		
		sb.append("GND(tot)="+String.format("%.4g", density));
		sb.append("\tDD(tot)="+String.format("%.4g", scalarDensity));
		sb.append("\tVol.="+String.format("%.4f", volumeElement.getVolume()));
		
		for (int i=0; i<3; i++){
			for (int j=0; j<3; j++){
				sb.append("\tGND(");
				if (i==0) sb.append("x");
				else if (i==1) sb.append("y");
				else if (i==2) sb.append("z");
				if (j==0) sb.append("x");
				else if (j==1) sb.append("y");
				else if (j==2) sb.append("z");
				sb.append(")="+String.format("%.4g", densityTensor[i][j]));
			}
		}
		return sb.toString();
	}
	
	@Override
	public String printMessage(InputEvent ev, AtomData data) {
		return toString();
	}
	
	@Override
	public Vec3 getCenterOfObject() {
		return null;
	}
}
