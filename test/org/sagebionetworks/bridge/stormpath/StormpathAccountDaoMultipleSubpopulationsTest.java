package org.sagebionetworks.bridge.stormpath;

import static org.junit.Assert.assertEquals;

import java.util.List;

import javax.annotation.Resource;

import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.services.StudyService;
import org.sagebionetworks.bridge.services.SubpopulationService;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class StormpathAccountDaoMultipleSubpopulationsTest {
    private static final String PASSWORD = "P4ssword!";

    @Resource(name="stormpathAccountDao")
    private StormpathAccountDao accountDao;

    @Autowired
    private StudyService studyService;

    @Autowired
    private SubpopulationService subpopService;

    private Study study;

    @Before
    public void before() {
        Study studyToCreate = TestUtils.getValidStudy(StormpathAccountDaoMultipleSubpopulationsTest.class);
        study = studyService.createStudy(studyToCreate);
    }

    @After
    public void after() {
        if (study != null) {
            studyService.deleteStudy(study.getIdentifier());
        }
    }

    @Test
    public void canSetAndRetrieveConsentsForMultipleSubpopulations() {
        // Create a second subpop for the purpose of this test.
        Subpopulation subpopToCreate = Subpopulation.create();
        subpopToCreate.setName("subpop 2");
        subpopService.createSubpopulation(study, subpopToCreate);

        // Get the subpops back. There should be exactly 2 subpops (one default, one we just created).
        List<Subpopulation> subpopList = subpopService.getSubpopulations(study);
        assertEquals(2, subpopList.size());
        Subpopulation subpop1 = subpopList.get(0);
        Subpopulation subpop2 = subpopList.get(1);

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

        String email = TestUtils.makeRandomTestEmail(StormpathAccountDaoTest.class);
        StudyParticipant participant = new StudyParticipant.Builder().withEmail(email).withPassword(PASSWORD).build();
        Account account = null;
        try {
            account = accountDao.constructAccount(study, participant.getEmail(), participant.getPassword());
            accountDao.createAccount(study, account, false);

            account.getConsentSignatureHistory(subpop1.getGuid()).add(sig1);
            account.getConsentSignatureHistory(subpop2.getGuid()).add(sig2);
            accountDao.updateAccount(account);

            account = accountDao.getAccount(study, account.getId());

            List<ConsentSignature> history1 = account.getConsentSignatureHistory(subpop1.getGuid());
            assertEquals(1, history1.size());
            assertEquals(sig1, history1.get(0));
            assertEquals(sig1, account.getActiveConsentSignature(subpop1.getGuid()));

            List<ConsentSignature> history2 = account.getConsentSignatureHistory(subpop2.getGuid());
            assertEquals(1, history2.size());
            assertEquals(sig2, history2.get(0));
            assertEquals(sig2, account.getActiveConsentSignature(subpop2.getGuid()));
        } finally {
            if (account != null) {
                accountDao.deleteAccount(study, account.getId());
            }
        }
    }
}
