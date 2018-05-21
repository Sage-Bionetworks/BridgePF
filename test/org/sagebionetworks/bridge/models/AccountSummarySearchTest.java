package org.sagebionetworks.bridge.models;

import static org.junit.Assert.assertEquals;

import org.joda.time.DateTime;
import org.junit.Test;
import org.sagebionetworks.bridge.BridgeConstants;

import com.google.common.collect.Sets;

import nl.jqno.equalsverifier.EqualsVerifier;

public class AccountSummarySearchTest {
    
    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(AccountSummarySearch.class).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void itWorks() {
        DateTime startTime = DateTime.now().minusDays(2);
        DateTime endTime = DateTime.now();
        
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
        
        assertEquals(10, search.getOffsetBy());
        assertEquals(100, search.getPageSize());
        assertEquals("email", search.getEmailFilter());
        assertEquals("phone", search.getPhoneFilter());
        assertEquals(Sets.newHashSet("group1"), search.getAllOfGroups());
        assertEquals(Sets.newHashSet("group2"), search.getNoneOfGroups());
        assertEquals("en", search.getLanguage());
        assertEquals(startTime, search.getStartTime());
        assertEquals(endTime, search.getEndTime());
    }
    
    @Test
    public void setsDefaults() {
        AccountSummarySearch search = new AccountSummarySearch.Builder().build();
        
        assertEquals(0, search.getOffsetBy());
        assertEquals(BridgeConstants.API_DEFAULT_PAGE_SIZE, search.getPageSize());
    }
}
