package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.TEST_CONTEXT;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;
import static org.sagebionetworks.bridge.dao.ParticipantOption.DATA_GROUPS;
import static org.sagebionetworks.bridge.dao.ParticipantOption.LANGUAGES;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Resource;

import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import org.sagebionetworks.bridge.DefaultStudyBootstrapper;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestUserAdminHelper;
import org.sagebionetworks.bridge.TestUserAdminHelper.TestUser;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.dao.UserConsentDao;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.Email;
import org.sagebionetworks.bridge.models.accounts.EmailVerification;
import org.sagebionetworks.bridge.models.accounts.HealthId;
import org.sagebionetworks.bridge.models.accounts.PasswordReset;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.SignUp;
import org.sagebionetworks.bridge.models.accounts.UserConsent;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.accounts.UserSessionInfo;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.stormpath.StormpathAccount;

import com.google.common.collect.Sets;
import com.stormpath.sdk.directory.CustomData;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class AuthenticationServiceTest {
    
    @Resource
    private CacheProvider cacheProvider;

    @Resource
    private AuthenticationService authService;

    @Resource
    private ParticipantOptionsService optionsService;
    
    @Resource
    private AccountDao accountDao;
    
    @Resource
    private HealthCodeService healthCodeService;
    
    @Resource
    private StudyService studyService;

    @Resource
    private TestUserAdminHelper helper;
    
    @Resource
    private UserConsentDao userConsentDao;
    
    @Resource
    private SubpopulationService subpopService;
    
    private TestUser makeTestUser() {
        return helper.getBuilder(AuthenticationServiceTest.class).build();
    }

    @Test(expected = BridgeServiceException.class)
    public void signInNoEmail() throws Exception {
        TestUser testUser = makeTestUser();
        try {
            authService.signIn(testUser.getStudy(), TEST_CONTEXT, new SignIn(null, "bar"));    
        } finally {
            helper.deleteUser(testUser);
        }
    }

    @Test(expected = BridgeServiceException.class)
    public void signInNoPassword() throws Exception {
        TestUser testUser = makeTestUser();
        try {
            authService.signIn(testUser.getStudy(), TEST_CONTEXT, new SignIn("foobar", null));
        } finally {
            helper.deleteUser(testUser);
        }
    }

    @Test(expected = EntityNotFoundException.class)
    public void signInInvalidCredentials() throws Exception {
        TestUser testUser = makeTestUser();
        try {
            authService.signIn(testUser.getStudy(), TEST_CONTEXT, new SignIn("foobar", "bar"));
        } finally {
            helper.deleteUser(testUser);
        }
    }

    @Test
    public void signInCorrectCredentials() throws Exception {
        TestUser testUser = makeTestUser();
        try {
            UserSession newSession = authService.getSession(testUser.getSessionToken());
            assertEquals("Email is for test2 user", newSession.getUser().getEmail(), testUser.getEmail());
            assertTrue("Session token has been assigned", StringUtils.isNotBlank(testUser.getSessionToken()));
        } finally {
            helper.deleteUser(testUser);
        }
    }

    @Test
    public void signInWhenSignedIn() throws Exception {
        TestUser testUser = makeTestUser();
        try {
            String sessionToken = testUser.getSessionToken();
            UserSession newSession = authService.signIn(testUser.getStudy(), TEST_CONTEXT, testUser.getSignIn());
            assertEquals("Email is for test2 user", testUser.getEmail(), newSession.getUser().getEmail());
            assertEquals("Should update the existing session instead of creating a new one.",
                    sessionToken, newSession.getSessionToken());
        } finally {
            helper.deleteUser(testUser);
        }
    }

    @Test
    public void signInSetsSharingScope() { 
        TestUser testUser = makeTestUser();
        try {
            UserSession newSession = authService.signIn(testUser.getStudy(), TEST_CONTEXT, testUser.getSignIn());
            assertEquals(SharingScope.NO_SHARING, newSession.getUser().getSharingScope()); // this is the default.
        } finally {
            helper.deleteUser(testUser);
        }
    }

    @Test
    public void getSessionWithBogusSessionToken() throws Exception {
        UserSession session = authService.getSession("anytoken");
        assertNull("Session is null", session);

        session = authService.getSession(null);
        assertNull("Session is null", session);
    }

    @Test
    public void getSessionWhenAuthenticated() throws Exception {
        TestUser testUser = makeTestUser();
        try {
            UserSession newSession = authService.getSession(testUser.getSessionToken());

            assertEquals("Email is for test2 user", testUser.getEmail(), newSession.getUser().getEmail());
            assertTrue("Session token has been assigned", StringUtils.isNotBlank(newSession.getSessionToken()));
        } finally {
            helper.deleteUser(testUser);
        }
    }

    @Test(expected = NullPointerException.class)
    public void requestPasswordResetFailsOnNull() throws Exception {
        TestUser testUser = makeTestUser();
        try {
            authService.requestResetPassword(testUser.getStudy(), null);
        } finally {
            helper.deleteUser(testUser);
        }
    }

    @Test(expected = InvalidEntityException.class)
    public void requestPasswordResetFailsOnEmptyString() throws Exception {
        TestUser testUser = makeTestUser();
        try {
            Email email = new Email(testUser.getStudyIdentifier(), "");
            authService.requestResetPassword(testUser.getStudy(), email);
        } finally {
            helper.deleteUser(testUser);
        }
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void resetPasswordWithBadTokenFails() throws Exception {
        authService.resetPassword(new PasswordReset("newpassword", "resettoken"));
    }

    @Test
    public void canResendEmailVerification() throws Exception {
        TestUser testUser = makeTestUser();
        try {
            TestUser user = helper.getBuilder(AuthenticationServiceTest.class)
                    .withConsent(false).withSignIn(false).build();
            try {
                Email email = new Email(testUser.getStudyIdentifier(), user.getEmail());
                authService.resendEmailVerification(user.getStudyIdentifier(), email);
            } finally {
                helper.deleteUser(user);
            }
        } finally {
            helper.deleteUser(testUser);
        }
    }

    @Test
    public void createResearcherAndSignInWithoutConsentError() {
        TestUser researcher = helper.getBuilder(AuthenticationServiceTest.class)
                .withConsent(false).withSignIn(false).withRoles(Roles.RESEARCHER).build();
        try {
            authService.signIn(researcher.getStudy(), TEST_CONTEXT, researcher.getSignIn());
            // no exception should have been thrown.
        } finally {
            helper.deleteUser(researcher);
        }
    }

    @Test
    public void createAdminAndSignInWithoutConsentError() {
        TestUser researcher = helper.getBuilder(AuthenticationServiceTest.class)
                .withConsent(false).withSignIn(false).withRoles(Roles.ADMIN).build();
        try {
            authService.signIn(researcher.getStudy(), TEST_CONTEXT, researcher.getSignIn());
            // no exception should have been thrown.
        } finally {
            helper.deleteUser(researcher);
        }
    }

    @Test
    public void testSignOut() {
        TestUser testUser = makeTestUser();
        try {
            final String sessionToken = testUser.getSessionToken();
            final String userId = testUser.getUser().getId();
            authService.signOut(testUser.getSession());
            assertNull(cacheProvider.getUserSession(sessionToken));
            assertNull(cacheProvider.getUserSessionByUserId(userId));
        } finally {
            helper.deleteUser(testUser);
        }
    }

    @Test
    public void testSignOutWhenSignedOut() {
        TestUser testUser = makeTestUser();
        try {
            final String sessionToken = testUser.getSessionToken();
            final String userId = testUser.getUser().getId();
            authService.signOut(testUser.getSession());
            authService.signOut(testUser.getSession());
            assertNull(cacheProvider.getUserSession(sessionToken));
            assertNull(cacheProvider.getUserSessionByUserId(userId));
        } finally {
            helper.deleteUser(testUser);
        }
    }
    
    @Test
    public void signUpWillCreateDataGroups() {
        authService = spy(authService);
        optionsService = spy(optionsService);
        authService.setOptionsService(optionsService);
        
        Study study = studyService.getStudy(TEST_STUDY_IDENTIFIER);
        
        Set<String> list = Sets.newHashSet("group1");
        String name = TestUtils.randomName(AuthenticationServiceTest.class);
        String email = "bridge-testing+"+name+"@sagebase.org";
        
        try {
            SignUp signUp = new SignUp(email, "P@ssword1", null, list);

            authService.signUp(study, signUp, true);
            Account account = accountDao.getAccount(study, email);
            HealthId healthId = healthCodeService.getMapping(account.getHealthId());
            
            verify(authService).signUp(study, signUp, true);
            // Verify that data groups were set correctly as an option
            Set<String> persistedGroups = optionsService.getOptions(healthId.getCode()).getStringSet(DATA_GROUPS);
            assertEquals(list, persistedGroups);
            
        } finally {
            accountDao.deleteAccount(study, email);
        }
    }
    
    @Test
    public void userCreatedWithDataGroupsHasThemOnSignIn() throws Exception {
        int numOfGroups = DefaultStudyBootstrapper.TEST_DATA_GROUPS.size();
        TestUser user = helper.getBuilder(AuthenticationServiceTest.class).withConsent(true)
                .withDataGroups(DefaultStudyBootstrapper.TEST_DATA_GROUPS).build();
        try {
            UserSession session = authService.signIn(user.getStudy(), TEST_CONTEXT, user.getSignIn());
            // Verify we created a list and the anticipated group was not null
            assertEquals(numOfGroups, session.getUser().getDataGroups().size()); 
            assertEquals(DefaultStudyBootstrapper.TEST_DATA_GROUPS, session.getUser().getDataGroups());
        } finally {
            helper.deleteUser(user);
        }
    }
    
    @Test
    public void invalidDataGroupsAreRejected() throws Exception {
        try {
            Set<String> dataGroups = Sets.newHashSet("bugleboy");
            helper.getBuilder(AuthenticationServiceTest.class).withConsent(false).withSignIn(false)
                    .withDataGroups(dataGroups).build();
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertTrue(e.getMessage().contains("dataGroups 'bugleboy' is not one of these valid values"));
        }
    }
    
    // Account enumeration security. Verify the service is quite (throws no exceptions) when we don't
    // recognize an account.

    @Test
    public void secondSignUpTriggersResetPasswordInstead() {
        // Verify that requestResetPassword is called in this case
        authService = spy(authService);
        
        TestUser user = helper.getBuilder(AuthenticationServiceTest.class)
                .withConsent(false).withSignIn(false).build();
        try {
            authService.signUp(user.getStudy(), user.getSignUp(), true);
            verify(authService).requestResetPassword(any(Study.class), any(Email.class));
        } finally {
            helper.deleteUser(user);
        }
    }
    
    @Test
    public void resendEmailVerificationLooksSuccessfulWhenNoAccount() throws Exception {
        // In particular, it must not throw an EntityNotFoundException
        TestUser user = helper.getBuilder(AuthenticationServiceTest.class)
                .withConsent(false).withSignIn(false).build();
        try {
            Email email = new Email(user.getStudyIdentifier(), "notarealaccount@sagebase.org");
            authService.resendEmailVerification(user.getStudyIdentifier(), email);
        } finally {
            helper.deleteUser(user);
        }
    }
    
    @Test
    public void requestResetPasswordLooksSuccessfulWhenNoAccount() throws Exception {
        TestUser testUser = makeTestUser();
        try {
            // In particular, it must not throw an EntityNotFoundException
            TestUser user = helper.getBuilder(AuthenticationServiceTest.class)
                    .withConsent(false).withSignIn(false).build();
            try {
                Email email = new Email(testUser.getStudyIdentifier(), "notarealaccount@sagebase.org");
                authService.requestResetPassword(testUser.getStudy(), email);
            } finally {
                helper.deleteUser(user);
            }
        } finally {
            helper.deleteUser(testUser);
        }
    }
    
    // Consent statuses passed on to sessionInfo
    
    @Test
    public void consentStatusesPresentInSession() {
        TestUser testUser = makeTestUser();
        try {
            // User is consenting
            TestUser user = helper.getBuilder(AuthenticationServiceTest.class)
                    .withConsent(true).withSignIn(true).build();
            try {
                SubpopulationGuid guid = SubpopulationGuid.create(testUser.getStudyIdentifier().getIdentifier());
                
                // This is the object we pass back to the user, we want to see the statuses copied or present
                // all the way from the user to the sessionInfo. We test elsewhere that these are properly 
                // serialized/deserialized (SubpopulationGuidDeserializer)
                UserSessionInfo sessionInfo = new UserSessionInfo(user.getSession());
                
                ConsentStatus status = sessionInfo.getConsentStatuses().get(guid);
                assertTrue(status.isConsented());
                assertEquals(testUser.getStudyIdentifier().getIdentifier(), status.getSubpopulationGuid());
            } finally {
                helper.deleteUser(user);
            }
        } finally {
            helper.deleteUser(testUser);
        }
    }
    
    @Test
    public void userWithSignatureAndNoDbRecordIsRepaired() throws Exception {
        TestUser testUser = helper.getBuilder(AuthenticationServiceTest.class)
                .withConsent(true).withSignIn(true).build();
        try {
            authService.signOut(testUser.getSession());
            
            // now delete any and all consent records, and zero out the signed on dates
            
            Account account = accountDao.getAccount(testUser.getStudy(), testUser.getEmail());
            
            List<Subpopulation> subpops = subpopService.getSubpopulations(testUser.getStudyIdentifier());
            for (Subpopulation subpop : subpops) {
                // Delete all DDB records
                userConsentDao.deleteAllConsents(testUser.getUser().getHealthCode(), subpop.getGuid());
                
                // zero out the signed on date
                ConsentSignature sig = account.getConsentSignatureHistory(subpop.getGuid()).get(0);
                sig = new ConsentSignature.Builder().withConsentSignature(sig).withSignedOn(0L).build();
                
                // alter the customData object so it stores the one signature and no history, as 
                // was stored in the past when this problem was present
                com.stormpath.sdk.account.Account stormpathAcct = ((StormpathAccount)account).getAccount();
                CustomData data = stormpathAcct.getCustomData();
                data.put(subpop.getGuidString()+"_signature", BridgeObjectMapper.get().writeValueAsString(sig));
                data.put(subpop.getGuidString()+"_signature_version", 2);
                data.remove(subpop.getGuidString()+"_signatures");
                data.remove(subpop.getGuidString()+"_signatures_version");                
            }
            accountDao.updateAccount(testUser.getStudy(), account);
            
            // Signing in should still work to create a consented user.
            Study study = studyService.getStudy(testUser.getStudyIdentifier());
            
            // Create the context you would get for an unauthenticated user, don't use the now 
            // fully-initialized testUser.getCriteriaContext()
            CriteriaContext context =  new CriteriaContext.Builder()
                    .withStudyIdentifier(study.getStudyIdentifier())
                    .withLanguages(TestUtils.newLinkedHashSet("en"))
                    .withClientInfo(ClientInfo.UNKNOWN_CLIENT)
                    .build();
            
            UserSession session = authService.signIn(study, context, testUser.getSignIn());
            
            // and this person should be recorded as consented...
            for (ConsentStatus status : session.getUser().getConsentStatuses().values()) {
                assertTrue(!status.isRequired() || status.isConsented());
                UserConsent consent  = userConsentDao.getActiveUserConsent(session.getUser().getHealthCode(), SubpopulationGuid.create(status.getSubpopulationGuid()));
                assertTrue(consent.getSignedOn() > 0L);
            }
        } finally {
            helper.deleteUser(testUser);
        }
    }
    
    @Test
    public void existingLanguagePreferencesAreLoaded() {
        LinkedHashSet<String> LANGS = TestUtils.newLinkedHashSet("en","es");
        
        TestUser testUser = helper.getBuilder(AuthenticationServiceTest.class)
                .withConsent(true).withSignIn(true).build();
        try {
            String healthCode = testUser.getUser().getHealthCode();
            optionsService.setOrderedStringSet(
                    testUser.getStudyIdentifier(), healthCode, ParticipantOption.LANGUAGES, LANGS);

            authService.signOut(testUser.getSession());
            
            Study study = studyService.getStudy(testUser.getStudyIdentifier());
            CriteriaContext context = testUser.getCriteriaContext();
            
            UserSession session = authService.signIn(study, context, testUser.getSignIn());
            assertEquals(LANGS, session.getUser().getLanguages());
        } finally {
            helper.deleteUser(testUser);
        }
    }
    
    @Test
    public void languagePreferencesAreRetrievedFromContext() {
        LinkedHashSet<String> LANGS = TestUtils.newLinkedHashSet("fr","es");
        
        TestUser testUser = helper.getBuilder(AuthenticationServiceTest.class)
                .withConsent(true).withSignIn(true).build();
        try {
            
            testUser.getUser().setLanguages(LANGS);
            CriteriaContext context = testUser.getCriteriaContext();
            
            authService.signOut(testUser.getSession());
            
            Study study = studyService.getStudy(testUser.getStudyIdentifier());
            
            UserSession session = authService.signIn(study, context, testUser.getSignIn());
            assertEquals(LANGS, session.getUser().getLanguages());
            
            LinkedHashSet<String> persistedLangs = optionsService.getOptions(testUser.getUser().getHealthCode()).getOrderedStringSet(LANGUAGES);
            assertEquals(LANGS, persistedLangs);
        } finally {
            helper.deleteUser(testUser);
        }        
    }
    
    @Test
    public void verifyEmailWorksWithRepairConsents() throws Exception {
        TestUser testUser = helper.getBuilder(AuthenticationServiceTest.class)
                .withConsent(false).withSignIn(false).build();
        try {
            // Calling verifyEmail fails if you don't have a valid sptoken, which we don't have. So we have to mock the 
            // DAO to verify that the call succeeds
            EmailVerification verification = new EmailVerification("asdf");
            Study study = testUser.getStudy();
            String email = testUser.getEmail();
            
            // We need to mock the client because it will throw an exception when it gets the garbage token "asdf", 
            // and we're only concerned with what happens when this is successful. Tedious to mock the Stormpath client.
            AccountDao accountDaoSpy = mock(AccountDao.class);
            when(accountDaoSpy.verifyEmail(study, verification)).thenReturn(accountDao.getAccount(study, email));
            authService.setAccountDao(accountDaoSpy);
            
            CriteriaContext context = new CriteriaContext.Builder().withStudyIdentifier(testUser.getStudyIdentifier()).build();
            
            UserSession session = authService.verifyEmail(study, context, verification);
            // Consents are okay. User hasn't consented.
            ConsentStatus status = session.getUser().getConsentStatuses().values().iterator().next();
            assertFalse(status.isConsented());
            
            // This should not have been altered in any way by the lack of consents.
            verify(accountDaoSpy).verifyEmail(study, verification);
        } finally {
            authService.setAccountDao(accountDao);
            helper.deleteUser(testUser);
        }        
    }
    
}
