package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;

import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class ListMarshallerTest {
    private static final TestListMarshaller MARSHALLER = new TestListMarshaller();

    private static class TestObject {
        private String value;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    private static class TestListMarshaller extends ListMarshaller<TestObject> {
        private static final TypeReference<List<TestObject>> TYPE_REF = new TypeReference<List<TestObject>>(){};

        @Override
        public TypeReference<List<TestObject>> getTypeReference() {
            return TYPE_REF;
        }
    }

    @Test
    public void testConvert() throws Exception {
        // start with JSON
        String jsonText = "[\n" +
                "   {\"value\":\"foo\"},\n" +
                "   {\"value\":\"bar\"}\n" +
                "]";

        // unconvert from JSON to list
        List<TestObject> list = MARSHALLER.unconvert(jsonText);
        assertEquals(2, list.size());
        assertEquals("foo", list.get(0).getValue());
        assertEquals("bar", list.get(1).getValue());

        // convert from list to JSON
        String convertedJsonText = MARSHALLER.convert(list);

        // use Jackson to convert to a JsonNode for validation
        JsonNode convertedJsonNode = BridgeObjectMapper.get().readTree(convertedJsonText);
        assertEquals(2, convertedJsonNode.size());
        assertEquals("foo", convertedJsonNode.get(0).get("value").textValue());
        assertEquals("bar", convertedJsonNode.get(1).get("value").textValue());
    }
}
