package uk.me.parabola.mkgmap.osmstyle.eval;

import java.util.HashSet;
import java.util.Set;
import java.util.Stack;

import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.scan.TokenScanner;
import uk.me.parabola.mkgmap.scan.WordInfo;

import static uk.me.parabola.mkgmap.osmstyle.eval.AbstractOp.*;

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
			else
				pushValue(wordInfo.getText());
		}

		// Complete building the tree
		while (!opStack.isEmpty())
			runOp();

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
		// quick check, has to be one or two characters
		if (token.isQuoted())
			return false;

		String text = token.getText();
		if (text.length() > 2 || text.isEmpty())
			return false;

		// quoted strings are never operators
		char first = text.charAt(0);
		if (first == '\'' || first == '"')
			return false;

		// If first character is an operation character then it is an operator
		// (or a syntax error)
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
			op = createOp(value);
			while (!opStack.isEmpty() && opStack.peek().hasHigherPriority(op))
				runOp();
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
	 */
	private void runOp() {
		Op op = opStack.pop();
		log.debug("Running op...", op.getType());

		if (op instanceof BinaryOp) {
			Op arg2 = stack.pop();
			Op arg1 = stack.pop();
			BinaryOp binaryOp = (BinaryOp) op;
			binaryOp.setFirst(arg1);
			binaryOp.setSecond(arg2);

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
			op.setFirst(stack.pop());
		}

		if (op.getFirst().isType(VALUE))
			usedTags.add(op.getFirst().value());

		stack.push(op);
	}

	private void pushValue(String value) {
		stack.push(new ValueOp(value));
	}
}
