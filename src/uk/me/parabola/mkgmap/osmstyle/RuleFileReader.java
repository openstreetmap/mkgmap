/*
 * Copyright (C) 2008, 2012 Steve Ratcliffe
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License version 3 or version 2
 *  as published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *
 * Author: Steve Ratcliffe
 * Create date: 02-Nov-2008
 */
package uk.me.parabola.mkgmap.osmstyle;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Formatter;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.general.LevelInfo;
import uk.me.parabola.mkgmap.osmstyle.actions.Action;
import uk.me.parabola.mkgmap.osmstyle.actions.ActionList;
import uk.me.parabola.mkgmap.osmstyle.actions.ActionReader;
import uk.me.parabola.mkgmap.osmstyle.actions.AddTagAction;
import uk.me.parabola.mkgmap.osmstyle.actions.DeleteAction;
import uk.me.parabola.mkgmap.osmstyle.eval.EqualsOp;
import uk.me.parabola.mkgmap.osmstyle.eval.ExpressionReader;
import uk.me.parabola.mkgmap.osmstyle.eval.NotOp;
import uk.me.parabola.mkgmap.osmstyle.eval.Op;
import uk.me.parabola.mkgmap.osmstyle.eval.ValueOp;
import uk.me.parabola.mkgmap.osmstyle.function.GetTagFunction;
import uk.me.parabola.mkgmap.reader.osm.FeatureKind;
import uk.me.parabola.mkgmap.reader.osm.GType;
import uk.me.parabola.mkgmap.reader.osm.Rule;
import uk.me.parabola.mkgmap.scan.SyntaxException;
import uk.me.parabola.mkgmap.scan.TokType;
import uk.me.parabola.mkgmap.scan.Token;
import uk.me.parabola.mkgmap.scan.TokenScanner;

/**
 * Read a rules file.  A rules file contains a list of rules and the
 * resulting garmin type, should the rule match.
 *
 * @author Steve Ratcliffe
 */
public class RuleFileReader {
	private static final Logger log = Logger.getLogger(RuleFileReader.class);

	private final FeatureKind kind;
	private final TypeReader typeReader;

	private final RuleSet rules;
	private RuleSet finalizeRules;
	private final boolean performChecks;
	private final Map<Integer, List<Integer>> overlays;

	private final Deque<Op[]> ifStack = new LinkedList<>();
	public static final String IF_PREFIX = "mkgmap:if:"; 

	private boolean inFinalizeSection;

	private final ExpressionArranger arranger = new ExpressionArranger();

	public RuleFileReader(FeatureKind kind, LevelInfo[] levels, RuleSet rules, boolean performChecks, 
			Map<Integer, List<Integer>> overlays) {
		this.kind = kind;
		this.rules = rules;
		this.performChecks = performChecks;
		this.overlays = overlays;
		typeReader = new TypeReader(kind, levels);
	}

	/**
	 * Read a rules file.
	 * @param loader A file loader.
	 * @param name The name of the file to open.
	 * @throws FileNotFoundException If the given file does not exist.
	 */
	public void load(StyleFileLoader loader, String name) throws FileNotFoundException {
		loadFile(loader, name);
		rules.prepare();
		if (finalizeRules != null) {
			finalizeRules.prepare();
			rules.setFinalizeRule(finalizeRules);
		}
	}

