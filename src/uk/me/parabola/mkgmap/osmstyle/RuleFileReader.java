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
import uk.me.parabola.mkgmap.osmstyle.eval.AndOp;
import uk.me.parabola.mkgmap.osmstyle.eval.BinaryOp;
import uk.me.parabola.mkgmap.osmstyle.eval.EqualsOp;
import uk.me.parabola.mkgmap.osmstyle.eval.ExistsOp;
import uk.me.parabola.mkgmap.osmstyle.eval.ExpressionReader;
import uk.me.parabola.mkgmap.osmstyle.eval.LinkedOp;
import uk.me.parabola.mkgmap.osmstyle.eval.NodeType;
import uk.me.parabola.mkgmap.osmstyle.eval.NotOp;
import uk.me.parabola.mkgmap.osmstyle.eval.Op;
import uk.me.parabola.mkgmap.osmstyle.eval.OrOp;
import uk.me.parabola.mkgmap.osmstyle.eval.ValueOp;
import uk.me.parabola.mkgmap.osmstyle.function.GetTagFunction;
import uk.me.parabola.mkgmap.osmstyle.function.StyleFunction;
import uk.me.parabola.mkgmap.reader.osm.FeatureKind;
import uk.me.parabola.mkgmap.reader.osm.GType;
import uk.me.parabola.mkgmap.reader.osm.Rule;
import uk.me.parabola.mkgmap.scan.SyntaxException;
import uk.me.parabola.mkgmap.scan.TokType;
import uk.me.parabola.mkgmap.scan.Token;
import uk.me.parabola.mkgmap.scan.TokenScanner;

