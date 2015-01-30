package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.sagebionetworks.bridge.crypto.AesGcmEncryptor;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyParticipant;

import com.google.common.collect.Lists;
import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.account.AccountCriteria;
import com.stormpath.sdk.account.AccountList;
import com.stormpath.sdk.application.Application;
import com.stormpath.sdk.directory.CustomData;

public class ParticipantRosterGeneratorTest {

    private Study study;
    
    private ParticipantRosterGenerator generator;
    
    private ArgumentCaptor<List<StudyParticipant>> argument;
    
    private SendMailService sendMailService;
    
    @Before
    public void before() {
        study = new DynamoStudy();
        study.setIdentifier("test");
        
        @SuppressWarnings("unchecked")
        Class<List<StudyParticipant>> listClass = (Class<List<StudyParticipant>>)(Class<?>)List.class;
        argument = ArgumentCaptor.forClass(listClass);
        
        AesGcmEncryptor encryptor = mock(AesGcmEncryptor.class);
        when(encryptor.decrypt(any(String.class))).then(returnsFirstArg());
        UserProfileServiceImpl userProfileService = new UserProfileServiceImpl();
        userProfileService.setHealthCodeEncryptor(encryptor);
        
        sendMailService = mock(SendMailService.class);
        
        Account account1 = createAccount("zanadine@test.com", "FirstZ", "LastZ", "(206) 333-444", true);
        Account account2 = createAccount("first.last@test.com", "First", "Last", "(206) 111-2222", true);
        // Gail will not have the key for the consent record, and will be filtered out.
        Account account3 = createAccount("gail.tester@test.com", "Gail", "Tester", null, false);
        AccountList list = mock(AccountList.class);
        when(list.iterator()).thenReturn(Lists.newArrayList(account1, account2, account3).iterator());
        
        Application app = mock(Application.class);
        when(app.getAccounts(any(AccountCriteria.class))).thenReturn(list);
        
        generator = new ParticipantRosterGenerator(app, study, userProfileService, sendMailService);
    }
    
    @Test
    public void generatorCreatesRoster() {
        generator.run();
        verify(sendMailService).sendStudyParticipantsRoster(eq(study), argument.capture());
        
        List<StudyParticipant> participants = argument.getValue();
        
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
    }

    private Account createAccount(String email, String firstName, String lastName, String phone, boolean hasConsented) {
        CustomData data = mock(CustomData.class);
        when(data.get(any())).thenReturn(phone);
        when(data.containsKey(any())).thenReturn(hasConsented);
        
        Account account = mock(Account.class);
        when(account.getEmail()).thenReturn(email);
        when(account.getGivenName()).thenReturn(firstName);
        when(account.getSurname()).thenReturn(lastName);
        when(account.getCustomData()).thenReturn(data);
        return account;
    }
}
