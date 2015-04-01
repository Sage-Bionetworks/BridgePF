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
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyParticipant;
import org.sagebionetworks.bridge.services.email.ParticipantRosterProvider;

import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClient;
import com.amazonaws.services.simpleemail.model.SendRawEmailRequest;
import com.amazonaws.services.simpleemail.model.SendRawEmailResult;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
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
        study.getUserProfileAttributes().add("phone");
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
    public void sendParticipantRoster() {
        StudyParticipant participant = new StudyParticipant();
        participant.setFirstName("First");
        participant.setLastName("Last");
        participant.setEmail("test@test.com");
        participant.put("phone", "(123) 456-7890");
        participant.setSharingScope(SharingScope.ALL_QUALIFIED_RESEARCHERS);
        participant.setNotifyByEmail(Boolean.FALSE);
        List<StudyParticipant> participants = Lists.newArrayList(participant);
        
        String header = row("Email", "First Name", "Last Name", "Sharing Scope", "Email Notifications", "Phone", "Recontact");
        
        ParticipantRosterProvider provider = new ParticipantRosterProvider(study, participants);
        service.sendEmail(provider);
        
        verify(emailClient).sendRawEmail(argument.capture());
        
        SendRawEmailRequest req = argument.getValue();
        assertEquals("Correct sender", "test-sender@sagebase.org", req.getSource());

        // validate to
        List<String> toList = req.getDestinations();
        assertEquals("Correct number of recipients", 1, toList.size());
        assertEquals("Correct recipient", "consent-notification@test.com", toList.get(0));

        // Validate message content. MIME message must be ASCII
        String rawMessage = new String(req.getRawMessage().getData().array(), Charsets.UTF_8);
        
        assertTrue("Has right subject", rawMessage.contains("Study participants for Test Study"));
        String output = header + row("test@test.com", "First", "Last", "All Qualified Researchers", "false", "(123) 456-7890", "");
        
        assertTrue("TSV has the participant", rawMessage.contains(output));
        assertTrue("text description of participant", rawMessage.contains("There is 1 user enrolled in this study."));
    }
    
    private String row(String... values) {
        return Joiner.on("\t").join(values) + "\n";
    }
}
