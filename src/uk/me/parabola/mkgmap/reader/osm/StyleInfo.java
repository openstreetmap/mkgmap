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
 * Create date: Apr 20, 2008
 */
package uk.me.parabola.mkgmap.reader.osm;

/**
 * Information about a style.  This is so style authors can include
 * descriptions of their styles within the style itself.
 *
 * @author Steve Ratcliffe
 */
public class StyleInfo {

	private String version;
	private String summary;
	private String longDescription;
	private String baseStyleName;


	public String getSummary() {
		return summary == null ? "No summary available" : summary.trim();
	}

	public String getVersion() {
		return version == null ? "1" : version.trim();
	}

	public String getLongDescription() {
		return longDescription != null ? longDescription.trim() : "";
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public void setSummary(String summary) {
		this.summary = summary;
	}

	public void setLongDescription(String longDescription) {
		this.longDescription = longDescription;
	}

	public String getBaseStyleName() {
		return baseStyleName;
	}

	public void setBaseStyleName(String value) {
		this.baseStyleName = value.trim();
	}
}
