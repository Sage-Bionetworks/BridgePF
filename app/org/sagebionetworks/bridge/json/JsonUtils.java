package org.sagebionetworks.bridge.json;

import java.util.Collections;
import java.util.List;

import org.sagebionetworks.bridge.models.DateConverter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Lists;

/**
 * There are actually a number of ways to indicate a null value, 
 * and we need to test for all of them to insure we don't accidentally
 * get the string "null" or something similar. We also want to check 
 * type casting as well. Hence these utility methods. 
 */
public class JsonUtils {

    public static String asText(JsonNode parent, String property) {
        if (parent != null && parent.hasNonNull(property)) {
            return parent.get(property).asText();
        }
        return null;
    }

    /**
     * This method defaults to false if the value is not present or set to 
     * null.
     * @param parent
     * @param property
     * @return
     */
    public static boolean asBoolean(JsonNode parent, String property) {
        if (parent != null && parent.hasNonNull(property)) {
            return parent.get(property).asBoolean();
        }
        return false;
    }
    
    public static Long asLong(JsonNode parent, String property) {
        if (parent != null && parent.hasNonNull(property)) {
            return new Long(parent.get(property).asLong());    
        }
        return null;
    }
    
    public static long asLongPrimitive(JsonNode parent, String property) {
        if (parent != null && parent.hasNonNull(property)) {
            return parent.get(property).asLong();    
        }
        return 0L;
    }
    
    public static Integer asInt(JsonNode parent, String property) {
        if (parent != null && parent.hasNonNull(property)) {
            return new Integer(parent.get(property).asInt());
        }
        return null;
    }
    
    public static int asIntPrimitive(JsonNode parent, String property) {
        if (parent != null && parent.hasNonNull(property)) {
            return parent.get(property).asInt();
        }
        return 0;
    }
    
    public static long asMillisSinceEpoch(JsonNode parent, String property) {
        if (parent != null && parent.hasNonNull(property)) {
            return DateConverter.convertMillisFromEpoch(parent.get(property).asText());
        }
        return 0L;
    }
    
    public static JsonNode asJsonNode(JsonNode parent, String property) {
        if (parent != null && parent.hasNonNull(property)) {
            return parent.get(property);
        }
        return null;
    }
    
    public static ObjectNode asObjectNode(JsonNode parent, String property) {
        if (parent != null && parent.hasNonNull(property) && parent.get(property).isObject()) {
            return (ObjectNode)parent.get(property);
        }
        return null;
    }
    
    public static ArrayNode asArrayNode(JsonNode parent, String property) {
        if (parent != null && parent.hasNonNull(property) && parent.get(property).isArray()) {
            return (ArrayNode)parent.get(property);
        }
        return null;
    }
    
    public static boolean asBoolean(JsonNode parent, String property) {
        if (parent != null && parent.hasNonNull(property)) {
            return parent.get(property).asBoolean();
        }
        return false;
    }
    
    public static List<String> toStringList(JsonNode parent, String property) {
        if (parent != null && parent.hasNonNull(property)) {
            ArrayNode array = JsonUtils.asArrayNode(parent, property);
            List<String> results = Lists.newArrayListWithCapacity(array.size());
            for (int i=0; i < array.size(); i++) {
                results.add(array.get(i).asText());
            }
            return results;
        }
        return Collections.emptyList();
    }
    
    public static ArrayNode toArrayNode(List<String> list) {
        ArrayNode array = JsonNodeFactory.instance.arrayNode();
        for (String element : list) {
            array.add(element);
        }
        return array;
    }

}