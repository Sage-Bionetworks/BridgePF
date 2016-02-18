package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import play.mvc.Result;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.models.accounts.DataGroups;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.services.StudyService;
import org.sagebionetworks.bridge.services.UserAdminService;

public class UserManagementControllerUpdateDataGroupsTest {
    private static final String TEST_EMAIL = "email@example.com";

    @Test
    public void test() throws Exception {
        // Spy controller. Mock session.
        UserSession mockSession = new UserSession();
        mockSession.setStudyIdentifier(TestConstants.TEST_STUDY);

        UserManagementController controller = spy(new UserManagementController());
        doReturn(mockSession).when(controller).getAuthenticatedSession(Roles.RESEARCHER);

        // Mock study service
        DynamoStudy mockStudy = new DynamoStudy();
        StudyService mockStudyService = mock(StudyService.class);
        when(mockStudyService.getStudy(TestConstants.TEST_STUDY)).thenReturn(mockStudy);
        controller.setStudyService(mockStudyService);

        // Mock request JSON.
        String requestJsonText = "{\n" +
                "   \"dataGroups\":[\"foo\", \"bar\", \"baz\"]\n" +
                "}";
        TestUtils.mockPlayContextWithJson(requestJsonText);

        // Mock User Admin Service
        UserAdminService mockUserAdminService = mock(UserAdminService.class);
        controller.setUserAdminService(mockUserAdminService);

        // execute and validate
        Result result = controller.updateDataGroupForUser(TEST_EMAIL);
        assertEquals(200, result.status());

        // verify it called through to the User Admin Service
        ArgumentCaptor<DataGroups> dataGroupsCaptor = ArgumentCaptor.forClass(DataGroups.class);
        verify(mockUserAdminService).updateDataGroupForUser(same(mockStudy), eq(TEST_EMAIL),
                dataGroupsCaptor.capture());

        DataGroups dataGroups = dataGroupsCaptor.getValue();
        assertEquals(ImmutableSet.of("foo", "bar", "baz"), dataGroups.getDataGroups());
    }
}
