package model.dataContainer;

import gui.glUtils.Shader.BuiltInShader;
import gui.glUtils.VertexDataStorageLocal;
import gui.RenderRange;
import gui.ViewerGLJPanel;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.io.*;
import java.util.Collection;
import java.util.regex.Pattern;

import javax.media.opengl.GL;
import javax.media.opengl.GL3;
import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileNameExtensionFilter;

import common.ColorTable;
import common.Vec3;
import model.AtomData;
import model.BoxParameter;
import model.Configuration;
import model.Pickable;

public class LoadBalancingData extends DataContainer {

	private static JLoadBalancingControlPanel dataPanel = null;
	private int numCPU;
	private int[] cpuDim = new int[3];
	private LoadBalanceInfo[] info;
	private static boolean drawAsWireframe = true;
	
	private float maxLoad;
	private float minLoad;
//	private float variance;
	private File dataFile = null;
	
	private static final int[] triangleIndices = new int[]{0,5,4, 0,1,5, 1,7,5, 1,3,7, 3,2,7, 
		2,6,7, 2,0,6, 0,4,6, 4,7,6, 4,5,7, 0,2,3, 3,1,0};
	
	
	@Override
	public void drawSolidObjects(ViewerGLJPanel viewer, GL3 gl, RenderRange renderRange, boolean picking, BoxParameter box) {
		return;
	}
	
