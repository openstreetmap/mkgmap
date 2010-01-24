/*
 * Copyright (C) 2009.
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
package uk.me.parabola.imgfmt.app.mdr;

/**
 * A base class that provides access to the MDR configuration data.
 */
public abstract class ConfigBase {
	private MdrConfig config;

	protected boolean isForDevice() {
		return config.isForDevice();
	}

	public void setConfig(MdrConfig config) {
		this.config = config;
	}

	protected MdrConfig getConfig() {
		return config;
	}
}