import static uk.me.parabola.mkgmap.osmstyle.eval.NodeType.*;

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

	private Deque<Op[]> ifStack = new LinkedList<>();

	private boolean inFinalizeSection;
	
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
			ActionList actionList = actionReader.readActions();
			checkIfStack(actionList);
			
			List<GType> types = new ArrayList<>();
			while (scanner.checkToken("[")) {
				GType type = typeReader.readType(scanner, performChecks, overlays);
				types.add(type);
				scanner.skipSpace();
			};
			
			// If there is an action list, then we don't need a type
			if (types.isEmpty() && (actionList.isEmpty()))
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
	 * @param actionList
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
				|| (next.getType() == TokType.SYMBOL && (next.isValue("'") || next.isValue("\""))))
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
		
		//System.out.println("From: " + op);
		Op op2 = rearrangeExpression(op);
		//System.out.println("TO  : " + op2);

		if (op2 instanceof BinaryOp) {
			optimiseAndSaveBinaryOp(scanner, (BinaryOp) op2, actions, gt);
		} else {
			optimiseAndSaveOtherOp(scanner, op2, actions, gt);
 		}
	}

	/**
	 * Rearrange the expression so that it is solvable, that is it starts with
	 * an EQUALS or an EXISTS.
	 * @param op The expression to be rearranged.
	 * @return An equivalent expression re-arranged so that it starts with an
	 * indexable term. If that is not possible then the original expression is
	 * returned.
	 */
	private static Op rearrangeExpression(Op op) {
		if (isFinished(op))
			return op;

		if (op.getFirst().isType(OR))
			op = distribute(op.getFirst(), op.getSecond());

		if (op.isType(AND)) {
			// Recursively re-arrange the child nodes
			rearrangeExpression(op.getFirst());
			rearrangeExpression(op.getSecond());

			swapForSelectivity((BinaryOp) op);

			// Rearrange ((A&B)&C) to (A&(B&C)).
			while (op.getFirst().isType(AND)) {
				Op aAndB = op.getFirst();
				Op c = op.getSecond();
				op.setFirst(aAndB.getFirst()); // A

				aAndB.setFirst(aAndB.getSecond());
				((BinaryOp) aAndB).setSecond(c);  // a-and-b is now b-and-c
				((BinaryOp) op).setSecond(aAndB);
			}

			Op op1 = op.getFirst();
			Op op2 = op.getSecond();

			// If the first term is an EQUALS or EXISTS then this subtree is
			// already solved and we need to do no more.
			if (isSolved(op1)) {
				return rearrangeAnd((BinaryOp) op, op1, op2);
			} else if (isSolved(op2)) {
				return rearrangeAnd((BinaryOp) op, op2, op1);
			}
		}

		return op;
	}

	/**
	 * Swap the terms so that the most selective or fastest term to calculate
	 * is first.
	 * @param op A AND operation.
	 */
	private static void swapForSelectivity(BinaryOp op) {
		Op first = op.getFirst();
		int sel1 = selectivity(first);
		Op second = op.getSecond();
		int sel2 = selectivity(second);
		if (sel1 > sel2) {
			op.setFirst(second);
			op.setSecond(first);
		}
	}

	/**
	 * Rearrange an AND expression so that it can be executed with indexable
	 * terms at the front.
	 * @param top This will be an AndOp.
	 * @param op1 This is a child of top that is guaranteed to be
	 * solved already.
	 * @param op2 This expression is the other child of top.
	 * @return A re-arranged expression with an indexable term at the beginning
	 * or several such expressions ORed together.
	 */
	private static BinaryOp rearrangeAnd(BinaryOp top, Op op1, Op op2) {
		if (isIndexable(op1)) {
			top.setFirst(op1);
			top.setSecond(op2);
			return top;
		} else if (op1.isType(AND)) {
			// The first term is AND.
			// If its first term is indexable (EQUALS or EXIST) then we
			// re-arrange the tree so that that term is first.
			Op first = op1.getFirst();
			if (isIndexable(first)) {
				top.setFirst(first);
				op1.setFirst(op2);
				swapForSelectivity((AndOp) op1);
				top.setSecond(op1);
				return top;
			}
		} else if (op1.isType(OR)) {
			// Transform ((first | second) & topSecond)
			// into (first & topSecond) | (second & topSecond)

			return distribute(op1, top.getSecond());
		} else {
			// This shouldn't happen
			throw new SyntaxException("X3:" + op1.getType());
		}
		return top;
	}

	private static OrOp distribute(Op op1, Op topSecond) {
		Op first = op1.getFirst();
		OrOp orOp = new OrOp();

		BinaryOp and1 = new AndOp();
		and1.setFirst(first);
		and1.setSecond(topSecond);

		BinaryOp and2 = new AndOp();
		Op second = rearrangeExpression(op1.getSecond());
		if (second.isType(OR)) {
			and2 = distribute(second, topSecond);
		} else {
			and2.setFirst(second);
			and2.setSecond(topSecond);
		}
		orOp.setFirst(and1);
		orOp.setSecond(and2);

		return orOp;
	}

	/**
	 * True if this operation can be indexed.  It is a plain equality or
	 * Exists operation.
	 */
	private static boolean isIndexable(Op op) {
		return (op.isType(EQUALS)
				&& ((ValueOp) op.getFirst()).isIndexable() && op.getSecond().isType(VALUE))
				|| (op.isType(EXISTS) && ((ValueOp) op.getFirst()).isIndexable());
	}

	/**
	 * True if this expression is 'solved'.  This means that the first term
	 * is indexable or it is indexable itself.
	 */
	private static boolean isSolved(Op op) {
		if (op.isType(NOT))
			return false;
		return isIndexable(op) || isIndexable(op.getFirst());
	}

	/**
	 * True if there is nothing more that we can do to rearrange this expression.
	 * It is either solved or it cannot be solved.
	 */
	private static boolean isFinished(Op op) {
		// If we can improve the ordering then we are not done just yet
		if (op.isType(AND) && selectivity(op.getFirst()) > selectivity(op.getSecond()))
			return false;

		if (isSolved(op))
			return true;

		NodeType type = op.getType();
		switch (type) {
		case AND:
			return false;
		case OR:
			return false;
		default:
			return true;
		}
	}

	/**
	 * Get a value for how selective this operation is.  We try to bring
	 * EQUALS to the front followed by EXISTS.  Without knowing tag
	 * frequency you can only guess at what the most selective operations
	 * are, so all we do is arrange EQUALS - EXISTS - everything else.
	 * Note that you must have an EQUALS or EXISTS first, so you can't
	 * bring anything else earlier than them.
	 *
	 * @return An integer, lower values mean the operation should be earlier
	 * in the expression than operations with higher values.
	 */
	private static int selectivity(Op op) {
		switch (op.getType()) {
		case EQUALS:
			return 0;
		case EXISTS:
			return 10;

		case AND:
			return Math.max(selectivity(op.getFirst()), selectivity(op.getSecond()));

		case OR:
			return Math.max(selectivity(op.getFirst()), selectivity(op.getSecond()));

		default:
			return 1000;
		}
	}

	private void optimiseAndSaveOtherOp(TokenScanner scanner, Op op, ActionList actions, GType gt) {
		if (op.isType(EXISTS)) {
			// The lookup key for the exists operation is 'tag=*'
			createAndSaveRule(op.getFirst().getKeyValue() + "=*", op, actions, gt);
		} else {
			throw new SyntaxException(scanner, "Cannot start expression with: " + op);
		}
	}

	/**
	 * Optimise the expression tree, extract the primary key and
	 * save it as a rule.
	 * @param scanner The token scanner, used for error message file/line numbers.
	 * @param op a binary expression
	 * @param actions list of actions to execute on match
	 * @param gt the Garmin type of the element
	 */
	private void optimiseAndSaveBinaryOp(TokenScanner scanner, BinaryOp op, ActionList actions, GType gt) {
		Op first = op.getFirst();
		Op second = op.getSecond();

		log.debug("binop", op.getType(), first.getType());

		/*
		 * We allow the following cases:
		 * An EQUALS at the top.
		 * An AND at the top level.
		 * An OR at the top level.
		 */
		String keystring;
		if (op.isType(EQUALS) && (first.isType(FUNCTION) && second.isType(VALUE))) {
			keystring = first.getKeyValue() + "=" + second.getKeyValue();
		} else if (op.isType(AND)) {
			if (first.isType(EQUALS)) {
				keystring = first.getFirst().getKeyValue() + "=" + first.getSecond().getKeyValue();
			} else if (first.isType(EXISTS)) {
				keystring = first.getFirst().getKeyValue() + "=*";
			} else if (first.isType(NOT_EXISTS)) {
				throw new SyntaxException(scanner, "Cannot start rule with tag!=*");
			} else if (first.getFirst() != null &&
					first.getFirst().getType() == FUNCTION
					&& ((StyleFunction) first.getFirst()).isIndexable())
			{
				// Extract the initial key and add an exists clause at the beginning
				AndOp aop = combineWithExists(new ValueOp(first.getFirst().getKeyValue()), op);
				optimiseAndSaveBinaryOp(scanner, aop, actions, gt);
				return;
			} else {
				throw new SyntaxException(scanner, "Invalid rule expression: " + op);
			}
		} else if (op.isType(OR)) {
			LinkedOp op1 = LinkedOp.create(first, true);
			saveRule(scanner, op1, actions, gt);

			saveRestOfOr(scanner, actions, gt, second, op1);
			return;
		} else {
			if (!first.isType(FUNCTION) || !((StyleFunction) first).isIndexable())
				throw new SyntaxException("Cannot use " + first + " without tag matches");

			// We can make every other binary op work by converting to AND(EXISTS, op), as long as it does
			// not involve an un-indexable function.
			AndOp andOp = combineWithExists(first, op);
			optimiseAndSaveBinaryOp(scanner, andOp, actions, gt);
			return;
		}

		createAndSaveRule(keystring, op, actions, gt);
	}

	private AndOp combineWithExists(Op first, BinaryOp op) {
		Op existsOp = new ExistsOp();
		existsOp.setFirst(first);

		AndOp andOp = new AndOp();
		andOp.setFirst(existsOp);
		andOp.setSecond(op);
		return andOp;
	}

	private void saveRestOfOr(TokenScanner scanner, ActionList actions, GType gt, Op second, LinkedOp op1) {
		if (second.isType(OR)) {
			LinkedOp nl = LinkedOp.create(second.getFirst(), false);
			op1.setLink(nl);
			saveRule(scanner, nl, actions, gt);
			saveRestOfOr(scanner, actions, gt, second.getSecond(), op1);
		} else {
			LinkedOp op2 = LinkedOp.create(second, false);
			op1.setLink(op2);
			saveRule(scanner, op2, actions, gt);
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
		return RuleSet.IF_PREFIX  + ++ifCounter;
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
