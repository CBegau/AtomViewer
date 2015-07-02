package processingModules.otherModules;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashSet;

import javax.swing.JFrame;

import common.Vec3;
import model.*;
import model.BurgersVector.BurgersVectorType;
import model.skeletonizer.Dislocation;
import model.skeletonizer.SkeletonNode;
import model.skeletonizer.Skeletonizer;
import processingModules.ProcessingModule;
import processingModules.ProcessingResult;

public class EnergyAnalysisProcessingModule implements ProcessingModule {
	
	@Override
	public ProcessingResult process(AtomData atomData) throws IOException {	
		File f5 = new File(Configuration.getLastOpenedFolder(), atomData.getName()+"_5nm.txt");
		writeData(atomData, f5, 0, atomData.getBox().getHeight().z, 50);
		
		File f625 = new File(Configuration.getLastOpenedFolder(),atomData.getName()+"_6,25nm.txt");
		writeData(atomData, f625, 0, atomData.getBox().getHeight().z, 62.5f);
		
		File f125 = new File(Configuration.getLastOpenedFolder(),atomData.getName()+"_12,5nm.txt");
		writeData(atomData, f125, 0, atomData.getBox().getHeight().z, 125);
		
		File f25 = new File(Configuration.getLastOpenedFolder(),atomData.getName()+"_25nm.txt");
		writeData(atomData, f25, 0, atomData.getBox().getHeight().z, 250);
		
		File fmax = new File(Configuration.getLastOpenedFolder(),atomData.getName()+"_max.txt");
		writeData(atomData, fmax, 0, atomData.getBox().getHeight().z, atomData.getBox().getHeight().minComponent());
		
		return null;
	}

	@Override
	public String getFunctionDescription() {
		return "Energy Dislocation Stuff";
	}
	
