/*
 * Copyright (C) 2006 Steve Ratcliffe
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
 * Create date: 02-Dec-2006
 */
package uk.me.parabola.imgfmt.fs;

import java.nio.channels.Channel;
import java.nio.channels.ByteChannel;
import java.nio.ByteBuffer;

/**
 * @author Steve Ratcliffe
 */
public interface ImgChannel extends Channel, ByteChannel {

	/**
	 * Allocates a suitable buffer for operations.  It takes acount of the
	 * blocksize and the fact that the file is in little endian.
	 *
	 * @return A suitable byte buffer.
	 */
	public ByteBuffer allocateBuffer();

	/**
	 * Get the file position. Note that this is a logical position relative
	 * to the begining of the file.
	 *
	 * @return The offset in bytes from the beginning of the file.
	 */
	public int position();
}
