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

package processingModules.otherModules;

import gui.*;
import gui.glUtils.ArrowRenderer;
import gui.glUtils.ObjectRenderData;

import java.awt.GridLayout;
import java.awt.event.*;
import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import com.jogamp.opengl.GL3;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.filechooser.FileNameExtensionFilter;

import model.AtomData;
import model.Configuration;
import model.DataColumnInfo;
import model.Pickable;
import processingModules.ClonableProcessingModule;
import processingModules.DataContainer;
import processingModules.JDataPanel;
import processingModules.ProcessingResult;
import Jama.EigenvalueDecomposition;
import Jama.Matrix;
import common.*;

public class StressDataModule extends ClonableProcessingModule {

	private static JStressControlPanel dataPanel;
	private File stressFile = null;
	
	
	@Override
	public boolean showConfigurationDialog(JFrame frame, AtomData data) {
		File folder = Configuration.getLastOpenedFolder();
		JFileChooser chooser = new JFileChooser(folder);
		chooser.setFileFilter(new FileNameExtensionFilter("Stress Data", "stress", "stress.gz"));
		
		int result = chooser.showOpenDialog(null);
		if (result == JFileChooser.APPROVE_OPTION)
			stressFile = chooser.getSelectedFile();
		return result == JFileChooser.APPROVE_OPTION;
	}
	
	@Override
	public ProcessingResult process(AtomData data) throws Exception {
		StressDataContainer dc = new StressDataContainer();
		dc.processData(data, stressFile);
		return new DataContainer.DefaultDataContainerProcessingResult(dc, "");
	}

	@Override
	public String getFunctionDescription() {
		return "Stress data";
	}
	
	@Override
	public String getShortName() {
		return "Import stress";
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
	public DataColumnInfo[] getDataColumnsInfo() {
		return null;
	}
	
	

	private class StressDataContainer extends DataContainer{
		private ArrayList<StressValue> stresses = new ArrayList<StressValue>();
		private float[] minStress = new float[7];
		private float[] maxStress = new float[7];
		
		public boolean processData(AtomData data, File stressFile) throws IOException {
			getDataControlPanel(); // Initialize data panel, might be null
			BufferedReader lnr;
			DataInputStreamWrapper dis = null;
			FileInputStream fis = null;
			
			if (stressFile.getName().endsWith(".gz")){
				//Directly read gzip-compressed files
				lnr = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(stressFile))));
			} else 
				lnr = new BufferedReader(new FileReader(stressFile));
			

			Pattern p = Pattern.compile(" +");
			String format = "A";

			for (int i = 0; i < 7; i++) {
				this.minStress[i] = Float.POSITIVE_INFINITY;
				this.maxStress[i] = Float.NEGATIVE_INFINITY;
			}

			int numColumns = 0;
			int xColoumn = -1;
			int yColoumn = -1;
			int zColoumn = -1;
			
			int[] stressColumns = new int[6];
			for (int i=0; i<stressColumns.length; i++)
				stressColumns[i] = -1;

