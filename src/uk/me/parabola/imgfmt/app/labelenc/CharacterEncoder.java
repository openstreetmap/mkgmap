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
 * Create date: 14-Jan-2007
 */
package uk.me.parabola.imgfmt.app.labelenc;

/**
 * Interface for encoding characters for use in the Label section of a .img
 * file.
 *
 * Older units are only able to display uppercase ascii characters.  Newer ones
 * can also display latin1 characters and perhaps lowercase too.  I believe
 * that it is possible to buy Japanese units that display Japanese characters
 * too.
 *
 * So we need different implementations to deal with all this.  It is made
 * harder because the possibilities are not known.  Many experimental
 * implementations may be needed before settling on a good one.
 *
 * @author Steve Ratcliffe
 */
public interface CharacterEncoder {

	public EncodedText encodeText(String text);
}
