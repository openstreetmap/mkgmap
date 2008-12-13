package uk.me.parabola.mkgmap.osmstyle.eval;

import java.util.Stack;

import uk.me.parabola.log.Logger;
import static uk.me.parabola.mkgmap.osmstyle.eval.Op.CLOSE_PAREN;
import static uk.me.parabola.mkgmap.osmstyle.eval.Op.EQUALS;
import static uk.me.parabola.mkgmap.osmstyle.eval.Op.NOT_EQUALS;
import static uk.me.parabola.mkgmap.osmstyle.eval.Op.OPEN_PAREN;
import static uk.me.parabola.mkgmap.osmstyle.eval.Op.VALUE;
import uk.me.parabola.mkgmap.scan.TokenScanner;

/**
 * Read an expression from a style file.
 */
public class ExpressionReader {
	private static final Logger log = Logger.getLogger(ExpressionReader.class);

	private final Stack<Op> stack = new Stack<Op>();
	private final Stack<Op> opStack = new Stack<Op>();
	private final TokenScanner scanner;

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

			String val = scanner.nextWord();
			if ("&|!=~()><".contains(val)) 
				saveOp(val);
			else
				pushValue(val);
		}

		// Complete building the tree
		while (!opStack.isEmpty())
			runOp();

		// The stack should contain one entry which is the complete tree
		assert stack.size() == 1;
		return stack.pop();
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
			op = Op.createOp(value);
			while (!opStack.isEmpty() && opStack.peek().hasHigherPriority(op))
				runOp();
		} catch (SyntaxException e) {
			throw new SyntaxException(scanner, e.getRawMessage());
		}

		if (op.getType() == CLOSE_PAREN) {
			// Check that there was an opeing paren and remove it
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
		}
		stack.push(op);
	}

	private void pushValue(String value) {
		stack.push(new ValueOp(value));
	}
}
