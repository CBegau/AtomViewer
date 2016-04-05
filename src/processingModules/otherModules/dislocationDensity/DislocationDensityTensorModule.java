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

package processingModules.otherModules.dislocationDensity;

import gui.JLogPanel;
import gui.JPrimitiveVariablesPropertiesDialog;
import gui.RenderRange;
import gui.ViewerGLJPanel;
import gui.PrimitiveProperty.*;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Arrays;

import com.jogamp.opengl.GL3;
import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;

import processingModules.ClonableProcessingModule;
import processingModules.DataContainer;
import processingModules.JDataPanel;
import processingModules.ProcessingResult;
import processingModules.atomicModules.LatticeRotationModule;
import processingModules.skeletonizer.Skeletonizer;
import processingModules.toolchain.Toolchainable.ExportableValue;
import processingModules.toolchain.Toolchainable.ToolchainSupport;
import common.ColorTable;
import common.Vec3;
import model.*;

@ToolchainSupport
public class DislocationDensityTensorModule extends ClonableProcessingModule {
	private static JDislocationDensityTensorControls dataPanel = null;
	
	@ExportableValue
	private int gridX;
	@ExportableValue
	private int gridY;
	@ExportableValue
	private int gridZ;
	
	@Override
	public ProcessingResult process(AtomData data) throws Exception {
		DDTDataContainer dc = new DDTDataContainer();
		dc.processData(data);
		return new DataContainer.DefaultDataContainerProcessingResult(dc, "");
	}

	@Override
	public boolean showConfigurationDialog(JFrame frame, AtomData data) {
		JPrimitiveVariablesPropertiesDialog dialog = new JPrimitiveVariablesPropertiesDialog(null, getShortName());
		dialog.addLabel(getFunctionDescription());
		
		IntegerProperty sliceX = dialog.addInteger("sliceX", "Slices in X-direction", "", 10, 1, 10000000);
		IntegerProperty sliceY = dialog.addInteger("sliceY", "Slices in Y-direction", "", 10, 1, 10000000);
		IntegerProperty sliceZ = dialog.addInteger("sliceZ", "Slices in Z-direction", "", 10, 1, 10000000);
		
		boolean ok = dialog.showDialog();
		if (ok){
			this.gridX = sliceX.getValue();
			this.gridY = sliceY.getValue();
			this.gridZ = sliceZ.getValue();
		}
		return ok;
	}

	@Override
	public String getFunctionDescription() {
		return "Computes the local dislocation density in blocks of the total domain";
	}

	@Override
	public String getShortName() {
		return "Local dislocation densities";
	}
	
	@Override
	public boolean isApplicable(AtomData data) {
		boolean hasLatticeRotation = data.getDataColumnInfos().containsAll(
						Arrays.asList(new LatticeRotationModule().getDataColumnsInfo()));
		boolean hasSkeleton = (data.getDataContainer(Skeletonizer.class)!=null);
		
		return hasLatticeRotation || hasSkeleton;
	}

	@Override
	public String getRequirementDescription() {
		return "Either the dislocation network or lattice rotations (for single crystals) are needed";
	}
	
	@Override
	public boolean canBeAppliedToMultipleFilesAtOnce() {
		return true;
	}
	
	@Override
	public DataColumnInfo[] getDataColumnsInfo() {
		return null;
	}
	
	private class DDTDataContainer extends DataContainer {
		private DislocationDensityTensor[][][] ddtFromLatt;
		private DislocationDensityTensor[][][] ddtFromDis;
		private int[] gridSize;
		
		@Override
		public void drawSolidObjects(ViewerGLJPanel viewer, GL3 gl, RenderRange renderRange, boolean picking, AtomData data) {
			return;
		}
		
