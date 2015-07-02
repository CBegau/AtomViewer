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
package processingModules;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import model.AtomData;
import model.Configuration;
import model.RbvBuilder;
import model.dataContainer.*;
import model.dataContainer.dislocationDensity.DislocationDensityTensorModule;
import model.skeletonizer.SkeletonizerModule;
import crystalStructures.MonoclinicNiTi;

public class AvailableProcessingModules {
private final static ArrayList<ProcessingModule> atomicScaleModules;
private final static ArrayList<ProcessingModule> otherModules;
	
	static {
		//Creating the list of all available processingModules
		atomicScaleModules = new ArrayList<ProcessingModule>();
		otherModules = new ArrayList<ProcessingModule>();
		atomicScaleModules.add(new CentroSymmetryModule());
		atomicScaleModules.add(new TemperatureModule());
		atomicScaleModules.add(new LatticeRotationModule());
		atomicScaleModules.add(new SpatialAveragingModule());
		atomicScaleModules.add(new SlipVectorModule());
		atomicScaleModules.add(new DisplacementModule());
		atomicScaleModules.add(new DeltaValueModule());
		atomicScaleModules.add(new CoordinationNumberModule());
		atomicScaleModules.add(new AtomicVolumeModule());
		atomicScaleModules.add(new SpatialAveragingVectorModule());
		atomicScaleModules.add(new DeltaVectorModule());
		atomicScaleModules.add(new MonoclinicNiTi());
		
		otherModules.add(new FilterSurfaceModule());
		otherModules.add(new RbvBuilder());
		otherModules.add(new SkeletonizerModule());
		otherModules.add(new VacancyDetectionModule());//geht
		otherModules.add(new SurfaceApproximationModule());	//geht
		otherModules.add(new DislocationDensityTensorModule());
		otherModules.add(new StressDataModule());	//geht
		otherModules.add(new LoadBalancingProcessingModule());	//geht
		otherModules.add(new GrainIdentificationModule());
		otherModules.add(new RemoveInvisibleAtomsModule());
	}
	
	public static java.util.List<ProcessingModule> getAtomicScaleProcessingModule() {
		return Collections.unmodifiableList(atomicScaleModules);
	}
	
	public static java.util.List<ProcessingModule> getOtherProcessingModule() {
		return Collections.unmodifiableList(otherModules);
	}
	
	
	public static class JProcessingModuleDialog extends JDialog{
		public enum SelectedState {CANCEL, ONE_FILE, ALL_FILES};
		private static final long serialVersionUID = 1L;
		
		private ProcessingModule selectedProcessingModule = null;
		private SelectedState state = SelectedState.CANCEL;
		private AtomData currentAtomData = null;
		
		
		public JProcessingModuleDialog(JFrame frame){
			super(frame, true);
		}
		
		public SelectedState showDialog(List<ProcessingModule> modules){
			this.setTitle("Analysis modules");
			
			currentAtomData = Configuration.getCurrentAtomData();
			
			
			GraphicsDevice gd = this.getOwner().getGraphicsConfiguration().getDevice();
			
			final JList moduleList = new JList(new Vector<ProcessingModule>(modules));
			moduleList.setCellRenderer(new ProcessingModuleCellRenderer());
			
			final JButton applyButton = new JButton("Apply on current data sets");
			applyButton.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					selectedProcessingModule = (ProcessingModule)moduleList.getSelectedValue();
					if (selectedProcessingModule!=null && 
							selectedProcessingModule.isApplicable(currentAtomData))
					state = SelectedState.ONE_FILE;
					dispose();
				}
			});
			
			final JButton applyAllButton = new JButton("Apply on all opened data sets");
			applyAllButton.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					selectedProcessingModule = (ProcessingModule)moduleList.getSelectedValue();
					if (selectedProcessingModule!=null)
						state = SelectedState.ALL_FILES;
					dispose();
				}
			});

			
			final JButton cancelButton = new JButton("Cancel");
			cancelButton.addActionListener(new ActionListener(){
				@Override
				public void actionPerformed(ActionEvent e) {
					dispose();
				}
			});
			this.setLayout(new BorderLayout());
			this.add(new JScrollPane(moduleList,
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
			descriptionContainer.setPreferredSize(new Dimension(550, 400));
			descriptionContainer.setLayout(new BoxLayout(descriptionContainer, BoxLayout.Y_AXIS));
			descriptionContainer.add(new JLabel("Description"));
			descriptionContainer.add(new JScrollPane(descriptionlabel,
					JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED));
			c.add(descriptionContainer);
			
			final Container requirementContainer = new Container();
			requirementContainer.setPreferredSize(new Dimension(550, 400));
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
			
			moduleList.addListSelectionListener(new ListSelectionListener() {
				@Override
				public void valueChanged(ListSelectionEvent e) {
					ProcessingModule pm = (ProcessingModule) moduleList.getSelectedValue();
					if (pm != null){
						int size = Math.max(20, descriptionContainer.getSize().width-20);
						String req = pm.getRequirementDescription();
						if (req == null || req.isEmpty()) req = "none";
						requirementlabel.setText("<html><table><tr><td width='"+size+"'>"+ req +"</td></tr></table></html>");
						descriptionlabel.setText("<html><table><tr><td width='"+size+"'>"+ pm.getFunctionDescription() +"</td></tr></table></html>");
						
						applyAllButton.setEnabled(pm.canBeAppliedToMultipleFilesAtOnce());
					}
				}
			});
			
			this.pack();
			this.setLocation( (gd.getDisplayMode().getWidth()-this.getWidth())>>1, 
					(gd.getDisplayMode().getHeight()-this.getHeight())>>1);
			this.setVisible(true);
			
			return state;
		}
		
		public ProcessingModule getSelectedProcessingModule() {
			return selectedProcessingModule;
		}
		
		class ProcessingModuleCellRenderer extends DefaultListCellRenderer{
			private static final long serialVersionUID = 1L;
			@Override
			public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus)
			{
			    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
			    if (value != null)
			    {
			        ProcessingModule m = (ProcessingModule)value;
			        setText(m.getShortName());
			        setEnabled(m.isApplicable(currentAtomData));
			    }
			    return this;
			}
			
		}
	}
}
