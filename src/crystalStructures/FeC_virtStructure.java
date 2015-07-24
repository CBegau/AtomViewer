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

import gui.RenderRange;
import gui.ViewerGLJPanel;
import gui.PrimitiveProperty.BooleanProperty;
import gui.ViewerGLJPanel.AtomRenderType;
import gui.glUtils.ObjectRenderData;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.media.opengl.GL3;
import javax.swing.JFrame;

import common.ColorTable;
import common.Tupel;
import common.Vec3;
import model.Atom;
import model.AtomData;
import model.BoxParameter;
import model.RenderingConfiguration;
import model.ImportConfiguration;
import model.DataColumnInfo;
import model.NearestNeighborBuilder;
import processingModules.ClonableProcessingModule;
import processingModules.DataContainer;
import processingModules.JDataPanel;
import processingModules.ProcessingModule;
import processingModules.ProcessingResult;
import processingModules.otherModules.ParticleDataContainer;

public class FeC_virtStructure extends BCCStructure {
	
	protected BooleanProperty placeholderProperty = 
			new BooleanProperty("placeholders", "handle Placeholders separately","", true);
	
	
	public FeC_virtStructure() {
		super();
		this.getCrystalProperties().add(placeholderProperty);
		this.getCrystalProperties().remove(super.highTempProperty);
	}

	@Override
	protected CrystalStructure deriveNewInstance() {
		return new FeC_virtStructure();
	}
	
	@Override
	protected String getIDName() {
		return "FeC_virtStructure";
	}
	
	@Override
	public String getNameForType(int i) {
		switch (i) {
			case 0: return "bcc";
			case 1: return "unused";
			case 2: return "unused";
			case 3: return "14 neighbors";
			case 4: return "11-13 neighbors";
			case 5: return ">14 neighbors";
			case 6: return "<11 neighbors";
			case 7: return "Carbon";
			case 8: return "Placeholder";
			default: return "unknown";
		}
	}

	@Override
	/**
	 * Ignore carbon and placeholders
	 */
	public boolean considerAtomAsNeighborDuringRBVCalculation(Atom a){
		if (a.getType() == 7) return false;
		return true;
	}
	
	@Override
	public int getNumberOfElements(){
		return 3;
	}
	
	@Override
	public String[] getNamesOfElements(){
		return new String[]{"Fe", "C", "-"};
	}

	@Override
	public int getNumberOfTypes() {
		if (placeholderProperty==null) return 9;
		return placeholderProperty.getValue() ? 8 : 9;
	}

	@Override
	public float[] getSphereSizeScalings(){
		float[] size = new float[getNumberOfElements()];
		size[0] = 1f;
		size[1] = 0.43f;
		size[2] = 0.43f;
		return size;
	}
	
	@Override
	public int identifyAtomType(Atom atom, NearestNeighborBuilder<Atom> nnb) {
		ArrayList<Tupel<Atom, Vec3>> neigh = nnb.getNeighAndNeighVec(atom);		
		/*
		 * type=0: bcc
		 * type=1: unused
		 * type=2: unused
		 * type=3: 14 neighbors
		 * type=4: 11-13 neighbors
		 * type=5: >14 neighbors
		 * type=6: less than 11 neighbors
		 * type=7: carbon
		 */
		
		//carbon
		if (atom.getElement()%3 == 1) return 7;
		//placeholder
		else if (atom.getElement()%3 == 2) return 8;
		
		//count Fe neighbors for Fe atoms
		int count = 0;
		for (int i=0; i < neigh.size(); i++)
			if (neigh.get(i).getO1().getElement() % 3 == 0) count++;		
		
		if (count < 11) return 6;
		else if (count == 11) return 4;
		else if (count >= 15) return 5;
		else {
			int co_x0 = 0;
			int co_x1 = 0;
//			int co_x2 = 0;
			for (int i = 0; i < neigh.size(); i++) {
				if (neigh.get(i).getO1().getElement() % 3 != 0) continue; //ignore carbon atoms
				Vec3 v = neigh.get(i).getO2();
				
				float v_length = v.getLength();
				
				for (int j = 0; j < i; j++) {
					if (neigh.get(j).getO1().getElement() % 3 != 0) continue; //ignore carbon atoms
					Vec3 u = neigh.get(j).getO2();
					float u_length = u.getLength();
					float a = v.dot(u) / (v_length*u_length);
					
					if (a < -.945)
						co_x0++;
					else if (a < -.915)
						co_x1++;
//					else if (a > -.75 && a< -0.67)
//						co_x2++;
				}
			}
			
//			if (co_x0 > 5 && co_x0+co_x1==7 && co_x2<=2 && count==14) return 0;
			if (co_x0+co_x1==7 && count==14) return 0;
			else if (count==13 && neigh.size()==14 && co_x0==6) return 0;
			else if (count == 13) return 4;
			else if (count == 12) return 4;
			else return 3;
		}
	}
	
	@Override
	public boolean isRBVToBeCalculated(Atom a) {
		int type = a.getType();		
		if (type != 0 && type < 6) {			
			return true;
		}
		return false;		
	}
	
