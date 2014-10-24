package global;

import java.io.IOException;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.Tracker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

public class JsonSchemaValidator {

    private CacheLoader<Tracker, JsonNode> loader = new CacheLoader<Tracker, JsonNode>() {
        @Override
        public JsonNode load(Tracker tracker) throws JsonProcessingException, IOException {
            return BridgeObjectMapper.get().readTree(tracker.getSchemaFile().getInputStream());
        }
    };

    private LoadingCache<Tracker, JsonNode> cache = CacheBuilder.newBuilder().build(loader);

    public JsonNode getSchemaAsNode(Tracker tracker) throws Exception {
        return cache.get(tracker);
    }

    public ProcessingReport validate(Tracker tracker, JsonNode node) throws Exception {
        JsonSchemaFactory factory = JsonSchemaFactory.byDefault();
        JsonNode schemaFile = getSchemaAsNode(tracker);
        JsonSchema schema = factory.getJsonSchema(schemaFile);

        return schema.validate(node);
    }


}
