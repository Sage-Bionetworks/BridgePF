package org.sagebionetworks.bridge.stormpath;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.Roles.TEST_USERS;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;

import java.util.Iterator;
import java.util.List;

import javax.annotation.Resource;

import org.apache.commons.lang3.RandomStringUtils;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.accounts.Email;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.SignUp;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.services.StudyService;
import org.sagebionetworks.bridge.services.SubpopulationService;

import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class StormpathAccountDaoTest {

    private static final String PASSWORD = "P4ssword!";

    @Resource(name="stormpathAccountDao")
    private StormpathAccountDao accountDao;
    
    @Resource
    private StudyService studyService;

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
    public void getStudyPagedAccounts() {
        List<String> newAccounts = Lists.newArrayList();
        try {
            PagedResourceList<AccountSummary> accounts = accountDao.getPagedAccountSummaries(study, 0, 10);
            // This test requires 6 accounts be present (one more than a page so we can verify the results are capped)
            // API directories already have 3-6 accounts. They don't need to be verified, consented, etc.
            if (accounts.getTotal() < 6) {
                for (int i=0; i < (6-accounts.getTotal()); i++) {
                    String random = RandomStringUtils.randomAlphabetic(5);
                    String email = "bridge-testing+"+random+"@sagebridge.org";
                    SignUp signUp = new SignUp(email, PASSWORD, Sets.newHashSet(TEST_USERS), null);
                    accountDao.signUp(study, signUp, false);
                    newAccounts.add(email);
                }
            }
            // Fetch only 5 accounts
            accounts = accountDao.getPagedAccountSummaries(study, 0, 5);
            
            // pageSize is respected
            assertEquals(5, accounts.getItems().size());
            
            // offsetBy is advanced
            AccountSummary account1 = accountDao.getPagedAccountSummaries(study, 1, 5).getItems().get(0);
            AccountSummary account2 = accountDao.getPagedAccountSummaries(study, 2, 5).getItems().get(0);
            assertEquals(accounts.getItems().get(1), account1);
            assertEquals(accounts.getItems().get(2), account2);
            
            // Next page = offset + pageSize
            AccountSummary nextPageAccount = accountDao.getPagedAccountSummaries(study, 5, 5).getItems().get(0);
            assertFalse(accounts.getItems().contains(nextPageAccount));
            
            // This should be beyond the number of users in any API study. Should be empty
            accounts = accountDao.getPagedAccountSummaries(study, 100000, 100);
            assertEquals(0, accounts.getItems().size());
        } finally {
            for (String email : newAccounts) {
                accountDao.deleteAccount(study, email);
            }
        }
    }
    
    @Test
    public void returnsNullWhenThereIsNoAccount() {
        Account account = accountDao.getAccount(study, "thisemaildoesntexist@stormpath.com");
        assertNull(account);
    }
    
    @Test
    public void canAuthenticate() {
        String random = RandomStringUtils.randomAlphabetic(5);
        String email = "bridge-testing+"+random+"@sagebridge.org";
        Account account = null;
        
        try {
            SignUp signUp = new SignUp(email, PASSWORD, Sets.newHashSet(TEST_USERS), null);
            accountDao.signUp(study, signUp, false);
            
            account = accountDao.authenticate(study, new SignIn(email, PASSWORD));
            assertEquals(email, account.getEmail());
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
            SignUp signUp = new SignUp(email, PASSWORD, Sets.newHashSet(TEST_USERS), null);
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
            
            SignUp signUp = new SignUp(email, PASSWORD, Sets.newHashSet(TEST_USERS), null);
            account = accountDao.signUp(study, signUp, false);
            
            assertNull(account.getFirstName()); // defaults are not visible
            assertNull(account.getLastName());
            account.setEmail(email);
            account.setAttribute("phone", "123-456-7890");
            account.setHealthId("abc");
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
        SignUp signUp = new SignUp("bridge-testing+" + random + "@sagebridge.org", PASSWORD, null, null); 
        try {
            accountDao.signUp(study, signUp, false); // don't send email
            
            Email emailObj = new Email(study.getStudyIdentifier(), signUp.getEmail());
            accountDao.resendEmailVerificationToken(study.getStudyIdentifier(), emailObj); // now send email
        } finally {
            accountDao.deleteAccount(study, signUp.getEmail());
        }
    }
    
    @Test
    public void canSetAndRetrieveConsentsForMultipleSubpopulations() {
        // Need to mock out the retrieval of these two subpopulations for the purpose
        // of this test
        Subpopulation subpop1 = Subpopulation.create();
        subpop1.setGuidString("test1");
        Subpopulation subpop2 = Subpopulation.create();
        subpop2.setGuidString("test2");
        
        SubpopulationService mockSubpopService = mock(SubpopulationService.class);
        when(mockSubpopService.getSubpopulations(study)).thenReturn(Lists.newArrayList(subpop1, subpop2));
        accountDao.setSubpopulationService(mockSubpopService);
        
        ConsentSignature sig1 = new ConsentSignature.Builder()
                .withName("Name 1")
                .withBirthdate("2000-10-10")
                .withSignedOn(DateTime.now().getMillis())
                .build();
        
        ConsentSignature sig2 = new ConsentSignature.Builder()
                .withName("Name 2")
                .withBirthdate("2000-02-02")
                .withSignedOn(DateTime.now().getMillis())
                .build();

        String random = RandomStringUtils.randomAlphabetic(5);
        SignUp signUp = new SignUp("bridge-testing+" + random + "@sagebridge.org", PASSWORD, null, null); 
        try {
            Account account = accountDao.signUp(study, signUp, false); // don't send email
            
            account.getConsentSignatureHistory(subpop1.getGuid()).add(sig1);
            account.getConsentSignatureHistory(subpop2.getGuid()).add(sig2);
            accountDao.updateAccount(study, account);
            
            account = accountDao.getAccount(study, account.getEmail());
            
            List<ConsentSignature> history1 = account.getConsentSignatureHistory(subpop1.getGuid());
            assertEquals(1, history1.size());
            assertEquals(sig1, history1.get(0));
            assertEquals(sig1, account.getActiveConsentSignature(subpop1.getGuid()));
            
            List<ConsentSignature> history2 = account.getConsentSignatureHistory(subpop2.getGuid());
            assertEquals(1, history2.size());
            assertEquals(sig2, history2.get(0));
            assertEquals(sig2, account.getActiveConsentSignature(subpop2.getGuid()));

        } finally {
            accountDao.deleteAccount(study, signUp.getEmail());
        }
        
    }
    
}