		@Override
		public void drawTransparentObjects(ViewerGLJPanel viewer, GL3 gl, RenderRange renderRange, boolean picking, AtomData data) {
			if (!getDataControlPanel().isDataVisible()) return;
			
			DislocationDensityTensor[][][] ddt;
			if (dataPanel.lattOrDis) ddt = this.ddtFromDis;
			else ddt = this.ddtFromLatt;
			
			if (ddt==null) return;
			
			float minPower = 14.5f;
			float maxPower = 17f;
			
			for (int i=0; i<ddt.length; i++){
				for (int j=0; j<ddt[i].length; j++){
					for (int k=0; k<ddt[i][j].length; k++){
						if (dataPanel.ddtPlane == JDislocationDensityTensorControls.DDTPlane.X_PLANE){
							if (i<dataPanel.renderStartSlice || i>dataPanel.renderEndSlice) continue;
						} else if (dataPanel.ddtPlane == JDislocationDensityTensorControls.DDTPlane.Y_PLANE){
							if (j<dataPanel.renderStartSlice || j>dataPanel.renderEndSlice) continue;
						} else {
							if (k<dataPanel.renderStartSlice || k>dataPanel.renderEndSlice) continue;
						}
						
						float c1;
						
						if (dataPanel.ddtCompX==-1){
							c1 = (float)Math.log10(Math.abs(ddt[i][j][k].getDensity()));
							maxPower = 18f;
							minPower = 15f;
						} else if (dataPanel.ddtCompX==-2){
							c1 = (float)Math.log10(Math.abs(ddt[i][j][k].getScalarDensity()));
							maxPower = 18f;
							minPower = 15f;
						} else {
							c1 = (float)Math.log10(Math.abs(ddt[i][j][k].getDensityTensor()[dataPanel.ddtCompX][dataPanel.ddtCompY]));
							maxPower = 17f;
							minPower = 14.5f;
						}
						
						VolumeElement ve = ddt[i][j][k].getVolumeElement();
						float[] f = ColorTable.getIntensityGLColor(minPower, maxPower, c1, 0.20f);
						
						if (c1>0) {
							if (picking)
								f = viewer.getNextPickingColor(ddt[i][j][k]);
							ve.render(gl, picking, f);
						}
					}
				}
			}
			
//			//Plastic zone rendering
//			if (ddtd.plasticZone != null){
//				float c1;
//				if (ddtCompX==-1){
//					c1 = (float)Math.log10(Math.abs(ddtd.plasticZone.getDensity()));
//					maxPower = 18f;
//					minPower = 15f;
//				} else if (ddtCompX==-2){
//					c1 = (float)Math.log10(Math.abs(ddtd.plasticZone.getScalarDensity()));
//					maxPower = 18f;
//					minPower = 15f;
//				} else {
//					c1 = (float)Math.log10(Math.abs(ddtd.plasticZone.getDensityTensor()[ddtCompX][ddtCompY]));
//					maxPower = 17f;
//					minPower = 14.5f;
//				}
//				
//				VolumeElement ve = ddtd.plasticZone.getVolumeElement();
//				float[] f = ColorTable.getIntensityGLColor(minPower, maxPower, c1, 0.1f);		
//				ve.render(gl, picking, f);
//			}
			
			viewer.drawLegendThisFrame("10^"+Float.toString(minPower)+"/m²", "",  "10^"+Float.toString(maxPower)+"/m²");
		}
		
		@Override
		public boolean isTransparenceRenderingRequired() {
			return true;
		}
		
		
		public boolean processData(AtomData atomData) throws IOException {
			gridSize = new int[]{gridX, gridY, gridZ};
			DataColumnInfo[] dci = new LatticeRotationModule().getDataColumnsInfo();
			boolean hasLatticeRotation = true;
			for (DataColumnInfo d : dci)
				if (atomData.getDataColumnIndex(d) == -1)
					hasLatticeRotation = false;
			
			boolean hasSkeleton = (atomData.getDataContainer(Skeletonizer.class)!=null);
			
			if (!hasSkeleton && !hasLatticeRotation)
				return false;
			
			if (hasSkeleton){
				ddtFromDis = calcDislocationDensityTensors(atomData);
				
//				if (data.getFileMetaData("extpot") != null && data.getFileMetaData("plasticzone") != null){
//					float[] indent = (float[]) data.getFileMetaData("extpot");
//					float[] pz = (float[]) data.getFileMetaData("plasticzone");
//					
//					plasticZone = new DislocationDensityTensor(data.getSkeletonizer(), 
//							new HalfShellSector(pz[3], new Vec3(indent[0],indent[1],indent[2]),
//									indent[3], pz[4], new Vec3(pz[0],pz[1],pz[2])));
//				}
			}
			if (hasLatticeRotation){
				CuboidSectorDensityTensorBuilder csdtb = 
						new CuboidSectorDensityTensorBuilder(atomData, gridSize[0], gridSize[1], gridSize[2]);
				ddtFromLatt = csdtb.createCuboids();
			}
			
			return true;
		}
		
		@Override
		public JDataPanel getDataControlPanel() {
			if (dataPanel == null)
				dataPanel = new JDislocationDensityTensorControls(this);
			return dataPanel;
		}
		
