/*
 * Copyright (C) 2007 Steve Ratcliffe
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
 * Create date: Dec 14, 2007
 */
package uk.me.parabola.imgfmt;

/**
 * @author Steve Ratcliffe
 */
public class ReadFailedException extends RuntimeException {
	/**
	 * Constructs a new runtime exception with the specified detail message. The
	 * cause is not initialized, and may subsequently be initialized by a call to
	 * {@link #initCause}.
	 *
	 * @param message the detail message. The detail message is saved for later
	 * retrieval by the {@link #getMessage()} method.
	 */
	public ReadFailedException(String message) {
		super(message);
	}

	/**
	 * Constructs a new runtime exception with the specified detail message and
	 * cause.  <p>Note that the detail message associated with <code>cause</code>
	 * is <i>not</i> automatically incorporated in this runtime exception's detail
	 * message.
	 *
	 * @param message the detail message (which is saved for later retrieval by the
	 * {@link #getMessage()} method).
	 * @param cause the cause (which is saved for later retrieval by the {@link
	 * #getCause()} method).  (A <tt>null</tt> value is permitted, and indicates
	 * that the cause is nonexistent or unknown.)
	 * @since 1.4
	 */
	public ReadFailedException(String message, Throwable cause) {
		super(message, cause);
	}
}
