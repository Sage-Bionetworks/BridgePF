package org.sagebionetworks.bridge.play.controllers;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.DynamoPagedResourceList;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.services.ExternalIdService;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import play.mvc.Result;
import play.test.Helpers;

@RunWith(MockitoJUnitRunner.class)
public class ExternalIdControllerTest {
    
    @Mock
    ExternalIdService externalIdService;
    
    ExternalIdController controller;
    
    @Before
    public void before() throws Exception {
        controller = new ExternalIdController();
        controller.setExternalIdService(externalIdService);
        
        DynamoPagedResourceList<String> page = new DynamoPagedResourceList<>(Lists.newArrayList("AAA", "BBB", "CCC"),
                "CCC", 5, 10, Maps.newHashMap());
        
        when(externalIdService.getExternalIds(any(), any(), any(), any(), any())).thenReturn(page);

        User user = new User();
        user.setHealthCode("BBB");
        
        UserSession session = mock(UserSession.class);
        when(session.getUser()).thenReturn(user);
        when(session.getStudyIdentifier()).thenReturn(new StudyIdentifierImpl("test-study"));
        
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();
        doReturn(session).when(controller).getAuthenticatedSession();

        TestUtils.mockPlayContext();
    }

    @Test
    public void getExternalIds() throws Exception {
        Result result = controller.getExternalIds(null, null, null, null);
        
        String message = BridgeObjectMapper.get().readTree(Helpers.contentAsString(result)).get("message").asText();
        System.out.println(message);
    }
    
    @Test
    public void addExternalIds() {
    }
    
}
