package org.sagebionetworks.bridge.models.schedules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

import org.joda.time.DateTime;
import org.junit.Test;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolderImpl;

public class SurveyReferenceTest {

    private static final String CREATED_ON_STRING = "2015-04-29T23:41:56.231Z";
    
    private static final DateTime CREATED_ON = DateTime.parse(CREATED_ON_STRING);
    
    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(SurveyReference.class).suppress(Warning.NONFINAL_FIELDS).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void correctlyParsesSurveyURL() {
        SurveyReference ref = new SurveyReference("https://webservices.sagebridge.org/api/v1/surveys/AAA-BBB-CCC/revisions/2015-04-29T23:41:56.231Z");
        
        assertEquals("AAA-BBB-CCC", ref.getGuid());
        assertEquals(CREATED_ON, ref.getCreatedOn());
        
        GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl("AAA-BBB-CCC", CREATED_ON.getMillis());
        assertEquals(keys, ref.getGuidCreatedOnVersionHolder());
    }
    
    @Test
    public void correctlyParsesPublishedSurveyURL() {
        SurveyReference ref = new SurveyReference("https://webservices.sagebridge.org/api/v1/surveys/AAA-BBB-CCC/revisions/published");
        
        assertEquals("AAA-BBB-CCC", ref.getGuid());
        assertNull(ref.getCreatedOn());
        assertNull(ref.getGuidCreatedOnVersionHolder());
    }

    @Test(expected = IllegalStateException.class)
    public void throwsExceptionOnCloseButNotCorrectURL() {
        new SurveyReference("https://webservices.sagebridge.org/api/v1/surveys/response/AAA-BBB-CCC");
    }
    
    @Test
    public void correctlyIdentifiesSurveyURL() {
        // This isn't perfect, but it helps.
        assertFalse(SurveyReference.isSurveyRef("https://webservices.sagebridge.org/api/v1/surveys/response/AAA-BBB-CCC"));
        assertFalse(SurveyReference.isSurveyRef("https://webservices.sagebridge.org/api/v1/surveys/response/AAA-BBB-CCC/revisions/DDDD/belgium"));
        assertFalse(SurveyReference.isSurveyRef("/api/v1/surveys/AAA-BBB-CCC/revisions/published"));
        assertTrue(SurveyReference.isSurveyRef("https://webservices.sagebridge.org/api/v1/surveys/AAA-BBB-CCC/revisions/2015-04-29T23:41:56.231Z"));
        assertTrue(SurveyReference.isSurveyRef("https://webservices.sagebridge.org/api/v1/surveys/AAA-BBB-CCC/revisions/published"));
    }
    
}
