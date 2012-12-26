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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
import uk.me.parabola.mkgmap.combiners.GmapsuppBuilder;
import uk.me.parabola.mkgmap.combiners.MdrBuilder;
import uk.me.parabola.mkgmap.combiners.MdxBuilder;
import uk.me.parabola.mkgmap.combiners.NsisBuilder;
import uk.me.parabola.mkgmap.combiners.TdbBuilder;
import uk.me.parabola.mkgmap.osmstyle.StyleFileLoader;
import uk.me.parabola.mkgmap.osmstyle.StyleImpl;
import uk.me.parabola.mkgmap.reader.osm.Style;
import uk.me.parabola.mkgmap.reader.osm.StyleInfo;
import uk.me.parabola.mkgmap.reader.overview.OverviewMapDataSource;
import uk.me.parabola.mkgmap.scan.SyntaxException;
import uk.me.parabola.mkgmap.srt.SrtTextReader;

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
	private final List<Combiner> combiners = new ArrayList<Combiner>();

	private final Map<String, MapProcessor> processMap = new HashMap<String, MapProcessor>();
	private String styleFile = "classpath:styles";
	private boolean verbose;

	private final List<FilenameTask> futures = new LinkedList<FilenameTask>();
	private ExecutorService threadPool;
	// default number of threads
	private int maxJobs = 1;

	/**
	 * The main program to make or combine maps.  We now use a two pass process,
	 * first going through the arguments and make any maps and collect names
	 * to be used for creating summary files like the TDB and gmapsupp.
	 *
	 * @param args The command line arguments.
	 */
	public static void main(String[] args) {
		long start = System.currentTimeMillis();
		System.out.println("Time started: " + new Date());
		// We need at least one argument.
		if (args.length < 1) {
			System.err.println("Usage: mkgmap [options...] <file.osm>");
			printHelp(System.err, getLang(), "options");
			return;
		}

		Main mm = new Main();

		try {
			// Read the command line arguments and process each filename found.
			CommandArgsReader commandArgs = new CommandArgsReader(mm);
			commandArgs.setValidOptions(getValidOptions(System.err));
			commandArgs.readArgs(args);
		} catch (MapFailedException e) {
			System.err.println(e.getMessage());
		} catch (ExitException e) {
			System.err.println(e.getMessage());
		}
		System.out.println("Time finished: " + new Date());
		System.out.println("Total time taken: " + (System.currentTimeMillis() - start) + "ms"); 
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

		Set<String> result = new HashSet<String>();
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

		final MapProcessor mp = mapMaker(ext);

		args.setSort(getSort(args));

		log.info("Submitting job " + filename);
		FilenameTask task = new FilenameTask(new Callable<String>() {
			public String call() {
				log.threadTag(filename);
				String output = mp.makeMap(args, filename);
				log.debug("adding output name", output);
				log.threadTag(null);
				return output;
			}
		});
		task.setArgs(args);
		futures.add(task);
	}

	private MapProcessor mapMaker(String ext) {
		MapProcessor mp = processMap.get(ext);
		if (mp == null)
			mp = new MapMaker();
		return mp;
	}

	public void processOption(String opt, String val) {
		log.debug("option:", opt, val);

		if (opt.equals("number-of-files")) {

			// This option always appears first.  We use it to turn on/off
			// generation of the overview files if there is only one file
			// to process.
			int n = Integer.valueOf(val);
			if (n > 1)
				addTdbBuilder();

		} else if (opt.equals("tdbfile")) {
			addTdbBuilder();
		} else if (opt.equals("nsis")) {
			addCombiner(new NsisBuilder());
		} else if (opt.equals("help")) {
			printHelp(System.out, getLang(), (!val.isEmpty()) ? val : "help");
		} else if (opt.equals("style-file") || opt.equals("map-features")) {
			styleFile = val;
		} else if (opt.equals("verbose")) {
			verbose = true;
		} else if (opt.equals("list-styles")) {
			listStyles();
		} else if (opt.equals("max-jobs")) {
			if (val.isEmpty())
				maxJobs = Runtime.getRuntime().availableProcessors();
			else
				maxJobs = Integer.parseInt(val);
			if(maxJobs < 1) {
				log.warn("max-jobs has to be at least 1");
				maxJobs = 1;
			}
		} else if (opt.equals("version")) {
			System.err.println(Version.VERSION);
			System.exit(0);
		}
	}

	public void removeOption(String opt) {
	}

	private void addTdbBuilder() {
		TdbBuilder builder = new TdbBuilder();
		builder.setOverviewSource(new OverviewMapDataSource());
		addCombiner(builder);
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
			Style style;
			try {
				style = new StyleImpl(styleFile, name);
			} catch (SyntaxException e) {
				System.err.println("Error in style: " + e.getMessage());
				continue;
			} catch (FileNotFoundException e) {
				log.debug("could not find style", name);
				try {
					style = new StyleImpl(styleFile, null);
				} catch (SyntaxException e1) {
					System.err.println("Error in style: " + e1.getMessage());
					continue;
				} catch (FileNotFoundException e1) {
					log.debug("could not find style", styleFile);
					continue;
				}
			}

			StyleInfo info = style.getInfo();
			System.out.format("%-15s %6s: %s\n",
					name,info.getVersion(), info.getSummary());
			if (verbose) {
				for (String s : info.getLongDescription().split("\n"))
					System.out.printf("\t%s\n", s.trim());
			}
		}
	}

	private static String getLang() {
		return "en";
	}

	private void addCombiner(Combiner combiner) {
		combiners.add(combiner);
	}

	public void endOptions(CommandArgs args) {
		fileOptions(args);

		log.info("Start tile processors");
		if (threadPool == null) {
			log.info("Creating thread pool with " + maxJobs + " threads");
			threadPool = Executors.newFixedThreadPool(maxJobs);
		}

		// process all input files
		for (FilenameTask task : futures) {
			threadPool.execute(task);
		}


		List<FilenameTask> filenames = new ArrayList<FilenameTask>();

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
				} catch (ExitException ee) {
					throw ee;
				} catch (MapFailedException mfe) {
					System.err.println(mfe.getMessage());
				} catch (Throwable t) {
					t.printStackTrace();
					if (!args.getProperties().getProperty("keep-going", false)) {
						throw new ExitException("Exiting - if you want to carry on regardless, use the --keep-going option");
					}
				}
			}
		}

		if (combiners.isEmpty())
			return;

		log.info("Combining maps");

		args.setSort(getSort(args));

		// Get them all set up.
		for (Combiner c : combiners)
			c.init(args);

		// Tell them about each filename
		for (FilenameTask file : filenames) {
			if (file == null || file.isCancelled())
				continue;

			try {
				log.info("  " + file);
				FileInfo fileInfo = FileInfo.getFileInfo(file.getFilename());
				fileInfo.setArgs(file.getArgs());
				for (Combiner c : combiners)
					c.onMapEnd(fileInfo);
			} catch (FileNotFoundException e) {
				throw new MapFailedException("could not open file " + e.getMessage());
			}
		}

		// All done, allow tidy up or file creation to happen
		for (Combiner c : combiners)
			c.onFinish();
	}

	private void fileOptions(CommandArgs args) {
		boolean indexOpt = args.exists("index");
		boolean gmapOpt = args.exists("gmapsupp");
		boolean tdbOpt = args.exists("tdbfile");

		if (gmapOpt) {
			GmapsuppBuilder gmapBuilder = new GmapsuppBuilder();
			gmapBuilder.setCreateIndex(indexOpt);

			addCombiner(gmapBuilder);
		}

		if (indexOpt && (tdbOpt || !gmapOpt)) {
			addCombiner(new MdrBuilder());
			addCombiner(new MdxBuilder());
		}
	}

	/**
	 * Get the extension of the filename, ignoring any compression suffix.
	 *
	 * @param filename The original filename.
	 * @return The file extension.
	 */
	private String extractExtension(String filename) {
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
