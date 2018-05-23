package org.sagebionetworks.bridge.models;

import static org.junit.Assert.assertEquals;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.chrono.ISOChronology;
import org.junit.Test;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;

import nl.jqno.equalsverifier.EqualsVerifier;

public class AccountSummarySearchTest {
    
    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(AccountSummarySearch.class).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void equalityWithChronologyDifference() throws Exception {
        // Why are these not found to be the same in the controller test after serialization/deserialization?
        DateTimeZone losAngeles = DateTimeZone.forID("America/Los_Angeles");
        // This does weird things to Joda Time's Chronology object. Enough to break equality
        DateTimeZone offsetHours = DateTimeZone.forTimeZone(losAngeles.toTimeZone());
        
        DateTime startTime = DateTime.parse("2018-05-22T06:50:21.650-07:00")
                .withChronology(ISOChronology.getInstance(losAngeles));
        DateTime endTime = DateTime.parse("2018-05-22T09:50:21.664-07:00")
                .withChronology(ISOChronology.getInstance(offsetHours));
        
        AccountSummarySearch search1 = new AccountSummarySearch.Builder()
                .withOffsetBy(10)
                .withPageSize(100)
                .withEmailFilter("email")
                .withPhoneFilter("phone")
                .withAllOfGroups(Sets.newHashSet("group1"))
                .withNoneOfGroups(Sets.newHashSet("group2"))
                .withLanguage("en")
                .withStartTime(startTime)
                .withEndTime(endTime)
                .build();
        
        ObjectMapper mapper = BridgeObjectMapper.get();
        String json = mapper.writeValueAsString(search1);
        AccountSummarySearch search2 = mapper.readValue(json, AccountSummarySearch.class);
        
        assertEquals(search1, search2);
    }
    
    @Test
    public void canBeSerialized() throws Exception {
        // Verify that our serialization preserves the time zone. This has been an 
        // issue with Jackson.
        DateTime startTime = DateTime.now(DateTimeZone.forOffsetHours(3)).minusDays(2);
        DateTime endTime = DateTime.now(DateTimeZone.forOffsetHours(3));
        
        AccountSummarySearch search = new AccountSummarySearch.Builder()
            .withOffsetBy(10)
            .withPageSize(100)
            .withEmailFilter("email")
            .withPhoneFilter("phone")
            .withAllOfGroups(Sets.newHashSet("group1"))
            .withNoneOfGroups(Sets.newHashSet("group2"))
            .withLanguage("en")
            .withStartTime(startTime)
            .withEndTime(endTime).build();
        
        String json = BridgeObjectMapper.get().writeValueAsString(search);
        JsonNode node = BridgeObjectMapper.get().readTree(json);
        
        // The one JSON property with no analogue in the object itself, verify it's correct
        assertEquals("AccountSummarySearch", node.get("type").textValue());
        
        AccountSummarySearch deser = BridgeObjectMapper.get().readValue(json, AccountSummarySearch.class);
        
        assertEquals(10, deser.getOffsetBy());
        assertEquals(100, deser.getPageSize());
        assertEquals("email", deser.getEmailFilter());
        assertEquals("phone", deser.getPhoneFilter());
        assertEquals(Sets.newHashSet("group1"), deser.getAllOfGroups());
        assertEquals(Sets.newHashSet("group2"), deser.getNoneOfGroups());
        assertEquals("en", deser.getLanguage());
        assertEquals(startTime, deser.getStartTime());
        assertEquals(endTime, deser.getEndTime());
    }
    
    @Test
    public void setsDefaults() {
        assertEquals(0, AccountSummarySearch.EMPTY_SEARCH.getOffsetBy());
        assertEquals(BridgeConstants.API_DEFAULT_PAGE_SIZE, AccountSummarySearch.EMPTY_SEARCH.getPageSize());
    }
}
