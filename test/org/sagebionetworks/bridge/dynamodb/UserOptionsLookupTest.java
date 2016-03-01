package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.sagebionetworks.bridge.dao.ParticipantOption.DATA_GROUPS;
import static org.sagebionetworks.bridge.dao.ParticipantOption.EXTERNAL_IDENTIFIER;
import static org.sagebionetworks.bridge.dao.ParticipantOption.SHARING_SCOPE;

import java.util.Map;
import java.util.Set;

import org.junit.Test;

import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.models.accounts.UserOptionsLookup;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class UserOptionsLookupTest {

    @Test
    public void nullDefaultIsOK() {
        UserOptionsLookup lookup = new UserOptionsLookup(null);
        
        assertNull(lookup.getString(EXTERNAL_IDENTIFIER));
    }
    
    @Test
    public void returnsDefaultValueWhenNullSet() {
        UserOptionsLookup lookup = new UserOptionsLookup(null);
        
        SharingScope scope = lookup.getEnum(SHARING_SCOPE, SharingScope.class);
        assertEquals(SharingScope.NO_SHARING, scope);
    }
    
    @Test
    public void returnsDefaultValueWhenEmptySet() {
        UserOptionsLookup lookup = new UserOptionsLookup(Maps.newHashMap());
        
        SharingScope scope = lookup.getEnum(SHARING_SCOPE, SharingScope.class);
        assertEquals(SharingScope.NO_SHARING, scope);
    }
    
    @Test
    public void returnsValueWhenSet() {
        UserOptionsLookup lookup = new UserOptionsLookup(map(SHARING_SCOPE, SharingScope.ALL_QUALIFIED_RESEARCHERS.name()));
        
        SharingScope scope = lookup.getEnum(SHARING_SCOPE, SharingScope.class);
        assertEquals(SharingScope.ALL_QUALIFIED_RESEARCHERS, scope);
    }
    
    @Test
    public void correctlySetsAndGetsStringSet() {
        UserOptionsLookup lookup = new UserOptionsLookup(map(DATA_GROUPS, "group1,group2"));
        
        Set<String> groups = lookup.getStringSet(DATA_GROUPS);
        assertEquals(Sets.newHashSet("group1","group2"), groups);
    }
    
    private Map<String,String> map(ParticipantOption option, String value) {
        Map<String,String> map = Maps.newHashMap();
        map.put(option.name(), value);
        return map;
    }

}

