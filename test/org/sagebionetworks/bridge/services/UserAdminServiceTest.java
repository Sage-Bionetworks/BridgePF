package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.sagebionetworks.bridge.TestConstants.TEST_CONTEXT;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;

import java.util.List;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dynamodb.DynamoExternalIdentifier;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.google.common.collect.Lists;

import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class UserAdminServiceTest {

    // Decided not to use the helper class for this test because so many edge conditions are
    // being tested here.

    @Resource
    AuthenticationService authService;

    @Resource
    BridgeConfig bridgeConfig;

    @Resource
    StudyService studyService;

    @Resource
    UserAdminService userAdminService;
    
    @Resource
    ExternalIdService externalIdService;
    
    @Resource(name = "externalIdDdbMapper")
    private DynamoDBMapper mapper;
    
    private Study study;

    private StudyParticipant participant;

    private UserSession session;

    @Before
    public void before() {
        study = studyService.getStudy(TEST_STUDY_IDENTIFIER);
        String email = TestUtils.makeRandomTestEmail(UserAdminServiceTest.class);
        participant = new StudyParticipant.Builder().withEmail(email).withPassword("P4ssword!").build();
    }

    @After
    public void after() {
        if (session != null) {
            userAdminService.deleteUser(study, session.getId());
        }
    }

    @Test
    public void deletedUserHasBeenDeleted() {
        session = userAdminService.createUser(study, participant, null, true, true);

        userAdminService.deleteUser(study, session.getId());
        session = null;

        // This should fail with a 404.
        try {
            authService.signIn(study, TEST_CONTEXT, new SignIn(participant.getEmail(), participant.getPassword()));
            fail("Should have thrown exception");
        } catch(EntityNotFoundException e) {
            
        }
    }

    @Test
    public void canCreateConsentedAndSignedInUser() {
        session = userAdminService.createUser(study, participant, null, true, true);
        
        assertTrue(session.isAuthenticated());
        assertTrue(session.doesConsent());
        for(ConsentStatus status : session.getConsentStatuses().values()) {
            assertTrue(status.isConsented());
        }
    }
    
    @Test
    public void canCreateUserWithoutConsentingOrSigningUserIn() {
        UserSession session = userAdminService.createUser(study, participant, null, false, false);
        assertFalse(session.isAuthenticated());

        session = authService.signIn(study, TEST_CONTEXT,
                new SignIn(participant.getEmail(), participant.getPassword()));
        assertFalse(session.doesConsent());
    }

    @Test
    public void cannotCreateUserWithSameEmail() {
        session = userAdminService.createUser(study, participant, null, true, false);
        try {
            userAdminService.createUser(study, participant, null, false, false);
            fail("Sign up with email already in use should throw an exception");
        } catch(EntityAlreadyExistsException e) { 
            assertEquals("Account already exists.", e.getMessage());
        }
    }

    @Test
    public void testDeleteUserWhenSignedOut() {
        session = userAdminService.createUser(study, participant, null, true, true);
        authService.signOut(session);
        assertNull(authService.getSession(session.getSessionToken()));
        // Shouldn't crash
        userAdminService.deleteUser(study, session.getId());
        assertNull(authService.getSession(session.getSessionToken()));
        session = null;
    }

    @Test
    public void testDeleteUserThatHasBeenDeleted() {
        session = userAdminService.createUser(study, participant, null, true, true);
        userAdminService.deleteUser(study, session.getId());
        assertNull(authService.getSession(session.getSessionToken()));
        // Delete again shouldn't crash
        userAdminService.deleteUser(study, session.getId());
        assertNull(authService.getSession(session.getSessionToken()));
        session = null;
    }
    
    @Test
    public void creatingUserThenDeletingRemovesExternalIdAssignment() {
        List<String> idForTest = Lists.newArrayList("AAA");
        externalIdService.addExternalIds(study, idForTest);
        try {
            session = userAdminService.createUser(study, participant, null, true, true);
            study.setExternalIdValidationEnabled(true);
            
            externalIdService.assignExternalId(study, "AAA", session.getHealthCode());

            DynamoExternalIdentifier identifier = getDynamoExternalIdentifier(session);
            assertEquals(session.getHealthCode(), identifier.getHealthCode());
            
            // Now delete the user, and the assignment should then be free;
            userAdminService.deleteUser(study, session.getId());
            
            identifier = getDynamoExternalIdentifier(session);
            assertNull(identifier.getHealthCode());
            
            // Now this works
            externalIdService.assignExternalId(study, "AAA", session.getHealthCode());
        } finally {
            session = null;
            // this is a cheat, for sure, but allow deletion
            study.setExternalIdValidationEnabled(false);
            externalIdService.deleteExternalIds(study, idForTest);
        }
    }

    private DynamoExternalIdentifier getDynamoExternalIdentifier(UserSession session) {
        DynamoExternalIdentifier keyObject = new DynamoExternalIdentifier(study.getStudyIdentifier(), "AAA");
        return mapper.load(keyObject);
    }
}
