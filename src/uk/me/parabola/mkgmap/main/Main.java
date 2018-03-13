/*
 * Copyright (C) 2007 - 2012.
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.lang.OutOfMemoryError;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import uk.me.parabola.imgfmt.ExitException;
import uk.me.parabola.imgfmt.MapFailedException;
import uk.me.parabola.imgfmt.app.srt.Sort;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.ArgumentProcessor;
import uk.me.parabola.mkgmap.CommandArgs;
import uk.me.parabola.mkgmap.CommandArgsReader;
import uk.me.parabola.mkgmap.Version;
import uk.me.parabola.mkgmap.combiners.Combiner;
import uk.me.parabola.mkgmap.combiners.FileInfo;
import uk.me.parabola.mkgmap.combiners.GmapiBuilder;
import uk.me.parabola.mkgmap.combiners.GmapsuppBuilder;
import uk.me.parabola.mkgmap.combiners.MdrBuilder;
import uk.me.parabola.mkgmap.combiners.MdxBuilder;
import uk.me.parabola.mkgmap.combiners.NsisBuilder;
import uk.me.parabola.mkgmap.combiners.OverviewBuilder;
import uk.me.parabola.mkgmap.combiners.TdbBuilder;
import uk.me.parabola.mkgmap.osmstyle.StyleFileLoader;
import uk.me.parabola.mkgmap.osmstyle.StyleImpl;
import uk.me.parabola.mkgmap.reader.osm.Style;
import uk.me.parabola.mkgmap.reader.osm.StyleInfo;
import uk.me.parabola.mkgmap.scan.SyntaxException;
import uk.me.parabola.mkgmap.srt.SrtTextReader;
import uk.me.parabola.util.EnhancedProperties;

/**
 * The new main program.  There can be many file names to process and there can
 * be differing outputs determined by options.  So the actual work is mostly
 * done in other classes.  This one just works out what is wanted.
 *
 * @author Steve Ratcliffe
 */
public class Main implements ArgumentProcessor {
	private static final Logger log = Logger.getLogger(Main.class);

	// Final .img file combiners.
	private final List<Combiner> combiners = new ArrayList<>();

	private final Map<String, MapProcessor> processMap = new HashMap<>();
	private String styleFile = "classpath:styles";
	private String styleOption;
	private boolean verbose;

	private final List<FilenameTask> futures = new LinkedList<>();
	private ExecutorService threadPool;
	// default number of threads
	private int maxJobs = 0;

	private boolean createTdbFiles = false;
	private boolean tdbBuilderAdded = false;
	// used for messages in listStyles and checkStyles
	private String searchedStyleName;

	private volatile int programRC = 0;

	private final Map<String, Combiner> combinerMap = new HashMap<>();

	/**
	 * Used for unit tests
	 */
	public static void mainNoSystemExit(String... args) {
		Main.mainStart(args);
	}
	
	public static void main(String... args) {
		int rc = Main.mainStart(args);
		if (rc != 0)
			System.exit(1);
	}
	