		public DislocationDensityTensor[][][] calcDislocationDensityTensors(AtomData data){
			DataContainer dc = data.getDataContainer(Skeletonizer.class);
			Skeletonizer skel = null;
			if (dc != null)
				skel = (Skeletonizer)dc;
			else return null;
			
			CuboidVolumeElement[][][] ve = new CuboidVolumeElement[gridSize[0]][gridSize[1]][gridSize[2]];
			
			if (!data.getBox().isOrtho())
				JLogPanel.getJLogPanel().addWarning("Inaccurate dislocation densities",
						"The simulation box is non-orthogonal. The computed dislocation densities are inaccurate");
			
			for (int i=0; i<gridSize[0]; i++){
				for (int j=0;j<gridSize[1]; j++){
					for (int k=0;k<gridSize[2]; k++){
						Vec3 low = new Vec3();
						Vec3 high = new Vec3();
						low.x = (data.getBox().getHeight().x/gridSize[0])*i;
						low.y = (data.getBox().getHeight().y/gridSize[1])*j;
						low.z = (data.getBox().getHeight().z/gridSize[2])*k;
						
						high.x = (data.getBox().getHeight().x/gridSize[0])*(i+1);
						high.y = (data.getBox().getHeight().y/gridSize[1])*(j+1);
						high.z = (data.getBox().getHeight().z/gridSize[2])*(k+1);
						
						ve[i][j][k] = new CuboidVolumeElement(high, low);	
					}
					
				}
			}
			
			DislocationDensityTensor[][][] ddt = new DislocationDensityTensor[gridSize[0]][gridSize[1]][gridSize[2]];
					
			for (int i=0; i<gridSize[0]; i++){
				for (int j=0;j<gridSize[1]; j++){
					for (int k=0;k<gridSize[2]; k++){
						DislocationDensityTensor d = new DislocationDensityTensor(skel, ve[i][j][k]); 
						ddt[i][j][k] = d;
					}
				}
			}
			
			return ddt;
		}
	}
	
	
	public static class JDislocationDensityTensorControls extends JDataPanel {
		private enum DDTPlane { X_PLANE, Y_PLANE, Z_PLANE};
		private static final long serialVersionUID = 1L;
		
		public JCheckBox enableCheckBox = new JCheckBox("Show DDT");
		private JComboBox axisComboBox = new JComboBox(new String[]{"X-Axis","Y-Axis","Z-Axis"});
		private JRadioButton[][] tensorComp = new JRadioButton[3][3];
		private JRadioButton tensorSum;
		private JRadioButton scalarDens;
		
		private JFormattedTextField lowerLimitTextBox = new JFormattedTextField(new DecimalFormat());
		private JFormattedTextField upperLimitTextBox = new JFormattedTextField(new DecimalFormat());
		
		private JRadioButton latticeDDTComboBox = new JRadioButton("DDT from lattice");
		private JRadioButton dislocationNetworkDDTComboBox = new JRadioButton("DDT from dislocation network");
		
		private ViewerGLJPanel viewer;
		
		private boolean lattOrDis = false;
		private DDTPlane ddtPlane = DDTPlane.X_PLANE;
		private int ddtCompX = 0, ddtCompY = 0;
		private int renderStartSlice, renderEndSlice;
		
		private DDTDataContainer dc;
		
