package org.sagebionetworks.bridge.hibernate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sagebionetworks.bridge.BridgeUtils;

import com.google.common.base.Joiner;

/**
 * A helper class to manage construction of HQL strings.
 */
class QueryBuilder {
    
    private final List<String> phrases = new ArrayList<>();
    private final Map<String,Object> params = new HashMap<>();
    
    public void append(String phrase) {
        phrases.add(phrase);
    }
    public void append(String phrase, String key, Object value) {
        phrases.add(phrase);
        params.put(key, value);
    }
    public void append(String phrase, String key1, Object value1, String key2, Object value2) {
        phrases.add(phrase);
        params.put(key1, value1);
        params.put(key2, value2);
    }
    public void dataGroups(Set<String> dataGroups, String operator) {
        if (!BridgeUtils.isEmpty(dataGroups)) {
            int i = 0;
            List<String> clauses = new ArrayList<>();
            for (String oneDataGroup : dataGroups) {
                String varName = operator.replace(" ", "") + (++i);
                clauses.add(":"+varName+" "+operator+" elements(acct.dataGroups)");
                params.put(varName, oneDataGroup);
            }
            phrases.add("AND (" + Joiner.on(" AND ").join(clauses) + ")");
        }
    }
    public String getQuery() {
        return BridgeUtils.SPACE_JOINER.join(phrases);
    }
    public Map<String,Object> getParameters() {
        return params;
    }
}