	/**
	 * The main program to make or combine maps.  We now use a two pass process,
	 * first going through the arguments and make any maps and collect names
	 * to be used for creating summary files like the TDB and gmapsupp.
	 *
	 * @param args The command line arguments.
	 */
	private static int mainStart(String... args) {
		Instant start = Instant.now();
		System.out.println("Time started: " + new Date());
		// We need at least one argument.
		if (args.length < 1) {
			printUsage();
			printHelp(System.err, getLang(), "options");
			return 0;
		}

		Main mm = new Main();

		int numExitExceptions = 0;
		try {
			// Read the command line arguments and process each filename found.
			CommandArgsReader commandArgs = new CommandArgsReader(mm);
			commandArgs.setValidOptions(getValidOptions(System.err));
			commandArgs.readArgs(args);
		} catch (OutOfMemoryError e) {
			++numExitExceptions;
			System.err.println(e);
			if (mm.maxJobs > 1)
				System.err.println("Try using the mkgmap --max-jobs option with a value less than " + mm.maxJobs  + " to reduce the memory requirement, or use the Java -Xmx option to increase the available heap memory.");
			else
				System.err.println("Try using the Java -Xmx option to increase the available heap memory.");
		} catch (MapFailedException e) {
			// one of the combiners failed
			e.printStackTrace();
			++numExitExceptions;
		} catch (ExitException e) {
			++numExitExceptions;
			String message = e.getMessage();
			Throwable cause = e.getCause();
			while (cause != null) {
				message += "\r\n" + cause.toString();
				cause = cause.getCause();
			}
			System.err.println(message);
		}
		
		System.out.println("Number of ExitExceptions: " + numExitExceptions);
		
		System.out.println("Time finished: " + new Date());
		Duration duration = Duration.between(start, Instant.now());
		long seconds = duration.getSeconds();
		if (seconds > 0) {
			long hours = seconds / 3600;
			seconds -= hours * 3600;
			long minutes = seconds / 60;
			seconds -= minutes * 60;
			System.out.println("Total time taken: " + 
								(hours > 0 ? hours + (hours > 1 ? " hours " : " hour ") : "") +
								(minutes > 0 ? minutes + (minutes > 1 ? " minutes " : " minute ") : "") +
								(seconds > 0 ? seconds + (seconds > 1 ? " seconds" : " second") : ""));
		}
		else
			System.out.println("Total time taken: " + duration.getNano() / 1000000 + " ms");
		if (numExitExceptions > 0 || mm.getProgramRC() != 0){
			return 1;
		}
		return 0;
	}
	
	private static void printUsage (){
		System.err.println("Usage: mkgmap [options...] <file.osm>");
	}

	private void setProgramRC(int rc){
		programRC = rc;
	}

	private int getProgramRC(){
		return programRC;
	}
	
	/**
	 * Grab the options help file and print it.
	 * @param err The output print stream to write to.
	 * @param lang A language hint.  The help will be displayed in this
     * language if it has been translated.
	 * @param file The help file to display.
	 */
	private static void printHelp(PrintStream err, String lang, String file) {
		String path = "/help/" + lang + '/' + file;
		InputStream stream = Main.class.getResourceAsStream(path);
		if (stream == null) {
			err.println("Could not find the help topic: " + file + ", sorry");
			return;
		}

		BufferedReader r = new BufferedReader(new InputStreamReader(stream));
		try {
			String line;
			while ((line = r.readLine()) != null)
				err.println(line);
		} catch (IOException e) {
			err.println("Could not read the help topic: " + file + ", sorry");
		}
	}

	private static Set<String> getValidOptions(PrintStream err) {
		String path = "/help/en/options";
		InputStream stream = Main.class.getResourceAsStream(path);
		if (stream == null)
			return null;

		Set<String> result = new HashSet<>();
		try {
			BufferedReader r = new BufferedReader(new InputStreamReader(stream, "utf-8"));

			Pattern p = Pattern.compile("^--?([a-zA-Z0-9-]*).*$");
			String line;
			while ((line = r.readLine()) != null) {
				Matcher matcher = p.matcher(line);
				if (matcher.matches()) {
					String opt = matcher.group(1);
					result.add(opt);
				}
			}
		} catch (IOException e) {
			err.println("Could not read valid optoins");
			return null;
		}

		return result;
	}

	public void startOptions() {
		MapProcessor saver = new NameSaver();
		processMap.put("img", saver);
		processMap.put("mdx", saver);

		processMap.put("typ", new TypSaver());

		// Normal map files.
		processMap.put("rgn", saver);
		processMap.put("tre", saver);
		processMap.put("lbl", saver);
		processMap.put("net", saver);
		processMap.put("nod", saver);

		processMap.put("txt", new TypCompiler());
	}

