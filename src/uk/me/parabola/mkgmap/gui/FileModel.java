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

import javax.swing.table.AbstractTableModel;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Model for the list of file names that will be processed.
 * 
 * @author Steve Ratcliffe
 */
class FileModel extends AbstractTableModel {
	private static final String FILE_LIST_RESOURCES = "uk/me/parabola/mkgmap/gui/MainFileList";

	private final transient ResourceBundle resource = ResourceBundle.getBundle(FILE_LIST_RESOURCES);
	private final transient List<InputFile> files = new ArrayList<InputFile>();
	private final String[] headers = {
			"",
			resource.getString("heading.input.file"),
			resource.getString("heading.output.file"),
	};

	private int nextOutput = 63240001;
	
	public int getRowCount() {
		return files.size();
	}

	public int getColumnCount() {
		return headers.length;
	}

	/**
	 * Get the actual value to display in each cell.
	 *
	 * @param rowIndex The row, used to look up the InputFile.
	 * @param columnIndex The column, which maps to some property of the InputFile.
	 * @return The value to display.
	 */
	public Object getValueAt(int rowIndex, int columnIndex) {
		if (rowIndex >= files.size())
			return "";

		InputFile inf = files.get(rowIndex);
		switch (columnIndex) {
		case 0:
			return inf.isEnabled();
		case 1:
			return inf.getInputFile();
		case 2:
			return inf.getOutputBaseName();
		default:
			return "";
		}
	}

	/**
	 * Set the class types for each column.
	 * @param columnIndex The column number.
	 * @return The class for that column.
	 */
	public Class<?> getColumnClass(int columnIndex) {
		switch (columnIndex) {
		case 0:
			return Boolean.class;
		case 1:
			return File.class;
		default:
			return super.getColumnClass(columnIndex);
		}
	}

	public String getColumnName(int column) {
		return headers[column];
	}

	public boolean isCellEditable(int rowIndex, int columnIndex) {
		if (columnIndex <= 1)
			return true;

		return false;
	}

	/**
	 * Set the value of a cell in the table.
	 * @param aValue The actual value to be set.
	 * @param rowIndex The row, ie the file.
	 * @param columnIndex The column.
	 */
	public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
		if (rowIndex >= files.size())
			return;

		InputFile inf = files.get(rowIndex);
		if (columnIndex == 0) {
			inf.setEnabled((Boolean) aValue);
		}
	}

	/**
	 * Add a file to the model.
	 *
	 * @param input The input file.
	 * @return The created InputFile object.
	 */
	public InputFile addFile(File input) {
		InputFile inputFile = new InputFile(input, String.valueOf(nextOutput++));
		int size = files.size();
		files.add(inputFile);
		fireTableRowsInserted(size, size+1);

		return inputFile;
	}

	public List<InputFile> getInputFiles() {
		return files;
	}
}
