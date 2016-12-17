/*
 * Copyright (C) 2016.
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
package uk.me.parabola.imgfmt.mps;

import uk.me.parabola.io.FileBlock;

public abstract class MpsBlock extends FileBlock {
	private final int codePage;

	public MpsBlock(int kind, int codePage) {
		super(kind);
		this.codePage = codePage;
	}

	public int getCodePage() {
		return codePage;
	}
}
