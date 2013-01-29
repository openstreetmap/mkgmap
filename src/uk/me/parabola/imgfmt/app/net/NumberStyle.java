package uk.me.parabola.imgfmt.app.net;

/**
 * The number style down one side of a side of a road.
 *
 * @author Steve Ratcliffe
 */
public enum NumberStyle {
	NONE(0), // No numbers.

	EVEN(1) {
		public int round(int val, int direction) {
			if ((val & 1) == 1) return val + direction;
			return val;
		}
	}, // Numbers are even on this side of the road.

	ODD(2) {
		@Override
		public int round(int val, int direction) {
			if ((val & 1) == 0) return val + direction;
			return val;
		}
	},  // Numbers are odd on this side of the road

	BOTH(3), // Both odd and even numbers (can also be used for a range of a single number)
	;

	private final int val;

	NumberStyle(int val) {
		this.val = val;
	}

	public int getVal() {
		return val;
	}

	public int round(int val, int direction) {
		return val;
	}

	public static NumberStyle fromInt(int n) {
		switch (n) {
		case 0: return NONE;
		case 1: return EVEN;
		case 2: return ODD;
		case 3: return BOTH;
		default: return NONE;
		}
	}


	public String toString() {
		return super.toString().substring(0, 1);
	}

	public static NumberStyle fromChar(String string) {
		switch (string.charAt(0)) {
		case 'N': return NONE;
		case 'E': return EVEN;
		case 'O': return ODD;
		case 'B': return BOTH;
		case '0':
			System.err.println("zero instead of capital O in number spec");
			return ODD;
		default: return NONE;
		}
	}
}
