package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.appconfig.AppConfigElement;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

public class DynamoAppConfigElementTest {
    
    private static final DateTime CREATED_ON = DateTime.now(DateTimeZone.UTC);
    private static final DateTime MODIFIED_ON = CREATED_ON.plusHours(2);
    
    @Test
    public void hashCodeEquals() {
        JsonNode clientData2 = TestUtils.getClientData();
        ((ObjectNode)clientData2).put("test", "tones");
        EqualsVerifier.forClass(DynamoAppConfigElement.class)
                .withPrefabValues(JsonNode.class, TestUtils.getClientData(), clientData2)
                .suppress(Warning.NONFINAL_FIELDS).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void canSerialize() throws Exception {
        DynamoAppConfigElement element = new DynamoAppConfigElement();
        element.setKey(TestConstants.TEST_STUDY, "id");
        element.setStudyId("studyId");
        element.setRevision(1L);
        element.setId("id");
        element.setDeleted(true);
        element.setData(TestUtils.getClientData());
        element.setCreatedOn(CREATED_ON.getMillis());
        element.setModifiedOn(MODIFIED_ON.getMillis());
        element.setVersion(2L);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(element);
        assertNull(node.get("key"));
        assertNull(node.get("studyId"));
        assertEquals(1L, node.get("revision").longValue());
        assertEquals("id", node.get("id").textValue());
        assertTrue(node.get("deleted").booleanValue());
        assertEquals(TestUtils.getClientData(), node.get("data"));
        assertEquals(CREATED_ON.toString(), node.get("createdOn").textValue());
        assertEquals(MODIFIED_ON.toString(), node.get("modifiedOn").textValue());
        assertEquals(2L, node.get("version").longValue());
        assertEquals("AppConfigElement", node.get("type").textValue());
        
        AppConfigElement deser = BridgeObjectMapper.get().readValue(node.toString(), AppConfigElement.class);
        element.setKey(null);
        element.setStudyId(null);
        
        assertEquals(element, deser);
    }
    
}
