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

import java.util.ArrayList;

import model.Atom;
import model.NearestNeighborBuilder;
import common.Vec3;

public class SiliconStructure extends DiamondCubicStructure{

	@Override
	protected String getIDName() {
		return "Silicon";
	}
	
	@Override
	protected CrystalStructure deriveNewInstance() {
		return new SiliconStructure();
	}
	
	
	/**
	 * Characterization of multiple phases in Si
	 * @author Junjie Zhang
	 */
	@Override
	public int identifyAtomType(Atom atom, NearestNeighborBuilder<Atom> nnb) {
		ArrayList<Vec3> neighVecLongRange = nnb.getNeighVec(atom);
		if (neighVecLongRange.size() <= 12) return 6;
		ArrayList<Vec3> neighVec = new ArrayList<Vec3>();
		for (Vec3 f : neighVecLongRange)
			if (f.getLengthSqr() < (0.25f * latticeConstant * latticeConstant)) neighVec.add(f);

		if (neighVec.size() == 3) return 5; //3 neighbors distorted surface
		if (neighVec.size() < 3) return 6; // free surface
		else {
			int co_x0 = 0;
			// int co_betaTin = 0;
			int co_betaTin2 = 0;
			int co_bct5 = 0;
			for (int i = 0; i < neighVec.size(); i++) {
				float v_length = neighVec.get(i).getLength();

				for (int j = 0; j < i; j++) {
					float u_length = neighVec.get(j).getLength();
					float a = neighVec.get(i).dot(neighVec.get(j));
					a /= (v_length * u_length); /* cos(angle) */

					// if (a < -0.7) co_betaTin++;
					if (a > 0.14 && a < 0.6) co_betaTin2++;

					/* if (a < -0.173648178 && a > -0.6 ) co_x0++; */
					if (a < -0.1 && a > -0.6) co_x0++;

					if (a < 0.60) co_bct5++;
					/*
					 * if (a> -0.84 && a < 0.30) co_bct5++; /* if (a > -0.21 &&
					 * a < 0.30) co_bct5++; if (a > -0.51 && a < -0.23)
					 * co_bct5_2++; if (a > -0.84 && a < -0.61) co_bct5_3++;
					 */
				}
			}

			if (neighVec.size() < 3) return 6;

			 //if (neighVec.size()==6 && co_betaTin == 3 && co_betaTin2 == 4)
			 //return 1;
			if (neighVec.size() == 6) {
				/* if (co_betaTin == 3 && co_betaTin2 == 4) return 1; /* BetaSn */
				if (co_betaTin2 == 4) return 1;
				else return 4; /* 6 neighbors distorted atoms */
			}

			if (neighVec.size() == 5) {
				if (co_bct5 == 10) return 2; /* BCT5 */
				else return 7; /* 5 neighbors distorted atoms */
			}

			
			//if (neighVec.size()==5 && co_bct5==4 && co_betaTin == 2 && co_x0 == 4) return 2;
			//if (neighVec.size()==5 && co_bct5==4 && co_bct5_2 == 4 && * co_bct5_3 == 2) return 2; /* BCT5 phase*/

			if (neighVec.size() == 4) {
				if (co_x0 == 6) return 0; /* Diamond Cubic */
				else return 3; /* bc8/r8 */
			}
			/* if (neighVec.size() == 5) return 2; /* distorted 5 neighbors */
			else return 8; /* > 6 neighbors atoms */
		}
	}
	
	@Override
	public int getSurfaceType() {
		return 6;
	}

	@Override
	public int getNumberOfTypes() {
		return 9;
	};
	
	@Override
	public String getNameForType(int i) {
		switch (i) {
			case 0: return "Diamond Cubic";
			case 1: return "BetaSn";
			case 2: return "BCT5";
			case 3: return "BC8/R8";
			case 4: return "6 neighbors (distort)"; 
/*			case 5: return "3 neighbors (distort)";		*/
			case 5: return "Distorted Surface";
			case 6: return "Free Surface";
			case 7: return "5 neighbors (distort)";
			case 8: return ">6 neighbors (distort)";
			default: return "unknown";
		}
	}
	
}
