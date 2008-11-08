package uk.me.parabola.mkgmap.osmstyle.eval;

import java.util.Stack;

import uk.me.parabola.log.Logger;
import static uk.me.parabola.mkgmap.osmstyle.eval.Op.*;
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
				if (tok.getValue().equals("*"))
					pushValue(tok.getValue());
				else
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

		if (op.getType() == CLOSE_PAREN) {
			// Check that there was an opeing paren and remove it
			if (opStack.isEmpty() || !opStack.peek().isType(OPEN_PAREN))
				throw new SyntaxException(scanner, "No matching open parenthesis");
			opStack.pop();
		} else {
			opStack.push(op);
		}
	}

	void runOp() {
		Op op = opStack.pop();
		log.debug("Running op...", op.getType());

		if (op instanceof BinaryOp) {
			Op arg2 = stack.pop();
			Op arg1 = stack.pop();
			BinaryOp binaryOp = (BinaryOp) op;
			binaryOp.setFirst(arg1);
			binaryOp.setSecond(arg2);
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

	void pushValue(String value) {
		log.debug("push value", value);
		stack.push(new ValueOp(value));
	}
}
