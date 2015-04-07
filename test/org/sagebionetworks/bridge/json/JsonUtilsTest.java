package org.sagebionetworks.bridge.json;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import java.util.List;
import java.util.Set;

import org.joda.time.DateTime;
import org.joda.time.Period;
import org.junit.Test;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.ActivityType;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.ScheduleType;
import org.sagebionetworks.bridge.models.surveys.Image;
import org.sagebionetworks.bridge.models.surveys.IntegerConstraints;
import org.sagebionetworks.bridge.models.surveys.UIHint;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

/**
 * By and large, these are null-safe accessors of values in the Jackson JSON object model.
 *
 */
public class JsonUtilsTest {
    
    public ObjectMapper mapper = BridgeObjectMapper.get();
    
    private String esc(String string) {
        return string.replaceAll("'", "\"");
    }
    
    @Test
    public void asText() throws Exception {
        JsonNode node = mapper.readTree(esc("{'key':'value'}"));
        
        assertNull(JsonUtils.asText(node, null));
        assertNull(JsonUtils.asText(node, "badProp"));
        assertEquals("value", JsonUtils.asText(node, "key"));
    }

    @Test
    public void asLong() throws Exception {
        JsonNode node = mapper.readTree(esc("{'key':3}"));
        
        assertNull(JsonUtils.asLong(node, null));
        assertNull(JsonUtils.asLong(node, "badProp"));
        assertEquals(new Long(3), JsonUtils.asLong(node, "key"));
    }

    @Test
    public void asLongPrimitive() throws Exception {
        JsonNode node = mapper.readTree(esc("{'key':3}"));
        
        assertEquals(0L, JsonUtils.asLongPrimitive(node, null));
        assertEquals(0L, JsonUtils.asLongPrimitive(node, "badProp"));
        assertEquals(3L, JsonUtils.asLongPrimitive(node, "key"));
    }

    @Test
    public void asInt() throws Exception {
        JsonNode node = mapper.readTree(esc("{'key':3}"));
        
        assertNull(JsonUtils.asInt(node, null));
        assertNull(JsonUtils.asInt(node, "badProp"));
        assertEquals(new Integer(3), JsonUtils.asInt(node, "key"));
    }

    @Test
    public void asIntPrimitive() throws Exception {
        JsonNode node = mapper.readTree(esc("{'key':3}"));
        
        assertEquals(0, JsonUtils.asIntPrimitive(node, null));
        assertEquals(0, JsonUtils.asIntPrimitive(node, "badProp"));
        assertEquals(3, JsonUtils.asIntPrimitive(node, "key"));
    }

    @Test
    public void asMillisDuration() throws Exception {
        JsonNode node = mapper.readTree(esc("{'key':'PT1H'}"));
        
        assertEquals(0L, JsonUtils.asMillisDuration(node, null));
        assertEquals(0L, JsonUtils.asMillisDuration(node, "badProp"));
        assertEquals(1*60*60*1000, JsonUtils.asMillisDuration(node, "key"));
    }

    @Test
    public void asPeriod() throws Exception {
        JsonNode node = mapper.readTree(esc("{'key':'PT1H'}"));
        
        assertNull(JsonUtils.asPeriod(node, null));
        assertNull(JsonUtils.asPeriod(node, "badProp"));
        assertEquals(Period.parse("PT1H"), JsonUtils.asPeriod(node, "key"));
    }
    
    @Test
    public void asMillisSinceEpoch() throws Exception {
        DateTime time = DateTime.parse("2015-03-23T10:00:00.000-07:00");
        
        JsonNode node = mapper.readTree(esc("{'key':'2015-03-23T10:00:00.000-07:00'}"));
        
        assertEquals(0L, JsonUtils.asMillisSinceEpoch(node, null));
        assertEquals(0L, JsonUtils.asMillisSinceEpoch(node, "badProp"));
        assertEquals(time.getMillis(), JsonUtils.asMillisSinceEpoch(node, "key"));
    }
    
    @Test
    public void asDateTime() throws Exception {
        DateTime time = DateTime.parse("2015-03-23T10:00:00.000-07:00");
        
        JsonNode node = mapper.readTree(esc("{'key':'2015-03-23T10:00:00.000-07:00'}"));
        
        assertNull(JsonUtils.asDateTime(node, null));
        assertNull(JsonUtils.asDateTime(node, "badProp"));
        assertNull(JsonUtils.asDateTime(node, "{}"));
        assertNull(JsonUtils.asDateTime(node, esc("{'key':'value'}")));
        assertEquals(time, JsonUtils.asDateTime(node, "key"));
    }

    @Test
    public void asJsonNode() throws Exception {
        JsonNode node = mapper.readTree(esc("{'key':{'subKey':'value'}}"));
        
        JsonNode node2 = mapper.readTree(esc("{'subKey':'value'}"));
        
        assertNull(JsonUtils.asJsonNode(node, null));
        assertNull(JsonUtils.asJsonNode(node, "badProp"));
        assertEquals(node2, JsonUtils.asJsonNode(node, "key"));
    }

