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

import gui.ColoringFilter;
import gui.PrimitiveProperty.BooleanProperty;
import gui.ViewerGLJPanel.AtomRenderType;

import java.io.IOException;
import java.util.ArrayList;

import javax.swing.JFrame;

import common.ColorTable;
import model.Atom;
import model.AtomData;
import model.RenderingConfiguration;
import model.ImportConfiguration;
import model.DataColumnInfo;
import model.Filter;
import model.NearestNeighborBuilder;
import processingModules.ClonableProcessingModule;
import processingModules.DataContainer;
import processingModules.JDataPanel;
import processingModules.ProcessingResult;
import processingModules.otherModules.ParticleDataContainer;
import processingModules.toolchain.Toolchain;

public class FeC_virtStructure extends FeCStructure {
	
	protected BooleanProperty skipPlaceholderProperty = 
			new BooleanProperty("skipPlaceholders", "Do not import placeholders","", true);
	protected BooleanProperty placeholderProperty = 
			new BooleanProperty("placeholders", "Handle placeholders separately","", true);
	
	
	public FeC_virtStructure() {
		super();
		this.getCrystalProperties().add(skipPlaceholderProperty);
		skipPlaceholderProperty.addDependentComponent(placeholderProperty, true);
		this.getCrystalProperties().add(placeholderProperty);
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
		if (a.getType() == 7 || a.getType() == 8) return false;
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
	public float[] getDefaultSphereSizeScalings(){
		return new float[]{1f, 0.43f, 0.43f};
	}
	
	@Override
	public int identifyAtomType(Atom atom, NearestNeighborBuilder<Atom> nnb) {
		if (atom.getElement()%3 == 2) return 8;	//Placeholder particles
		else return super.identifyAtomType(atom, nnb);
	}
	
	@Override
	public Toolchain getToolchainToApplyAtBeginningOfAnalysis() {
		Toolchain t = new Toolchain();
		if (placeholderProperty.getValue())
			t.addModule(new PlaceholderModule());			
		return t;
	}
	
	public Filter<Atom> getIgnoreAtomsDuringImportFilter(){
		if (skipPlaceholderProperty.getValue())
			return new Filter<Atom>() {
				@Override
				public boolean accept(Atom a) {
					return (a.getElement()%3 != 2);
				}
			};
		return null;
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
		private static PlaceholderColoringFilter colFunc;
		
		@Override
		protected String getLabelForControlPanel() {
			return "Placeholder";
		}
		
		protected ColoringFilter<Atom> getColoringFilter(){
			if(colFunc == null)
				colFunc = new PlaceholderColoringFilter();
			
			return colFunc;
		}
		
		private class PlaceholderColoringFilter implements ColoringFilter<Atom>{
			DataColumnInfo dataInfo;
			int selected;
			float min, max;
			boolean filterMin,filterMax,inversed, colorByValue;
			
			@Override
			public boolean accept(Atom a) {
				if (!colorByValue) return true;
				if (selected == -1) return false;
				return !(((filterMin && a.getData(selected)<min) || (filterMax && a.getData(selected)>max))^inversed);
			}
			
			@Override
			public void update() {
				colorByValue = RenderingConfiguration.getViewer().getAtomRenderType() == AtomRenderType.DATA;
				if (colorByValue){
					dataInfo = RenderingConfiguration.getSelectedColumn();
					selected = particleDataColumns.indexOf(dataInfo);
					min = dataInfo.getLowerLimit();
					max = dataInfo.getUpperLimit();
					filterMin = RenderingConfiguration.isFilterMin();
					filterMax = RenderingConfiguration.isFilterMax();
					inversed = RenderingConfiguration.isFilterInversed();
				}
				
			}
			
			@Override
			public float[] getColor(Atom c) {
				if (colorByValue) return ColorTable.getIntensityGLColor(min, max, c.getData(selected));
				else return getParticleDataControlPanel().getColor();
			}
		};
		
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
			updateRenderData(atomData);
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
