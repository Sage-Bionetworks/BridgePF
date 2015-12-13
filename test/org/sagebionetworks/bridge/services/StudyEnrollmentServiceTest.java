package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import javax.annotation.Resource;

import org.junit.Test;

import org.sagebionetworks.bridge.TestUserAdminHelper;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.TestUserAdminHelper.TestUser;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.redis.JedisOps;
import org.sagebionetworks.bridge.redis.RedisKey;

import com.google.common.collect.Lists;

public class StudyEnrollmentServiceTest {
    
    private static final String NUM_PARTICIPANTS_KEY = RedisKey.NUM_OF_PARTICIPANTS.getRedisKey("test");
    
    @Resource
    private StudyEnrollmentService studyEnrollmentService;
    
    @Resource
    private ConsentService userConsentService;
    
    @Resource
    private StudyService studyService;
    
    @Resource
    private JedisOps jedisOps;

    @Resource
    private SubpopulationService subpopService;
    
    @Resource
    private TestUserAdminHelper helper;
    
    @Test
    public void enforcesStudyEnrollmentLimit() {
        User user = new User();
        user.setConsentStatuses(
            Lists.newArrayList(new ConsentStatus("Consent", "guid", true, true, true)));
        
        try {
            jedisOps.del(NUM_PARTICIPANTS_KEY);
            
            Study study = TestUtils.getValidStudy(ConsentServiceImplTest.class);
            study.setIdentifier("test");
            study.setMaxNumOfParticipants(2);

            // Set the cache so we avoid going to DynamoDB. We're testing the caching layer
            // in the service test, we'll test the DAO in the DAO test.
            jedisOps.del(NUM_PARTICIPANTS_KEY);

            boolean limit = studyEnrollmentService.isStudyAtEnrollmentLimit(study);
            assertFalse("No limit reached", limit);
            studyEnrollmentService.incrementStudyEnrollment(study, user);
            studyEnrollmentService.incrementStudyEnrollment(study, user);
            assertTrue("Limit reached", studyEnrollmentService.isStudyAtEnrollmentLimit(study));
            assertEquals("2", jedisOps.get(NUM_PARTICIPANTS_KEY));
        } finally {
            jedisOps.del(NUM_PARTICIPANTS_KEY);
        }
    }
    
    @Test
    public void studyEnrollmentNotIncrementedOnSubsequentConsents() {
        User user = new User();
        user.setConsentStatuses(
            Lists.newArrayList(new ConsentStatus("Consent", "guid", true, true, true)));
        
        try {
            jedisOps.del(NUM_PARTICIPANTS_KEY);
            jedisOps.setnx(NUM_PARTICIPANTS_KEY, "0");
            
            Study study = TestUtils.getValidStudy(ConsentServiceImplTest.class);
            study.setIdentifier("test");
            study.setMaxNumOfParticipants(2);

            studyEnrollmentService.incrementStudyEnrollment(study, user);
            assertFalse(studyEnrollmentService.isStudyAtEnrollmentLimit(study));
            
            // On subsequent consents, user is not added and enrollment is not increased
            user.setConsentStatuses(Lists.newArrayList(
                new ConsentStatus("Consent", "guid", true, true, true),
                new ConsentStatus("Consent", "guid2", true, true, true)
            ));
            studyEnrollmentService.incrementStudyEnrollment(study, user);
            studyEnrollmentService.incrementStudyEnrollment(study, user);
            studyEnrollmentService.incrementStudyEnrollment(study, user);
            assertFalse(studyEnrollmentService.isStudyAtEnrollmentLimit(study));
            assertEquals("1", jedisOps.get(NUM_PARTICIPANTS_KEY));
        } finally {
            jedisOps.del(NUM_PARTICIPANTS_KEY);
        }
    }
    
    @Test
    public void decrementingStudyWorks() {
        User user = new User();
        user.setConsentStatuses(
            Lists.newArrayList(new ConsentStatus("Consent", "guid", true, false, true)));
        
        try {
            jedisOps.del(NUM_PARTICIPANTS_KEY);
            jedisOps.setnx(NUM_PARTICIPANTS_KEY, "2");
            
            Study study = TestUtils.getValidStudy(ConsentServiceImplTest.class);
            study.setIdentifier("test");
            study.setMaxNumOfParticipants(2);

            studyEnrollmentService.decrementStudyEnrollment(study, user);
            assertFalse(studyEnrollmentService.isStudyAtEnrollmentLimit(study));
            
            studyEnrollmentService.decrementStudyEnrollment(study, user);
            studyEnrollmentService.decrementStudyEnrollment(study, user);
            studyEnrollmentService.decrementStudyEnrollment(study, user);
            studyEnrollmentService.decrementStudyEnrollment(study, user);
            assertFalse(studyEnrollmentService.isStudyAtEnrollmentLimit(study));
            assertEquals("0", jedisOps.get(NUM_PARTICIPANTS_KEY));
        } finally {
            jedisOps.del(NUM_PARTICIPANTS_KEY);
        }        
    }
    
    @Test
    public void studyEnrollmentNotDecrementedUntilLastWithdrawal() {
        User user = new User();
        user.setConsentStatuses(
            Lists.newArrayList(new ConsentStatus("Consent", "guid", true, true, true)));
        
        try {
            jedisOps.del(NUM_PARTICIPANTS_KEY);
            jedisOps.setnx(NUM_PARTICIPANTS_KEY, "2");
            
            Study study = TestUtils.getValidStudy(ConsentServiceImplTest.class);
            study.setIdentifier("test");
            study.setMaxNumOfParticipants(2);

            // With a consent, this does not decrement
            studyEnrollmentService.decrementStudyEnrollment(study, user);
            assertEquals("2", jedisOps.get(NUM_PARTICIPANTS_KEY));
            
            // With one consent not signed, this will decrement.
            user.setConsentStatuses(
                    Lists.newArrayList(new ConsentStatus("Consent", "guid", true, false, true)));
            studyEnrollmentService.decrementStudyEnrollment(study, user);
            assertEquals("1", jedisOps.get(NUM_PARTICIPANTS_KEY));
        } finally {
            jedisOps.del(NUM_PARTICIPANTS_KEY);
        }           
    }
    
    @Test
    public void getNumberOfParticipants() {
        Study study = TestUtils.getValidStudy(ConsentServiceImplTest.class);
        study = studyService.createStudy(study);
        
        Subpopulation subpop1 = Subpopulation.create();
        subpop1.setName("Group 1");
        subpopService.createSubpopulation(study, subpop1);
        
        Subpopulation subpop2 = Subpopulation.create();
        subpop2.setName("Group 2");
        subpopService.createSubpopulation(study, subpop2);
        
        TestUserAdminHelper.Builder builder = helper.getBuilder(StudyServiceImplTest.class).withStudy(study)
                .withConsent(true);
        
        TestUser user1 = builder.withSubpopulation(subpop1).build();
        TestUser user2 = builder.withSubpopulation(subpop1).build();
        TestUser user3 = builder.withSubpopulation(subpop1).build();
        // and user2 is also in the other subpop2, but will still be counted correctly.
        userConsentService.consentToResearch(study, subpop2, user2.getUser(), 
                new ConsentSignature.Builder().withBirthdate("1980-01-01").withName("Name").build(), SharingScope.NO_SHARING, false);
        try {

            long count = studyEnrollmentService.getNumberOfParticipants(study);
            assertEquals(3, count);
            
        } finally {
            helper.deleteUser(user1);
            helper.deleteUser(user2);
            helper.deleteUser(user3);
            studyService.deleteStudy(study.getIdentifier());
        }
    }

}