		private JDislocationDensityTensorControls(DDTDataContainer dc) {
			this.dc = dc;
			this.setBorder(new TitledBorder(new EtchedBorder(1), "DDT"));
			this.setLayout(new GridBagLayout());
			GridBagConstraints gbc = new GridBagConstraints();
			
			ActionListener al = new ActionListener(){
				@Override
				public void actionPerformed(ActionEvent arg0) {
					int i = Integer.parseInt(arg0.getActionCommand());
					if (i < 0){
						ddtCompX = i;
						ddtCompY = 0;
					} else {
						ddtCompX = i/3;
						ddtCompY = i%3;
					}
					viewer.reDraw();
				}
			};
			
			enableCheckBox.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					viewer.reDraw();
				}
			});
			
			gbc.fill = GridBagConstraints.BOTH;
			gbc.gridx = 0; gbc.gridy = 0;
			gbc.gridwidth = 3;
			this.add(enableCheckBox, gbc);
			
			gbc.gridy++; gbc.gridwidth = 1;
			
			gbc.weightx = 0.33;
			ButtonGroup bg = new ButtonGroup();
			for (int i=0; i<3; i++){
				String s1;
				if (i==0) s1="x";
				else if (i==1) s1="y";
				else s1="z";
				for (int j=0; j<3; j++){
					String s2;
					if (j==0) s2="x";
					else if (j==1) s2="y";
					else s2="z";
					
					tensorComp[i][j] = new JRadioButton(String.format("%s,%s", s1, s2));
					tensorComp[i][j].setActionCommand(Integer.toString(3*i+j));
					tensorComp[i][j].addActionListener(al);
					gbc.gridx = j; gbc.gridy = i+1;
					bg.add(tensorComp[i][j]);
					this.add(tensorComp[i][j],gbc);
				}
			}
			
			gbc.gridx = 0; gbc.gridy +=3;
			gbc.gridwidth = 3;
			tensorSum = new JRadioButton("GND (sum of absolutes)");
			tensorSum.setActionCommand("-1");
			tensorSum.addActionListener(al);
			this.add(tensorSum,gbc); gbc.gridy++;
			bg.add(tensorSum);
			scalarDens = new JRadioButton("Dis. density (ignore BV)");
			scalarDens.setActionCommand("-2");
			scalarDens.addActionListener(al);
			this.add(scalarDens,gbc);
			bg.add(scalarDens);
			
			gbc.gridx = 0; gbc.gridy++;
			this.add(axisComboBox, gbc);
			axisComboBox.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					int selected = axisComboBox.getSelectedIndex();
					if (selected == 0)
						ddtPlane = DDTPlane.X_PLANE;
					else if (selected == 1)
						ddtPlane = DDTPlane.Y_PLANE;
					else if (selected == 2)
						ddtPlane = DDTPlane.Z_PLANE;
						
					renderStartSlice = 0;
					renderEndSlice = JDislocationDensityTensorControls.this.dc.gridSize[selected];
									
					lowerLimitTextBox.setValue(0);
					upperLimitTextBox.setValue(JDislocationDensityTensorControls.this.dc.gridSize[selected]);
					viewer.reDraw();
				}
			});
			
			gbc.gridwidth = 1;
			gbc.gridy++;
			gbc.gridx = 0;
			this.add(new JLabel("min/max slide"),gbc);
			
			gbc.gridx = 1;
			upperLimitTextBox.addPropertyChangeListener(new PropertyChangeListener() {
				@Override
				public void propertyChange(PropertyChangeEvent arg0) {
					if (upperLimitTextBox.getValue()==null) return;
					Number l = (Number)upperLimitTextBox.getValue();
					renderEndSlice = l.intValue();
					viewer.reDraw();
				}
			});
			lowerLimitTextBox.addPropertyChangeListener(new PropertyChangeListener() {
				@Override
				public void propertyChange(PropertyChangeEvent arg0) {
					if (lowerLimitTextBox.getValue()==null) return;
					Number l = (Number)lowerLimitTextBox.getValue();
					renderStartSlice = l.intValue();
					viewer.reDraw();
				}
			});
			this.add(lowerLimitTextBox,gbc);
			gbc.gridx = 2;
			this.add(upperLimitTextBox,gbc);
			
			gbc.gridx = 0;
			gbc.gridwidth = 3;
			bg = new ButtonGroup();
			bg.add(dislocationNetworkDDTComboBox);
			bg.add(latticeDDTComboBox);
			gbc.gridy++;
			this.add(dislocationNetworkDDTComboBox, gbc);
			dislocationNetworkDDTComboBox.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					lattOrDis = true;
					viewer.reDraw();
				}
			});
			gbc.gridy++;
			this.add(latticeDDTComboBox, gbc);
			latticeDDTComboBox.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					lattOrDis = false;
					viewer.reDraw();
				}
			});
			
			//Enable default value, hide buttons if no selection possible
			boolean hasLatticeRotation = 
					Configuration.getCurrentAtomData().getDataColumnInfos().containsAll(
							Arrays.asList(new LatticeRotationModule().getDataColumnsInfo())
							);
			boolean hasSkeleton = (Configuration.getCurrentAtomData().getDataContainer(Skeletonizer.class)!=null);
			
			if (!hasSkeleton || !hasLatticeRotation){
				dislocationNetworkDDTComboBox.setVisible(false);
				latticeDDTComboBox.setVisible(false);
				if (hasSkeleton) lattOrDis = true;
				else lattOrDis = false;
			} else if (hasSkeleton) {
				dislocationNetworkDDTComboBox.doClick();
			} 
			
			ddtCompX = -2;
			ddtCompY = 0;
			scalarDens.setSelected(true);
			enableCheckBox.setSelected(true);
		}

		@Override
		public void setViewer(ViewerGLJPanel viewer) {
			this.viewer = viewer;
		}
		
		@Override
		public boolean isDataVisible() {
			return enableCheckBox.isSelected();
		}
		
		@Override
		public void update(DataContainer dc) {
			assert (dc instanceof DDTDataContainer);
			this.dc = (DDTDataContainer) dc;
		}
	}
	
		
}
