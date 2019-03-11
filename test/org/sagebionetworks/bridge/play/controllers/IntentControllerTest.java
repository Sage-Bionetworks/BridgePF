package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.itp.IntentToParticipate;
import org.sagebionetworks.bridge.services.IntentService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

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
        // See comment in controller. Client APIs send scope as part of signature for legacy
        // reasons, but it is not part of the consent signature. Controller transfers it to the ITP.
        IntentToParticipate intent = TestUtils.getIntentToParticipate(TIMESTAMP).build();
        JsonNode node = BridgeObjectMapper.get().valueToTree(intent);
        ((ObjectNode)node).remove("scope");
        ((ObjectNode)node.get("consentSignature")).put("scope", "all_qualified_researchers");
        
        TestUtils.mockPlay().withJsonBody(node.toString()).mock();
        
        Result result = controller.submitIntentToParticipate();
        TestUtils.assertResult(result, 202, "Intent to participate accepted.");
        
        verify(mockIntentService).submitIntentToParticipate(intentCaptor.capture());
        
        IntentToParticipate captured = intentCaptor.getValue();
        // It's pretty simple, we just want to make sure we got it, check a couple of fields
        assertEquals(TestConstants.PHONE.getNumber(), captured.getPhone().getNumber());
        assertEquals("Gladlight Stonewell", captured.getConsentSignature().getName());
        assertEquals(SharingScope.ALL_QUALIFIED_RESEARCHERS, captured.getScope());
    }

}
