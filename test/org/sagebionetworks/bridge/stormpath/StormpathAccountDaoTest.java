package org.sagebionetworks.bridge.stormpath;

import static org.junit.Assert.*;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Iterator;

import javax.annotation.Resource;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.Email;
import org.sagebionetworks.bridge.models.accounts.EmailVerification;
import org.sagebionetworks.bridge.models.accounts.PasswordReset;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.SignUp;
import org.sagebionetworks.bridge.models.studies.ConsentSignature;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.StudyServiceImpl;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.google.common.collect.Sets;
import com.stormpath.sdk.application.Application;
import com.stormpath.sdk.client.Client;
import com.stormpath.sdk.directory.Directory;
import com.stormpath.sdk.resource.ResourceException;
import com.stormpath.sdk.tenant.Tenant;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class StormpathAccountDaoTest {

    private static final String PASSWORD = "P4ssword!";

    @Resource(name="stormpathAccountDao")
    private StormpathAccountDao accountDao;
    
    @Resource
    private StudyServiceImpl studyService;
    
    private Study study;
    
    @Before
    public void setUp() {
        study = studyService.getStudy(TestConstants.TEST_STUDY_IDENTIFIER);
    }
    
    @Test
    public void getStudyAccounts() {
        Iterator<Account> i = accountDao.getStudyAccounts(study);
        
        // There's always one... the behavior of the iterator is tested separately
        assertTrue(i.hasNext());
    }
    
    @Test
    public void getAllAccounts() {
        Iterator<Account> i = accountDao.getAllAccounts(); 
        
        // There's always one... the behavior of the iterator is tested separately
        assertTrue(i.hasNext());
    }
    
    @Test
    public void returnsNulWhenThereIsNoAccount() {
        Account account = accountDao.getAccount(study, "thisemaildoesntexist@stormpath.com");
        assertNull(account);
    }
    
    @Test
    public void canAuthenticate() {
        String random = RandomStringUtils.randomAlphabetic(5);
        String email = "bridge-testing+"+random+"@sagebridge.org";
        Account account = null;
        
        try {
            SignUp signUp = new SignUp(random, email, PASSWORD, Sets.newHashSet("test_users"));
            accountDao.signUp(study, signUp, false);
            
            account = accountDao.authenticate(study, new SignIn(email, PASSWORD));
            assertEquals(random, account.getUsername());
            assertEquals(1, account.getRoles().size());
        } finally {
            accountDao.deleteAccount(study, email);
        }
    }
    
    @Test
    public void badPasswordReportedAs404() {
        String random = RandomStringUtils.randomAlphabetic(5);
        String email = "bridge-testing+"+random+"@sagebridge.org";
        try {
            SignUp signUp = new SignUp(random, email, PASSWORD, Sets.newHashSet("test_users"));
            accountDao.signUp(study, signUp, false);
            
            try {
                accountDao.authenticate(study, new SignIn(email, "BadPassword"));
                fail("Should have thrown an exception");
            } catch(EntityNotFoundException e) {
                assertEquals("Account not found.", e.getMessage());
            }
        } finally {
            accountDao.deleteAccount(study, email);
        }
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void cannotAuthenticate() {
        accountDao.authenticate(study, new SignIn("bridge-testing+noone@sagebridge.org", "belgium"));
    }
    
    @Test
    public void crudAccount() {
        String random = RandomStringUtils.randomAlphabetic(5);
        String email = "bridge-testing+"+random+"@sagebridge.org";
        Account account = null;
        try {
            ConsentSignature sig = ConsentSignature.create("Test Test", "1970-01-01", null, null);
            
            SignUp signUp = new SignUp(random, email, PASSWORD, Sets.newHashSet("test_users"));
            
            accountDao.signUp(study, signUp, false);
            account = accountDao.getAccount(study, signUp.getEmail());
            assertNull(account.getFirstName()); // defaults are not visible
            assertNull(account.getLastName());
            account.setEmail(email);
            account.setAttribute("phone", "123-456-7890");
            account.setHealthId("abc");
            account.setUsername(random);
            account.setConsentSignature(sig);
            account.setAttribute("attribute_one", "value of attribute one");
            
            accountDao.updateAccount(study, account);
            Account newAccount = accountDao.getAccount(study, account.getEmail());
            assertNotNull(newAccount.getEmail());
            
            assertNull(newAccount.getFirstName()); // defaults still not visible
            assertNull(newAccount.getLastName());
            assertEquals(account.getEmail(), newAccount.getEmail());
            assertEquals(account.getAttribute("phone"), newAccount.getAttribute("phone"));
            assertEquals(account.getHealthId(), newAccount.getHealthId());
            assertEquals(account.getUsername(), newAccount.getUsername());
            assertEquals(account.getConsentSignature(), newAccount.getConsentSignature());
            assertEquals(1, newAccount.getRoles().size());
            assertEquals(account.getRoles().iterator().next(), newAccount.getRoles().iterator().next());
            assertEquals("value of attribute one", account.getAttribute("attribute_one"));
            assertNull(account.getAttribute("attribute_two"));

            // Just remove a group... this gets into verifying and avoiding saving the underlying
            // Stormpath account. 
            account.getRoles().remove("test_users");
            accountDao.updateAccount(study, account);
            
            newAccount = accountDao.getAccount(study, newAccount.getEmail());
            assertEquals(0, newAccount.getRoles().size());
            
            // finally, test the name
            newAccount.setFirstName("Test");
            newAccount.setLastName("Tester");
            accountDao.updateAccount(study, newAccount);
            
            newAccount = accountDao.getAccount(study, newAccount.getEmail());
            assertEquals("Test", newAccount.getFirstName()); // name is now visible
            assertEquals("Tester", newAccount.getLastName());
            
        } finally {
            accountDao.deleteAccount(study, email);
            account = accountDao.getAccount(study, email);
            assertNull(account);
        }
    }
    
    @Test
    public void canResendEmailVerification() throws Exception {
        String random = RandomStringUtils.randomAlphabetic(5);
        SignUp signUp = new SignUp(random, "bridge-testing+" + random + "@sagebridge.org", PASSWORD, null); 
        try {
            accountDao.signUp(study, signUp, false); // don't send email
            
            Email emailObj = new Email(study.getStudyIdentifier(), signUp.getEmail());
            accountDao.resendEmailVerificationToken(study.getStudyIdentifier(), emailObj); // now send email
        } finally {
            accountDao.deleteAccount(study, signUp.getEmail());
        }
    }

    @Test
    public void verifyEmail() {
        StormpathAccountDao dao = new StormpathAccountDao();
        
        EmailVerification verification = new EmailVerification("tokenAAA");
        
        Client client = mock(Client.class);
        Tenant tenant = mock(Tenant.class);
        when(client.getCurrentTenant()).thenReturn(tenant);
        dao.setStormpathClient(client);
        
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
        
        dao.resetPassword(passwordReset);
        verify(application).resetPassword(passwordReset.getSptoken(), passwordReset.getPassword());
        verifyNoMoreInteractions(application);
    }
    
    @Test
    public void stormpathAccountCorrectlyInitialized() {
        StormpathAccountDao dao = new StormpathAccountDao();
        
        Directory directory = mock(Directory.class);
        com.stormpath.sdk.account.Account account = mock(com.stormpath.sdk.account.Account.class);
        Client client = mock(Client.class);
        
        when(client.instantiate(com.stormpath.sdk.account.Account.class)).thenReturn(account);
        when(client.getResource(study.getStormpathHref(), Directory.class)).thenReturn(directory);
        dao.setStormpathClient(client);
        
        String random = RandomStringUtils.randomAlphabetic(5);
        String email = "bridge-testing+"+random+"@sagebridge.org";
        SignUp signUp = new SignUp(random, email, PASSWORD, null);
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

}
