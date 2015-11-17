package org.sagebionetworks.bridge.models.accounts;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.google.common.collect.Sets;

import nl.jqno.equalsverifier.EqualsVerifier;

public class DataGroupsTest {

    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(DataGroups.class).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void canRoundTripSerialize() throws Exception {
        DataGroups groups = new DataGroups(Sets.newHashSet("A","B","C"));
        
        String json = BridgeObjectMapper.get().writeValueAsString(groups);
        assertEquals("{\"dataGroups\":[\"A\",\"B\",\"C\"],\"type\":\"DataGroups\"}", json);
       
        DataGroups newGroups = BridgeObjectMapper.get().readValue(json, DataGroups.class);
        assertEquals(3, newGroups.getDataGroups().size());
        assertEquals(groups.getDataGroups(), newGroups.getDataGroups());
    }
    
    @Test
    public void handlesNulls() throws Exception {
        DataGroups groups = new DataGroups(null);
        
        String json = BridgeObjectMapper.get().writeValueAsString(groups);
        assertEquals("{\"dataGroups\":[],\"type\":\"DataGroups\"}", json);
       
        DataGroups newGroups = BridgeObjectMapper.get().readValue(json, DataGroups.class);
        assertEquals(0, newGroups.getDataGroups().size());
    }
    
}
