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

import gui.ViewerGLJPanel.RenderOption;

import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.*;
import java.text.DecimalFormat;
import java.util.Locale;

import javax.imageio.ImageIO;
import javax.media.opengl.GLCapabilities;
//import javax.media.opengl.GLDrawableFactory;
//import javax.media.opengl.GLOffscreenAutoDrawable;
import javax.media.opengl.GLProfile;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.jogamp.opengl.JoglVersion;

import common.ColorTable;
import common.ColorTable.ColorBarScheme;
import crystalStructures.CrystalStructure;
import model.*;
import model.io.*;
import model.io.MDFileLoader.InputFormat;
import model.Configuration.Options;
import model.dataContainer.DataContainer;

public class JMainWindow extends JFrame implements WindowListener {

	public static final String VERSION = "2.1"; 
	public static final String SUBVERSION = "1944";
	
	private static final long serialVersionUID = 1L;
	private ViewerGLJPanel viewer;
	
	private KeyBoardAction keyAction = new KeyBoardAction();
	
	private final JAtomicMenuPanel atomicMenu = new JAtomicMenuPanel(keyAction, this);
	private final JGraphicOptionCheckBoxMenuItem stereoCheckBoxMenu;
	private final JGraphicOptionCheckBoxMenuItem perspectiveCheckBoxMenu;
	private final JGraphicOptionCheckBoxMenuItem drawCoordinateSystemBoxMenu;
	private final JGraphicOptionCheckBoxMenuItem whiteBackgroundCheckBoxMenu;
	private final JMenuItem editRangeMenuItem;
	private final JMenuItem exportSkeletonFileMenuItem;
	private final JMenuItem editCrystalConf = new JMenuItem("Edit configuration");
	private final JMenu typeColorMenu;
	