	/**
	 * Load a rules file.  This should be used when calling recursively when including
	 * files.
	 */
	private void loadFile(StyleFileLoader loader, String name) throws FileNotFoundException {
		Reader r = loader.open(name);
		TokenScanner scanner = new TokenScanner(name, r);
		scanner.setExtraWordChars("-:.");

		ExpressionReader expressionReader = new ExpressionReader(scanner, kind);
		ActionReader actionReader = new ActionReader(scanner);

		// Read all the rules in the file.
		scanner.skipSpace();
		while (!scanner.isEndOfFile()) {
			if (checkCommand(loader, scanner, expressionReader))
				continue;

			if (scanner.isEndOfFile())
				break;

			Op expr = expressionReader.readConditions(ifStack);
			expr = arranger.arrange(expr);

			ActionList actionList = actionReader.readActions();
			checkIfStack(actionList);

			if (performChecks && this.kind == FeatureKind.RELATION) {
				String actionsString = actionList.getList().toString();
				if (actionsString.contains("set mkgmap:stylefilter") || actionsString.contains("add mkgmap:stylefilter")) {
					log.error("Style file", name, "should not set or add the special tag mkgmap:stylefilter:", actionsString);
				}
			}

			List<GType> types = new ArrayList<>();
			while (scanner.checkToken("[")) {
				GType type = typeReader.readType(scanner, performChecks, overlays);
				types.add(type);
				scanner.skipSpace();
			}
			
			// If there is an action list, then we don't need a type
			if (types.isEmpty() && actionList.isEmpty())
				throw new SyntaxException(scanner, "No type definition given");

			if (types.isEmpty())
				saveRule(scanner, expr, actionList, null);
			
			if (types.size() >= 2 && actionList.isModifyingTags()) {
				throw new SyntaxException(scanner, "Combination of multiple type definitions with tag modifying action is not yet supported.");
			}

			for (int i = 0; i < types.size(); i++) {
				GType type = types.get(i);
				if (i + 1 < types.size()) {
					type.setContinueSearch(true);
				}
				// No need to create a deep copy of expr
				saveRule(scanner, expr, actionList, type);
				actionList = new ActionList(Collections.emptyList(), Collections.emptySet());
			}
		}

		rules.addUsedTags(expressionReader.getUsedTags());
		rules.addUsedTags(actionReader.getUsedTags());
	}

	/**
	 * Check for a keyword that introduces a command.
	 *
	 * Commands are context sensitive, if a keyword is used is part of an expression, then it must still
	 * work. In other words the following is valid:
	 * <pre>
	 *     include 'filename';
	 *
	 *     include=yes [0x02 ...]
	 * </pre>
	 * To achieve this the keyword is a) not quoted, b) is followed by text or quoted text or some symbol that cannot
	 * be part of an expression.
	 *
	 * Called before reading an expression, must put back any token (apart from whitespace) if there is
	 * not a command.
	 * @return true if a command was found. The caller should check again for a command.
	 * @param currentLoader The current style loader. Any included files are loaded from here, if no other
	 * style is specified.
	 * @param scanner The current token scanner.
	 * @param expressionReader The current expression reader
	 */
	private boolean checkCommand(StyleFileLoader currentLoader, TokenScanner scanner, ExpressionReader expressionReader) {
		scanner.skipSpace();
		if (scanner.isEndOfFile())
			return false;

		if (scanner.checkToken("include")) {
			if (readInclude(currentLoader, scanner)) return true;

		} else if (scanner.checkToken("if")) {
			if (readIf(scanner, expressionReader)) return true;

		} else if (scanner.checkToken("else")) {
			if (readElse(scanner)) return true;

		} else if (scanner.checkToken("end")) {
			if (readEnd(scanner)) return true;

		} else if (scanner.checkToken("<")) {
			// check if it is the start label of the <finalize> section
			if (readFinalize(scanner)) return true;
		}
		scanner.skipSpace();
		return false;
	}

