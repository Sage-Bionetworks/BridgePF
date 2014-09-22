package org.sagebionetworks.bridge.json;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.surveys.Constraints;
import org.sagebionetworks.bridge.models.surveys.MultiValueConstraints;
import org.sagebionetworks.bridge.models.surveys.SurveyAnswer;
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
    
    private static final ObjectMapper mapper = new ObjectMapper();

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
    
    public static Constraints asConstraints(JsonNode parent, String property, String dataTypeProperty, String enumProperty) {
        JsonNode constraints = JsonUtils.asJsonNode(parent, property);
        if (constraints != null) {
            String type = JsonUtils.asText(constraints, dataTypeProperty);
            // If the constraints contain an enumeration, then actually, it's 
            // MultiValueConstraint, with the type specified.
            if (constraints.hasNonNull(enumProperty)) {
                return mapper.convertValue(constraints, MultiValueConstraints.class);
            } else {
                return mapper.convertValue(constraints, Constraints.CLASSES.get(type));    
            }
        }
        return null;
    }
    
    public static Schedule asSchedule(JsonNode parent, String property) {
        JsonNode schedule = JsonUtils.asJsonNode(parent, property);
        if (schedule != null) {
            return mapper.convertValue(schedule, Schedule.class);
        }
        return null;
    }
    
    @SuppressWarnings("unchecked")
    public static List<SurveyAnswer> asSurveyAnswers(JsonNode parent, String property) {
        JsonNode answers = JsonUtils.asJsonNode(parent, property);
        if (answers != null) {
            return (List<SurveyAnswer>) mapper.convertValue(answers,
                    mapper.getTypeFactory().constructCollectionType(ArrayList.class, SurveyAnswer.class));
        }
        return Lists.newLinkedList();
    }
    
    @SuppressWarnings("unchecked")
    public static List<SurveyAnswer> asSurveyAnswers(JsonNode node) {
        if (node != null && node.isArray()) {
            return (List<SurveyAnswer>) mapper.convertValue(node,
                    mapper.getTypeFactory().constructCollectionType(ArrayList.class, SurveyAnswer.class));
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
    
    public static String toJSON(Object object) {
        try {
            return new ObjectMapper().writeValueAsString(object);
        } catch(JsonProcessingException e) {
            return e.getMessage();
        }
    }

}