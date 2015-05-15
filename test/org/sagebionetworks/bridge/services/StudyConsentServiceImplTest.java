package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;

import javax.annotation.Resource;

import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.TestUserAdminHelper;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.dao.StudyConsentDao;
import org.sagebionetworks.bridge.dynamodb.DynamoInitializer;
import org.sagebionetworks.bridge.dynamodb.DynamoStudyConsent1;
import org.sagebionetworks.bridge.dynamodb.DynamoTestUtil;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
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

    @Resource
    private StudyConsentDao studyConsentDao;

    @Resource
    private TestUserAdminHelper helper;

    @Resource(name = "s3ConsentsHelper")
    private S3Helper s3Helper;

    @Resource
    private StudyConsentService studyConsentService;

    @Before
    public void before() {
        DynamoInitializer.init(DynamoStudyConsent1.class);
        DynamoTestUtil.clearTable(DynamoStudyConsent1.class);
    }

    @After
    public void after() {
        DynamoTestUtil.clearTable(DynamoStudyConsent1.class);
    }

    @Test
    public void crudStudyConsent() {
        StudyIdentifier studyId = new StudyIdentifierImpl("study-key");
        String documentContent = "<document/>";
        StudyConsentForm form = new StudyConsentForm(documentContent);

        // addConsent should return a non-null consent object.
        StudyConsentView addedConsent1 = studyConsentService.addConsent(studyId, form);
        assertNotNull(addedConsent1);

        try {
            studyConsentService.getActiveConsent(studyId);
            fail("getActiveConsent should throw exception, as there is no currently active consent.");
        } catch (Exception e) {
        }

        // Get active consent returns the most recently activated consent document.
        StudyConsentView activatedConsent = studyConsentService.activateConsent(studyId, addedConsent1.getCreatedOn());
        StudyConsentView getActiveConsent = studyConsentService.getActiveConsent(studyId);
        assertTrue(activatedConsent.getCreatedOn() == getActiveConsent.getCreatedOn());
        assertEquals(documentContent, getActiveConsent.getDocumentContent());
        assertNull(getActiveConsent.getStudyConsent().getPath());
        
        // Get all consents returns one consent document (addedConsent).
        List<StudyConsent> allConsents = studyConsentService.getAllConsents(studyId);
        assertTrue(allConsents.size() == 1);

        // Cannot delete active consent document.
        try {
            studyConsentService.deleteConsent(studyId, getActiveConsent.getCreatedOn());
            fail("Was able to successfully delete active consent, which we should not be able to do.");
        } catch (BridgeServiceException e) {
        }
    }
    
    @Test
    public void studyConsentWithFileAndS3ContentTakesS3Content() throws Exception {
        StudyIdentifier studyId = new StudyIdentifierImpl("api");
        DateTime createdOn = DateTime.now();
        String key = "api." + createdOn.getMillis();
        s3Helper.writeBytesToS3(BUCKET, key, "<document/>".getBytes());
        
        StudyConsent consent = studyConsentDao.addConsent(studyId, "/junk/path", key, createdOn);
        studyConsentDao.setActive(consent, true);
        
        // The junk path should not prevent the service from getting the S3 content.
        // We actually wouldn't get here if it tried to load from disk with the path we've provided.
        StudyConsentView view = studyConsentService.getConsent(studyId, createdOn.getMillis());
        assertEquals("<document/>", view.getDocumentContent());
    }
}