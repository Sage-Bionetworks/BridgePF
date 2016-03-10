package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.AccountStatus;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.ParticipantService;
import org.sagebionetworks.bridge.services.StudyService;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.Lists;

import play.mvc.Result;
import play.test.Helpers;

@RunWith(MockitoJUnitRunner.class)
public class ParticipantControllerTest {
    
    private static final TypeReference<PagedResourceList<AccountSummary>> ACCOUNT_SUMMARY_PAGE = new TypeReference<PagedResourceList<AccountSummary>>(){};

    private static final AccountSummary SUMMARY = new AccountSummary("firstName","lastName","email",AccountStatus.ENABLED);
    
    private static final Study STUDY = new DynamoStudy();
    
    @Spy
    private ParticipantController controller;
    
    @Mock
    private ParticipantService participantService;
    
    @Mock
    private StudyService studyService;
    
    @Before
    public void before() throws Exception {
        UserSession session = new UserSession();
        session.setStudyIdentifier(TestConstants.TEST_STUDY);
        
        doReturn(session).when(controller).getAuthenticatedSession(Roles.RESEARCHER);
        
        when(studyService.getStudy(TestConstants.TEST_STUDY)).thenReturn(STUDY);
        
        List<AccountSummary> summaries = Lists.newArrayListWithCapacity(3);
        summaries.add(SUMMARY);
        summaries.add(SUMMARY);
        summaries.add(SUMMARY);
        
        PagedResourceList<AccountSummary> page = new PagedResourceList<>(summaries, 10, 20, 30);
        
        when(participantService.getPagedAccountSummaries(eq(STUDY), anyInt(), anyInt())).thenReturn(page);
        
        controller.setParticipantService(participantService);
        controller.setStudyService(studyService);
        
        TestUtils.mockPlayContext();
    }
    
    @Test
    public void getParticipants() throws Exception {
        Result result = controller.getParticipants("10", "20");
        PagedResourceList<AccountSummary> page = resultToPage(result);
        
        // verify the result contains items
        assertEquals(3, page.getItems().size());
        assertEquals(30, page.getTotal());
        assertEquals(SUMMARY, page.getItems().get(0));
        
        //verify paging
        assertEquals(10, page.getOffsetBy());
        assertEquals(20, page.getPageSize());
        verify(participantService).getPagedAccountSummaries(STUDY, 10, 20);
    }
    
    @Test(expected = BadRequestException.class)
    public void oddParametersUseDefaults() throws Exception {
        controller.getParticipants("asdf", "qwer");
        
        // paging with defaults
        verify(participantService).getPagedAccountSummaries(STUDY, 0, BridgeConstants.API_DEFAULT_PAGE_SIZE);
    }
    
    public void nullParametersUseDefaults() throws Exception {
        controller.getParticipants(null, null);

        // paging with defaults
        verify(participantService).getPagedAccountSummaries(STUDY, 0, BridgeConstants.API_DEFAULT_PAGE_SIZE);
    }
    
    private PagedResourceList<AccountSummary> resultToPage(Result result) throws Exception {
        String string = Helpers.contentAsString(result);
        return BridgeObjectMapper.get().readValue(string, ACCOUNT_SUMMARY_PAGE);
    }
}
