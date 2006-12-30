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


/**
 * Simple logging class.
 *
 * @author Steve Ratcliffe
 */
public class Logger {
	private static FileWriter file;
	private static boolean initFailed;

	public static Logger getLogger(Class aClass) {
		return new Logger();
	}

	public Logger() {
		openFile();
	}

	private static void openFile() {
		if (file != null)
			return;

		try {
			file = new FileWriter("out.log", true);
		} catch (IOException e) {
			if (!initFailed)
				System.err.println("Could not create log file");
			initFailed = true;
		}
	}

	public void debug(String o) {
		if (initFailed)
			return;
		try {
			file.write(o);
			file.write('\n');
			file.flush();
		} catch (IOException e) {
			initFailed = true;
			System.err.println("Failed to write to log, disabling");
		}
	}

	public boolean isDebugEnabled() {
		return false;
	}

	public void debug(Object o) {
	}

	public void warn(Object o) {

	}

	public void info(Object o) {

	}

	public void error(Object o, Throwable e) {

	}
}
