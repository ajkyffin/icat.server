package org.icatproject.core.newparser.model;

import java.util.ArrayList;
import java.util.List;

public class SelectClause {

	// select_clause ::= SELECT [DISTINCT] select_expression {, select_expression}*
 
	public final boolean distinct;
	public final List<SelectExpression> selectExpressions;

	public SelectClause(boolean distinct, List<SelectExpression> selectExpressions) {
		this.distinct = distinct;
		this.selectExpressions = selectExpressions;
	}

	@Override
	public String toString() {
		List<String> selectExpressionStrings = new ArrayList<>();
		selectExpressions.forEach((x) -> selectExpressionStrings.add(x.toString()));

		return "SELECT "
			+ (distinct ? "DISTINCT " : "")
			+ String.join(", ", selectExpressionStrings);
	}
}
