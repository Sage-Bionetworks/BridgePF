package org.sagebionetworks.bridge.json;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.surveys.Constraints;
import org.sagebionetworks.bridge.models.surveys.DataType;
import org.sagebionetworks.bridge.time.DateUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Lists;


/**
 * There are actually a number of ways to indicate a null value,
 * and we need to test for all of them to insure we don't accidentally
 * get the string "null" or something similar. We also want to check
 * type casting as well. Hence these utility methods.
 */
public class JsonUtils {

    private static final String DATA_TYPE_PROPERTY = "dataType";
    private static final String ENUM_PROPERTY = "enumeration";
    private static final String MULTIVALUE_PROPERTY = "multivalue";
    public static final TypeReference<Map<String, Object>> TYPE_REF_RAW_MAP =
            new TypeReference<Map<String, Object>>(){};

    /**
     * Parses a JSON String value in ISO8601 format as a Joda DateTime. Returns null if the value is not a valid
     * ISO8601 date-time.
     */
    public static DateTime asDateTime(JsonNode parent, String property) {
        String dateTimeStr = asText(parent, property);
        if (StringUtils.isBlank(dateTimeStr)) {
            return null;
        }
        try {
            return DateTime.parse(dateTimeStr);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public static String asText(JsonNode parent, String property) {
        if (parent != null && parent.hasNonNull(property)) {
            return parent.get(property).asText();
        }
        return null;
    }

    public static Long asLong(JsonNode parent, String property) {
        if (parent != null && parent.hasNonNull(property)) {
            return parent.get(property).asLong();
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
            return parent.get(property).asInt();
        }
        return null;
    }

    public static int asIntPrimitive(JsonNode parent, String property) {
        if (parent != null && parent.hasNonNull(property)) {
            return parent.get(property).asInt();
        }
        return 0;
    }

    public static long asMillisDuration(JsonNode parent, String property) {
        if (parent != null && parent.hasNonNull(property)) {
            return DateUtils.convertToMillisFromDuration(parent.get(property).asText());
        }
        return 0L;
    }
    
    public static long asMillisSinceEpoch(JsonNode parent, String property) {
        if (parent != null && parent.hasNonNull(property)) {
            return DateUtils.convertToMillisFromEpoch(parent.get(property).asText());
        }
        return 0L;
    }

    public static JsonNode asJsonNode(JsonNode parent, String property) {
        if (parent != null && parent.hasNonNull(property)) {
            return parent.get(property);
        }
        return null;
    }

    public static Constraints asConstraints(JsonNode parent, String property) {
        ObjectNode node = (ObjectNode)JsonUtils.asJsonNode(parent, property);
        if (node != null) {
            String dataType = node.get(DATA_TYPE_PROPERTY).asText();
            // If the constraints contain an enumeration, then it's a MultiValueConstraint,
            // deserialize it as such and then replace the type
            if (node.hasNonNull(ENUM_PROPERTY)) {
                node.put(DATA_TYPE_PROPERTY, MULTIVALUE_PROPERTY);
            }
            Constraints constraint = BridgeObjectMapper.get().convertValue(node, Constraints.class);
            constraint.setDataType(DataType.valueOf(dataType.toUpperCase()));
            return constraint;
        }
        return null;
    }

    public static <T> T asEntity(JsonNode parent, String property, Class<T> clazz) {
        JsonNode child = JsonUtils.asJsonNode(parent, property);
        if (child != null) {
            try {
                return BridgeObjectMapper.get().treeToValue(child, clazz);    
            } catch(JsonProcessingException e) {
                throw new BridgeServiceException(e);
            }
        }
        return null;
    }
    
    public static <T> List<T> asEntityList(JsonNode parent, String property, Class<T> clazz) {
        JsonNode list = JsonUtils.asJsonNode(parent, property);
        return JsonUtils.asEntityList(list, clazz);
    }

    @SuppressWarnings("unchecked")
    public static <T> List<T> asEntityList(JsonNode list, Class<T> clazz) {
        BridgeObjectMapper mapper = BridgeObjectMapper.get();
        if (list != null && list.isArray()) {
            return (List<T>) mapper.convertValue(list,
                    mapper.getTypeFactory().constructCollectionType(ArrayList.class, clazz));
        }
        return Lists.newLinkedList();
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

    public static Set<String> asStringSet(JsonNode parent, String property) {
        Set<String> results = new HashSet<>();
        if (parent != null && parent.hasNonNull(property)) {
            ArrayNode array = (ArrayNode)parent.get(property);
            for (int i = 0; i < array.size(); i++) {
                results.add(array.get(i).asText());
            }
        }
        return results;
    }
    
    public static Set<Roles> asRolesSet(JsonNode parent, String property) {
        Set<Roles> results = new HashSet<>();
        if (parent != null && parent.hasNonNull(property)) {
            ArrayNode array = (ArrayNode)parent.get(property);
            for (int i = 0; i < array.size(); i++) {
                String name = array.get(i).asText().toUpperCase();
                results.add(Roles.valueOf(name));
            }
        }
        return results;
    }
}