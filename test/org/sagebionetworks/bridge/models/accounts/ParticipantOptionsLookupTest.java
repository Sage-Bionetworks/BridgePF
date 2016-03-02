package org.sagebionetworks.bridge.models.accounts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.sagebionetworks.bridge.dao.ParticipantOption.DATA_GROUPS;
import static org.sagebionetworks.bridge.dao.ParticipantOption.EMAIL_NOTIFICATIONS;
import static org.sagebionetworks.bridge.dao.ParticipantOption.EXTERNAL_IDENTIFIER;
import static org.sagebionetworks.bridge.dao.ParticipantOption.LANGUAGES;
import static org.sagebionetworks.bridge.dao.ParticipantOption.SHARING_SCOPE;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;

import com.google.common.collect.Maps;

public class ParticipantOptionsLookupTest {
    
    @Test
    public void getDefaultValues() {
        ParticipantOptionsLookup lookup = new ParticipantOptionsLookup(Maps.newHashMap());
        
        SharingScope scope = lookup.getEnum(SHARING_SCOPE, SharingScope.class);
        assertEquals(SharingScope.NO_SHARING, scope);
        
        Boolean email = lookup.getBoolean(EMAIL_NOTIFICATIONS);
        assertEquals(Boolean.TRUE, email);
        
        String identifier = lookup.getString(EXTERNAL_IDENTIFIER);
        assertNull(identifier);
        
        Set<String> dataGroups = lookup.getStringSet(DATA_GROUPS);
        assertTrue(dataGroups.isEmpty());
        
        String lang = lookup.getString(LANGUAGES);
        assertNull(lang);
    }
    
    @Test
    public void getValuesWithWrongAccessor() {
        ParticipantOptionsLookup lookup = new ParticipantOptionsLookup(Maps.newHashMap());
        
        ParticipantOption e = lookup.getEnum(SHARING_SCOPE, ParticipantOption.class);
        assertNull(e);

        Boolean b = lookup.getBoolean(EXTERNAL_IDENTIFIER);
        assertEquals(Boolean.FALSE, b);
        
        // This happens because we store everything as a string, so we're going to get
        // back a string here based on the enumeration name.
        String s = lookup.getString(SHARING_SCOPE);
        assertEquals("NO_SHARING", s);
        
        String s2 = lookup.getString(DATA_GROUPS);
        assertNull(s2);
        
        Set<String> set = lookup.getStringSet(EXTERNAL_IDENTIFIER);
        assertTrue(set.isEmpty());
        
        LinkedHashSet<String> set2 = lookup.getOrderedStringSet(EXTERNAL_IDENTIFIER);
        assertTrue(set2.isEmpty());
    }
    
    @Test
    public void getStringWithValue() {
        ParticipantOptionsLookup lookup = setupLookup(EXTERNAL_IDENTIFIER, "foo");
        
        assertEquals("foo", lookup.getString(EXTERNAL_IDENTIFIER));
    }

    @Test
    public void getStringSetWithValue() {
        ParticipantOptionsLookup lookup = setupLookup(DATA_GROUPS, "foo1,foo2,foo3");
        
        LinkedHashSet<String> set = TestUtils.newLinkedHashSet("foo1","foo2","foo3");
        assertEquals(set, lookup.getStringSet(DATA_GROUPS));
    }
    
    @Test
    public void getOrderedStringSetWithValue() {
        ParticipantOptionsLookup lookup = setupLookup(LANGUAGES, "en,fr,es");
        
        LinkedHashSet<String> set = TestUtils.newLinkedHashSet("en","fr","es");
        assertEquals(set, lookup.getOrderedStringSet(LANGUAGES));
    }

    @Test
    public void getBooleanWithValue() {
        ParticipantOptionsLookup lookup = setupLookup(EMAIL_NOTIFICATIONS, "false");
        
        assertEquals(Boolean.FALSE, lookup.getBoolean(EMAIL_NOTIFICATIONS));
    }
    
    @Test
    public void getEnumWithValue() {
        ParticipantOptionsLookup lookup = setupLookup(SHARING_SCOPE, "ALL_QUALIFIED_RESEARCHERS");
        
        assertEquals(SharingScope.ALL_QUALIFIED_RESEARCHERS, lookup.getEnum(SHARING_SCOPE, SharingScope.class));
    }
    
    private ParticipantOptionsLookup setupLookup(ParticipantOption option, String value) {
        Map<String,String> map = Maps.newHashMap();
        map.put(option.name(), value);
        return new ParticipantOptionsLookup(map);
    }
}
