package processingModules.otherModules;

import gui.PrimitiveProperty.*;
import gui.glUtils.Shader.BuiltInShader;
import gui.glUtils.Shader;
import gui.JColorSelectPanel;
import gui.JPrimitiveVariablesPropertiesDialog;
import gui.ProgressMonitor;
import gui.RenderRange;
import gui.ViewerGLJPanel;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.concurrent.Callable;

import com.jogamp.opengl.GL3;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import common.ThreadPool;
import model.*;
import model.mesh.Mesh;
import model.polygrain.grainDetection.*;
import processingModules.ClonableProcessingModule;
import processingModules.DataContainer;
import processingModules.JDataPanel;
import processingModules.ProcessingResult;
import processingModules.toolchain.Toolchainable.ExportableValue;
import processingModules.toolchain.Toolchainable.ToolchainSupport;

@ToolchainSupport
public class SurfaceApproximationModule extends ClonableProcessingModule {

	private static JSurfaceMeshControlPanel dataPanel = null;	
	
	@ExportableValue
	private float cellSize = 5f;
	@ExportableValue
	private float simplification = 3f;
	@ExportableValue
	private float offset = 3f;
	@ExportableValue
	private boolean smooth = true;
	@ExportableValue
	private boolean clusterAnalysis = false;
	@ExportableValue
	private int minAtomsPerCluster = 20;
	@ExportableValue
	private float clusterSearchRadius = 3;
	@ExportableValue
	private boolean filter = false;
	
	@Override
	public ProcessingResult process(AtomData data) throws Exception {
		SurfaceApproximationDataContainer dc = new SurfaceApproximationDataContainer();
		dc.processData(data);
		return new DataContainer.DefaultDataContainerProcessingResult(dc, "");
	}
	
	@Override
	public String getFunctionDescription() {
		return "Approximates the surface of a data set";
	}

	@Override
	public String getShortName() {
		return "Surface approximation";
	}
	
	@Override
	public boolean showConfigurationDialog(JFrame frame, AtomData data) {
		JPrimitiveVariablesPropertiesDialog dialog = new JPrimitiveVariablesPropertiesDialog(null, "Approximate surface");
		
		BooleanProperty allAtoms = dialog.addBoolean("allAtoms", "Create surface from visible atoms only", "", false);
		BooleanProperty smooth = dialog.addBoolean("smooth", "Smooth mesh", "", true);
		BooleanProperty reduceTriangles = dialog.addBoolean("reduceTriangle", "Reduce triangle count", "", false);
		FloatProperty initCellSize = dialog.addFloat("cellSize", "Initial sampling resolution"
				, "", 5f, 0f, 1000f);
		FloatProperty meshOffset = dialog.addFloat("offset", "Offset to atoms"
				, "", 2f, 0f, 1000f);
		
		dialog.add(new JSeparator());
		dialog.startGroup("Clustering");
		BooleanProperty clusterAtoms = dialog.addBoolean("cluster", "Identify cluster first", "", false);
		IntegerProperty minClusterSize = dialog.addInteger("clusterSize", "Minimum atoms per cluster", "", 20, 1, 10000000);
		FloatProperty clusterDetectionRadius = dialog.addFloat("clusterRadius", "Radius to find neighboring atoms during clustering", 
				"", data.getCrystalStructure().getNearestNeighborSearchRadius(), 0.001f, 100000f);
		dialog.endGroup();
		
		clusterAtoms.addDependentComponent(minClusterSize);
		clusterAtoms.addDependentComponent(clusterDetectionRadius);
		
		boolean ok = dialog.showDialog();
		if (ok){
			if (RenderingConfiguration.isHeadless()) this.filter = false;
			else this.filter = allAtoms.getValue();
			this.smooth = smooth.getValue();
			this.offset = meshOffset.getValue();
			this.cellSize = initCellSize.getValue();
			this.simplification = reduceTriangles.getValue() ? 3f : 0f;
			this.clusterAnalysis = clusterAtoms.getValue();
			this.clusterSearchRadius = clusterDetectionRadius.getValue();
			this.minAtomsPerCluster = minClusterSize.getValue();
		}
		return ok;
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
	public DataColumnInfo[] getDataColumnsInfo() {
		return null;
	}
	
	@Override
	public boolean canBeAppliedToMultipleFilesAtOnce() {
		return true;
	}
	
	
	public static class JSurfaceMeshControlPanel  extends JDataPanel {
		private static final long serialVersionUID = 1L;
		private JCheckBox showSurfaceCheckbox = new JCheckBox("Show surface", false);
		private JCheckBox showMeshCheckbox = new JCheckBox("Show mesh", false);
		private JSlider transparencySlider = new JSlider(0, 100, 0);
		
		float[] color = new float[]{0.5f, 0.5f, 0.5f};
		boolean showMesh = false;
		
		private JColorSelectPanel colorPanel = new JColorSelectPanel(color, new Color(color[0], color[1], color[2]));
		
		float transparency = 1f;
		
		public JSurfaceMeshControlPanel() {
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

			showMeshCheckbox.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					showMesh = showMeshCheckbox.isSelected();
					RenderingConfiguration.getViewer().reDraw();
				}
			});
			
