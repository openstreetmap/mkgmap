/*
 * Copyright (C) 2008 Steve Ratcliffe
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
 * Create date: 11-Jan-2009
 */
package func.lib;

/**
 * Standard output and error as produced during a run.
 *
 * @author Steve Ratcliffe
 */
public class Outputs {
	private final String out;
	private final String err;

	public Outputs(String out, String err) {
		this.out = out;
		this.err = err;
	}

	public String getOut() {
		return out;
	}

	public String getErr() {
		return err;
	}

	public void printOut() {
		System.out.println(out);
	}
}
