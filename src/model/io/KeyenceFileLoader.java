// Part of AtomViewer: AtomViewer is a tool to display and analyse
// atomistic simulations
//
// Copyright (C) 2015  ICAMS, Ruhr-Universität Bochum
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

package model.io;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JSlider;
import javax.swing.filechooser.FileFilter;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL3;

import common.Vec3;
import gui.JColorSelectPanel;
import gui.PrimitiveProperty;
import gui.PrimitiveProperty.BooleanProperty;
import gui.PrimitiveProperty.FloatProperty;
import gui.glUtils.Shader;
import gui.glUtils.Shader.BuiltInShader;
import gui.RenderRange;
import gui.ViewerGLJPanel;
import model.Atom;
import model.AtomData;
import model.DataColumnInfo;
import model.ImportConfiguration;
import model.RenderingConfiguration;
import processingModules.ClonableProcessingModule;
import processingModules.DataContainer;
import processingModules.JDataPanel;
import processingModules.ProcessingResult;
import model.DataColumnInfo.Component;
import model.mesh.FinalMesh;
import model.Filter;

public class KeyenceFileLoader extends MDFileLoader {

	private FloatProperty zScaling = new FloatProperty("zScaling", "Z-Scaling",
			"Multiplier for the z-axiz", 1f, 0f, 1000f);
	private BooleanProperty createMesh = new BooleanProperty("meshing", "Create surface as mesh", "Creates a 3D-mesh representing the Keyence information", false);
	private BooleanProperty ignoreZeros = new BooleanProperty("ignoreZeros", "Discard values at z=0", "Discards data points at z=0", true);

	@Override
	public List<PrimitiveProperty<?>> getOptions(){
		ArrayList<PrimitiveProperty<?>> list = new ArrayList<PrimitiveProperty<?>>();
		list.add(zScaling);
		list.add(createMesh);
		list.add(ignoreZeros);
		return list;
	}
		
	private static JKeyenceMeshControlPanel dataPanel = null;
	
	@Override
	public String getName() {
		return "Keyence 3D-Image";
	}
	