			showSurfaceCheckbox.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					showMeshCheckbox.setEnabled(showSurfaceCheckbox.isSelected());
					RenderingConfiguration.getViewer().reDraw();
				}
			});
			
			transparencySlider.addChangeListener(new ChangeListener() {
				@Override
				public void stateChanged(ChangeEvent e) {
					transparency = 1-(transparencySlider.getValue()*0.01f);
					RenderingConfiguration.getViewer().reDraw();
				}
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
	
	private class SurfaceApproximationDataContainer extends DataContainer{
		private ArrayList<Mesh> meshes = new ArrayList<Mesh>();
		
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
			
			Shader s = BuiltInShader.UNIFORM_COLOR_DEFERRED.getShader();
			if (transparencyRendering && !picking)
				s = BuiltInShader.OID_ADS_UNIFORM_COLOR.getShader();
			
			s.enableAndPushOld(gl);
			
			int colorUniform = gl.glGetUniformLocation(s.getProgram(), "Color");
			gl.glUniform4f(colorUniform, dataPanel.color[0], dataPanel.color[1], dataPanel.color[2], dataPanel.transparency);
			
			for (Mesh mesh : meshes){
				if (picking){
					float[] c = viewer.getNextPickingColor(mesh);
					gl.glUniform4f(colorUniform, c[0], c[1], c[2], c[3]);
				}
				mesh.getFinalMesh().renderMesh(gl);
			}
				
			
			if (dataPanel.showMesh && !picking){
				gl.glUniform4f(colorUniform, 0f, 0f, 0f, dataPanel.transparency);
				
				gl.glEnable(GL3.GL_POLYGON_OFFSET_LINE);
				gl.glPolygonOffset(0f, -500f);
				gl.glPolygonMode(GL3.GL_FRONT_AND_BACK, GL3.GL_LINE);
				
				for (Mesh mesh : meshes)
					mesh.getFinalMesh().renderMesh(gl);
			
				gl.glPolygonMode(GL3.GL_FRONT_AND_BACK, GL3.GL_FILL);
				gl.glPolygonOffset(0f, 0f);
				gl.glDisable(GL3.GL_POLYGON_OFFSET_LINE);
			}
			
			Shader.popAndEnableShader(gl);
		}
		
		@Override
		public boolean isTransparenceRenderingRequired() {
			return true;
		}
		
		
		public boolean processData(AtomData atomData) throws Exception {
			FilterSet<Atom> afs = null;
			if (filter) afs = RenderingConfiguration.getAtomFilterset();
			List<Atom> atoms = atomData.getAtoms();
			if (afs != null){
				ArrayList<Atom> filteredAtoms = new ArrayList<Atom>();
				for (Atom a : atomData.getAtoms()){
					if (afs.accept(a))
						filteredAtoms.add(a);
				}
				atoms = filteredAtoms;
			}
			
			
			
			if (clusterAnalysis){
				
				GrainDetectionCriteria gdc = new GrainDetectionCriteria() {
					@Override
					public boolean includeAtom(AtomToGrainObject atom, List<AtomToGrainObject> neighbors) {
						return true;
					}
					@Override
					public boolean includeAtom(Atom atom) {
						return true;
					}
					@Override
					public float getNeighborDistance() {
						return clusterSearchRadius;
					}
					@Override
					public int getMinNumberOfAtoms() {
						return minAtomsPerCluster;
					}
					@Override
					public boolean acceptAsFirstAtomInGrain(Atom atom, List<AtomToGrainObject> neighbors) {
						return true;
					}
				};
				
				List<List<Atom>> clusters = GrainDetector.identifyGrains(atoms, gdc, atomData.getBox());
				for (List<Atom> l : clusters)
					this.meshes.add(new Mesh(l, cellSize, simplification, atomData.getBox()));
				
			} else {
				this.meshes.add(new Mesh(atoms, cellSize, simplification, atomData.getBox()));
			}
			
			Vector<Callable<Void>> parallelTasks = new Vector<Callable<Void>>();
			for (int i=0; i<meshes.size(); i++){
				final int j = i;
				parallelTasks.add(new Callable<Void>() {
					@Override
					public Void call() throws Exception {
						processMesh(meshes.get(j));
						return null;
					}
				});
			}
			
			int steps = meshes.size()*(11 + (smooth?10:3));
			ProgressMonitor.getProgressMonitor().start(steps);
			
			ThreadPool.executeParallel(parallelTasks);

			ProgressMonitor.getProgressMonitor().stop();
			return true;
		}
		
		private void processMesh(Mesh mesh){
			ProgressMonitor pg = ProgressMonitor.getProgressMonitor();

			mesh.createMesh(); 				pg.addToCounter(1);
			
			//Mesh postprocessing
			mesh.cornerPreservingSmooth();	pg.addToCounter(1);
			mesh.cornerPreservingSmooth();	pg.addToCounter(1);
			mesh.cornerPreservingSmooth();	pg.addToCounter(1);
			mesh.shrink(offset);			pg.addToCounter(1);
			mesh.cornerPreservingSmooth();	pg.addToCounter(1);
			mesh.shrink(offset);			pg.addToCounter(1);
			mesh.cornerPreservingSmooth();	pg.addToCounter(1);
			mesh.shrink(offset);			pg.addToCounter(1);
			for (int i=0; i<(smooth?10:3); i++){
				mesh.smooth();
				pg.addToCounter(1);
			}
		
			mesh.simplifyMesh(6f*(float)Math.pow(simplification,3.)); pg.addToCounter(1);
			
			mesh.finalizeMesh(); pg.addToCounter(1);
		}

		@Override
		public JDataPanel getDataControlPanel() {
			if (dataPanel == null)
				dataPanel = new JSurfaceMeshControlPanel();
			return dataPanel;
		}
	}
}