	@Override
	public String getShortName() {
		return "Energy Dislocation Stuff";
	}

	
	private void writeData(AtomData atomData, File f, float minZ, float maxZ, float blocksize){
		int bx, by, bz;
		
		
		BoxParameter bp = atomData.getBox();
		bx = Math.round(bp.getHeight().x/blocksize);
		by = Math.round(bp.getHeight().y/blocksize);
		bz = Math.round((maxZ-minZ)/blocksize);
		
		float hx = (bp.getHeight().x/bx);
		float hy = (bp.getHeight().y/by);
		float hz = ((maxZ-minZ)/bz);
		
		
		EngData[][][] eng = new EngData[bx][by][bz];
		for (int i=0; i<bx; i++){
			for (int j=0; j<by; j++){
				for (int k=0; k<bz; k++){
					eng[i][j][k] = new EngData( i,j,k, (i+0.5f*hx), (j+0.5f*hy), (k+0.5f*hz), hx, hy, hz);
				}	
			}	
		}
		
		hx = 1f/hx;
		hy = 1f/hy;
		hz = 1f/hz;
		
		int ec = -1;
		for (int i=0; i<atomData.getDataColumnInfos().size(); i++){
			if (atomData.getDataColumnInfos().get(i).getName().equals("Epot"))
				ec = i;
		}
		
		//Mark atoms in dislocations
		HashSet<Atom> dislocationAtoms = new HashSet<Atom>();
		Skeletonizer skel = (Skeletonizer)atomData.getDataContainer(Skeletonizer.class);
		for (Dislocation d : skel.getDislocations()){
			if ( d.getBurgersVectorInfo().getAverageResultantBurgersVector().getLength()>0.2f && 
					d.getBurgersVectorInfo().getBurgersVector().getType() != BurgersVectorType.ZERO){
				//Mark atoms in proper dislocations
				for (SkeletonNode n : d.getLine()){
					for (Atom a : n.getMappedAtoms()){
						dislocationAtoms.add(a);
					}
				}
			}
		}
		
		for (Atom a : atomData.getAtoms()){
			int x = (int) (hx * a.x);
			int y = (int) (hy * a.y);
			int z = (int) (hz * a.z);
			
			if (x>=bx) x -= bx;
			if (y>=by) y -= by;
			if (z>=bz) z -= bz;
			
			if (x<0) x += bx;
			if (y<0) y += by;
			if (z<0) z += bz;
			
			float e = a.getData(ec); 
			
			eng[x][y][z].atoms++;
			eng[x][y][z].eSum += e;
			
			if ( a.getType() == 1){
				eng[x][y][z].atomsBulk++;
				eng[x][y][z].eBulk += e;
			} else if (a.getType() == 2 || a.getType() == 3){
				eng[x][y][z].atomsStackingFault++;
				eng[x][y][z].eStackingFault += e;
			} else if (a.getType() == 6){
				eng[x][y][z].atomsSurface++;
				eng[x][y][z].eSurface += e;
			} else if (a.getGrain() == Atom.IGNORED_GRAIN){
				eng[x][y][z].atomsGrainBoundary++;
				eng[x][y][z].eGrainBoundary += e;
			} else if (dislocationAtoms.contains(a)){
				eng[x][y][z].atomsCore++;
				eng[x][y][z].eCore += e;
			} else {
				eng[x][y][z].atomsDefect++;
				eng[x][y][z].eDefect += e;
			}
		}
		
		for (Dislocation d : skel.getDislocations()){
			if ( d.getBurgersVectorInfo().getAverageResultantBurgersVector().getLength()>0.2f && 
					d.getBurgersVectorInfo().getBurgersVector().getType() != BurgersVectorType.ZERO){
				BurgersVector bv;
				Vec3 bvLocal;
				if (d.getBurgersVectorInfo()!=null && d.getBurgersVectorInfo().getBurgersVector().isFullyDefined()){
					bv = d.getBurgersVectorInfo().getBurgersVector();
					bvLocal = bv.getInXYZ(atomData.getCrystalRotation());
				} else {
					bv = atomData.getCrystalRotation().rbvToBurgersVector(d.getBurgersVectorInfo().getAverageResultantBurgersVector());
					bvLocal = bv.getInXYZ(atomData.getCrystalRotation());
					//Consider the pure values are always too small
					bvLocal.multiply(1.5f);
				}
				
				//if (bv.getFraction() == 0) continue;
				
				for (int i=0; i<d.getLine().length-1; i++){
					Vec3 dir = atomData.getBox().getPbcCorrectedDirection(d.getLine()[i], d.getLine()[i+1]);
					float segmentLength = dir.getLength();
					float lTimesBurgerSquared = segmentLength * bvLocal.getLengthSqr();
					
					int atomToDistributeDisTo = d.getLine()[i].getMappedAtoms().size() + d.getLine()[i+1].getMappedAtoms().size();
					
					double weight = 1./atomToDistributeDisTo;

					bvLocal = atomData.getCrystalRotation().getInCrystalCoordinates(bvLocal);
					dir = atomData.getCrystalRotation().getInCrystalCoordinates(dir);
					
					for (Atom a : d.getLine()[i].getMappedAtoms()){
						int x1 = (int) (hx * a.x);
						int y1 = (int) (hy * a.y);
						int z1 = (int) (hz * a.z);
						
						if (x1>=bx) x1 -= bx;
						if (y1>=by) y1 -= by;
						if (z1>=bz) z1 -= bz;
						
						if (x1<0) x1 += bx;
						if (y1<0) y1 += by;
						if (z1<0) z1 += bz;
						
						eng[x1][y1][z1].lengthDisloactions += segmentLength*weight;
						eng[x1][y1][z1].lengthTimesBsquared += lTimesBurgerSquared*weight;
						
						eng[x1][y1][z1].gnd[0][0] += bvLocal.x * dir.x * weight;
						eng[x1][y1][z1].gnd[1][0] += bvLocal.x * dir.y * weight;
						eng[x1][y1][z1].gnd[2][0] += bvLocal.x * dir.z * weight;
						                      
						eng[x1][y1][z1].gnd[0][1] += bvLocal.y * dir.x * weight;
						eng[x1][y1][z1].gnd[1][1] += bvLocal.y * dir.y * weight;
						eng[x1][y1][z1].gnd[2][1] += bvLocal.y * dir.z * weight;
						                      
						eng[x1][y1][z1].gnd[0][2] += bvLocal.z * dir.x * weight;
						eng[x1][y1][z1].gnd[1][2] += bvLocal.z * dir.y * weight;
						eng[x1][y1][z1].gnd[2][2] += bvLocal.z * dir.z * weight;
					}
					
					for (Atom a : d.getLine()[i+1].getMappedAtoms()){
						int x1 = (int) (hx * a.x);
						int y1 = (int) (hy * a.y);
						int z1 = (int) (hz * a.z);
						
						if (x1>=bx) x1 -= bx;
						if (y1>=by) y1 -= by;
						if (z1>=bz) z1 -= bz;
						
						if (x1<0) x1 += bx;
						if (y1<0) y1 += by;
						if (z1<0) z1 += bz;
						
						eng[x1][y1][z1].lengthDisloactions += segmentLength*weight;
						eng[x1][y1][z1].lengthTimesBsquared += lTimesBurgerSquared*weight;
						
						eng[x1][y1][z1].gnd[0][0] += bvLocal.x * dir.x * weight;
						eng[x1][y1][z1].gnd[1][0] += bvLocal.x * dir.y * weight;
						eng[x1][y1][z1].gnd[2][0] += bvLocal.x * dir.z * weight;
						                      
						eng[x1][y1][z1].gnd[0][1] += bvLocal.y * dir.x * weight;
						eng[x1][y1][z1].gnd[1][1] += bvLocal.y * dir.y * weight;
						eng[x1][y1][z1].gnd[2][1] += bvLocal.y * dir.z * weight;
						                      
						eng[x1][y1][z1].gnd[0][2] += bvLocal.z * dir.x * weight;
						eng[x1][y1][z1].gnd[1][2] += bvLocal.z * dir.y * weight;
						eng[x1][y1][z1].gnd[2][2] += bvLocal.z * dir.z * weight;
					}
				}

					
			}
		}
		
		PrintWriter pw = null;
		try{
			pw = new PrintWriter(f);
			pw.println(eng[0][0][0].getDataHeader());

			pw.println(String.format("#Blocks;%d;%d;%d", bx, by,by));
			pw.println(String.format("#Blocksize;%.8f;%.8f;%.8f", (bp.getHeight().x/bx), (bp.getHeight().y/by), (maxZ-minZ)/bz));
			pw.println(String.format("#Volume;%.8f", (bp.getHeight().x/bx)*(bp.getHeight().y/by)*(maxZ-minZ)/bz));
			
			
			for (int i=0; i<bx; i++){                                 
				for (int j=0; j<by; j++){
					for (int k=0; k<bz; k++){
						pw.println(eng[i][j][k].toString());
					}	
				}
			}
		} catch(IOException e){
			e.printStackTrace();
		} finally{
			if (pw!=null) pw.close();
		}
		
	}

	
	private class EngData{
		final Vec3 center = new Vec3();
		final Vec3 size = new Vec3();
//		final float volume;
		final int x,y,z;
		
