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

/**
 * Holder for each turn restriction definition.
 * @author Supun Jayathilake
 */
public class PolishTurnRestriction {
    private long nodId;
    private long toNodId;
    private long fromNodId;
    private long viaNodId;
    private long roadIdA;
    private long roadIdB;
    private long roadIdC;
    private byte exceptMask;


    //  Consider as a valid node upon the instantiation.
    private boolean valid = true;

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public long getNodId() {
        return nodId;
    }

    public void setNodId(long nodId) {
        this.nodId = nodId;
    }

    public long getToNodId() {
        return toNodId;
    }

    public void setToNodId(long toNodId) {
        this.toNodId = toNodId;
    }

    public long getFromNodId() {
        return fromNodId;
    }

    public void setFromNodId(long fromNodId) {
        this.fromNodId = fromNodId;
    }

    public long getViaNodId() {
		return viaNodId;
	}

	public void setViaNodId(long viaNodId) {
		this.viaNodId = viaNodId;
	}

	public long getRoadIdA() {
        return roadIdA;
    }

    public void setRoadIdA(long roadIdA) {
        this.roadIdA = roadIdA;
    }

    public long getRoadIdB() {
        return roadIdB;
    }

    public void setRoadIdB(long roadIdB) {
        this.roadIdB = roadIdB;
    }

	public long getRoadIdC() {
		return roadIdC;
	}

	public void setRoadIdC(long roadIdC) {
		this.roadIdC = roadIdC;
	}

	public byte getExceptMask() {
        return exceptMask;
    }

    public void setExceptMask(byte exceptMask) {
        this.exceptMask = exceptMask;
    }

    @Override
    public String toString() {
        return "TurnRestriction[FromNodId=" + fromNodId + ", ViaNodId=" + nodId + ", ToNodId=" + toNodId + "]";
    }

}