	private boolean readIf(TokenScanner scanner, ExpressionReader expressionReader) {
		// Take the 'if' token
		Token tok = scanner.nextToken();
		scanner.skipSpace();

		// If 'if'' is being used as a keyword then it is followed by a '('.
		Token next = scanner.peekToken();
		if (next.getType() == TokType.SYMBOL && next.isValue("(")) {
			Op origExpr = expressionReader.readConditions();
			scanner.validateNext("then");
			
			// add rule expr { set <ifVar> = true } 
			String ifVar = getNextIfVar();
			ArrayList<Action> actions = new ArrayList<>(1);
			actions.add(new AddTagAction(ifVar,"true", true));
			ActionList actionList = new ActionList(actions, Collections.singleton(ifVar+"=true"));
			saveRule(scanner, origExpr, actionList, null);
			// create expression (<ifVar> = true) 
			EqualsOp safeExpr = new EqualsOp();
			safeExpr.setFirst(new GetTagFunction(ifVar));
			safeExpr.setSecond(new ValueOp("true"));
			Op[] ifExpressions = {origExpr, safeExpr};  
			ifStack.addLast(ifExpressions);
			
			return true;
		} else {
			// Wrong syntax for if statement, so push back token to allow a possible expression to be read
			scanner.pushToken(tok);
		}
		return false;
	}

	private boolean readElse(TokenScanner scanner) {
		Token tok = scanner.nextToken();
		scanner.skipSpace();

		Token next = scanner.peekToken();
		if (next.getType() == TokType.SYMBOL && !next.isValue("(") && !next.isValue("!")) {
			scanner.pushToken(tok);
			return false;
		}

		Op[] ifExpressions = ifStack.removeLast();
		for (int i = 0; i < ifExpressions.length; i++) {
			Op op = ifExpressions[i];
			NotOp not = new NotOp();
			not.setFirst(op);
			ifExpressions[i] = not;
		}
		ifStack.addLast(ifExpressions);

		return true;
	}

	private boolean readEnd(TokenScanner scanner) {
		Token tok = scanner.nextToken();
		scanner.skipSpace();
		if (ifStack.isEmpty()) {
			scanner.pushToken(tok);
			return false;
		}

		ifStack.removeLast();
		return true;
	}

	/**
	 * Check if one of the actions in the actionList would change the result of a previously read if expression.
	 * If so, use the alternative expression with the generated tag.
	 */
	private void checkIfStack(ActionList actionList) {
		if (actionList.isEmpty())
			return;
		for (Op[] ops : ifStack) {
			if (ops[0] != ops[1]) {
				// check if this action can change the result of the initial if expression 
				if (possiblyChanged(ops[0], actionList)) {
					// the result may be changed, use the generated tag for all further rules in this if / else block
					ops[0] = ops[1];
				} 
			}
		}
	}