	@Override
	public AtomData readInputData(File f, AtomData previous, Filter<Atom> atomFilter) throws Exception {
	ImportDataContainer idc = new ImportDataContainer();
		
		idc.name = f.getName();

			
		BufferedImage im3d = ImageIO.read(f);
		
		File file2D;
		
		if (f.getName().endsWith("D1.jpg") || f.getName().endsWith("D1.bmp")) {
			file2D = new File(f.getAbsolutePath().replace("D1", "B1"));
			if (!file2D.exists())
				file2D = new File(f.getAbsolutePath().replace("D1", "B1").replace(".bmp", ".jpg"));
		} else { 
			file2D = new File(f.getAbsolutePath().replace("_3D", "_2D"));
			if (!file2D.exists())
				file2D = new File(f.getAbsolutePath().replace("_3D", "_2D").replace(".bmp", ".jpg"));
		}
		
		BufferedImage im2d = ImageIO.read(file2D);
		
		boolean compressed = f.getName().endsWith(".jpg");
		
		boolean mixedResolution = im3d.getWidth() != im2d.getWidth();
			
			
		int width3D = im3d.getWidth();
		int height3D = im3d.getHeight();
		
		int width2D = im2d.getWidth();
		int height2D = im2d.getHeight();
		
		idc.boxSizeX.setTo(new Vec3(width3D*(compressed?2:1), 0, 0));
		idc.boxSizeY.setTo(new Vec3(0, height3D*(compressed?2:1), 0));
		idc.boxSizeZ.setTo(new Vec3(0, 0, 256));
		
		idc.pbc[0] = false;
		idc.pbc[1] = false;
		idc.pbc[2] = false;
		
		idc.makeBox();
		
		int[] dataColumns = new int[ImportConfiguration.getInstance().getDataColumns().size()];
		
		int depthCol = -1;
		int imageCol = -1;
		
		for (int i = 0; i<dataColumns.length; i++) {
			if (ImportConfiguration.getInstance().getDataColumns().get(i).getId().equals("Depth"))
				depthCol = i;
			if (ImportConfiguration.getInstance().getDataColumns().get(i).getId().equals("Image"))
				imageCol = i;
		}
		
		
		if (mixedResolution) {
			//Uncompressed full res bmp
			for (int x = 0; x<width2D;x++) {
				for (int y = 0; y<height2D;y++) {
					
					int z = im3d.getRaster().getSample(x/2, y/2, 0);
					
					if (z>0 || !ignoreZeros.getValue()) {
						Vec3 pos = new Vec3();
						pos.x = x;
						pos.y = y;
						pos.z = z*zScaling.getValue();
						//pos.z = g;
	
						Atom a = new Atom(pos, x*height2D+y, (byte)0);
						
						idc.atoms.add(a);
					
						
						if (imageCol != -1)
							idc.dataArrays.get(imageCol).add(im2d.getRaster().getSample(x, y, 0)/255f);
						if (depthCol != -1)
							idc.dataArrays.get(depthCol).add(pos.z/255f);
						
					}
				}
			}
		} else {
			if (compressed) {
				//reduced quality and resolution
				for (int x = 0; x<width3D;x++) {
					for (int y = 0; y<height3D;y++) {
						int z = im3d.getRaster().getSample(x, y, 0);
						
						if (z>0 || !ignoreZeros.getValue()) {
							Vec3 pos = new Vec3();
							pos.x = x*2f;
							pos.y = y*2f;
							pos.z = z*zScaling.getValue();
	
//							Atom a = new Atom(pos, x*2*width3D*2+y*2, (byte)0);
							Atom a = new Atom(pos, x*height3D+y, (byte)0);
							
							idc.atoms.add(a);
						
							
							if (imageCol != -1)
								idc.dataArrays.get(imageCol).add(im2d.getRaster().getSample(x, y, 0)/255f);
							if (depthCol != -1)
								idc.dataArrays.get(depthCol).add(pos.z/255f);
							
						}
					}
				}
			} else {
				//Uncompressed full res bmp
				for (int x = 0; x<width3D;x++) {
					for (int y = 0; y<height3D;y++) {
						int r = im3d.getRaster().getSample(x, y, 0);	// only 3 bits used
						int g = im3d.getRaster().getSample(x, y, 1);	// all 8 bits used
						int b = im3d.getRaster().getSample(x, y, 2);	// only 4 bits used
						
						int z = (g & 0xff)<<7 | (r & 0xff)<<4 | (b & 0xff);
						
						//int z = g<<7 | r<<3 | b;
						
						if (z>0 || !ignoreZeros.getValue()) {
							Vec3 pos = new Vec3();
							pos.x = x;
							pos.y = y;
							pos.z = z*zScaling.getValue()/128;
							//pos.z = g;
		
							Atom a = new Atom(pos, x*height3D+y, (byte)0);
							
							idc.atoms.add(a);
						
							
							if (imageCol != -1)
								idc.dataArrays.get(imageCol).add(im2d.getRaster().getSample(x, y, 0)/255f);
							if (depthCol != -1)
								idc.dataArrays.get(depthCol).add(pos.z/255f);
							
						}
					}
				}
			
			}
		}
		
		//Add the names of the elements to the input
		idc.elementNames.put(1, "Pixel");
		
		idc.maxElementNumber = (byte)1;
		
		AtomData data = new AtomData(previous, idc);
		if (createMesh.getValue())
			data.applyProcessingModule(new KeyenceMeshModule(compressed & !mixedResolution));
		return data;
	}

	
	
	@Override
	public FileFilter getDefaultFileFilter() {
		FileFilter keyenceFileFilterBasic = new FileFilter() {
			@Override
			public String getDescription() {
				return "(ext)keyence file (*_3D.bmp | *_3D.jpg)";
			}
			
			@Override
			public boolean accept(File f) {
				if (f.isDirectory()) return true;
				String name = f.getName();
				if (name.endsWith("_3D.bmp") || name.endsWith("_3D.jpg") || name.endsWith("D1.bmp") || name.endsWith("D1.jpg")){
					return true;
				}
				return false;
			}
		};
		return keyenceFileFilterBasic;
	}
	
	
	@Override
	public String[][] getColumnNamesUnitsFromHeader(File f) throws IOException {		
		return new String[][] {{"Depth","-"}, {"Image","-"}};
	}
	
	@Override
	public Map<String, Component> getDefaultNamesForComponents() {
		HashMap<String, Component> map = new HashMap<String, Component>();
		
		return map;
	}

	public static class JKeyenceMeshControlPanel  extends JDataPanel {
		private static final long serialVersionUID = 1L;
		private JCheckBox showSurfaceCheckbox = new JCheckBox("Show surface", false);
		private JCheckBox showMeshCheckbox = new JCheckBox("Show mesh", false);
		private JSlider transparencySlider = new JSlider(0, 100, 0);
		
		float[] color = new float[]{0.5f, 0.5f, 0.5f};
		boolean showMesh = false;
		
		private JColorSelectPanel colorPanel = new JColorSelectPanel(color, new Color(color[0], color[1], color[2]));
		
