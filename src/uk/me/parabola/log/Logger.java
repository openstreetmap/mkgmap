/*
 * Copyright (C) 2006 Steve Ratcliffe
 * 
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License version 2 as
 *  published by the Free Software Foundation.
 * 
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 * 
 * 
 * Author: Steve Ratcliffe
 * Create date: 30-Dec-2006
 */
package uk.me.parabola.log;

import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Properties;


/**
 * Simple logging class.  By default it is disabled.  You have to set it up
 * using (currently) a system property.  On the other hand it uses varargs
 * to make easier logging without having to do string concatenation in the
 * regular code.
 *
 * @author Steve Ratcliffe
 */
public class Logger {
	private static FileWriter file ;

	private static boolean loggingActive ;

	private static final Map<String, Logger> loggers = new ConcurrentHashMap<String, Logger>();

	private String shortname;

	private final boolean debugEnabled;
	private final boolean infoEnabled;
	private final boolean warnEnabled;
	private final boolean errorEnabled;

	static {
		initLogging();
	}

	public static Logger getLogger(Class<? extends Object> aClass) {
		String name = aClass.getName();
		Logger log = loggers.get(name);
		if (log == null) {
			log = new Logger(name);
			
			loggers.put(name, log);
		}

		return log;
	}

	private Logger(String name) {
		int dot = name.lastIndexOf('.');
		if (dot > 0)
			shortname = name.substring(dot+1);

		debugEnabled = true;
		infoEnabled = true;
		warnEnabled = true;
		errorEnabled = true;
	}

	private static void initLogging() {

		file = null;
		loggingActive = false;

		Properties props = System.getProperties();
		String filename = props.getProperty("log.filename");
		if (filename == null)
			return;

		try {
			file = new FileWriter(filename, true);
			loggingActive = true;

		} catch (IOException e) {
			System.err.println("ERROR: Logging initialisation failed");
		}
	}

	public boolean isDebugEnabled() {
		return debugEnabled;
	}

	public boolean isInfoEnabled() {
		return infoEnabled;
	}

	public boolean isWarnEnabled() {
		return warnEnabled;
	}

	public boolean isErrorEnabled() {
		return errorEnabled;
	}

	public void debug(Object o) {
		if (!debugEnabled)
			return;

		simpleFormat("DEBUG", o);
	}

	public void debug(Object ... olist) {
		if (!debugEnabled)
			return;

		String type = "DEBUG";
		arrayFormat(type, olist);
	}

	public void warn(Object o) {
		if (!warnEnabled)
			return;

		simpleFormat("WARN", o);
	}

	public void info(Object o) {
		if (!infoEnabled)
			return;

		simpleFormat("INFO", o);
	}

	public void error(Object o) {
		if (!errorEnabled)
			return;
		simpleFormat("ERROR", o);
	}

	public void error(Object o, Throwable e) {
		if (!errorEnabled)
			return;

		simpleFormat("ERROR", o);
		e.printStackTrace(); // XXX temporary.
	}

	private void simpleFormat(String type, Object o) {
		StringBuilder sb = new StringBuilder();
		sb.append(type);
		sb.append(": [");
		sb.append(shortname);
		sb.append("] ");
		sb.append(o);
		sb.append('\n');

		commonWrite(sb);
	}

	private void arrayFormat(String type, Object... olist) {
		StringBuilder sb = new StringBuilder(type);
		sb.append(": [");
		sb.append(shortname);
		sb.append("] ");
		for (Object o : olist) {
			sb.append(o);
			sb.append(' ');
		}
		sb.setLength(sb.length()-1);
		sb.append('\n');

		commonWrite(sb);
	}

	private static void commonWrite(StringBuilder sb) {
		if (!loggingActive)
			return;

		try {
			file.write(sb.toString());
			file.flush();
		} catch (IOException e) {
			loggingActive = false;
			System.err.println("Failed to write to log, disabling");
		}
	}
}