	@Override
	public void drawTransparentObjects(ViewerGLJPanel viewer, GL3 gl, RenderRange renderRange, boolean picking, BoxParameter box) {
		if (!getDataControlPanel().isDataVisible()) return;
		
		int xMin, xMax, yMin, yMax, zMin, zMax;
		
		xMin = ((SpinnerNumberModel)(dataPanel.lowerLimitSpinnerX.getModel())).getNumber().intValue();
		yMin = ((SpinnerNumberModel)(dataPanel.lowerLimitSpinnerY.getModel())).getNumber().intValue();
		zMin = ((SpinnerNumberModel)(dataPanel.lowerLimitSpinnerZ.getModel())).getNumber().intValue();
		xMax = ((SpinnerNumberModel)(dataPanel.upperLimitSpinnerX.getModel())).getNumber().intValue();
		yMax = ((SpinnerNumberModel)(dataPanel.upperLimitSpinnerY.getModel())).getNumber().intValue();
		zMax = ((SpinnerNumberModel)(dataPanel.upperLimitSpinnerZ.getModel())).getNumber().intValue();
		
		if (xMax > cpuDim[0]) xMax=cpuDim[0];
		if (yMax > cpuDim[1]) yMax=cpuDim[1];
		if (zMax > cpuDim[2]) zMax=cpuDim[2];
		
		if (drawAsWireframe){
			gl.glPolygonMode(GL.GL_FRONT_AND_BACK, GL3.GL_LINE);
			gl.glDisable(GL.GL_CULL_FACE);
		}
		
		BuiltInShader.NO_LIGHTING.getShader().enable(gl);
		int numElements = 0;
		VertexDataStorageLocal vds = 
				new VertexDataStorageLocal(gl, cpuDim[0]*cpuDim[1]*cpuDim[2]*36, 3, 3, 0, 4, 0, 0, 0, 0);
		vds.beginFillBuffer(gl);
		
		gl.glLineWidth(1f);
		
		for (int x = xMin; x < Math.min(xMax, cpuDim[0]); x++) {
			for (int y = yMin; y < Math.min(yMax, cpuDim[1]); y++) {
				for (int z = zMin; z < Math.min(zMax, cpuDim[2]); z++) {
					LoadBalanceInfo in = info[x*cpuDim[1]*cpuDim[2] + y*cpuDim[2] + z];
					
					vds.setColor(ColorTable.getIntensityGLColor(0, numCPU-1, in.cpu, 1f));
					vds.setColor(ColorTable.getIntensityGLColor(0.8f, 1.2f, in.load, 0.4f));
					
//					else vds.setColor(ColorTable.getIntensityGLColor(0, numCPU-1, in.cpu, 1f));
					vds.setColor(ColorTable.getIntensityGLColor(minLoad, maxLoad, in.load, 0.4f));
					vds.setColor(ColorTable.getIntensityGLColor(0, numCPU-1, in.cpu, 0.4f));
//					vds.setColor(ColorTable.getIntensityGLColor(0.5f, 1.5f, in.load, 0.2f));

					
					
//					vds.setColor(0f, 0f, 0f, 1f);
					
					if (picking) vds.setColor(viewer.getNextPickingColor(in));
					
					for (int j=0; j<12; j++){
						float[] p1 = in.cornerPosition[triangleIndices[3*j+0]];
						float[] p2 = in.cornerPosition[triangleIndices[3*j+1]];
						float[] p3 = in.cornerPosition[triangleIndices[3*j+2]];
						Vec3 normal = Vec3.makeNormal(new Vec3(p1[0], p1[1], p1[2]),
								new Vec3(p2[0], p2[1], p2[2]), new Vec3(p3[0], p3[1], p3[2]));
						vds.setNormal(normal.x, normal.y, normal.z);
						vds.setVertex(p1);
						vds.setVertex(p2);
						vds.setVertex(p3);
						numElements += 3;
					}
				}
			}
		}
		
		vds.endFillBuffer(gl);
		vds.setNumElements(numElements);
		vds.draw(gl, GL.GL_TRIANGLES);
		
		if (!drawAsWireframe){
			gl.glEnable(GL3.GL_POLYGON_OFFSET_LINE);
			gl.glPolygonOffset(0f, -100f);
			gl.glPolygonMode(GL.GL_FRONT_AND_BACK, GL3.GL_LINE);
		
			vds.beginFillBuffer(gl);
			vds.setColor(0f, 0f, 0f, 1f);
			for (int x = xMin; x < Math.min(xMax, cpuDim[0]); x++) {
				for (int y = yMin; y < Math.min(yMax, cpuDim[1]); y++) {
					for (int z = zMin; z < Math.min(zMax, cpuDim[2]); z++) {
						LoadBalanceInfo in = info[x*cpuDim[1]*cpuDim[2] + y*cpuDim[2] + z];
						
						for (int j=0; j<12; j++){
							float[] p1 = in.cornerPosition[triangleIndices[3*j+0]];
							float[] p2 = in.cornerPosition[triangleIndices[3*j+1]];
							float[] p3 = in.cornerPosition[triangleIndices[3*j+2]];
							//Creating normal
							Vec3 normal = Vec3.makeNormal(new Vec3(p1[0], p1[1], p1[2]),
									new Vec3(p2[0], p2[1], p2[2]), new Vec3(p3[0], p3[1], p3[2]));
							vds.setNormal(normal.x, normal.y, normal.z);
							vds.setVertex(p1);
							vds.setVertex(p2);
							vds.setVertex(p3);
						}
					}
				}
			}
			vds.endFillBuffer(gl);
			vds.draw(gl, GL.GL_TRIANGLES);
			
			gl.glDisable(GL3.GL_POLYGON_OFFSET_LINE);
		}
		
		gl.glPolygonMode(GL.GL_FRONT_AND_BACK, GL3.GL_FILL);
		
//		gl.glPointSize(20);
//
//		vds.beginFillBuffer(gl);
//		numElements = 0;
//		for (int x=xMin; x<xMax; x++){
//			for (int y=yMin; y<yMax; y++){
//				for (int z=zMin; z<zMax; z++){
//					LoadBalanceInfo in = info[x*cpuDim[1]*cpuDim[2] + y*cpuDim[2] + z];					
//					for (int i=0; i<8; i++){
//						vds.setColor(ColorTable.getIntensityGLColor(0, 7, i, 1f));
//						vds.setVertex(in.cornerPosition[i]);
//					}
//					numElements += 8;
//				}
//			}
//		}
//		vds.endFillBuffer(gl);
//		vds.setNumElements(numElements);
//		vds.draw(gl, GL.GL_POINTS);
		
		vds.dispose(gl);
		viewer.drawLegendThisFrame(Float.toString(minLoad), Float.toString((minLoad+maxLoad)*0.5f),  Float.toString(maxLoad));
//		viewer.drawLegendThisFrame("0.8", "1.0",  "1.2");
		
		gl.glEnable(GL.GL_CULL_FACE);
	}
	
