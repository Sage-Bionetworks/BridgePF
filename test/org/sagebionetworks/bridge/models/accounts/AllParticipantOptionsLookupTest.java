package org.sagebionetworks.bridge.models.accounts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.sagebionetworks.bridge.dao.ParticipantOption.EXTERNAL_IDENTIFIER;

import java.util.Map;

import org.junit.Test;

import com.google.common.collect.Maps;

public class AllParticipantOptionsLookupTest {

    @Test
    public void alwaysReturnsAValue() {
        Map<String,String> map = Maps.newHashMap();
        map.put(EXTERNAL_IDENTIFIER.name(), "foo");
        
        ParticipantOptionsLookup lookup = new ParticipantOptionsLookup(map);
        
        AllParticipantOptionsLookup allLookup = new AllParticipantOptionsLookup();
        allLookup.put("AAA", lookup);
        
        // This returns a lookup, as you'd expect
        assertEquals(lookup, allLookup.get("AAA"));
        
        // And so does this, a null object that returns default values
        assertNotNull(allLookup.get("BBB"));
    }
    
}
