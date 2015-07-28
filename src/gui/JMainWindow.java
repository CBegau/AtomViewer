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
import javax.media.opengl.GLCapabilities;
//import javax.media.opengl.GLDrawableFactory;
//import javax.media.opengl.GLOffscreenAutoDrawable;
import javax.media.opengl.GLProfile;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

import processingModules.AvailableProcessingModules;
import processingModules.DataContainer;
import processingModules.AvailableProcessingModules.JProcessingModuleDialog;
import processingModules.skeletonizer.Skeletonizer;
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

	public static final String VERSION = "3.0 alpha"; 
	public static String buildVersion = "";
	
	private static final long serialVersionUID = 1L;
	
	private KeyBoardAction keyAction = new KeyBoardAction();
	
	private final JAtomicMenuPanel atomicMenu;;
	private final JGraphicOptionCheckBoxMenuItem stereoCheckBoxMenu;
	private final JGraphicOptionCheckBoxMenuItem perspectiveCheckBoxMenu;
	private final JGraphicOptionCheckBoxMenuItem drawCoordinateSystemBoxMenu;
	private final JGraphicOptionCheckBoxMenuItem whiteBackgroundCheckBoxMenu;
	private final JMenuItem editRangeMenuItem;
	private final JMenuItem exportSkeletonFileMenuItem;
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
		gbc.gridx++; gbc.weightx = 1;
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
		
		this.atomicMenu = new JAtomicMenuPanel(keyAction, this);
		this.add(atomicMenu, BorderLayout.WEST);
		
		this.add(splitPane, BorderLayout.CENTER);
		addWindowListener(this);
		
		OpenMenuListener oml = new OpenMenuListener();
		
		JMenuBar menu = new JMenuBar();
		JMenu fileMenu = new JMenu("File");
		JMenuItem openFileMenuItem = new JMenuItem("Open file");
		openFileMenuItem.addActionListener(CursorController.createListener(this, oml));
		fileMenu.add(openFileMenuItem);
		
		JMenuItem exportScreenShotMenuItem = new JMenuItem("Save Screenshot");
		exportScreenShotMenuItem.setActionCommand("Screenshot");
		exportScreenShotMenuItem.addActionListener(CursorController.createListener(this, new ExportActionListener()));
		fileMenu.add(exportScreenShotMenuItem);
		
		ExportFileActionListener exportFileListener = new ExportFileActionListener();
		
		JMenuItem exportAsciiFile = new JMenuItem("Export as IMD-Checkpoint");
		exportAsciiFile.addActionListener(CursorController.createListener(this, exportFileListener));
		fileMenu.add(exportAsciiFile);
		
		exportSkeletonFileMenuItem = new JMenuItem("Export dislocation network");
		exportSkeletonFileMenuItem.addActionListener(CursorController.createListener(this, new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				DataContainer dc = Configuration.getCurrentAtomData().getDataContainer(Skeletonizer.class);
				Skeletonizer skel = null;
				if (dc != null)
					skel = (Skeletonizer)dc;
				if (skel == null) return;
				
				JFileChooser chooser = new JFileChooser();
				int result = chooser.showSaveDialog(JMainWindow.this);
				if (result == JFileChooser.APPROVE_OPTION){
					try {
						skel.writeDislocationSkeleton(chooser.getSelectedFile());
					} catch (IOException e1) {
						e1.printStackTrace();
					}
				}
			}
		}));
		exportSkeletonFileMenuItem.setEnabled(false);
		fileMenu.add(exportSkeletonFileMenuItem);
		
		
		fileMenu.add(new JSeparator());
		JMenuItem exitMenuItem = new JMenuItem("Exit");
		exitMenuItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				System.exit(0);
			}
		});
		fileMenu.add(exitMenuItem);
		
		menu.add(fileMenu);
		
		JMenu viewMenu = new JMenu("View");
		
		editRangeMenuItem = new JMenuItem("Edit visible range");
		editRangeMenuItem.setMnemonic(KeyEvent.VK_R);
		
		editRangeMenuItem.addActionListener(new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent e) {
				if (Configuration.getCurrentAtomData() != null){
					RenderRange rr = RenderingConfiguration.getViewer().getRenderRange();
					new JRenderedIntervalEditorDialog(JMainWindow.this, rr);
					RenderingConfiguration.getViewer().updateAtoms();
				}
			}
		});
		
		final JCheckBoxMenuItem drawLegendMenuItem = new JCheckBoxMenuItem("Show legends");
		drawLegendMenuItem.setSelected(RenderOption.LEGEND.isEnabled());
		drawLegendMenuItem.setMnemonic(KeyEvent.VK_L);
		drawLegendMenuItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				RenderOption.LEGEND.setEnabled(drawLegendMenuItem.isSelected());
			}
		});
		
		JMenuItem changeSphereSizeMenuItem = new JMenuItem("Change sphere size");
		changeSphereSizeMenuItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				String str = JOptionPane.showInputDialog(null, "Enter new sphere size (current "+
						Float.toString(RenderingConfiguration.getViewer().getSphereSize()) +"): ", 
						"Enter sphere size", JOptionPane.QUESTION_MESSAGE);
				try {
					if (str!=null){
						float f = Float.parseFloat(str);
						RenderingConfiguration.getViewer().setSphereSize(f);
					}
				} catch (Exception ex) {
					JOptionPane.showMessageDialog(null, "Please enter a valid number", "Warning", JOptionPane.WARNING_MESSAGE);
				}
			}
		});
		
		perspectiveCheckBoxMenu = new JGraphicOptionCheckBoxMenuItem("Perspective projection", RenderOption.PERSPECTIVE);
		perspectiveCheckBoxMenu.setToolTipText("Enable perspective projection instead of orthogonal");
		perspectiveCheckBoxMenu.setMnemonic(KeyEvent.VK_P);
		
		stereoCheckBoxMenu = new JGraphicOptionCheckBoxMenuItem("Anaglyphic stereo", RenderOption.STEREO);
		stereoCheckBoxMenu.setMnemonic(KeyEvent.VK_S);
		
		whiteBackgroundCheckBoxMenu = new JGraphicOptionCheckBoxMenuItem("White background", RenderOption.PRINTING_MODE);
		whiteBackgroundCheckBoxMenu.setMnemonic(KeyEvent.VK_W);
		
		JGraphicOptionCheckBoxMenuItem drawBoundingBoxCheckBoxMenu = 
				new JGraphicOptionCheckBoxMenuItem("Bounding box", RenderOption.BOUNDING_BOX);
		JGraphicOptionCheckBoxMenuItem drawTetraederCheckBoxMenu = 
				new JGraphicOptionCheckBoxMenuItem("Thompson Tetraeder", RenderOption.THOMPSON_TETRAEDER);
		JGraphicOptionCheckBoxMenuItem drawLengthScaleBoxMenu = 
				new JGraphicOptionCheckBoxMenuItem("Length scale", RenderOption.LENGTH_SCALE);
		drawLengthScaleBoxMenu.setToolTipText("A lenght scale bar is only possible in othogonal projection"
				+ ", but not in perspective projection");
		JGraphicOptionCheckBoxMenuItem drawIndentBoxMenu = 
				new JGraphicOptionCheckBoxMenuItem("Indenter (if available)", RenderOption.INDENTER);
		
		drawCoordinateSystemBoxMenu = new JGraphicOptionCheckBoxMenuItem("Coordinate System", RenderOption.COORDINATE_SYSTEM);
		drawCoordinateSystemBoxMenu.setMnemonic(KeyEvent.VK_C);
		
		viewMenu.add(editRangeMenuItem);
		viewMenu.add(changeSphereSizeMenuItem);
		viewMenu.add(stereoCheckBoxMenu);
		viewMenu.add(perspectiveCheckBoxMenu);
		viewMenu.add(whiteBackgroundCheckBoxMenu);
		viewMenu.add(drawBoundingBoxCheckBoxMenu);
		viewMenu.add(drawTetraederCheckBoxMenu);
		viewMenu.add(drawCoordinateSystemBoxMenu);
		viewMenu.add(drawLengthScaleBoxMenu);
		viewMenu.add(drawIndentBoxMenu);
		viewMenu.add(drawLegendMenuItem);
		
		JMenu legendStyleMenu = new JMenu("Legend style");
		final JCheckBoxMenuItem swapLegend = new JCheckBoxMenuItem("Swap colors");
		swapLegend.setSelected(ColorTable.isColorBarSwapped());
		swapLegend.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				ColorTable.setColorBarSwapped(swapLegend.isSelected());
				RenderingConfiguration.getViewer().updateAtoms();
				RenderingConfiguration.saveProperties();
			}
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
		
		JMenu editMenu = new JMenu("Edit");
		JMenuItem editCrystalConf = new JMenuItem("Edit configuration");
		editMenu.add(editCrystalConf);
		editCrystalConf.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				if (Configuration.getCurrentAtomData()==null) return;
				File selectedFile = new File(Configuration.getCurrentAtomData().getFullPathAndFilename());
				
				JCrystalConfigurationDialog ccd = 
						new JCrystalConfigurationDialog(JMainWindow.this, 
								Configuration.getLastOpenedFolder(), selectedFile, true);
				if (ccd.isSavedSuccessfully()) {
					JOptionPane.showMessageDialog(JMainWindow.this, "File(s) must be reloaded for changes to be effective");
				}
			}
		});
		
		JMenuItem dropAtomDataMenuItem = new JMenuItem("Drop atom data file");
		editMenu.add(dropAtomDataMenuItem);
		dropAtomDataMenuItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				AtomData atomData = Configuration.getCurrentAtomData();
				if (atomData == null) return;
				AtomData next = atomData.getNext();
				AtomData pre = atomData.getPrevious();
				if (pre != null) pre.setNext(next);
				if (next != null) next.setPrevious(pre);
				atomData.clear();
				atomData = null;
				if (next!=null) atomData = next;
				else atomData = pre;
				
				Configuration.setCurrentAtomData(atomData, true, false);
			}
		});
		
		typeColorMenu = new JMenu("Colors");
		JMenuItem resetColorMenuItem = new JMenuItem("Reset colors of atom types");
		resetColorMenuItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				CrystalStructure c = Configuration.getCurrentAtomData().getCrystalStructure();
				if (c!=null) {
					c.resetColors();
					atomicMenu.updateValues();
					RenderingConfiguration.getViewer().updateAtoms();
				}
			}
		});
		typeColorMenu.add(resetColorMenuItem);
		
		JMenuItem saveColorMenuItem = new JMenuItem("Save colors of atom types");
		saveColorMenuItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
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
			}
		});
		typeColorMenu.add(saveColorMenuItem);
		
		JMenuItem loadColorMenuItem = new JMenuItem("Load colors of atom types");
		loadColorMenuItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
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
			}
		});
		typeColorMenu.add(loadColorMenuItem);
		
		editMenu.add(typeColorMenu);
		typeColorMenu.setEnabled(false);
		menu.add(editMenu);
		
		final JMenu processingMenu = new JMenu("Analysis");
		
		JMenuItem atomicModulesMenu = new JMenuItem("Atom based analyis");
		atomicModulesMenu.setActionCommand("atomic");
		JMenuItem otherModulesMenu = new JMenuItem("Other analyis");
		otherModulesMenu.setActionCommand("other");
		
		ActionListener processingActionListener = new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				AtomData data = Configuration.getCurrentAtomData();
				if (data != null){
					AvailableProcessingModules.JProcessingModuleDialog dialog 
						= new AvailableProcessingModules.JProcessingModuleDialog(JMainWindow.this);
					
					List<ProcessingModule> modules = null;
					if (e.getActionCommand().equals("atomic"))
						modules = AvailableProcessingModules.getAtomicScaleProcessingModule();
					else modules = AvailableProcessingModules.getOtherProcessingModule();
					
					JProcessingModuleDialog.SelectedState ok = dialog.showDialog(modules);
					ProcessingModule pm = dialog.getSelectedProcessingModule();
					if (ok != JProcessingModuleDialog.SelectedState.CANCEL && pm!=null){
						boolean multipleFiles = (ok == JProcessingModuleDialog.SelectedState.ALL_FILES);
						
						boolean possible = pm.showConfigurationDialog(JMainWindow.this, data);
						if (possible){
							while (multipleFiles && data.getPrevious() != null)
								data = data.getPrevious();
							
							do {
								if (pm.isApplicable(data)){
									applyProcessWindowWithDisplay(data, pm, multipleFiles);
								}
								if (multipleFiles) data = data.getNext();
							} while (multipleFiles && data != null);
							
							Configuration.setCurrentAtomData(Configuration.getCurrentAtomData(), true, false);
						}
					}
				}
			}

		};
		atomicModulesMenu.addActionListener(processingActionListener);
		otherModulesMenu.addActionListener(processingActionListener);
		
		processingMenu.add(atomicModulesMenu);
		processingMenu.add(otherModulesMenu);
		menu.add(processingMenu);
		
		final JMenuItem settingsMenu = new JMenu("Settings");
		for (Options o : RenderingConfiguration.Options.values()){
			settingsMenu.add(new JOptionCheckBoxMenuItem(o));
		}
		final JMenuItem selectFontMenuItem = new JMenuItem("Select font");
		selectFontMenuItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
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
				
			}
		});
		settingsMenu.add(selectFontMenuItem);
		menu.add(settingsMenu);
		
		
		final JMenuItem toolchainMenu = new JMenu("Toolchain");
		
		JMenuItem saveToolchainMenuItem = new JMenuItem("Save");
		saveToolchainMenuItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				JFileChooser chooser = new JFileChooser();
				chooser.setFileFilter(new FileNameExtensionFilter("Toolchainfile (*.tcf)","tcf"));
				int result = chooser.showSaveDialog(JMainWindow.this);
				if (result == JFileChooser.APPROVE_OPTION){
					File file = chooser.getSelectedFile();
					try {
						FileOutputStream f = new FileOutputStream(file);
						Configuration.getCurrentAtomData().getToolchain().saveToolchain(f);
					} catch (Exception e1) {
						e1.printStackTrace();
					}
				}
			}
		});
		
		JMenuItem applyToolchainMenuItem = new JMenuItem("Apply");
		applyToolchainMenuItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				try {
					JFileChooser chooser = new JFileChooser();
					chooser.setFileFilter(new FileNameExtensionFilter("Toolchainfile (*.tcf)","tcf"));
					int result = chooser.showOpenDialog(JMainWindow.this);
					if (result == JFileChooser.APPROVE_OPTION){
						File file = chooser.getSelectedFile();
						FileInputStream f = new FileInputStream(file);
						Toolchain tc = Toolchain.readToolchain(f);
						for (ProcessingModule pm : tc.getProcessingModules()){
							applyProcessWindowWithDisplay(Configuration.getCurrentAtomData(), pm.clone(), false);
						}
						Configuration.setCurrentAtomData(Configuration.getCurrentAtomData(), true, false);
						JLogPanel.getJLogPanel().addLog("Applied toolchain");
					}
					
					
				} catch (Exception ex) {
					ex.printStackTrace();
				}
				
			}
		});
		
		JMenuItem applyToAllToolchainMenuItem = new JMenuItem("Apply to all");
		applyToAllToolchainMenuItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				try {
					JFileChooser chooser = new JFileChooser();
					chooser.setFileFilter(new FileNameExtensionFilter("Toolchainfile (*.tcf)","tcf"));
					int result = chooser.showOpenDialog(JMainWindow.this);
					if (result == JFileChooser.APPROVE_OPTION){
						File file = chooser.getSelectedFile();
						FileInputStream f = new FileInputStream(file);
						Toolchain tc = Toolchain.readToolchain(f);
						AtomData def = Configuration.getCurrentAtomData();
						for (ProcessingModule pm : tc.getProcessingModules()){
							for (AtomData d : Configuration.getAtomDataIterable()){
								Configuration.setCurrentAtomData(d, false, false);
								ProcessingModule pmc = pm.clone();
								applyProcessWindowWithDisplay(d, pmc, false);
							}
						}
						Configuration.setCurrentAtomData(def, true, false);
						JLogPanel.getJLogPanel().addLog("Applied toolchain");
					}
					
					
				} catch (Exception ex) {
					ex.printStackTrace();
				}
				
			}
		});
		
		toolchainMenu.add(saveToolchainMenuItem);
		toolchainMenu.add(applyToolchainMenuItem);
		toolchainMenu.add(applyToAllToolchainMenuItem);