		float transparency = 1f;
		
		public JKeyenceMeshControlPanel() {
			super("Approx. surface");
			
			this.setLayout(new GridBagLayout());
			
			GridBagConstraints gbc = new GridBagConstraints();
			
			gbc.anchor = GridBagConstraints.WEST;
			gbc.weightx = 1;
			gbc.fill = GridBagConstraints.BOTH;
			gbc.gridy = 0; gbc.gridx = 0;
			gbc.gridwidth = 2;
			this.add(showSurfaceCheckbox, gbc);
			showMeshCheckbox.setEnabled(showSurfaceCheckbox.isSelected());
			
			gbc.gridwidth = 1;
			gbc.gridx = 0; gbc.gridy++;
			this.add(new JLabel("Color"), gbc); gbc.gridx++;
			this.add(colorPanel, gbc);
			
			gbc.gridwidth = 2;
			gbc.gridx = 0; gbc.gridy++;
			this.add(new JLabel("Transparency"), gbc);
			gbc.gridy++;
			this.add(transparencySlider, gbc);
			gbc.gridy++;
			this.add(showMeshCheckbox, gbc);

			showMeshCheckbox.addActionListener(e-> {
				showMesh = showMeshCheckbox.isSelected();
				RenderingConfiguration.getViewer().reDraw();
			});
			
			showSurfaceCheckbox.addActionListener(e-> {
				showMeshCheckbox.setEnabled(showSurfaceCheckbox.isSelected());
				RenderingConfiguration.getViewer().reDraw();
			});
			
			transparencySlider.addChangeListener(e-> {
				transparency = 1-(transparencySlider.getValue()*0.01f);
				RenderingConfiguration.getViewer().reDraw();
			});
		}

		@Override
		public void setViewer(ViewerGLJPanel viewer) {}
		
		@Override
		public void update(DataContainer dc) {}

		@Override
		public boolean isDataVisible() {
			return showSurfaceCheckbox.isSelected();
		}
	}
	
	private class KeyenceMeshModule extends ClonableProcessingModule{
		
		private boolean compressed = false;
		
		public KeyenceMeshModule(boolean compressed) {
			this.compressed = compressed;
		}
		
		@Override
		public String getShortName() {
			return "Keyence Mesh";
		}

		@Override
		public String getFunctionDescription() {
			return "Creates the 3D mesh from keyence files";
		}

		@Override
		public String getRequirementDescription() {
			return "";
		}

		@Override
		public boolean isApplicable(AtomData data) {
			return true;
		}

		@Override
		public boolean canBeAppliedToMultipleFilesAtOnce() {
			return true;
		}

		@Override
		public boolean showConfigurationDialog(JFrame frame, AtomData data) {
			return false;
		}

		@Override
		public DataColumnInfo[] getDataColumnsInfo() {
			return null;
		}

		@Override
		public ProcessingResult process(AtomData data) throws Exception {
			
			int imageCol = -1;
			
			for (int i = 0; i<data.getDataColumnInfos().size(); i++) {
				if (data.getDataColumnInfos().get(i).getId().equals("Image"))
					imageCol = i;
			}
			
			
			int pixelCount = 0;
			
			int width = (int)data.getBox().getBoxSize()[0].x;
			int height = (int)data.getBox().getBoxSize()[1].y;
			
			if (compressed) {
				width/=2;
				height/=2;
			}
			
			 			
			
			Atom[] orderedPixels = new Atom[width*height];
			int[] pixelIndex = new int[width*height];
			Arrays.fill(pixelIndex, -1);
			
			for (Atom a : data.getAtoms()) {
				orderedPixels[a.getNumber()] = a;
				
				pixelIndex[a.getNumber()] = pixelCount++;
			}
			
			//Build vertex array and color array
			float[] vertexArray = new float[pixelCount*3];
			float[] colorArray = new float[pixelCount*4];
			
			for (int i=0; i<pixelIndex.length; i++) {
				int idx = pixelIndex[i];
				if (idx!=-1) {
					vertexArray[idx*3+0] = orderedPixels[i].x;
					vertexArray[idx*3+1] = orderedPixels[i].y;
					vertexArray[idx*3+2] = orderedPixels[i].z;
					
					//gray scale, all color channels are equals
//					float c = orderedPixels[pixelIndex[i]].getData(imageCol, data);
					float c = orderedPixels[i].getData(imageCol, data);
					colorArray[idx*4+0] = c;
					colorArray[idx*4+1] = c;
					colorArray[idx*4+2] = c;
					colorArray[idx*4+3] = 1;
				}
			}
				
			ArrayList<Integer> triangleIndices = new ArrayList<Integer>(); 
			
			for (int x = 0; x<width-1; x++) {
				for (int y = 0; y<height-1; y++) {
						
				if (orderedPixels[x*height+y] != null && orderedPixels[(x+1)*height+y] != null
						&& orderedPixels[x*height+(y+1)] != null && orderedPixels[(x+1)*height+(y+1)] != null) {

						triangleIndices.add(pixelIndex[x*height+y]*3);
						triangleIndices.add(pixelIndex[(x+1)*height+y]*3);
						triangleIndices.add(pixelIndex[(x+1)*height+(y+1)]*3);
						
						triangleIndices.add(pixelIndex[(x+1)*height+(y+1)]*3);
						triangleIndices.add(pixelIndex[x*height+(y+1)]*3);
						triangleIndices.add(pixelIndex[x*height+y]*3);
					}
					
				}
			}
			
			int[] tia = new int[triangleIndices.size()];
			for (int i=0; i<triangleIndices.size(); i++)
				tia[i] = triangleIndices.get(i);
			
			FinalMesh mesh = new FinalMesh(tia, vertexArray, colorArray);
			KeyenceMesh km = new KeyenceMesh(mesh);
			return new DataContainer.DefaultDataContainerProcessingResult(km, "");
		}
		
		
		
		
		private class KeyenceMesh extends DataContainer{
			private FinalMesh mesh;
			
