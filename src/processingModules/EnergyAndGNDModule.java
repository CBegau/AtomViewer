package processingModules;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;

import common.Vec3;
import model.*;
import model.BurgersVector.BurgersVectorType;
import model.dataContainer.dislocationDensity.CuboidVolumeElement;
import model.dataContainer.dislocationDensity.DislocationDensityTensor;
import model.polygrain.Grain;
import model.polygrain.mesh.Mesh;
import model.skeletonizer.Dislocation;
import model.skeletonizer.SkeletonNode;

public class EnergyAndGNDModule implements ProcessingModule {

	@Override
	public String getShortName() {
		return "Energy and GND analysis";
	}

	@Override
	public String getFunctionDescription() {
		return "";
	}

	@Override
	public String getRequirementDescription() {
		return "";
	}

	@Override
	public boolean isApplicable() {
		if (Configuration.getIndexForDataColumnName("Epot_av") != -1 ||
				Configuration.getIndexForDataColumnName("Epot") != -1)
			return true;
		return false;
	}

	@Override
	public DataColumnInfo[] getDataColumnsInfo() {
		DataColumnInfo cci = new DataColumnInfo("isInDislocation", "", "",true, 1);
		
		return new DataColumnInfo[]{cci};
	}

	@Override
	public void process(final AtomData data) throws Exception {
		int surfaceType = Configuration.getCrystalStructure().getSurfaceType();
				
		int e = Configuration.getIndexForDataColumnName("Epot_av");
		if (e==-1) e = Configuration.getIndexForDataColumnName("Epot");
		if (e==-1) return;
		
		int dislocationIndex = Configuration.getIndexForDataColumnName("isInDislocation");
		
		ArrayList<Atom> atoms = new ArrayList<Atom>();
		float zmax = -Float.MAX_VALUE;
		float zmin = Float.MAX_VALUE;
		
		Mesh m;
		
		int totalAtoms = data.getAtoms().size();	//Number of atoms in the sample
		double totalPotEnergy = 0.0;				//Summed potential energy of all atoms
		
		double measuredVolumeOfNotExcludedAtoms = 0.0;
		double energyOfNotExcludedAtoms = 0.0;
		int atomsInFilteredBySubgrains = 0;
		double energyOfFilteredSubgrains = 0.0;
		
		int atomsInDislocationCores = 0;
		double energyOfAtomsInDislocationCores = 0.0;
		
		int atomsInStackingFaults = 0;
		double energyOfAtomsInStackingFaults = 0.0;
		
		int atomsAtVacancies = 0;
		double energyOfAtomsAtVacancies = 0.0;
		
		int atomsInBulk = 0;
		double energyOfAtomsInBulk = 0.0;
		
		int atomsAtSurface = 0;
		double energyOfSurfaceAtoms = 0.0;
		
		double lengthOfDislocations = 0.0;
		double lTimesBurgerSquared = 0.0;
		
		double surfaceOfSubgrains = 0.0;
		double volumeOfSubgrains = 0.0;
		
		if (ImportStates.POLY_MATERIAL.isActive()){
			for (Atom a : data.getAtoms()){
				if (a.getType() != surfaceType && a.getGrain()==0){
					atoms.add(a);
					if (a.z<zmin) zmin = a.z;
					if (a.z>zmax) zmax = a.z;
				}
			}
			// Get the largest grain
			m = data.getGrains(0).getMesh();
		} else {
			for (Atom a : data.getAtoms()){
				if (a.getType() != surfaceType){
					atoms.add(a);
					if (a.z<zmin) zmin = a.z;
					if (a.z>zmax) zmax = a.z;
				}
			}
			m = new Mesh(atoms, Configuration.getCrystalStructure());
			m.call();
			m.finalizeMesh();
		}
		
		CuboidVolumeElement cve = new CuboidVolumeElement(
				new Vec3(data.getBox().getHeight().x, data.getBox().getHeight().y, zmax),
				new Vec3(0,0,zmin));
		DislocationDensityTensor ddt = new DislocationDensityTensor(data.getSkeletonizer() ,cve);
		
		measuredVolumeOfNotExcludedAtoms = m.getVolume();
		
		//Mark atoms in dislocations
		Iterator<Dislocation> disIter = data.getSkeletonizer().getDislocations().iterator();
		while(disIter.hasNext()){
			Dislocation d = disIter.next();
			if ( d.getBurgersVectorInfo().getAverageResultantBurgersVector().getLength()<0.2f
					&& d.getEndNode().getJoiningDislocations().size() == 1
					&& d.getStartNode().getJoiningDislocations().size() == 1){
				disIter.remove();	//Delete dislocations that are supposedly artifacts
			} else if (d.getBurgersVectorInfo().getBurgersVector().getType() != BurgersVectorType.ZERO){
				//Mark atoms in proper dislocations
				for (SkeletonNode n : d.getLine()){
					for (Atom a : n.getMappedAtoms()){
						a.setData(1f, dislocationIndex);
					}
				}
			}
		}
		
		//Sum up energy in full system
		for (Atom a : data.getAtoms()){
			totalPotEnergy += a.getData(e); //<-Everything
			
			if (a.getType() == surfaceType){
				atomsAtSurface++;
				energyOfSurfaceAtoms+=a.getData(e); //<-Surface
			}
			
			if (ImportStates.POLY_MATERIAL.isActive()){
				if (a.getType() != surfaceType && a.getGrain()!=0){
					atomsInFilteredBySubgrains++;
					energyOfFilteredSubgrains += a.getData(e); //<-Grains
				}
			}
		}
		
		if (ImportStates.POLY_MATERIAL.isActive()){
			for (Grain g : data.getGrains()){
				if (g.getGrainNumber()!=0){
					surfaceOfSubgrains += g.getMesh().getArea();
					volumeOfSubgrains += g.getMesh().getVolume();
				}
			}
		}
		
		//Count atoms in different sets and sum up their energies
		for (Atom a : atoms){
			energyOfNotExcludedAtoms += a.getData(e);
			
			if ( a.getType() == 1){
				atomsInBulk++;
				energyOfAtomsInBulk += a.getData(e);
			} else if (a.getType() == 2 || a.getType() == 3){
				atomsInStackingFaults++;
				energyOfAtomsInStackingFaults += a.getData(e);
			} else if (a.getData(dislocationIndex) == 0){
				atomsAtVacancies++;
				energyOfAtomsAtVacancies += a.getData(e);
			} else {
				atomsInDislocationCores++;
				energyOfAtomsInDislocationCores += a.getData(e);
			}
		}
		
		for (Dislocation d: data.getSkeletonizer().getDislocations()){
			BurgersVector bv;
			Vec3 bvLocal;
			if (d.getBurgersVectorInfo()!=null && d.getBurgersVectorInfo().getBurgersVector().isFullyDefined()){
				bv = d.getBurgersVectorInfo().getBurgersVector();
				bvLocal = bv.getInXYZ(Configuration.getCrystalRotationTools());
			} else {
				bv = Configuration.getCrystalRotationTools().rbvToBurgersVector(d.getBurgersVectorInfo().getAverageResultantBurgersVector());
				bvLocal = bv.getInXYZ(Configuration.getCrystalRotationTools());
				//Consider the pure values are always too small
				bvLocal.multiply(1.5f);
			}
			
			if (bv.getFraction() == 0) continue;
			// Sum up length of dislocations
			lengthOfDislocations += d.getLength();
			
			for (int i=0; i<d.getLine().length-1; i++){
				if (cve.isInVolume(d.getLine()[i])){
					Vec3 dir = Configuration.pbcCorrectedDirection(d.getLine()[i], d.getLine()[i+1]);
					
					lTimesBurgerSquared += dir.getLength() * bvLocal.getLengthSqr();
				}
			}
		}
		
		
		StringBuilder sb = new StringBuilder();
		
		sb.append("Values for the full sample\n");
		sb.append("\n");
		sb.append("#Atoms:\t" + totalAtoms +"\n");
		sb.append("Total EPot:\t" + totalPotEnergy +"\n");
		
		sb.append("#Atoms marked as surface:\t" + atomsAtSurface +"\n");
		sb.append("EPot of atoms marked as surface:\t" + energyOfSurfaceAtoms +"\n");
		
		sb.append("#Atoms filtered by subgrains:\t" + atomsInFilteredBySubgrains +"\n");
		sb.append("EPot filtered by subgrains:\t" + energyOfFilteredSubgrains +"\n");
		
		sb.append("Volume of subgrains (A³):\t" + volumeOfSubgrains +"\n");
		sb.append("Surface area of subgrains (A²):\t" + surfaceOfSubgrains +"\n");
		
		sb.append("\n");
		sb.append("\n");
		
		
		sb.append("Results of sample without surface and grains\n");
		sb.append("\n");
		sb.append("Measured volume (A³):\t" + measuredVolumeOfNotExcludedAtoms +"\n");
		sb.append("#Atoms:\t" + atoms.size() +"\n");
		sb.append("EPot total (eV):\t" + energyOfNotExcludedAtoms +"\n");
		
		sb.append("#Atoms in bulk:\t" + atomsInBulk +"\n");
		sb.append("EPot in bulk (eV):\t" + energyOfAtomsInBulk +"\n");
		
		sb.append("#Atoms in dislocation cores:\t" + atomsInDislocationCores +"\n");
		sb.append("EPot in dislocation cores (eV):\t" + energyOfAtomsInDislocationCores +"\n");
		
		sb.append("#Atoms in stacking faults:\t" + atomsInStackingFaults +"\n");
		sb.append("EPot in stacking faults (eV):\t" + energyOfAtomsInStackingFaults +"\n");
		
		sb.append("#Atoms at vacancies:\t" + atomsAtVacancies +"\n");
		sb.append("EPot at vacancies (eV):\t" + energyOfAtomsAtVacancies +"\n");
		
		sb.append("\n");
		sb.append("\n");
		
		sb.append("Results on dislocations\n");
		sb.append("\n");
		
		sb.append("Dislocation length (A)\t"+lengthOfDislocations +"\n");
		sb.append("Dislocation length time bv² (A³)\t"+lTimesBurgerSquared +"\n");
		
		
		double a = ddt.getVolumeElement().getVolume()/m.getVolume();
		sb.append("GND tensor (1/m²)\n");
		sb.append(ddt.getDensityTensor()[0][0]*a+"\t"+ddt.getDensityTensor()[0][1]*a+"\t"+ddt.getDensityTensor()[0][2]*a +"\n");
		sb.append(ddt.getDensityTensor()[1][0]*a+"\t"+ddt.getDensityTensor()[1][1]*a+"\t"+ddt.getDensityTensor()[1][2]*a +"\n");
		sb.append(ddt.getDensityTensor()[2][0]*a+"\t"+ddt.getDensityTensor()[2][1]*a+"\t"+ddt.getDensityTensor()[2][2]*a +"\n");
		
		sb.append("Summed GND tensor (1/m²)\t" + ddt.getDensity()*a +"\n");
		
		System.out.println(sb.toString());
		
		try{
			File f = new File(Configuration.getLastOpenedFolder(),"GNDdata_"+data.getName()+".csv");
			PrintWriter pw = new PrintWriter(f);
			pw.println(sb.toString());
			pw.close();
		} catch (IOException ex){
			ex.printStackTrace();
		}
	}
}
