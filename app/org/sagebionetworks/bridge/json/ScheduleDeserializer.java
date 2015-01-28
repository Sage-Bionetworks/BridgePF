package org.sagebionetworks.bridge.json;

import static org.sagebionetworks.bridge.models.schedules.Schedule.ACTIVITIES_PROPERTY;
import static org.sagebionetworks.bridge.models.schedules.Schedule.ACTIVITY_REF_PROPERTY;
import static org.sagebionetworks.bridge.models.schedules.Schedule.ACTIVITY_TYPE_PROPERTY;
import static org.sagebionetworks.bridge.models.schedules.Schedule.CRON_TRIGGER_PROPERTY;
import static org.sagebionetworks.bridge.models.schedules.Schedule.ENDS_ON_PROPERTY;
import static org.sagebionetworks.bridge.models.schedules.Schedule.EXPIRES_PROPERTY;
import static org.sagebionetworks.bridge.models.schedules.Schedule.LABEL_PROPERTY;
import static org.sagebionetworks.bridge.models.schedules.Schedule.SCHEDULE_TYPE_PROPERTY;
import static org.sagebionetworks.bridge.models.schedules.Schedule.STARTS_ON_PROPERTY;

import java.io.IOException;

import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.Schedule;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;

public class ScheduleDeserializer extends JsonDeserializer<Schedule> {

    @Override
    public Schedule deserialize(JsonParser parser, DeserializationContext context) throws IOException,
            JsonProcessingException {

        JsonNode node = parser.getCodec().readTree(parser);
        
        Schedule schedule = new Schedule();
        if (node.has(ACTIVITIES_PROPERTY)) {
            schedule.setActivities(JsonUtils.asEntityList(node.get(ACTIVITIES_PROPERTY), Activity.class));
        } else if (node.has(ACTIVITY_TYPE_PROPERTY) && node.has(ACTIVITY_REF_PROPERTY)) {
            String label = JsonUtils.asText(node, LABEL_PROPERTY);
            String ref = JsonUtils.asText(node, ACTIVITY_REF_PROPERTY);
            Activity activity = new Activity(label, ref);
            schedule.setActivities(Lists.newArrayList(activity));
        }
        schedule.setLabel(JsonUtils.asText(node, LABEL_PROPERTY));
        schedule.setScheduleType(JsonUtils.asScheduleType(node, SCHEDULE_TYPE_PROPERTY));
        schedule.setCronTrigger(JsonUtils.asText(node, CRON_TRIGGER_PROPERTY));
        schedule.setStartsOn(JsonUtils.asMillisDurationLong(node, STARTS_ON_PROPERTY));
        schedule.setEndsOn(JsonUtils.asMillisDurationLong(node, ENDS_ON_PROPERTY));
        schedule.setExpires(JsonUtils.asMillisDurationLong(node, EXPIRES_PROPERTY));
        
        return schedule;
    }

}
