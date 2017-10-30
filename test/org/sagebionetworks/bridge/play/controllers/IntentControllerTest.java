package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.models.itp.IntentToParticipate;
import org.sagebionetworks.bridge.services.IntentService;

import play.mvc.Result;

@RunWith(MockitoJUnitRunner.class)
public class IntentControllerTest {

    private static final long TIMESTAMP = 1000L; 
    
    @Mock
    IntentService mockIntentService;
    
    @Captor
    ArgumentCaptor<IntentToParticipate> intentCaptor;

    private IntentController controller;

    @Before
    public void before() {
        controller = new IntentController();
        controller.setIntentService(mockIntentService);
    }
    
    @Test
    public void canSubmitAnIntent() throws Exception {
        IntentToParticipate itp = TestUtils.getIntentToParticipate(TIMESTAMP);
        TestUtils.mockPlayContextWithJson(itp);
        
        Result result = controller.submitIntentToParticipate();
        TestUtils.assertResult(result, 202, "Intent to participate accepted.");
        
        verify(mockIntentService).submitIntentToParticipate(intentCaptor.capture());
        
        IntentToParticipate captured = intentCaptor.getValue();
        // It's pretty simple, we just want to make sure we got it, check a couple of fields
        assertEquals("email@email.com", captured.getEmail());
        assertEquals("Gladlight Stonewell", captured.getConsentSignature().getName());
    }

}
