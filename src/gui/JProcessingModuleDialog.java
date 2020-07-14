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
package gui;

import java.awt.*;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import model.Configuration;
import processingModules.ProcessingModule;
import processingModules.atomicModules.*;
import processingModules.otherModules.*;
import processingModules.otherModules.dislocationDensity.DislocationDensityTensorModule;
import crystalStructures.MonoclinicNiTi;

public class JProcessingModuleDialog extends JDialog {
	private final static DefaultMutableTreeNode root = new DefaultMutableTreeNode("Analysis modules");
	
	static {
		DefaultMutableTreeNode atomic = new DefaultMutableTreeNode("Per atom analysis");
		root.add(atomic);
		DefaultMutableTreeNode atomicGP = new DefaultMutableTreeNode("General");
		atomic.add(atomicGP);
		atomicGP.insert(new ModuleTreeWrapper(new CentroSymmetryModule()), 0);
		atomicGP.insert(new ModuleTreeWrapper(new TemperatureModule()), 1);
		atomicGP.insert(new ModuleTreeWrapper(new LatticeRotationModule()), 2);
		atomicGP.insert(new ModuleTreeWrapper(new SlipVectorModule()), 3);
		atomicGP.insert(new ModuleTreeWrapper(new CommonNeighborsAnalysisModule()), 4);
		
		DefaultMutableTreeNode atomicDens = new DefaultMutableTreeNode("Densities & Volumes");
		atomic.add(atomicDens);
		atomicDens.insert(new ModuleTreeWrapper(new CoordinationNumberModule()), 0);
		atomicDens.insert(new ModuleTreeWrapper(new ParticleDensityModule()), 1);
		atomicDens.insert(new ModuleTreeWrapper(new AtomicVolumeModule()), 2);
		atomicDens.insert(new ModuleTreeWrapper(new ConcentrationModule()), 3);
		          
		DefaultMutableTreeNode atomicAvg = new DefaultMutableTreeNode("Averages & Differences");
		atomic.add(atomicAvg);
		atomicAvg.insert(new ModuleTreeWrapper(new DisplacementModule()), 0);
		atomicAvg.insert(new ModuleTreeWrapper(new SpatialAveragingModule()), 1);
		atomicAvg.insert(new ModuleTreeWrapper(new SpatialAveragingVectorModule()), 2);
		atomicAvg.insert(new ModuleTreeWrapper(new DeltaValueModule()), 3);
		atomicAvg.insert(new ModuleTreeWrapper(new DeltaVectorModule()), 4);
		atomicAvg.insert(new ModuleTreeWrapper(new SpatialDerivatiesModule()), 5);
		
		DefaultMutableTreeNode filteratom = new DefaultMutableTreeNode("Filter");
		atomic.add(filteratom);
		filteratom.insert(new ModuleTreeWrapper(new FilterSurfaceModule()), 0);
		filteratom.insert(new ModuleTreeWrapper(new RemoveInvisibleAtomsModule()), 1);
		
		DefaultMutableTreeNode atomicSpecial = new DefaultMutableTreeNode("Special");
		atomic.add(atomicSpecial);
		atomicSpecial.insert(new ModuleTreeWrapper(new MonoclinicNiTi()), 0);
		atomicSpecial.insert(new ModuleTreeWrapper(new KeyenceMarkerExport()), 1);
		
		DefaultMutableTreeNode defect = new DefaultMutableTreeNode("Defect analysis");
		root.add(defect);
		defect.insert(new ModuleTreeWrapper(new RbvModule()), 0);
		defect.insert(new ModuleTreeWrapper(new SkeletonizerModule()), 1);
		defect.insert(new ModuleTreeWrapper(new VacancyDetectionModule()), 2);
		defect.insert(new ModuleTreeWrapper(new DislocationDensityTensorModule()), 3);
		defect.insert(new ModuleTreeWrapper(new GrainIdentificationModule()), 4);
		defect.insert(new ModuleTreeWrapper(new SurfaceApproximationModule()), 5);
		
		DefaultMutableTreeNode external = new DefaultMutableTreeNode("Load from external data");
		root.add(external);
		external.insert(new ModuleTreeWrapper(new StressDataModule()), 0);
		external.insert(new ModuleTreeWrapper(new LoadBalancingProcessingModule()), 1);
	}

	public enum SelectedState {CANCEL, ONE_FILE, ALL_FILES};
	private static final long serialVersionUID = 1L;
	
	private ProcessingModule selectedProcessingModule = null;
	private SelectedState state = SelectedState.CANCEL;
	
	
	public JProcessingModuleDialog(JFrame frame){
		super(frame, true);
	}
	
