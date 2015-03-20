package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.notNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyParticipant;

import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClient;
import com.amazonaws.services.simpleemail.model.SendRawEmailRequest;
import com.amazonaws.services.simpleemail.model.SendRawEmailResult;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;

public class SendMailViaAmazonServiceParticipantRosterTest {
    
    private Study study;
    private SendMailViaAmazonService service;
    private AmazonSimpleEmailServiceClient emailClient;
    private ArgumentCaptor<SendRawEmailRequest> argument;

    @Before
    public void setUp() throws Exception {
        study = new DynamoStudy();
        study.setName("Test Study");
        study.setConsentNotificationEmail("consent-notification@test.com");
        study.getUserProfileAttributes().add("recontact");

        emailClient = mock(AmazonSimpleEmailServiceClient.class);
        when(emailClient.sendRawEmail(notNull(SendRawEmailRequest.class))).thenReturn(
            new SendRawEmailResult().withMessageId("test-message-id"));
        argument = ArgumentCaptor.forClass(SendRawEmailRequest.class);

        service = new SendMailViaAmazonService();
        service.setSupportEmail("test-sender@sagebase.org");
        service.setEmailClient(emailClient);
    }
    
    @Test
    public void participantsCorrectlyDescribedInText() {
        StudyParticipant participant = new StudyParticipant();
        participant.setFirstName("First");
        participant.setLastName("Last");
        participant.setEmail("test@test.com");
        participant.setPhone("(123) 456-7890");
        participant.put("recontact", "true");
        List<StudyParticipant> participants = Lists.newArrayList(participant);

        String output = service.createInlineParticipantRoster(study, participants);
        
        assertEquals("There is 1 user enrolled in this study:\n\ntest@test.com (First Last)\nPhone: (123) 456-7890\nRecontact: true\n", output);
        
        participant.setLastName(null);
        output = service.createInlineParticipantRoster(study, participants);
        assertEquals("There is 1 user enrolled in this study:\n\ntest@test.com (First)\nPhone: (123) 456-7890\nRecontact: true\n", output);
        
        participant.setFirstName(null);
        participant.setLastName("Last");
        output = service.createInlineParticipantRoster(study, participants);
        assertEquals("There is 1 user enrolled in this study:\n\ntest@test.com (Last)\nPhone: (123) 456-7890\nRecontact: true\n", output);
        
        participant.setPhone(null);
        output = service.createInlineParticipantRoster(study, participants);
        assertEquals("There is 1 user enrolled in this study:\n\ntest@test.com (Last)\nPhone: \nRecontact: true\n", output);
        
        participant.remove("recontact");
        output = service.createInlineParticipantRoster(study, participants);
        assertEquals("There is 1 user enrolled in this study:\n\ntest@test.com (Last)\nPhone: \nRecontact: \n", output);
        
        StudyParticipant numberTwo = new StudyParticipant();
        numberTwo.setEmail("test2@test.com");
        
        participants.add(numberTwo);
        output = service.createInlineParticipantRoster(study, participants);
        assertTrue(output.contains("There are 2 users enrolled in this study:"));
        
        participants.clear();
        output = service.createInlineParticipantRoster(study, participants);
        assertTrue(output.contains("There are no users enrolled in this study."));
    }
    
    @Test
    public void participantsCorrectlyDescribedInCSV() {
        StudyParticipant participant = new StudyParticipant();
        participant.setFirstName("First");
        participant.setLastName("Last");
        participant.setEmail("test@test.com");
        // Tab snuck into this string should be converted to a space
        participant.setPhone("(123)\t456-7890");
        List<StudyParticipant> participants = Lists.newArrayList(participant);

        String output = service.createParticipantCSV(study, participants);
        assertEquals("Email\tFirst Name\tLast Name\tPhone\tRecontact\ntest@test.com\tFirst\tLast\t(123) 456-7890\t\n", output);
        
        participant.setLastName(null);
        output = service.createParticipantCSV(study, participants);
        assertEquals("Email\tFirst Name\tLast Name\tPhone\tRecontact\ntest@test.com\tFirst\t\t(123) 456-7890\t\n", output);
        
        participant.setFirstName(null);
        participant.setLastName("Last");
        output = service.createParticipantCSV(study, participants);
        assertEquals("Email\tFirst Name\tLast Name\tPhone\tRecontact\ntest@test.com\t\tLast\t(123) 456-7890\t\n", output);
        
        participant.setPhone(null);
        output = service.createParticipantCSV(study, participants);
        assertEquals("Email\tFirst Name\tLast Name\tPhone\tRecontact\ntest@test.com\t\tLast\t\t\n", output);
        
        StudyParticipant numberTwo = new StudyParticipant();
        numberTwo.setEmail("test2@test.com");
        
        participants.add(numberTwo);
        output = service.createParticipantCSV(study, participants);
        assertEquals("Email\tFirst Name\tLast Name\tPhone\tRecontact\ntest@test.com\t\tLast\t\t\ntest2@test.com\t\t\t\t\n", output);
        
        participants.clear();
        output = service.createParticipantCSV(study, participants);
        assertEquals("Email\tFirst Name\tLast Name\tPhone\tRecontact\n", output);
    }
    
    @Test
    public void sendParticipantRoster() {
        StudyParticipant participant = new StudyParticipant();
        participant.setFirstName("First");
        participant.setLastName("Last");
        participant.setEmail("test@test.com");
        participant.setPhone("(123) 456-7890");
        List<StudyParticipant> participants = Lists.newArrayList(participant);
        
        service.sendStudyParticipantsRoster(study, participants);
        
        verify(emailClient).sendRawEmail(argument.capture());
        
        SendRawEmailRequest req = argument.getValue();
        assertEquals("Correct sender", "test-sender@sagebase.org", req.getSource());

        // validate to
        List<String> toList = req.getDestinations();
        assertEquals("Correct number of recipients", 1, toList.size());
        assertEquals("Correct recipient", "consent-notification@test.com", toList.get(0));

        // Validate message content. MIME message must be ASCII
        String rawMessage = new String(req.getRawMessage().getData().array(), Charsets.US_ASCII);
        
        assertTrue("Has right subject", 
            rawMessage.contains("Study participants for Test Study"));
        assertTrue("CSV has the participant", 
            rawMessage.contains("Email\tFirst Name\tLast Name\tPhone\tRecontact\ntest@test.com\tFirst\tLast\t(123) 456-7890"));
        assertTrue("text description of participant", 
            rawMessage.contains("There is 1 user enrolled in this study:\n\ntest@test.com (First Last)\nPhone: (123) 456-7890"));
    }
}
