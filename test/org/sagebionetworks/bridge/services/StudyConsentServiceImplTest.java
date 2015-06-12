package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;

import javax.annotation.Resource;

import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.TestUserAdminHelper;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.dao.StudyConsentDao;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.studies.StudyConsent;
import org.sagebionetworks.bridge.models.studies.StudyConsentForm;
import org.sagebionetworks.bridge.models.studies.StudyConsentView;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.s3.S3Helper;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class StudyConsentServiceImplTest {
    
    private static final String BUCKET = BridgeConfigFactory.getConfig().getConsentsBucket();
    private static final StudyIdentifier STUDY_ID = new StudyIdentifierImpl("study-key");

    @Resource
    private StudyConsentDao studyConsentDao;

    @Resource
    private TestUserAdminHelper helper;

    @Resource(name = "s3ConsentsHelper")
    private S3Helper s3Helper;

    @Resource
    private StudyConsentService studyConsentService;
    
    @After
    public void after() {
        studyConsentDao.deleteAllConsents(STUDY_ID);
    }

    @Test
    public void crudStudyConsent() {
        String documentContent = "<document/>";
        StudyConsentForm form = new StudyConsentForm(documentContent);

        // addConsent should return a non-null consent object.
        StudyConsentView addedConsent1 = studyConsentService.addConsent(STUDY_ID, form);
        assertNotNull(addedConsent1);

        try {
            studyConsentService.getActiveConsent(STUDY_ID);
            fail("getActiveConsent should throw exception, as there is no currently active consent.");
        } catch (Exception e) {
        }

        // Get active consent returns the most recently activated consent document.
        StudyConsentView activatedConsent = studyConsentService.activateConsent(STUDY_ID, addedConsent1.getCreatedOn());
        StudyConsentView getActiveConsent = studyConsentService.getActiveConsent(STUDY_ID);
        assertTrue(activatedConsent.getCreatedOn() == getActiveConsent.getCreatedOn());
        assertEquals(documentContent, getActiveConsent.getDocumentContent());
        assertNotNull(getActiveConsent.getStudyConsent().getStoragePath());
        
        // Get all consents returns one consent document (addedConsent).
        List<StudyConsent> allConsents = studyConsentService.getAllConsents(STUDY_ID);
        assertTrue(allConsents.size() == 1);
    }
    
    @Test
    public void studyConsentWithFileAndS3ContentTakesS3Content() throws Exception {
        DateTime createdOn = DateTime.now();
        String key = STUDY_ID.getIdentifier() + "." + createdOn.getMillis();
        s3Helper.writeBytesToS3(BUCKET, key, "<document/>".getBytes());
        
        StudyConsent consent = studyConsentDao.addConsent(STUDY_ID, key, createdOn);
        studyConsentDao.activate(consent);
        // The junk path should not prevent the service from getting the S3 content.
        // We actually wouldn't get here if it tried to load from disk with the path we've provided.
        StudyConsentView view = studyConsentService.getConsent(STUDY_ID, createdOn.getMillis());
        assertEquals("<document/>", view.getDocumentContent());
    }
    
    
    @Test(expected = InvalidEntityException.class)
    public void invalidXmlStudyConsentThrowsException() {
        StudyConsentForm form = new StudyConsentForm("<cml><p>This is not valid XML.</cml>");
        studyConsentService.addConsent(new StudyIdentifierImpl("api"), form);
    }
    
}