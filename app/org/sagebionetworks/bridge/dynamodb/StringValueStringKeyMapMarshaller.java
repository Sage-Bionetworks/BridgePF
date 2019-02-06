package org.sagebionetworks.bridge.dynamodb;

import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;

public class StringValueStringKeyMapMarshaller extends StringKeyMapMarshaller<String> {
    private static final TypeReference<Map<String, String>> REF = new TypeReference<Map<String, String>>() {};

    @Override
    public TypeReference<Map<String, String>> getTypeReference() {
        return REF;
    }
}
