package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DynamoUserConsentTest {

    @Test
    public void test() {
        String healthCode = "123";
        String studyKey = "456";
        long consentTimestamp = 789L;
        DynamoStudyConsent1 studyConsent = new DynamoStudyConsent1();
        studyConsent.setStudyKey(studyKey);
        studyConsent.setCreatedOn(consentTimestamp);
        DynamoUserConsent userConsent = new DynamoUserConsent(healthCode, studyConsent);
        assertEquals(healthCode + ":" + studyKey + ":" + consentTimestamp, userConsent.getHealthCodeStudy());
        assertEquals(consentTimestamp, userConsent.getConsentTimestamp());
        assertEquals(studyKey, userConsent.getStudyKey());
        userConsent.setGive(2873950L);
        userConsent.setVersion(9588L);
        userConsent.setWithdraw(89813L);
        DynamoUserConsent userConsentCopy = new DynamoUserConsent(userConsent);
        assertEquals(userConsent.getHealthCodeStudy(), userConsentCopy.getHealthCodeStudy());
        assertEquals(userConsent.getStudyKey(), userConsentCopy.getStudyKey());
        assertEquals(userConsent.getConsentTimestamp(), userConsentCopy.getConsentTimestamp());
        assertEquals(userConsent.getGive(), userConsentCopy.getGive());
        assertEquals(userConsent.getWithdraw(), userConsentCopy.getWithdraw());
        assertEquals(userConsent.getVersion(), userConsentCopy.getVersion());
    }
}
