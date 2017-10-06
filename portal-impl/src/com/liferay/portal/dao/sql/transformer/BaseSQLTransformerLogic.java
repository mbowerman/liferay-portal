/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.portal.dao.sql.transformer;

import com.liferay.portal.kernel.dao.db.DB;
import com.liferay.portal.kernel.util.CharPool;
import com.liferay.portal.kernel.util.ObjectValuePair;
import com.liferay.portal.kernel.util.StringBundler;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.util.StringUtil;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Manuel de la Peña
 * @author Brian Wing Shun Chan
 */
public abstract class BaseSQLTransformerLogic implements SQLTransformerLogic {

	public BaseSQLTransformerLogic(DB db) {
		_db = db;
	}

	@Override
	public Function<String, String>[] getFunctions() {
		return _functions;
	}

	protected Function<String, String> getBitwiseCheckFunction() {
		Pattern pattern = getBitwiseCheckPattern();

		return (String sql) -> replaceBitwiseCheck(pattern.matcher(sql));
	}

	protected Pattern getBitwiseCheckPattern() {
		return Pattern.compile("BITAND\\(\\s*(.+?)\\s*,\\s*(.+?)\\s*\\)");
	}

	protected Function<String, String> getBooleanFunction() {
		return (String sql) -> StringUtil.replace(
			sql, new String[] {"[$FALSE$]", "[$TRUE$]"},
			new String[] {_db.getTemplateFalse(), _db.getTemplateTrue()});
	}

	protected Function<String, String> getCastClobTextFunction() {
		Pattern pattern = getCastClobTextPattern();

		return (String sql) -> replaceCastClobText(pattern.matcher(sql));
	}

	protected Pattern getCastClobTextPattern() {
		return Pattern.compile(
			"CAST_CLOB_TEXT\\((.+?)\\)", Pattern.CASE_INSENSITIVE);
	}

	protected Function<String, String> getCastLongFunction() {
		Pattern pattern = getCastLongPattern();

		return (String sql) -> replaceCastLong(pattern.matcher(sql));
	}

	protected Pattern getCastLongPattern() {
		return Pattern.compile(
			"CAST_LONG\\((.+?)\\)", Pattern.CASE_INSENSITIVE);
	}

	protected Function<String, String> getCastTextFunction() {
		Pattern pattern = getCastTextPattern();

		return (String sql) -> replaceCastText(pattern.matcher(sql));
	}

	protected Pattern getCastTextPattern() {
		return Pattern.compile(
			"CAST_TEXT\\((.+?)\\)", Pattern.CASE_INSENSITIVE);
	}

	protected Function<String, String> getConcatFunction() {
		return _getConcatFunction(getReplacementConcatSQLFunction());
	}

	protected Function<String, String> getInstrFunction() {
		Pattern pattern = getInstrPattern();

		return (String sql) -> replaceInstr(pattern.matcher(sql));
	}

	protected Pattern getInstrPattern() {
		return Pattern.compile(
			"INSTR\\(\\s*(.+?)\\s*,\\s*(.+?)\\s*\\)", Pattern.CASE_INSENSITIVE);
	}

	protected Function<String, String> getIntegerDivisionFunction() {
		Pattern pattern = getIntegerDivisionPattern();

		return (String sql) -> replaceIntegerDivision(pattern.matcher(sql));
	}

	protected Pattern getIntegerDivisionPattern() {
		return Pattern.compile(
			"INTEGER_DIV\\(\\s*(.+?)\\s*,\\s*(.+?)\\s*\\)",
			Pattern.CASE_INSENSITIVE);
	}

	protected Function<String, String> getModFunction() {
		Pattern pattern = getModPattern();

		return (String sql) -> replaceMod(pattern.matcher(sql));
	}

	protected Pattern getModPattern() {
		return Pattern.compile(
			"MOD\\(\\s*(.+?)\\s*,\\s*(.+?)\\s*\\)", Pattern.CASE_INSENSITIVE);
	}

	protected Function<String, String> getNullDateFunction() {
		return (String sql) -> StringUtil.replace(sql, "[$NULL_DATE$]", "NULL");
	}

	protected Function<LinkedList<String>, String>
		getReplacementConcatSQLFunction() {

		return (LinkedList<String> expressions) -> {
			int numExpressions = expressions.size();

			StringBundler sb = new StringBundler(4 * numExpressions - 3);

			while (expressions.size() > 1) {
				sb.append(CONCAT_OPEN);
				sb.append(expressions.poll());
				sb.append(StringPool.COMMA_AND_SPACE);
			}

			sb.append(expressions.poll());

			for (int i = 1; i < numExpressions; i++) {
				sb.append(StringPool.CLOSE_PARENTHESIS);
			}

			return sb.toString();
		};
	}

	protected Function<String, String> getSubstrFunction() {
		Pattern pattern = getSubstrPattern();

		return (String sql) -> replaceSubstr(pattern.matcher(sql));
	}

	protected Pattern getSubstrPattern() {
		return Pattern.compile(
			"SUBSTR\\(\\s*(.+?)\\s*,\\s*(.+?)\\s*,\\s*(.+?)\\s*\\)",
			Pattern.CASE_INSENSITIVE);
	}

	protected String replaceBitwiseCheck(Matcher matcher) {
		return matcher.replaceAll("($1 & $2)");
	}