    @Test
    public void asConstraints() throws Exception {
        IntegerConstraints c = new IntegerConstraints();
        c.setMinValue(1d);
        c.setMaxValue(5d);
        
        JsonNode node = mapper.readTree(esc("{'key':"+mapper.writeValueAsString(c)+"}"));
        assertNull(JsonUtils.asConstraints(node, null));
        assertNull(JsonUtils.asConstraints(node, "badProp"));
        assertEquals(c, JsonUtils.asConstraints(node, "key"));
    }

    @Test
    public void asSchedule() throws Exception {
        Schedule schedule = new Schedule();
        schedule.addActivity(new Activity("label", "task:job"));
        schedule.setScheduleType(ScheduleType.ONCE);
        schedule.setDelay("PT4H");
        
        JsonNode node = mapper.readTree(esc("{'key':"+mapper.writeValueAsString(schedule)+"}"));
        assertNull(JsonUtils.asSchedule(node, null));
        assertNull(JsonUtils.asSchedule(node, "badProp"));
        assertEquals(schedule, JsonUtils.asSchedule(node, "key"));
    }

    @Test
    public void asObjectNode() throws Exception {
        JsonNode node = mapper.readTree(esc("{'key':{'subKey':'value'}}"));
        JsonNode subNode = mapper.readTree(esc("{'subKey':'value'}"));
        
        assertNull(JsonUtils.asObjectNode(node, null));
        assertNull(JsonUtils.asObjectNode(node, "badProp"));
        assertEquals(subNode, JsonUtils.asObjectNode(node, "key"));
    }

    @Test
    public void asArrayNode() throws Exception {
        JsonNode node = mapper.readTree(esc("{'key':[1,2,3,4]}"));
        JsonNode subNode = mapper.readTree(esc("[1,2,3,4]"));
        
        assertNull(JsonUtils.asArrayNode(node, null));
        assertNull(JsonUtils.asArrayNode(node, "badProp"));
        assertEquals(subNode, JsonUtils.asArrayNode(node, "key"));
    }

    @Test
    public void asBoolean() throws Exception {
        JsonNode node = mapper.readTree(esc("{'key':true}"));
        
        assertFalse(JsonUtils.asBoolean(node, null));
        assertFalse(JsonUtils.asBoolean(node, "badProp"));
        assertEquals(true, JsonUtils.asBoolean(node, "key"));
    }

    @Test
    public void asUIHint() throws Exception {
        JsonNode node = mapper.readTree(esc("{'key':'list'}"));
        
        assertNull(JsonUtils.asUIHint(node, null));
        assertNull(JsonUtils.asUIHint(node, "badProp"));
        assertEquals(UIHint.LIST, JsonUtils.asUIHint(node, "key"));
    }
    
    @Test
    public void asActivityType() throws Exception {
        JsonNode node = mapper.readTree(esc("{'key':'survey'}"));
        
        assertNull(JsonUtils.asActivityType(node, null));
        assertNull(JsonUtils.asActivityType(node, "badProp"));
        assertEquals(ActivityType.SURVEY, JsonUtils.asActivityType(node, "key"));
    }

    @Test
    public void asScheduleType() throws Exception {
        JsonNode node = mapper.readTree(esc("{'key':'once'}"));
        
        assertNull(JsonUtils.asScheduleType(node, null));
        assertNull(JsonUtils.asScheduleType(node, "badProp"));
        assertEquals(ScheduleType.ONCE, JsonUtils.asScheduleType(node, "key"));
    }
    
    @Test
    public void asImage() throws Exception {
        JsonNode node = mapper.readTree(esc("{'key':{'source':'sourceValue','width':50,'height':50}}"));
        Image image = new Image("sourceValue", 50, 50);
        
        assertNull(JsonUtils.asImage(node, null));
        assertNull(JsonUtils.asImage(node, "badProp"));
        assertEquals(image, JsonUtils.asImage(node, "key"));
    }

    @Test
    public void asStringList() throws Exception {
        List<String> list = Lists.newArrayList("A", "B", "C");
        
        JsonNode node = mapper.readTree(esc("{'key':['A','B','C']}"));
        
        assertEquals(Lists.newArrayList(), JsonUtils.asStringList(node, null));
        assertEquals(Lists.newArrayList(), JsonUtils.asStringList(node, "badProp"));
        assertEquals(list, JsonUtils.asStringList(node, "key"));
    }

    @Test
    public void asStringSet() throws Exception {
        Set<String> set = Sets.newHashSet("A", "B", "C");
        
        JsonNode node = mapper.readTree(esc("{'key':['A','B','C']}"));
        
        assertEquals(Sets.newHashSet(), JsonUtils.asStringSet(node, null));
        assertEquals(Sets.newHashSet(), JsonUtils.asStringSet(node, "badProp"));
        assertEquals(set, JsonUtils.asStringSet(node, "key"));
    }

}
