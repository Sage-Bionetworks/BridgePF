package org.sagebionetworks.bridge.upload;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import javax.annotation.Nonnull;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

/**
 * Validation handler for parsing the upload data into JSON, if applicable. This handler reads unzipped data from
 * {@link org.sagebionetworks.bridge.upload.UploadValidationContext#getUnzippedDataMap}. As it iterates over the unzipped
 * data, if it's able to parse any entries into JSON, it will remove it from getUnzippedDataMap, and write it to
 * {@link org.sagebionetworks.bridge.upload.UploadValidationContext#getJsonDataMap}.
 */
@Component
public class ParseJsonHandler implements UploadValidationHandler {
    private static final Logger logger = LoggerFactory.getLogger(ParseJsonHandler.class);

    /** {@inheritDoc} */
    @Override
    public void handle(@Nonnull UploadValidationContext context) {
        Map<String, byte[]> unzippedDataMap = context.getUnzippedDataMap();
        Map<String, JsonNode> jsonDataMap = new HashMap<>();

        // We use the iterator directly, because we may want to modify unzippedDataMap as we iterate it, and this is
        // the most straightforward way to do that without hitting a ConcurrentModificationException.
        Iterator<Map.Entry<String, byte[]>> unzippedDataIter = unzippedDataMap.entrySet().iterator();
        while (unzippedDataIter.hasNext()) {
            Map.Entry<String, byte[]> oneUnzippedData = unzippedDataIter.next();
            String entryName = oneUnzippedData.getKey();

            try {
                // Try to parse it as JSON. If you can, remove the entry from unzippedDataMap and add it to
                // jsonDataMap.
                JsonNode jsonNode = BridgeObjectMapper.get().readTree(oneUnzippedData.getValue());
                jsonDataMap.put(entryName, jsonNode);
                unzippedDataIter.remove();

                logger.debug(String.format("Found JSON file %s", entryName));
            } catch (Exception ex) {
                // Can't parse this as a JSON node. This could be normal (for example, for audio files). Ignore the
                // error and move on.
                logger.debug(String.format("Found non-JSON file %s", entryName));
            }
        }

        context.setJsonDataMap(jsonDataMap);
    }
}