	@Override
	public boolean isTransparenceRenderingRequired() {
		return true;
	}

	@Override
	public boolean showOptionsDialog() {
		File folder = Configuration.getLastOpenedFolder();
		JFileChooser chooser = new JFileChooser(folder);
		chooser.setFileFilter(new FileNameExtensionFilter("Load balancing data", "lb"));
		
		int result = chooser.showOpenDialog(null);
		if (result == JFileChooser.APPROVE_OPTION)
			dataFile = chooser.getSelectedFile();
		return result == JFileChooser.APPROVE_OPTION;
	};
	
	@Override
	public boolean processData(AtomData atomData) throws IOException {
		LineNumberReader lnr = null;
		Pattern p = Pattern.compile(" +");
		
		try {
			lnr = new LineNumberReader(new FileReader(dataFile));
			String s = lnr.readLine();
			
			String[] parts = p.split(s);
			if (!parts[0].equals("#I")) return false;
			
			cpuDim[0] = Integer.parseInt(parts[1]);
			cpuDim[1] = Integer.parseInt(parts[2]);
			cpuDim[2] = Integer.parseInt(parts[3]);
			
			numCPU = cpuDim[0]*cpuDim[1]*cpuDim[2];
			info = new LoadBalanceInfo[numCPU];
			for (int i=0; i<numCPU; i++){
				info[i] = new LoadBalanceInfo(i);
			}
			
			s = lnr.readLine();	//Box Size
			s = lnr.readLine();
			s = lnr.readLine();
			
			s = lnr.readLine();
			parts = p.split(s);
			
			maxLoad = Float.parseFloat(parts[1]);
			minLoad = Float.parseFloat(parts[3]);
//			variance = Float.parseFloat(parts[5]);
			
			s = lnr.readLine();
			while (s!=null){
				parts = p.split(s);
				
				if (parts[0].equals("offset")){
					LoadBalanceInfo i = info[Integer.parseInt(parts[1])];
					i.offset[0] = Integer.parseInt(parts[2]);
					i.offset[1] = Integer.parseInt(parts[3]);
					i.offset[2] = Integer.parseInt(parts[4]);
				}
				if (parts[0].equals("size")){
					LoadBalanceInfo i = info[Integer.parseInt(parts[1])];
					i.size[0] = Integer.parseInt(parts[2]);
					i.size[1] = Integer.parseInt(parts[3]);
					i.size[2] = Integer.parseInt(parts[4]);
				}
				if (parts[0].equals("load")){
					LoadBalanceInfo i = info[Integer.parseInt(parts[1])];
					i.load = Float.parseFloat(parts[2]);
				}
				if (parts[0].equals("volume")){
					LoadBalanceInfo i = info[Integer.parseInt(parts[1])];
					i.volume = Float.parseFloat(parts[2]);
				}
				if (parts[0].equals("particles")){
					LoadBalanceInfo i = info[Integer.parseInt(parts[1])];
					i.particles = Integer.parseInt(parts[2]);
				}
				if (parts[0].equals("cog")){
					LoadBalanceInfo i = info[Integer.parseInt(parts[1])];
					i.cog[0] = Float.parseFloat(parts[2]);
					i.cog[1] = Float.parseFloat(parts[3]);
					i.cog[2] = Float.parseFloat(parts[4]);
				}
				if (parts[0].equals("corner") && parts[1].equals("p")){
					LoadBalanceInfo i = info[Integer.parseInt(parts[2])];
					int j = Integer.parseInt(parts[3]);
					i.cornerPosition[j][0] = Float.parseFloat(parts[4]);
					i.cornerPosition[j][1] = Float.parseFloat(parts[5]);
					i.cornerPosition[j][2] = Float.parseFloat(parts[6]);
				}
				
				if (parts[0].equals("corner") && parts[1].equals("fixed")){
					LoadBalanceInfo i = info[Integer.parseInt(parts[2])];
					int j = Integer.parseInt(parts[3]);
					i.cornerFixed[j][0] = Integer.parseInt(parts[4])==1;
					i.cornerFixed[j][1] = Integer.parseInt(parts[5])==1;
					i.cornerFixed[j][2] = Integer.parseInt(parts[6])==1;
				}
				
				s = lnr.readLine();
			}
			
		} catch (IOException ex){
			throw ex;
		} catch (NumberFormatException ex){
			return false;
		} finally {
			if (lnr != null) lnr.close();
		}
		return true;
	}

