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

	private Stack<Op> stack;
	private Stack<Op> opStack;

	public ExpressionReader(Stack<Op> stack, Stack<Op> opStack) {
		this.stack = stack;
		this.opStack = opStack;
	}


	public Op readConditions(TokenScanner ts) {
		while (!ts.isEndOfFile()) {
			if (ts.checkToken(TokType.SYMBOL, "{"))
				break;
			Token tok = ts.nextToken();

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
		Op op = Op.createOp(value);
		while (!opStack.isEmpty() && op.hasHigherPriority(opStack.peek())) {
			runOp();
		}
		opStack.push(op);
	}

	void runOp() {
		log.debug("Running op...");
		Op op = opStack.pop();
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
