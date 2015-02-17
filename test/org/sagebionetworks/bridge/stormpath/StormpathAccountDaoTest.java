package org.sagebionetworks.bridge.stormpath;

import static org.junit.Assert.*;

import java.util.Iterator;

import javax.annotation.Resource;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.SignIn;
import org.sagebionetworks.bridge.models.SignUp;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.studies.ConsentSignature;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.StudyServiceImpl;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.google.common.collect.Sets;

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
    
}
