package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUserAdminHelper;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.TestUserAdminHelper.TestUser;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.redis.JedisOps;
import org.sagebionetworks.bridge.redis.RedisKey;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class StudyEnrollmentServiceTest {
    
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

    private Study study;
    
    private String numParticipantsKey;
    
    @After
    public void after() {
        jedisOps.del(numParticipantsKey);
        subpopService.deleteAllSubpopulations(study.getStudyIdentifier());
    }
    
    @Test
    public void enforcesStudyEnrollmentLimit() {
        UserSession session = new UserSession(null);
        session.setConsentStatuses(TestUtils.toMap(TestConstants.REQUIRED_SIGNED_CURRENT));
        
        study = TestUtils.getValidStudy(ConsentServiceTest.class);
        study.setMaxNumOfParticipants(2);
        numParticipantsKey = RedisKey.NUM_OF_PARTICIPANTS.getRedisKey(study.getIdentifier());

        boolean limit = studyEnrollmentService.isStudyAtEnrollmentLimit(study);
        assertFalse("No limit reached", limit);
        studyEnrollmentService.incrementStudyEnrollment(study, session);
        studyEnrollmentService.incrementStudyEnrollment(study, session);
        assertTrue("Limit reached", studyEnrollmentService.isStudyAtEnrollmentLimit(study));
        assertEquals("2", jedisOps.get(numParticipantsKey));
    }
    
    @Test
    public void studyEnrollmentNotIncrementedOnSubsequentConsents() {
        UserSession session = new UserSession(null);
        session.setConsentStatuses(TestUtils.toMap(TestConstants.REQUIRED_SIGNED_CURRENT));
        
        study = TestUtils.getValidStudy(ConsentServiceTest.class);
        study.setMaxNumOfParticipants(2);
        numParticipantsKey = RedisKey.NUM_OF_PARTICIPANTS.getRedisKey(study.getIdentifier());
        
        studyEnrollmentService.incrementStudyEnrollment(study, session);
        assertFalse(studyEnrollmentService.isStudyAtEnrollmentLimit(study));
        
        ConsentStatus consent2 = new ConsentStatus.Builder().withName("Consent")
                .withGuid(SubpopulationGuid.create("guid2")).withConsented(true).withRequired(true)
                .withSignedMostRecentConsent(true).build();
        // On subsequent consents, user is not added and enrollment is not increased
        
        Map<SubpopulationGuid, ConsentStatus> map = TestUtils.toMap(TestConstants.REQUIRED_SIGNED_CURRENT, consent2);
        session.setConsentStatuses(map);
        studyEnrollmentService.incrementStudyEnrollment(study, session);
        studyEnrollmentService.incrementStudyEnrollment(study, session);
        studyEnrollmentService.incrementStudyEnrollment(study, session);
        assertFalse(studyEnrollmentService.isStudyAtEnrollmentLimit(study));
        assertEquals("1", jedisOps.get(numParticipantsKey));
    }
    
    @Test
    public void decrementingStudyWorks() {
        UserSession session = new UserSession(null);
        session.setConsentStatuses(TestUtils.toMap(TestConstants.REQUIRED_UNSIGNED));
        
        study = TestUtils.getValidStudy(ConsentServiceTest.class);
        study.setMaxNumOfParticipants(2);
        numParticipantsKey = RedisKey.NUM_OF_PARTICIPANTS.getRedisKey(study.getIdentifier());
        
        jedisOps.del(numParticipantsKey);
        jedisOps.setnx(numParticipantsKey, "2");

        studyEnrollmentService.decrementStudyEnrollment(study, session);
        assertFalse(studyEnrollmentService.isStudyAtEnrollmentLimit(study));
        
        studyEnrollmentService.decrementStudyEnrollment(study, session);
        studyEnrollmentService.decrementStudyEnrollment(study, session);
        studyEnrollmentService.decrementStudyEnrollment(study, session);
        studyEnrollmentService.decrementStudyEnrollment(study, session);
        assertFalse(studyEnrollmentService.isStudyAtEnrollmentLimit(study));
        assertEquals("0", jedisOps.get(numParticipantsKey));
    }
    
    @Test
    public void studyEnrollmentNotDecrementedUntilLastWithdrawal() {
        UserSession session = new UserSession(null);
        session.setConsentStatuses(TestUtils.toMap(TestConstants.REQUIRED_SIGNED_CURRENT));
        
        study = TestUtils.getValidStudy(ConsentServiceTest.class);
        study.setMaxNumOfParticipants(2);
        numParticipantsKey = RedisKey.NUM_OF_PARTICIPANTS.getRedisKey(study.getIdentifier());
        
        jedisOps.del(numParticipantsKey);
        jedisOps.setnx(numParticipantsKey, "2");
        
        // With a signed consent, this does not decrement, because user is still in study
        studyEnrollmentService.decrementStudyEnrollment(study, session);
        assertEquals("2", jedisOps.get(numParticipantsKey));
        
        // With no signed consents, this will decrement.
        session.setConsentStatuses(TestUtils.toMap(TestConstants.REQUIRED_UNSIGNED));
        studyEnrollmentService.decrementStudyEnrollment(study, session);
        assertEquals("1", jedisOps.get(numParticipantsKey));
    }
    
    @Test
    public void getNumberOfParticipants() {
        study = TestUtils.getValidStudy(ConsentServiceTest.class);
        study.setExternalIdValidationEnabled(false);
        study = studyService.createStudy(study);
        numParticipantsKey = RedisKey.NUM_OF_PARTICIPANTS.getRedisKey(study.getIdentifier());
        
        Subpopulation subpop1 = Subpopulation.create();
        subpop1.setName("Group 1");
        subpopService.createSubpopulation(study, subpop1);
        
        Subpopulation subpop2 = Subpopulation.create();
        subpop2.setName("Group 2");
        subpopService.createSubpopulation(study, subpop2);
        
        TestUserAdminHelper.Builder builder = helper.getBuilder(StudyServiceTest.class).withStudy(study)
                .withConsent(true);
        
        TestUser user1 = builder.withGuid(subpop1.getGuid()).build();
        TestUser user2 = builder.withGuid(subpop1.getGuid()).build();
        TestUser user3 = builder.withGuid(subpop1.getGuid()).build();
        // and user2 is also in the other subpop2, but will still be counted correctly.
        userConsentService.consentToResearch(study, subpop2.getGuid(), user2.getSession(), 
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