		double eSum 		 = 0f;
		double eBulk 		 = 0f;
		double eCore 		 = 0f;
		double eStackingFault= 0f;
		double eDefect 		 = 0f;
		double eSurface 	 = 0f;
		double eGrainBoundary= 0f;
		
		int atoms 			   = 0;
		int atomsBulk          = 0;
		int atomsCore 		   = 0;
		int atomsStackingFault = 0;
		int atomsDefect 	   = 0;
		int atomsSurface       = 0;
		int atomsGrainBoundary = 0;
		
		
		double lengthDisloactions = 0f;
		double lengthTimesBsquared = 0f;
		double[][] gnd = new double[3][3];
		
		public EngData(int px, int py, int pz, float x, float y, float z, float sx, float sy, float sz) {
			this.center.setTo(x,y,z);
			this.size.setTo(sx,sy,sz);
//			this.volume = sx*sy*sz;
			this.x = px;
			this.y = py;
			this.z = pz;
		}
		
		public String getDataHeader(){
			StringBuilder sb = new StringBuilder();
			sb.append("x"); sb.append(';');
			sb.append("y"); sb.append(';');
			sb.append("z"); sb.append(';');
//			sb.append("center.x"); sb.append(';');
//			sb.append("center.y"); sb.append(';');
//			sb.append("center.z"); sb.append(';');
//			sb.append("size.x");   sb.append(';');
//			sb.append("size.y");   sb.append(';');
//			sb.append("size.z");   sb.append(';');
//			sb.append("volume");   sb.append(';');
			
			sb.append("eSum" 		  ); sb.append(';');
			sb.append("eBulk" 		  ); sb.append(';');
			sb.append("eCore" 		  ); sb.append(';');
			sb.append("eStackingFault"); sb.append(';');
			sb.append("eDefect" 	  ); sb.append(';');
			sb.append("eSurface"      ); sb.append(';');
			sb.append("eGrainBoundary"); sb.append(';');
			
			sb.append("atoms" 		      ); sb.append(';');
			sb.append("atomsBulk"         ); sb.append(';');
			sb.append("atomsCore" 		  ); sb.append(';');
			sb.append("atomsStackingFault"); sb.append(';');
			sb.append("atomsDefect" 	  ); sb.append(';');
			sb.append("atomsSurface"      ); sb.append(';');
			sb.append("atomsGrainBoundary"); sb.append(';');
			
			sb.append("lengthDisloactions" ); sb.append(';');
			sb.append("lengthTimesBsquared"); sb.append(';');
			
			sb.append("gnd_xx"); sb.append(';');
			sb.append("gnd_xy"); sb.append(';');
			sb.append("gnd_xz"); sb.append(';');
			
			sb.append("gnd_yx"); sb.append(';');
			sb.append("gnd_yy"); sb.append(';');
			sb.append("gnd_yz"); sb.append(';');
			          
			sb.append("gnd_zx"); sb.append(';');
			sb.append("gnd_zy"); sb.append(';');
			sb.append("gnd_zz");
			
			return sb.toString();
		}
		
		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			
			sb.append(String.format("%4s", Integer.toString(x))); sb.append(';');
			sb.append(String.format("%4s", Integer.toString(y))); sb.append(';');
			sb.append(String.format("%4s", Integer.toString(z))); sb.append(';');
			
//			sb.append(String.format("%.6f", center.x)); sb.append(';');
//			sb.append(String.format("%.6f", center.y)); sb.append(';');
//			sb.append(String.format("%.6f", center.z)); sb.append(';');
//			sb.append(String.format("%.6f", size.x));   sb.append(';');
//			sb.append(String.format("%.6f", size.y));   sb.append(';');
//			sb.append(String.format("%.6f", size.z));   sb.append(';');
//			sb.append(String.format("%.6f", volume));   sb.append(';');
			
