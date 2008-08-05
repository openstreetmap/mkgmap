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
 * Create date: 16-Jul-2008
 */
package uk.me.parabola.mkgmap.general;

/**
 * @author Steve Ratcliffe
 */
public class IndexAndNode {
	private int index;
	private long nodeId;

	public IndexAndNode(int index, long nodeId) {
		this.index = index;
		this.nodeId = nodeId;
	}
}
