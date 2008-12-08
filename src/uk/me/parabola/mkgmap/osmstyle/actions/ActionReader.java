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
 * Create date: 16-Nov-2008
 */
package uk.me.parabola.mkgmap.osmstyle.actions;

import java.util.ArrayList;
import java.util.List;

import uk.me.parabola.mkgmap.osmstyle.eval.SyntaxException;
import uk.me.parabola.mkgmap.scan.Token;
import uk.me.parabola.mkgmap.scan.TokenScanner;

/**
 * Read an action block.  This is contained within braces and contains
 * commands to change tags etc.
 * 
 * @author Steve Ratcliffe
 */
public class ActionReader {
	private final TokenScanner scanner;

	public ActionReader(TokenScanner scanner) {
		this.scanner = scanner;
	}

	public List<Action> readActions() {
		List<Action> actions = new ArrayList<Action>();
		scanner.skipSpace();
		if (!scanner.checkToken("{"))
			return actions;

		scanner.nextToken();
		
		while (inAction()) {
			Token tok = scanner.nextToken();
			if (tok.isValue(";"))
				continue;

			String cmd = tok.getValue();
			if ("set".equals(cmd)) {
				actions.add(readTagValue(true));
			} else if ("add".equals(cmd)) {
				actions.add(readTagValue(false));
			} else if ("apply".equals(cmd)) {
				actions.add(readAllCmd());
			} else if ("name".equals(cmd)) {
				actions.add(readNameCmd());
			} else if ("delete".equals(cmd)) {
				String tag = scanner.nextWord();
				actions.add(new DeleteAction(tag));
			} else if ("rename".equals(cmd)) {
				String from = scanner.nextWord();
				String to = scanner.nextWord();
				Action act = new RenameAction(from, to);
				actions.add(act);
			} else {
				throw new SyntaxException(scanner, "Unrecognised command '" + cmd + '\'');
			}

			scanner.skipSpace();
		}
		if (scanner.checkToken("}"))
			scanner.nextToken();
		scanner.skipSpace();
		return actions;
	}

	private Action readAllCmd() {
		String role = null;
		if (scanner.checkToken("role")) {
			scanner.nextToken();
			String eq = scanner.nextValue();
			if (!"=".equals(eq))
				throw new SyntaxException(scanner, "Expecting '=' after role keyword");
			role = scanner.nextWord();
		}
		SubAction subAction = new SubAction(role);

		List<Action> actionList = readActions();
		for (Action a : actionList)
			subAction.add(a);
		
		return subAction;
	}

	/**
	 * A name command has a number of alternatives separated by '|' characters.
	 */
	private Action readNameCmd() {
		NameAction nameAct = new NameAction();
		while (inActionCmd()) {
			if (scanner.checkToken("|")) {
				scanner.nextToken();
				continue;
			}
			String val = scanner.nextWord();
			nameAct.add(val);
		}
		return nameAct;
	}

	private AddTagAction readTagValue(boolean modify) {
		String key = scanner.nextWord();
		if (!scanner.checkToken("="))
			throw new SyntaxException(scanner, "Expecting tag=value");
		scanner.nextToken();
		String val = scanner.nextWord();

		AddTagAction action = new AddTagAction(key, val, modify);
		return action;
	}

	private boolean inActionCmd() {
		boolean end = scanner.checkToken(";");
		return inAction() && !end;
	}

	private boolean inAction() {
		return !scanner.isEndOfFile() && !scanner.checkToken("}");
	}
}