			try {
				String s = lnr.readLine();

				while (s != null && !s.startsWith("#E")) {
					if (s.startsWith("#F")) {
						String[] parts = p.split(s);
						format = parts[1];
					}
					if (s.startsWith("#C")) {
						String[] parts = p.split(s);
						numColumns = parts.length - 1;
						for (int i = 0; i < parts.length; i++) {
							if (parts[i].equals("x")) xColoumn = i - 1;
							if (parts[i].equals("y")) yColoumn = i - 1;
							if (parts[i].equals("z")) zColoumn = i - 1;
							if (parts[i].equals("s_xx")) stressColumns[0] = i - 1;
							if (parts[i].equals("s_yy")) stressColumns[1] = i - 1;
							if (parts[i].equals("s_zz")) stressColumns[2] = i - 1;
							if (parts[i].equals("s_yz")) stressColumns[3] = i - 1;
							if (parts[i].equals("s_zx")) stressColumns[4] = i - 1;
							if (parts[i].equals("s_xy")) stressColumns[5] = i - 1;
						}
					}
					s = lnr.readLine();
				}
				s = lnr.readLine();
				if (format.equals("A")) { // Ascii-Format
					float x,y,z;
					while (s != null) {
						String[] parts = p.split(s);

						x = Float.parseFloat(parts[xColoumn]);
						y = Float.parseFloat(parts[yColoumn]);
						z = Float.parseFloat(parts[zColoumn]);

						float[] stress = new float[7];
						stress[0] = Float.parseFloat(parts[stressColumns[0]]) * 160.2f;
						stress[1] = Float.parseFloat(parts[stressColumns[1]]) * 160.2f;
						stress[2] = Float.parseFloat(parts[stressColumns[2]]) * 160.2f;
						stress[3] = Float.parseFloat(parts[stressColumns[3]]) * 160.2f;
						stress[4] = Float.parseFloat(parts[stressColumns[4]]) * 160.2f;
						stress[5] = Float.parseFloat(parts[stressColumns[5]]) * 160.2f;
						stress[6] = calcVonMisesStress(stress);

						if (stress[6] > dataPanel.minimalVonMisesStress) {
							stresses.add(new StressValue(x,y,z, stress));
							for (int i = 0; i < 7; i++) {
								if (stress[i] < minStress[i]) minStress[i] = stress[i];
								if (stress[i] > maxStress[i]) maxStress[i] = stress[i];
							}
						}
						s = lnr.readLine();
					}
				} else if (format.equals("l") || format.equals("b") || format.equals("L") || format.equals("B")) {
					//Binary reader
					fis = new FileInputStream(stressFile);
					BufferedInputStream bis;
					long filesize = 0l;
					
					if (stressFile.getName().endsWith(".gz")){
						//Setup streams for compressed files
						//read streamSize from the last 4 bytes in the file
						RandomAccessFile raf = new RandomAccessFile(stressFile, "r");
						long l = raf.length();
						raf.seek(l-4);
						
						byte[] w = new byte[4];
						raf.readFully(w, 0, 4);
						filesize = (w[3]) << 24 | (w[2]&0xff) << 16 | (w[1]&0xff) << 8 | (w[0]&0xff);
						raf.close();
						bis = new BufferedInputStream(new GZIPInputStream(new FileInputStream(stressFile)), 4096);
					} else {
						bis = new BufferedInputStream(new FileInputStream(stressFile), 16384);
						filesize = fis.available();
					}

					boolean littleEndian = format.equals("l") || format.equals("L");
					boolean doublePrecision = format.equals("L") || format.equals("B");
					
					dis = DataInputStreamWrapper.getDataInputStreamWrapper(bis, doublePrecision, littleEndian);

					// Seek the end of the header
					boolean terminationSymbolFound = false;
					byte b1, b2 = 0;
					do {
						b1 = b2;
						b2 = dis.readByte();
						if (b1 == 0x23 && b2 == 0x45) terminationSymbolFound = true;
					} while (!terminationSymbolFound);
					// read end of line - lf for unix or cr/lf for windows
					byte b = dis.readByte();
					if (b == 0x0d) {
						b = dis.readByte();
					}

					float x = 0f,y = 0f, z = 0f;
					while (filesize > dis.getBytesRead()) {
						float[] stress = new float[7];

						for (int i = 0; i < numColumns; i++) {
							//Check if a stress value is next to be read
							int stressColoum = -1;
							for (int j = 0; j < 6; j++)
								if (stressColumns[j] == i) stressColoum = j;
							
							if (stressColoum != -1){
								stress[stressColoum] = dis.readFloat();
								stress[stressColoum] *= 160.2f;
							}
							//else test x,y,z
							else if (i == xColoumn) {
								x = dis.readFloat();
							} else if (i == yColoumn) {
								y = dis.readFloat();
							} else if (i == zColoumn) {
								z = dis.readFloat();
							} else {
								if (doublePrecision) {
									dis.readInt();
									dis.readInt();
								} else {
									dis.readInt();
								}
							}
						} // end of for (int i=0; i<numColumns; i++)
						stress[6] = calcVonMisesStress(stress);
						if (stress[6] > dataPanel.minimalVonMisesStress) {
							stresses.add(new StressValue(x,y,z, stress));
							for (int j = 0; j < 7; j++) {
								if (stress[j] < minStress[j]) minStress[j] = stress[j];
								if (stress[j] > maxStress[j]) maxStress[j] = stress[j];
							}
						}
					} // end of while (filesize>bytesRead)
				} else throw new IllegalArgumentException("File format not supported");	
			} catch (IOException ex) {
				throw ex;
			} finally {
				if (fis != null) fis.close();
				if (dis != null) dis.close();
				lnr.close();
			}