	/**
	 * Switch out to the appropriate class to process the filename.
	 */
	public void processFilename(final CommandArgs args, final String filename) {
		
		final String ext = extractExtension(filename);
		log.debug("file", filename, ", extension is", ext);
		// ignore ovm_* files given as command line arguments
		if (OverviewBuilder.isOverviewImg(filename))
			return;
		
		final MapProcessor mp = mapMaker(ext);

		args.setSort(getSort(args));

		log.info("Submitting job " + filename);
		FilenameTask task = new FilenameTask(new Callable<String>() {
			public String call() {
				log.threadTag(filename);
				if (filename.startsWith("test-map:") || new File(filename).exists()){
					String output = mp.makeMap(args, filename);
					log.debug("adding output name", output);
					log.threadTag(null);
					return output;
				} else {
					log.error("file " + filename + " doesn't exist");
					return null;
				}
			}
		});
		task.setArgs(args);
		futures.add(task);
	}

	private MapProcessor mapMaker(String ext) {
		MapProcessor mp = processMap.get(ext);
		if (mp == null)
			mp = new MapMaker(createTdbFiles);
		return mp;
	}

	public void processOption(String opt, String val) {
		log.debug("option:", opt, val);

		switch (opt) {
		case "number-of-files":

			// This option always appears first.  We use it to turn on/off
			// generation of the overview files if there is only one file
			// to process.
			int n = Integer.valueOf(val);
			if (n > 0) // TODO temporary, this option will become properly default of on.
				createTdbFiles = true;

			break;
		case "help":
			printHelp(System.out, getLang(), (!val.isEmpty()) ? val : "help");
			break;
		case "style-file":
		case "map-features":
			styleFile = val;
			break;
		case "style":
			styleOption = val;
			break;
		case "verbose":
			verbose = true;
			break;
		case "list-styles":
			listStyles();
			break;
		case "check-styles":
			checkStyles();
			break;
		case "max-jobs":
			if (val.isEmpty())
				maxJobs = Runtime.getRuntime().availableProcessors();
			else {
				maxJobs = Integer.parseInt(val);
				if (maxJobs < 1) {
					log.warn("max-jobs has to be at least 1");
					maxJobs = 1;
				}
				if (maxJobs > Runtime.getRuntime().availableProcessors())
					log.warn("It is recommended that max-jobs be no greater that the number of processor cores");
			}
			break;
		case "version":
			System.err.println(Version.VERSION);
			System.exit(0);
		}
	}

	public void removeOption(String opt) {
		if (Objects.equals("tdbfile", opt))
			createTdbFiles = false;
	}

	/**
	 * Add the builders for the TDB and overview map.  These are always
	 * generated together as we use some info that is calculated when constructing
	 * the overview map in the TDB file.
	 */
	private void addTdbBuilder() {
		if (!tdbBuilderAdded ){
			OverviewBuilder overviewBuilder = new OverviewBuilder();
			addCombiner("img", overviewBuilder);
			TdbBuilder tdbBuilder = new TdbBuilder(overviewBuilder);
			addCombiner("tdb", tdbBuilder);
			tdbBuilderAdded = true;
		}
	}

	private void listStyles() {
		String[] names;
		try {
			StyleFileLoader loader = StyleFileLoader.createStyleLoader(styleFile, null);
			names = loader.list();
			loader.close();
		} catch (FileNotFoundException e) {
			log.debug("didn't find style file", e);
			throw new ExitException("Could not list style file " + styleFile);
		}

		Arrays.sort(names);
		System.out.println("The following styles are available:");
		for (String name : names) {
			Style style = readOneStyle(name, false);
			if (style == null)
				continue;
			StyleInfo info = style.getInfo();
			System.out.format("%-15s %6s: %s\n",
					searchedStyleName,info.getVersion(), info.getSummary());
			if (verbose) {
				for (String s : info.getLongDescription().split("\n"))
					System.out.printf("\t%s\n", s.trim());
			}
		}
	}
 
