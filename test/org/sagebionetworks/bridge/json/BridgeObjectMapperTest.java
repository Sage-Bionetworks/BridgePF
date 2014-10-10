package org.sagebionetworks.bridge.json;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

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
        
    
}
