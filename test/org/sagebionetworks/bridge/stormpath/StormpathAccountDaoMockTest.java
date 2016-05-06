package org.sagebionetworks.bridge.stormpath;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.google.common.collect.ImmutableSet;
import com.stormpath.sdk.group.Group;
import com.stormpath.sdk.group.GroupList;
import com.stormpath.sdk.impl.account.DefaultAccount;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.crypto.BridgeEncryptor;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.Email;
import org.sagebionetworks.bridge.models.accounts.EmailVerification;
import org.sagebionetworks.bridge.models.accounts.HealthId;
import org.sagebionetworks.bridge.models.accounts.PasswordReset;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.services.HealthCodeService;
import org.sagebionetworks.bridge.services.SubpopulationService;

import com.google.common.collect.Lists;
import com.stormpath.sdk.application.Application;
import com.stormpath.sdk.authc.AuthenticationResult;
import com.stormpath.sdk.client.Client;
import com.stormpath.sdk.directory.CustomData;
import com.stormpath.sdk.directory.Directory;
import com.stormpath.sdk.resource.ResourceException;
import com.stormpath.sdk.tenant.Tenant;

@RunWith(MockitoJUnitRunner.class)
public class StormpathAccountDaoMockTest {

    private static final String PASSWORD = "P4ssword!";
    
    @Mock
    SubpopulationService subpopService;

    @Mock
    Application application;
    
    @Mock
    Directory directory;
    
    @Mock
    Client client;
    
    @Mock
    Tenant tenant;
    
    @Mock
    HealthId healthId;
    
    @Mock
    com.stormpath.sdk.account.Account stormpathAccount;
    
    @Mock
    HealthCodeService healthCodeService;

    @Mock
    AuthenticationResult authResult;
    
    @Mock
    BridgeEncryptor encryptor;
    
    @Mock
    CustomData customData;

    StormpathAccountDao dao;
    
    Study study;
    
    @Before
    public void setUp() {
        study = new DynamoStudy();
        study.setIdentifier("test-study");
        study.setStormpathHref("http://some/dumb.href");
        
        Subpopulation subpop = Subpopulation.create();
        subpop.setGuidString(study.getIdentifier());
        when(subpopService.getSubpopulations(study.getStudyIdentifier())).thenReturn(Lists.newArrayList(subpop));
        
        when(encryptor.decrypt("2")).thenReturn("2");
        when(encryptor.decrypt("healthId")).thenReturn("healthId");
        
        List<BridgeEncryptor> encryptors = Lists.newArrayList();
        encryptors.add(encryptor);
        
        dao = new StormpathAccountDao();
        dao.setStormpathClient(client);
        dao.setSubpopulationService(subpopService);
        dao.setStormpathApplication(application);
        dao.setHealthCodeService(healthCodeService);
        dao.setEncryptors(encryptors);
    }

    @Test
    public void verifyEmail() {
        EmailVerification verification = new EmailVerification("tokenAAA");
        
        when(client.getCurrentTenant()).thenReturn(tenant);
        when(client.verifyAccountEmail("tokenAAA")).thenReturn(stormpathAccount);
        
        when(customData.get("test-study_version")).thenReturn(2);
        when(customData.get("test-study_code")).thenReturn("healthId");
        when(stormpathAccount.getCustomData()).thenReturn(customData);
        when(healthCodeService.getMapping("healthId")).thenReturn(healthId);

        Account account = dao.verifyEmail(study, verification);
        assertNotNull(account);
        verify(client).verifyAccountEmail("tokenAAA");
    }

    @Test
    public void requestResetPassword() {
        String emailString = "bridge-tester+43@sagebridge.org";
        
        when(client.getResource(study.getStormpathHref(), Directory.class)).thenReturn(directory);
        
        dao.requestResetPassword(study, new Email(study.getStudyIdentifier(), emailString));
        
        verify(client).getResource(study.getStormpathHref(), Directory.class);
        verify(application).sendPasswordResetEmail(emailString, directory);
    }
    
    @Test(expected = BridgeServiceException.class)
    public void requestPasswordRequestThrowsException() {
        String emailString = "bridge-tester+43@sagebridge.org";
        
        when(client.getResource(study.getStormpathHref(), Directory.class)).thenReturn(directory);
        
        com.stormpath.sdk.error.Error error = mock(com.stormpath.sdk.error.Error.class);
        ResourceException e = new ResourceException(error);
        when(application.sendPasswordResetEmail(emailString, directory)).thenThrow(e);
        
        dao.requestResetPassword(study, new Email(study.getStudyIdentifier(), emailString));
    }

    @Test
    public void resetPassword() {
        PasswordReset passwordReset = new PasswordReset("password", "sptoken");
        
        dao.resetPassword(passwordReset);
        verify(application).resetPassword(passwordReset.getSptoken(), passwordReset.getPassword());
        verifyNoMoreInteractions(application);
    }
    
