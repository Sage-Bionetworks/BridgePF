package org.sagebionetworks.bridge.exceptions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.http.HttpStatus;
import org.junit.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.BridgeEntity;

public class ConcurrentModificationExceptionTest {
    private static final TestEntity DUMMY_ENTITY = new TestEntity();
    private static final String ERROR_MESSAGE = "Something bad happened.";

    @Test
    public void serialization() {
        ConcurrentModificationException ex = new ConcurrentModificationException(DUMMY_ENTITY, ERROR_MESSAGE);
        JsonNode exNode = BridgeObjectMapper.get().valueToTree(ex);
        assertTrue(exNode.has("entity"));
        assertTrue(exNode.has("entityClass"));
        assertEquals(ERROR_MESSAGE, exNode.get("message").textValue());
        assertEquals(HttpStatus.SC_CONFLICT, exNode.get("statusCode").intValue());
    }

    @Test
    public void serializationNullEntity() {
        ConcurrentModificationException ex = new ConcurrentModificationException(ERROR_MESSAGE);
        JsonNode exNode = BridgeObjectMapper.get().valueToTree(ex);
        assertFalse(exNode.has("entity"));
        assertFalse(exNode.has("entityClass"));
        assertEquals(ERROR_MESSAGE, exNode.get("message").textValue());
        assertEquals(HttpStatus.SC_CONFLICT, exNode.get("statusCode").intValue());
    }

    // Trivial class with a single getter. We need a concrete implementation of BridgeEntity, and Jackson requires that
    // it be non-empty.
    private static class TestEntity implements BridgeEntity {
        @SuppressWarnings("unused")
        public String getFoo() {
            return "foo";
        }
    }
}
