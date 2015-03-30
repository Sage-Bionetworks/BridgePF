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
import com.newrelic.agent.deps.com.google.common.base.Joiner;

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
        participant.setNotifyByEmail(Boolean.FALSE);
        participant.put("recontact", "true");
        List<StudyParticipant> participants = Lists.newArrayList(participant);

        ParticipantRosterProvider provider = new ParticipantRosterProvider(study, participants);
        
        assertEquals("There is 1 user enrolled in this study. Please see the attached TSV file.\n", provider.createInlineParticipantRoster());
        
        StudyParticipant numberTwo = new StudyParticipant();
        numberTwo.setEmail("test2@test.com");
        participants.add(numberTwo);
        
        assertTrue(provider.createInlineParticipantRoster().contains(
            "There are 2 users enrolled in this study. Please see the attached TSV file.\n"));
        
        participants.clear();
        assertTrue(provider.createInlineParticipantRoster().contains(
            "There are no users enrolled in this study.\n"));
    }
    
    @Test
    public void participantsCorrectlyDescribedInCSV() {
        StudyParticipant participant = new StudyParticipant();
        participant.setFirstName("First");
        participant.setLastName("Last");
        participant.setEmail("test@test.com");
        participant.setPhone("(123)\t456-7890"); // Tab snuck into this string should be converted to a space
        participant.setNotifyByEmail(Boolean.FALSE);
        participant.put("recontact", "false");
        participant.put(UserProfile.SHARING_SCOPE_FIELD, SharingScope.NO_SHARING.name());
        List<StudyParticipant> participants = Lists.newArrayList(participant);

        String headerString = row("Email", "First Name", "Last Name", "Phone", "Sharing Scope", "Email Notifications", "Recontact");
        
        ParticipantRosterProvider provider = new ParticipantRosterProvider(study, participants);
        String output = headerString + row("test@test.com", "First", "Last", "(123) 456-7890", "Not Sharing", "false", "false");
        assertEquals(output, provider.createParticipantTSV());
        
        participant.setLastName(null);
        output = headerString + row("test@test.com","First","","(123) 456-7890","Not Sharing","false","false");
        assertEquals(output, provider.createParticipantTSV());
        
        participant.setFirstName(null);
        participant.setLastName("Last");
        output = headerString + row("test@test.com","","Last","(123) 456-7890","Not Sharing","false","false");
        assertEquals(output, provider.createParticipantTSV());
        
        participant.setPhone(null);
        output = headerString + row("test@test.com","","Last","","Not Sharing","false","false");
        assertEquals(output, provider.createParticipantTSV());
        
        participant.remove(UserProfile.SHARING_SCOPE_FIELD);
        output = headerString + row("test@test.com","","Last","","","false","false");
        assertEquals(output, provider.createParticipantTSV());
        
        StudyParticipant numberTwo = new StudyParticipant();
        numberTwo.setEmail("test2@test.com");
        
        // This is pretty broken, but you should still get output. 
        participants.add(numberTwo);
        output = headerString + row("test@test.com","","Last","","","false","false") + row("test2@test.com","","","","","","");
        assertEquals(output, provider.createParticipantTSV());
        
        participants.clear();
        assertEquals(headerString, provider.createParticipantTSV());
    }
    
    private String row(String... fields) {
        return Joiner.on("\t").join(fields) + "\n";
    }

}
