package global;

import java.io.IOException;

import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.Tracker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonschema.core.report.ProcessingMessage;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

public class JsonSchemaValidator {
    
    private CacheLoader<Tracker, JsonNode> loader = new CacheLoader<Tracker, JsonNode>() {
        public JsonNode load(Tracker tracker) throws JsonProcessingException, IOException {
            return BridgeObjectMapper.get().readTree(tracker.getSchemaFile().getInputStream());
        }
    };

    private LoadingCache<Tracker, JsonNode> cache = CacheBuilder.newBuilder().build(loader);

    public JsonNode getSchemaAsNode(Tracker tracker) throws Exception {
        return cache.get(tracker);
    }
    
    public void validate(Tracker tracker, JsonNode node) throws Exception {
        // Why does validation occur in the controller layer? Because the validation is occurring 
        // in the transfer format, not the service API format, and the error messages will make more 
        // sense to the client if the validation occurs before conversion. However that means there's 
        // no validation at the service level, we'd have to convert *back* to JSON to use the same 
        // schema for that purpose, and do it all twice.
        JsonSchemaFactory factory = JsonSchemaFactory.byDefault();
        JsonNode schemaFile = getSchemaAsNode(tracker);
        JsonSchema schema = factory.getJsonSchema(schemaFile);

        ProcessingReport report = schema.validate(node);
        if (!report.isSuccess()) {
            StringBuilder sb = new StringBuilder();
            for (ProcessingMessage message : report) {
                sb.append(message.getMessage());
                sb.append(". ");
            }
            throw new BadRequestException(sb.toString());
        }
    }


}
