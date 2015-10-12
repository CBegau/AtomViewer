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

package processingModules.atomicModules;

import gui.JLogPanel;
import gui.JPrimitiveVariablesPropertiesDialog;
import gui.ProgressMonitor;
import gui.PrimitiveProperty.*;

import java.util.ArrayList;
import java.util.Vector;
import java.util.concurrent.Callable;

import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JRadioButton;
import javax.swing.JSeparator;

import model.Atom;
import model.AtomData;
import model.DataColumnInfo;
import model.NearestNeighborBuilder;
import model.DataColumnInfo.Component;
import processingModules.ClonableProcessingModule;
import processingModules.ProcessingResult;
import processingModules.toolchain.Toolchainable.ExportableValue;
import processingModules.toolchain.Toolchainable.ToolchainSupport;
import common.CommonUtils;
import common.ThreadPool;
import common.Tupel;
import common.Vec3;

@ToolchainSupport()
public class ParticleDensityModule extends ClonableProcessingModule {

	private static DataColumnInfo densityColumn = 
			new DataColumnInfo("Particle density" , "partDens" ,"");
	@ExportableValue
	private float radius = 5f;
	@ExportableValue
	private float scalingFactor = 1f;
	@ExportableValue
	private boolean useSmoothKernel = true;
	@ExportableValue
	private boolean weigthByMass = true;
	
	@Override
	public DataColumnInfo[] getDataColumnsInfo() {
		return new DataColumnInfo[]{densityColumn};
	}
	
	@Override
	public String getShortName() {
		return "Particle density";
	}
	
	@Override
	public boolean canBeAppliedToMultipleFilesAtOnce() {
		return true;
	}
	
	@Override
	public String getFunctionDescription() {
		return "Computes the local particle density. The computed value is locally smoothed within a defined radius.<br>"
				+ "If masses are defined per particle, the local mass density can be alternatively computed.";
	}
	
	@Override
	public String getRequirementDescription() {
		return "";
	}
	
	@Override
	public boolean isApplicable(AtomData atomData) {
		return true;
	}

	@Override
	public ProcessingResult process(final AtomData data) throws Exception {
		ProgressMonitor.getProgressMonitor().start(data.getAtoms().size());
		final float sphereVolume = radius*radius*radius*((float)Math.PI)*(4f/3f);
		
		final int v = data.getIndexForCustomColumn(densityColumn);
		
		final NearestNeighborBuilder<Atom> nnb = new NearestNeighborBuilder<Atom>(data.getBox(), radius, true);
		nnb.addAll(data.getAtoms());
		
		final int massColumn = data.getIndexForComponent(Component.MASS);
		final boolean scaleMass = weigthByMass && massColumn != -1;
		if (weigthByMass && !scaleMass)
			JLogPanel.getJLogPanel().addWarning("Mass not found",
					String.format("Weightened particle density selected, but mass column is missing in %s", data.getName()));
		
		Vector<Callable<Void>> parallelTasks = new Vector<Callable<Void>>();
		for (int i=0; i<ThreadPool.availProcessors(); i++){
			final int j = i;
			parallelTasks.add(new Callable<Void>() {
				@Override
				public Void call() throws Exception {
					final int start = (int)(((long)data.getAtoms().size() * j)/ThreadPool.availProcessors());
					final int end = (int)(((long)data.getAtoms().size() * (j+1))/ThreadPool.availProcessors());
					
					for (int i=start; i<end; i++){
						if ((i-start)%1000 == 0)
							ProgressMonitor.getProgressMonitor().addToCounter(1000);
						
						Atom a = data.getAtoms().get(i);	
						
						if (!useSmoothKernel){
							if (scaleMass){
								float density = 0f;
								for (Atom n : nnb.getNeigh(a))
									density += n.getData(massColumn);
								a.setData(density/sphereVolume*scalingFactor, v);
							} else {
								float density = ((nnb.getNeigh(a).size()+1)/sphereVolume);
								a.setData(density*scalingFactor, v);
							}
						} else {
							ArrayList<Tupel<Atom,Vec3>> neigh = nnb.getNeighAndNeighVec(a);
							float mass = scaleMass ? a.getData(massColumn) : 1f;
							
							//Include central particle a with d = 0
							float density = mass*CommonUtils.getM4SmoothingKernelWeight(0f, radius*0.5f);
							//Estimate local density based on distance to other particles
							for (int k=0, len = neigh.size(); k<len; k++){
								Tupel<Atom,Vec3> n = neigh.get(k);
								mass = scaleMass ? n.o1.getData(massColumn) : 1f;
								density += mass * CommonUtils.getM4SmoothingKernelWeight(n.o2.getLength(), radius*0.5f);
							}
							//Temporarily store the density of the particle 
							a.setData(density*scalingFactor, v);
						}
					}
					
					ProgressMonitor.getProgressMonitor().addToCounter((end-start)%1000);
					return null;
				}
			});
		}
		ThreadPool.executeParallel(parallelTasks);	
		
		ProgressMonitor.getProgressMonitor().stop();
		return null;
	}

	@Override
	public boolean showConfigurationDialog(JFrame frame, AtomData data) {
		JPrimitiveVariablesPropertiesDialog dialog = new JPrimitiveVariablesPropertiesDialog(frame, "Compute particle density");
		dialog.addLabel(getFunctionDescription());
		dialog.add(new JSeparator());
		FloatProperty avRadius = dialog.addFloat("avRadius", "Radius of the sphere", "", 5f, 0f, 1000f);
		
		FloatProperty scaling = dialog.addFloat("scalingFactor", "Scaling factor for the result (e.g. to particles/nm³)", "", 1f, 0f, 1e20f);
		
		ButtonGroup bg = new ButtonGroup();
		
		dialog.startGroup("Averaging method");
		final JRadioButton smoothingButton = new JRadioButton("Cubic spline smoothing kernel");
		JRadioButton arithmeticButton = new JRadioButton("Arithmetic average");
		
		final JCheckBox considerMassButton = new JCheckBox("Compute mass density", false);
		considerMassButton.setToolTipText("Weigth particles by their mass, thus computes the local mass density, "
				+ "instead of particle density (if possible)");
		if (data.getIndexForComponent(Component.MASS)==-1) considerMassButton.setEnabled(false);
		
		String wrappedToolTip = CommonUtils.getWordWrappedString("Computed average is the weightend average of all particles based on their distance d "
				+ "<br> (2-d)³-4(1-d)³ for d&lt;1/2r <br> (2-d)³ for 1/2r&lt;d&lt;r", smoothingButton);
		
		smoothingButton.setToolTipText(wrappedToolTip);
		arithmeticButton.setToolTipText("Computed average is the arithmetic average");
		
		smoothingButton.setSelected(true);
		arithmeticButton.setSelected(false);
		dialog.addComponent(smoothingButton);
		dialog.addComponent(arithmeticButton);
		dialog.addComponent(considerMassButton);
		bg.add(smoothingButton);
		bg.add(arithmeticButton);
		dialog.endGroup();
		
		boolean ok = dialog.showDialog();
		if (ok){
			this.weigthByMass = considerMassButton.isEnabled() && considerMassButton.isSelected();
			this.radius = avRadius.getValue();
			this.useSmoothKernel = smoothingButton.isSelected();
			this.scalingFactor = scaling.getValue();
		}
		return ok;
	}
}
