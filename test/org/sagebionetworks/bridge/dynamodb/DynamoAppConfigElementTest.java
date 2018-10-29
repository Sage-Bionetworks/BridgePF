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

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

public class DynamoAppConfigElementTest {
    
    private static final String KEY = "api:confusing: key: divider";
    private static final String ID = "confusing: key: divider";
    private static final DateTime CREATED_ON = DateTime.now(DateTimeZone.UTC);
    private static final DateTime MODIFIED_ON = CREATED_ON.plusHours(2);
    
    @Test
    public void keyParsing() {
        // set through setters, get key
        DynamoAppConfigElement element = new DynamoAppConfigElement();
        element.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        element.setId(ID);
        assertEquals(KEY, element.getKey());
        
        // set key, get through getters
        element = new DynamoAppConfigElement();
        element.setKey(KEY);
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, element.getStudyId());
        assertEquals(ID, element.getId());
        
        // set key, get through getters
        element = new DynamoAppConfigElement();
        element.setKey(KEY);
        assertEquals(KEY, element.getKey());
        
        // setters nullify key
        element = new DynamoAppConfigElement();
        element.setKey(KEY);
        element.setStudyId(null);
        assertNull(element.getKey());
        
        element = new DynamoAppConfigElement();
        element.setKey(KEY);
        element.setId(null);
        assertNull(element.getId());
        
        element = new DynamoAppConfigElement();
        element.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        element.setId(ID);
        element.setKey(null);
        assertNull(element.getStudyId());
        assertNull(element.getId());
    }
    
    @Test
    public void hashCodeEquals() {
        EqualsVerifier.forClass(DynamoAppConfigElement.class)
                .withPrefabValues(JsonNode.class, TestUtils.getClientData(), TestUtils.getOtherClientData())
                .suppress(Warning.NONFINAL_FIELDS).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void canSerialize() throws Exception {
        DynamoAppConfigElement element = new DynamoAppConfigElement();
        element.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        element.setId(ID);
        element.setRevision(1L);
        element.setDeleted(true);
        element.setData(TestUtils.getClientData());
        element.setCreatedOn(CREATED_ON.getMillis());
        element.setModifiedOn(MODIFIED_ON.getMillis());
        element.setVersion(2L);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(element);
        assertNull(node.get("key"));
        assertNull(node.get("studyId"));
        assertEquals(1L, node.get("revision").longValue());
        assertEquals(ID, node.get("id").textValue());
        assertTrue(node.get("deleted").booleanValue());
        assertEquals(TestUtils.getClientData(), node.get("data"));
        assertEquals(CREATED_ON.toString(), node.get("createdOn").textValue());
        assertEquals(MODIFIED_ON.toString(), node.get("modifiedOn").textValue());
        assertEquals(2L, node.get("version").longValue());
        assertEquals("AppConfigElement", node.get("type").textValue());
        
        AppConfigElement deser = BridgeObjectMapper.get().readValue(node.toString(), AppConfigElement.class);
        deser.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        assertEquals(element, deser);
    }
    
}
