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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import uk.me.parabola.mkgmap.scan.SyntaxException;
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

	private final Set<String> usedTags = new HashSet<String>();

	public ActionReader(TokenScanner scanner) {
		this.scanner = scanner;
	}

	public ActionList readActions() {
		List<Action> actions = new ArrayList<Action>();
		Set<String> changeableTags = new HashSet<String>();
		scanner.skipSpace();
		if (!scanner.checkToken("{"))
			return new ActionList(actions, changeableTags);

		scanner.nextToken();
		while (inAction()) {
			Token tok = scanner.nextToken();
			if (tok.isValue(";"))
				continue;

			String cmd = tok.getValue();
			if ("set".equals(cmd)) {
				actions.add(readTagValue(true, changeableTags));
			} else if ("add".equals(cmd)) {
				actions.add(readTagValue(false, changeableTags));
			} else if ("apply".equals(cmd)) {
				actions.add(readAllCmd(false));
			} else if ("apply_once".equals(cmd)) {
				actions.add(readAllCmd(true));
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
				// The 'to' tag may come into existence and you may attempt
				// to match on it, therefore we have to save it.
				changeableTags.add(to);
				// the from tag must not be dropped from the input
				usedTags.add(from);
			} else if ("echo".equals(cmd)) {
				String str = scanner.nextWord();
				actions.add(new EchoAction(str));
			} else {
				throw new SyntaxException(scanner, "Unrecognised command '" + cmd + '\'');
			}

			scanner.skipSpace();
		}
		if (scanner.checkToken("}"))
			scanner.nextToken();
		scanner.skipSpace();

		return new ActionList(actions, changeableTags);
	}

	private Action readAllCmd(boolean once) {
		String role = null;
		if (scanner.checkToken("role")) {
			scanner.nextToken();
			String eq = scanner.nextValue();
			if (!"=".equals(eq))
				throw new SyntaxException(scanner, "Expecting '=' after role keyword");
			role = scanner.nextWord();
		}
		SubAction subAction = new SubAction(role, once);

		List<Action> actionList = readActions().getList();
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
		usedTags.addAll(nameAct.getUsedTags());
		return nameAct;
	}

	/**
	 * Read a tag/value pair.  If the action is executed then the tag name
	 * will possibly be modified or set.  If that is the case then we will
	 * have to make sure that we are executing rules for that tag.
	 *
	 * @param modify If true the tag value can be modified.  If it is not set
	 * then a tag can only be added; if it already exists, then it will not
	 * be changed.
	 * @param changeableTags Tags that could be changed by the action.  This is
	 * an output parameter, any such tags should be added to this set.
	 * @return The new add tag action.
	 */
	private AddTagAction readTagValue(boolean modify, Set<String> changeableTags) {
		String key = scanner.nextWord();
		if (!scanner.checkToken("="))
			throw new SyntaxException(scanner, "Expecting tag=value");
		scanner.nextToken();

		AddTagAction action = null;
		while (inActionCmd()) {

			String val = scanner.nextWord();
			if (action == null)
				action = new AddTagAction(key, val, modify);
			else
				action.add(val);
			// Save the tag as one that is potentially set during the operation.
			// If the value contains a variable, then we do not know what the
			// value will be.  Otherwise save the full tag=value
			if (val.contains("$")) {
				changeableTags.add(key);
			} else {
				changeableTags.add(key + "=" + val);
			}
			if (scanner.checkToken("|"))
				scanner.nextToken();
		}
		if (action != null)
			usedTags.addAll(action.getUsedTags());
		return action;
	}

	private boolean inActionCmd() {
		boolean end = scanner.checkToken(";");
		return inAction() && !end;
	}

	private boolean inAction() {
		return !scanner.isEndOfFile() && !scanner.checkToken("}");
	}

	public Set<String> getUsedTags() {
		return usedTags;
	}
}
