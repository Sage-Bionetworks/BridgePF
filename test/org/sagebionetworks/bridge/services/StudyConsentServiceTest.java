package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.List;

import javax.annotation.Resource;

import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.TestUserAdminHelper;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.dao.StudyConsentDao;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsent;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsentForm;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsentView;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.s3.S3Helper;

import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class StudyConsentServiceTest {

    private static final String BUCKET = BridgeConfigFactory.getConfig().getConsentsBucket();

    @Resource
    private StudyConsentDao studyConsentDao;

    @Resource
    private TestUserAdminHelper helper;

    @Resource(name = "s3ConsentsHelper")
    private S3Helper s3Helper;

    @Resource
    private StudyConsentService studyConsentService;
    
    @Resource
    private SubpopulationService subpopService;
    
    private Study study;
    
    private Subpopulation subpopulation;
    
    @Before
    public void before() {
        String id = TestUtils.randomName(StudyConsentServiceTest.class);
        
        study = new DynamoStudy();
        study.setIdentifier(id);
        study.setName("StudyConsentServiceTest Name");
        study.setSponsorName("StudyConsentServiceTest Sponsor");
        
        subpopulation = Subpopulation.create();
        subpopulation.setName("Subpopulation for StudyConsentServiceTest");
        subpopService.createSubpopulation(study, subpopulation);
    }
    
    @After
    public void after() {
        studyConsentDao.deleteAllConsents(subpopulation.getGuid());
        subpopService.deleteSubpopulation(study.getStudyIdentifier(), subpopulation.getGuid(), true);
    }

    @Test
    public void crudStudyConsent() {
        String documentContent = "<p>This is a consent document.</p><p>This is the second paragraph of same.</p>";
        StudyConsentForm form = new StudyConsentForm(documentContent);

        StudyConsentView view = studyConsentService.getActiveConsent(subpopulation);
        assertEquals("<p>This is a placeholder for your consent document.</p>", view.getDocumentContent());
        
        view = studyConsentService.addConsent(subpopulation.getGuid(), form);
        assertNotNull(view);
        studyConsentService.publishConsent(study, subpopulation, view.getCreatedOn());
        
        StudyConsentView getActiveConsent = studyConsentService.getActiveConsent(subpopulation);
        assertTrue(view.getCreatedOn() == getActiveConsent.getCreatedOn());
        
        // This is "fixed" by the XML and sanitizing parse that happens. It's fine.
        assertEquals("<p>This is a consent document.</p>\n<p>This is the second paragraph of same.</p>", getActiveConsent.getDocumentContent());
        assertNotNull(getActiveConsent.getStudyConsent().getStoragePath());

        // Get all consents returns one consent document (addedConsent).
        List<StudyConsent> allConsents = studyConsentService.getAllConsents(subpopulation.getGuid());
        assertEquals(2, allConsents.size());
    }
    
    @Test
    public void studyConsentWithFileAndS3ContentTakesS3Content() throws Exception {
        long createdOn = DateUtils.getCurrentMillisFromEpoch();
        String key = subpopulation.getGuidString() + "." + createdOn;
        s3Helper.writeBytesToS3(BUCKET, key, "<document/>".getBytes());
        
        studyConsentDao.addConsent(subpopulation.getGuid(), key, createdOn);
        // The junk path should not prevent the service from getting the S3 content.
        // We actually wouldn't get here if it tried to load from disk with the path we've provided.
        StudyConsentView view = studyConsentService.getConsent(subpopulation.getGuid(), createdOn);
        assertEquals("<document/>", view.getDocumentContent());
    }
    
    @Test
    public void invalidMarkupIsFixed() {
        StudyConsentForm form = new StudyConsentForm("<cml><p>This is not valid XML.</cml>");
        StudyConsentView view = studyConsentService.addConsent(subpopulation.getGuid(), form);
        assertEquals("<p>This is not valid XML.</p>", view.getDocumentContent());
    }
    
    @Test
    public void fullDocumentsAreConvertedToFragments() {
        String doc = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\"><html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title></title></head><body><p>This is all the content that should be kept.</p><br><p>And this makes it a fragment.</p></body></html>";
        
        StudyConsentForm form = new StudyConsentForm(doc);
        StudyConsentView view = studyConsentService.addConsent(subpopulation.getGuid(), form);
        assertEquals("<p>This is all the content that should be kept.</p>\n<br />\n<p>And this makes it a fragment.</p>", view.getDocumentContent());
    }
    
    @Test
    public void ckeditorMarkupIsPreserved() {
        String doc = "<s>This is a test</s><p style=\"color:red\">of new attributes ${url}.</p><hr />";
        
        StudyConsentForm form = new StudyConsentForm(doc);
        StudyConsentView view = studyConsentService.addConsent(subpopulation.getGuid(), form);
        // Text is pretty printed so remove that before comparing 
        assertEquals(doc, view.getDocumentContent().replaceAll("[\n\t\r]", ""));
    }
    
    @Test
    public void studyWithXmlCharactersCanBeRenderedAsPDF() {
        String doc = "<p>This is a test ${url} of how we escape ${studyName}${supportEmail}${technicalEmail}${sponsorName}</p>";
        
        StudyConsentForm form = new StudyConsentForm(doc);
        StudyConsentView view = studyConsentService.addConsent(subpopulation.getGuid(), form);
        
        Study studyWithEntities = new DynamoStudy();
        studyWithEntities.setIdentifier(study.getIdentifier());
        studyWithEntities.setName("This name's got an apostrophe & an ampersand");
        studyWithEntities.setSponsorName("This has a UTF-8 flower: ❃");
        // not sure our mail system allows these next two, but it shouldn't break document rendering
        studyWithEntities.setTechnicalEmail("дерек@екзампил.ком"); 
        studyWithEntities.setSupportEmail("\"test@test.com\"");
        
        // Without escaping this call throws a SAXParseException. I've verified the HTML preserves all the UTF-8
        // characters and displays correctly. The PDF displays what it can with the font it uses (to display the flower,
        // we'd need to include a font with more UTF-8 characters; we do not).
        studyConsentService.publishConsent(studyWithEntities, subpopulation, view.getCreatedOn());
    }
    
    /**
     * There used to be a test that an InvalidEntityException would be thrown if the content was not valid XML. But
     * Jsoup is very dogged in fixing even the worst documents, as this test demonstrates. Consenquently the validator 
     * just isn't throwing an exception when testing through the service.
     */
    @Test
    public void evenVeryBrokenContentIsFixed() {
        StudyConsentForm form = new StudyConsentForm("</script><div ankle='foo'>This just isn't a SGML-based document no matter how you slice it.</p><h4><img>");
        StudyConsentView view = studyConsentService.addConsent(subpopulation.getGuid(), form);
        assertEquals("<div>\n This just isn't a SGML-based document no matter how you slice it.\n <p></p>\n <h4><img /></h4>\n</div>", view.getDocumentContent());
    }
    
    @Test
    public void publishingConsentCreatesPublicBucketDocuments() throws IOException {
        String content = "<p>"+BridgeUtils.generateGuid()+"</p>";

        StudyConsentForm form = new StudyConsentForm(content);
        StudyConsentView view = studyConsentService.addConsent(subpopulation.getGuid(), form);
        studyConsentService.publishConsent(study, subpopulation, view.getCreatedOn());

        // Now retrieve the HTML version of the document and verify it has been updated.
        // Removing SSL because IOUtils doesn't support it and although we do it, we don't need to.
        String htmlURL = subpopulation.getConsentHTML();
        
        String retrievedContent = IOUtils.toString(new URL(htmlURL).openStream(), Charset.forName("UTF-8"));
        assertTrue(retrievedContent.contains(content));
    }
    
    @Test
    public void getActiveConsentUsesSubpopulation() {
        String documentContent = "<p>This is a consent document.</p>";
        StudyConsentForm form = new StudyConsentForm(documentContent);
        StudyConsentView view = studyConsentService.addConsent(subpopulation.getGuid(), form);        
        studyConsentService.publishConsent(study, subpopulation, view.getCreatedOn());

        view = studyConsentService.getActiveConsent(subpopulation);
        assertEquals(subpopulation.getPublishedConsentCreatedOn(), view.getCreatedOn());
    }
    
    @Test
    public void getActiveConsentWorksWithoutSubpopulation() {
        StudyConsentForm form = new StudyConsentForm("<p>This is a consent document.</p>");
        StudyConsentView view = studyConsentService.addConsent(subpopulation.getGuid(), form);        
        studyConsentService.publishConsent(study, subpopulation, view.getCreatedOn());
        
        assertTrue(subpopulation.getPublishedConsentCreatedOn() > 0L);
        view = studyConsentService.getActiveConsent(subpopulation);
        assertEquals(subpopulation.getPublishedConsentCreatedOn(), view.getCreatedOn());
    }
    
    @Test
    public void publishConsentUpdatesSubpopulation() {
        String documentContent = "<p>This is a consent document.</p>";
        StudyConsentForm form = new StudyConsentForm(documentContent);
        StudyConsentView view = studyConsentService.addConsent(subpopulation.getGuid(), form);

        studyConsentService.publishConsent(study, subpopulation, view.getCreatedOn());
        assertEquals(subpopulation.getPublishedConsentCreatedOn(), view.getCreatedOn());
    }
    
    //- verify the get* methods all continue to work even with a subpopulation that has 0L timestamp

}