	/**
	 * Check one or all styles in the path given in styleFile. 
	 */
	private void checkStyles() {
		String[] names;
		try {
			StyleFileLoader loader = StyleFileLoader.createStyleLoader(styleFile, null);
			names = loader.list();
			loader.close();
		} catch (FileNotFoundException e) {
			log.debug("didn't find style file", e);
			throw new ExitException("Could not check style file " + styleFile);
		}

		Arrays.sort(names);
		
		if (styleOption == null){
			if (names.length > 1)
				System.out.println("The following styles are available:");
			else 
				System.out.println("Found one style in " + styleFile);
		}
		int checked = 0;
		for (String name : names) {
			if (styleOption != null && !Objects.equals(name, styleOption))
				continue;
			if (names.length > 1){
				System.out.println("checking style: " + name);
			}
			++checked;
			boolean performChecks = true;
			if (Objects.equals("classpath:styles", styleFile) && !Objects.equals("default", name)){
					performChecks = false;
			}
			Style style = readOneStyle(name, performChecks);
			if (style == null){
				System.out.println("could not open style " + name);
			}
		}
		if (checked == 0)
			System.out.println("could not open style " + styleOption + " in " + styleFile );
		System.out.println("finished check-styles");
	}

	/**
	 * Try to read a style from styleFile directory
	 * @param name name of the style
	 * @param performChecks perform checks?
	 * @return the style or null in case of errors
	 */
	private Style readOneStyle(String name, boolean performChecks){
		searchedStyleName = name;
		Style style = null;
		try {
			style = new StyleImpl(styleFile, name, new EnhancedProperties(), performChecks);
		} catch (SyntaxException e) {
			System.err.println("Error in style: " + e.getMessage());
		} catch (FileNotFoundException e) {
			log.debug("could not find style", name);
			try {
				searchedStyleName = new File(styleFile).getName();
				style = new StyleImpl(styleFile, null, new EnhancedProperties(), performChecks);
			} catch (SyntaxException e1) {
				System.err.println("Error in style: " + e1.getMessage());
			} catch (FileNotFoundException e1) {
				log.debug("could not find style", styleFile);
			}
		}
		return style;
	}
	
	private static String getLang() {
		return "en";
	}

	private void addCombiner(String name, Combiner combiner) {
		combinerMap.put(name, combiner);
		combiners.add(combiner);
	}

