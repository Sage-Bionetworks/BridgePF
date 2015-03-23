package org.sagebionetworks.bridge.services.email;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Iterator;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.studies.ConsentSignature;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyParticipant;
import org.sagebionetworks.bridge.services.ParticipantRosterGenerator;
import org.sagebionetworks.bridge.services.SendMailService;
import org.sagebionetworks.bridge.services.email.ParticipantRosterProvider;

import com.google.common.collect.Lists;

public class ParticipantRosterGeneratorTest {

    private Study study;
    
    private ParticipantRosterGenerator generator;
    
    private ArgumentCaptor<ParticipantRosterProvider> argument;
    
    private SendMailService sendMailService;
    
    @Before
    public void before() {
        study = new DynamoStudy();
        study.setIdentifier("test");
        study.getUserProfileAttributes().add("can_recontact");
        
        @SuppressWarnings("unchecked")
        Class<ParticipantRosterProvider> providerClass = (Class<ParticipantRosterProvider>)(Class<?>)List.class;
        argument = ArgumentCaptor.forClass(providerClass);
        
        sendMailService = mock(SendMailService.class);
        
        Account account1 = createAccount("zanadine@test.com", "FirstZ", "LastZ", "(206) 333-444", true);
        Account account2 = createAccount("first.last@test.com", "First", "Last", "(206) 111-2222", true);
        // Gail will not have the key for the consent record, and will be filtered out.
        Account account3 = createAccount("gail.tester@test.com", "Gail", "Tester", null, false);
        
        Iterator<Account> iterator = Lists.newArrayList(account1, account2, account3).iterator();
        
        generator = new ParticipantRosterGenerator(iterator, study, sendMailService);
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
        assertEquals("(206) 111-2222", p.getPhone());
        assertEquals("true", p.get("can_recontact"));
        assertNull(p.get("another_attribute"));
    }

    private Account createAccount(String email, String firstName, String lastName, String phone, boolean hasConsented) {
        Account account = mock(Account.class);
        when(account.getEmail()).thenReturn(email);
        when(account.getFirstName()).thenReturn(firstName);
        when(account.getLastName()).thenReturn(lastName);
        when(account.getPhone()).thenReturn(phone);
        when(account.getAttribute("can_recontact")).thenReturn("true");
        if (hasConsented) {
            ConsentSignature sig = ConsentSignature.create(firstName + " " + lastName, "1970-02-02", null, null);
            when(account.getConsentSignature()).thenReturn(sig);
        }
        return account;
    }
}
