package org.sagebionetworks.bridge.json;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.sagebionetworks.bridge.models.surveys.Survey;

import com.fasterxml.jackson.databind.JsonNode;

public class BridgeObjectMapperTest {

    @Test
    public void addsTypeField() {
        final class NotAnnotated {
            @SuppressWarnings("unused") public String field;
        }
        
        @BridgeTypeName("AnnotationName")
        final class Annotated {
            @SuppressWarnings("unused") public String field;
        }
        
        BridgeObjectMapper mapper = new BridgeObjectMapper();
        
        JsonNode node = mapper.valueToTree(new NotAnnotated());
        assertEquals("Type is NotAnnotated", "NotAnnotated", node.get("type").asText());
        
        node = mapper.valueToTree(new Annotated());
        assertEquals("Type is AnnotationName", "AnnotationName", node.get("type").asText());
    }
    
    @Test
    public void doesNotOverrideExistingTypeField() {
        @BridgeTypeName("WrongName")
        final class NotAnnotated {
            private final String type;
            public NotAnnotated(String type) {
                this.type = type;
            }
            @SuppressWarnings("unused") public String getType() {
                return type;
            }
        }
        
        BridgeObjectMapper mapper = new BridgeObjectMapper();
        
        JsonNode node = mapper.valueToTree(new NotAnnotated("ThisIsTheName"));
        assertEquals("Type is ThisIsTheName", "ThisIsTheName", node.get("type").asText());
    }
        
    /**
     * It should be possible to send a null for any field, including fields that are deserialized into 
     * primitive longs, but this sets off a series of misadventures in Jackson deserialization that lead 
     * to deserialization exceptions. Instead, use the default field value of 0L and then validate whether 
     * or not this value is valid (it never is where we use a primitive long, but in all cases we set the 
     * value internally, ignoring what is sent from the client, so explicit validation is never needed).
     * @throws Exception
     */
    @Test
    public void canDeserializePrimitiveLongsExpressedAsNulls() throws Exception {
        String json = "{\"createdOn\":null,\"modifiedOn\":null}";
        
        Survey survey = new BridgeObjectMapper().readValue(json, Survey.class);
        assertEquals(0, survey.getCreatedOn());
        assertEquals(0, survey.getModifiedOn());
    }
}
