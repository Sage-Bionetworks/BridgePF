package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuidImpl;

public class DynamoUserConsentTest {

    @Test
    public void test() {
        // Test constructor 1
        String healthCode = "123";
        String studyIdentifier = "456";
        DynamoUserConsent3 userConsent = new DynamoUserConsent3(healthCode, new SubpopulationGuidImpl(studyIdentifier));
        assertEquals(healthCode + ":" + studyIdentifier, userConsent.getHealthCodeSubpopGuid());
        assertEquals(healthCode, userConsent.getHealthCode());
        assertEquals(studyIdentifier, userConsent.getSubpopulationGuid());

        // Test constructor 2
        long consentTimestamp = 789L;
        userConsent = new DynamoUserConsent3(healthCode, new SubpopulationGuidImpl(studyIdentifier));
        userConsent.setConsentCreatedOn(consentTimestamp);
        assertEquals(healthCode + ":" + studyIdentifier, userConsent.getHealthCodeSubpopGuid());
        assertEquals(healthCode, userConsent.getHealthCode());
        assertEquals(studyIdentifier, userConsent.getSubpopulationGuid());
        assertEquals(consentTimestamp, userConsent.getConsentCreatedOn());

        // Test copy constructor
        userConsent.setSignedOn(555L);
        userConsent.setVersion(777L);

        DynamoUserConsent3 userConsentCopy = new DynamoUserConsent3(healthCode, new SubpopulationGuidImpl(studyIdentifier));
        assertEquals(healthCode + ":" + studyIdentifier, userConsentCopy.getHealthCodeSubpopGuid());
        assertEquals(healthCode, userConsentCopy.getHealthCode());
        assertEquals(studyIdentifier, userConsentCopy.getSubpopulationGuid());
    }
}
