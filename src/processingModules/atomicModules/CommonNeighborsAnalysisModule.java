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

import gui.JPrimitiveVariablesPropertiesDialog;
import gui.ProgressMonitor;
import gui.PrimitiveProperty.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.Callable;

import javax.swing.JFrame;
import javax.swing.JSeparator;

import model.Atom;
import model.AtomData;
import model.DataColumnInfo;
import model.NearestNeighborBuilder;
import processingModules.ClonableProcessingModule;
import processingModules.ProcessingResult;
import processingModules.toolchain.Toolchainable.ToolchainSupport;
import processingModules.toolchain.Toolchainable.ExportableValue;
import common.ThreadPool;
import common.Tupel;
import common.Vec3;

@ToolchainSupport()
public class CommonNeighborsAnalysisModule extends ClonableProcessingModule {

	private static DataColumnInfo cnaColumn = 
			new DataColumnInfo("CNA" , "cna" ,"");
	
	@ExportableValue
	private float cutoff = 5f;
	
	
	@Override
	public DataColumnInfo[] getDataColumnsInfo() {
		return new DataColumnInfo[]{cnaColumn};
	}
	
	@Override
	public String getShortName() {
		return "Common Neighbor analysis";
	}
	
	@Override
	public boolean canBeAppliedToMultipleFilesAtOnce() {
		return true;
	}
	
	@Override
	public String getFunctionDescription() {
		return "Perform the common neighbor analysis as described in<br>"
				+ "<i>Honeycutt & Andersen J. Phys. Chem. 91 4950–63 (1987)</i>.";
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
		
		final Pattern[] pattern = new Pattern[]{
				new Pattern(4, 2, 1),
				new Pattern(4, 2, 2),
				new Pattern(6, 6, 6),
				new Pattern(4, 4, 4),
				new Pattern(5, 4, 3),
				new Pattern(6, 6, 3)	
		};
		
		final int cnaCol = data.getIndexForCustomColumn(cnaColumn);
		
		final NearestNeighborBuilder<Atom> nnb = new NearestNeighborBuilder<Atom>(data.getBox(), cutoff, true);
		nnb.addAll(data.getAtoms());
		
		Vector<Callable<Void>> parallelTasks = new Vector<Callable<Void>>();
		for (int i=0; i<ThreadPool.availProcessors(); i++){
			final int j = i;
			parallelTasks.add(new Callable<Void>() {
				@Override
				public Void call() throws Exception {
					final int start = (int)(((long)data.getAtoms().size() * j)/ThreadPool.availProcessors());
					final int end = (int)(((long)data.getAtoms().size() * (j+1))/ThreadPool.availProcessors());
					
					for (int i=start; i<end; i++){
						int[] counter = new int[pattern.length];
						
						if ((i-start)%1000 == 0)
							ProgressMonitor.getProgressMonitor().addToCounter(1000);
						
						Atom a = data.getAtoms().get(i);	
						List<Vec3> neigh = nnb.getNeighVec(a);
						
						for (Vec3 n : neigh){
							List<Vec3> common = new ArrayList<Vec3>(neigh.size());
							
							for (Vec3 n2 : neigh){
								if (!n.equals(n2) && n.getDistTo(n2)<cutoff){
									common.add(n2);
								}
							}
							ArrayList<Bond> bonds = new ArrayList<Bond>();

							for (int k=0; k<common.size()-1; k++){
								for (int l=k+1; l<common.size(); l++){
									if(common.get(k).getDistTo(common.get(l))<cutoff)
										bonds.add(new Bond(k,l));
								}
							}
							
							
							int longestChain = 0;
							for (int k = 0; k < bonds.size(); k++) {

								/* Initialize bond data */
								int start1 = bonds.get(k).v1;
								int end1 = bonds.get(k).v2;
								for (int l = 0; l < bonds.size(); l++)
									bonds.get(l).length = 0;
								bonds.get(k).length = 1;

								int tmp_cna_chain = 1;
								longestChain = Math.max(longestChain, tmp_cna_chain);
								if (longestChain == bonds.size()) break;

								/* Add further bonds to start bond recursively */
								Tupel<Integer, Integer> r = chain(start1, end1, bonds, longestChain, tmp_cna_chain);
								longestChain = r.o1;
								
								
								if (longestChain ==  bonds.size()) break;

							}
							
							Pattern p = new Pattern(common.size(), bonds.size(), longestChain);
							
							for (int l=0; l<pattern.length; l++)
								if (pattern[l].equals(p)) counter[l]++;
						}
						
						
						if (counter[0] == 12) a.setData(1, cnaCol); 
						else if (counter[0] == 6 && counter[1] == 6) a.setData(2, cnaCol);
						else if (counter[2] == 8 && counter[3] == 6) a.setData(3, cnaCol);
						else if (counter[4] == 12 && counter[5] == 4) a.setData(4, cnaCol);
						else a.setData(0, cnaCol);
					}
					
					ProgressMonitor.getProgressMonitor().addToCounter(end-start%1000);
					return null;
				}
				
				Tupel<Integer, Integer> chain(int start, int end, ArrayList<Bond> bonds, int max_chain, int chain){
					int i, start_old, end_old;

					/* Check all unused bonds */
					for (i = 0; i < bonds.size(); i++){
						if (bonds.get(i).length == 0) {

							start_old = start;
							end_old = end;

							if (bonds.get(i).v1 == start)
								start = bonds.get(i).v2;
							else if (bonds.get(i).v1 == end)
								end = bonds.get(i).v2;
							else if (bonds.get(i).v2 == start)
								start = bonds.get(i).v1;
							else if (bonds.get(i).v2 == end)
								end = bonds.get(i).v1;
							else continue;

							/* If a bond is found, remove it from the list of bonds */
							/* and invoke domino recursively */

							/* Update bond data */
							bonds.get(i).length = 1;
							++(chain);

							max_chain = Math.max(max_chain, chain);
							if (max_chain == bonds.size()) break;

							Tupel<Integer, Integer> r = chain(start, end, bonds, max_chain, chain);

							max_chain = r.o1;
							chain = r.o2;
							/* Reset bond data */
							--chain;
							start = start_old;
							end = end_old;
							bonds.get(i).length = 0;
						}
					}
					return new Tupel<Integer, Integer>(max_chain, chain);
				}
				
				class Bond{
					final int v1,v2;
					int length = 0;
					public Bond(int v1, int v2) {
						this.v1 = v1;
						this.v2 = v2;
					}
				}
			});
		}
		ThreadPool.executeParallel(parallelTasks);	
		
		ProgressMonitor.getProgressMonitor().stop();
		return null;
	}
	
