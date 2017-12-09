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

import java.util.*;
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

	private static ScheduledThreadPoolExecutor threadPool, secondLevelThreadPool;
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
		
		ThreadFactory tf2 = new ThreadFactory() {
			@Override
			public Thread newThread(Runnable r) {
				Thread t = new Thread(r);
				t.setPriority(Thread.MIN_PRIORITY);
				return t;
			}
		};
		secondLevelThreadPool = new ScheduledThreadPoolExecutor(processors, tf2);
	}
	
	/**
	 * Runs a batch of Callables instances in parallel.
	 * The method returns once all callables have been executed
	 * @param <V> The returned type of each Callable instance. If no return is required use Void 
	 * @param c List of callables
	 * @return List of Future objects, holding the returned values
	 * @deprecated
	 */
	public static <V> List<Future<V>> executeParallel(List<? extends Callable<V>> c){
		try {
			List<Future<V>> futures = threadPool.invokeAll(c);
			for (Future<V> f : futures){
				try {
					f.get();
				} catch (ExecutionException e) {
					e.printStackTrace();
				}
			}
			return futures;
		} catch (InterruptedException e) {
			return null;
		}
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
	 * Runs a batch of Callables instances in parallel.
	 * The method returns once all callables have been executed
	 * The Callables are exectuted with lower priority than the primary ThreadPool.
	 * It should be used to improve the level of parallelism within threads that run in parallel themself
	 * @param <V> The returned type of each Callable instance. If no return is required use Void 
	 * @param c List of callables
	 * @return List of Future objects, holding the returned values
	 */
	public static <V> List<Future<V>> executeParallelSecondLevel(List<? extends Callable<V>> c){
		try {
			List<Future<V>> futures = secondLevelThreadPool.invokeAll(c);
			for (Future<V> f : futures){
				try {
					f.get();
				} catch (ExecutionException e) {
					e.printStackTrace();
				}
			}
			return futures;
		} catch (InterruptedException e) {
			return null;
		}
	}
	
	/**
	 * Submit a single callable that is executed asynchronously,
	 * whenever the second level ThreadPool has free resources 
	 * @param c
	 * @return
	 */
	public static <V> Future<V> submitSecondLevel(Callable<V> c){
		return secondLevelThreadPool.submit(c);
	}
	
	/**
	 * Perform the given action in parallel and updates a progress monitor if possible
	 * @param size the number of elements to be processed in the action to properly set the progress monitor
	 * @param action an action to be performed in parallel
	 */
	public static void executeAsParallelStream(int size, IntConsumer action) {
		final int progressBarUpdateInterval = Math.min(1, (int)(size/200));
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
	
	public static int availProcessors(){
		return processors;
	}
	
	/**
	 * Provides start value to split a loop into several equal parts
	 * @param totalElements the total number of element in a loop
	 * @param subprocessID the id of a subprocess, must be within [0,..,availProcessors()]
	 * @return
	 */
	public static int getSliceStart(int totalElements, int subprocessID){
		return (int)(((long)totalElements * subprocessID)/ThreadPool.availProcessors());
	}
	
	/**
	 * Provides end value to split a loop into several equal parts
	 * @param totalElements the total number of element in a loop
	 * @param subprocessID the id of a subprocess, must be within [0,..,availProcessors()]
	 * @return
	 */
	public static int getSliceEnd(int totalElements, int subprocessID){
		return (int)(((long)totalElements * (subprocessID+1))/ThreadPool.availProcessors());
	}
}
