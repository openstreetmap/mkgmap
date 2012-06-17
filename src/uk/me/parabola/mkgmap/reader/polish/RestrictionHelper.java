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
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.general.MapDetails;

import java.util.ArrayList;
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
    private static final Logger log = Logger.getLogger(RestrictionHelper.class);

    // Holds all collected restrictions.
    private List<PolishTurnRestriction> allRestrictions = new ArrayList<PolishTurnRestriction>();

    public void processAndAddRestrictions(RoadHelper roadHelper, MapDetails mapper) {
        Map<Long, CoordNode> allNodes = roadHelper.getNodeCoords();
        CoordNode from;
        CoordNode to;
        CoordNode via;

        for (PolishTurnRestriction tr : allRestrictions) {
            if (tr.isValid()) { // Process only the restrictions marked as valid.
                from = allNodes.get(tr.getFromNodId());
                to = allNodes.get(tr.getToNodId());
                via = allNodes.get(tr.getNodId());

                if (from != null && to != null && via != null) {            // All nodes participating in the
                    mapper.addRestriction(from, to, via, tr.getExceptMask()); // restriction should be part of the map
                } else {
                    log.error("");
                }
            }
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
