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

package gui;

import gui.PrimitiveProperty.BooleanProperty;
import gui.PrimitiveProperty.FloatProperty;
import gui.ViewerGLJPanel.RenderOption;

import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.*;
import java.text.DecimalFormat;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLProfile;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

import processingModules.ProcessingModule;
import processingModules.toolchain.Toolchain;

import com.jogamp.opengl.JoglVersion;

import common.ColorTable;
import common.ColorTable.ColorBarScheme;
import common.CommonUtils;
import common.Vec3;
import crystalStructures.CrystalStructure;
import model.*;
import model.io.*;
import model.Configuration.AtomDataChangedEvent;
import model.Configuration.AtomDataChangedListener;
import model.ImportConfiguration.ImportStates;
import model.RenderingConfiguration.Options;

public class JMainWindow extends JFrame implements WindowListener, AtomDataChangedListener {

	public static final String VERSION = "3.0"; 
	public static String buildVersion = "";
	
	private static final long serialVersionUID = 1L;
	
	private final JAtomicMenuPanel atomicMenu;
	private final JMenu typeColorMenu;
	
	public JMainWindow() {
		//Set fonts
		for (Map.Entry<Object, Object> entry : javax.swing.UIManager.getDefaults().entrySet()) {
		    Object key = entry.getKey();
		    Object value = javax.swing.UIManager.get(key);
		    if (value != null && value instanceof javax.swing.plaf.FontUIResource) {
		        javax.swing.plaf.FontUIResource fr=(javax.swing.plaf.FontUIResource)value;
		        javax.swing.plaf.FontUIResource f = new javax.swing.plaf.FontUIResource(RenderingConfiguration.defaultFont, 
		        		fr.getStyle(), RenderingConfiguration.defaultFontSize);
		        javax.swing.UIManager.put(key, f);
		    }
		}
		
		//Load the icon
		try {
			final String resourcesPath = "resources/icon.png";
			ClassLoader l = this.getClass().getClassLoader();
			InputStream stream = l.getResourceAsStream(resourcesPath);
			if (stream!=null) this.setIconImage(ImageIO.read(stream));
		} catch (IOException e2) {}
		
		this.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		Configuration.addAtomDataListener(this);
		
		//Disable that ToolTips disappear automatically after short time
		ToolTipManager.sharedInstance().setDismissDelay(Integer.MAX_VALUE);
		
		GLProfile maxProfile = GLProfile.getMaxProgrammableCore(true);
		GLCapabilities glCapabilities = new GLCapabilities(maxProfile);
		
		ViewerGLJPanel viewer = new ViewerGLJPanel(650, 650, glCapabilities);
		viewer.setFocusable(true);
		viewer.requestFocus(); // the viewer now has focus, so receives key events
		this.setTitle("AtomViewer");
		
		JPanel renderPane = new JPanel();
		renderPane.setLayout(new BorderLayout());
		renderPane.add(viewer,BorderLayout.CENTER);
		
		final JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
		splitPane.add(renderPane);
		
		//Combine view buttons and the log panel into a container 
		JPanel cont = new JPanel();
		cont.setLayout(new GridBagLayout());
		GridBagConstraints gbc = CommonUtils.getBasicGridBagConstraint();
		gbc.weightx = 10;
		cont.add(JLogPanel.getJLogPanel(),gbc);
		gbc.gridx++; gbc.weightx = 0.2;
		cont.add(new DefaultPerspectivesButtonPanel(),gbc);
		
		splitPane.add(cont);
		splitPane.setDividerLocation(0.9);
		
		splitPane.addComponentListener(new ComponentAdapter() {
			public void componentResized(ComponentEvent evt) {
				splitPane.setDividerLocation(0.9);
			}
		});
		
		splitPane.getActionMap().setParent(this.getRootPane().getActionMap());
		splitPane.getInputMap().setParent(this.getRootPane().getInputMap());
		
		this.atomicMenu = new JAtomicMenuPanel(this);
		this.add(atomicMenu, BorderLayout.WEST);
		
		this.add(splitPane, BorderLayout.CENTER);
		addWindowListener(this);
		
		
		JMenuBar menu = new JMenuBar();
		JMenu fileMenu = new JMenu("File");
		JMenuItem openFileMenuItem = new JMenuItem("Open file");
		openFileMenuItem.addActionListener(new OpenMenuListener());
		fileMenu.add(openFileMenuItem);
		
		JMenuItem exportScreenShotMenuItem = new JMenuItem("Save Screenshot");
		exportScreenShotMenuItem.setActionCommand("Screenshot");
		exportScreenShotMenuItem.addActionListener(new ExportScreenshotActionListener());
		fileMenu.add(exportScreenShotMenuItem);
		
		JMenuItem exportAsciiFile = new JMenuItem("Export as IMD-Checkpoint");
		exportAsciiFile.addActionListener(new ExportFileActionListener());
		fileMenu.add(exportAsciiFile);	
		
		final JMenuItem exportMarksFileMenuItem = new JMenuItem("Export defect marking");
		exportMarksFileMenuItem.addActionListener(new ExportMarkersActionListener());
		fileMenu.add(exportMarksFileMenuItem);
		
		
		fileMenu.add(new JSeparator());
		JMenuItem exitMenuItem = new JMenuItem("Exit");
		exitMenuItem.addActionListener(l->System.exit(0));
		fileMenu.add(exitMenuItem);
		
		menu.add(fileMenu);
		
		JMenu viewMenu = new JMenu("View");
		
		JMenuItem editRangeMenuItem = new JMenuItem("Edit visible range");
		editRangeMenuItem.addActionListener(l->{
			if (Configuration.getCurrentAtomData() != null){
				RenderRange rr = RenderingConfiguration.getViewer().getRenderRange();
				new JRenderedIntervalEditorDialog(JMainWindow.this, rr);
				RenderingConfiguration.getViewer().updateAtoms();
			}			
		});
		
		final JCheckBoxMenuItem drawLegendMenuItem = new JCheckBoxMenuItem("Show legends");
		drawLegendMenuItem.setSelected(RenderOption.LEGEND.isEnabled());
		drawLegendMenuItem.addActionListener(l -> RenderOption.LEGEND.setEnabled(drawLegendMenuItem.isSelected()) );
		
		JMenuItem changeSphereSizeMenuItem = new JMenuItem("Change sphere size");
		changeSphereSizeMenuItem.addActionListener(new JChangeSphereSizeActionListener());
		
		JMenuItem switchMarkingMode = new JMenuItem("Switch Defect Marking Mode");
		switchMarkingMode.addActionListener(l -> viewer.switchMarkingDefectsMode());
		JMenuItem deleteMarkingMode = new JMenuItem("Remove Defect Marking");
		deleteMarkingMode.addActionListener(l -> viewer.switchToMarkingDefectsDeleteMode());
		
		JGraphicOptionCheckBoxMenuItem perspectiveCheckBoxMenu = 
				new JGraphicOptionCheckBoxMenuItem("Perspective projection", RenderOption.PERSPECTIVE, 
						"Enable perspective projection instead of orthogonal");
		JGraphicOptionCheckBoxMenuItem stereoCheckBoxMenu = 
				new JGraphicOptionCheckBoxMenuItem("Anaglyphic stereo", RenderOption.STEREO, "");
		JGraphicOptionCheckBoxMenuItem whiteBackgroundCheckBoxMenu = 
				new JGraphicOptionCheckBoxMenuItem("White background", RenderOption.PRINTING_MODE, "");
		JGraphicOptionCheckBoxMenuItem drawBoundingBoxCheckBoxMenu = 
				new JGraphicOptionCheckBoxMenuItem("Bounding box", RenderOption.BOUNDING_BOX, "");
		JGraphicOptionCheckBoxMenuItem drawTetraederCheckBoxMenu = 
				new JGraphicOptionCheckBoxMenuItem("Thompson Tetraeder", RenderOption.THOMPSON_TETRAEDER, "");
		JGraphicOptionCheckBoxMenuItem drawLengthScaleBoxMenu = 
				new JGraphicOptionCheckBoxMenuItem("Length scale", RenderOption.LENGTH_SCALE, 
						"A lenght scale bar is only possible in othogonal projection, but not in perspective projection");
		JGraphicOptionCheckBoxMenuItem drawIndentBoxMenu = 
				new JGraphicOptionCheckBoxMenuItem("Indenter (if available)", RenderOption.INDENTER, "");
		JGraphicOptionCheckBoxMenuItem drawCoordinateSystemBoxMenu = 
				new JGraphicOptionCheckBoxMenuItem("Coordinate System", RenderOption.COORDINATE_SYSTEM, "");
		JGraphicOptionCheckBoxMenuItem drawMarkerCheckBoxMenu = 
				new JGraphicOptionCheckBoxMenuItem("Show markers", RenderOption.MARKER, "");
		
		viewMenu.add(editRangeMenuItem);
		viewMenu.add(changeSphereSizeMenuItem);
		viewMenu.add(switchMarkingMode);
		viewMenu.add(deleteMarkingMode);
		viewMenu.add(stereoCheckBoxMenu);
		viewMenu.add(perspectiveCheckBoxMenu);
		viewMenu.add(whiteBackgroundCheckBoxMenu);
		viewMenu.add(drawBoundingBoxCheckBoxMenu);
		viewMenu.add(drawCoordinateSystemBoxMenu);
		viewMenu.add(drawLengthScaleBoxMenu);
		viewMenu.add(drawTetraederCheckBoxMenu);
		viewMenu.add(drawIndentBoxMenu);
		viewMenu.add(drawLegendMenuItem);
		viewMenu.add(drawMarkerCheckBoxMenu);
		
		JMenu legendStyleMenu = new JMenu("Legend style");
		final JCheckBoxMenuItem swapLegend = new JCheckBoxMenuItem("Swap colors");
		swapLegend.setSelected(ColorTable.isColorBarSwapped());
		swapLegend.addActionListener(l-> {
			ColorTable.setColorBarSwapped(swapLegend.isSelected());
			RenderingConfiguration.getViewer().updateAtoms();
			RenderingConfiguration.saveProperties();
		});
		legendStyleMenu.add(swapLegend);
		
		ButtonGroup colorSchemeButtonGroup = new ButtonGroup();
		for (final ColorBarScheme scheme : ColorTable.ColorBarScheme.values()){
			if (scheme.isGeneralScheme()){
				final JRadioButtonMenuItem item = new JRadioButtonMenuItem(scheme.toString());
				colorSchemeButtonGroup.add(item);
				item.setSelected(scheme == ColorTable.getColorBarScheme());
				legendStyleMenu.add(item);
				item.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent arg0) {
						ColorTable.setColorBarScheme(scheme);
						RenderingConfiguration.saveProperties();
						RenderingConfiguration.getViewer().updateAtoms();
					}
				});
			}
		}
		viewMenu.add(legendStyleMenu);
		
		menu.add(viewMenu);
		
		typeColorMenu = new JMenu("Atom type colors");
		JMenuItem resetColorMenuItem = new JMenuItem("Reset colors of atom types");
		resetColorMenuItem.addActionListener(l-> {
			CrystalStructure c = Configuration.getCurrentAtomData().getCrystalStructure();
			if (c!=null) {
				c.resetColors();
				atomicMenu.updateValues();
				RenderingConfiguration.getViewer().updateAtoms();
			}
		});
		typeColorMenu.add(resetColorMenuItem);
		
		JMenuItem saveColorMenuItem = new JMenuItem("Save colors of atom types");
		saveColorMenuItem.addActionListener(l-> {
			try {
				JFileChooser chooser = new JFileChooser();
				chooser.setFileFilter(new FileNameExtensionFilter("Color schemes (*.color)", "color"));
				int result = chooser.showSaveDialog(JMainWindow.this);
				if (result == JFileChooser.APPROVE_OPTION){
					File f = chooser.getSelectedFile();
					if(!f.getAbsolutePath().endsWith(".color")){
					    f = new File(f.getAbsolutePath() + ".color");
					}
					CrystalStructure cs = Configuration.getCurrentAtomData().getCrystalStructure();
					int numCol = cs.getNumberOfTypes();
					float[][] currentColors = new float[numCol][];
					for (int i=0; i<numCol; i++)
						currentColors[i] = cs.getGLColor(i);
					ColorTable.saveColorsToFile(f, currentColors);
				}
			} catch (IOException e1) {
				JOptionPane.showMessageDialog(JMainWindow.this, e1.getMessage());
			}
		});
		typeColorMenu.add(saveColorMenuItem);
		
		JMenuItem loadColorMenuItem = new JMenuItem("Load colors of atom types");
		loadColorMenuItem.addActionListener(l -> {
			try {
				JFileChooser chooser = new JFileChooser();
				chooser.setFileFilter(new FileNameExtensionFilter("Color schemes (*.color)", "color"));
				int result = chooser.showOpenDialog(JMainWindow.this);
				if (result == JFileChooser.APPROVE_OPTION){
					float[][] colors = ColorTable.loadColorsFromFile(chooser.getSelectedFile());
					CrystalStructure cs = Configuration.getCurrentAtomData().getCrystalStructure();
					for (int i = 0; i<colors.length && i<cs.getNumberOfTypes(); i++){
						cs.setGLColors(i, colors[i]);
					}
					
					atomicMenu.updateValues();
					RenderingConfiguration.getViewer().updateAtoms();
				}
			} catch (IOException e1) {
				JOptionPane.showMessageDialog(JMainWindow.this, e1.getMessage());
			}
		});
		typeColorMenu.add(loadColorMenuItem);
		
		viewMenu.add(typeColorMenu);
		typeColorMenu.setEnabled(false);
		
		final JMenu processingMenu = new JMenu("Analysis");
		
		JMenuItem analysisModulesMenu = new JMenuItem("Add analyis");
		analysisModulesMenu.setActionCommand("analysis");
		
		ActionListener processingActionListener = new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				AtomData data = Configuration.getCurrentAtomData();
				if (data != null){
					JProcessingModuleDialog dialog = new JProcessingModuleDialog(JMainWindow.this);
					
					JProcessingModuleDialog.SelectedState ok = dialog.showDialog();
					ProcessingModule pm = dialog.getSelectedProcessingModule();
					if (ok != JProcessingModuleDialog.SelectedState.CANCEL && pm!=null){
						boolean multipleFiles = (ok == JProcessingModuleDialog.SelectedState.ALL_FILES);
						
						boolean possible = pm.showConfigurationDialog(JMainWindow.this, data);
						List<AtomData> toProcess = new ArrayList<AtomData>();
						if (possible){
							while (multipleFiles && data.getPrevious() != null)
								data = data.getPrevious();
							
							do {
								if (pm.isApplicable(data))
									toProcess.add(data);
								if (multipleFiles) data = data.getNext();
							} while (multipleFiles && data != null);
							
							applyProcessWindowWithDisplay(toProcess, pm);
							
							Configuration.setCurrentAtomData(Configuration.getCurrentAtomData(), true, false);
						}
					}
				}
			}

		};
		analysisModulesMenu.addActionListener(processingActionListener);
		processingMenu.add(analysisModulesMenu);
		
		JMenuItem saveToolchainMenuItem = new JMenuItem("Save toolchain of data set");
		saveToolchainMenuItem.addActionListener(l -> {
			JFileChooser chooser = new JFileChooser();
			chooser.setFileFilter(new FileNameExtensionFilter("Toolchainfile (*.tcf)","tcf"));
			int result = chooser.showSaveDialog(JMainWindow.this);
			if (result == JFileChooser.APPROVE_OPTION){				
				String filename = chooser.getSelectedFile().toString();
				if (!filename.endsWith(".tcf"))
					filename += ".tcf";
				File file = new File(filename);

				try (FileOutputStream f = new FileOutputStream(file)){
					Configuration.getCurrentAtomData().getToolchain().saveToolchain(f);
				} catch (Exception e1) {
					e1.printStackTrace();
				}
			}
		});
		
		JMenuItem applyToolchainMenuItem = new JMenuItem("Apply toolchain file");
		applyToolchainMenuItem.addActionListener(l -> {
			try {
				JFileChooser chooser = new JFileChooser();
				chooser.setFileFilter(new FileNameExtensionFilter("Toolchainfile (*.tcf)","tcf"));
				int result = chooser.showOpenDialog(JMainWindow.this);
				if (result == JFileChooser.APPROVE_OPTION){
					File file = chooser.getSelectedFile();
					try (FileInputStream f = new FileInputStream(file)){
						Toolchain tc = Toolchain.readToolchain(f);
						
						for (ProcessingModule pm : tc.getProcessingModules()){
							applyProcessWindowWithDisplay(Configuration.getCurrentAtomData(), pm.clone());
						}
						Configuration.setCurrentAtomData(Configuration.getCurrentAtomData(), true, false);
						JLogPanel.getJLogPanel().addInfo("Applied toolchain",
								String.format("Applied toolchain %s", file.getName()));
					}
				}
				
				
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		});
		
		JMenuItem applyToAllToolchainMenuItem = new JMenuItem("Apply toolchain file to all data sets");
		applyToAllToolchainMenuItem.addActionListener(l -> {
			try {
				JFileChooser chooser = new JFileChooser();
				chooser.setFileFilter(new FileNameExtensionFilter("Toolchainfile (*.tcf)","tcf"));
				int result = chooser.showOpenDialog(JMainWindow.this);
				if (result == JFileChooser.APPROVE_OPTION){
					File file = chooser.getSelectedFile();
					try (FileInputStream f = new FileInputStream(file)){
						Toolchain tc = Toolchain.readToolchain(f);
						AtomData def = Configuration.getCurrentAtomData();
						//Apply each processing module onto every AtomData instance one by one
						//If a processing module requires another instance of AtomData
						//as a reference, this enforces that the data is available even
						//if it generated by a previous module in the toolchain
						for (ProcessingModule pm : tc.getProcessingModules()){
							for (AtomData d : Configuration.getAtomDataIterable()){
								ProcessingModule pmc = pm.clone();
								applyProcessWindowWithDisplay(d, pmc);
							}
						}
						Configuration.setCurrentAtomData(def, true, false);
						JLogPanel.getJLogPanel().addInfo("Applied toolchain",
								String.format("Applied toolchain %s", file.getName()));
					}
				}
				
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		});
		
		final JMenuItem toolchainMenu = new JMenu("Toolchain (experimental)");
		toolchainMenu.add(saveToolchainMenuItem);
		toolchainMenu.add(applyToolchainMenuItem);
		toolchainMenu.add(applyToAllToolchainMenuItem);
		processingMenu.add(toolchainMenu);
	
		menu.add(processingMenu);
		
		final JMenuItem settingsMenu = new JMenu("Settings");
		for (Options o : RenderingConfiguration.Options.values()){
			settingsMenu.add(new JOptionCheckBoxMenuItem(o));
		}
		
		JMenuItem exportPOVMenuItem = new JMenuItem("Export POV");
		exportPOVMenuItem.setToolTipText("This option provides a string that can be used to restore the current point of view");
		exportPOVMenuItem.addActionListener(l -> {
			float[] m = RenderingConfiguration.getViewer().getPov();
			String s = "";
			for (int i=0; i<m.length; i++)
				s += m[i]+";";
			String message = "This string represents the current point of view. To reproduce images later, please store this string."; 
			JOptionPane.showInputDialog(JMainWindow.this, message, "Export POV", JOptionPane.PLAIN_MESSAGE, null, null, s);			
		});
		
		JMenuItem importPOVMenuItem = new JMenuItem("Import POV");
		importPOVMenuItem.setToolTipText("Restore the point of view using a stored configuration");
		importPOVMenuItem.addActionListener(l -> {
			String input = JOptionPane.showInputDialog(
					JMainWindow.this, "Please insert the point of view", "Import POV",  JOptionPane.PLAIN_MESSAGE);
			if (input != null && !input.isEmpty()){
				try {
				String[] split = input.trim().split(";");
				float[] m = new float[split.length];
				for (int i=0; i<split.length; i++)
					m[i] = Float.parseFloat(split[i]);
				RenderingConfiguration.getViewer().setPOV(m);
				} catch (Exception ex){}
			}
		});
		settingsMenu.add(exportPOVMenuItem);
		settingsMenu.add(importPOVMenuItem);
		
		
		final JMenuItem selectFontMenuItem = new JMenuItem("Select font");
		selectFontMenuItem.addActionListener(l -> {
			JPrimitiveVariablesPropertiesDialog dialog = new JPrimitiveVariablesPropertiesDialog(JMainWindow.this, "Select font");
			JFontChooser fc = new JFontChooser();
			JPanel panel = new JPanel(new GridLayout(1,1));
			panel.add(fc);
			dialog.addComponent(panel);
			boolean ok = dialog.showDialog();
			if (ok){
				RenderingConfiguration.defaultFont = fc.getSelectedFontFamily();
				RenderingConfiguration.defaultFontStyle = fc.getSelectedFontStyle();
				RenderingConfiguration.defaultFontSize = fc.getSelectedFontSize();
				RenderingConfiguration.saveProperties();
				JOptionPane.showMessageDialog(JMainWindow.this, "AtomViewer must be restarted to change the font", "AtomViewer", JOptionPane.INFORMATION_MESSAGE);
			}
		});
		settingsMenu.add(selectFontMenuItem);
		menu.add(settingsMenu);
		
		final JMenuItem helpMenu = new JMenu("Help");
		final JMenuItem helpMenuItem = new JMenuItem("Help");
		helpMenuItem.addActionListener(l -> {
			String message = "<html><body>" +
			"Zoom, move and rotate:<br><br>" +
			"Press mouse button -> Rotate <br>"+
			"Press mouse button + Shift-> Rotate around axis<br>"+
			"Press mouse button + Ctrl -> Move<br>"+
			"Press mouse button + Alt+Ctrl -> Zoom in/out<br>"+
			"Hold Shift+Ctrl+Click on object -> Focus on this object"+
			"</body></html>";
			JOptionPane.showMessageDialog(JMainWindow.this, message, "AtomViewer", JOptionPane.INFORMATION_MESSAGE);
		});
		final JMenuItem aboutMenuItem = new JMenuItem("About");
		aboutMenuItem.addActionListener(l -> {
			String message = "<html><body>" +
					"AtomViewer  Copyright (C) 2015, ICAMS, Ruhr-Universtät Bochum <br>" +
					"AtomViewer is a tool to display and analyse atomistic simulations<br><br>" +
					"AtomViewer Version "+JMainWindow.VERSION+" "+JMainWindow.buildVersion+"<br>"+
					"Available OpenGL version on this machine: "+ViewerGLJPanel.openGLVersion +"<br><br>" +
					"Using Jogl Version "+JoglVersion.getInstance().getSpecificationVersion()+"<br>"+
					"This program comes with ABSOLUTELY NO WARRANTY <br>" +
					"This is free software, and you are welcome to redistribute it under certain conditions.<br>"+
					"For details see the file COPYING which comes along with this program, or if not <br>" +
					"http://www.gnu.org/licenses/gpl.html <br>" +
					"</body></html>";
			JOptionPane.showMessageDialog(JMainWindow.this, message, "AtomViewer", JOptionPane.INFORMATION_MESSAGE);
		});
		helpMenu.add(helpMenuItem);
		helpMenu.add(aboutMenuItem);
		menu.add(helpMenu);
		
		this.setJMenuBar(menu);
		
		this.pack();
		this.setExtendedState(JFrame.MAXIMIZED_BOTH);
		
		//Hotkeys and accelerators 
		
		JRootPane rp = getRootPane();
		rp.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
			    KeyStroke.getKeyStroke(KeyEvent.VK_X,0), "next");
		rp.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
			    KeyStroke.getKeyStroke("pressed RIGHT"), "next");
		rp.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
			    KeyStroke.getKeyStroke("pressed KP_RIGHT"), "next");
		
		rp.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
			    KeyStroke.getKeyStroke("pressed LEFT"), "previous");
		rp.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
			    KeyStroke.getKeyStroke("pressed KP_LEFT"), "previous");
		rp.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
			    KeyStroke.getKeyStroke(KeyEvent.VK_Z,0), "previous");
		rp.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
			    KeyStroke.getKeyStroke(KeyEvent.VK_Y,0), "previous");
		
		rp.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
			    KeyStroke.getKeyStroke(KeyEvent.VK_L,0), "last");
		rp.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
			    KeyStroke.getKeyStroke("pressed KP_DOWN"), "last");
		rp.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
			    KeyStroke.getKeyStroke("pressed DOWN"), "last");
		rp.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
			    KeyStroke.getKeyStroke(KeyEvent.VK_F,0), "first");
		rp.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
			    KeyStroke.getKeyStroke("pressed KP_UP"), "first");
		rp.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
			    KeyStroke.getKeyStroke("pressed UP"), "first");
		
		stereoCheckBoxMenu.setMnemonic(KeyEvent.VK_A);
		stereoCheckBoxMenu.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_A, 0));
		changeSphereSizeMenuItem.setMnemonic(KeyEvent.VK_S);
		changeSphereSizeMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, 0));
		editRangeMenuItem.setMnemonic(KeyEvent.VK_R);
		editRangeMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, 0));
		drawLengthScaleBoxMenu.setMnemonic(KeyEvent.VK_E);
		drawLengthScaleBoxMenu.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_E, 0));
		perspectiveCheckBoxMenu.setMnemonic(KeyEvent.VK_P);
		perspectiveCheckBoxMenu.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_P, 0));
		whiteBackgroundCheckBoxMenu.setMnemonic(KeyEvent.VK_W);
		whiteBackgroundCheckBoxMenu.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_W, 0));
		drawCoordinateSystemBoxMenu.setMnemonic(KeyEvent.VK_C);
		drawCoordinateSystemBoxMenu.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, 0));
		drawBoundingBoxCheckBoxMenu.setMnemonic(KeyEvent.VK_B);
		drawBoundingBoxCheckBoxMenu.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_B, 0));
		
		switchMarkingMode.setMnemonic(KeyEvent.VK_M);
		switchMarkingMode.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_M, 0));
		deleteMarkingMode.setMnemonic(KeyEvent.VK_D);
		deleteMarkingMode.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_D, 0));
		
		exportPOVMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F3, 0));
		importPOVMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F4, 0));
		openFileMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0));
		exportScreenShotMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0));
		analysisModulesMenu.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0));
		
		rp.getActionMap().put("next", KeyActionCollection.getActionNextData());
		rp.getActionMap().put("previous", KeyActionCollection.getActionPreviousData());
		rp.getActionMap().put("first", KeyActionCollection.getActionFirstData());
		rp.getActionMap().put("last", KeyActionCollection.getActionLastData());
	}

	//region WindowListener
	public void windowActivated(WindowEvent e) {
		RenderingConfiguration.getViewer().repaint();
	}

	public void windowDeiconified(WindowEvent e) {
		RenderingConfiguration.getViewer().repaint();
	}
	
	public void windowOpened(WindowEvent e) {
		RenderingConfiguration.getViewer().repaint();
	}
	
	public void windowDeactivated(WindowEvent e) {}
	public void windowIconified(WindowEvent e) {}
	public void windowClosing(WindowEvent e) {}
	public void windowClosed(WindowEvent e) {}

	
	//endregion WindowListener
	
	private class JOptionCheckBoxMenuItem extends JCheckBoxMenuItem{
		private static final long serialVersionUID = 1L;
		private final Options option;
		
		public JOptionCheckBoxMenuItem(Options o) {
			super(o.getName());
			this.option = o;
			if (o.getKeyAccelerator() != null)
				this.setAccelerator(o.getKeyAccelerator());
			this.setSelected(o.isEnabled());
			this.setToolTipText(o.getInfoMessage());
			this.addActionListener(l -> {
				option.setEnabled(JOptionCheckBoxMenuItem.this.isSelected());
				if (!option.getActivateMessage().isEmpty())
					JOptionPane.showMessageDialog(JMainWindow.this, option.getActivateMessage());
				RenderingConfiguration.saveProperties();
			});
		}
	}
	
	private class DefaultPerspectivesButtonPanel extends JPanel{
		private static final long serialVersionUID = 1L;

		class ViewButton extends JButton implements ActionListener{
			private static final long serialVersionUID = 1L;
			Vec3 pov;
			boolean resetZoom;
			
			ViewButton(String text, float x, float y, float z, boolean resetZoom){
				super(text);
				this.pov = new Vec3(x,y,z);
				this.resetZoom = resetZoom;
				this.addActionListener(this);
			}
			
			@Override
			public void actionPerformed(ActionEvent e) {
				ViewerGLJPanel viewer = RenderingConfiguration.getViewer();
				if (resetZoom) viewer.resetZoom();
				else viewer.setPOV(pov.x, pov.y, pov.z);
			}
		}
		
		DefaultPerspectivesButtonPanel() {
			this.setLayout(new GridBagLayout());
			GridBagConstraints gbc = CommonUtils.getBasicGridBagConstraint();
			this.add(new ViewButton("+Z", 0f, 0f, 0f, false),gbc);     gbc.gridx++;
			this.add(new ViewButton("-Z", 180f, 0f, 0f, false),gbc);   gbc.gridx = 0; gbc.gridy++;
			this.add(new ViewButton("+Y", -90f, 0f, 180f, false),gbc); gbc.gridx++;
			this.add(new ViewButton("-Y", -90f, 0f, 0f, false),gbc);   gbc.gridx = 0; gbc.gridy++;
			this.add(new ViewButton("+X", -90f, 0f, -90f, false),gbc); gbc.gridx++;
			this.add(new ViewButton("-X", -90f, 0f, 90f, false),gbc);  gbc.gridx = 0; gbc.gridy++;
			gbc.gridwidth = 2;
			this.add(new ViewButton("<html><center>Reset zoom<br> and focus</center><html>", 0f, 0f, 0f, true),gbc);
		}
	}
		
	@Override
	public void atomDataChanged(AtomDataChangedEvent e) {
		if (e.getNewAtomData() == null)
			JMainWindow.this.setTitle("AtomViewer");
		else 
			JMainWindow.this.setTitle("AtomViewer ("+e.getNewAtomData().getName()+")");
	}
	
	public static void startAtomViewer(String[] args) {
		Locale.setDefault(Locale.US);
		//Headless processing
		if (args.length != 0 && !args[0].equals("-exp")){
			new BatchProcessing().processBatch(args);
		} else {
			EventQueue.invokeLater( () -> {
				try {
					GLProfile.initSingleton();
					JMainWindow frame = new JMainWindow();
					frame.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			});
		}
	}
	
	public void applyProcessWindowWithDisplay(AtomData data, ProcessingModule pm) {
		List<AtomData> d = new ArrayList<AtomData>();
		d.add(data);
		applyProcessWindowWithDisplay(d, pm);
	}
	
	public void applyProcessWindowWithDisplay(Iterable<AtomData> data, ProcessingModule pm) {
		final SwingWorker<Void,Void> sw = new ProcessModuleWorker(pm, data);
		
		final JProgressDisplayDialog progressDisplay = 
				new JProgressDisplayDialog(sw, JMainWindow.this, false);
		progressDisplay.setTitle("Analysis");
		
		sw.addPropertyChangeListener( l -> {
			if ( sw.isDone() || sw.isCancelled()){
				progressDisplay.dispose();
			}
		});
		
		sw.execute();
		progressDisplay.setVisible(true);
	}
	
	private class OpenMenuListener implements ActionListener{
		@Override
		public void actionPerformed(ActionEvent e) {
			JMDFileChooser chooser = new JMDFileChooser();
					
			if (Configuration.getLastOpenedFolder() != null) {
				chooser.setCurrentDirectory(Configuration.getLastOpenedFolder());
			}
			int result = chooser.showOpenDialog(JMainWindow.this);
			if (result == JFileChooser.APPROVE_OPTION){	
				final MDFileLoader fileLoader = Configuration.getCurrentFileLoader();
				
				if (Configuration.getCurrentAtomData() != null && !ImportStates.APPEND_FILES.isActive()) {
					Configuration.getCurrentAtomData().clear();
					Configuration.setCurrentAtomData(null, true, true);
					RenderingConfiguration.getViewer().getRenderRange().reset();
				}
				boolean successfulCreated = chooser.createConfiguration();
				if (!successfulCreated) return;
				Configuration.setLastOpenedFolder(chooser.getSelectedFile().getParentFile());
				typeColorMenu.setEnabled(true);

				fileLoader.setFilesToRead(chooser.getSelectedFiles());
				final SwingWorker<AtomData, String> worker = fileLoader.getNewSwingWorker();
				
				final JProgressDisplayDialog progressDisplay = new JProgressDisplayDialog(worker, JMainWindow.this);
				progressDisplay.setTitle("Opening files...");
				
				PropertyChangeListener pcl = new PropertyChangeListener() {
					@Override
					public void propertyChange(PropertyChangeEvent arg0) {
						if ( worker.isDone() || worker.isCancelled()){
							try {
								//Retrieve the results from the background worker
								if (!worker.isCancelled())
									Configuration.setCurrentAtomData(worker.get(), true, !ImportStates.APPEND_FILES.isActive());
							} catch (Exception e) {
								progressDisplay.dispose();
								JOptionPane.showMessageDialog(null, e.toString());
								e.printStackTrace();
							} finally {
								worker.removePropertyChangeListener(this);
								progressDisplay.dispose();
							}
						}
					}
				};
				worker.addPropertyChangeListener(pcl);
				
				if (!ImportStates.APPEND_FILES.isActive())
					Configuration.setCurrentAtomData(null, true, true);
			
				worker.execute();
				
				progressDisplay.setVisible(true);
			} 
		}
	}

	private class ExportFileActionListener implements ActionListener {
		@Override
		public void actionPerformed(ActionEvent e) {
			assert Configuration.getCurrentAtomData()!=null : "current AtomData is null";
			
			final JFileChooser chooser = new JFileChooser();
			JPanel optionPanel = new JPanel();
			optionPanel.setLayout(new BoxLayout(optionPanel, BoxLayout.Y_AXIS));
			final JCheckBox exportAll = new JCheckBox("Export all files at once");
			optionPanel.add(exportAll);
			chooser.setAccessory(optionPanel);
			
			final MDFileWriter writer = new ImdFileWriter();
			
			int result = chooser.showSaveDialog(JMainWindow.this);
			if (result == JFileChooser.APPROVE_OPTION){
				
				final AtomData current = Configuration.getCurrentAtomData();
				
				JPrimitiveVariablesPropertiesDialog configDialog = 
						new JPrimitiveVariablesPropertiesDialog(JMainWindow.this, "Configure export");
				List<PrimitiveProperty<?>> options = 
						writer.getAdditionalProperties(current, exportAll.isSelected()); 
				if (options != null && options.size()>0){
					configDialog.startGroup("Options");
					for (PrimitiveProperty<?> p : options) configDialog.addComponent(p);
					configDialog.endGroup();
				}
				
				boolean exportGrain = current.isPolyCrystalline();
				boolean exportRBV = current.isRbvAvailable();
				List<DataColumnInfo> dci = current.getDataColumnInfos();
				//Identify which entries are common in all AtomData
				if (exportAll.isSelected()){
					for (AtomData d : Configuration.getAtomDataIterable()){
						dci.retainAll(d.getDataColumnInfos());
						if (exportGrain) exportGrain = d.isPolyCrystalline();
						if (exportRBV) exportRBV = d.isRbvAvailable();
					}
				}
				
				BooleanProperty eNum = new BooleanProperty("num", "Atom number", "Export atom number", true);
				BooleanProperty eEle = new BooleanProperty("ele", "Element", "Export atom element", true);
				BooleanProperty eg = new BooleanProperty("grains", "Grain number", "Export Grain number per atom", true);
				BooleanProperty erbv = new BooleanProperty("rbv", "RBV", "Export RBV per atom", true);
				BooleanProperty etype = new BooleanProperty("Type", "Structure type", "Export classified type", true);
				BooleanProperty[] dciEnabled = new BooleanProperty[dci.size()];
				
				configDialog.startGroup("Include values in output");
				configDialog.addComponent(eNum);
				configDialog.addComponent(eEle);
				configDialog.addComponent(etype);
				if (exportGrain) configDialog.addComponent(eg);
				if (exportRBV) configDialog.addComponent(erbv);
				
				for (int i=0; i<dci.size(); i++){
					DataColumnInfo d = dci.get(i);
					BooleanProperty bp = new BooleanProperty(d.getId(), d.getName(), "", true);
					configDialog.addComponent(bp);
					dciEnabled[i] = bp;
				}
				configDialog.endGroup();
				
				boolean ok = configDialog.showDialog();
				if (!ok) return;
				
				List<DataColumnInfo> toExport = new ArrayList<DataColumnInfo>();
				for (int i=0; i<dciEnabled.length; i++)
					if (dciEnabled[i].getValue()) toExport.add(dci.get(i));
				
				writer.setDataToExport(eNum.getValue(), eEle.getValue(), etype.getValue(),
						erbv.getValue(), eg.getValue(), toExport.toArray(new DataColumnInfo[toExport.size()]));
//				
//				final SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>(){
//					protected Void doInBackground(){
//						ProgressMonitor pm = null;
//						try {
//							pm = ProgressMonitor.createNewProgressMonitor(this);
//							pm.setActivityName("Exporting");
//							if (exportAll.isSelected()){
//								File path = chooser.getSelectedFile().getParentFile();
//								String prefix = chooser.getSelectedFile().getName();
//								int num = 0;
//								for (AtomData d : Configuration.getAtomDataIterable()){
//									pm.setCurrentFilename(d.getName());
//									
//									String newName = current.getName();
//									newName = String.format("%s.%05d", prefix, num++);
//									
//									writer.writeFile(path, newName, d, null);
//								}
//							} else {
//								String filename = chooser.getSelectedFile().getAbsolutePath();
//								pm.setCurrentFilename(filename);
//								writer.writeFile(null, filename, current, null);
//							}
//						} catch (final Exception e){
//							SwingUtilities.invokeLater(new Runnable() {
//								@Override
//								public void run() {
//									JOptionPane.showMessageDialog(null, e.getLocalizedMessage());
//								}
//							});
//						} finally {
//							if (pm!=null) pm.destroy();
//						}
//						return null;
//					};
//				};
				

				final SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>(){
					protected Void doInBackground(){
						ProgressMonitor pm = null;
						try {
							pm = ProgressMonitor.createNewProgressMonitor(this);
							pm.setActivityName("Exporting");
							if (exportAll.isSelected()){
								File path = chooser.getSelectedFile().getParentFile();
								String prefix = chooser.getSelectedFile().getName();
								int num = 0;
								for (AtomData d : Configuration.getAtomDataIterable()){
									pm.setCurrentFilename(d.getName());
									
									String newName = current.getName();
									newName = String.format("%s.%05d", prefix, num++);
									
									writer.writeFile(path, newName, d, null);
								}
							} else {
								String filename = chooser.getSelectedFile().getAbsolutePath();
								pm.setCurrentFilename(filename);
								writer.writeFile(null, filename, current, null);
							}
						} catch (final Exception e){
							SwingUtilities.invokeLater(new Runnable() {
								@Override
								public void run() {
									JOptionPane.showMessageDialog(null, e.getLocalizedMessage());
								}
							});
						} finally {
							if (pm!=null) pm.destroy();
						}
						return null;
					};
				};
				
				final JProgressDisplayDialog progressDisplay = new JProgressDisplayDialog(worker, JMainWindow.this, false);
				progressDisplay.setTitle("Exporting IMD files");
				
				PropertyChangeListener pcl = new PropertyChangeListener() {
					@Override
					public void propertyChange(PropertyChangeEvent arg0) {
						if ( worker.isDone() || worker.isCancelled()){
							worker.removePropertyChangeListener(this);
							progressDisplay.dispose();
						}
					}
				};
				worker.addPropertyChangeListener(pcl);
				worker.execute();
				progressDisplay.setVisible(true);
			}
		}
	}
	
	private class ExportMarkersActionListener implements ActionListener{
		@Override
		public void actionPerformed(ActionEvent e) {
			
			JPrimitiveVariablesPropertiesDialog dialog = new JPrimitiveVariablesPropertiesDialog(JMainWindow.this, "Export defects");
			
			dialog.addLabel("Export defect markings to file");
			dialog.add(new JSeparator());
			
			dialog.startGroup("Options");
			BooleanProperty allFilesExport = dialog.addBoolean("allFiles", "Export all files",
					"Exports all opened files", false, false);
			BooleanProperty fixedFolderExport = dialog.addBoolean("fixedFolder", "Save marks in different folder?",
					"Exported files are stored in a selected folder", false, false);
			dialog.endGroup();
			
			dialog.add(new JSeparator());
			BooleanProperty atomViewerFormat = dialog.addBoolean("AtomViewer", "AtomViewer fomat (recommended)",
					"Save defects in the AtomViewer format. Required to reload marks.", true, false);
			BooleanProperty csvFormat = dialog.addBoolean("CSV", "VGG Image Annotator format (csv)",
					"Save defect marks in the VGG Image Annotator format. Can be imported in VIA.", false, false);
			BooleanProperty svgFormat = dialog.addBoolean("SVG", "SVG format (b/w)",
					"Export defect marks as a flat black/white svg file", false, false);
			BooleanProperty svgOverlayFormat = dialog.addBoolean("SVG overlay", "SVG format (overlay)",
					"Export defect marks as a svg file where defects are visible as overlays on the 2D image", false, false);
			
			
			boolean ok = dialog.showDialog();
			if (ok){
				//Show folder selector if selected
				File fixedExportFolder = null;
				if (fixedFolderExport.getValue()) {
					JFileChooser chooser = new JFileChooser(Configuration.getLastOpenedFolder());
					chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		            int option = chooser.showOpenDialog(JMainWindow.this);
		            if(option == JFileChooser.APPROVE_OPTION){
		            	fixedExportFolder = chooser.getSelectedFile();
		            }else{
		               return;
		            }
				}
				
				List<AtomData> filesToProcess = new ArrayList<>();
				if (allFilesExport.getValue()) {
					for (AtomData ad : Configuration.getAtomDataIterable())
						filesToProcess.add(ad);
				} else {
					filesToProcess.add(Configuration.getCurrentAtomData());
				}
				
				for (AtomData ad : filesToProcess) {
					try {
						File base;
						if (fixedFolderExport.getValue()){
							base = fixedExportFolder;
						} else {
							base = ((File)ad.getFileMetaData("File3D_file")).getParentFile();
						}
						
						if (atomViewerFormat.getValue()) {
							String name =ad.getName().replace(".bmp", ".xml").replace(".jpg", ".xml");
							File exportTo = new File(base, name);
							DefectMarking.export(ad, exportTo);
						}
						
						if (svgFormat.getValue()) {
							String svgname =ad.getName().replace(".bmp", ".svg").replace(".jpg", ".svg");
							File exportTo = new File(base, svgname);
							DefectMarking.exportSvg(ad, exportTo, false);
						}
						
						if (svgOverlayFormat.getValue()) {
							String svgname =ad.getName().replace(".bmp", ".svg").replace(".jpg", ".svg");
							String xmlnameOverlay ="overlay_"+svgname;
							File exportTo = new File(base, xmlnameOverlay);
							DefectMarking.exportSvg(ad, exportTo, true);
						}
						
						if (csvFormat.value) {
							String csvname =ad.getName().replace(".bmp", ".csv").replace(".jpg", ".csv");
							File exportTo = new File(base, csvname);
							DefectMarking.exportCsv(ad, exportTo);
						}
					} catch (Exception e1) {
						e1.printStackTrace();
					}
				}
			}
			//JOptionPane.showMessageDialog(JMainWindow.this, "Export successful");			
		}
	}
	
	private class ExportScreenshotActionListener implements ActionListener{
		@Override
		public void actionPerformed(ActionEvent e) {
			if (Configuration.getCurrentAtomData()!=null){
				JFileChooser chooser = new JFileChooser();
				if (Configuration.getLastOpenedExportFolder() !=null) 
					chooser.setCurrentDirectory(Configuration.getLastOpenedExportFolder());
				String fileEnding = "";
				
				chooser.setDialogTitle("Export screenshot");
				chooser.setFileFilter(new FileNameExtensionFilter("Portable network graphic (*.png)","png"));
				if (ImageIO.getImageWritersBySuffix("jpg").hasNext())
					chooser.addChoosableFileFilter(new FileNameExtensionFilter("JPEG file (*.jpg)", "jpg"));				
				chooser.setAcceptAllFileFilterUsed(false);
				
				chooser.setSelectedFile(new File(Configuration.getCurrentAtomData().getName()));
				
				JPanel imageSizeDialogExtension = new JPanel(new GridBagLayout());
				GridBagConstraints gbc = CommonUtils.getBasicGridBagConstraint();
				
				gbc.gridwidth = 2;
				final JCheckBox exportAll = new JCheckBox("Export all");
				imageSizeDialogExtension.add(exportAll, gbc); gbc.gridy++;
				imageSizeDialogExtension.add(new JLabel("Image size (pixel)"), gbc); gbc.gridy++;
				gbc.weighty = 0.1;
				imageSizeDialogExtension.add(new JLabel(""), gbc); gbc.gridy++;
				gbc.gridwidth = 1; gbc.weighty = 0;
				imageSizeDialogExtension.add(new JLabel("Width"), gbc); gbc.gridx++;
				imageSizeDialogExtension.add(new JLabel("Height"), gbc); gbc.gridy++; gbc.gridx = 0;
				
				gbc.fill = GridBagConstraints.HORIZONTAL;
				JFormattedTextField widthTextField = new JFormattedTextField(new DecimalFormat("#"));
				widthTextField.setValue(RenderingConfiguration.getViewer().getWidth());
				imageSizeDialogExtension.add(widthTextField, gbc); gbc.gridx++;
				
				JFormattedTextField heightTextField = new JFormattedTextField(new DecimalFormat("#"));
				heightTextField.setValue(RenderingConfiguration.getViewer().getHeight());
				imageSizeDialogExtension.add(heightTextField, gbc); gbc.gridy++;
				
				gbc.weighty = 1;
				imageSizeDialogExtension.add(new JLabel(""), gbc);
				
				chooser.setAccessory(imageSizeDialogExtension);
				
				int result = chooser.showSaveDialog(JMainWindow.this);
				if (result == JFileChooser.APPROVE_OPTION){
					fileEnding = ((FileNameExtensionFilter)chooser.getFileFilter()).getExtensions()[0];
					Configuration.setLastOpenedExportFolder(chooser.getSelectedFile().getParentFile());
					String filename = chooser.getSelectedFile().getAbsolutePath();
					//Add ending only if not a sequence, otherwise the name is just a prefix for the automatically
					//generated filename
					boolean sequence = exportAll.isSelected();
					if (!filename.endsWith(fileEnding) && !sequence) filename += "."+fileEnding;
					try {
						int width = ((Number)widthTextField.getValue()).intValue();
						int height = ((Number)heightTextField.getValue()).intValue();
						
						if (width<=0) width = 1;
						if (height<=0) height = 1;
							
						RenderingConfiguration.getViewer().makeScreenshot(filename, fileEnding, sequence, width, height);
					} catch (Exception e1) {
						JOptionPane.showMessageDialog(null, e1.getLocalizedMessage());
					}
				}
			}
		}
	}
	
	private class ProcessModuleWorker extends SwingWorker<Void, Void>{
		ProcessingModule pm;
		Iterable<AtomData> data;
		
		
		ProcessModuleWorker(ProcessingModule pm, Iterable<AtomData> data) {
			this.pm = pm;
			this.data = data;
			ProgressMonitor.createNewProgressMonitor(this);
		}
		
		@Override
		protected Void doInBackground() throws Exception {	
			try {
				for (AtomData d : data)
					d.applyProcessingModule(pm);
			} catch (Exception e) {
				JLogPanel.getJLogPanel().addError("Error in processing module", e.getMessage());
				e.printStackTrace();
			}
			return null;
		}
		
		@Override
		protected void done() {
			super.done();
			ProgressMonitor.getProgressMonitor().destroy();
		}
	}
	
	private class JGraphicOptionCheckBoxMenuItem extends JCheckBoxMenuItem{
		private static final long serialVersionUID = 1L;
		final RenderOption ro;
		
		public JGraphicOptionCheckBoxMenuItem(String label, RenderOption option, String tooltip) {
			super(label);
			this.ro = option;
			this.setToolTipText(tooltip);
			this.setSelected(ro.isEnabled());
			this.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					ro.setEnabled(JGraphicOptionCheckBoxMenuItem.this.isSelected());
				}
			});
		}
	}
	
	private class JChangeSphereSizeActionListener implements ActionListener{
		@Override
		public void actionPerformed(ActionEvent e) {
			final float min = 0.00001f;
			final float max = 10000f;
			JPrimitiveVariablesPropertiesDialog d = 
					new JPrimitiveVariablesPropertiesDialog(JMainWindow.this, "Edit sphere size");
			FloatProperty defSize = d.addFloat("scale", "Global scaling factor", "Adjust the scaling of all atoms",
					Math.max(min, Math.min(RenderingConfiguration.getViewer().getSphereSize(), max)), min, max);
			
			FloatProperty[] atomScales = new FloatProperty[0];
			
			if (Configuration.getCurrentAtomData() != null){
				CrystalStructure cs = Configuration.getCurrentAtomData().getCrystalStructure();
				defSize.setDefaultValue(cs.getDistanceToNearestNeighbor()*0.55f);
				
				if (cs.getNumberOfElements() > 1){
					float[] defsizes = cs.getDefaultSphereSizeScalings();
					float[] sizes = cs.getSphereSizeScalings();
					String[] names = cs.getNamesOfElements();
					atomScales = new FloatProperty[sizes.length];
					d.add(new JSeparator());
					d.startGroup("Individual scaling");
			
					for (int i=0; i<cs.getNumberOfElements(); i++){
						atomScales[i] = d.addFloat("", names[i], "", sizes[i], 0.001f, 1000f);
						atomScales[i].setDefaultValue(defsizes[i]); 
					}
					d.endGroup();
				}
			}
			boolean ok = d.showDialog();
			if (ok){
				for (int i=0; i<atomScales.length; i++)
					Configuration.getCurrentAtomData().getCrystalStructure().setSphereSizeScalings(i, atomScales[i].value);
				RenderingConfiguration.getViewer().setSphereSize(defSize.value);
			}
		}
	}
}
