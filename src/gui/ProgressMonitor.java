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

package gui;

import javax.swing.SwingWorker;

public class ProgressMonitor {
	
	private SwingWorker<?,?> worker;
	private int updateInterval = 100;
	
	private long max;
	private long counter;
	private boolean destroyed = false;
	private boolean started = false;
	
	public ProgressMonitor(SwingWorker<?,?> worker) {
		if (worker == null) return;
		this.worker = worker;
		Thread t = new Thread(new Runnable() {
			@Override
			public void run() {
				long oldValue = counter;
				while(true){
					if (destroyed) return;
					if (started)
						if (oldValue != counter){
							ProgressMonitor.this.worker.firePropertyChange("operation_progress", null, (counter*100)/max);
							oldValue = counter;
						}
					try {
						Thread.sleep(updateInterval);
					} catch (InterruptedException e) {}
				}
			}
		});
		t.start();
	}
	
	public synchronized void setUpdateInterval(int interval){
		this.updateInterval = interval;
	}
	
	public synchronized void start(long maximum){
		this.max = maximum;
		this.started = true;
		this.counter = 0l;
		if (worker != null)
			worker.firePropertyChange("operation_progress", null, 0);
	}
	
	public synchronized void stop(){
		this.started = false;
		if (worker != null)
			worker.firePropertyChange("operation_no_progress", null, null);
	}
	
	public synchronized void setTitle(String title){
		if (worker != null)
			worker.firePropertyChange("title", null, title);
	}
	
	public synchronized void setCurrentFilename(String progress){
		if (worker != null)
			worker.firePropertyChange("progress", null, progress);
	}
	
	public synchronized void setActivityName(String activity){
		if (worker != null)
			worker.firePropertyChange("operation", null, activity);
	}
	
	public synchronized void addToCounter(long inc){
		this.counter+=inc;
	}
	
	public synchronized void setCounter(long counter){
		this.counter=counter;
	}
	
	public synchronized void destroy(){
		this.destroyed = true;
	}
}
