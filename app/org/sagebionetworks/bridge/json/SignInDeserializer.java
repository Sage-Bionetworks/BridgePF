package org.sagebionetworks.bridge.json;

import java.io.IOException;

import org.apache.commons.lang3.StringUtils;

import org.sagebionetworks.bridge.models.accounts.SignIn;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

public class SignInDeserializer extends JsonDeserializer<SignIn> {

    @Override
    public SignIn deserialize(JsonParser parser, DeserializationContext context) throws IOException, JsonProcessingException {
        JsonNode node = parser.getCodec().readTree(parser);
        String username = JsonUtils.asText(node, "username");
        String email = JsonUtils.asText(node, "email");
        String password = JsonUtils.asText(node, "password");
        
        if (StringUtils.isNotBlank(username)) {
            return new SignIn(username, password);
        }
        return new SignIn(email, password);
    }

}
