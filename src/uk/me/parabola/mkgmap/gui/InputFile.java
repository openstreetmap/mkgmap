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
 * Create date: 13-Oct-2007
 */
package uk.me.parabola.mkgmap.gui;

import java.io.File;

/**
 * This represents one input file and its options that have been set for it.
 *
 * @author Steve Ratcliffe
 */
class InputFile {
	private final File inputFile;
	private final String outputBaseName;

	private String description;
	private boolean enabled;

	InputFile(File inputFile, String outputBaseName) {
		this.inputFile = inputFile;
		this.outputBaseName = outputBaseName;
	}

	public File getInputFile() {
		return inputFile;
	}

	public String getInputFileName() {
		return inputFile.getName();
	}

	public String getOutputBaseName() {
		return outputBaseName;
	}

	public void setEnabled(Boolean enabled) {
		this.enabled = enabled;
	}

	public boolean isEnabled() {
		return enabled;
	}
}
