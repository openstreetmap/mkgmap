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

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.LogManager;

/**
 * Simple logging class.  By default it is disabled.  You have to set it up
 * using (currently) a system property.  On the other hand it uses varargs
 * to make easier logging without having to do string concatenation in the
 * regular code.
 *
 * @author Steve Ratcliffe
 */
public class Logger {
	private final java.util.logging.Logger log;

	static {
		initLogging();
	}

	private Logger(String name) {
		this.log = java.util.logging.Logger.getLogger(name);
	}

	/**
	 * Get a logger by its name.
	 *
	 * @param name The name of the logger.  Uses the same conventions as in
	 * java.util.logging.Logger as this is just a thin wrapper around that
	 * class.
	 * @return The logger.
	 */
	private static Logger getLogger(String name) {
		return new Logger(name);
	}

	/**
	 * Convenience class to get a logger using a class name as the name.
	 * @param aClass The class - its name will be used to retrieve the
	 * logger.
	 * @return The logger.
	 */
	public static Logger getLogger(Class<?> aClass) {
		String name = aClass.getName();
		return getLogger(name);
	}

	public static void resetLogging(String filename) {
		initLoggingFromFile(filename);
	}

	private static void initLogging() {
		Properties props = System.getProperties();

		String logconf = props.getProperty("log.config");
		if (logconf != null) {
			initLoggingFromFile(logconf);
		}
		else {
			staticSetup();
		}
	}

	private static void initLoggingFromFile(String logconf) {
		try {
			InputStream in = new FileInputStream(logconf);
			LogManager lm = LogManager.getLogManager();
			lm.reset();
			lm.readConfiguration(in);
		} catch (FileNotFoundException e) {
			System.err.println("Failed to open logging config file " + logconf);
			staticSetup();
		} catch (IOException e) {
			staticSetup();
		}
	}

	/**
	 * The default setup, which is basically not to do any logging apart from
	 * showing warnings and errors (and I may remove that).
	 */
	private static void staticSetup() {
		// Static setup.
		LogManager.getLogManager().reset();
		java.util.logging.Logger l = java.util.logging.Logger.getLogger("");

		ConsoleHandler handler = new ConsoleHandler();
		UsefulFormatter f = new UsefulFormatter();

		f.setShowTime(false);

		handler.setFormatter(f);
		handler.setLevel(Level.SEVERE);

		l.addHandler(handler);
		l.setLevel(Level.WARNING);
	}

	public boolean isLoggable(Level level) {
		return log.isLoggable(level);
	}

	public boolean isDebugEnabled() {
		return log.isLoggable(Level.FINE);
	}

	public boolean isInfoEnabled() {
		return log.isLoggable(Level.INFO);
	}

	public boolean isWarnEnabled() {
		return log.isLoggable(Level.WARNING);
	}

	public boolean isErrorEnabled() {
		return log.isLoggable(Level.SEVERE);
	}

	/**
	 * Debug message.  We are using the j.u.l FINE level for this.  As it is
	 * possible that the toString method on the logged object is expensive
	 * we check that the message should be logged first.  Though this is
	 * perhaps overkill.
	 *
	 * This comment applies to all the corresponding methods below.
	 *
	 * @param o The object to be logged.
	 */
	public void debug(Object o) {
		if (log.isLoggable(Level.FINE))
			log.fine(o!=null? o.toString(): "null");
	}

	/**
	 * Log a message that consists of a variable number of arguments.  The
	 * arguments are simply concatenated with a space between them.
	 *
	 * The arrayFormat call is very expensive and checking the log level first
	 * is important.  The same applies to all similar routines below.
	 *
	 * @param olist The list of objects to log as one message.
	 */
	public void debug(Object ... olist) {
		if (log.isLoggable(Level.FINE))
			arrayFormat(Level.FINE, olist);
	}

	public void info(Object o) {
		if (log.isLoggable(Level.INFO))
			log.info(o.toString());
	}

	public void info(Object ... olist) {
		if (log.isLoggable(Level.INFO))
			arrayFormat(Level.INFO, olist);
	}

	public void warn(Object o) {
		if (log.isLoggable(Level.WARNING))
			log.warning(o.toString());
	}

	public void warn(Object ... olist) {
		if (log.isLoggable(Level.WARNING))
			arrayFormat(Level.WARNING, olist);
	}

	public void error(Object o) {
		log.severe(o.toString());
	}

	public void error(Object o, Throwable e) {
		log.log(Level.SEVERE, o.toString(), e);
	}

	public void log(Level level, Object o) {
		if (log.isLoggable(level))
			log.log(level, o.toString());
	}

	public void log(Level level, Object ... olist) {
		if (log.isLoggable(Level.INFO))
			arrayFormat(level, olist);
	}
	
	/**
	 * Format the list of arguments by appending them to one string, keeping a
	 * space between them.
	 *
	 * Only call this if you've checked that the message needs to be printed,
	 * otherwise it will all go to waste.
	 *
	 * @param type The Level type FINE, INFO etc.
	 * @param olist The argument list as objects.
	 */
	private void arrayFormat(Level type, Object... olist) {
		StringBuffer sb = new StringBuffer();

		for (Object o : olist) {
			sb.append(o);
			sb.append(' ');
		}
		sb.setLength(sb.length()-1);

		log.log(type, sb.toString());
	}
}
