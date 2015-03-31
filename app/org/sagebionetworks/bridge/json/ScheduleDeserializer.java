package org.sagebionetworks.bridge.json;

import static org.sagebionetworks.bridge.models.schedules.Schedule.ACTIVITIES_PROPERTY;
import static org.sagebionetworks.bridge.models.schedules.Schedule.CRON_TRIGGER_PROPERTY;
import static org.sagebionetworks.bridge.models.schedules.Schedule.DELAY_PROPERTY;
import static org.sagebionetworks.bridge.models.schedules.Schedule.ENDS_ON_PROPERTY;
import static org.sagebionetworks.bridge.models.schedules.Schedule.EVENT_ID_PROPERTY;
import static org.sagebionetworks.bridge.models.schedules.Schedule.EXPIRES_PROPERTY;
import static org.sagebionetworks.bridge.models.schedules.Schedule.FREQUENCY_PROPERTY;
import static org.sagebionetworks.bridge.models.schedules.Schedule.LABEL_PROPERTY;
import static org.sagebionetworks.bridge.models.schedules.Schedule.SCHEDULE_TYPE_PROPERTY;
import static org.sagebionetworks.bridge.models.schedules.Schedule.STARTS_ON_PROPERTY;
import static org.sagebionetworks.bridge.models.schedules.Schedule.TIMES_PROPERTY;

import java.io.IOException;

import org.joda.time.LocalTime;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.Schedule;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

public class ScheduleDeserializer extends JsonDeserializer<Schedule> {

    @Override
    public Schedule deserialize(JsonParser parser, DeserializationContext context) throws IOException,
            JsonProcessingException {

        JsonNode node = parser.getCodec().readTree(parser);
        
        Schedule schedule = new Schedule();
        if (node.has(ACTIVITIES_PROPERTY)) {
            schedule.setActivities(JsonUtils.asEntityList(node.get(ACTIVITIES_PROPERTY), Activity.class));
        }
        if (node.has(TIMES_PROPERTY)) {
            ArrayNode array = (ArrayNode)node.get(TIMES_PROPERTY);
            for (int i=0; i < array.size(); i++) {
                LocalTime time = LocalTime.parse(array.get(i).asText());
                schedule.addTime(time);
            }
        }
        schedule.setLabel(JsonUtils.asText(node, LABEL_PROPERTY));
        schedule.setScheduleType(JsonUtils.asScheduleType(node, SCHEDULE_TYPE_PROPERTY));
        schedule.setCronTrigger(JsonUtils.asText(node, CRON_TRIGGER_PROPERTY));
        schedule.setStartsOn(JsonUtils.asDateTime(node, STARTS_ON_PROPERTY));
        schedule.setEndsOn(JsonUtils.asDateTime(node, ENDS_ON_PROPERTY));
        schedule.setExpires(JsonUtils.asPeriod(node, EXPIRES_PROPERTY));
        schedule.setDelay(JsonUtils.asPeriod(node, DELAY_PROPERTY));
        schedule.setFrequency(JsonUtils.asPeriod(node, FREQUENCY_PROPERTY));
        schedule.setEventId(JsonUtils.asText(node, EVENT_ID_PROPERTY));
        return schedule;
    }

}