	public JMainWindow() {
		//Load the icon
		try {
			final String resourcesPath = "resources/icon.png";
			ClassLoader l = this.getClass().getClassLoader();
			InputStream stream = l.getResourceAsStream(resourcesPath);
			if (stream!=null) this.setIconImage(ImageIO.read(stream));
		} catch (IOException e2) {}
		
		this.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		
		//Disable that ToolTips disappear automatically after short time
		ToolTipManager.sharedInstance().setDismissDelay(Integer.MAX_VALUE);
		
		GLProfile maxProfile = GLProfile.getMaxProgrammableCore(true);
		GLCapabilities glCapabilities = new GLCapabilities(maxProfile);
		
		viewer = new ViewerGLJPanel(650, 650, glCapabilities);
		viewer.setFocusable(true);
		viewer.requestFocus(); // the viewer now has focus, so receives key events
		this.setTitle("AtomViewer");
		
		JPanel renderPane = new JPanel();
		renderPane.setLayout(new BorderLayout());
		renderPane.add(viewer,BorderLayout.CENTER);
		
		final JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
		splitPane.add(renderPane);
		
		//Combine view buttons and the log panel into a container 
		Container cont = new Container();
		cont.setLayout(new GridBagLayout());
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = 0; gbc.gridy = 0;
		gbc.fill = GridBagConstraints.BOTH; gbc.weightx = 10; gbc.weighty = 1;
		cont.add(JLogPanel.getJLogPanel(),gbc);
		gbc.gridx++; gbc.weightx = 1;
		cont.add(new DefaultPerspectivesButtonContainer(),gbc);;
		
		splitPane.add(cont);
		splitPane.setDividerLocation(0.9);
		
		splitPane.addComponentListener(new ComponentAdapter() {
			public void componentResized(ComponentEvent evt) {
				splitPane.setDividerLocation(0.9);
			}
		});
		
		this.add(atomicMenu, BorderLayout.WEST);
		
		this.add(splitPane, BorderLayout.CENTER);
		addWindowListener(this);
		
		OpenMenuListener oml = new OpenMenuListener();
		
		JMenuBar menu = new JMenuBar();
		JMenu fileMenu = new JMenu("File");
		JMenuItem openIMDMenuItem = new JMenuItem("Open IMD file");
		openIMDMenuItem.setActionCommand(InputFormat.IMD.name());
		openIMDMenuItem.addActionListener(CursorController.createListener(this, oml));
		fileMenu.add(openIMDMenuItem);
		JMenuItem openLammpsMenuItem = new JMenuItem("Open Lammps file");
		openLammpsMenuItem.setActionCommand(InputFormat.LAMMPS.name());
		openLammpsMenuItem.addActionListener(CursorController.createListener(this, oml));
		fileMenu.add(openLammpsMenuItem);
		
		JMenuItem exportScreenShotMenuItem = new JMenuItem("Save Screenshot");
		exportScreenShotMenuItem.setActionCommand("Screenshot");
		exportScreenShotMenuItem.addActionListener(CursorController.createListener(this, new ExportActionListener()));
		fileMenu.add(exportScreenShotMenuItem);
		
		ExportFileActionListener exportFileListener = new ExportFileActionListener();
		
		JMenuItem exportAsciiFile = new JMenuItem("Export Data as ada-file (ASCII)");
		exportAsciiFile.addActionListener(CursorController.createListener(this, exportFileListener));
		exportAsciiFile.setActionCommand("ascii");
		fileMenu.add(exportAsciiFile);
		
		JMenuItem exportBinaryFile = new JMenuItem("Export Data as ada-file (Binary)");
		exportBinaryFile.addActionListener(CursorController.createListener(this, exportFileListener));
		exportBinaryFile.setActionCommand("binary");
		fileMenu.add(exportBinaryFile);
		
		exportSkeletonFileMenuItem = new JMenuItem("Export dislocation network");
		exportSkeletonFileMenuItem.addActionListener(CursorController.createListener(this, new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				assert Configuration.getCurrentAtomData().getSkeletonizer() != null : "No Skeleton";
				
				JFileChooser chooser = new JFileChooser();
				int result = chooser.showSaveDialog(JMainWindow.this);
				if (result == JFileChooser.APPROVE_OPTION){
					try {
						Configuration.getCurrentAtomData().getSkeletonizer().writeDislocationSkeleton(chooser.getSelectedFile());
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
					RenderRange rr = viewer.getRenderRange();
					new JRenderedIntervalEditorDialog(JMainWindow.this, rr);
					viewer.updateAtoms();
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
				String str = JOptionPane.showInputDialog(null, "Enter new sphere size (current "+Float.toString(viewer.getSphereSize()) +"): ", 
						"Enter sphere size", JOptionPane.QUESTION_MESSAGE);
				try {
					if (str!=null){
						float f = Float.parseFloat(str);
						viewer.setSphereSize(f);
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
		if (!Configuration.Options.SIMPLE.isEnabled()) viewMenu.add(drawIndentBoxMenu);
		viewMenu.add(drawLegendMenuItem);
		
		JMenu legendStyleMenu = new JMenu("Legend style");
		final JCheckBoxMenuItem swapLegend = new JCheckBoxMenuItem("Swap colors");
		swapLegend.setSelected(ColorTable.isColorBarSwapped());
		swapLegend.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				ColorTable.setColorBarSwapped(swapLegend.isSelected());
				viewer.updateAtoms();
				Configuration.Options.saveProperties();
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
						Configuration.Options.saveProperties();
						viewer.updateAtoms();
					}
				});
			}
		}
		viewMenu.add(legendStyleMenu);
		
		menu.add(viewMenu);
		
		JMenu editMenu = new JMenu("Edit");
		editMenu.add(editCrystalConf);
		editCrystalConf.setEnabled(false);
		editCrystalConf.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				File selectedFile = new File(Configuration.getCurrentAtomData().getFullPathAndFilename());
				
				JCrystalConfigurationDialog ccd = 
						new JCrystalConfigurationDialog(JMainWindow.this, 
								Configuration.getLastOpenedFolder(), selectedFile, true);
				if (ccd.isSavedSuccessfully()) {
					JOptionPane.showMessageDialog(JMainWindow.this, "File(s) must be reloaded for changes to be effective");
				}
			}
		});
		
		typeColorMenu = new JMenu("Colors");
		JMenuItem resetColorMenuItem = new JMenuItem("Reset colors of atom types");
		resetColorMenuItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				CrystalStructure c = Configuration.getCrystalStructure();
				if (c!=null) {
					c.resetColors();
					atomicMenu.updateValues();
					viewer.updateAtoms();
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
						CrystalStructure cs = Configuration.getCrystalStructure();
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
						CrystalStructure cs = Configuration.getCrystalStructure();
						for (int i = 0; i<colors.length && i<cs.getNumberOfTypes(); i++){
							cs.setGLColors(i, colors[i]);
						}
						
						atomicMenu.updateValues();
						viewer.updateAtoms();
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
		
		final JMenuItem extrasMenu = new JMenu("Extras");
		for (final DataContainer dc : DataContainer.getDataContainer()){
			final JMenuItem extraMenuItem = new JMenuItem(dc.getName());
			extraMenuItem.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					if (Configuration.getCurrentAtomData() != null){
						Configuration.getCurrentAtomData().addAdditionalData(dc);
						atomicMenu.setAtomData(Configuration.getCurrentAtomData(), viewer, false);
						viewer.reDraw();
					}
				}
			});
			extrasMenu.add(extraMenuItem);
		}
		menu.add(extrasMenu);
		
		final JMenuItem settingsMenu = new JMenu("Settings");
		for (Options o : Configuration.Options.values()){
			settingsMenu.add(new JOptionCheckBoxMenuItem(o));
		}
		menu.add(settingsMenu);
		
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
				"Press mouse button + Alt -> Zoom in/out"+
				"</body></html>";
				JOptionPane.showMessageDialog(JMainWindow.this, message, "AtomViewer", JOptionPane.INFORMATION_MESSAGE);
			}
		});
		final JMenuItem aboutMenuItem = new JMenuItem("About");
		aboutMenuItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				String message = "<html><body>" +
						"AtomViewer  Copyright (C) 2014, ICAMS, Ruhr-Universtät Bochum <br>" +
						"AtomViewer is a tool to display and analyse atomistic simulations<br><br>" +
						"AtomViewer Version "+JMainWindow.VERSION+"-"+JMainWindow.SUBVERSION+"<br>"+
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
				float[] m = viewer.getPov();
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
					viewer.setPOV(m);
				}
			}
		});
	}

	//region WindowListener
	public void windowActivated(WindowEvent e) {
		viewer.repaint();
	}

	public void windowDeiconified(WindowEvent e) {
		viewer.repaint();
	}
	
	public void windowOpened(WindowEvent e) {
		viewer.repaint();
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
					Options.saveProperties();
				}
			});
		}
	}
	
	private class DefaultPerspectivesButtonContainer extends Container{
		private static final long serialVersionUID = 1L;

		DefaultPerspectivesButtonContainer() {
			this.setLayout(new GridLayout(4,1));
			final JButton topViewButton = new JButton("Top");
			final JButton frontViewButton = new JButton("Front");
			final JButton sideViewButton = new JButton("Side");
			final JButton resetZoomViewButton = new JButton("Reset zoom");
			
			ActionListener al = new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					if (viewer != null) {
						if (arg0.getSource() == topViewButton) {
							if ((arg0.getModifiers() & ActionEvent.SHIFT_MASK) != 0) viewer.setPOV(180f, 0f, 0f);
							else viewer.setPOV(0f, 0f, 0f);
						} else if (arg0.getSource() == frontViewButton) {
							if ((arg0.getModifiers() & ActionEvent.SHIFT_MASK) != 0) viewer.setPOV(-90f, 0f, 180f);
							else viewer.setPOV(-90f, 0f, 0f);
						} else if (arg0.getSource() == sideViewButton){
							if ((arg0.getModifiers() & ActionEvent.SHIFT_MASK) != 0) viewer.setPOV(-90f, 0f, -90f);
							else viewer.setPOV(-90f, 0f, 90f);
						} else if (arg0.getSource() == resetZoomViewButton){
							viewer.resetZoom();
						}
					}
				}
			};
			
			topViewButton.addActionListener(al);
			frontViewButton.addActionListener(al);
			sideViewButton.addActionListener(al);
			resetZoomViewButton.addActionListener(al);
			
			this.add(topViewButton);
			this.add(frontViewButton);
			this.add(sideViewButton);
			this.add(resetZoomViewButton);
		}
	}
	
	private class ExportFileActionListener implements ActionListener {
		@Override
		public void actionPerformed(ActionEvent e) {
			assert Configuration.getCurrentAtomData()!=null : "current AtomData is null";
			
			boolean binary = e.getActionCommand().equals("binary");
			JFileChooser chooser = new JFileChooser();
			JPanel optionPanel = new JPanel();
			final JCheckBox exportAll = new JCheckBox("Export all");
			optionPanel.add(exportAll);
			chooser.setAccessory(optionPanel);
			
			int result = chooser.showSaveDialog(JMainWindow.this);
			if (result == JFileChooser.APPROVE_OPTION){
				if (exportAll.isSelected()){
					try {
						AtomData current = Configuration.getCurrentAtomData();
						while (current.getPrevious() != null)
							current = current.getPrevious();
						
						int num=0;
						String path = chooser.getSelectedFile().getParent();
						String prefix = chooser.getSelectedFile().getName();
						
						
						do {
							String newName = current.getName();
							if (newName.endsWith(".ada")) newName = newName.substring(0, newName.length()-4);
							if (newName.endsWith(".ada.gz")) newName = newName.substring(0, newName.length()-7);
								
							if (newName.endsWith(".chkpt")) newName = newName.substring(0, newName.length()-6);
							if (newName.endsWith(".chkpt.gz")) newName = newName.substring(0, newName.length()-9);
							
							newName = String.format("%s%s.ada", prefix, newName, num++);
							if (binary) newName += ".gz";
							current.printToFile(new File(path, newName), binary, binary);
							current = current.getNext(); 
						} while (current != null);
					} catch (IOException e1) {
						e1.printStackTrace();
					}
				} else {
					try {
						String filename = chooser.getSelectedFile().getAbsolutePath();
						if (binary){
							if (filename.endsWith(".ada")) filename += ".gz";
							if (!filename.endsWith(".ada.gz")) filename += ".ada.gz";
						} else if (!filename.endsWith(".ada")) filename += ".ada";
						Configuration.getCurrentAtomData().printToFile(new File(filename), binary, binary);
					} catch (IOException e1) {
						e1.printStackTrace();
					}
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
				GridBagConstraints gbc = new GridBagConstraints();
				gbc.anchor = GridBagConstraints.SOUTHWEST;
				gbc.weightx = 0.5; gbc.weighty = 0;
				gbc.gridx = 0; gbc.gridy = 0;
				
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
				widthTextField.setValue(JMainWindow.this.viewer.getWidth());
				imageSizeDialogExtension.add(widthTextField, gbc); gbc.gridx++;
				
				JFormattedTextField heightTextField = new JFormattedTextField(new DecimalFormat("#"));
				heightTextField.setValue(JMainWindow.this.viewer.getHeight());
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
							
						JMainWindow.this.viewer.makeScreenshot(filename, fileEnding, sequence, width, height);
					} catch (Exception e1) {
						JOptionPane.showMessageDialog(null, e1.getLocalizedMessage());
					}
				}
			}
		}
	}
	
	public static void main(String[] args) {
		Locale.setDefault(Locale.US);
		//Headless processing
		if (args.length != 0){
			ImdFileLoader fileLoader = new ImdFileLoader(null);
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
				Configuration.currentFileLoader = fileLoader;
				
				fileLoader.addPropertyChangeListener(new PropertyChangeListener() {
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
				
				Configuration.setHeadless(true);
				
				ImportStates.loadProperties(new File(args[3]));
				ImportStates.readConfigurationFile(new File(args[2]));
				File inputFile = new File(args[1]);

				Configuration.create();
				
				Configuration.setLastOpenedFolder(inputFile.getParentFile());
				AtomData data = fileLoader.readInputData(inputFile);
				
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
				
				String outfile = args[4]+".ada";
				if (binaryFormat)
					outfile+=".gz";
				data.printToFile(new File(outfile), binaryFormat, binaryFormat);
				
				if (exportDislocations && data.getSkeletonizer() != null){
					outfile = args[4]+"_dislocation.txt";
					data.getSkeletonizer().writeDislocationSkeleton(new File(outfile));
				}
			} catch (IOException e) {
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
	
	protected class KeyBoardAction extends AbstractAction{
		private static final long serialVersionUID = 1L;

		@Override
		public void actionPerformed(ActionEvent e) {
			
			if (e.getActionCommand().equals("s")){
				stereoCheckBoxMenu.doClick();
			} else if (e.getActionCommand().equals("x")){
				if (Configuration.getCurrentAtomData().getNext()!=null){
					AtomData next = Configuration.getCurrentAtomData().getNext();
					Configuration.setCurrentAtomData(next);
					viewer.setAtomData(next, false);
					atomicMenu.setAtomData(next, viewer, false);
					JMainWindow.this.setTitle("AtomViewer ("+next.getName()+")");
				}
			} else if (e.getActionCommand().equals("y") || e.getActionCommand().equals("z")){
				if (Configuration.getCurrentAtomData().getPrevious() != null) {
					AtomData previous = Configuration.getCurrentAtomData().getPrevious();
					Configuration.setCurrentAtomData(previous);
					viewer.setAtomData(previous, false);
					atomicMenu.setAtomData(previous, viewer, false);
					JMainWindow.this.setTitle("AtomViewer (" + previous.getName() + ")");
				}
			} else if (e.getActionCommand().equals("f")){
				AtomData current = Configuration.getCurrentAtomData();
				while (current.getPrevious() != null)
					current = current.getPrevious();
				atomicMenu.setAtomData(current, viewer, false);
				viewer.setAtomData(current, false);
				Configuration.setCurrentAtomData(current);
				JMainWindow.this.setTitle("AtomViewer (" + current.getName() + ")");
			} else if (e.getActionCommand().equals("l")){
				AtomData current = Configuration.getCurrentAtomData();
				while (current.getNext() != null)
					current = current.getNext();
				atomicMenu.setAtomData(current, viewer, false);
				viewer.setAtomData(current, false);
				Configuration.setCurrentAtomData(current);
				JMainWindow.this.setTitle("AtomViewer (" + current.getName() + ")");
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
			JMDFileChooser chooser;
			if (e.getActionCommand().equals(InputFormat.IMD.name()))
				chooser = new JMDFileChooser(InputFormat.IMD);
			else if (e.getActionCommand().equals(InputFormat.LAMMPS.name()))
				chooser = new JMDFileChooser(InputFormat.LAMMPS);
			else return;
			
			final MDFileLoader fileLoader;
			if (e.getActionCommand().equals(InputFormat.IMD.name()))
				fileLoader = new ImdFileLoader(chooser);
			else if (e.getActionCommand().equals(InputFormat.LAMMPS.name()))
				fileLoader = new LammpsFileLoader(chooser);
			else return;
			
			Configuration.currentFileLoader = fileLoader;
			
			if (Configuration.getLastOpenedFolder() != null) {
				chooser.setCurrentDirectory(Configuration.getLastOpenedFolder());
			}
			int result = chooser.showOpenDialog(JMainWindow.this);
			if (result == JFileChooser.APPROVE_OPTION){	
				if (Configuration.getCurrentAtomData() != null) {
					Configuration.getCurrentAtomData().clear();
					viewer.getRenderRange().reset();
				}
				boolean successfulCreated = chooser.createConfiguration();
				if (!successfulCreated) return;
				Configuration.setLastOpenedFolder(chooser.getSelectedFile().getParentFile());
				editCrystalConf.setEnabled(true);
				typeColorMenu.setEnabled(true);
				
				exportSkeletonFileMenuItem.setEnabled(ImportStates.SKELETONIZE.isActive());

				final JProgressDisplayDialog progressDisplay = new JProgressDisplayDialog(fileLoader, JMainWindow.this);
				progressDisplay.setTitle("Opening files...");
				
				fileLoader.addPropertyChangeListener(new PropertyChangeListener() {
					@Override
					public void propertyChange(PropertyChangeEvent arg0) {
						if ( fileLoader.isDone() || fileLoader.isCancelled()){
							try {
								//Retrieve the results from the background worker
								if (!fileLoader.isCancelled())
									Configuration.setCurrentAtomData(fileLoader.get());
							} catch (Exception e) {
								progressDisplay.dispose();
								e.printStackTrace();
								fileLoader.removePropertyChangeListener(this);
								JOptionPane.showMessageDialog(null, e.toString());
							} finally {
								progressDisplay.dispose();
							}
						}
					}
				});
				
				Configuration.setCurrentAtomData(null);
				viewer.setAtomData(null, true);
				fileLoader.execute();
				
				progressDisplay.setVisible(true);
				if (Configuration.getCurrentAtomData() != null){
					viewer.setAtomData(Configuration.getCurrentAtomData(), true);
					atomicMenu.setAtomData(Configuration.getCurrentAtomData(), viewer, true);
					JMainWindow.this.setTitle("AtomViewer ("+Configuration.getCurrentAtomData().getName()+")");
				}
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
