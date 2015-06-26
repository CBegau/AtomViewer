package crystalStructures;

import java.util.ArrayList;

import model.Atom;
import model.NearestNeighborBuilder;
import common.Vec3;

public class HCPStructure extends CrystalStructure {

	private final static int FCC = 1;
	private final static int HCP = 2;
	
	private static Vec3[] neighPerfHCP = new Vec3[]{
		new Vec3(1f, 0f, 0f),
		new Vec3(-1f, 0f, 0f),

		new Vec3(0.866f, 0.5f, 0f),
		new Vec3(0.866f, -0.5f, 0f),
		new Vec3(-0.866f, -0.5f, 0f),
		new Vec3(-0.866f, 0.5f, 0f),
		
		new Vec3(0f, 0.7906f, 0.6124f),
		new Vec3(0f, 0.7906f,-0.6124f),
		
		new Vec3(0.5f, 0.6124f, 0.6124f),
		new Vec3(0.5f, 0.6124f, -0.6124f),
		
		new Vec3(-0.5f, 0.5f, 0.6124f),
		new Vec3(-0.5f, 0.5f,-0.6124f),
	};
	
	@Override
	protected String getIDName() {
		return "HCP";
	}

	@Override
	protected CrystalStructure deriveNewInstance() {
		return new HCPStructure();
	}

	@Override
	public int getNumberOfTypes() {
		return 8;
	}

	@Override
	public int identifyAtomType(Atom atom, NearestNeighborBuilder<Atom> nnb) {
		final float threshold = -0.945f;
		ArrayList<Vec3> neigh = nnb.getNeighVec(atom);
		/*
		 * type=0: bcc
		 * type=1: fcc
		 * type=2: hcp
		 * type=3: unassigned
		 * type=4: less than 12 neighbors
		 * type=5: more than 12 neighbors
		 * type=6: less than 10 neighbors
		 */
		if (neigh.size() < 10) return 6;
		else if (neigh.size() < 12) return 4;
		else if (neigh.size() > 14) return 7;
		else {
			int co_x0 = 0;
			int co_x1 = 0;
			int co_x2 = 0;
			for (int i = 0; i < neigh.size(); i++) {
				Vec3 v = neigh.get(i);
				float v_length = v.getLength();
				for (int j = 0; j < i; j++) {
					Vec3 u = neigh.get(j);
					float u_length = u.getLength();
					float a = v.dot(u) / (v_length*u_length);
					
					if (a < threshold) co_x0++; // 0.945
					else if (a < -.915f) co_x1++;
					else if (a < -.775f) co_x2++; // 0.755
				}
			}

			if (co_x0 == 7 && neigh.size() == 14) return 0;
			else if (co_x0 == 6 && neigh.size() == 12) return FCC;
			else if (co_x0 == 3 && co_x1 <= 1 && co_x2 > 2 && neigh.size() == 12) return HCP;
			else if (neigh.size() > 12) return 5;
			else if (neigh.size() == 12) return 3;
			else return 4;
		}
	}

	@Override
	public int getNumberOfNearestNeighbors() {
		return 12;
	}

	@Override
	public boolean isRBVToBeCalculated(Atom a) {
		return a.getType()!=HCP;
	}

	@Override
	public String getNameForType(int i) {
		switch (i) {
		case 0: return "bcc";
		case FCC: return "fcc";
		case HCP: return "hcp";
		case 3: return "12 neigh., unassigned";
		case 4: return "10-11 neighbors";
		case 5: return "13-14 neighbors";
		case 6: return "<10 neighbors";
		case 7: return ">14 neighbors";
		default: return "unknown";
	}
	}

	@Override
	public int getDefaultType() {
		return HCP;
	}

	@Override
	public int getSurfaceType() {
		return 6;
	}

	@Override
	public float getPerfectBurgersVectorLength() {
		return 0.47f*latticeConstant;
	}

	@Override
	public Vec3[] getPerfectNearestNeighborsUnrotated() {
		return neighPerfHCP.clone();
	}

	@Override
	public float getDefaultNearestNeighborSearchScaleFactor() {
		return 1.1f;
	}

}
