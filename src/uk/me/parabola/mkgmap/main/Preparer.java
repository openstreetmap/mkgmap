/*
 * Copyright (C) 2006, 2012.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 or
 * version 2 as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 */
package uk.me.parabola.mkgmap.main;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;

import uk.me.parabola.util.EnhancedProperties;

/**
 * A task that prepares some data before running the map tile processing.
 * 
 * @author WanMil
 */
public abstract class Preparer implements Runnable {

	private ExecutorService threadPool;
	private final BlockingQueue<Future<Object>> remainingTasks = new LinkedBlockingQueue<Future<Object>>();

	/**
	 * Initializes the preparer. The given thread pool can be used to run the
	 * preparer in multithreaded context.
	 * 
	 * @param props the properties
	 * @param threadPool a thread pool in which the preparer is run
	 * @return <code>true</code>: the preparer should run; <code>false</code>
	 *         the preparer is not enabled and should not run
	 */
	public boolean init(EnhancedProperties props, ExecutorService threadPool) {
		this.threadPool = threadPool;
		return true;
	}

	@SuppressWarnings("unchecked")
	protected <V> Future<V> addWorker(Callable<V> worker) {
		if (threadPool == null) {
			// only one thread available for the preparer
			// so execute the task directly
			FutureTask<V> future = new FutureTask<V>(worker);
			future.run();
			return future;
		} else {
			Future<Object> task = threadPool.submit((Callable<Object>) worker);
			remainingTasks.add(task);
			return (Future<V>) task;
		}
	}

	/**
	 * Starts the preparer and blocks until it has finished.
	 * 
	 * @throws ExecutionException
	 * @throws InterruptedException
	 */
	public void runPreparer() throws InterruptedException, ExecutionException {
		if (threadPool == null) {
			// there is no thread pool so run it in the same thread and wait for
			// its completion
			run();
		} else {

			// start the preparer
			Future<Object> prepTask = threadPool.submit(this, new Object());

			// first wait for the main preparer task to finish
			prepTask.get();

			// then wait for all workers started by the preparer to finish
			while (true) {
				Future<Object> task = remainingTasks.poll();
				if (task == null) {
					// no more remaining tasks
					// preparer has finished completely
					break;
				}
				// wait for the task to finish
				task.get();
			}
		}
	}

}
