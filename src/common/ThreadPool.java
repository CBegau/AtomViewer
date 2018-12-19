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

package common;

import java.util.concurrent.*;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;

import gui.ProgressMonitor;

/**
 * Pool of worker threads. One thread is created per processor.
 * Submitted instances of Callable are executed one by one in the order they are submitted.
 * Do not submit new Callable from within a thread that is itself runnning in one of the worker threads,
 * since this is with a very high probability causing deadlocks.
 * Submit these to the second level pool
 * Note: In a future migration to Java7, this should be replaced by the more capable JoinForkPool-Class
 */
public class ThreadPool {

	private static ScheduledThreadPoolExecutor threadPool;
	private static int processors = Runtime.getRuntime().availableProcessors();
	
	static {
		ThreadFactory tf = new ThreadFactory() {
			@Override
			public Thread newThread(Runnable r) {
				Thread t = new Thread(r);
				t.setPriority(Thread.NORM_PRIORITY);
				return t;
			}
		};
		threadPool = new ScheduledThreadPoolExecutor(processors, tf);
	}
	
	/**
	 * Submit a single callable that is executed asynchronously,
	 * whenever the ThreadPool has free resources 
	 * @param c
	 * @return
	 */
	public static <V> Future<V> submit(Callable<V> c){
		return threadPool.submit(c);
	}
	
	/**
	 * Perform the given action in parallel and updates a progress monitor if possible
	 * @param size the number of elements to be processed in the action to properly set the progress monitor
	 * @param action an action to be performed in parallel
	 */
	public static void executeAsParallelStream(int size, IntConsumer action) {
		if (size<=0) return;
		final int progressBarUpdateInterval = Math.max(1, (int)(size/200));
		ProgressMonitor.getProgressMonitor().start(size);
		IntConsumer actionWithProgressBarUpdate = 
				action.andThen( i->{
					if (i%progressBarUpdateInterval == 0) 
						ProgressMonitor.getProgressMonitor().addToCounter(progressBarUpdateInterval);
					}
				);
		
		//Parallel calculation of volume/density, iterate over all indices in a stream
		IntStream.range(0, size).parallel().forEach(actionWithProgressBarUpdate);
		
		ProgressMonitor.getProgressMonitor().stop();
	}
}