			sb.append(String.format("%.8f", eSum 		  )); sb.append(';');
			sb.append(String.format("%.8f", eBulk 		  )); sb.append(';');
			sb.append(String.format("%.8f", eCore 		  )); sb.append(';');
			sb.append(String.format("%.8f", eStackingFault)); sb.append(';');
			sb.append(String.format("%.8f", eDefect 	  )); sb.append(';');
			sb.append(String.format("%.8f", eSurface 	  )); sb.append(';');
			sb.append(String.format("%.8f", eGrainBoundary)); sb.append(';');
			
			sb.append(String.format("%8s", Integer.toString(atoms 		      ))); sb.append(';');
			sb.append(String.format("%8s", Integer.toString(atomsBulk         ))); sb.append(';');
			sb.append(String.format("%8s", Integer.toString(atomsCore 		  ))); sb.append(';');
			sb.append(String.format("%8s", Integer.toString(atomsStackingFault))); sb.append(';');
			sb.append(String.format("%8s", Integer.toString(atomsDefect 	  ))); sb.append(';');
			sb.append(String.format("%8s", Integer.toString(atomsSurface      ))); sb.append(';');
			sb.append(String.format("%8s", Integer.toString(atomsGrainBoundary))); sb.append(';');
			
			sb.append(String.format("%.8f", lengthDisloactions )); sb.append(';');
			sb.append(String.format("%.8f", lengthTimesBsquared)); sb.append(';');
			
			double a = Configuration.getCurrentAtomData().getCrystalStructure().getPerfectBurgersVectorLength();
			
			sb.append(String.format("%.8f", gnd[0][0]/a)); sb.append(';');
			sb.append(String.format("%.8f", gnd[0][1]/a)); sb.append(';');
			sb.append(String.format("%.8f", gnd[0][2]/a)); sb.append(';');
			
			sb.append(String.format("%.8f", gnd[1][0]/a)); sb.append(';');
			sb.append(String.format("%.8f", gnd[1][1]/a)); sb.append(';');
			sb.append(String.format("%.8f", gnd[1][2]/a)); sb.append(';');
			
			sb.append(String.format("%.8f", gnd[2][0]/a)); sb.append(';');
			sb.append(String.format("%.8f", gnd[2][1]/a)); sb.append(';');
			sb.append(String.format("%.8f", gnd[2][2]/a));
			
			return sb.toString();
		}
	}

	@Override
	public boolean isApplicable(AtomData data) {
		return true;
	}


	@Override
	public String getRequirementDescription() {
		return "";
	}

	@Override
	public boolean canBeAppliedToMultipleFilesAtOnce() {
		return false;
	}

	@Override
	public boolean showConfigurationDialog(JFrame frame, AtomData data) {
		return true;
	}

	@Override
	public DataColumnInfo[] getDataColumnsInfo() {
		return null;
	}
	
}
