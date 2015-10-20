package org.sagebionetworks.bridge.services.email;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Iterator;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.dynamodb.OptionLookup;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.HealthId;
import org.sagebionetworks.bridge.models.studies.ConsentSignature;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyParticipant;
import org.sagebionetworks.bridge.services.HealthCodeService;
import org.sagebionetworks.bridge.services.ParticipantOptionsService;
import org.sagebionetworks.bridge.services.ParticipantRosterGenerator;
import org.sagebionetworks.bridge.services.SendMailService;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class ParticipantRosterGeneratorTest {

    private Study study;
    
    private ParticipantRosterGenerator generator;
    
    private ArgumentCaptor<ParticipantRosterProvider> argument;
    
    private SendMailService sendMailService;
    
    private HealthCodeService healthCodeService;
    
    private ParticipantOptionsService optionsService;
    
    @Before
    public void before() {
        study = TestUtils.getValidStudy(ParticipantRosterGeneratorTest.class);
        study.setUserProfileAttributes(Sets.newHashSet("phone", "can_recontact"));
        
        @SuppressWarnings("unchecked")
        Class<ParticipantRosterProvider> providerClass = (Class<ParticipantRosterProvider>)(Class<?>)List.class;
        argument = ArgumentCaptor.forClass(providerClass);
        
        sendMailService = mock(SendMailService.class);
        healthCodeService = mock(HealthCodeService.class);
        optionsService = mock(ParticipantOptionsService.class);
        
        HealthId healthId = new HealthId() {
            @Override public String getId() {
                return "healthId";
            }
            @Override public String getCode() {
                return "healthCode";
            }
        };
        
        OptionLookup emailLookup = mock(OptionLookup.class);
        when(emailLookup.get(anyString())).thenReturn(Boolean.TRUE.toString());
        when(optionsService.getOptionForAllStudyParticipants(study, ParticipantOption.EMAIL_NOTIFICATIONS)).thenReturn(emailLookup);

        OptionLookup sharingLookup = mock(OptionLookup.class);
        when(sharingLookup.getSharingScope(anyString())).thenReturn(SharingScope.ALL_QUALIFIED_RESEARCHERS);
        when(healthCodeService.getMapping(anyString())).thenReturn(healthId);
        when(optionsService.getOptionForAllStudyParticipants(study, ParticipantOption.SHARING_SCOPE)).thenReturn(sharingLookup);
        
        Account account1 = createAccount("zanadine@test.com", "FirstZ", "LastZ", "(206) 333-444", true);
        Account account2 = createAccount("first.last@test.com", "First", "Last", "(206) 111-2222", true);
        // Gail will not have the key for the consent record, and will be filtered out.
        Account account3 = createAccount("gail.tester@test.com", "Gail", "Tester", null, false);
        
        Iterator<Account> iterator = Lists.newArrayList(account1, account2, account3).iterator();
        
        generator = new ParticipantRosterGenerator(iterator, study, sendMailService, healthCodeService, optionsService);
    }
    
    @Test
    public void generatorCreatesRoster() {
        generator.run();
        verify(sendMailService).sendEmail(argument.capture());
        
        ParticipantRosterProvider provider = argument.getValue();
        
        List<StudyParticipant> participants = provider.getParticipants();
        
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
        assertNull(p.get("another_attribute"));
    }

    private Account createAccount(String email, String firstName, String lastName, String phone, boolean hasConsented) {
        Account account = mock(Account.class);
        when(account.getEmail()).thenReturn(email);
        when(account.getFirstName()).thenReturn(firstName);
        when(account.getLastName()).thenReturn(lastName);
        when(account.getHealthId()).thenReturn(email);
        when(account.getAttribute("phone")).thenReturn(phone);
        when(account.getAttribute("can_recontact")).thenReturn("true");
        if (hasConsented) {
            ConsentSignature sig = ConsentSignature.create(firstName + " " + lastName, "1970-02-02", null, null,
                    DateUtils.getCurrentMillisFromEpoch());
            when(account.getConsentSignature()).thenReturn(sig);
        }
        return account;
    }
}
