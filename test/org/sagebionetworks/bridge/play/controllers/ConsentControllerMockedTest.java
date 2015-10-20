package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.Iterator;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.ConsentSignature;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.play.controllers.ConsentController;
import org.sagebionetworks.bridge.services.ConsentService;
import org.sagebionetworks.bridge.services.ParticipantOptionsService;
import org.sagebionetworks.bridge.services.StudyService;

import com.fasterxml.jackson.databind.JsonNode;

import play.mvc.Result;
import play.test.Helpers;

public class ConsentControllerMockedTest {

    private static final long UNIX_TIMESTAMP = DateUtils.getCurrentMillisFromEpoch(); 
    
    private UserSession session;
    private User user;
    private Study study;
    private ConsentController controller;
    
    private StudyService studyService;
    private ConsentService consentService;
    ParticipantOptionsService optionsService;
    
    @Before
    public void before() {
        session = mock(UserSession.class);
        StudyIdentifier studyId = mock(StudyIdentifier.class);
        when(session.getStudyIdentifier()).thenReturn(studyId);
        user = mock(User.class);
        when(user.getHealthCode()).thenReturn("healthCode");
        when(session.getUser()).thenReturn(user);
        
        controller = spy(new ConsentController());
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();
        
        studyService = mock(StudyService.class);
        study = mock(Study.class);
        when(studyService.getStudy(studyId)).thenReturn(study);
        controller.setStudyService(studyService);
        
        consentService = mock(ConsentService.class);
        controller.setConsentService(consentService);

        optionsService = mock(ParticipantOptionsService.class);
        controller.setOptionsService(optionsService);

        controller.setCacheProvider(mock(CacheProvider.class));
    }
    
    @Test
    public void testChangeSharingScope() {
        controller.changeSharingScope(SharingScope.NO_SHARING, "message");
        
        InOrder inOrder = inOrder(optionsService, consentService);
        inOrder.verify(optionsService).setOption(study, "healthCode", SharingScope.NO_SHARING);
        inOrder.verify(consentService).emailConsentAgreement(study, user);
    }
    
    @Test
    public void consentSignatureCorrect() throws Exception {
        ConsentSignature sig = ConsentSignature.create("Jack Aubrey", "1970-10-10", "data:asdf", "image/png", UNIX_TIMESTAMP);
        when(consentService.getConsentSignature(study, user)).thenReturn(sig);
        
        Result result = controller.getConsentSignature();
        
        String json = Helpers.contentAsString(result);
        JsonNode node = BridgeObjectMapper.get().readTree(json);
        
        assertEquals(5, fieldNameCount(node));
        assertEquals("Jack Aubrey", node.get("name").asText());
        assertEquals("1970-10-10", node.get("birthdate").asText());
        assertEquals("ConsentSignature", node.get("type").asText());
        assertEquals("data:asdf", node.get("imageData").asText());
        assertEquals("image/png", node.get("imageMimeType").asText());
    }
    
    private int fieldNameCount(JsonNode node) {
        int count = 0;
        for (Iterator<String> i = node.fieldNames(); i.hasNext();) {
            i.next();
            count++;
        }
        return count;
    }
}
