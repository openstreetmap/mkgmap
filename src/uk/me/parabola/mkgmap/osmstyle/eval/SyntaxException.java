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
 * Create date: 03-Nov-2008
 */
package uk.me.parabola.mkgmap.osmstyle.eval;

import java.util.Formatter;

import uk.me.parabola.mkgmap.scan.TokenScanner;

/**
 * A syntax error in a rule file.
 * @author Steve Ratcliffe
 */
public class SyntaxException extends RuntimeException {

	private String fileName;
	private int lineNumber;

	public SyntaxException(String message) {
		super(message);
	}

	public SyntaxException(TokenScanner ts, String msg) {
		super(msg);
		fileName = ts.getFileName();
		lineNumber = ts.getLinenumber();
	}

	public String getRawMessage() {
		return super.getMessage();
	}

	public String getMessage() {
		Formatter fmt = new Formatter();
		fmt.format("Error: ");
		if (fileName != null)
			fmt.format("(%s:%d): ", fileName, lineNumber);

		fmt.format(super.getMessage());
		return fmt.toString();
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public void setLineNumber(int lineNumber) {
		this.lineNumber = lineNumber;
	}
}