	public SelectedState showDialog(){
		this.setTitle("Analysis modules");

		GraphicsDevice gd = this.getOwner().getGraphicsConfiguration().getDevice();
		
		final JTree moduleTree = new JTree(root);
		moduleTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
		moduleTree.setCellRenderer(new ProcessingModuleCellRenderer());
		expandAll(moduleTree);
		
		final JButton applyButton = new JButton("Apply on current data sets");
		applyButton.setEnabled(false);
		applyButton.addActionListener(l -> {
			selectedProcessingModule = ((ModuleTreeWrapper)moduleTree.getSelectionPath().getLastPathComponent()).module;
			if (selectedProcessingModule!=null && 
					selectedProcessingModule.isApplicable(Configuration.getCurrentAtomData()))
			state = SelectedState.ONE_FILE;
			dispose();
		});
		
		final JButton applyAllButton = new JButton("Apply on all opened data sets");
		applyAllButton.setEnabled(false);
		applyAllButton.addActionListener(l -> {
			selectedProcessingModule = ((ModuleTreeWrapper)moduleTree.getSelectionPath().getLastPathComponent()).module;
			if (selectedProcessingModule!=null)
				state = SelectedState.ALL_FILES;
			dispose();
		});

		
		final JButton cancelButton = new JButton("Cancel");
		cancelButton.addActionListener(l->dispose());
		
		this.setLayout(new BorderLayout());
		this.add(new JScrollPane(moduleTree,
				JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED), BorderLayout.WEST);
		
		final JLabel descriptionlabel = new JLabel("");
		descriptionlabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		descriptionlabel.setAlignmentY(Component.TOP_ALIGNMENT);
		final JLabel requirementlabel = new JLabel("");
		requirementlabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		requirementlabel.setAlignmentY(Component.TOP_ALIGNMENT);
		
		Container c = new Container();
		c.setLayout(new GridLayout(2,1));
		
		final Container descriptionContainer = new Container(); 
		descriptionContainer.setPreferredSize(new Dimension(550, 320));
		descriptionContainer.setLayout(new BoxLayout(descriptionContainer, BoxLayout.Y_AXIS));
		descriptionContainer.add(new JLabel("Description"));
		descriptionContainer.add(new JScrollPane(descriptionlabel,
				JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED));
		c.add(descriptionContainer);
		
		final Container requirementContainer = new Container();
		requirementContainer.setPreferredSize(new Dimension(550, 320));
		requirementContainer.setLayout(new BoxLayout(requirementContainer, BoxLayout.Y_AXIS));
		requirementContainer.add(new JLabel("Requirements"));
		requirementContainer.add(new JScrollPane(requirementlabel,
				JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED));
		c.add(requirementContainer);
		this.add(c,BorderLayout.CENTER);
		
		c = new Container();
		c.setLayout(new GridBagLayout());
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.fill = GridBagConstraints.BOTH;
		gbc.gridx = 0; gbc.gridy = 0;
		gbc.weightx = 0.5;
		c.add(applyButton, gbc); gbc.gridy++;
		c.add(applyAllButton, gbc);
		gbc.gridx = 1; gbc.gridy = 0; gbc.gridheight = 2;
		c.add(cancelButton, gbc); 
		
		this.add(c,BorderLayout.SOUTH);
		
		moduleTree.addTreeSelectionListener(e -> {			
			Object o = moduleTree.getSelectionPath().getLastPathComponent();
			if (o instanceof ModuleTreeWrapper){
				ProcessingModule pm = ((ModuleTreeWrapper)o).module;
				
				int size = Math.max(20, descriptionContainer.getSize().width-20);
				String req = pm.getRequirementDescription();
				if (req == null || req.isEmpty()) req = "none";
				requirementlabel.setText("<html><table><tr><td width='"+size+"'>"+ req +"</td></tr></table></html>");
				descriptionlabel.setText("<html><table><tr><td width='"+size+"'>"+ pm.getFunctionDescription() +"</td></tr></table></html>");
				
				boolean applicable = pm.isApplicable(Configuration.getCurrentAtomData());
				applyButton.setEnabled(applicable);
				applyAllButton.setEnabled(pm.canBeAppliedToMultipleFilesAtOnce() && applicable);
			} else {
				requirementlabel.setText("");
				descriptionlabel.setText("");
				applyAllButton.setEnabled(false);
				applyButton.setEnabled(false);
			}
		});
		
		this.pack();
		
		this.setLocation( (gd.getDisplayMode().getWidth()-this.getWidth())>>1, 
				(gd.getDisplayMode().getHeight()-this.getHeight())>>1);
		this.setVisible(true);
		
		return state;
	}
	
	private void expandAll(JTree tree) {
		TreeNode root = (TreeNode) tree.getModel().getRoot();
		expandAll(tree, new TreePath(root));
	}

	private void expandAll(JTree tree, TreePath parent) {
		TreeNode node = (TreeNode) parent.getLastPathComponent();
		for (int i = 0; i < node.getChildCount(); i++) {
			TreeNode n = node.getChildAt(i);
			TreePath path = parent.pathByAddingChild(n);
			expandAll(tree, path);
		}
		tree.expandPath(parent);
	}
	
	public ProcessingModule getSelectedProcessingModule() {
		return selectedProcessingModule;
	}
	
	class ProcessingModuleCellRenderer extends DefaultTreeCellRenderer{
		private static final long serialVersionUID = 1L;
		@Override
		public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded,
				boolean leaf, int row, boolean hasFocus) {

			super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
			setIcon(null);
			if (value != null) {
				if (value instanceof ModuleTreeWrapper) {
					ProcessingModule m = ((ModuleTreeWrapper) value).module;
					setText(m.getShortName());
					setEnabled(m.isApplicable(Configuration.getCurrentAtomData()));
					
				} else setText(value.toString());
			}
			return this;
		}
	}

	public static class ModuleTreeWrapper extends DefaultMutableTreeNode{
		private static final long serialVersionUID = 1L;
		private ProcessingModule module;
	    
	    private ModuleTreeWrapper(ProcessingModule module){
	    	this.module = module;
	    }
	    
	    @Override
	    public String toString() {
	    	return module.getShortName();
	    }
	}
}
