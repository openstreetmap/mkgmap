/*
 * Copyright (C) 2010, 2012.
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
package uk.me.parabola.mkgmap.reader.polish;

import uk.me.parabola.imgfmt.app.CoordNode;
import uk.me.parabola.imgfmt.app.net.GeneralRouteRestriction;
import uk.me.parabola.mkgmap.general.MapDetails;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Helps handling turn restrictions for Polish format.
 * Remembers, and later, after reading through the map,
 * adds all restrictions to the map.
 *
 * Use one instance of this class per file.
 *
 * @author Supun Jayathilake
 */
public class RestrictionHelper {

    // Holds all collected restrictions.
    private final List<PolishTurnRestriction> allRestrictions = new ArrayList<PolishTurnRestriction>();

    public void processAndAddRestrictions(RoadHelper roadHelper, MapDetails mapper) {
        Map<Long, CoordNode> allNodes = roadHelper.getNodeCoords();

        for (PolishTurnRestriction tr : allRestrictions) {
        	GeneralRouteRestriction rr = new GeneralRouteRestriction("not", tr.getExceptMask(),Long.toString(tr.getNodId()));
        	rr.setFromNode(allNodes.get(tr.getFromNodId()));
        	rr.setFromWayId(tr.getRoadIdA());
        	rr.setToNode(allNodes.get(tr.getToNodId()));
        	if (tr.getViaNodId() != 0){
        		rr.setViaNodes(Arrays.asList(allNodes.get(tr.getNodId()),allNodes.get(tr.getViaNodId())));
        		rr.setViaWayIds(Arrays.asList(tr.getRoadIdB()));
        		rr.setToWayId(tr.getRoadIdC());
        	} else {
        		rr.setViaNodes(Arrays.asList(allNodes.get(tr.getNodId())));
        		rr.setToWayId(tr.getRoadIdB());
        	}
        	mapper.addRestriction(rr); // restriction should be part of the map
        }
    }

    /**
     * Restriction collector.
     * @param restriction Restriction to be added to the map.
     */
    public void addRestriction(PolishTurnRestriction restriction) {
        allRestrictions.add(restriction);
    }
}