    @Test
    public void stormpathAccountCorrectlyInitialized() {
        dao = spy(dao);
        doReturn(false).when(dao).isAccountDirty(any());
        
        when(stormpathAccount.getCustomData()).thenReturn(customData);
        
        when(client.instantiate(com.stormpath.sdk.account.Account.class)).thenReturn(stormpathAccount);
        when(client.getResource(study.getStormpathHref(), Directory.class)).thenReturn(directory);
        
        doReturn(healthId).when(healthCodeService).createMapping(study);
        
        String random = RandomStringUtils.randomAlphabetic(5);
        String email = "bridge-testing+"+random+"@sagebridge.org";
        StudyParticipant participant = new StudyParticipant.Builder().withEmail(email).withPassword(PASSWORD).build();
        
        Account account = dao.constructAccount(study, participant.getEmail(), participant.getPassword());
        assertNotNull(account);
        dao.createAccount(study, account, false);

        ArgumentCaptor<com.stormpath.sdk.account.Account> argument = ArgumentCaptor.forClass(com.stormpath.sdk.account.Account.class);
        verify(directory).createAccount(argument.capture(), anyBoolean());

        com.stormpath.sdk.account.Account acct = argument.getValue();
        verify(acct).setUsername(email);
        verify(acct).setEmail(email);
        verify(acct).setPassword(PASSWORD);
    }
    
    @Test
    public void authenticate() {
        when(client.getResource(study.getStormpathHref(), Directory.class)).thenReturn(directory);

        // mock stormpath account
        when(stormpathAccount.getGivenName()).thenReturn("Test");
        when(stormpathAccount.getSurname()).thenReturn("User");
        when(stormpathAccount.getEmail()).thenReturn("email@email.com");
        
        // mock authentication result
        when(authResult.getAccount()).thenReturn(stormpathAccount);
        
        // mock stormpath application
        when(application.authenticateAccount(any())).thenReturn(authResult);
        
        when(customData.get("test-study_version")).thenReturn(2);
        when(customData.get("test-study_code")).thenReturn("healthId");
        when(stormpathAccount.getCustomData()).thenReturn(customData);
        when(healthCodeService.getMapping("healthId")).thenReturn(healthId);
        
        // authenticate
        Account account = dao.authenticate(study, new SignIn("dummy-user", PASSWORD));
        
        // Just verify a few fields, the full object initialization is tested elsewhere.
        assertEquals("Test", account.getFirstName());
        assertEquals("User", account.getLastName());
        assertEquals("email@email.com", account.getEmail());
        
        // verify eager fetch occurring. Can't verify AuthenticationRequest configuration because 
        // you can set it but settings themselves are hidden in implementation. 
        verify(authResult).getAccount();
        verify(stormpathAccount, times(6)).getCustomData();
        verify(stormpathAccount).getGroups();
    }

    @Test
    public void accountDisabled() {
        // mock stormpath client
        when(client.getResource(study.getStormpathHref(), Directory.class)).thenReturn(directory);

        // mock stormpath application - Don't check the args to Application.authenticateAccount(). This is tested
        // elsewhere.
        com.stormpath.sdk.error.Error mockError = mock(com.stormpath.sdk.error.Error.class);
        when(mockError.getCode()).thenReturn(7101);
        ResourceException spException = new ResourceException(mockError);

        when(application.authenticateAccount(any())).thenThrow(spException);

        // execute and validate
        try {
            dao.authenticate(study, new SignIn("dummy-user", PASSWORD));
            fail("expected exception");
        } catch (BridgeServiceException ex) {
            assertEquals(HttpStatus.SC_LOCKED, ex.getStatusCode());
        }
    }

    @Test
    public void updatingAccountWithNoGroupChanges() {
        Set<String> oldGroupSet = ImmutableSet.of("test_users", "worker");
        Set<Roles> newGroupSet = EnumSet.of(Roles.TEST_USERS, Roles.WORKER);
        com.stormpath.sdk.account.Account mockSpAccount = setupGroupChangeTest(oldGroupSet, newGroupSet);
        verify(mockSpAccount, never()).addGroup(anyString());
        verify(mockSpAccount, never()).removeGroup(anyString());
    }

    @Test
    public void updatingAccountWithAddedGroups() {
        Set<String> oldGroupSet = ImmutableSet.of("test_users", "worker");
        Set<Roles> newGroupSet = EnumSet.of(Roles.RESEARCHER, Roles.TEST_USERS, Roles.WORKER);
        com.stormpath.sdk.account.Account mockSpAccount = setupGroupChangeTest(oldGroupSet, newGroupSet);
        verify(mockSpAccount, times(1)).addGroup("researcher");
        verify(mockSpAccount, times(1)).addGroup(anyString());
        verify(mockSpAccount, never()).removeGroup(anyString());
    }

