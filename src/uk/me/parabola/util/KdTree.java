/*
 * Copyright (C) 2014 Gerd Petermann
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
package uk.me.parabola.util;


import java.util.HashSet;
import java.util.Set;
import uk.me.parabola.imgfmt.app.Coord;


/**
 * A kd-tree (2D) implementation to solve the nearest neighbor problem.
 * The tree is not explicitly balanced.
 * 
 * @author Gerd Petermann
 *
 */
public class KdTree <T extends Locatable> {
	private static final boolean ROOT_NODE_USES_LONGITUDE = false;
	
	private class KdNode {
		T point;
		KdNode left;
		KdNode right;

		KdNode(T p) {
			point = p;
		}
	}
	// the tree root
    private KdNode root;
    // number of saved objects  
    private int size;

    // helpers 
    private T nextPoint ;
    private double minDist;
    private double maxDist;
    private Set<T> set;

    /**
     *  create an empty tree
     */
	public KdTree() {
		root = null;
	}

	public long size()
	{
		return size;
	}

	
	/**
	 * Start the add action with the root
	 * @param toAdd
	 */
	public void add(T toAdd) {
		size++;
		root = add(toAdd, root, ROOT_NODE_USES_LONGITUDE);
	}

	/**
	 * Compares the given axis of both points. 
	 * @param longitude <code>true</code>: compare longitude; <code>false</code> compare latitude
	 * @param c1 a point
	 * @param c2 another point
	 * @return <code>true</code> the axis value of c1 is smaller than c2; 
	 * 		<code>false</code> the axis value of c1 is equal or larger than c2
	 */
	private boolean isSmaller(boolean longitude, Coord c1, Coord c2) {
		if (longitude) {
			return c1.getLongitude() < c2.getLongitude();
		} else {
			return c1.getLatitude() < c2.getLatitude();
		}
	}
	
	/**
	 * Recursive routine to find the right place for inserting 
	 * into the tree.  
	 * @param toAdd the point
	 * @param tree the subtree root node where to add (maybe <code>null</code>)
	 * @param useLongitude <code>true</code> the tree node uses longitude for comparison; 
	 * 		<code>false</code> the tree node uses latitude for comparison
	 * @return the subtree root node after insertion
	 */
    private KdNode add( T toAdd, KdNode tree,  boolean useLongitude){
        if( tree == null ) {
            tree = new KdNode( toAdd );
        } else {
        	if(isSmaller(useLongitude, toAdd.getLocation(), tree.point.getLocation())) {
        		tree.left = add(toAdd, tree.left, !useLongitude);
        	} else {
        		tree.right = add(toAdd, tree.right, !useLongitude);
        	}
        }
        return tree;
    }
    
	/**
	 * Searches for the point that has smallest distance to the given point.
	 * @param p the point to search for
	 * @return the point with shortest distance to <var>p</var>
	 */
	public T findNextPoint(Locatable p) {
		// reset 
		minDist = Double.MAX_VALUE;
		maxDist = -1;
		set = null;
		nextPoint = null;
		
		// false => first node is a latitude level
		return findNextPoint(p, root, ROOT_NODE_USES_LONGITUDE);
	}

	/**
	 * Searches for the point that has smallest distance to the given point.
	 * @param p the point to search for
	 * @return the point with shortest distance to <var>p</var>
	 */
	public Set<T> findNextPoint(Locatable p, double maxDist) {
		// reset 
		minDist = Double.MAX_VALUE;
		this.maxDist = Math.pow(maxDist * 360 / Coord.U, 2); // convert maxDist in meter to distanceInDegreesSquared
		nextPoint = null;
		this.set = new HashSet<>();
		// false => first node is a latitude level
		findNextPoint(p, root, ROOT_NODE_USES_LONGITUDE);
		return set;
	}

	private T findNextPoint(Locatable p, KdNode tree, boolean useLongitude) {
		boolean continueWithLeft = false;
		if (tree == null)
			return nextPoint;
		
		if (tree.left == null && tree.right == null){
			double dist = tree.point.getLocation().distanceInDegreesSquared(p.getLocation());
			if (dist <= maxDist && set != null){
				set.add(tree.point);
			}
			if (dist < minDist){
				nextPoint = tree.point;
				if (dist < maxDist)
					minDist = maxDist;
				else
					minDist = dist;
			}
			return nextPoint;
		}
		else {
			if (isSmaller(useLongitude, p.getLocation(), tree.point.getLocation())){
				continueWithLeft = false;
				nextPoint = findNextPoint(p, tree.left, !useLongitude);
			}
			else {
				continueWithLeft = true;
				nextPoint = findNextPoint(p, tree.right, !useLongitude);
			}
		}
		
		double dist = tree.point.getLocation().distanceInDegreesSquared(p.getLocation());
		if (dist <= maxDist && set != null)
			set.add(tree.point);
		if (dist < minDist){
			nextPoint = tree.point;
			minDist = dist;
			if (dist < maxDist)
				minDist = maxDist;
			else
				minDist = dist;
		} 		
		// do we have to search the other part of the tree?
		Coord test;
		if (useLongitude)
			test = Coord.makeHighPrecCoord(p.getLocation().getHighPrecLat(), tree.point.getLocation().getHighPrecLon());
		else
			test = Coord.makeHighPrecCoord(tree.point.getLocation().getHighPrecLat(), p.getLocation().getHighPrecLon());
		if (test.distanceInDegreesSquared(p.getLocation()) < minDist){
			if (continueWithLeft) 
				nextPoint = findNextPoint(p, tree.left, !useLongitude);
			else
				nextPoint = findNextPoint(p, tree.right, !useLongitude);
		}
		return nextPoint;
	}
} 