	@Override
	public List<ProcessingModule> getProcessingModuleToApplyAtBeginningOfAnalysis() {
		ArrayList<ProcessingModule> pm = new ArrayList<ProcessingModule>();
		if (placeholderProperty.getValue())
			pm.add(new PlaceholderModule());
		return pm;
	}
	
	private static final class PlaceholderModule extends ClonableProcessingModule{
		@Override
		public String getFunctionDescription() {
			return "Extract placeholder";
		}

		@Override
		public String getShortName() {
			return "Placeholder filter";
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
		public boolean showConfigurationDialog(JFrame frame, AtomData data) {
			return true;
		}

		@Override
		public boolean canBeAppliedToMultipleFilesAtOnce() {
			return true;
		}
		
		@Override
		public DataColumnInfo[] getDataColumnsInfo() {
			return null;
		}
		
		@Override
		public ProcessingResult process(AtomData data) throws Exception {
			PlaceholderDataContainer dc = new PlaceholderDataContainer();
			dc.processData(data);
			return new DataContainer.DefaultDataContainerProcessingResult(dc, "");
		}
	}
	
	private static final class PlaceholderDataContainer extends ParticleDataContainer<Atom>{
		private static JParticleDataControlPanel<Atom> dataPanel = null;
		
		@Override
		protected String getLabelForControlPanel() {
			return "Placeholder";
		}

		@Override
		public void drawTransparentObjects(ViewerGLJPanel viewer, GL3 gl, RenderRange renderRange, boolean picking, BoxParameter box) {
			return;
		}

		@Override
		public void drawSolidObjects(ViewerGLJPanel viewer, GL3 gl, RenderRange renderRange, boolean picking, BoxParameter box) {
			if (!dataPanel.isDataVisible()) return;
			
			if (viewer.isUpdateRenderContent()){
				float particleSize = getParticleDataControlPanel().getParticleSize();
				
				if (viewer.getAtomRenderType() == AtomRenderType.DATA){
					DataColumnInfo dataInfo = RenderingConfiguration.getSelectedColumn();
					int selected = particleDataColumns.indexOf(dataInfo);
					if (selected == -1){
						for (ObjectRenderData<Atom>.Cell cell : ord.getRenderableCells()){
							for (int i = 0; i < cell.getNumObjects(); i++) {
								 cell.getVisibiltyArray()[i] = false;
							}
						}
					} else {
						float min = dataInfo.getLowerLimit();
						float max = dataInfo.getUpperLimit();
						boolean filterMin = RenderingConfiguration.isFilterMin();
						boolean filterMax = RenderingConfiguration.isFilterMax();
						boolean inversed = RenderingConfiguration.isFilterInversed();
						
						//Set custom color scheme of the data value if present
						for (ObjectRenderData<Atom>.Cell cell : ord.getRenderableCells()){
							for (int i = 0; i < cell.getNumObjects(); i++) {
								Atom c = cell.getObjects().get(i);
								if (renderRange.accept(c) && 
									!(((filterMin && c.getData(selected)<min) || (filterMax && c.getData(selected)>max))^inversed)) {
									 cell.getVisibiltyArray()[i] = true;
									 cell.getSizeArray()[i] = particleSize;
									 float[] col = ColorTable.getIntensityGLColor(min, max, c.getData(selected));
									 cell.getColorArray()[3*i+0] = col[0];
									 cell.getColorArray()[3*i+1] = col[1];
									 cell.getColorArray()[3*i+2] = col[2];
								 } else {
									 cell.getVisibiltyArray()[i] = false;
								 }
							}
						}
					}
				} else {
					float[] col = getParticleDataControlPanel().getColor();
					for (ObjectRenderData<Atom>.Cell cell : ord.getRenderableCells()){
						for (int i = 0; i < cell.getNumObjects(); i++) {
							Atom c = cell.getObjects().get(i);
							if (renderRange.accept(c)) {
								 cell.getVisibiltyArray()[i] = true;
								 cell.getSizeArray()[i] = particleSize;
								 cell.getColorArray()[3*i+0] = col[0];
								 cell.getColorArray()[3*i+1] = col[1];
								 cell.getColorArray()[3*i+2] = col[2];
							 } else {
								 cell.getVisibiltyArray()[i] = false;
							 }
						}
					}	
				}
				ord.reinitUpdatedCells();
			}
			
			viewer.drawSpheres(gl, ord, picking);
		}
		
		public boolean processData(AtomData atomData) throws IOException {
			ArrayList<Atom> realAtoms = new ArrayList<Atom>();
			
			for (Atom a : atomData.getAtoms()){
				if (a.getElement()%3 == 2){
					particles.add(a);
					a.setType(8);
				}
				else realAtoms.add(a);
			}
			particleDataColumns.addAll(ImportConfiguration.getInstance().getDataColumns());
			
			atomData.getAtoms().clear();
			atomData.getAtoms().addAll(realAtoms);
			updateRenderData(atomData.getBox());
			return true;
		}

		@Override
		protected JParticleDataControlPanel<Atom> getParticleDataControlPanel() {
			if (dataPanel == null){
				dataPanel = new JParticleDataControlPanel<Atom>(this, new float[]{0.1f,0.95f,0.3f}, 0.5f);
			}
			return dataPanel;
		}
		
		@Override
		public JDataPanel getDataControlPanel() {
			return getParticleDataControlPanel();
		}
	}
}
