package org.sagebionetworks.bridge.hibernate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.substudies.Substudy;

import com.fasterxml.jackson.databind.JsonNode;

public class HibernateSubstudyTest {

    private static final DateTime CREATED_ON = DateTime.now().withZone(DateTimeZone.UTC);
    private static final DateTime MODIFIED_ON = DateTime.now().minusHours(1).withZone(DateTimeZone.UTC);
    
    @Test
    public void canSerialize() throws Exception {
        Substudy substudy = Substudy.create();
        substudy.setId("oneId");
        substudy.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        substudy.setName("name");
        substudy.setDeleted(true);
        substudy.setCreatedOn(CREATED_ON);
        substudy.setModifiedOn(MODIFIED_ON);
        substudy.setVersion(3L);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(substudy);
        assertEquals(7, node.size());
        assertEquals("oneId", node.get("id").textValue());
        assertEquals("name", node.get("name").textValue());
        assertTrue(node.get("deleted").booleanValue());
        assertEquals(CREATED_ON.toString(), node.get("createdOn").textValue());
        assertEquals(MODIFIED_ON.toString(), node.get("modifiedOn").textValue());
        assertEquals(3L, node.get("version").longValue());
        assertEquals("Substudy", node.get("type").textValue());
        assertNull(node.get("studyId"));
        
        Substudy deser = BridgeObjectMapper.get().readValue(node.toString(), Substudy.class);
        assertEquals("oneId", deser.getId());
        assertEquals("name", deser.getName());
        assertTrue(deser.isDeleted());
        assertEquals(CREATED_ON, deser.getCreatedOn());
        assertEquals(MODIFIED_ON, deser.getModifiedOn());
        assertEquals(new Long(3), deser.getVersion());
    }
}