	@Override
	public boolean showConfigurationDialog(JFrame frame, AtomData data) {
		JPrimitiveVariablesPropertiesDialog dialog = new JPrimitiveVariablesPropertiesDialog(frame, "Perform common neighbor analysis" );
		dialog.addLabel(getFunctionDescription());
		dialog.add(new JSeparator());
		FloatProperty cutoff = dialog.addFloat("avRadius", "cutoff_radius", "", 5f, 0f, 1000f);
		
		boolean ok = dialog.showDialog();
		if (ok){
			this.cutoff = cutoff.getValue();
		}
		return ok;
	}
	
	private class Pattern implements Comparator<Pattern>{
		int j,k,l;
		
		public Pattern(int j, int k, int l) {
			this.j = j;
			this.k = k;
			this.l = l;
		}
		
		@Override
		public boolean equals(Object obj) {
			if (obj instanceof Pattern) {
				Pattern o = (Pattern) obj;
				if (this.j == o.j && this.k == o.k && this.l == o.l)
					return true;
			}
			return false;
		}
		
		@Override
		public int compare(Pattern o1, Pattern o2) {
			if (o1.j < o2.j) return 1;
			else if (o1.j > o2.j) return -1;
			if (o1.k < o2.k) return 1;
			else if (o1.k > o2.k) return -1;
			if (o1.l < o2.l) return 1;
			else if (o1.l > o2.l) return -1;
			return 0;
		}
	}
	
}
