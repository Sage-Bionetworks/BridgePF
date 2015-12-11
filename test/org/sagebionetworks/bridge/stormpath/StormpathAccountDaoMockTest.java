package org.sagebionetworks.bridge.stormpath;

import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.accounts.Email;
import org.sagebionetworks.bridge.models.accounts.EmailVerification;
import org.sagebionetworks.bridge.models.accounts.PasswordReset;
import org.sagebionetworks.bridge.models.accounts.SignUp;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.services.SubpopulationService;

import com.google.common.collect.Lists;
import com.stormpath.sdk.application.Application;
import com.stormpath.sdk.client.Client;
import com.stormpath.sdk.directory.CustomData;
import com.stormpath.sdk.directory.Directory;
import com.stormpath.sdk.resource.ResourceException;
import com.stormpath.sdk.tenant.Tenant;

public class StormpathAccountDaoMockTest {

    private static final String PASSWORD = "P4ssword!";
    
    private Study study;
    
    @Before
    public void setUp() {
        study = new DynamoStudy();
        study.setIdentifier("test-study");
        study.setStormpathHref("http://some/dumb.href");
    }

    @Test
    public void verifyEmail() {
        StormpathAccountDao dao = new StormpathAccountDao();
        
        EmailVerification verification = new EmailVerification("tokenAAA");
        
        Client client = mock(Client.class);
        Tenant tenant = mock(Tenant.class);
        when(client.getCurrentTenant()).thenReturn(tenant);
        dao.setStormpathClient(client);
        dao.setSubpopulationService(mockSubpopService());
        
        dao.verifyEmail(study, verification);
        verify(client).verifyAccountEmail("tokenAAA");
    }

    @Test
    public void requestResetPassword() {
        String emailString = "bridge-tester+43@sagebridge.org";
        
        Application application = mock(Application.class);
        Directory directory = mock(Directory.class);
        Client client = mock(Client.class);
        when(client.getResource(study.getStormpathHref(), Directory.class)).thenReturn(directory);
        
        StormpathAccountDao dao = new StormpathAccountDao();
        dao.setStormpathApplication(application);
        dao.setStormpathClient(client);
        dao.setSubpopulationService(mockSubpopService());
        
        dao.requestResetPassword(study, new Email(study.getStudyIdentifier(), emailString));
        
        verify(client).getResource(study.getStormpathHref(), Directory.class);
        verify(application).sendPasswordResetEmail(emailString, directory);
    }
    
    @Test(expected = BridgeServiceException.class)
    public void requestPasswordRequestThrowsException() {
        String emailString = "bridge-tester+43@sagebridge.org";
        
        Application application = mock(Application.class);
        Directory directory = mock(Directory.class);
        Client client = mock(Client.class);
        when(client.getResource(study.getStormpathHref(), Directory.class)).thenReturn(directory);
        
        StormpathAccountDao dao = new StormpathAccountDao();
        dao.setStormpathApplication(application);
        dao.setStormpathClient(client);
        
        com.stormpath.sdk.error.Error error = mock(com.stormpath.sdk.error.Error.class);
        ResourceException e = new ResourceException(error);
        when(application.sendPasswordResetEmail(emailString, directory)).thenThrow(e);
        dao.setStormpathApplication(application);
        
        dao.requestResetPassword(study, new Email(study.getStudyIdentifier(), emailString));
    }

    @Test
    public void resetPassword() {
        StormpathAccountDao dao = new StormpathAccountDao();
        PasswordReset passwordReset = new PasswordReset("password", "sptoken");
        
        Application application = mock(Application.class);
        dao.setStormpathApplication(application);
        dao.setSubpopulationService(mockSubpopService());
        
        dao.resetPassword(passwordReset);
        verify(application).resetPassword(passwordReset.getSptoken(), passwordReset.getPassword());
        verifyNoMoreInteractions(application);
    }
    
    @Test
    public void stormpathAccountCorrectlyInitialized() {
        StormpathAccountDao dao = new StormpathAccountDao();
        
        Directory directory = mock(Directory.class);
        com.stormpath.sdk.account.Account account = mock(com.stormpath.sdk.account.Account.class);
        when(account.getCustomData()).thenReturn(mock(CustomData.class));
        Client client = mock(Client.class);
        
        when(client.instantiate(com.stormpath.sdk.account.Account.class)).thenReturn(account);
        when(client.getResource(study.getStormpathHref(), Directory.class)).thenReturn(directory);
        dao.setStormpathClient(client);
        dao.setSubpopulationService(mockSubpopService());
        
        String random = RandomStringUtils.randomAlphabetic(5);
        String email = "bridge-testing+"+random+"@sagebridge.org";
        SignUp signUp = new SignUp(random, email, PASSWORD, null, null);
        dao.signUp(study, signUp, false);

        ArgumentCaptor<com.stormpath.sdk.account.Account> argument = ArgumentCaptor.forClass(com.stormpath.sdk.account.Account.class);
        verify(directory).createAccount(argument.capture(), anyBoolean());
        
        com.stormpath.sdk.account.Account acct = argument.getValue();
        verify(acct).setSurname("<EMPTY>");
        verify(acct).setGivenName("<EMPTY>");
        verify(acct).setUsername(random);
        verify(acct).setEmail(email);
        verify(acct).setPassword(PASSWORD);
    }

    private SubpopulationService mockSubpopService() {
        Subpopulation subpop = Subpopulation.create();
        subpop.setGuid(study.getIdentifier());
        SubpopulationService subpopService = mock(SubpopulationService.class);
        when(subpopService.getSubpopulations(study.getStudyIdentifier())).thenReturn(Lists.newArrayList(subpop));
        return subpopService;
    }

}
