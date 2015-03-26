package org.sagebionetworks.bridge.services.email;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.models.UserProfile;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyParticipant;

import com.google.common.collect.Lists;

public class ParticipantRosterProviderTest {
    
    private Study study;

    @Before
    public void setUp() throws Exception {
        study = new DynamoStudy();
        study.setName("Test Study");
        study.setConsentNotificationEmail("consent-notification@test.com");
        study.getUserProfileAttributes().add("recontact");
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

        ParticipantRosterProvider provider = new ParticipantRosterProvider(study, participants);
        String output = provider.createInlineParticipantRoster();
        
        assertEquals("There is 1 user enrolled in this study. Please see the attached TSV file.\n", output);
        
        StudyParticipant numberTwo = new StudyParticipant();
        numberTwo.setEmail("test2@test.com");
        
        participants.add(numberTwo);
        output = provider.createInlineParticipantRoster();
        assertTrue(output.contains("There are 2 users enrolled in this study. Please see the attached TSV file.\n"));
        
        participants.clear();
        output = provider.createInlineParticipantRoster();
        assertTrue(output.contains("There are no users enrolled in this study.\n"));
    }
    
    @Test
    public void participantsCorrectlyDescribedInCSV() {
        StudyParticipant participant = new StudyParticipant();
        participant.setFirstName("First");
        participant.setLastName("Last");
        participant.setEmail("test@test.com");
        participant.setPhone("(123)\t456-7890"); // Tab snuck into this string should be converted to a space
        participant.put("recontact", "false");
        participant.put(UserProfile.SHARING_SCOPE_FIELD, SharingScope.NO_SHARING.name());
        List<StudyParticipant> participants = Lists.newArrayList(participant);

        ParticipantRosterProvider provider = new ParticipantRosterProvider(study, participants);
        String output = provider.createParticipantTSV();
        
        // assertEquals("Email\tFirst Name\tLast Name\tSharing Scope\tExternal ID\tPhone\tRecontact\ntest@test.com\tFirst\tLast\tNot Sharing\t\t(123) 456-7890\tfalse\n", output);
        assertEquals("Email\tFirst Name\tLast Name\tSharing Scope\tPhone\tRecontact\ntest@test.com\tFirst\tLast\tNot Sharing\t(123) 456-7890\tfalse\n", output);
        
        participant.setLastName(null);
        output = provider.createParticipantTSV();
        // assertEquals("Email\tFirst Name\tLast Name\tSharing Scope\tExternal ID\tPhone\tRecontact\ntest@test.com\tFirst\t\tNot Sharing\t\t(123) 456-7890\tfalse\n", output);
        assertEquals("Email\tFirst Name\tLast Name\tSharing Scope\tPhone\tRecontact\ntest@test.com\tFirst\t\tNot Sharing\t(123) 456-7890\tfalse\n", output);
        
        participant.setFirstName(null);
        participant.setLastName("Last");
        output = provider.createParticipantTSV();
        // assertEquals("Email\tFirst Name\tLast Name\tSharing Scope\tExternal ID\tPhone\tRecontact\ntest@test.com\t\tLast\tNot Sharing\t\t(123) 456-7890\tfalse\n", output);
        assertEquals("Email\tFirst Name\tLast Name\tSharing Scope\tPhone\tRecontact\ntest@test.com\t\tLast\tNot Sharing\t(123) 456-7890\tfalse\n", output);
        
        participant.setPhone(null);
        output = provider.createParticipantTSV();
        // assertEquals("Email\tFirst Name\tLast Name\tSharing Scope\tExternal ID\tPhone\tRecontact\ntest@test.com\t\tLast\tNot Sharing\t\t\tfalse\n", output);
        assertEquals("Email\tFirst Name\tLast Name\tSharing Scope\tPhone\tRecontact\ntest@test.com\t\tLast\tNot Sharing\t\tfalse\n", output);
        
        participant.remove(UserProfile.SHARING_SCOPE_FIELD);
        output = provider.createParticipantTSV();
        // assertEquals("Email\tFirst Name\tLast Name\tSharing Scope\tExternal ID\tPhone\tRecontact\ntest@test.com\t\tLast\t\t\t\tfalse\n", output);
        assertEquals("Email\tFirst Name\tLast Name\tSharing Scope\tPhone\tRecontact\ntest@test.com\t\tLast\t\t\tfalse\n", output);
        
        StudyParticipant numberTwo = new StudyParticipant();
        numberTwo.setEmail("test2@test.com");
        
        // This is pretty broken, but you should still get output. 
        participants.add(numberTwo);
        output = provider.createParticipantTSV();
        
        // assertEquals("Email\tFirst Name\tLast Name\tSharing Scope\tExternal ID\tPhone\tRecontact\ntest@test.com\t\tLast\t\t\t\tfalse\ntest2@test.com\t\t\t\t\t\t\n", output);
        assertEquals("Email\tFirst Name\tLast Name\tSharing Scope\tPhone\tRecontact\ntest@test.com\t\tLast\t\t\tfalse\ntest2@test.com\t\t\t\t\t\n", output);
        
        participants.clear();
        output = provider.createParticipantTSV();
        assertEquals("Email\tFirst Name\tLast Name\tSharing Scope\tPhone\tRecontact\n", output);
    }

}