	/**
	 * Check if the expression depends on tags modified by an action in the action list. 
	 * @param expr the expression to check
	 * @param actionList the ActionList 
	 * @return true if the value of the expression depends on one or more of the changeable tags
	 */
	private boolean possiblyChanged(Op expr, ActionList actionList) {
		Set<String> evaluated = expr.getEvaluatedTagKeys();
		if (evaluated.isEmpty())
			return false;
		for (String tagKey : evaluated) {
			for (String s : actionList.getChangeableTags()) {
				int pos = s.indexOf("=");
				String key = pos > 0 ? s.substring(0, pos) : s;
				if (tagKey.equals(key)) {
					return true;
				}
			}
			for (Action a : actionList.getList()) {
				if (a instanceof DeleteAction) {
					if (a.toString().contains(tagKey)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private boolean readInclude(StyleFileLoader currentLoader, TokenScanner scanner) {
		// Consume the 'include' token and skip spaces
		Token token = scanner.nextToken();
		scanner.skipSpace();

		// If include is being used as a keyword then it is followed by a word or a quoted word.
		Token next = scanner.peekToken();
		if (next.getType() == TokType.TEXT
				|| next.getType() == TokType.SYMBOL && (next.isValue("'") || next.isValue("\"")))
		{
			String filename = scanner.nextWord();

			StyleFileLoader loader = currentLoader;
			scanner.skipSpace();

			// The include can be followed by an optional 'from' clause. The file is read from the given
			// style-name in that case.
			if (scanner.checkToken("from")) {
				scanner.nextToken();
				String styleName = scanner.nextWord();
				if (Objects.equals(styleName, ";"))
					throw new SyntaxException(scanner, "No style name after 'from'");

				try {
					loader = StyleFileLoader.createStyleLoader(null, styleName);
				} catch (FileNotFoundException e) {
					throw new SyntaxException(scanner, "Cannot find style: " + styleName);
				}
			}

			if (scanner.checkToken(";"))
				scanner.nextToken();

			try {
				loadFile(loader, filename);
				return true;
			} catch (FileNotFoundException e) {
				throw new SyntaxException(scanner, "Cannot open included file: " + filename);
			} finally {
				if (loader != currentLoader)
					Utils.closeFile(loader);
			}
		} else {
			// Wrong syntax for include statement, so push back token to allow a possible expression to be read
			scanner.pushToken(token);
		}
		return false;
	}

	private boolean readFinalize(TokenScanner scanner) {
		Token token = scanner.nextToken();
		if (scanner.checkToken("finalize")) {
			Token finalizeToken = scanner.nextToken();
			if (scanner.checkToken(">")) {
				if (inFinalizeSection) {
					// there are two finalize sections which is not allowed
					throw new SyntaxException(scanner, "There is only one finalize section allowed");
				} else {
					// consume the > token
					scanner.nextToken();
					// mark start of the finalize block
					inFinalizeSection = true;
					finalizeRules = new RuleSet();
					return true;
				}
			} else {
				scanner.pushToken(finalizeToken);
				scanner.pushToken(token);
			}
		} else {
			scanner.pushToken(token);
		}
		return false;
	}

	/**
	 * Save the expression as a rule.  We need to extract an index such
	 * as highway=primary first and then add the rest of the expression as
	 * the condition for it.
	 *
	 * So in other words each condition is dropped into a number of different
	 * baskets based on the first 'tag=value' term.  We then only look
	 * for expressions that are in the correct basket.  For each expression
	 * in a basket we know that the first term is true so we can drop that
	 * from the expression.
	 */
	private void saveRule(TokenScanner scanner, Op op, ActionList actions, GType gt) {
		log.debug("EXP", op, ", type=", gt);

		// check if the type definition is allowed
		if (inFinalizeSection && gt != null)
			throw new SyntaxException(scanner, "Element type definition is not allowed in <finalize> section");

		Iterator<Op> it = arranger.prepareForSave(op);
		while (it.hasNext()) {
			Op prepared = it.next();
			String keystring = arranger.getKeystring(scanner, prepared);
			createAndSaveRule(keystring, prepared, actions, gt);
		}
	}

	private void createAndSaveRule(String keystring, Op expr, ActionList actions, GType gt) {

		Rule rule;
		if (actions.isEmpty()) 
			rule = new ExpressionRule(expr, gt);
		else
			rule = new ActionRule(expr, actions.getList(), gt);

		if (inFinalizeSection)
			finalizeRules.add(keystring, rule, actions.getChangeableTags());
		else
			rules.add(keystring, rule, actions.getChangeableTags());
	}

	
	private int ifCounter;
	/**
	 * 
	 * @return a new tag key unique 
	 */
	public String getNextIfVar (){
		return IF_PREFIX  + ++ifCounter;
	}
	
	public static void main(String[] args) throws FileNotFoundException {
		if (args.length > 0) {
			RuleSet rs = new RuleSet();
			RuleFileReader rr = new RuleFileReader(FeatureKind.POLYLINE,
					LevelInfo.createFromString("0:24 1:20 2:18 3:16 4:14"), rs, false,
					Collections.emptyMap());

			StyleFileLoader loader = new DirectoryFileLoader(
					new File(args[0]).getAbsoluteFile().getParentFile());
			String fname = new File(args[0]).getName();
			rr.load(loader, fname);


			StylePrinter.dumpRuleSet(new Formatter(System.out), "rules", rs);
		} else {
			System.err.println("Usage: RuleFileReader <file>");
		}
	}
}
