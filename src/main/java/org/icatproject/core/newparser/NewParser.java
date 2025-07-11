package org.icatproject.core.newparser;

import java.util.Map.Entry;

import org.icatproject.core.entity.EntityBaseBean;
import org.icatproject.core.newparser.Token.Type;
import org.icatproject.core.newparser.model.SelectClause;
import org.icatproject.core.newparser.model.SelectQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NewParser {
	private static Logger logger = LoggerFactory.getLogger(NewParser.class);

	public SelectQuery parseSelectQuery(Input input) throws ParserException {
		selectClause = new SelectClause(input);
		if (selectClause.isCount()) {
			noAuthzResult.add(0L);
		}

		fromClause = new FromClause(input, selectClause.getIdPaths());
		for (Entry<String, String> entry : fromClause.getReplaceMap().entrySet()) {
			selectClause.replace(entry.getKey(), entry.getValue());
		}
		Token t = input.peek(0);
		if (t != null && t.getType() == Token.Type.WHERE) {
			whereClause = new WhereClause(input);
			t = input.peek(0);
		}

		if (t != null && (t.getType() == Token.Type.GROUP || t.getType() == Token.Type.HAVING
				|| t.getType() == Token.Type.ORDER)) {
			otherJpqlClauses = new OtherJpqlClauses(input);
			t = input.peek(0);
		}
		if (t != null && t.getType() == Token.Type.INCLUDE) {
			if (selectClause.getIdPaths().size() > 1) {
				throw new ParserException("INCLUDE is only valid for one quantity in the SELECT clause");
			} else {
				String idv = selectClause.getIdPaths().iterator().next();
				Class<? extends EntityBaseBean> bean = fromClause.getAuthzMap().get(idv + ".id");
				includeClause = new IncludeClause(bean, input, idv.toUpperCase(), gateKeeper);
				t = input.peek(0);
			}

		}
		if (t != null && t.getType() == Token.Type.LIMIT) {
			limitClause = new LimitClause(input);
			t = input.peek(0);
		}

		if (includeClause == null && t != null && t.getType() == Token.Type.INCLUDE) {
			if (selectClause.getIdPaths().size() > 1) {
				throw new ParserException("INCLUDE is only valid for one quantity in the SELECT clause");
			} else {
				String idv = selectClause.getIdPaths().iterator().next();
				Class<? extends EntityBaseBean> bean = fromClause.getAuthzMap().get(idv + ".id");
				includeClause = new IncludeClause(bean, input, idv.toUpperCase(), gateKeeper);
				t = input.peek(0);
			}
		}
		if (t != null) {
			throw new ParserException(input, new Type[0]);
		}
	}

	public SelectClause parseSelectClause(Input input) throws ParserException {
		/*
		 * Identify the set of identification variables and paths that must be
		 * present in the from clause. Also see if COUNT is used.
		 */
		input.consume(Token.Type.SELECT);
		StringBuilder sb = new StringBuilder();

		Token t = input.peek(0);
		while (t != null && t.getType() != Token.Type.FROM) {
			input.consume();
			sb.append(" " + t.getValue());
			if (t.getType() == Token.Type.NAME) {
				idPaths.add(t.getValue());
			} else if (t.getType() == Token.Type.COUNT) {
				count = true;
			}
			t = input.peek(0);
		}
		clause = sb.toString();
	}
}
