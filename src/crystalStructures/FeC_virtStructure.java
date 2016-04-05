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

import java.awt.event.InputEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import javax.swing.JFrame;

import common.ColorTable;
import common.CommonUtils;
import common.FastTFloatArrayList;
import common.Tupel;
import common.Vec3;
import model.Atom;
import model.AtomData;
import model.RenderingConfiguration;
import model.DataColumnInfo;
import model.Filter;
import model.NearestNeighborBuilder;
import model.Pickable;
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
	
	private static final class PlaceholderDataContainer extends ParticleDataContainer<PlaceholderDataContainer.Placeholder>{
		private static JParticleDataControlPanel<Placeholder> dataPanel = null;
		private static PlaceholderColoringFilter colFunc;
		
		@Override
		protected String getLabelForControlPanel() {
			return "Placeholder";
		}
		
		protected ColoringFilter<Placeholder> getColoringFilter(){
			if(colFunc == null)
				colFunc = new PlaceholderColoringFilter();
			
			return colFunc;
		}
		
		private class PlaceholderColoringFilter implements ColoringFilter<Placeholder>{
			DataColumnInfo dataInfo;
			int selected;
			float min, max;
			boolean filterMin,filterMax,inversed, colorByValue;
			
			@Override
			public boolean accept(Placeholder p) {
				if (!colorByValue) return true;
				if (selected == -1) return false;
				float value = particleData.get(selected).get(p.getID());
				return !(( (filterMin && value<min) || (filterMax && value>max) )^inversed);
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
			public float[] getColor(Placeholder c) {
				if (colorByValue) 
					return ColorTable.getIntensityGLColor(min, max, particleData.get(selected).get(c.getID()));
				else return getParticleDataControlPanel().getColor();
			}
		};
		
		public boolean processData(AtomData atomData) throws IOException {
			particleDataColumns.addAll(atomData.getDataColumnInfos());
			for (int i=0; i<atomData.getDataColumnInfos().size(); i++)
				particleData.add(new FastTFloatArrayList());
			
			int index = 0;
			for (int i=0; i<atomData.getAtoms().size(); i++){
				Atom a = atomData.getAtoms().get(i);
				if (a.getElement()%3 == 2){
					particles.add(new Placeholder(a, index++));
					
					for (int j=0; j<particleData.size(); j++)
						particleData.get(j).add(atomData.getDataValueArray(j).get(i));
				}
			}
			particleDataColumns.addAll(atomData.getDataColumnInfos());
			
			//Delete all placeholders from the 
			atomData.removeAtoms(new Filter<Atom>(){
				@Override
				public boolean accept(Atom a) {
					return (a.getElement()%3 != 2);
				}
			});
			
			updateRenderData(atomData);
			return true;
		}

		@Override
		protected JParticleDataControlPanel<Placeholder> getParticleDataControlPanel() {
			if (dataPanel == null){
				dataPanel = new JParticleDataControlPanel<Placeholder>(this, new float[]{0.1f,0.95f,0.3f}, 0.5f);
			}
			return dataPanel;
		}
		
		@Override
		public JDataPanel getDataControlPanel() {
			return getParticleDataControlPanel();
		}
		
		class Placeholder extends Vec3 implements Pickable{
			private int ID;
			private int number;
			private byte element;
			
			public Placeholder(Atom a, int ID) {
				super(a.x,a.y, a.z);
				this.ID = ID;
				this.number = a.getNumber();
				this.element = (byte)a.getElement();
			}
			
			public int getID() {
				return ID;
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
			public Tupel<String,String> printMessage(InputEvent ev, AtomData data) {			
				ArrayList<String> keys = new ArrayList<String>();
				ArrayList<String> values = new ArrayList<String>();
				
				Vec3 offset = data.getBox().getOffset();
				keys.add("Nr"); values.add(Integer.toString(number));
				keys.add("Position"); values.add(this.addClone(offset).toString());
				
				keys.add("element");
				if (data.getNameOfElement(element).isEmpty())
					values.add(Integer.toString(element));
				else values.add(Integer.toString(element)+" "+data.getNameOfElement(element));
				
				for (DataColumnInfo c : particleDataColumns){
					if (!c.isVectorComponent()){
						int index1 = getIndexForCustomColumn(c);
						keys.add(c.getName());
						values.add(CommonUtils.outputDecimalFormatter.format(particleData.get(index1).get(ID))+c.getUnit());
					} else if (c.isFirstVectorComponent()){
						keys.add(c.getVectorName()+(!c.getUnit().isEmpty()?"("+c.getUnit()+")":""));
						int index1 = getIndexForCustomColumn(c.getVectorComponents()[0]);
						int index2 = getIndexForCustomColumn(c.getVectorComponents()[1]);
						int index3 = getIndexForCustomColumn(c.getVectorComponents()[2]);
						Vec3 vec = new Vec3(
								particleData.get(index1).get(ID),
								particleData.get(index2).get(ID),
								particleData.get(index3).get(ID));
						values.add(vec.toString());
						keys.add("Magnitude of "+c.getVectorName()+(!c.getUnit().isEmpty()?"("+c.getUnit()+")":""));
						values.add(Float.toString(vec.getLength()));
					}
				}
				
				return new Tupel<String, String>("Placeholder "+number, 
						CommonUtils.buildHTMLTableForKeyValue(
								keys.toArray(new String[keys.size()]), values.toArray(new String[values.size()])));
			}
			
			@Override
			public boolean equals(Object obj) {
				return this == obj;
			}
			
			@Override
			public Vec3 getCenterOfObject() {
				return this.clone();
			}
		}
	}
}