    @Test
    public void updatingAccountWithRemovedGroups() {
        Set<String> oldGroupSet = ImmutableSet.of("test_users", "worker");
        Set<Roles> newGroupSet = EnumSet.of(Roles.TEST_USERS);
        com.stormpath.sdk.account.Account mockSpAccount = setupGroupChangeTest(oldGroupSet, newGroupSet);
        verify(mockSpAccount, never()).addGroup(anyString());
        verify(mockSpAccount, times(1)).removeGroup("worker");
        verify(mockSpAccount, times(1)).removeGroup(anyString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void getStudyPagedAccountsRejectsPageSizeTooSmall() {
        dao.getPagedAccountSummaries(study, 0, BridgeConstants.API_MINIMUM_PAGE_SIZE-1, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void getStudyPagedAccountsRejectsPageSizeTooLarge() {
        dao.getPagedAccountSummaries(study, 0, BridgeConstants.API_MAXIMUM_PAGE_SIZE+1, null);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void getStudyPagedAccountsRejectsNonsenseOffsetBy() {
        dao.getPagedAccountSummaries(study, -10, BridgeConstants.API_DEFAULT_PAGE_SIZE, null);
    }
    
    @Test
    public void verifyEmailCreatesHealthCode() {
        mockAccountWithoutHealthCode();
        
        doReturn(stormpathAccount).when(client).verifyAccountEmail("spToken");
        EmailVerification emailVerification = new EmailVerification("spToken");
        
        Account account = dao.verifyEmail(study, emailVerification);
        assertEquals("ABC", account.getHealthCode());
        verify(healthCodeService).createMapping(study);
    }
    
    @Test
    public void authenticatedCreatesHealthCode() {
        mockAccountWithoutHealthCode();
        
        SignIn signIn = new SignIn("email@email.com", "password");
        
        AuthenticationResult result = mock(AuthenticationResult.class);
        doReturn(stormpathAccount).when(result).getAccount();
        doReturn(result).when(application).authenticateAccount(any());
        
        Account account = dao.authenticate(study, signIn);
        assertEquals("ABC", account.getHealthCode());
        verify(healthCodeService).createMapping(study);
    }
    
    @Test
    public void getAccountCreatesHealthCode() {
        mockAccountWithoutHealthCode();
        
        Account account = dao.getAccount(study, "id");
        assertEquals("ABC", account.getHealthCode());
        verify(healthCodeService).createMapping(study);
    }

    private void mockAccountWithoutHealthCode() {
        // Necessary to override this method where we do a cast that fails on the mock stormpathAccount
        dao = org.mockito.Mockito.spy(dao);
        doReturn(false).when(dao).isAccountDirty(any());
        
        doReturn(stormpathAccount).when(client).getResource(any(), eq(com.stormpath.sdk.account.Account.class), any());
        doReturn(directory).when(stormpathAccount).getDirectory();
        doReturn(directory).when(client).getResource(study.getStormpathHref(), Directory.class);
        doReturn(study.getStormpathHref()).when(directory).getHref();
        
        GroupList groupList = mock(GroupList.class);
        when(groupList.iterator()).thenReturn(Lists.<Group>newArrayList().iterator());
        
        HealthId healthId = mock(HealthId.class);
        doReturn("ABC").when(healthId).getCode();
        doReturn("healthId").when(healthId).getId();
        
        doReturn(null).when(healthCodeService).getMapping("healthId");
        doReturn(healthId).when(healthCodeService).createMapping(study);
        
        // There is no healthId in the custom data, which is key to the setup of this test.
        doReturn(null).when(customData).get("test-study_version");
        doReturn(null).when(customData).get("test-study_code");
        doReturn(customData).when(stormpathAccount).getCustomData();
        doReturn(groupList).when(stormpathAccount).getGroups();
    }
    
    private com.stormpath.sdk.account.Account setupGroupChangeTest(Set<String> oldGroupSet, Set<Roles> newGroupSet) {
        // mock Stormpath account
        List<Group> mockGroupJavaList = new ArrayList<>();
        for (String oneGroupName : oldGroupSet) {
            Group mockGroup = mock(Group.class);
            when(mockGroup.getName()).thenReturn(oneGroupName);
            mockGroupJavaList.add(mockGroup);
        }

        GroupList mockGroupSpList = mock(GroupList.class);
        when(mockGroupSpList.iterator()).thenReturn(mockGroupJavaList.iterator());

        // Due to some funkiness in Stormpath's type tree, DefaultAccount is the only public class we can mock.
        com.stormpath.sdk.account.Account mockSpAccount = mock(DefaultAccount.class);
        when(mockSpAccount.getCustomData()).thenReturn(mock(CustomData.class));
        when(mockSpAccount.getGroups()).thenReturn(mockGroupSpList);

        // mock Bridge account
        StormpathAccount mockAccount = mock(StormpathAccount.class);
        when(mockAccount.getAccount()).thenReturn(mockSpAccount);
        when(mockAccount.getRoles()).thenReturn(newGroupSet);

        // execute
        dao.updateAccount(mockAccount);

        // return this, so the caller can verify back-end mock calls
        return mockSpAccount;
    }

}