	public void endOptions(CommandArgs args) {
		fileOptions(args);

		log.info("Start tile processors");
		int threadCount = maxJobs;
		int taskCount = futures.size();
		Runtime runtime = Runtime.getRuntime();
		if (threadPool == null) {
			if (threadCount == 0) {
				threadCount = 1;
				if (taskCount > 2) {
					//run one task to see how much memory it uses
					log.info("Max Memory: " + runtime.maxMemory());
					futures.get(0).run();
					long maxMemory = 0;
					for (MemoryPoolMXBean mxBean : ManagementFactory.getMemoryPoolMXBeans())
					{
						if (mxBean.getType() == MemoryType.HEAP) {
							MemoryUsage memoryUsage = mxBean.getPeakUsage();
							log.info("Max: " + memoryUsage.getMax());
							log.info("Used: " + memoryUsage.getUsed());
							if (memoryUsage.getMax() > maxMemory && memoryUsage.getUsed() != 0) {
								maxMemory = memoryUsage.getMax();
								threadCount = (int)(memoryUsage.getMax() / memoryUsage.getUsed());
							}
						}
					}
					threadCount = Math.max(threadCount, 1);
					threadCount = Math.min(threadCount, runtime.availableProcessors());
					System.out.println("Setting max-jobs to " + threadCount);
				}
			}

			log.info("Creating thread pool with " + threadCount + " threads");
			threadPool = Executors.newFixedThreadPool(threadCount);
		}

		// process all input files
		for (FilenameTask task : futures) {
			threadPool.execute(task);
		}


		List<FilenameTask> filenames = new ArrayList<>();
		
		int numMapFailedExceptions = 0;
		
		if (threadPool != null) {
			threadPool.shutdown();
			while (!futures.isEmpty()) {
				try {
					try {
						// don't call get() until a job has finished
						if (futures.get(0).isDone()) {
							FilenameTask future = futures.remove(0);

							// Provoke any exceptions by calling get and then
							// save the result for later use
							future.setFilename(future.get());
							filenames.add(future);
						} else
							Thread.sleep(100);
					} catch (ExecutionException e) {
						// Re throw the underlying exception
						Throwable cause = e.getCause();
						if (cause instanceof Exception)
							//noinspection ProhibitedExceptionThrown
							throw (Exception) cause;
						else if (cause instanceof Error)
							//noinspection ProhibitedExceptionThrown
							throw (Error) cause;
						else
							throw e;
					}
				} catch (OutOfMemoryError | ExitException e) {
					throw e;
				} catch (MapFailedException mfe) {
//					System.err.println(mfe.getMessage()); // already printed via log
					numMapFailedExceptions++;
					setProgramRC(-1);
				} catch (Throwable t) {
					t.printStackTrace();
					if (!args.getProperties().getProperty("keep-going", false)) {
						throw new ExitException("Exiting - if you want to carry on regardless, use the --keep-going option");
					}
				}
			}
		}
		System.out.println("Number of MapFailedExceptions: " + numMapFailedExceptions);
		if ((taskCount > threadCount + 1) && (maxJobs == 0) && (threadCount < runtime.availableProcessors())) {
			System.out.println("To reduce the run time, consider increasing the amnount of memory available for use by mkgmap by using the Java -Xmx flag to set the memory to more than " + 100* (1 + ((runtime.maxMemory() * runtime.availableProcessors()) / (threadCount * 1024 * 1024 * 100))) + " MB, providing this is less than the amount of physical memory installed.");
		}

		if (combiners.isEmpty())
			return;
		boolean hasFiles = false;
		for (FilenameTask file : filenames) {
			if (file == null || file.isCancelled() || file.getFilename() == null){
				if (args.getProperties().getProperty("keep-going", false))
					continue;
				else 
					throw new ExitException("Exiting - if you want to carry on regardless, use the --keep-going option");
			}
			hasFiles = true;
		}
		if (!hasFiles){
			log.warn("nothing to do for combiners.");
			return;
		}
		log.info("Combining maps");

		args.setSort(getSort(args));

		// Get them all set up.
		for (Combiner c : combiners)
			c.init(args);

		filenames.sort(new Comparator<FilenameTask>() {
			public int compare(FilenameTask o1, FilenameTask o2) {
				if (!o1.getFilename().endsWith(".img") || !o2.getFilename().endsWith(".img"))
					return o1.getFilename().compareTo(o2.getFilename());

				// Both end in .img
				try {
					int id1 = FileInfo.getFileInfo(o1.getFilename()).getHexname();
					int id2 = FileInfo.getFileInfo(o2.getFilename()).getHexname();
					if (id1 == id2)
						return 0;
					else if (id1 < id2)
						return -1;
					else
						return 1;
				} catch (FileNotFoundException ignored) {
				}
				return 0;
			}

		});

		// will contain img files for which an additional ovm file was found
		HashSet<String> foundOvmFiles = new HashSet<>();
		// try OverviewBuilder with special files  
		if (tdbBuilderAdded){
			for (FilenameTask file : filenames) {
				if (file == null || file.isCancelled())
					continue;

				try {
					String fileName = file.getFilename();
					if (!fileName.endsWith(".img"))
						continue;
					fileName = OverviewBuilder.getOverviewImgName(fileName);
					log.info("  " + fileName);
					FileInfo fileInfo = FileInfo.getFileInfo(fileName);
					fileInfo.setArgs(file.getArgs());
					// add the real input file 
					foundOvmFiles.add(file.getFilename());
					for (Combiner c : combiners){
						if (c instanceof OverviewBuilder)
							c.onMapEnd(fileInfo);
					}
				} catch (FileNotFoundException ignored) {
				}
			} 
		}
		
		// Tell them about each filename (OverviewBuilder excluded) 
		for (FilenameTask file : filenames) {
			if (file == null || file.isCancelled())
				continue;

			try {
				log.info("  " + file);
				FileInfo fileInfo = FileInfo.getFileInfo(file.getFilename());
				fileInfo.setArgs(file.getArgs());
				for (Combiner c : combiners){
					if (c instanceof OverviewBuilder && foundOvmFiles.contains(file.getFilename()))
						continue;
					c.onMapEnd(fileInfo);
				}
			} catch (FileNotFoundException e) {
				throw new MapFailedException("could not open file " + e.getMessage());
			}
		} 
		

		// All done, allow tidy up or file creation to happen
		for (Combiner c : combiners)
			c.onFinish();
		
		if (tdbBuilderAdded && args.getProperties().getProperty("remove-ovm-work-files", false)){
			for (String fName:foundOvmFiles){
				String ovmFile = OverviewBuilder.getOverviewImgName(fName);
				log.info("removing " + ovmFile);
				new File(ovmFile).delete();
			}
		}
	}