//		menu.add(toolchainMenu);
		
		
		final JMenuItem helpMenu = new JMenu("Help");
		final JMenuItem helpMenuItem = new JMenuItem("Help");
		helpMenuItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				String message = "<html><body>" +
				"Zoom, move and rotate:<br><br>" +
				"Press mouse button -> Rotate <br>"+
				"Press mouse button + Shift-> Rotate around axis<br>"+
				"Press mouse button + Ctrl -> Move<br>"+
				"Press mouse button + Alt+Ctrl -> Zoom in/out<br>"+
				"Hold Shift+Ctrl+Click on object -> Focus on this object"+
				"</body></html>";
				JOptionPane.showMessageDialog(JMainWindow.this, message, "AtomViewer", JOptionPane.INFORMATION_MESSAGE);
			}
		});
		final JMenuItem aboutMenuItem = new JMenuItem("About");
		aboutMenuItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				String message = "<html><body>" +
						"AtomViewer  Copyright (C) 2015, ICAMS, Ruhr-Universtät Bochum <br>" +
						"AtomViewer is a tool to display and analyse atomistic simulations<br><br>" +
						"AtomViewer Version "+JMainWindow.VERSION+"-"+JMainWindow.buildVersion+"<br>"+
						"Available OpenGL version on this machine: "+ViewerGLJPanel.openGLVersion +"<br><br>" +
						"Using Jogl Version "+JoglVersion.getInstance().getSpecificationVersion()+"<br>"+
						"This program comes with ABSOLUTELY NO WARRANTY <br>" +
						"This is free software, and you are welcome to redistribute it under certain conditions.<br>"+
						"For details see the file COPYING which comes along with this program, or if not <br>" +
						"http://www.gnu.org/licenses/gpl.html <br>" +
						"</body></html>";
				JOptionPane.showMessageDialog(JMainWindow.this, message, "AtomViewer", JOptionPane.INFORMATION_MESSAGE);
			}
		});
		helpMenu.add(helpMenuItem);
		helpMenu.add(aboutMenuItem);
		menu.add(helpMenu);
		
		this.setJMenuBar(menu);
		
		this.pack();
		this.setExtendedState(JFrame.MAXIMIZED_BOTH);
		
		JRootPane rp = getRootPane();
		rp.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
		    KeyStroke.getKeyStroke(KeyEvent.VK_A,0), "keypressed");
		rp.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
			    KeyStroke.getKeyStroke(KeyEvent.VK_C,0), "keypressed");
		rp.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
			    KeyStroke.getKeyStroke(KeyEvent.VK_S,0), "keypressed");
		rp.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
			    KeyStroke.getKeyStroke(KeyEvent.VK_R,0), "keypressed");
		rp.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
			    KeyStroke.getKeyStroke(KeyEvent.VK_W,0), "keypressed");
		rp.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
			    KeyStroke.getKeyStroke(KeyEvent.VK_P,0), "keypressed");
		
		rp.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
			    KeyStroke.getKeyStroke(KeyEvent.VK_X,0), "keypressed");
		rp.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
			    KeyStroke.getKeyStroke(KeyEvent.VK_Z,0), "keypressed");
		rp.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
			    KeyStroke.getKeyStroke(KeyEvent.VK_Y,0), "keypressed");
		rp.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
			    KeyStroke.getKeyStroke(KeyEvent.VK_L,0), "keypressed");
		rp.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
			    KeyStroke.getKeyStroke(KeyEvent.VK_F,0), "keypressed");
		
		rp.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
			    KeyStroke.getKeyStroke(KeyEvent.VK_F3,0), "f3");
		rp.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
			    KeyStroke.getKeyStroke(KeyEvent.VK_F4,0), "f4");
		
		rp.getActionMap().put("keypressed", keyAction);
		rp.getActionMap().put("f3", new AbstractAction() {
			private static final long serialVersionUID = 1L;
			@Override
			public void actionPerformed(ActionEvent arg0) {
				float[] m = RenderingConfiguration.getViewer().getPov();
				String s = "";
				for (int i=0; i<m.length; i++)
					s += m[i]+";";
				JOptionPane.showInputDialog(null, "POV", s);
			}
		});
		rp.getActionMap().put("f4", new AbstractAction() {
			private static final long serialVersionUID = 1L;
			@Override
			public void actionPerformed(ActionEvent arg0) {
				String input = JOptionPane.showInputDialog("POV").trim();
				if (input != null && !input.isEmpty()){ 
					String[] split = input.split(";");
					float[] m = new float[split.length];
					for (int i=0; i<split.length; i++)
						m[i] = Float.parseFloat(split[i]);
					RenderingConfiguration.getViewer().setPOV(m);
				}
			}
		});
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
			this.setSelected(o.isEnabled());
			this.setToolTipText(o.getInfoMessage());
			this.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					option.setEnabled(JOptionCheckBoxMenuItem.this.isSelected());
					if (!option.getActivateMessage().isEmpty())
						JOptionPane.showMessageDialog(JMainWindow.this, option.getActivateMessage());
					RenderingConfiguration.saveProperties();
				}
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
			this.add(new ViewButton("Reset zoom and focus", 0f, 0f, 0f, true),gbc);
		}
	}
	
	private class ExportFileActionListener implements ActionListener {
		@Override
		public void actionPerformed(ActionEvent e) {
			assert Configuration.getCurrentAtomData()!=null : "current AtomData is null";
			
			JFileChooser chooser = new JFileChooser();
			JPanel optionPanel = new JPanel();
			optionPanel.setLayout(new BoxLayout(optionPanel, BoxLayout.Y_AXIS));
			final JCheckBox exportAll = new JCheckBox("Export all files at once");
			optionPanel.add(exportAll);
			chooser.setAccessory(optionPanel);
			
			MDFileWriter writer = new ImdFileWriter();
			
			int result = chooser.showSaveDialog(JMainWindow.this);
			if (result == JFileChooser.APPROVE_OPTION){
				
				AtomData current = Configuration.getCurrentAtomData();
				
				JPrimitiveVariablesPropertiesDialog configDialog = 
						new JPrimitiveVariablesPropertiesDialog(JMainWindow.this, "Configure export");
				List<PrimitiveProperty<?>> options = 
						writer.getAdditionalProperties(current, exportAll.isSelected()); 
				if (options != null && options.size()>0){
					configDialog.startGroup("Options");
					for (PrimitiveProperty<?> p : options) configDialog.addProperty(p, false);
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
				configDialog.addProperty(eNum, false);
				configDialog.addProperty(eEle, false);
				configDialog.addProperty(etype, false);
				if (exportGrain) configDialog.addProperty(eg, false);
				if (exportRBV) configDialog.addProperty(erbv, false);
				
				for (int i=0; i<dci.size(); i++){
					DataColumnInfo d = dci.get(i);
					BooleanProperty bp = new BooleanProperty(d.getId(), d.getName(), "", true);
					configDialog.addProperty(bp, false);
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
				try {
					if (exportAll.isSelected()){
						File path = chooser.getSelectedFile().getParentFile();
						String prefix = chooser.getSelectedFile().getName();
						int num = 0;
						for (AtomData d : Configuration.getAtomDataIterable()){
							String newName = current.getName();
							newName = String.format("%s.%05d", prefix, num++);
							
							writer.writeFile(path, newName, d, null);
						}
					} else {
						String filename = chooser.getSelectedFile().getAbsolutePath();
						writer.writeFile(null, filename, current, null);
					}
				} catch (IOException e1) {
					e1.printStackTrace();
				}
				
			}
		}
	}
	
	private class ExportActionListener implements ActionListener{
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
	
	@Override
	public void atomDataChanged(AtomDataChangedEvent e) {
		if (e.getNewAtomData() == null)
			JMainWindow.this.setTitle("AtomViewer");
		else 
			JMainWindow.this.setTitle("AtomViewer ("+e.getNewAtomData().getName()+")");
	}
	
	public static void main(String[] args) {
		Locale.setDefault(Locale.US);
		//Headless processing
		if (args.length != 0){
			ImdFileLoader fileLoader = new ImdFileLoader();
			try {
				if (args.length < 5 || !args[0].equals("-h")){
					System.out.println("*************************************************");
					System.out.println("ERROR: Insufficient parameters for headless processing");
					System.out.println("USAGE: -h <inputFile> <crystalConfFile> <viewerConfFile> <outputFile> -options1 -option2...");
					System.out.println();
					System.out.println("<inputFile>: Input file");
					System.out.println("<crystalConfFile>: File containing the crystal information (usually named crystal.conf)");
					System.out.println("<viewerConfFile>: Viewer config file containing settings for post-processing (e.g. viewer.conf)");
					System.out.println("<outputFile>: Output file");
					System.out.println("Further options");
					System.out.println("-A: Write output in ASCII format and not in binary");
					System.out.println("-DN: Write dislocation network (if created)");
					System.out.println("*************************************************");
					System.exit(1);
				}
				Configuration.setCurrentFileLoader(fileLoader);
				final SwingWorker<AtomData, String> worker = fileLoader.getNewSwingWorker();
				
				worker.addPropertyChangeListener(new PropertyChangeListener() {
					@Override
					public void propertyChange(PropertyChangeEvent evt) {
						if ("progress" == evt.getPropertyName()) {
							String progressing = evt.getNewValue().toString();
							System.out.println("Processing "+progressing);
						}
						if ("operation" == evt.getPropertyName()) {
							String operation = evt.getNewValue().toString();
							System.out.println(operation);
						}
					}
				});
				
				RenderingConfiguration.setHeadless(true);
				
				
				ImportConfiguration ic = ImportConfiguration.getNewInstance();
				ic.loadProperties(new File(args[3]));
				ic.readConfigurationFile(new File(args[2]));
				File inputFile = new File(args[1]);

				Configuration.create();
				
				Configuration.setLastOpenedFolder(inputFile.getParentFile());
				AtomData data = fileLoader.readInputData(inputFile, null);
				
				boolean binaryFormat = true;
				boolean exportDislocations = false;
				
				if (args.length > 5){
					for (int i=5; i<args.length; i++){
						if (args[i].equals("-A"))
							binaryFormat = false;
						if (args[i].equals("-DN"))
							exportDislocations = true;
					}
				}

//				try {
//					GLProfile.initSingleton();
//					GLProfile maxProfile = GLProfile.getMaxProgrammableCore(true);
//					GLCapabilities glCapabilities = new GLCapabilities(maxProfile);
//					glCapabilities.setOnscreen(false);
//					
//					ViewerGLJPanel viewer = new ViewerGLJPanel(128, 128, glCapabilities);
//					viewer.setAtomData(data, true);
//					GLDrawableFactory factory = GLDrawableFactory.getFactory(maxProfile);
//					GLOffscreenAutoDrawable drawable = factory.createOffscreenAutoDrawable(null,glCapabilities,null,128,128);
//					drawable.display();
//					drawable.getContext().makeCurrent();
//					
//					viewer.init(drawable);
//					viewer.reshape(drawable, 0, 0, 128, 128);
//					viewer.makeScreenshot("test.png", "png", false, 1000, 1000);
//					drawable.destroy();
//				} catch (Exception e) {
//					e.printStackTrace();
//				}
				
				String outfile = args[4];
				ImdFileWriter writer = new ImdFileWriter(binaryFormat, binaryFormat);
				writer.writeFile(null, outfile, data, null);
				
				DataContainer dc = Configuration.getCurrentAtomData().getDataContainer(Skeletonizer.class);
				Skeletonizer skel = null;
				if (dc != null)
					skel = (Skeletonizer)dc;
				
				if (exportDislocations && skel != null){
					outfile = args[4]+"_dislocation.txt";
					skel.writeDislocationSkeleton(new File(outfile));
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			
			System.exit(0);
		} else {
			EventQueue.invokeLater(new Runnable() {
				public void run() {
					try {
						GLProfile.initSingleton();
						JMainWindow frame = new JMainWindow();
						frame.setVisible(true);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			});
		}
	}
	
	public void applyProcessWindowWithDisplay(AtomData data, ProcessingModule pm, boolean multipleFiles) {
		final SwingWorker<Void,Void> sw = new ProcessModuleWorker(pm, data, !multipleFiles);
		ProgressMonitor.createNewProgressMonitor(sw);
		final JProgressDisplayDialog progressDisplay = 
				new JProgressDisplayDialog(sw, JMainWindow.this, false);
		progressDisplay.setTitle("Analysis");
		
		sw.addPropertyChangeListener(new PropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent arg0) {
				if ( sw.isDone() || sw.isCancelled()){
					progressDisplay.dispose();
				}
			}
		});
		
		sw.execute();
		progressDisplay.setVisible(true);
	}
	
	protected class KeyBoardAction extends AbstractAction{
		private static final long serialVersionUID = 1L;

		@Override
		public void actionPerformed(ActionEvent e) {
			
			if (e.getActionCommand().equals("s")){
				stereoCheckBoxMenu.doClick();
			} else if (e.getActionCommand().equals("x")){
				if (Configuration.getCurrentAtomData().getNext()!=null){
					AtomData next = Configuration.getCurrentAtomData().getNext();
					Configuration.setCurrentAtomData(next, true, false);
				}
			} else if (e.getActionCommand().equals("y") || e.getActionCommand().equals("z")){
				if (Configuration.getCurrentAtomData().getPrevious() != null) {
					AtomData previous = Configuration.getCurrentAtomData().getPrevious();
					Configuration.setCurrentAtomData(previous, true, false);
				}
			} else if (e.getActionCommand().equals("f")){
				AtomData first = Configuration.getCurrentAtomData();
				while (first.getPrevious() != null)
					first = first.getPrevious();
				Configuration.setCurrentAtomData(first, true, false);
			} else if (e.getActionCommand().equals("l")){
				AtomData last = Configuration.getCurrentAtomData();
				while (last.getNext() != null)
					last = last.getNext();
				Configuration.setCurrentAtomData(last, true, false);
			} else if (e.getActionCommand().equals("r")){
				editRangeMenuItem.doClick();
			} else if (e.getActionCommand().equals("w")){
				whiteBackgroundCheckBoxMenu.doClick();
			} else if (e.getActionCommand().equals("c")){
				drawCoordinateSystemBoxMenu.doClick();
			} else if (e.getActionCommand().equals("p")){
				perspectiveCheckBoxMenu.doClick();
			}
		}
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
									Configuration.setCurrentAtomData(worker.get(), true, true);
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

	public static final class CursorController {
		public final static Cursor busyCursor = Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR);
		public final static Cursor defaultCursor = Cursor.getDefaultCursor();

		private CursorController() {}

		public static ActionListener createListener(final Component component, final ActionListener mainActionListener) {
			ActionListener actionListener = new ActionListener() {
				public void actionPerformed(ActionEvent ae) {
					try {
						component.setCursor(busyCursor);
						mainActionListener.actionPerformed(ae);
					} finally {
						component.setCursor(defaultCursor);
					}
				}
			};
			return actionListener;
		}
	}
	
	private class ProcessModuleWorker extends SwingWorker<Void, Void>{
		ProcessingModule pm;
		AtomData data;
		boolean updateViewer;
		
		ProcessModuleWorker(ProcessingModule pm, AtomData data, boolean updateViewer) {
			this.pm = pm;
			this.data = data;
			this.updateViewer = updateViewer;
		}
		
		@Override
		protected Void doInBackground() throws Exception {
			ProgressMonitor.getProgressMonitor().setCurrentFilename(data.getName());
			ProgressMonitor.getProgressMonitor().setActivityName(pm.getShortName());
	
			try {
				data.applyProcessingModule(pm);
				if (updateViewer)
					RenderingConfiguration.getViewer().updateAtoms();
			} catch (Exception e) {
				ProgressMonitor.getProgressMonitor().destroy();
				JOptionPane.showMessageDialog(JMainWindow.this, e.getMessage());
				e.printStackTrace();
			}
			ProgressMonitor.getProgressMonitor().destroy();
			return null;
		}
		
	}
	
	private class JGraphicOptionCheckBoxMenuItem extends JCheckBoxMenuItem{
		private static final long serialVersionUID = 1L;
		final RenderOption ro;
		
		public JGraphicOptionCheckBoxMenuItem(String label, RenderOption option) {
			super(label);
			this.ro = option;
			this.setSelected(ro.isEnabled());
			this.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					ro.setEnabled(JGraphicOptionCheckBoxMenuItem.this.isSelected());
				}
			});
		}
	}
}
