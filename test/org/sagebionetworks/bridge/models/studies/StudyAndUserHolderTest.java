package org.sagebionetworks.bridge.models.studies;

import static org.junit.Assert.assertEquals;

import java.util.LinkedHashSet;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;

public class StudyAndUserHolderTest {
    private static final String TEST_STUDY_ID = "test-study-id";
    private static final String TEST_STUDY_NAME = "test=study-name";
    private static final String TEST_USER_EMAIL = "test+user@email.com";
    private static final String TEST_USER_EMAIL_2 = "test+user+2@email.com";
    private static final String TEST_USER_FIRST_NAME = "test_user_first_name";
    private static final String TEST_USER_LAST_NAME = "test_user_last_name";
    private static final String TEST_USER_PASSWORD = "test_user_password";

    @Test
    public void deserializeCorrectly() throws Exception {
        // mock
        Study study = new DynamoStudy();
        study.setActive(true);
        study.setIdentifier(TEST_STUDY_ID);
        study.setName(TEST_STUDY_NAME);
        study.setSupportEmail(TEST_USER_EMAIL);

        // make it ordered
        LinkedHashSet<Roles> user1Roles = new LinkedHashSet<>();
        user1Roles.add(Roles.RESEARCHER);
        user1Roles.add(Roles.DEVELOPER);

        StudyParticipant mockUser1 = new StudyParticipant.Builder()
                .withEmail(TEST_USER_EMAIL)
                .withFirstName(TEST_USER_FIRST_NAME)
                .withLastName(TEST_USER_LAST_NAME)
                .withRoles(ImmutableSet.copyOf(user1Roles))
                .withPassword(TEST_USER_PASSWORD)
                .build();

        StudyParticipant mockUser2 = new StudyParticipant.Builder()
                .withEmail(TEST_USER_EMAIL_2)
                .withFirstName(TEST_USER_FIRST_NAME)
                .withLastName(TEST_USER_LAST_NAME)
                .withRoles(ImmutableSet.of(Roles.RESEARCHER))
                .withPassword(TEST_USER_PASSWORD)
                .build();

        List<StudyParticipant> mockUsers = ImmutableList.of(mockUser1, mockUser2);

        StudyAndUserHolder holder = new StudyAndUserHolder(study, mockUsers);

        String studyAndUserString = BridgeObjectMapper.get().writeValueAsString(holder);
        JsonNode studyAndUserNode = BridgeObjectMapper.get().readTree(studyAndUserString);
        JsonNode studyNode = studyAndUserNode.get("study");
        ArrayNode users = (ArrayNode) studyAndUserNode.get("users");

        // verify
        assertEquals(TEST_STUDY_ID, studyNode.get("identifier").asText());
        assertEquals(TEST_STUDY_NAME, studyNode.get("name").asText());
        assertEquals(TEST_USER_EMAIL, studyNode.get("supportEmail").asText());
        assertEquals(true, studyNode.get("active").asBoolean());

        JsonNode user1 = users.get(0);

        assertEquals(TEST_USER_EMAIL, user1.get("email").asText());
        assertEquals(TEST_USER_FIRST_NAME, user1.get("firstName").asText());
        assertEquals(TEST_USER_LAST_NAME, user1.get("lastName").asText());
        assertEquals(TEST_USER_PASSWORD, user1.get("password").asText());
        ArrayNode rolesUser1 = (ArrayNode) user1.get("roles");
        assertEquals(Roles.DEVELOPER.toString(), rolesUser1.get(0).asText().toUpperCase());
        assertEquals(Roles.RESEARCHER.toString(), rolesUser1.get(1).asText().toUpperCase());

        JsonNode user2 = users.get(1);

        assertEquals(TEST_USER_EMAIL_2, user2.get("email").asText());
        assertEquals(TEST_USER_FIRST_NAME, user2.get("firstName").asText());
        assertEquals(TEST_USER_LAST_NAME, user2.get("lastName").asText());
        assertEquals(TEST_USER_PASSWORD, user2.get("password").asText());
        ArrayNode rolesUser2 = (ArrayNode) user2.get("roles");
        assertEquals(Roles.RESEARCHER.toString(), rolesUser2.get(0).asText().toUpperCase());
    }
}