	private void fileOptions(CommandArgs args) {
		boolean indexOpt = args.exists("index");
		boolean gmapsuppOpt = args.exists("gmapsupp");
		boolean tdbOpt = args.exists("tdbfile");
		boolean gmapiOpt = args.exists("gmapi");

		if (tdbOpt || createTdbFiles){ 
			addTdbBuilder();
		}
		if (args.exists("nsis")) {
			addCombiner("nsis", new NsisBuilder());
		}
		if (gmapsuppOpt) {
			GmapsuppBuilder gmapBuilder = new GmapsuppBuilder();
			gmapBuilder.setCreateIndex(indexOpt);

			addCombiner("gmapsupp", gmapBuilder);
		}

		if (indexOpt && (tdbOpt || !gmapsuppOpt)) {
			addCombiner("mdr", new MdrBuilder());
			addCombiner("mdx", new MdxBuilder());
		}

		if (gmapiOpt) {
			addCombiner("gmapi", new GmapiBuilder(combinerMap));
		}
	}

	/**
	 * Get the extension of the filename, ignoring any compression suffix.
	 *
	 * @param filename The original filename.
	 * @return The file extension.
	 */
	private static String extractExtension(String filename) {
		String[] parts = filename.toLowerCase(Locale.ENGLISH).split("\\.");
		List<String> ignore = Arrays.asList("gz", "bz2", "bz");

		// We want the last part that is not gz, bz etc (and isn't the first part ;)
		for (int i = parts.length - 1; i > 0; i--) {
			String ext = parts[i];
			if (!ignore.contains(ext))
				return ext;
		}
		return "";
	}

	/**
	 * Create the sort description for the map.  This is used to sort items in the files
	 * and also is converted into a SRT file which is included in the MDR file.
	 *
	 * We simply use the code page to locate a sorting description, but it would be possible to
	 * specify the sort separately.
	 *
	 * @return A sort description object.
	 */
	public Sort getSort(CommandArgs args) {
		return SrtTextReader.sortForCodepage(args.getCodePage());
	}

	/**
	 * A null implementation that just returns the input name as the output.
	 */
	private static class NameSaver implements MapProcessor {
		public String makeMap(CommandArgs args, String filename) {
			return filename;
		}
	}

	private static class FilenameTask extends FutureTask<String> {
		private CommandArgs args;
		private String filename;

		private FilenameTask(Callable<String> callable) {
			super(callable);
		}

		public void setArgs(CommandArgs args) {
			this.args = args;
		}

		public CommandArgs getArgs() {
			return args;
		}

		public void setFilename(String filename) {
			this.filename = filename;
		}

		public String getFilename() {
			return filename;
		}

		public String toString() {
			return filename;
		}
	}
}