			//In case no value exceeds the critical threshold, nullify min & max
			if (stresses.size() == 0)
			for (int j = 0; j < 7; j++) {
				minStress[j] = 0f;
				maxStress[j] = 0f;
			}
			
			for (int i = 0; i < 7; i++) {
				if (minStress[i] < dataPanel.globalMinStress[i]) dataPanel.globalMinStress[i] = minStress[i];
				if (maxStress[i] > dataPanel.globalMaxStress[i]) dataPanel.globalMaxStress[i] = maxStress[i];
			}
			return true;
		}
		
		@Override
		public void drawSolidObjects(ViewerGLJPanel viewer, GL3 gl, RenderRange renderRange, boolean picking, AtomData data) {
			if (!dataPanel.isDataVisible()) return;
			
			float min = dataPanel.globalMinStress[dataPanel.showStressValue];
			float max = dataPanel.globalMaxStress[dataPanel.showStressValue];
			
			float lowLimit = dataPanel.lowerLimit*(max-min) + min; 
			float upLimit = dataPanel.upperLimit*(max-min) + min;
			
			ArrayList<StressValue> objects = new ArrayList<StressValue>();
		
			for (int i = 0; i < stresses.size(); i++) {
				StressValue c = stresses.get(i);
				if (renderRange.isInInterval(c)){
					float s = c.getStress(dataPanel.showStressValue);
					if ( (!dataPanel.invertInterval && s >= lowLimit && s <= upLimit) ||
							(dataPanel.invertInterval && (s <= lowLimit || s >= upLimit))){
						objects.add(c);
					}
				}
			}
			
			ObjectRenderData<StressValue> ord = 
					new ObjectRenderData<StressValue>(objects, false, data);
			ObjectRenderData<?>.Cell c = ord.getRenderableCells().get(0);
			for(int i=0; i<objects.size(); i++){
				float[] col = ColorTable.getIntensityGLColor(min, max, objects.get(i).getStress(dataPanel.showStressValue));
				c.getColorArray()[3*i+0] = col[0];
				c.getColorArray()[3*i+1] = col[1];
				c.getColorArray()[3*i+2] = col[2];
				c.getSizeArray()[i] = 1f;
				c.getVisibiltyArray()[i] = true;
			}
			ord.reinitUpdatedCells();
			
			viewer.drawSpheres(gl, ord, picking);
		}
		
		@Override
		public void drawTransparentObjects(ViewerGLJPanel viewer, GL3 gl, RenderRange renderRange, boolean picking, AtomData data) {
			return;
		}
			
		@Override
		public boolean isTransparenceRenderingRequired() {
			return false;
		}
		
		/**
		 * Drawing the principal orientation of the stress tensor
		 * @param gl
		 * @param renderRange
		 * @param mode
		 */
		@SuppressWarnings("unused")
		public void drawStressTensor(ViewerGLJPanel viewer, GL3 gl, RenderRange renderRange, boolean picking){
			if (!dataPanel.isDataVisible()) return;

			float[] colR = new float[]{1.0f,0.0f,0.0f, 1f};
			float[] colG = new float[]{0.0f,1.0f,0.0f, 1f};
			float[] colB = new float[]{0.0f,0.0f,1.0f, 1f};
			
			final float scaleThick = 0.5f;
			final float scale = 0.5f;
			
			float min = dataPanel.globalMinStress[dataPanel.showStressValue];
			float max = dataPanel.globalMaxStress[dataPanel.showStressValue];
			
			float lowLimit = dataPanel.lowerLimit*(max-min) + min; 
			float upLimit = dataPanel.upperLimit*(max-min) + min;
			
			for (int i = 0; i < stresses.size(); i++) {
				StressValue c = stresses.get(i);
				if (renderRange.isInInterval(c)){
					float s = c.getStress(dataPanel.showStressValue);
					if ( (!dataPanel.invertInterval && s >= lowLimit && s <= upLimit) ||
							(dataPanel.invertInterval && (s <= lowLimit || s >= upLimit))){
						Vec3 dir = new Vec3();
					
						Matrix m = new Matrix(new double[][]{
								{c.getStress(0),c.getStress(5),c.getStress(4)},
								{c.getStress(5),c.getStress(1),c.getStress(3)},
								{c.getStress(4),c.getStress(3),c.getStress(2)}});
						EigenvalueDecomposition evd = m.eig();
						
						if (evd.getRealEigenvalues().length == 3){
							if (picking) {
								colR = viewer.getNextPickingColor(c);
								colG = colR; colB = colR;
							}
							
							
							dir.x = (float)evd.getV().get(0, 0) * (float)evd.getRealEigenvalues()[0] * scale;
							dir.y = (float)evd.getV().get(0, 1) * (float)evd.getRealEigenvalues()[0] * scale;
							dir.z = (float)evd.getV().get(0, 2) * (float)evd.getRealEigenvalues()[0] * scale;
							ArrowRenderer.renderArrow(gl, c, dir, scaleThick, 1f, colR, true);
							dir.x = (float)evd.getV().get(1, 0) * (float)evd.getRealEigenvalues()[1] * scale;
							dir.y = (float)evd.getV().get(1, 1) * (float)evd.getRealEigenvalues()[1] * scale;
							dir.z = (float)evd.getV().get(1, 2) * (float)evd.getRealEigenvalues()[1] * scale;
							ArrowRenderer.renderArrow(gl, c, dir, scaleThick, 1f, colG, true);
							dir.x = (float)evd.getV().get(2, 0) * (float)evd.getRealEigenvalues()[2] * scale;
							dir.y = (float)evd.getV().get(2, 1) * (float)evd.getRealEigenvalues()[2] * scale;
							dir.z = (float)evd.getV().get(2, 2) * (float)evd.getRealEigenvalues()[2] * scale;
							ArrowRenderer.renderArrow(gl, c, dir, scaleThick, 1f, colB,true);
						}
					}
				}
			}
		}
		
		public JDataPanel getDataControlPanel(){
			if (dataPanel == null)
				dataPanel = new JStressControlPanel(); 
			return dataPanel;
		}
		
		private float calcVonMisesStress(float[] stress) {
			return (float) Math.sqrt((
					(stress[0] - stress[1]) * (stress[0] - stress[1]) + (stress[1] - stress[2]) * 
					(stress[1] - stress[2]) + (stress[2] - stress[0]) * (stress[2] - stress[0]) + 
					6 * (stress[3] * stress[3] + stress[4] * stress[4] + stress[5] * stress[5])) * 0.5f);
		}
		
		public class StressValue extends Vec3 implements Pickable{
			private float[] stress;
			
			public StressValue(float x, float y, float z, float[] stress) {
				this.stress = stress;
				this.x = x;
				this.y = y;
				this.z = z;
			}
				
			public float getStress(int i) {
				if (stress == null || i<0 || i>6) return 0f;
				return stress[i];
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
				String[] keys = {"Position", "Stress_xx", "Stress_yy", "Stress_zz", "Stress_yz", "Stress_zx"
						, "Stress_xy", "von Mises stress"};
				String[] values = {
					this.toString(), Float.toString(stress[0]),
					Float.toString(stress[1]), Float.toString(stress[2]),
					Float.toString(stress[3]), Float.toString(stress[4]), 
					Float.toString(stress[5]), Float.toString(stress[6])
				};
				
				return new Tupel<String,String>("Stress value", CommonUtils.buildHTMLTableForKeyValue(keys, values));
			}
			
			@Override
			public Vec3 getCenterOfObject() {
				return this.clone();
			}
		}
	}	
	
	private static class JStressControlPanel extends JDataPanel {
		float[] globalMinStress = new float[7];
		float[] globalMaxStress = new float[7];
		int showStressValue = 0;
		float lowerLimit = 0f;
		float upperLimit = 1f;
		boolean invertInterval = false;
		float minimalVonMisesStress = 0f;
		
		
		private static final long serialVersionUID = 1L;
		private JRangeSlider rangeSlider = new JRangeSlider(0,100);
		private JLabel upperRangeLimit = new JLabel("");
		private JLabel lowerRangeLimit = new JLabel("");
		private JComboBox stressComboBox = new JComboBox(new String[]{"xx","yy","zz","yz","zx","xy","vonMises"});
		private JCheckBox invertIntervalCheckBox = new JCheckBox("Invert interval", invertInterval);
		private JCheckBox showStressCheckbox = new JCheckBox("Show Stress", false);
		
		private ViewerGLJPanel viewer;
		
		public JStressControlPanel() {
			super("Stress");
			this.setLayout(new GridLayout(6,1));
			
			this.add(showStressCheckbox);
			this.showStressCheckbox.addActionListener(new ActionListener() {				
				@Override
				public void actionPerformed(ActionEvent e) {
					if (viewer!=null) viewer.reDraw();
				}
			});
			
			this.add(stressComboBox);
			stressComboBox.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					showStressValue = stressComboBox.getSelectedIndex();
					double range = globalMaxStress[showStressValue]-globalMinStress[showStressValue];
					double min = globalMinStress[showStressValue];
					upperRangeLimit.setText(String.format("Max: %.2f GPa;Set: %.2f GPa", globalMaxStress[showStressValue], rangeSlider.getUpperValue()*0.01f*range+min));
					lowerRangeLimit.setText(String.format("Min: %.2f GPa;Set: %.2f GPa", globalMinStress[showStressValue], rangeSlider.getValue()*0.01f*range+min));
					if (viewer!=null) viewer.reDraw();
				}
			});
			
			upperRangeLimit.setText("Max: 0 GPa");
			lowerRangeLimit.setText("Min: 0 GPa");
			
			
			this.add(upperRangeLimit);
			this.add(rangeSlider);
			rangeSlider.addChangeListener(new ChangeListener() {
				@Override
				public void stateChanged(ChangeEvent e) {
					setLowerLimit(rangeSlider.getValue()*0.01f);
					setUpperLimit(rangeSlider.getUpperValue()*0.01f);
					if (viewer!=null) viewer.repaint();
					double range = globalMaxStress[showStressValue] - globalMinStress[showStressValue];
					double min = globalMinStress[showStressValue];
					rangeSlider.setToolTipText(String.format("lower limit = %.2f upper limit = %.2f", 
							rangeSlider.getValue()*0.01f*range+min,
							rangeSlider.getUpperValue()*0.01f*range+min));
					upperRangeLimit.setText(String.format("Max: %.2f GPa;Set: %.2f GPa", globalMaxStress[showStressValue], rangeSlider.getUpperValue()*0.01f*range+min));
					lowerRangeLimit.setText(String.format("Min: %.2f GPa;Set: %.2f GPa", globalMinStress[showStressValue], rangeSlider.getValue()*0.01f*range+min));
					
					if (viewer!=null) viewer.reDraw();
				}
			});
			rangeSlider.setValue(20);
			rangeSlider.setUpperValue(100);
			this.add(lowerRangeLimit);
			this.add(invertIntervalCheckBox);
			invertIntervalCheckBox.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					invertInterval = invertIntervalCheckBox.isSelected();
					if (viewer!=null) viewer.reDraw();
				}
			});
		}
		
		@Override
		public void setViewer(ViewerGLJPanel viewer){
			if (this.viewer==null){
				this.viewer = viewer;
				double range = globalMaxStress[showStressValue] - globalMinStress[showStressValue];
				double min = globalMinStress[showStressValue];
				upperRangeLimit.setText(String.format("Max: %.2f GPa;Set: %.2f GPa", globalMaxStress[showStressValue], rangeSlider.getUpperValue()*0.01f*range+min));
				lowerRangeLimit.setText(String.format("Min: %.2f GPa;Set: %.2f GPa", globalMinStress[showStressValue], rangeSlider.getValue()*0.01f*range+min));
			}
		}
		
		@Override
		public void update(DataContainer dc) {}
		
		@Override
		public boolean isDataVisible() {
			return showStressCheckbox.isSelected();
		}
		
		void setLowerLimit(float lowerLimit) {
			if (lowerLimit<0f) this.lowerLimit = 0f;
			if (lowerLimit>this.upperLimit) this.lowerLimit = this.upperLimit;
			else this.lowerLimit = lowerLimit;
		}
		
		void setUpperLimit(float upperLimit) {
			if (upperLimit>1f) this.upperLimit = 1f;
			if (upperLimit<this.lowerLimit) this.upperLimit = this.lowerLimit;
			else this.upperLimit = upperLimit;
		}
	}
}
