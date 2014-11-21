package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.studies.Study;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.stormpath.sdk.client.Client;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class StudyServiceImplTest {

    @Resource
    StudyServiceImpl studyService;
    
    @Resource
    Client stormpathClient;
    
    private Study study;
    
    private String identifier;
    
    @After
    public void after() {
        if (identifier != null) {
            studyService.deleteStudy(identifier);
        }
    }
    
    @Test(expected=InvalidEntityException.class)
    public void invalidIdentifier() {
        study = new DynamoStudy();
        study.setName("Belgian Waffles [Test]");
        study = studyService.createStudy(study);
    }
    
    @Test(expected=InvalidEntityException.class)
    public void invalidName() {
        study = new DynamoStudy();
        study.setIdentifier("belgium");
        study = studyService.createStudy(study);
    }
    
    @Test(expected=InvalidEntityException.class)
    public void invalidIdentifierOfOnlyOneLetter() {
        study = new DynamoStudy();
        study.setIdentifier("a");
        study = studyService.createStudy(study);
    }
    
    @Test
    public void cannotCreateAnExistingStudyWithAVersion() {
        identifier = TestUtils.randomName();
        study = new DynamoStudy();
        study.setIdentifier(identifier);
        study.setName("Belgium Waffles [Test]");
        study = studyService.createStudy(study);
        try {
            study = studyService.createStudy(study);
            fail("Should have thrown an exception");
        } catch(EntityAlreadyExistsException e) {
        }
    }
    
    @Test(expected=EntityAlreadyExistsException.class)
    public void cannotCreateAStudyWithAVersion() {
        study = new DynamoStudy();
        study.setIdentifier("sage-test");
        study.setName("Test of study creation");
        study.setVersion(1L);
        study = studyService.createStudy(study);
    }
    
    @Test
    public void crudStudy() {
        identifier = TestUtils.randomName();
        study = new DynamoStudy();
        study.setIdentifier(identifier);
        study.setName("Test of study creation");
        study.setMaxNumOfParticipants(100);
        study.setMinAgeOfConsent(18);
        study.setResearcherRole(identifier+"_researcher");
        study.setStormpathHref("http://dev-test-junk");
        study.setHostname("dev-hostname-test-junk");

        study = studyService.createStudy(study);
        assertNotNull("Version has been set", study.getVersion());
        
        study = studyService.getStudyByIdentifier(identifier);
        assertEquals(identifier, study.getIdentifier());
        assertEquals("Test of study creation", study.getName());
        assertEquals(100, study.getMaxNumOfParticipants());
        assertEquals(18, study.getMinAgeOfConsent());
        // these should have been changed
        assertEquals(identifier+"_researcher", study.getResearcherRole());
        assertNotEquals("http://local-test-junk", study.getStormpathHref());
        assertNotEquals("local-hostname-test-junk", study.getHostname());

        studyService.deleteStudy(identifier);
        try {
            studyService.getStudyByIdentifier(study.getIdentifier());
            fail("Should have thrown an exception");
        } catch(EntityNotFoundException e) {
        }
        identifier = null;
    }

}
