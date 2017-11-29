package org.sagebionetworks.bridge.dynamodb;

import java.util.Map;

import org.sagebionetworks.bridge.models.studies.OAuthProvider;

import com.fasterxml.jackson.core.type.TypeReference;

public class OAuthProviderMapMarshaller extends StringKeyMapMarshaller<OAuthProvider> {
    private static final TypeReference<Map<String, OAuthProvider>> REF = new TypeReference<Map<String, OAuthProvider>>() {
    };

    @Override
    public TypeReference<Map<String, OAuthProvider>> getTypeReference() {
        return REF;
    }
}
