package org.sagebionetworks.bridge.models.studies;

import static org.junit.Assert.assertEquals;

import java.util.LinkedHashSet;
import java.util.List;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;

public class StudyAndUsersTest {
    private static final String TEST_STUDY_ID = "test-study-id";
    private static final String TEST_STUDY_NAME = "test=study-name";
    private static final String TEST_USER_EMAIL = "test+user@email.com";
    private static final String TEST_USER_EMAIL_2 = "test+user+2@email.com";
    private static final String TEST_USER_FIRST_NAME = "test_user_first_name";
    private static final String TEST_USER_LAST_NAME = "test_user_last_name";
    private static final String TEST_USER_PASSWORD = "test_user_password";
    private static final String TEST_ADMIN_ID_1 = "3346407";
    private static final String TEST_ADMIN_ID_2 = "3348228";

    @Test
    public void deserializeCorrectly() throws Exception {
        // mock
        String json = "{\n" +
                "\t\"adminIds\": [\"3346407\", \"3348228\"],\n" +
                "\t\"study\": {\n" +
                "\t  \"identifier\": \"test-study-id\",\n" +
                "\t  \"supportEmail\": \"test+user@email.com\",\n" +
                "\t  \"name\": \"test=study-name\",\n" +
                "\t  \"active\": \"true\"\n" +
                "\t},\n" +
                "\t\"users\": [\n" +
                "\t\t{\n" +
                "\t\t\t\"firstName\": \"test_user_first_name\",\n" +
                "\t\t\t\"lastName\": \"test_user_last_name\",\n" +
                "\t\t\t\"email\": \"test+user@email.com\",\n" +
                "\t\t\t\"password\": \"test_user_password\",\n" +
                "\t\t\t\"roles\": [\"developer\",\"researcher\"]\n" +
                "\t\t},\n" +
                "\t\t{\n" +
                "\t\t\t\"firstName\": \"test_user_first_name\",\n" +
                "\t\t\t\"lastName\": \"test_user_last_name\",\n" +
                "\t\t\t\"email\": \"test+user+2@email.com\",\n" +
                "\t\t\t\"password\": \"test_user_password\",\n" +
                "\t\t\t\"roles\": [\"researcher\"]\n" +
                "\t\t}\n" +
                "\t]\n" +
                "}";

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
        List<String> adminIds = ImmutableList.of(TEST_ADMIN_ID_1, TEST_ADMIN_ID_2);

        StudyAndUsers retStudyAndUsers = BridgeObjectMapper.get().readValue(json, StudyAndUsers.class);
        List<String> retAdminIds = retStudyAndUsers.getAdminIds();
        Study retStudy = retStudyAndUsers.getStudy();
        List<StudyParticipant> userList = retStudyAndUsers.getUsers();

        // verify
        assertEquals(adminIds, retAdminIds);
        assertEquals(study, retStudy);
        assertEquals(mockUsers, userList);
    }
}
