package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.annotation.Resource;

import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.accounts.UserConsent;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import com.google.common.collect.Sets;

import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoUserConsentDaoTest {

    private List<String> healthCodes;
    private static final SubpopulationGuid SUBPOP_GUID = SubpopulationGuid.create("subpop123");

    @Resource
    private DynamoUserConsentDao userConsentDao;

    @Before
    public void before() {
        healthCodes = new ArrayList<>();
    }

    @After
    public void after() {
        for (String healthCode : healthCodes) {
            userConsentDao.deleteAllConsents(healthCode, SUBPOP_GUID);    
        }
    }
    
    private String healthCode() {
        return healthCodes.get(healthCodes.size()-1);
    }
    
    @Test
    public void canConsentAndWithdrawFromStudy() {
        healthCodes.add(BridgeUtils.generateGuid());
        
        final DynamoStudyConsent1 consent = createStudyConsent();
        verifyActiveConsentAbsent();
        verifyConsentAbsentAt(DateTime.now().getMillis()); // some random time will fail
        
        // Give consent
        long signedOn = DateTime.now().getMillis();
        userConsentDao.giveConsent(healthCode(), SUBPOP_GUID, consent.getCreatedOn(), signedOn);
        verifyActiveConsentExists();

        // Withdraw
        userConsentDao.withdrawConsent(healthCode(), SUBPOP_GUID, DateTime.now().getMillis());
        verifyActiveConsentAbsent();
        verifyConsentExistsAt(signedOn);
        assertFalse(userConsentDao.hasConsented(healthCode(), SUBPOP_GUID));
        
        // There should be no consent to withdraw from (even though it's in the historical list)
        try {
            userConsentDao.withdrawConsent(healthCode(), SUBPOP_GUID, DateTime.now().getMillis());
            fail("Should have thrown exception");
        } catch(BridgeServiceException e) {
            assertEquals(404, e.getStatusCode());
            assertEquals("UserConsent not found.", e.getMessage());
        }

        // Can give consent again because the previous consent is withdrawn
        long signedAgainOn = DateTime.now().getMillis();
        userConsentDao.giveConsent(healthCode(), SUBPOP_GUID, consent.getCreatedOn(), signedAgainOn);
        verifyActiveConsentExists();
        verifyConsentExistsAt(signedAgainOn);
        verifyConsentExistsAt(signedOn); // this is still also true.
        
        // Withdraw again
        userConsentDao.withdrawConsent(healthCode(), SUBPOP_GUID, DateTime.now().getMillis());
        verifyActiveConsentAbsent();
    }

    @Test
    public void canCountStudyParticipants() throws Exception {
        final DynamoStudyConsent1 consent = createStudyConsent();
        for (int i=1; i < 6; i++) {
            healthCodes.add(BridgeUtils.generateGuid());
            userConsentDao.giveConsent(healthCode(), SUBPOP_GUID, consent.getCreatedOn(), DateTime.now().getMillis());
        }
        Set<String> seekingHealthCodes = Sets.newHashSet(healthCodes.subList(healthCodes.size()-5, healthCodes.size()));
        
        int count = userConsentDao.getParticipantHealthCodes(SUBPOP_GUID).size();
        assertEquals("Correct number of participants", 5, count);
        
        userConsentDao.withdrawConsent(healthCode(), SUBPOP_GUID, DateTime.now().getMillis());
        count = userConsentDao.getParticipantHealthCodes(SUBPOP_GUID).size();
        assertEquals("Correct number of participants", 4, count);
        
        userConsentDao.giveConsent(healthCode(), SUBPOP_GUID, consent.getCreatedOn(), DateTime.now().getMillis());
        Set<String> finalHealthCodes = userConsentDao.getParticipantHealthCodes(SUBPOP_GUID);
        
        assertEquals(seekingHealthCodes, finalHealthCodes);
    }
    
    @Test
    public void canGetAllConsentsInHistory() throws Exception {
        healthCodes.add(BridgeUtils.generateGuid());
        final DynamoStudyConsent1 consent = createStudyConsent();
        for (int i=0; i < 5; i++) {
            userConsentDao.giveConsent(healthCode(), SUBPOP_GUID, consent.getCreatedOn(), DateTime.now().getMillis());
            userConsentDao.withdrawConsent(healthCode(), SUBPOP_GUID, DateTime.now().getMillis());
        }
        userConsentDao.giveConsent(healthCode(), SUBPOP_GUID, consent.getCreatedOn(), DateTime.now().getMillis());
        
        List<UserConsent> consents = userConsentDao.getUserConsentHistory(healthCode(), SUBPOP_GUID);
        assertEquals(6, consents.size());
        for (int i=0; i < 5; i++) {
            assertNotNull(consents.get(i).getWithdrewOn());
        }
        assertNull(consents.get(consents.size()-1).getWithdrewOn());
    }

    @Test
    public void singleUserCanSignMultipleConsents() {
        healthCodes.add(BridgeUtils.generateGuid());
        DynamoStudyConsent1 secondConsent = createStudyConsent(DateTime.now().getMillis());

        long signedOn = DateTime.now().getMillis();
        userConsentDao.giveConsent(healthCode(), SUBPOP_GUID, secondConsent.getCreatedOn(), signedOn);
        
        // You cannot sign the same study a second time
        try {
            userConsentDao.giveConsent(healthCode(), SUBPOP_GUID, secondConsent.getCreatedOn(), signedOn);
        } catch(BridgeServiceException e) {
            assertEquals(409, e.getStatusCode());
            assertEquals("UserConsent already exists.", e.getMessage());
        }
        
        // Now create a different study consent, you should be able to consent again.
        long secondConsentCreatedOn = DateTime.now().getMillis();
        secondConsent = createStudyConsent(secondConsentCreatedOn);
        long signedOnAgain = signedOn + (100000);
        userConsentDao.giveConsent(healthCode(), SUBPOP_GUID, secondConsent.getCreatedOn(), signedOnAgain);
        
        List<UserConsent> consents = userConsentDao.getUserConsentHistory(healthCode(), SUBPOP_GUID);
        assertEquals(2, consents.size());
        assertEquals(signedOn, consents.get(0).getSignedOn());
        assertEquals(signedOnAgain, consents.get(1).getSignedOn());
        
        // The active consent was signed against the second study consent.
        UserConsent activeConsent = userConsentDao.getActiveUserConsent(healthCode(), SUBPOP_GUID);
        assertEquals(signedOnAgain, activeConsent.getSignedOn());
        assertEquals(secondConsentCreatedOn, activeConsent.getConsentCreatedOn());
    }
    
    @Test
    public void getConsentBySignedOnDate() throws Exception {
        healthCodes.add(BridgeUtils.generateGuid());
        final DynamoStudyConsent1 studyConsent = createStudyConsent();
        
        final long signedOn = DateTime.now().getMillis();
        userConsentDao.giveConsent(healthCode(), SUBPOP_GUID, studyConsent.getCreatedOn(), signedOn);
        
        UserConsent consent = userConsentDao.getUserConsent(healthCode(), SUBPOP_GUID, signedOn);
        assertEquals(signedOn, consent.getSignedOn());
        assertEquals(SUBPOP_GUID.getGuid(), consent.getSubpopulationGuid());
        assertNull(consent.getWithdrewOn());
        
        // Withdraw consent, you can still retrieve
        final long withdrewOn = DateTime.now().getMillis();
        userConsentDao.withdrawConsent(healthCode(), SUBPOP_GUID, withdrewOn);
        
        consent = userConsentDao.getUserConsent(healthCode(), SUBPOP_GUID, signedOn);
        assertEquals(signedOn, consent.getSignedOn());
        assertEquals(SUBPOP_GUID.getGuid(), consent.getSubpopulationGuid());
        assertEquals((Long)withdrewOn, consent.getWithdrewOn());
    }
    
    private void verifyActiveConsentExists() {
        UserConsent active = userConsentDao.getActiveUserConsent(healthCode(), SUBPOP_GUID);
        assertNotNull(active);
        assertTrue(userConsentDao.hasConsented(healthCode(), SUBPOP_GUID));
        
        // Active consent should always be the last consent
        List<UserConsent> history = userConsentDao.getUserConsentHistory(healthCode(), SUBPOP_GUID);
        assertEquals(active.getSignedOn(), history.get(history.size()-1).getSignedOn());
        assertNull(history.get(history.size()-1).getWithdrewOn());
    }
    
    private void verifyActiveConsentAbsent() {
        assertNull(userConsentDao.getActiveUserConsent(healthCode(), SUBPOP_GUID));
        assertFalse(userConsentDao.hasConsented(healthCode(), SUBPOP_GUID));
    }
    
    private void verifyConsentExistsAt(long signedOn) {
        UserConsent consent = userConsentDao.getUserConsent(healthCode(), SUBPOP_GUID, signedOn);
        assertNotNull(consent);
        assertEquals(signedOn, consent.getSignedOn());
        
        List<UserConsent> history = userConsentDao.getUserConsentHistory(healthCode(), SUBPOP_GUID);
        for (UserConsent aConsent : history) {
            if (aConsent.getSignedOn() == signedOn) {
                return;
            }
        }
        fail("Could not find the consent in the history");
    }
    
    private void verifyConsentAbsentAt(long signedOn) {
        try {
            userConsentDao.getUserConsent(healthCode(), SUBPOP_GUID, signedOn);
            fail("Should have thrown an exception");
        } catch(BridgeServiceException e) {
            assertEquals(404, e.getStatusCode());
        }
    }
    
    private DynamoStudyConsent1 createStudyConsent(long createdOn) {
        final DynamoStudyConsent1 consent = new DynamoStudyConsent1();
        consent.setSubpopulationGuid(SUBPOP_GUID.getGuid());
        consent.setCreatedOn(createdOn);
        return consent;
    }
    
    private DynamoStudyConsent1 createStudyConsent() {
        return createStudyConsent(123L);
    }
}
