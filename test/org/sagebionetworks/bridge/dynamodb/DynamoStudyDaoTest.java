package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.Set;

import javax.annotation.Resource;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.studies.Study;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoStudyDaoTest {

    private final Set<String> EXTRA_USER_PROFILE_ATTRIBUTES = Sets.newHashSet("can-publish", "can-recontact");
    
    @Resource
    DynamoStudyDao studyDao;
    
    @Before
    public void before() {
        DynamoInitializer.init(DynamoStudy.class);
        // We need to leave the test study in the database.
        List<Study> studies = studyDao.getStudies();
        for (Study study : studies) {
            if (!"api".equals(study.getIdentifier())) {
                studyDao.deleteStudy(study.getIdentifier());
            }
        }
    }
    
    @Test
    public void crudOneStudy() {
        Study study = createStudy();
        
        study = studyDao.createStudy(study);
        assertNotNull("Study was assigned a version", study.getVersion());
        
        study.setName("This is a test name");
        study.setMaxNumOfParticipants(10);
        study = studyDao.updateStudy(study);
        
        study = studyDao.getStudy(study.getIdentifier());
        assertEquals("Name was set", "This is a test name", study.getName());
        assertEquals("Max participants was set", 10, study.getMaxNumOfParticipants());
        assertNotNull("Study deployment was set", study.getStormpathHref());
        assertEquals("support@test.com", study.getSupportEmail());
        assertEquals("consent-notification@test.com", study.getConsentNotificationEmail());
        assertEquals(EXTRA_USER_PROFILE_ATTRIBUTES, study.getUserProfileAttributes());

        String identifier = study.getIdentifier();
        studyDao.deleteStudy(study.getIdentifier());
        try {
            study = studyDao.getStudy(identifier);
            fail("Should have thrown EntityNotFoundException");
        } catch(EntityNotFoundException e) {
        }
    }
    
    @Test
    public void canRetrieveAllStudies() {
        List<String> identifiers = Lists.newArrayList();
        try {
            identifiers.add(studyDao.createStudy(createStudy()).getIdentifier());
            identifiers.add(studyDao.createStudy(createStudy()).getIdentifier());
            identifiers.add(studyDao.createStudy(createStudy()).getIdentifier());
            identifiers.add(studyDao.createStudy(createStudy()).getIdentifier());
            identifiers.add(studyDao.createStudy(createStudy()).getIdentifier());
            
            List<Study> studies = studyDao.getStudies();
            // The five studies, plus the API study we refuse to delete...
            assertEquals("There are six studies", 6, studies.size());
            
        } finally {
            for (String identifier : identifiers) {
                studyDao.deleteStudy(identifier);
            }
        }
        List<Study> studies = studyDao.getStudies();
        assertEquals("There should be only one study", 1, studies.size());
        assertEquals("That should be the test study study", "api", studies.get(0).getIdentifier());
    }
    
    @Test
    public void willNotSaveTwoStudiesWithSameIdentifier() {
        Study study = null;
        try {
            study = createStudy();
            study = studyDao.createStudy(study);
            study.setVersion(null);
            studyDao.createStudy(study);
            fail("Should have thrown entity exists exception");
        } catch(EntityAlreadyExistsException e) {
            
        } finally {
            studyDao.deleteStudy(study.getIdentifier());
        }
    }
    
    @Test(expected=EntityAlreadyExistsException.class)
    public void identifierUniquenessEnforcedByVersionChecks() throws Exception {
        Study study = createStudy();
        studyDao.createStudy(study);
        
        study.setVersion(null); // This is now a "new study"
        studyDao.createStudy(study);
    }
    
    private Study createStudy() {
        Study study = new DynamoStudy();
        study.setIdentifier(TestUtils.randomName());
        study.setMaxNumOfParticipants(100);
        study.setMinAgeOfConsent(18);
        study.setName(TestUtils.randomName());
        study.setResearcherRole(study.getIdentifier()+"_researcher");
        study.setStormpathHref("http://test/local/");
        study.setSupportEmail("support@test.com");
        study.setConsentNotificationEmail("consent-notification@test.com");
        study.setUserProfileAttributes(EXTRA_USER_PROFILE_ATTRIBUTES);
        return study;
    }
    
}
