package uk.me.parabola.mkgmap.osmstyle.eval;

import java.util.HashSet;
import java.util.Set;
import java.util.Stack;

import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.osmstyle.function.FunctionFactory;
import uk.me.parabola.mkgmap.osmstyle.function.GetTagFunction;
import uk.me.parabola.mkgmap.osmstyle.function.StyleFunction;
import uk.me.parabola.mkgmap.scan.SyntaxException;
import uk.me.parabola.mkgmap.scan.TokenScanner;
import uk.me.parabola.mkgmap.scan.WordInfo;

import static uk.me.parabola.mkgmap.osmstyle.eval.NodeType.*;


/**
 * Read an expression from a style file.
 */
public class ExpressionReader {
	private static final Logger log = Logger.getLogger(ExpressionReader.class);

	private final Stack<Op> stack = new Stack<Op>();
	private final Stack<Op> opStack = new Stack<Op>();
	private final TokenScanner scanner;

	private final Set<String> usedTags = new HashSet<String>();

	public ExpressionReader(TokenScanner scanner) {
		this.scanner = scanner;
	}

	/**
	 * Read the conditions.  They are terminated by a '[' or '{' character
	 * or by end of file.
	 */
	public Op readConditions() {
		while (!scanner.isEndOfFile()) {
			scanner.skipSpace();
			if (scanner.checkToken("[") || scanner.checkToken("{"))
				break;

			WordInfo wordInfo = scanner.nextWordWithInfo();
			if (isOperation(wordInfo))
				saveOp(wordInfo.getText());
			else if (scanner.checkToken("(")) {
				// it is a function
				// this requires a () after the function name
				scanner.validateNext("(");
				scanner.validateNext(")");
				saveFunction(wordInfo.getText());
			} else {
				pushValue(wordInfo.getText());
			}
		}

		// Complete building the tree
		while (!opStack.isEmpty())
			runOp(scanner);

		// The stack should contain one entry which is the complete tree
		if (stack.size() != 1)
			throw new SyntaxException(scanner, "Stack size is "+stack.size());

		assert stack.size() == 1;
		return stack.pop();
	}

	/**
	 * Is this a token representing an operation?
	 * @param token The string to test.
	 * @return True if this looks like an operator.
	 */
	private boolean isOperation(WordInfo token) {
		// A quoted word is not an operator eg: '=' is a string.
		if (token.isQuoted())
			return false;

		// Quick check, operators are 1 or 2 characters long.
		String text = token.getText();
		if (text.length() > 2 || text.isEmpty())
			return false;

		// If first character is an operation character then it is an operator
		// (or a syntax error)
		char first = text.charAt(0);
		String chars = "&|!=~()><";
		return chars.indexOf(first) >= 0;
	}

	/**
	 * Tags used in all the expressions in this file.
	 * @return A set of tag names.
	 */
	public Set<String> getUsedTags() {
		return usedTags;
	}

	/**
	 * An operation is saved on the operation stack.  The tree is built
	 * as operations of different priorities arrive.
	 */
	private void saveOp(String value) {
		log.debug("save op", value);
		if (value.equals("#")) {
			scanner.skipLine();
			return;
		}

		Op op;
		try {
			op = AbstractOp.createOp(value);
			while (!opStack.isEmpty() && opStack.peek().hasHigherPriority(op))
				runOp(scanner);
		} catch (SyntaxException e) {
			throw new SyntaxException(scanner, e.getRawMessage());
		}

		if (op.getType() == CLOSE_PAREN) {
			// Check that there was an opening parenthesis and remove it
			if (opStack.isEmpty() || !opStack.peek().isType(OPEN_PAREN))
				throw new SyntaxException(scanner, "No matching open parenthesis");
			opStack.pop();
		} else {
			opStack.push(op);
		}
	}

	/**
	 * Combine the operation at the top of its stack with its values.
	 * @param scanner The token scanner; used for line numbers.
	 */
	private void runOp(TokenScanner scanner) {
		Op op = opStack.pop();
		log.debug("Running op...", op.getType());

		if (op instanceof BinaryOp) {
			if (stack.size() < 2) {
				throw new SyntaxException(scanner, String.format("Not enough arguments for '%s' operator",
						op.getType().toSymbol()));
			}

			Op arg2 = stack.pop();
			Op arg1 = stack.pop();

			if (arg1.isType(VALUE) && arg2.isType(VALUE))
				arg1 = new GetTagFunction(arg1.getKeyValue());

			BinaryOp binaryOp = (BinaryOp) op;
			binaryOp.setFirst(arg1);
			binaryOp.setSecond(arg2);

			// Deal with the case where you have: a & b=2.  The 'a' is a syntax error in this case.
			if (op.isType(OR) || op.isType(AND)) {
				if (arg1.isType(VALUE))
					throw new SyntaxException(scanner, String.format("Value '%s' is not part of an expression", arg1));

				if (arg2.isType(VALUE))
					throw new SyntaxException(scanner, String.format("Value '%s' is not part of an expression", arg2));
			}

			// The combination foo=* is converted to exists(foo).
			if (op.isType(EQUALS) && arg2.isType(VALUE) && ((ValueOp) arg2).isValue("*")) {
				log.debug("convert to EXISTS");
				op = new ExistsOp();
				op.setFirst(arg1);
			} else if (op.isType(NOT_EQUALS) && arg2.isType(VALUE) && ((ValueOp) arg2).isValue("*")) {
				log.debug("convert to NOT EXISTS");
				op = new NotExistsOp();
				op.setFirst(arg1);
			}
		} else if (!op.isType(OPEN_PAREN)) {
			if (stack.size() < 1)
				throw new SyntaxException(scanner, String.format("Missing argument for %s operator",
						op.getType().toSymbol()));
			op.setFirst(stack.pop());
		}

		Op first = op.getFirst();
		if (first == null)
			throw new SyntaxException(scanner, "Invalid expression");

		if (first.isType(FUNCTION))
			usedTags.add(first.getKeyValue());

		stack.push(op);
	}
	
	private void saveFunction(String functionName) {
		StyleFunction function = FunctionFactory.createFunction(functionName);
		stack.push(function);
	}

	private void pushValue(String value) {
		stack.push(new ValueOp(value));
	}
}
