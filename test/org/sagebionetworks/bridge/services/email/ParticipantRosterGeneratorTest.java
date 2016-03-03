package org.sagebionetworks.bridge.services.email;

import static org.sagebionetworks.bridge.dao.ParticipantOption.DATA_GROUPS;
import static org.sagebionetworks.bridge.dao.ParticipantOption.EMAIL_NOTIFICATIONS;
import static org.sagebionetworks.bridge.dao.ParticipantOption.EXTERNAL_IDENTIFIER;
import static org.sagebionetworks.bridge.dao.ParticipantOption.SHARING_SCOPE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AllParticipantOptionsLookup;
import org.sagebionetworks.bridge.models.accounts.HealthId;
import org.sagebionetworks.bridge.models.accounts.ParticipantOptionsLookup;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyParticipant;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.services.HealthCodeService;
import org.sagebionetworks.bridge.services.ParticipantOptionsService;
import org.sagebionetworks.bridge.services.ParticipantRosterGenerator;
import org.sagebionetworks.bridge.services.SendMailService;
import org.sagebionetworks.bridge.services.SubpopulationService;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

@RunWith(MockitoJUnitRunner.class)
public class ParticipantRosterGeneratorTest {

    private Study study;
    
    private ParticipantRosterGenerator generator;
    
    @Captor
    private ArgumentCaptor<MimeTypeEmailProvider> argument;

    @Mock
    private SendMailService sendMailService;
    
    @Mock
    private HealthCodeService healthCodeService;
    
    @Mock
    private ParticipantOptionsService optionsService;
    
    @Mock
    private SubpopulationService subpopService;
    
    private List<Subpopulation> studySubpopulations;
    
    @Before
    public void before() {
        study = TestUtils.getValidStudy(ParticipantRosterGeneratorTest.class);
        study.setUserProfileAttributes(Sets.newHashSet("phone", "can_recontact"));
        
        HealthId healthId = new HealthId() {
            @Override public String getId() {
                return "healthId";
            }
            @Override public String getCode() {
                return "healthCode";
            }
        };
        
        studySubpopulations = Lists.newArrayList();
        when(subpopService.getSubpopulations(study.getStudyIdentifier())).thenReturn(studySubpopulations);
        
        when(healthCodeService.getMapping(anyString())).thenReturn(healthId);
        
        Map<String,String> map = Maps.newHashMap();
        map.put(DATA_GROUPS.name(), "data-group-1");
        map.put(EMAIL_NOTIFICATIONS.name(), "true");
        map.put(SHARING_SCOPE.name(), SharingScope.ALL_QUALIFIED_RESEARCHERS.name());
        map.put(EXTERNAL_IDENTIFIER.name(), "externalId");

        ParticipantOptionsLookup lookup = new ParticipantOptionsLookup(map);
        
        AllParticipantOptionsLookup allLookup = new AllParticipantOptionsLookup();
        allLookup.put("healthCode", lookup);
        
        when(optionsService.getOptionsForAllParticipants(study)).thenReturn(allLookup);
        
        Iterator<Account> iterator = buildAccountIterator();
        generator = new ParticipantRosterGenerator(iterator, study, sendMailService, healthCodeService, optionsService, subpopService);
    }
    
    @Test
    public void generatorCreatesRoster() throws Exception {
        generator.run();
        verify(sendMailService, times(2)).sendEmail(argument.capture());
        
        ParticipantRosterProvider rosterProvider = (ParticipantRosterProvider)argument.getAllValues().get(0);
        NotifyOperationsEmailProvider emailProvider = (NotifyOperationsEmailProvider)argument.getAllValues().get(1);
        
        List<StudyParticipant> participants = rosterProvider.getParticipants();
        // They're all there
        assertEquals(2, participants.size());
        // Should be sorted by email addresses
        assertEquals("first.last@test.com", participants.get(0).getEmail());
        assertEquals("zanadine@test.com", participants.get(1).getEmail());
        // These objects should be fully realized
        StudyParticipant p = participants.get(0);
        assertEquals("First", p.getFirstName());
        assertEquals("Last", p.getLastName());
        assertEquals("first.last@test.com", p.getEmail());
        assertEquals("(206) 111-2222", p.get("phone"));
        assertEquals("true", p.get("can_recontact"));
        assertEquals("healthCode", p.getHealthCode());
        assertEquals("data-group-1", p.getDataGroups());
        assertNull(p.get("another_attribute"));

        // Notification was sent to sysops
        assertNotNull(emailProvider);
        String subject = "A participant roster has been emailed";
        String body = "The participant roster for the study 'Test Study [ParticipantRosterGeneratorTest]' has been emailed to 'bridge-testing+consent@sagebase.org'.";
        assertEquals(subject, emailProvider.getSubject());
        assertEquals(body, emailProvider.getMessage());
    }

    @Test
    public void generatorWithoutHealthCodeExportDoesntExportHealthCode() {
        study.setHealthCodeExportEnabled(false);
        generator.run();
        verify(sendMailService, times(2)).sendEmail(argument.capture());
        
        ParticipantRosterProvider rosterProvider = (ParticipantRosterProvider)argument.getAllValues().get(0);
        
        for (StudyParticipant participant : rosterProvider.getParticipants()) {
            assertEquals("", participant.getHealthCode());
        }
    }
    
