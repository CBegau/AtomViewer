// Part of AtomViewer: AtomViewer is a tool to display and analyse
// atomistic simulations
//
// Copyright (C) 2014  ICAMS, Ruhr-Universität Bochum
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

import gui.JLogPanel;

import java.util.ArrayList;
import java.util.Vector;
import java.util.concurrent.Callable;

import common.ThreadPool;
import model.Atom;
import model.AtomData;
import model.Configuration;
import model.DataColumnInfo;
import model.DataColumnInfo.Component;
import model.NearestNeighborBuilder;

public class ComputeTemperatureModule implements ProcessingModule {

	private float averagingDistance = 5f;
	
	@Override
	public String getFunctionDescription() {
		return "Computes temperatures from atomic masses and velocities, "
				+ "considering the center of mass velocity in a given spherical area";
	}
	
	@Override
	public String getRequirementDescription() {
		return null;
	}
	
	@Override
	public String getShortName() {
		return "Compute temperature";
	}

	@Override
	public boolean isApplicable() {
		int m = -1;
		int v_x = -1;
		int v_y = -1;
		int v_z = -1;
		for (int i=0; i<Configuration.getSizeDataColumns(); i++){
			DataColumnInfo cci = Configuration.getDataColumnInfo(i);
			
			if (cci.getComponent() == Component.MASS)
				m = i;
			
			if (cci.getComponent() == Component.VELOCITY_X)
				v_x = i;
			
			if (cci.getComponent() == Component.VELOCITY_Y)
				v_y = i;
			
			if (cci.getComponent() == Component.VELOCITY_Z)
				v_z = i;
		}
		
		if (m==-1 || v_x==-1 || v_y==-1 || v_z==-1)
			return false;
		else return true;
	}

	@Override
	public DataColumnInfo[] getDataColumnsInfo() {
		return new DataColumnInfo[]{new DataColumnInfo("Temperature", "temp", "K", 1f, 5f)};
	}
	
	@Override
	public void process(final AtomData data) throws Exception {
		final NearestNeighborBuilder<Atom> nnb = new NearestNeighborBuilder<Atom>(averagingDistance);
		
		int t = -1;
		int m = -1;
		int v_x = -1;
		int v_y = -1;
		int v_z = -1;
		for (int i=0; i<Configuration.getSizeDataColumns(); i++){
			DataColumnInfo cci = Configuration.getDataColumnInfo(i);
			if (cci.getId().equals("temp"))
				t = i;
			
			if (cci.getComponent() == Component.MASS)
				m = i;
			
			if (cci.getComponent() == Component.VELOCITY_X)
				v_x = i;
			
			if (cci.getComponent() == Component.VELOCITY_Y)
				v_y = i;
			
			if (cci.getComponent() == Component.VELOCITY_Z)
				v_z = i;
			
		}
		final int tempColumn = t;
		final int massColumn = m;
		final int vxColumn = v_x;
		final int vyColumn = v_y;
		final int vzColumn = v_z;
		
		if (massColumn == -1  || tempColumn == -1 || vxColumn == -1 || vyColumn == -1 || vzColumn == -1)
			return;
		
		Configuration.currentFileLoader.getProgressMonitor().start(data.getAtoms().size());
		
		for (Atom a : data.getAtoms()){
			nnb.add(a);
		}
		
		Vector<Callable<Void>> parallelTasks = new Vector<Callable<Void>>();
		for (int i=0; i<ThreadPool.availProcessors(); i++){
			final int j = i;
			parallelTasks.add(new Callable<Void>() {
				@Override
				public Void call() throws Exception {
					float temp = 0f;
					final int start = (int)(((long)data.getAtoms().size() * j)/ThreadPool.availProcessors());
					final int end = (int)(((long)data.getAtoms().size() * (j+1))/ThreadPool.availProcessors());
					for (int i=start; i<end; i++){
						if ((i-start)%1000 == 0)
							Configuration.currentFileLoader.getProgressMonitor().addToCounter(1000);
						
						Atom a = data.getAtoms().get(i);
						float vx = a.getData(vxColumn);
						float vy = a.getData(vyColumn);
						float vz = a.getData(vzColumn);
						
						float av_vx = vx;
						float av_vy = vy;
						float av_vz = vz;
						
						ArrayList<Atom> neigh = nnb.getNeigh(a);
						for (Atom n : neigh){
							av_vx += n.getData(vxColumn);
							av_vy += n.getData(vyColumn);
							av_vz += n.getData(vzColumn);
						}
						
						av_vx /= neigh.size()+1;
						av_vy /= neigh.size()+1;
						av_vz /= neigh.size()+1;
						
						vx -= av_vx;
						vy -= av_vy;
						vz -= av_vz;
						
						temp = ((a.getData(massColumn) * (vx*vx+vy*vy+vz*vz))) / 3;
						temp *= 11605;
						
						a.setData(temp, tempColumn);
					}
					Configuration.currentFileLoader.getProgressMonitor().addToCounter(end-start%1000);
					return null;
				}
			});
		}
		ThreadPool.executeParallel(parallelTasks);
		
		double[] tempPerElement = new double[Configuration.getNumElements()];
		int[] numPerElement = new int[Configuration.getNumElements()];
		for (Atom a : data.getAtoms()){
			tempPerElement[a.getElement()] += a.getData(tempColumn);
			numPerElement[a.getElement()]++;
		}
		
		for (int i=tempPerElement.length-1; i>0; i--){
			JLogPanel.getJLogPanel().addLog(String.format("Element %d: %.6f", i, numPerElement[i]==0 ? 0. : 
					tempPerElement[i]/numPerElement[i]));
		}
		JLogPanel.getJLogPanel().addLog("Average temperatures for elements in "+data.getName());
		
		Configuration.currentFileLoader.getProgressMonitor().stop();
	}
}
