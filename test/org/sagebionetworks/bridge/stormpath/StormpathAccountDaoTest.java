package org.sagebionetworks.bridge.stormpath;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.sagebionetworks.bridge.Roles.TEST_USERS;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;

import java.util.Iterator;

import javax.annotation.Resource;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.Email;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.SignUp;
import org.sagebionetworks.bridge.models.studies.ConsentSignature;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.Subpopulation;
import org.sagebionetworks.bridge.services.StudyServiceImpl;
import org.sagebionetworks.bridge.services.SubpopulationService;

import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.google.common.collect.Sets;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class StormpathAccountDaoTest {

    private static final String PASSWORD = "P4ssword!";

    @Resource(name="stormpathAccountDao")
    private StormpathAccountDao accountDao;
    
    @Resource
    private StudyServiceImpl studyService;

    @Resource
    private SubpopulationService subpopService;
    
    private Study study;
    
    private Subpopulation subpop;
    
    @Before
    public void setUp() {
        study = studyService.getStudy(TEST_STUDY_IDENTIFIER);
        subpop = subpopService.getSubpopulations(study).get(0);
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
            SignUp signUp = new SignUp(random, email, PASSWORD, Sets.newHashSet(TEST_USERS), null);
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
            SignUp signUp = new SignUp(random, email, PASSWORD, Sets.newHashSet(TEST_USERS), null);
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
            // Sign Up
            long signedOn = DateUtils.getCurrentMillisFromEpoch();
            ConsentSignature sig = new ConsentSignature.Builder().withName("Test Test").withBirthdate("1970-01-01")
                    .withSignedOn(signedOn).build();
            
            SignUp signUp = new SignUp(random, email, PASSWORD, Sets.newHashSet(TEST_USERS), null);
            account = accountDao.signUp(study, signUp, false);
            
            assertNull(account.getFirstName()); // defaults are not visible
            assertNull(account.getLastName());
            account.setEmail(email);
            account.setAttribute("phone", "123-456-7890");
            account.setHealthId("abc");
            account.setUsername(random);
            account.getConsentSignatureHistory(subpop.getGuid()).add(sig);
            account.setAttribute("attribute_one", "value of attribute one");
            
            // Update Account
            accountDao.updateAccount(study, account);
            Account newAccount = accountDao.getAccount(study, account.getEmail());
            assertNotNull(newAccount.getEmail());
            
            assertNull(newAccount.getFirstName()); // defaults still not visible
            assertNull(newAccount.getLastName());
            assertEquals(account.getEmail(), newAccount.getEmail());
            assertEquals(account.getAttribute("phone"), newAccount.getAttribute("phone"));
            assertEquals(account.getHealthId(), newAccount.getHealthId());
            assertEquals(account.getUsername(), newAccount.getUsername());
            assertEquals(account.getActiveConsentSignature(subpop.getGuid()), 
                    newAccount.getActiveConsentSignature(subpop.getGuid()));
            assertEquals(account.getActiveConsentSignature(subpop.getGuid()).getSignedOn(), 
                    newAccount.getActiveConsentSignature(subpop.getGuid()).getSignedOn());
            assertEquals(signedOn, newAccount.getActiveConsentSignature(subpop.getGuid()).getSignedOn());
            assertEquals(1, newAccount.getRoles().size());
            assertEquals(account.getRoles().iterator().next(), newAccount.getRoles().iterator().next());
            assertEquals("value of attribute one", account.getAttribute("attribute_one"));
            assertNull(account.getAttribute("attribute_two"));

            // Just remove a group... this gets into verifying and avoiding saving the underlying
            // Stormpath account. 
            account.getRoles().remove(TEST_USERS);
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
        SignUp signUp = new SignUp(random, "bridge-testing+" + random + "@sagebridge.org", PASSWORD, null, null); 
        try {
            accountDao.signUp(study, signUp, false); // don't send email
            
            Email emailObj = new Email(study.getStudyIdentifier(), signUp.getEmail());
            accountDao.resendEmailVerificationToken(study.getStudyIdentifier(), emailObj); // now send email
        } finally {
            accountDao.deleteAccount(study, signUp.getEmail());
        }
    }
    
}