	@Override
	public JDataPanel getDataControlPanel() {
		if (dataPanel == null)
			dataPanel = new JLoadBalancingControlPanel();
		return dataPanel;
	}

	@Override
	public String getDescription() {
		return "Import the geometry of the dynamic IMD load balacing for visualization.";
	}

	@Override
	public String getName() {
		return "Import Load Balancing data";
	}

	@Override
	public DataContainer deriveNewInstance() {
		LoadBalancingData clone = new LoadBalancingData();
		clone.dataFile = this.dataFile;
		return clone;
	}

	
	public static class JLoadBalancingControlPanel extends JDataPanel {
		private static final long serialVersionUID = 1L;
		private JSpinner lowerLimitSpinnerX = new JSpinner(new SpinnerNumberModel(0, 0, 512, 1));
		private JSpinner upperLimitSpinnerX = new JSpinner(new SpinnerNumberModel(0, 0, 512, 1));
		private JSpinner lowerLimitSpinnerY = new JSpinner(new SpinnerNumberModel(0, 0, 512, 1));
		private JSpinner upperLimitSpinnerY = new JSpinner(new SpinnerNumberModel(0, 0, 512, 1));
		private JSpinner lowerLimitSpinnerZ = new JSpinner(new SpinnerNumberModel(0, 0, 512, 1));
		private JSpinner upperLimitSpinnerZ = new JSpinner(new SpinnerNumberModel(0, 0, 512, 1));
		private JCheckBox showLBCheckbox = new JCheckBox("Show Load Balancing", false);
		private JCheckBox wireFrameCheckbox = new JCheckBox("Wireframe", true);
		
		RedrawChangeListener redrawListener = new RedrawChangeListener();
		
		private ViewerGLJPanel viewer;
		
