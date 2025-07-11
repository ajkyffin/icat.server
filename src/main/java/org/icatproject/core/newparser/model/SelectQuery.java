package org.icatproject.core.newparser.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import org.icatproject.core.IcatException;
import org.icatproject.core.entity.EntityBaseBean;
import org.icatproject.core.entity.Rule;
import org.icatproject.core.manager.GateKeeper;
import org.icatproject.core.newparser.Input;
import org.icatproject.core.newparser.OtherJpqlClauses;
import org.icatproject.core.newparser.ParserException;
import org.icatproject.core.newparser.Token;
import org.icatproject.core.newparser.Token.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SelectQuery {

	// select_query ::= select_clause from_clause [where_clause] [groupby_clause] [having_clause] [orderby_clause] [include_clause] [limit_clause]

	public final SelectClause selectClause;
	public final FromClause fromClause;
	public final WhereClause whereClause;
	public final GroupByClause groupByClause;
	public final HavingClause havingClause;
	public final OrderByClause orderByClause;
	public final IncludeClause includeClause;
	public final LimitClause limitClause;

	public SelectQuery(SelectClause selectClause, FromClause fromClause, WhereClause whereClause,
			GroupByClause groupByClause, HavingClause havingClause, OrderByClause orderByClause,
			IncludeClause includeClause, LimitClause limitClause) {
		this.selectClause = selectClause;
		this.fromClause = fromClause;
		this.whereClause = whereClause;
		this.groupByClause = groupByClause;
		this.havingClause = havingClause;
		this.orderByClause = orderByClause;
		this.includeClause = includeClause;
		this.limitClause = limitClause;
	}

	@Override
	public String toString() {
		return selectClause.toString()
			+ fromClause.toString()
			+ whereClause   != null ? " " + whereClause.toString()   : ""
			+ groupByClause != null ? " " + groupByClause.toString() : ""
			+ havingClause  != null ? " " + havingClause.toString()  : ""
			+ orderByClause != null ? " " + orderByClause.toString() : ""
			+ includeClause != null ? " " + includeClause.toString() : ""
			+ limitClause   != null ? " " + limitClause.toString()   : "";
	}

	public String getJPQL(String userId, EntityManager entityManager) {
		logger.debug("Processing: " + this);
		logger.debug("=> fromClause: " + fromClause);
		logger.debug("=> whereClause: " + whereClause);

		// Trap case of selecting on id values where the LIMIT is ignored in the
		// eclipselink generated SQL
		if (limitClause != null && whereClause != null) {
			if (limitClause.getOffset() > 0 && idSearch.matcher(whereClause.toString()).matches()) {
				logger.debug("LIMIT offset is non-zero but can only return at most one entry");
				return null;
			}
		}

		StringBuilder sb = new StringBuilder("SELECT " + selectClause);
		sb.append(" FROM" + fromClause.toString());
		List<StringBuilder> whereBits = new ArrayList<>();
		if (whereClause != null) {
			whereBits.add(new StringBuilder(whereClause.toString()));
		}

		for (Entry<String, Class<? extends EntityBaseBean>> entry : fromClause.getAuthzMap().entrySet()) {
			String beanName = entry.getValue().getSimpleName();
			String path = entry.getKey();

			boolean restricted;
			List<Rule> rules = null;
			if (gateKeeper.getRootUserNames().contains(userId)) {
				logger.info("\"Root\" user " + userId + " is allowed READ to " + beanName);
				restricted = false;
			} else if (gateKeeper.getPublicTables().contains(beanName)) {
				logger.info("All are allowed READ to " + beanName);
				restricted = false;
			} else {
				TypedQuery<Rule> query = entityManager.createNamedQuery(Rule.SEARCH_QUERY, Rule.class)
						.setParameter("member", userId).setParameter("bean", beanName);
				rules = query.getResultList();
				SelectQuery.logger
						.debug("Got " + rules.size() + " authz queries for search by " + userId + " to a " + beanName);
				if (rules.size() == 0) {
					return null;
				}
				restricted = true;

				for (Rule r : rules) {
					if (!r.isRestricted()) {
						logger.info("Null restriction => Operation permitted");
						restricted = false;
						break;
					}
				}
			}

			if (restricted) {
				/* Can only get here if rules has been set for this bean */
				StringBuilder ruleWhere = new StringBuilder();
				for (Rule r : rules) {
					String jpql = r.getSearchJPQL();

					logger.info("Include authz rule {} for {}", jpql, beanName);

					if (ruleWhere.length() > 0) {
						ruleWhere.append(" OR ");
					}
					ruleWhere.append(path + " IN  (" + jpql + ")");
				}
				whereBits.add(ruleWhere);

			}
		}

		boolean first = true;
		for (StringBuilder ruleWhere : whereBits) {
			if (first) {
				sb.append(" WHERE ( ");
				first = false;
			} else {
				sb.append(" AND ( ");
			}
			sb.append(ruleWhere + " )");
		}

		if (otherJpqlClauses != null) {
			sb.append(" " + otherJpqlClauses.toString());
		}
		return sb.toString();
	}

	public String typeQuery() {
		logger.debug("Select clause is: {}", selectClause);
		String s = selectClause.toString();
		int n = s.indexOf('(');
		s = s.substring(n + 1, s.length() - 1).replace("DISTINCT ", "");
		logger.debug("Result is {}", "SELECT " + s + " FROM" + fromClause + " WHERE " + s + " IS NOT NULL");
		return "SELECT " + s + " FROM" + fromClause + " WHERE " + s + " IS NOT NULL";
	}

	public List<Object> getNoAuthzResult() {
		if (selectClause.isCount()) {
			return List.of(0L);
		}

		return List.of();
	}
}
