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
 * Create date: 07-Dec-2008
 */
package uk.me.parabola.mkgmap.osmstyle.eval;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import uk.me.parabola.mkgmap.scan.SyntaxException;

/**
 * Converting quantities from one unit to another.
 *
 * @author Steve Ratcliffe
 */
public class UnitConversions {
	private static final Pattern CODE_RE = Pattern.compile("(.*)=>(.*)");

	private static final Map<UnitType, Map<String, Double>> CONVERSIONS = new HashMap<>();

	private static final Map<String, Double> LENGTH_FACTORS = new HashMap<>();
	private static final Map<String, Double> SPEED_FACTORS = new HashMap<>();
	private static final Map<String, Double> WEIGHT_FACTORS = new HashMap<>();

	static {
		Map<String, Double> m = LENGTH_FACTORS;
		m.put("m", 1.0);
		m.put("km", 1000.0);
		m.put("ft", 0.3048);
		m.put("feet", 0.3048);
		m.put("mi", 1_609.344);
		CONVERSIONS.put(UnitType.LENGTH, LENGTH_FACTORS);

		m = SPEED_FACTORS;
		m.put("kmh", 1.0);
		m.put("km/h", 1.0);
		m.put("kmph", 1.0);
		m.put("mph", 1.60934);
		m.put("knots", 1.852);
		CONVERSIONS.put(UnitType.SPEED, SPEED_FACTORS);

		m = WEIGHT_FACTORS;
		m.put("t", 1.0);
		m.put("kg", 0.001);
		m.put("lb", 0.00045359237);
		m.put("lbs", 0.00045359237);
		CONVERSIONS.put(UnitType.WEIGHT, WEIGHT_FACTORS);
	}

	/** The type of unit, speed, length etc. */
	private final UnitType unitType;
	/** The target unit */
	private final String target;
	/** The factor to convert from the default source unit to the target */
	private final double defaultFactor;

	/**
	 * Create a converter between the units given in the {@code code} argument.
	 *
	 * This will be something like m=>ft to convert from meters to feet. If the input is a plain
	 * value such as 10, then the input is in meters and it is converted to feet.  If the
	 * input already has a unit, eg 10ft, then the conversion will be from that unit to
	 * the target unit. So in this case, since the input is already in feet, then result is 10.
	 *
	 * @param code A specifier from=>to.
	 */
	public static UnitConversions createConversion(String code) {
		Matcher matcher = CODE_RE.matcher(code);
		if (!matcher.matches())
			throw new SyntaxException(String.format("Unrecognised unit conversion: '%s'", code));

		String source = matcher.group(1);
		String target = matcher.group(2);

		return new UnitConversions(source, target);
	}

	private UnitConversions(String source, String target) {
		this.target = target;

		UnitType type = getType(source);
		if (type != null && type == getType(target)) {
			unitType = type;
			defaultFactor = getConversion(source);
		} else {
			unitType = null;
			defaultFactor = 1;
		}
	}

	/**
	 * The conversion factor; multiply by this value to convert to the target unit.
	 * @param source If this is not null, then use this as the source unit.
	 * @return The factor to multiply the value by to convert from the source to target units.
	 */
	public Double convertFactor(String source) {
		// The value has no unit, so use the default one. We already know the conversion factor for the
		// default source unit, so just return it.
		if (source == null)
			return defaultFactor;

		if (unitType == null)
			return null;

		return getConversion(source);
	}

	public double convertFrom(String source) {
		assert source != null && unitType != null;

		if (CONVERSIONS.get(unitType).containsKey(source))
			return getConversion(source);
		else
			return 0;
	}

	public boolean isValid() {
		return unitType != null;
	}

	/**
	 * Find the unit type that corresponds to the unit abbreviation.
	 *
	 * For example for km, this would be LENGTH.
	 * @param source A unit specifier.
	 * @return The type of unit.
	 */
	private static UnitType getType(String source) {
		for (UnitType t : UnitType.values()) {
			Map<String, Double> map = CONVERSIONS.get(t);
			for (String unit : map.keySet()) {
				if (unit.equals(source))
					return t;
			}
		}
		return null;
	}

	private double getFactor(String unit) {
		assert isValid();

		Double d = CONVERSIONS.get(unitType).get(unit);
		return d == null? 0: d;
	}

	private Double getConversion(String source) {
		Double in = getInFactor(unitType, source);
		if (in == null)
			return null;
		double out = getOutFactor(unitType, target);

		return in * out;
	}

	private static Double getInFactor(UnitType type, String source) {
		if (source.isEmpty())
			return 1.0;

		Map<String, Double> map = CONVERSIONS.get(type);
		assert map != null;

		return map.get(source);
	}

	private static double getOutFactor(UnitType type, String target) {
		return 1.0 / getInFactor(type, target);
	}

	public double getDefaultFactor() {
		return defaultFactor;
	}

	public static enum UnitType {
		LENGTH,
		SPEED,
		WEIGHT,
	}
}