	protected String replaceCastClobText(Matcher matcher) {
		return replaceCastText(matcher);
	}

	protected String replaceCastLong(Matcher matcher) {
		return matcher.replaceAll("$1");
	}

	protected String replaceCastText(Matcher matcher) {
		return matcher.replaceAll("$1");
	}

	protected String replaceInstr(Matcher matcher) {
		return matcher.replaceAll("CHARINDEX($2, $1)");
	}

	protected String replaceIntegerDivision(Matcher matcher) {
		return matcher.replaceAll("$1 / $2");
	}

	protected String replaceMod(Matcher matcher) {
		return matcher.replaceAll("$1 % $2");
	}

	protected String replaceSubstr(Matcher matcher) {
		return matcher.replaceAll("SUBSTRING($1, $2, $3)");
	}

	protected void setFunctions(Function... functions) {
		_functions = functions;
	}

	protected static final String CONCAT_OPEN = "CONCAT(";

	private ObjectValuePair<String, LinkedList<String>> _getConcatExpressions(
		String sql, int beginIndex) {

		LinkedList<String> concatExpressions = new LinkedList<>();

		int expressionBeginIndex = beginIndex;

		int expressionEndIndex = sql.length();

		while (expressionBeginIndex < sql.length()) {
			expressionEndIndex = _getEndOfExpression(sql, expressionBeginIndex);

			concatExpressions.add(
				sql.substring(expressionBeginIndex, expressionEndIndex));

			if (sql.charAt(expressionEndIndex) == CharPool.CLOSE_PARENTHESIS) {
				break;
			}

			expressionBeginIndex = expressionEndIndex + 1;

			while (expressionBeginIndex < sql.length()) {
				if (!Character.isWhitespace(sql.charAt(expressionBeginIndex))) {
					break;
				}

				expressionBeginIndex++;
			}
		}

		return new ObjectValuePair<>(
			CONCAT_OPEN + sql.substring(beginIndex, expressionEndIndex) +
				StringPool.CLOSE_PARENTHESIS,
			concatExpressions);
	}

	private Function<String, String> _getConcatFunction(
		Function<LinkedList<String>, String> getReplacementSQLFunction) {

		return (String sql) -> {
			int concatIndex = sql.indexOf(CONCAT_OPEN, 0);

			List<String[]> replacementSQLs = new LinkedList<>();

			while (concatIndex >= 0) {
				concatIndex += CONCAT_OPEN.length();

				ObjectValuePair<String, LinkedList<String>> objectValuePair =
					_getConcatExpressions(sql, concatIndex);

				LinkedList<String> expressions = objectValuePair.getValue();

				concatIndex = sql.indexOf(CONCAT_OPEN, concatIndex);

				if (expressions.size() < 2) {
					continue;
				}

				replacementSQLs.add(
					new String[]
						{
							objectValuePair.getKey(),
							getReplacementSQLFunction.apply(expressions)
						});
			}

			for (String[] replacementSQL : replacementSQLs) {
				sql = StringUtil.replaceFirst(
					sql, replacementSQL[0], replacementSQL[1]);
			}

			return sql;
		};
	}

	private int _getEndOfExpression(String sql, int index) {
		int state = _STATE_NORMAL;

		while (index < sql.length()) {
			switch (state) {
				case _STATE_NORMAL:
					if ((sql.charAt(index) == CharPool.COMMA) ||
						(sql.charAt(index) == CharPool.CLOSE_PARENTHESIS)) {

						return index;
					}
					else if (sql.charAt(index) == CharPool.OPEN_PARENTHESIS) {
						state = _STATE_PARENTHETICAL;
					}
					else if (sql.charAt(index) == CharPool.APOSTROPHE) {
						state = _STATE_QUOTE;
					}

					break;
				case _STATE_PARENTHETICAL:
					if (sql.charAt(index) == CharPool.APOSTROPHE) {
						state = _STATE_PARENTHETICAL_QUOTE;
					}
					else if (sql.charAt(index) == CharPool.CLOSE_PARENTHESIS) {
						state = _STATE_NORMAL;
					}

					break;
				case _STATE_QUOTE:
					index = sql.indexOf(CharPool.APOSTROPHE, index + 1);

					if (!_isEscaped(sql, index)) {
						state = _STATE_NORMAL;
					}

					break;
				case _STATE_PARENTHETICAL_QUOTE:
					index = sql.indexOf(CharPool.APOSTROPHE, index + 1);

					if (!_isEscaped(sql, index)) {
						state = _STATE_PARENTHETICAL;
					}

					break;
				default:
			}

			index++;
		}

		return index;
	}

	private boolean _isEscaped(String s, int index) {
		int numBackSlashes = 0;

		for (int i = index - 1; i >= 0; i--) {
			if (s.charAt(i) == CharPool.BACK_SLASH) {
				numBackSlashes++;
			}
			else {
				break;
			}
		}

		if ((numBackSlashes % 2) == 1) {
			return true;
		}

		return false;
	}

	private static final int _STATE_NORMAL = 0;

	private static final int _STATE_PARENTHETICAL = 2;

	private static final int _STATE_PARENTHETICAL_QUOTE = 3;

	private static final int _STATE_QUOTE = 1;

	private final DB _db;
	private Function[] _functions;

}