package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;

public class OptionLookup {
    
    private final Map<String, String> map = new HashMap<String, String>();
    private final String defaultValue;

    public OptionLookup(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    void put(String healthCode, String value) {
        checkNotNull(healthCode);
        if (isNotBlank(value)) {
            map.put(healthCode, value);
        }
    }

    public String get(String healthCode) {
        String value = map.get(healthCode);
        return (value == null) ? defaultValue : value;
    }
    
    public SharingScope getSharingScope(String healthCode) {
        return SharingScope.valueOf(get(healthCode));
    }
    
    public Set<String> getDataGroups(String healthCode) {
        String value = map.get(healthCode);
        return BridgeUtils.commaListToOrderedSet(value);
    }

    public LinkedHashSet<String> getLanguages(String healthCode) {
        String value = map.get(healthCode);
        // We know this implementation returns a LinkedHashSet
        return (LinkedHashSet<String>)BridgeUtils.commaListToOrderedSet(value);
    }
}