			public KeyenceMesh(FinalMesh mesh) {
				this.mesh = mesh;
			}
			
			@Override
			public boolean isTransparenceRenderingRequired() {
				return true;
			}

			@Override
			public void drawSolidObjects(ViewerGLJPanel viewer, GL3 gl, RenderRange renderRange, boolean picking, AtomData data) {
				getDataControlPanel(); //Make sure that singleton is created to prevent null-pointer
				if (dataPanel.transparency>=0.99f) drawMesh(viewer, gl, renderRange, picking, data, false);
			}
			
			@Override
			public void drawTransparentObjects(ViewerGLJPanel viewer, GL3 gl, RenderRange renderRange, boolean picking, AtomData data) {
				getDataControlPanel();
				if (dataPanel.transparency<0.99f && dataPanel.transparency>0.02f)
					drawMesh(viewer, gl, renderRange, picking, data, true);
			}
			
			private void drawMesh(ViewerGLJPanel viewer, GL3 gl, RenderRange renderRange, boolean picking, AtomData data, boolean transparencyRendering){
				if (!getDataControlPanel().isDataVisible()) return;
				
				if (picking)
					return;
				
				Shader s = BuiltInShader.VERTEX_COLOR_DEFERRED.getShader();
				if (transparencyRendering && !picking)
					s = BuiltInShader.OID_ADS_VERTEX_COLOR.getShader();
				
				s.enableAndPushOld(gl);
				
				int colorUniform = gl.glGetUniformLocation(s.getProgram(), "Color");
				gl.glUniform4f(colorUniform, dataPanel.color[0], dataPanel.color[1], dataPanel.color[2], dataPanel.transparency);
				
				
				if (picking){
					gl.glUniform4f(colorUniform, 0, 0, 0, 0);
				}
				gl.glDisable(GL.GL_CULL_FACE);
				mesh.renderMesh(gl);
				gl.glEnable(GL.GL_CULL_FACE);
				
				Shader.popAndEnableShader(gl);
				
				
				
				s = BuiltInShader.UNIFORM_COLOR_DEFERRED.getShader();
				if (transparencyRendering && !picking)
					s = BuiltInShader.OID_ADS_UNIFORM_COLOR.getShader();
				
				s.enableAndPushOld(gl);
				
				if (dataPanel.showMesh && !picking){
					gl.glUniform4f(colorUniform, 0f, 0f, 0f, dataPanel.transparency);
					
					gl.glEnable(GL3.GL_POLYGON_OFFSET_LINE);
					gl.glPolygonOffset(0f, -500f);
					gl.glPolygonMode(GL3.GL_FRONT_AND_BACK, GL3.GL_LINE);
					
					mesh.renderMesh(gl);
				
					gl.glPolygonMode(GL3.GL_FRONT_AND_BACK, GL3.GL_FILL);
					gl.glPolygonOffset(0f, 0f);
					gl.glDisable(GL3.GL_POLYGON_OFFSET_LINE);
				}
				
				Shader.popAndEnableShader(gl);
			}
			
			
			public JDataPanel getDataControlPanel() {
				if (dataPanel == null)
					dataPanel = new JKeyenceMeshControlPanel();
				return dataPanel;
			}
			
		}
		
	}
	
}
