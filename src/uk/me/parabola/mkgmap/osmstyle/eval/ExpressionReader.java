package uk.me.parabola.mkgmap.osmstyle.eval;

import java.util.Stack;

import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.scan.TokType;
import uk.me.parabola.mkgmap.scan.Token;
import uk.me.parabola.mkgmap.scan.TokenScanner;

/**
 * Read an expression from a style file.
 */
public class ExpressionReader {
	private static final Logger log = Logger.getLogger(ExpressionReader.class);

	private Stack<Op> stack = new Stack<Op>();
	private Stack<Op> opStack = new Stack<Op>();
	private TokenScanner scanner;

	public ExpressionReader(TokenScanner scanner) {
		this.scanner = scanner;
	}

	public Op readConditions() {
		while (!scanner.isEndOfFile()) {
			if (scanner.checkToken(TokType.SYMBOL, "["))
				break;
			Token tok = scanner.nextToken();

			log.debug("Token", tok.getValue());

			switch (tok.getType()) {
			case EOF:
				break;
			case EOL:
				break;
			case SPACE:
				break;
			case SYMBOL:
				saveOp(tok.getValue());
				break;
			case TEXT:
				pushValue(tok.getValue());
				break;
			}
		}

		while (!opStack.isEmpty())
			runOp();

		return stack.pop();
	}

	void saveOp(String value) {
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

		if (op.getType() == Op.CLOSE_PAREN) {
			// Check that there was an opeing paren and remove it
			if (opStack.isEmpty() || opStack.peek().getType() != Op.OPEN_PAREN)
				throw new SyntaxException(scanner, "No matching open parenthesis");
			opStack.pop();
		} else {
			opStack.push(op);
		}
	}

	void runOp() {
		Op op = opStack.pop();
		log.debug("Running op...", op.getType());
		Op arg2 = stack.pop();
		Op arg1 = stack.pop();

		//System.out.printf("%s(%s, %s)\n", op, arg1, arg2);
		if (op instanceof BinaryOp) {
			BinaryOp binaryOp = (BinaryOp) op;
			binaryOp.setFirst(arg1);
			binaryOp.setSecond(arg2);
			stack.push(binaryOp);
		}
	}

	void pushValue(String value) {
		log.debug("push value", value);
		stack.push(new ValueOp(value));
	}
}