		public JLoadBalancingControlPanel() {
			((JSpinner.NumberEditor)lowerLimitSpinnerX.getEditor()).getFormat().setMaximumFractionDigits(4);
			((JSpinner.NumberEditor)upperLimitSpinnerX.getEditor()).getFormat().setMaximumFractionDigits(4);
			((JSpinner.NumberEditor)lowerLimitSpinnerY.getEditor()).getFormat().setMaximumFractionDigits(4);
			((JSpinner.NumberEditor)upperLimitSpinnerY.getEditor()).getFormat().setMaximumFractionDigits(4);
			((JSpinner.NumberEditor)lowerLimitSpinnerZ.getEditor()).getFormat().setMaximumFractionDigits(4);
			((JSpinner.NumberEditor)upperLimitSpinnerZ.getEditor()).getFormat().setMaximumFractionDigits(4);
			 
			((SpinnerNumberModel)(lowerLimitSpinnerX.getModel())).setMinimum(0);
			((SpinnerNumberModel)(upperLimitSpinnerX.getModel())).setMinimum(0);
			((SpinnerNumberModel)(upperLimitSpinnerX.getModel())).setMaximum(512);
			((SpinnerNumberModel)(lowerLimitSpinnerX.getModel())).setMaximum(512);
			
			((SpinnerNumberModel)(lowerLimitSpinnerY.getModel())).setMinimum(0);
			((SpinnerNumberModel)(upperLimitSpinnerY.getModel())).setMinimum(0);
			((SpinnerNumberModel)(upperLimitSpinnerY.getModel())).setMaximum(512);
			((SpinnerNumberModel)(lowerLimitSpinnerY.getModel())).setMaximum(512);
			
			((SpinnerNumberModel)(lowerLimitSpinnerZ.getModel())).setMinimum(0);
			((SpinnerNumberModel)(upperLimitSpinnerZ.getModel())).setMinimum(0);
			((SpinnerNumberModel)(upperLimitSpinnerZ.getModel())).setMaximum(512);
			((SpinnerNumberModel)(lowerLimitSpinnerZ.getModel())).setMaximum(512);
			
			this.setBorder(new TitledBorder(new EtchedBorder(1), "Values"));
			
			this.setLayout(new GridBagLayout());
			
			GridBagConstraints gbc = new GridBagConstraints();
			
			gbc.anchor = GridBagConstraints.WEST;
			gbc.weightx = 1;
			gbc.fill = GridBagConstraints.BOTH;
			gbc.gridy = 0; gbc.gridx = 0;
			gbc.gridwidth = 2;
			this.add(showLBCheckbox, gbc);
			gbc.gridx = 0; gbc.gridy++;
			this.add(wireFrameCheckbox, gbc);
			gbc.gridx = 0; gbc.gridy++;
			gbc.gridwidth = 1;
			
			this.add(new JLabel("Min."), gbc);gbc.gridx++;
			this.add(new JLabel("Max."), gbc);
			gbc.gridx = 0; gbc.gridy++;
			this.add(lowerLimitSpinnerX, gbc);gbc.gridx++;
			this.add(upperLimitSpinnerX, gbc);
			gbc.gridx = 0; gbc.gridy++;
			
			this.add(lowerLimitSpinnerY, gbc);gbc.gridx++;
			this.add(upperLimitSpinnerY, gbc);
			gbc.gridx = 0; gbc.gridy++;
			
			this.add(lowerLimitSpinnerZ, gbc);gbc.gridx++;
			this.add(upperLimitSpinnerZ, gbc);
			gbc.gridx = 0; gbc.gridy++;
			
			showLBCheckbox.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					viewer.reDraw();
				}
			});
			
			wireFrameCheckbox.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					LoadBalancingData.drawAsWireframe = wireFrameCheckbox.isSelected();
					viewer.reDraw();
				}
			});
			
			lowerLimitSpinnerX.addChangeListener(redrawListener);
			upperLimitSpinnerX.addChangeListener(redrawListener);
			lowerLimitSpinnerY.addChangeListener(redrawListener);
			upperLimitSpinnerY.addChangeListener(redrawListener);
			lowerLimitSpinnerZ.addChangeListener(redrawListener);
			upperLimitSpinnerZ.addChangeListener(redrawListener);
		}

		@Override
		public void setViewer(ViewerGLJPanel viewer) {
			this.viewer = viewer;
			this.redrawListener.setViewer(viewer);
		}

		@Override
		public void update(DataContainer dc) {}

		@Override
		public boolean isDataVisible() {
			return showLBCheckbox.isSelected();
		}
		
	}

	
	private static class LoadBalanceInfo implements Pickable{
		private final int cpu;
		private final int[] size = new int[3];
		private final int[] offset = new int[3];
		private float volume;
		private float load;
		private int particles;
		private final float[] cog = new float[3];
		private final float[][] cornerPosition = new float[8][3];
		private final boolean[][] cornerFixed = new boolean[8][3];
		
		public LoadBalanceInfo(int cpu) {
			this.cpu = cpu;
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
		public String printMessage(InputEvent ev, AtomData data) {
			return String.format("CPU %d load: %.6f particles: %d volume: %.6f", cpu, load, particles, volume);
		}
	}

	private static class RedrawChangeListener implements ChangeListener{
		private ViewerGLJPanel viewer = null;
		
		public void setViewer(ViewerGLJPanel viewer) {
			this.viewer = viewer;
		}
		
		@Override
		public void stateChanged(ChangeEvent e) {
			this.viewer.reDraw();
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
}
