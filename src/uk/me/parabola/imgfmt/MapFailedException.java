/*
 * Copyright (C) 2010.
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
package uk.me.parabola.imgfmt;

import uk.me.parabola.log.Logger;

/**
 * Used for cases where the current map has failed to compile, but the error
 * is expected to be specific to the map (eg it is too big etc).  When this
 * error is thrown it may be possible for other maps on the command line to
 * succeed.
 *
 * If the error is such that processing further maps is not likely to be
 * successful then use {@link ExitException} instead.
 *
 * @author Steve Ratcliffe
 */
public class MapFailedException extends RuntimeException {
	private static final Logger log = Logger.getLogger(MapFailedException.class);

	/**
	 * Constructs a new runtime exception with the specified detail message.
	 * The cause is not initialized, and may subsequently be initialized by a
	 * call to {@link #initCause}.
	 *
	 * @param message the detail message. The detail message is saved for
	 *                later retrieval by the {@link #getMessage()} method.
	 */
	public MapFailedException(String message) {
		super(message);
		log(message);
	}

	/**
	 * Constructs a new runtime exception with the specified detail message and
	 * cause.  <p>Note that the detail message associated with
	 * <code>cause</code> is <i>not</i> automatically incorporated in
	 * this runtime exception's detail message.
	 *
	 * @param message the detail message (which is saved for later retrieval
	 *                by the {@link #getMessage()} method).
	 * @param cause   the cause (which is saved for later retrieval by the
	 *                {@link #getCause()} method).  (A <tt>null</tt> value is
	 *                permitted, and indicates that the cause is nonexistent or
	 *                unknown.)
	 * @since 1.4
	 */
	public MapFailedException(String message, Throwable cause) {
		super(message, cause);
		log(message);
	}
	
	private static void log(String message){
		String thrownBy = "";
		try{
			StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
			int callerPosInStack = 3; 
			String[] caller = stackTraceElements[callerPosInStack].getClassName().split("\\.");
			thrownBy = "(thrown in " + caller[caller.length-1]+ "." +stackTraceElements[callerPosInStack].getMethodName() + "()) ";
		} catch(Exception e){}
		log.error(thrownBy + message);
	}
}