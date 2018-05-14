package org.sagebionetworks.bridge.hibernate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

public final class HqlWhereClause {
    
    private static final Pattern PATTERN = Pattern.compile(":[^\\\b]+");
    private static final Joiner AND_JOINER = Joiner.on(" AND ");
    private static final Joiner OR_JOINER = Joiner.on(" OR ");
    private final List<String> expressions = new ArrayList<>();
    private final Map<String,Object> parameters = new HashMap<>();
    private final boolean useOrOperator;
    
    public HqlWhereClause(boolean useOrOperator) {
        this.useOrOperator = useOrOperator;
    }
    
    public HqlWhereClause addExpression(String expr) {
        Preconditions.checkNotNull(expr);
        expressions.add(expr);
        return this;
    }
    
    public HqlWhereClause addExpression(String expr, Object value) {
        Preconditions.checkNotNull(expr);
        if (value != null) {
            expressions.add(expr);
            // All like queries are infix queries.
            if (expr.toLowerCase().contains(" like ")) {
                value = "%" + value + "%";
            }
            String paramName = extractParameterName(expr);
            parameters.put(paramName, value);
        }
        return this;
    }
    
    public String getClause() {
        if (expressions.isEmpty()) {
            return null;
        }
        return (useOrOperator) ? OR_JOINER.join(expressions) : AND_JOINER.join(expressions);
    }
    
    public Map<String,Object> getParameters() {
        return parameters;
    }
    
    private String extractParameterName(String expr) {
        Matcher matcher = PATTERN.matcher(expr);
        matcher.find();
        return matcher.group().substring(1);
    }

    @Override
    public int hashCode() {
        return Objects.hash(parameters, expressions, useOrOperator);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        HqlWhereClause other = (HqlWhereClause) obj;
        return Objects.equals(parameters, other.parameters) && Objects.equals(expressions, other.expressions)
                && Objects.equals(useOrOperator, other.useOrOperator);
    }

    @Override
    public String toString() {
        return "HqlWhereClause [expressions=" + expressions + ", parameters=" + parameters + "]";
    }
}
