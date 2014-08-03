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
 * Create date: 30-Nov-2008
 */
package uk.me.parabola.imgfmt.app;

import org.junit.Test;

import static org.junit.Assert.*;


public class CoordTest {
	Coord JFK = new Coord(40.6333333333, -73.7833333333333333);
	Coord JFK2 = new Coord(40.6441666667, -73.7822222222);
	Coord LAX = new Coord(33.95, -118.4);
	Coord LAX2 = new Coord(33.9425222, -118.4071611);
	Coord LAX_E = new Coord(33.95, -118.25);
	Coord D = new Coord(34.5, -116.5);
	Coord X = new Coord(33.95, -73.7833333333333333);
	Coord C = new Coord(33.95, -116.5);
	Coord P0_0 = new Coord(0, 0);
	Coord P0_90 = new Coord(0.0, 90.0);
	Coord P45_0 = new Coord(45.0, 0.0);
	Coord p_a = new Coord(40.5, -73.8);
	Coord p_b = new Coord(40.5, -73.2);
	Coord p_c = new Coord(41.0, -73.2);
	Coord p_x = new Coord(40.57500025932, -73.51348314607);
	Coord p_y = new Coord(40.51032149682, -73.75786516854);
	/**
	 */
	@Test
	public void testBearingRhumb() {
		double b1 = P0_0.bearingToOnRhumbLine(P0_90, true);
		double blj = LAX.bearingToOnRhumbLine(JFK, true);
		assertEquals(90, b1, 0.001);
		assertEquals(79.32388, blj, 0.1);
		
	}
	@Test
	public void testDistanceEqauator() {
		double d1 = P0_0.distance(P0_90);
		double d2 = P0_90.distance(P0_0);
		double d2hp = P0_90.distanceHaversine(P0_0);
		assertEquals(Coord.U / 4, d1, 0.01);
		assertEquals(Coord.U / 4, d2, 0.01);
		assertEquals(Coord.U / 4, d2hp, 0.01);
	}
	
	@Test
	public void testDistanceVert() {
		double d = P45_0.distance(P0_0);
		double d2 = P45_0.distanceHaversine(P0_0);
		double exprectRes = Coord.U / 8;
		assertEquals(exprectRes, d, 1);
		assertEquals(exprectRes, d2, 1);
	}
	@Test
	public void testDistance2() {
		double d_LAX_JFK = LAX2.distance(JFK2);
		double d_LAX_JFK2 = LAX2.distanceHaversine(JFK2);
		double exprectRes = 3977300;
		assertEquals(exprectRes, d_LAX_JFK2, 1000);
		double dx0 = D.distToLineSegment(LAX, JFK);
		assertEquals(27777, dx0, 1);
	}

	@Test
	public void testDistance3() {
		double ab = p_a.distance(p_b);
		double ac = p_a.distance(p_c);
		double bc = p_b.distance(p_c);
		double d_a_bc = p_a.distToLineSegment(p_b,p_c);
		double d_c_ab = p_c.distToLineSegment(p_a,p_b);
		double d_b_ac = p_b.distToLineSegment(p_a,p_c);
		double d_x_ac = p_x.distToLineSegment(p_a,p_c);
		double d_x_ab = p_x.distToLineSegment(p_a,p_b);
		double d_x_bc = p_x.distToLineSegment(p_b,p_c);
		double d_y_ac = p_y.distToLineSegment(p_a,p_c);
		double d_y_ab = p_y.distToLineSegment(p_a,p_b);
		double d_y_bc = p_y.distToLineSegment(p_b,p_c);
		
	}
	
	@Test
	public void testDistanceRhumb() {
		double d0 = P0_0.distanceOnRhumbLine(P0_90);
		assertEquals(Coord.U / 4, d0, 1);
		double d_lax_jfk = LAX.distanceOnRhumbLine(JFK);
		assertEquals(4015991, d_lax_jfk, 1);
		double d_jfk_lax = JFK.distanceOnRhumbLine(LAX);
		assertEquals(4015991, d_jfk_lax, 1);
	}

}
