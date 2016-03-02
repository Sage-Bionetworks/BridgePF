package org.sagebionetworks.bridge.models.accounts;

import java.util.Map;

import com.google.common.collect.Maps;

/**
 * Simple map wrapper that ensures all health codes have a lookup object, even if that healthCode
 * does not have a record in the table. The lookup is null-safe and will have default values where 
 * these exist.
 */
public class AllParticipantOptionsLookup {
    
    private final Map<String,ParticipantOptionsLookup> map = Maps.newHashMap();
    
    public void put(String healthCode, ParticipantOptionsLookup lookup) {
        map.put(healthCode, lookup);
    }
    
    public ParticipantOptionsLookup get(String healthCode) {
        ParticipantOptionsLookup lookup = map.get(healthCode);
        return (lookup != null) ? lookup : new ParticipantOptionsLookup(Maps.newHashMap());
    }
    
}
