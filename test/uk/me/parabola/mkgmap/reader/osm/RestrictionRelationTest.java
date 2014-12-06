/*
 * Copyright (C) 2014
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
 */ 

package uk.me.parabola.mkgmap.reader.osm;

import static org.junit.Assert.*;

import org.junit.Test;

import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.net.AccessTagsAndBits;

/**
 * Test evaluation of restriction relations
 * @author GerdP
 *
 */
public class RestrictionRelationTest {
	private final static byte DEFAULT_EXCEPTION =  AccessTagsAndBits.FOOT  | AccessTagsAndBits.EMERGENCY;
	
	@Test
	public void basicTest() {
		GeneralRelation gr = createRelation();
		gr.addTag("restriction","no_left_turn");
		gr.addTag("except","bicycle");
		RestrictionRelation rr = new RestrictionRelation(gr);
		rr.eval(new Area(-100,-100,1000,1000));
		assertTrue(rr.isValid());
		assertEquals(AccessTagsAndBits.BIKE | DEFAULT_EXCEPTION, rr.getExceptMask());
	}
	
	@Test
	public void footTest() {
		GeneralRelation gr = createRelation();
		gr.addTag("restriction:foot","no_left_turn");
		RestrictionRelation rr = new RestrictionRelation(gr);
		rr.eval(new Area(-100,-100,1000,1000));
		assertTrue(rr.isValid());
		assertEquals(~AccessTagsAndBits.FOOT , rr.getExceptMask());
	}

	@Test
	public void footAndBikeTest() {
		GeneralRelation gr = createRelation();
		gr.addTag("restriction:foot","no_left_turn");
		gr.addTag("restriction:bicycle","no_left_turn");
		RestrictionRelation rr = new RestrictionRelation(gr);
		rr.eval(new Area(-100,-100,1000,1000));
		assertTrue(rr.isValid());
		assertEquals(~(AccessTagsAndBits.FOOT |  AccessTagsAndBits.BIKE), rr.getExceptMask());
	}

	@Test
	public void psvTest() {
		GeneralRelation gr = createRelation();
		gr.addTag("restriction","no_left_turn");
		gr.addTag("except","psv");
		RestrictionRelation rr = new RestrictionRelation(gr);
		rr.eval(new Area(-100,-100,1000,1000));
		assertTrue(rr.isValid());
		assertEquals(AccessTagsAndBits.BUS | AccessTagsAndBits.TAXI | DEFAULT_EXCEPTION, rr.getExceptMask());
	}

	@Test
	public void multipleExeptTest() {
		GeneralRelation gr = createRelation();
		gr.addTag("restriction","no_left_turn");
		gr.addTag("except","psv;bicycle");
		RestrictionRelation rr = new RestrictionRelation(gr);
		rr.eval(new Area(-100,-100,1000,1000));
		assertTrue(rr.isValid());
		assertEquals(AccessTagsAndBits.BUS | AccessTagsAndBits.TAXI | AccessTagsAndBits.BIKE | DEFAULT_EXCEPTION, rr.getExceptMask());
	}

	@Test
	public void multipleExeptTestWithUnknown() {
		GeneralRelation gr = createRelation();
		gr.addTag("restriction","no_left_turn");
		gr.addTag("except","psv;xyz;bicycle");
		RestrictionRelation rr = new RestrictionRelation(gr);
		rr.eval(new Area(-100,-100,1000,1000));
		assertTrue(rr.isValid());
		assertEquals(AccessTagsAndBits.BUS | AccessTagsAndBits.TAXI | AccessTagsAndBits.BIKE | DEFAULT_EXCEPTION, rr.getExceptMask());
	}

	@Test
	public void multipleExcplicitTestWithUnknown() {
		GeneralRelation gr = createRelation();
		gr.addTag("restriction:motorcar","no_left_turn");
		gr.addTag("restriction:hgv","no_left_turn");
		gr.addTag("restriction:xyz","no_left_turn");
		RestrictionRelation rr = new RestrictionRelation(gr);
		rr.eval(new Area(-100,-100,1000,1000));
		assertTrue(rr.isValid());
		assertEquals(~(AccessTagsAndBits.CAR |AccessTagsAndBits.TRUCK)  , rr.getExceptMask());
	}

	@Test
	public void excplicitTestWithUnknown() {
		GeneralRelation gr = createRelation();
		gr.addTag("restriction:xyz","no_left_turn");
		RestrictionRelation rr = new RestrictionRelation(gr);
		rr.eval(new Area(-100,-100,1000,1000));
		assertFalse(rr.isValid());
	}

