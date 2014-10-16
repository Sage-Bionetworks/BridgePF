package org.sagebionetworks.bridge.json;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.models.schedules.ActivityType;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.ScheduleType;
import org.sagebionetworks.bridge.models.surveys.Constraints;
import org.sagebionetworks.bridge.models.surveys.DataType;
import org.sagebionetworks.bridge.models.surveys.UIHint;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    
    private static final String DATA_TYPE_PROPERTY = "dataType";
    private static final String ENUM_PROPERTY = "enumeration";
    private static final String MULTIVALUE_PROPERTY = "multivalue";
    
    public static String asText(JsonNode parent, String property) {
        if (parent != null && parent.hasNonNull(property)) {
            return parent.get(property).asText();
        }
        return null;
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
    
    public static Schedule asSchedule(JsonNode parent, String property) {
        JsonNode schedule = JsonUtils.asJsonNode(parent, property);
        if (schedule != null) {
            return BridgeObjectMapper.get().convertValue(schedule, Schedule.class);
        }
        return null;
    }
    
    public static <T> List<T> asEntityList(JsonNode parent, String property, Class<T> clazz) {
        JsonNode list = JsonUtils.asJsonNode(parent, property);
        return JsonUtils.asEntityList(list, clazz);
    }
    
    @SuppressWarnings("unchecked")
    public static <T> List<T> asEntityList(JsonNode list, Class<T> clazz) {
        ObjectMapper mapper = BridgeObjectMapper.get();
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
    
    public static UIHint asUIHint(JsonNode parent, String property) {
        if (parent != null && parent.hasNonNull(property)) {
            String value = JsonUtils.asText(parent, property);
            return UIHint.valueOf(value.toUpperCase());
        }
        return null;
    }
    
    public static ActivityType asActivityType(JsonNode parent, String property) {
        if (parent != null && parent.hasNonNull(property)) {
            String value = JsonUtils.asText(parent, property);
            return ActivityType.valueOf(value.toUpperCase());
        }
        return null;
    }
    
    public static ScheduleType asScheduleType(JsonNode parent, String property) {
        if (parent != null && parent.hasNonNull(property)) {
            String value = JsonUtils.asText(parent, property);
            return ScheduleType.valueOf(value.toUpperCase());
        }
        return null;
    }
    
    public static List<String> asStringList(JsonNode parent, String property) {
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
    
    public static ArrayNode asArrayNode(List<UIHint> list) {
        ArrayNode array = JsonNodeFactory.instance.arrayNode();
        for (UIHint element : list) {
            array.add(element.name().toLowerCase());
        }
        return array;
    }
    
    public static void write(ObjectNode node, String propertyName, Enum<?> e) {
        if (e != null) {
            node.put(propertyName, e.name().toLowerCase());
        }
    }
    
    public static void write(ObjectNode node, String propertyName, String string) {
        if (StringUtils.isNotBlank(string)) {
            node.put(propertyName, string);
        }
    }
    
    public static void write(ObjectNode node, String propertyName, Long l) {
        if (l != null) {
            node.put(propertyName, l);
        }
    }
    
    public static String toJSON(Object object) {
        try {
            return new BridgeObjectMapper().writeValueAsString(object);
        } catch(JsonProcessingException e) {
            return e.getMessage();
        }
    }

}