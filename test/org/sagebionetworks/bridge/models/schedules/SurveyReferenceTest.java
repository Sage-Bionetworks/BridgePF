package org.sagebionetworks.bridge.models.schedules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.joda.time.DateTime;
import org.junit.Test;

import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolderImpl;

public class SurveyReferenceTest {

    private static final String IDENTIFIER = "id";
    private static final String GUID = "abc";
    private static final DateTime CREATED_ON = DateTime.parse("2017-02-09T20:15:59.558Z");
    
    @Test
    public void test() {
        SurveyReference ref = new SurveyReference(IDENTIFIER, GUID, CREATED_ON);
        
        assertEquals(IDENTIFIER, ref.getIdentifier());
        assertEquals(GUID, ref.getGuid());
        assertEquals(CREATED_ON, ref.getCreatedOn());
        assertTrue(ref.getHref().contains("/v3/surveys/abc/revisions/2017-02-09T20:15:59.558Z"));
        
        GuidCreatedOnVersionHolder keys1 = new GuidCreatedOnVersionHolderImpl(GUID, CREATED_ON.getMillis());
        assertTrue(ref.equalsSurvey(keys1));
        
        GuidCreatedOnVersionHolder keys2 = new GuidCreatedOnVersionHolderImpl("def", CREATED_ON.getMillis());
        assertFalse(ref.equalsSurvey(keys2));
        
        // Doesn't match published links, only specific ones.
        ref = new SurveyReference(IDENTIFIER, GUID, null);
        assertFalse(ref.equalsSurvey(keys1));
        assertFalse(ref.equalsSurvey(keys2));
        
        // Null test (should return false)
        assertFalse(ref.equalsSurvey(null));
        
        // Same guid, different createdOn
        GuidCreatedOnVersionHolder keys3 = new GuidCreatedOnVersionHolderImpl(GUID, DateTime.now().getMillis());
        assertFalse(ref.equalsSurvey(keys3));
        
        // Different guid, different createdOn
        GuidCreatedOnVersionHolder keys4 = new GuidCreatedOnVersionHolderImpl("def", DateTime.now().getMillis());
        assertFalse(ref.equalsSurvey(keys4));
    }
    
}
