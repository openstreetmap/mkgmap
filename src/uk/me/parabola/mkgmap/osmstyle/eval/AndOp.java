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

import uk.me.parabola.mkgmap.reader.osm.Element;

/**
 * The AND operation.  The second term is not executed if the first one
 * fails.
 *
 * @author Steve Ratcliffe
 */
public class AndOp extends AbstractBinaryOp {

	public AndOp() {
		setType(NodeType.AND);
	}

	public boolean eval(Element el) {
		return getFirst().eval(el) && getSecond().eval(el);
	}

	public boolean eval(int cacheId, Element el){
		if (lastCachedId > cacheId){
			System.err.println("fatal error: cache id invalid");
		}
		if (lastCachedId != cacheId){
			lastRes = getFirst().eval(cacheId, el);
			if (lastRes == true)
				lastRes = getSecond().eval(cacheId, el);
			lastCachedId = cacheId;
		}
//		else { 
//			System.out.println("cached: " + cacheId + " " + toString());
//			boolean testRes = getFirst().eval(cacheId, el);
//			if (testRes == true)
//				testRes = getSecond().eval(cacheId, el);
//			if (lastRes != testRes){
//				System.out.println("invalid cached result");
//			}
//		}
		return lastRes;
	}


	
	public int priority() {
		return 5;
	}
	
}
