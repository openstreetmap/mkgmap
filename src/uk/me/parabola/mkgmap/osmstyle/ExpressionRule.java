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
 * Create date: 07-Nov-2008
 */
package uk.me.parabola.mkgmap.osmstyle;

import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.osmstyle.eval.Op;
import uk.me.parabola.mkgmap.reader.osm.Element;
import uk.me.parabola.mkgmap.reader.osm.GType;
import uk.me.parabola.mkgmap.reader.osm.Rule;
import uk.me.parabola.mkgmap.reader.osm.TypeResult;

/**
 * A rule that contains a condition.  If the condition is matched by the
 * element then the finalize rule is executed and the held gtype is returned.
 * 
 * @author Steve Ratcliffe
 */
public class ExpressionRule implements Rule {
	private static final Logger statsLog = Logger.getLogger(ExpressionRule.class.getPackage().getName()+".stats");

	private Op expression;
	private final GType gtype;
	private Rule finalizeRule;
	private long numEval; // count how often the expression was evaluated 
	private long numTrue; // count how often the evaluation returned true

	/** Finalize rules must not have an element type definition so the add method must never be called. */
	private final static TypeResult finalizeTypeResult = new TypeResult() {
		public void add(Element el, GType type) {
			throw new UnsupportedOperationException("Finalize rules must not contain an action block.");
		}
	};
	
	public ExpressionRule(Op expression, GType gtype) {
		this.expression = expression;
		this.gtype = gtype;
	}

	
	public void resolveType(Element el, TypeResult result) {
		numEval++;
		if (expression.eval(el)) {
			numTrue++;
			// expression matches
			if (finalizeRule != null) {
				if (gtype.isContinueSearch()) {
					el = el.copy();
				}
				// run the finalize rules
				if (gtype.getDefaultName() != null)
					el.addTag("mkgmap:default_name", gtype.getDefaultName());
				finalizeRule.resolveType(el, finalizeTypeResult);
			}
			result.add(el, gtype);
		}
	}

	public int resolveType(int cacheId, Element el, TypeResult result) {
		numEval++;
		if (expression.eval(cacheId, el)){
			numTrue++;
			if (finalizeRule != null) {
				if (gtype.isContinueSearch()) {
					el = el.copy();
				}
				// run the finalize rules
				if (gtype.getDefaultName() != null)
					el.addTag("mkgmap:default_name", gtype.getDefaultName());
				cacheId = finalizeRule.resolveType(cacheId, el, finalizeTypeResult);
			}
			result.add(el, gtype);
		}
		return cacheId;
	}

	public String toString() {
		return expression.toString() + ' ' + gtype;
	}

	public void setFinalizeRule(Rule finalizeRule) {
		this.finalizeRule = finalizeRule;
	}
	
	@Override
	public Rule getFinalizeRule() {
		return finalizeRule;
	}

	public Op getOp(){
		return expression;
	}

	public void setOp(Op expression){
		this.expression = expression;
	}

	@Override
	public void printStats(String header) {
		if (statsLog.isInfoEnabled())
			statsLog.info(header,"stats (rule/evals/true)", this.toString() + "/" + numEval + "/" + numTrue);
	}

	@Override
	public boolean containsExpression(String exp) {
		return expression.toString().contains(exp);
	}
}
