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
package uk.me.parabola.mkgmap.reader.osm.boundary;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;

import uk.me.parabola.imgfmt.FormatException;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.reader.osm.LocationHook;

/**
 * Preprocesses the boundary information to be used by the {@link LocationHook} class.
 * @author WanMil
 */
public class BoundaryPreprocessor implements Runnable {
	private static final Logger log = Logger.getLogger(BoundaryPreprocessor.class);
	private static final List<Class<? extends LoadableBoundaryDataSource>> loaders;
	static {
		String[] sources = {
				"uk.me.parabola.mkgmap.reader.osm.boundary.OsmBinBoundaryDataSource",
				"uk.me.parabola.mkgmap.reader.osm.boundary.O5mBinBoundaryDataSource",
				// must be last as it is the default
				"uk.me.parabola.mkgmap.reader.osm.boundary.Osm5BoundaryDataSource", };

		loaders = new ArrayList<Class<? extends LoadableBoundaryDataSource>>();

		for (String source : sources) {
			try {
				@SuppressWarnings({ "unchecked" })
				Class<? extends LoadableBoundaryDataSource> c = (Class<? extends LoadableBoundaryDataSource>) Class
						.forName(source);
				loaders.add(c);
			} catch (ClassNotFoundException e) {
				// not available, try the rest
			} catch (NoClassDefFoundError e) {
				// not available, try the rest
			}
		}
	}

	/**
	 * Return a suitable boundary map reader. The name of the resource to be
	 * read is passed in. This is usually a file name, but could be something
	 * else.
	 * 
	 * @param name
	 *            The resource name to be read.
	 * @return A LoadableBoundaryDataSource that is capable of reading the
	 *         resource.
	 */
	private static LoadableBoundaryDataSource createMapReader(String name) {
		for (Class<? extends LoadableBoundaryDataSource> loader : loaders) {
			try {
				LoadableBoundaryDataSource src = loader.newInstance();
				if (name != null && src.isFileSupported(name))
					return src;
			} catch (InstantiationException e) {
				// try the next one.
			} catch (IllegalAccessException e) {
				// try the next one.
			} catch (NoClassDefFoundError e) {
				// try the next one
			}
		}

		// Give up and assume it is in the XML format. If it isn't we will get
		// an error soon enough anyway.
		return new Osm5BoundaryDataSource();
	}

	
	private String boundaryFilename;
	private String outDir;
	private ExecutorService threadPool;
	private final BlockingQueue<Future<Object>> remainingTasks = new LinkedBlockingQueue<Future<Object>>();

	/**
	 * constructor for stand-alone usage (workout only)
	 * @param in source directory or zip file 
	 * @param out target directory
	 */
	private BoundaryPreprocessor(String boundaryFilename, String out){
		this.boundaryFilename = boundaryFilename;
		this.outDir = out;

		int maxJobs = Runtime.getRuntime().availableProcessors();
		if (maxJobs > 1)
			this.threadPool = Executors.newFixedThreadPool(maxJobs);
		else
			this.threadPool = null;
	}
	
	/**
	 * Sets the number of threads that is used as maximum by the boundary
	 * preparer.
	 *
	 * @param maxThreads the maximum number of threads
	 */
	public void setMaxThreads(int maxThreads) {
		if (maxThreads > 1)
			this.threadPool = Executors.newFixedThreadPool(maxThreads);
		else
			this.threadPool = null;
	}

	public void run() {
		long t1 = System.currentTimeMillis();
		boolean prepOK = createRawData();
		long t2 = System.currentTimeMillis();
		log.info("BoundaryPreparer pass 1 took", (t2-t1), "ms");

		if (!prepOK){
			System.err.println("Boundary creation failed.");
			return;
		}
		workoutBoundaryRelations();
	}

	/**
	 * Parse OSM data and create boundaries. Distribute the boundaries on a grid
	 * with a fixed raster. 
	 * @return true if data was successfully written, else false
	 */
	private boolean createRawData(){
		File boundsDirectory = new File(outDir);
		BoundarySaver saver = new BoundarySaver(boundsDirectory, BoundarySaver.RAW_DATA_FORMAT);
		LoadableBoundaryDataSource dataSource = createMapReader(boundaryFilename);
		dataSource.setBoundarySaver(saver);
		log.info("Started loading", boundaryFilename);
		try {
			dataSource.load(boundaryFilename);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return false;
		} catch (FormatException e) {
			e.printStackTrace();
			return false;
		}
		saver.setBbox(dataSource.getBounds());
		log.info("Finished loading", boundaryFilename);
		saver.end();
		return true;
	}
	
	
	public static void main(String[] args) {
		if (args[0].equals("--help") || args.length != 2) {
			System.err.println("Usage:");
			System.err.println("java -cp mkgmap.jar uk.me.parabola.mkgmap.reader.osm.boundary.BoundaryPreprocessor <inputfile> <boundsdir>");
			System.err.println(" <inputfile>: File containing boundary data (OSM, PBF or O5M format)");
			System.err.println(" <boundsdir>: Directory in which the preprocessed bounds files are created");

			System.exit(-1);
		}
		
		String inputFile = args[0];
		String outputDir = args[1];
		
		long t1 = System.currentTimeMillis();
		
		BoundaryPreprocessor p = new BoundaryPreprocessor(inputFile, outputDir);
		try {
			p.runPreprocessing();
		} catch (InterruptedException exp) {
			exp.printStackTrace();
		} catch (ExecutionException exp) {
			System.err.println(exp);
			exp.printStackTrace();
		}
		System.out.println("Bnd files converted in " +  (System.currentTimeMillis()-t1) + " ms");
		log.info("Bnd files converted in", (System.currentTimeMillis()-t1), "ms");
	}

	/**
	 * Reworks all bounds files of the given directory so that all boundaries
	 * are applied with the information with which boundary they intersect.<br/>
	 * The files are rewritten in the QUADTREE_DATA_FORMAT which is used in the 
	 * LocationHook.
	 */
	private void workoutBoundaryRelations() {
		List<String> boundsFileNames = BoundaryUtil.getBoundaryDirContent(this.outDir);
				
		for (String boundsFileName : boundsFileNames) {
			// start workers that rework the boundary files and add the 
			// quadtree information
			addWorker(new QuadTreeWorker(this.outDir, boundsFileName));
		}
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
	 * Starts the preprocessing 
	 */
	public void runPreprocessing() throws InterruptedException, ExecutionException {
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
			
			// stop thread pool
			threadPool.shutdown();
		}
	}

	class QuadTreeWorker implements Callable<String> {
		private final String boundsDir;
		private final String boundsFilename;
		
		public QuadTreeWorker(String boundsDir, String boundsFilename) {
			this.boundsDir = boundsDir;
			this.boundsFilename = boundsFilename;
		}
		
		@Override
		public String call() throws Exception {
			log.info("Workout boundary relations in", boundsDir, boundsFilename);
			long t1 = System.currentTimeMillis();
			BoundaryQuadTree bqt = BoundaryUtil.loadQuadTree(boundsDir, boundsFilename);
			long dt = System.currentTimeMillis() - t1;
			log.info("splitting", boundsFilename, "took", dt, "ms");
			if (bqt != null){
				File outDir = new File(boundsDir);
				BoundarySaver saver = new BoundarySaver(outDir, BoundarySaver.QUADTREE_DATA_FORMAT);
				saver.setCreateEmptyFiles(false);

				saver.saveQuadTree(bqt, boundsFilename); 		
				saver.end();
			}
			return boundsFilename;
		}

	}

}