	@Test
	public void motor_vehicleTest() {
		GeneralRelation gr = createRelation();
		gr.addTag("restriction:motor_vehicle","no_left_turn");
		RestrictionRelation rr = new RestrictionRelation(gr);
		rr.eval(new Area(-100,-100,1000,1000));
		assertTrue(rr.isValid());
		assertEquals(AccessTagsAndBits.BIKE | DEFAULT_EXCEPTION, rr.getExceptMask());
	}

	@Test
	public void motor_vehicleTest2() {
		GeneralRelation gr = createRelation();
		gr.addTag("restriction","no_left_turn");
		gr.addTag("type", "restriction:motor_vehicle");
		RestrictionRelation rr = new RestrictionRelation(gr);
		rr.eval(new Area(-100,-100,1000,1000));
		assertTrue(rr.isValid());
		assertEquals(AccessTagsAndBits.BIKE | DEFAULT_EXCEPTION, rr.getExceptMask());
	}

	@Test
	public void motor_vehicleExceptCarTest() {
		GeneralRelation gr = createRelation();
		gr.addTag("except","motorcar");
		gr.addTag("restriction:motor_vehicle","no_left_turn");
		RestrictionRelation rr = new RestrictionRelation(gr);
		rr.eval(new Area(-100,-100,1000,1000));
		assertTrue(rr.isValid());
		assertEquals(AccessTagsAndBits.BIKE | AccessTagsAndBits.CAR | DEFAULT_EXCEPTION, rr.getExceptMask());
	}

	@Test
	public void motor_vehicleExceptCarTest2() {
		GeneralRelation gr = createRelation();
		gr.addTag("except","motorcar");
		gr.addTag("restriction", "no_left_turn");
		gr.addTag("type", "restriction:motor_vehicle");
		RestrictionRelation rr = new RestrictionRelation(gr);
		rr.eval(new Area(-100,-100,1000,1000));
		assertTrue(rr.isValid());
		assertEquals(AccessTagsAndBits.BIKE | AccessTagsAndBits.CAR | DEFAULT_EXCEPTION, rr.getExceptMask());
	}

	@Test
	public void noEmergencyTest1() {
		GeneralRelation gr = createRelation();
		gr.addTag("restriction:emergency","no_left_turn");
		RestrictionRelation rr = new RestrictionRelation(gr);
		rr.eval(new Area(-100,-100,1000,1000));
		assertTrue(rr.isValid());
		assertEquals(~AccessTagsAndBits.EMERGENCY, rr.getExceptMask());
	}

	@Test
	public void noEmergencyTest2() {
		GeneralRelation gr = createRelation();
		gr.addTag("restriction:motor_vehicle","no_left_turn");
		gr.addTag("restriction:emergency","no_left_turn");
		RestrictionRelation rr = new RestrictionRelation(gr);
		rr.eval(new Area(-100,-100,1000,1000));
		assertTrue(rr.isValid());
		assertEquals(AccessTagsAndBits.BIKE | AccessTagsAndBits.FOOT, rr.getExceptMask());
	}

	@Test
	public void mixedDirectionsTest() {
		GeneralRelation gr = createRelation();
		gr.addTag("restriction:car","no_left_turn");
		gr.addTag("restriction:truck","no_u_turn");
		RestrictionRelation rr = new RestrictionRelation(gr);
		rr.eval(new Area(-100,-100,1000,1000));
		assertFalse(rr.isValid());
	}

	@Test
	public void ignoreMotorcycleTest1() {
		GeneralRelation gr = createRelation();
		gr.addTag("restriction:motorcycle","no_u_turn");
		RestrictionRelation rr = new RestrictionRelation(gr);
		rr.eval(new Area(-100,-100,1000,1000));
		assertFalse(rr.isValid());
	}
	
	@Test
	public void ignoreMotorcycleTest2() {
		GeneralRelation gr = createRelation();
		gr.addTag("restriction","no_u_turn");
		gr.addTag("except","motorcycle");
		RestrictionRelation rr = new RestrictionRelation(gr);
		rr.eval(new Area(-100,-100,1000,1000));
		assertTrue(rr.isValid());
		assertEquals(DEFAULT_EXCEPTION, rr.getExceptMask());		
	}
	
	private static GeneralRelation createRelation(){
		GeneralRelation gr = new GeneralRelation(1);
		gr.addTag("type", "restriction");
		Way fromWay = new Way(1);
		Way toWay = new Way(2);
		Coord viaCoord = new Coord(100,100);
		Node viaNode = new Node(1, viaCoord);
		fromWay.addPoint(new Coord(0,0));
		fromWay.addPoint(viaCoord);
		toWay.addPoint(new Coord(120,200));
		toWay.addPoint(viaCoord);
		gr.addElement("from", fromWay);
		gr.addElement("to", toWay);
		gr.addElement("via", viaNode);
		return gr;
	}
}
