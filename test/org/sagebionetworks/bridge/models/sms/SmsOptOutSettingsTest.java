package org.sagebionetworks.bridge.models.sms;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SmsOptOutSettingsTest {
    private static final String STUDY_ID = "test-study";
    private static final String OTHER_STUDY_ID = "other-study";

    @Test
    public void promotionalStudyTrueOverridesGlobalFalse() {
        SmsOptOutSettings optOut = SmsOptOutSettings.create();
        optOut.setGlobalPromotionalOptOut(false);
        optOut.getPromotionalOptOuts().put(STUDY_ID, true);

        assertTrue(optOut.getPromotionalOptOutForStudy(STUDY_ID));
        assertFalse(optOut.getPromotionalOptOutForStudy(OTHER_STUDY_ID));
    }

    @Test
    public void promotionalStudyFalseOverridesGlobalTrue() {
        SmsOptOutSettings optOut = SmsOptOutSettings.create();
        optOut.setGlobalPromotionalOptOut(true);
        optOut.getPromotionalOptOuts().put(STUDY_ID, false);

        assertFalse(optOut.getPromotionalOptOutForStudy(STUDY_ID));
        assertTrue(optOut.getPromotionalOptOutForStudy(OTHER_STUDY_ID));
    }

    @Test
    public void transactionalTrue() {
        SmsOptOutSettings optOut = SmsOptOutSettings.create();
        optOut.getTransactionalOptOuts().put(STUDY_ID, true);
        assertTrue(optOut.getTransactionalOptOutForStudy(STUDY_ID));
    }

    @Test
    public void transactionalFalse() {
        SmsOptOutSettings optOut = SmsOptOutSettings.create();
        optOut.getTransactionalOptOuts().put(STUDY_ID, false);
        assertFalse(optOut.getTransactionalOptOutForStudy(STUDY_ID));
    }

    @Test
    public void transactionalDefaultsToFalse() {
        SmsOptOutSettings optOut = SmsOptOutSettings.create();
        assertFalse(optOut.getTransactionalOptOutForStudy(STUDY_ID));
    }
}