    @Test
    public void failureToGenerateRosterSendsEmailToSysops() {
        when(healthCodeService.getMapping(anyString())).thenThrow(new RuntimeException("Something bad happened"));
        study.setHealthCodeExportEnabled(false);
        generator.run();
        
        verify(sendMailService, times(1)).sendEmail(argument.capture());
        NotifyOperationsEmailProvider emailProvider = (NotifyOperationsEmailProvider)argument.getValue();
        
        assertTrue(emailProvider.getMessage().contains("Something bad happened"));
        assertTrue(emailProvider.getMessage().contains("ParticipantRosterGenerator")); // dumb way to verify it's a stacktrace
        assertEquals("Generating participant roster failed for the study 'Test Study [ParticipantRosterGeneratorTest]'", emailProvider.getSubject());
    }
    
    @Test
    public void generatorFindsAndIncludesNamesOfMultipleConsentSubpopulations() {
        Iterator<Account> iterator = buildAccountIteratorWithMultipleConsentSignatures("lala@lala.com", "La", "La");
        generator = new ParticipantRosterGenerator(iterator, study, sendMailService, healthCodeService, optionsService, subpopService);
        generator.run();
        verify(sendMailService, times(2)).sendEmail(argument.capture());
        
        ParticipantRosterProvider rosterProvider = (ParticipantRosterProvider)argument.getAllValues().get(0);
        
        StudyParticipant participant = rosterProvider.getParticipants().get(0);
        String subpopNames = participant.getSubpopulationNames();
        assertTrue(subpopNames.contains("Consent One"));
        assertTrue(subpopNames.contains("Consent Two"));
        assertTrue(subpopNames.contains("Consent Three"));
        
        // Service is called only once despite the presence of multiple accounts.
        verify(subpopService, times(1)).getSubpopulations(study.getStudyIdentifier());
        verifyNoMoreInteractions(subpopService);
    }

    private Iterator<Account> buildAccountIterator() {
        Account account1 = createAccount("zanadine@test.com", "FirstZ", "LastZ", "(206) 333-444", "Subpop One", true);
        Account account2 = createAccount("first.last@test.com", "First", "Last", "(206) 111-2222", "Subpop Two", true);
        // Gail will not have the key for the consent record, and will be filtered out.
        Account account3 = createAccount("gail.tester@test.com", "Gail", "Tester", null, "Subpop One", false);
        
        return Lists.newArrayList(account1, account2, account3).iterator();
    }
    
    private Iterator<Account> buildAccountIteratorWithMultipleConsentSignatures(String email, String firstName, String lastName) {
        Account account = mock(Account.class);
        when(account.getEmail()).thenReturn(email);
        when(account.getFirstName()).thenReturn(firstName);
        when(account.getLastName()).thenReturn(lastName);
        
        Map<SubpopulationGuid,List<ConsentSignature>> map = new HashMap<>();
        addConsentToAccount(account, map, "Consent One", firstName, lastName);
        addConsentToAccount(account, map, "Consent Two", firstName, lastName);
        addConsentToAccount(account, map, "Consent Three", firstName, lastName);
        when(account.getAllConsentSignatureHistories()).thenReturn(map);
        
        return Lists.newArrayList(account).iterator();
    }
    
    private Account createAccount(String email, String firstName, String lastName, String phone, String subpopName, boolean hasConsented) {
        Account account = mock(Account.class);
        when(account.getEmail()).thenReturn(email);
        when(account.getFirstName()).thenReturn(firstName);
        when(account.getLastName()).thenReturn(lastName);
        when(account.getHealthId()).thenReturn(email);
        when(account.getAttribute("phone")).thenReturn(phone);
        when(account.getAttribute("can_recontact")).thenReturn("true");
        
        Map<SubpopulationGuid,List<ConsentSignature>> map = new HashMap<>();
        if (hasConsented) {
            addConsentToAccount(account, map, subpopName, firstName, lastName);
        }
        when(account.getAllConsentSignatureHistories()).thenReturn(map);
        return account;
    }
    
    private void addConsentToAccount(Account account, Map<SubpopulationGuid, List<ConsentSignature>> map, 
            String subpopName, String firstName, String lastName) {
        ConsentSignature sig = new ConsentSignature.Builder().withName(firstName + " " + lastName)
                .withBirthdate("1970-02-02").withSignedOn(DateUtils.getCurrentMillisFromEpoch()).build();

        SubpopulationGuid subpopGuid = SubpopulationGuid.create(subpopName);
        
        List<ConsentSignature> list = Lists.newArrayList(sig);
        map.put(subpopGuid, list);
        when(account.getActiveConsentSignature(subpopGuid)).thenReturn(sig);
        
        Subpopulation subpop = Subpopulation.create();
        subpop.setName(subpopGuid.getGuid());
        subpop.setGuid(subpopGuid);
        studySubpopulations.add(subpop);
    }
}
