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

package processingModules.skeletonizer.processors;

import java.util.List;
import java.util.stream.IntStream;

import Jama.Matrix;
import common.Vec3;
import model.Atom;
import model.RBVStorage;
import processingModules.skeletonizer.*;

/**
 * Mesh contraction that is based on the derived line sense of the RBV data
 * Each node to the point on the plane orthogonal to the line sense vector that minimizes
 * the distance to its neighbors. Furthermore neighbors are weighted in this process by the magnitude of the RBV.
 * Yields quickly better results compared to the standard mesh contraction scheme and is physically reasonable.
 */
public class MeshLineSenseCenteringPreprocessor implements SkeletonPreprocessor {

	@Override
	public void preProcess(Skeletonizer skel) {		
		if (!skel.getAtomData().isRbvAvailable()) return;
		if (skel.getNodes().size() == 0) return;
		
		Vec3[] moveTo = new Vec3[skel.getNodes().size()];
		List<SkeletonNode> nodes = skel.getNodes();
				
		final double defaultLength = 1./skel.getAtomData().getCrystalStructure().getPerfectBurgersVectorLength();
		
		RBVStorage storage = skel.getAtomData().getRbvStorage();
		IntStream.range(0, nodes.size()).parallel().forEach(j->{
			SkeletonNode n = nodes.get(j);
			Atom central = n.getMappedAtoms().get(0);
			
			//Compute a orthogonal basis with the line direction as one normal
			Vec3 line = storage.getRBV(central).lineDirection;
			Vec3 normalA;
			if (Math.abs(line.x) < Math.abs(line.y) && Math.abs(line.x) < Math.abs(line.z)){
				normalA = new Vec3(0f, line.y, -line.z);
			} else if (Math.abs(line.y) < Math.abs(line.z)) {
				normalA = new Vec3(line.x, 0f, -line.z);
			} else normalA = new Vec3(line.x, -line.y, 0f);
			
			Vec3 normalB = line.cross(normalA);
			
			//Create the system of equation, including the possible movement directions (a+b) and the
			//distance vector towards the nearest neighbors, also include the center point itself
			double[][] x = new double[n.getNeigh().size()*3 + 3][2];
			double[][] y = new double[n.getNeigh().size()*3 + 3][1];
			
			for (int i=0; i < n.getNeigh().size(); i++){
				Atom neigh = n.getNeigh().get(i).getMappedAtoms().get(0);
				
				double w = Math.sqrt(storage.getRBV(neigh).bv.getLengthSqr())*defaultLength;
				
				x[i*3+0][0] = normalA.x * w; x[i*3+0][1] = normalB.x * w;
				x[i*3+1][0] = normalA.y * w; x[i*3+1][1] = normalB.y * w;
				x[i*3+2][0] = normalA.z * w; x[i*3+2][1] = normalB.z * w;
				
				Vec3 dir = skel.getAtomData().getBox().getPbcCorrectedDirection(n.getNeigh().get(i), n);
				y[i*3+0][0] = dir.x;
				y[i*3+1][0] = dir.y;
				y[i*3+2][0] = dir.z;
			}
			
			x[n.getNeigh().size()*3+0][0] = normalA.x; x[n.getNeigh().size()*3+0][1] = normalB.x;
			x[n.getNeigh().size()*3+1][0] = normalA.y; x[n.getNeigh().size()*3+1][1] = normalB.y;
			x[n.getNeigh().size()*3+2][0] = normalA.z; x[n.getNeigh().size()*3+2][1] = normalB.z;
			
			y[n.getNeigh().size()*3+0][0] = 0f;
			y[n.getNeigh().size()*3+1][0] = 0f;
			y[n.getNeigh().size()*3+2][0] = 0f;
			
			//Solve equation by least square fitting and move the atom to the coordinate that minimizes the
			//average distance
			Matrix xM = new Matrix(x);
			Matrix yM = new Matrix(y);
			
			Matrix b = xM.solve(yM);
			
			moveTo[j] = new Vec3();
			moveTo[j].x = (float)(n.x + b.get(0, 0)*normalA.x + b.get(1, 0)*normalB.x);
			moveTo[j].y = (float)(n.y + b.get(0, 0)*normalA.y + b.get(1, 0)*normalB.y);
			moveTo[j].z = (float)(n.z + b.get(0, 0)*normalA.z + b.get(1, 0)*normalB.z);
		});
		
		//Move all node to the new position
		for (int i=0; i<nodes.size(); i++){
			nodes.get(i).setTo(moveTo[i]);
		}
	}
}
