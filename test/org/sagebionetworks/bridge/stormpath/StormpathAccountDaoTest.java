package org.sagebionetworks.bridge.stormpath;

import static org.junit.Assert.*;
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
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.Email;
import org.sagebionetworks.bridge.models.EmailVerification;
import org.sagebionetworks.bridge.models.PasswordReset;
import org.sagebionetworks.bridge.models.SignIn;
import org.sagebionetworks.bridge.models.SignUp;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.studies.ConsentSignature;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.StudyServiceImpl;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.google.common.collect.Sets;
import com.stormpath.sdk.application.Application;
import com.stormpath.sdk.client.Client;
import com.stormpath.sdk.resource.ResourceException;
import com.stormpath.sdk.tenant.Tenant;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class StormpathAccountDaoTest {

    @Resource
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
    public void canAuthenticate() {
        String email = "bridge-testing+"+RandomStringUtils.randomAlphabetic(5)+"@sagebridge.org";
        Account account = null;
        try {
            SignUp signUp = new SignUp("tester", email, "P4ssword", Sets.newHashSet("test_users"));
            accountDao.signUp(study, signUp, false);
            
            account = accountDao.authenticate(study, new SignIn(email, "P4ssword"));
            assertEquals("tester", account.getUsername());
            assertEquals(1, account.getRoles().size());
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
        String email = "bridge-testing+tester@sagebridge.org";
        Account account = null;
        try {
            ConsentSignature sig = ConsentSignature.create("Test Test", "1970-01-01", null, null);
            
            SignUp signUp = new SignUp("tester", email, "P4ssword", Sets.newHashSet("test_users"));
            
            accountDao.signUp(study, signUp, false);
            account = accountDao.getAccount(study, signUp.getEmail());
            assertNull(account.getFirstName()); // defaults are not visible
            assertNull(account.getLastName());
            account.setEmail(email);
            account.setPhone("123-456-7890");
            account.setHealthId("abc");
            account.setUsername("tester");
            account.setConsentSignature(sig);
            
            accountDao.updateAccount(study, account);
            Account newAccount = accountDao.getAccount(study, account.getEmail());
            assertNotNull(newAccount.getEmail());
            
            assertNull(newAccount.getFirstName()); // defaults still not visible
            assertNull(newAccount.getLastName());
            assertEquals(account.getEmail(), newAccount.getEmail());
            assertEquals(account.getPhone(), newAccount.getPhone());
            assertEquals(account.getHealthId(), newAccount.getHealthId());
            assertEquals(account.getUsername(), newAccount.getUsername());
            assertEquals(account.getConsentSignature(), newAccount.getConsentSignature());
            assertEquals(1, newAccount.getRoles().size());
            assertEquals(account.getRoles().iterator().next(), newAccount.getRoles().iterator().next());

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
            try {
                accountDao.getAccount(study, email);
                fail("Should have thrown entity not found exception");
            } catch(EntityNotFoundException e) {
                // just so
            }
        }
    }
    
    @Test
    public void canResendEmailVerification() throws Exception {
        String email = "bridge-testing+tester@sagebridge.org";
        try {
            SignUp signUp = new SignUp("tester", email, "P4ssword", Sets.newHashSet("test_users"));
            accountDao.signUp(study, signUp, false);
            
            Email emailObj = new Email(email);
            accountDao.resendEmailVerificationToken(emailObj);
        } finally {
            accountDao.deleteAccount(study, email);
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
        verify(tenant).verifyAccountEmail("tokenAAA");
    }

    @Test
    public void requestResetPassword() {
        String emailString = "bridge-tester+43@sagebridge.org";
        StormpathAccountDao dao = new StormpathAccountDao();
        Application application = mock(Application.class);
        dao.setStormpathApplication(application);
        
        dao.requestResetPassword(new Email(emailString));
        
        verify(application).sendPasswordResetEmail(emailString);
    }
    
    @Test(expected = BridgeServiceException.class)
    public void requestPasswordRequestThrowsException() {
        String emailString = "bridge-tester+43@sagebridge.org";
        StormpathAccountDao dao = new StormpathAccountDao();
        
        Application application = mock(Application.class);
        com.stormpath.sdk.error.Error error = mock(com.stormpath.sdk.error.Error.class);
        ResourceException e = new ResourceException(error);
        when(application.sendPasswordResetEmail(emailString)).thenThrow(e);
        dao.setStormpathApplication(application);
        
        dao.requestResetPassword(new Email(emailString));
    }

    @Test
    public void resetPassword() {
        StormpathAccountDao dao = new StormpathAccountDao();
        
        com.stormpath.sdk.account.Account account = mock(com.stormpath.sdk.account.Account.class);
        
        PasswordReset passwordReset = new PasswordReset("password", "sptoken");
        
        Application application = mock(Application.class);
        when(application.verifyPasswordResetToken(passwordReset.getSptoken())).thenReturn(account);
        dao.setStormpathApplication(application);
        
        dao.resetPassword(passwordReset);
        verify(account).setPassword(passwordReset.getPassword());
        verify(account).save();
        verifyNoMoreInteractions(account);
    }

}
