package org.sagebionetworks.bridge.json;

import static org.sagebionetworks.bridge.models.schedules.Schedule.ACTIVITIES_PROPERTY;
import static org.sagebionetworks.bridge.models.schedules.Schedule.ACTIVITY_REF_PROPERTY;
import static org.sagebionetworks.bridge.models.schedules.Schedule.ACTIVITY_TYPE_PROPERTY;
import static org.sagebionetworks.bridge.models.schedules.Schedule.CRON_TRIGGER_PROPERTY;
import static org.sagebionetworks.bridge.models.schedules.Schedule.ENDS_ON_PROPERTY;
import static org.sagebionetworks.bridge.models.schedules.Schedule.EXPIRES_PROPERTY;
import static org.sagebionetworks.bridge.models.schedules.Schedule.LABEL_PROPERTY;
import static org.sagebionetworks.bridge.models.schedules.Schedule.SCHEDULE_TYPE_NAME;
import static org.sagebionetworks.bridge.models.schedules.Schedule.SCHEDULE_TYPE_PROPERTY;
import static org.sagebionetworks.bridge.models.schedules.Schedule.STARTS_ON_PROPERTY;
import static org.sagebionetworks.bridge.models.schedules.Schedule.TYPE_PROPERTY_NAME;

import java.io.IOException;

import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.Schedule;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

public class ScheduleSerializer extends JsonSerializer<Schedule> {

    @Override
    public void serialize(Schedule schedule, JsonGenerator gen, SerializerProvider provider) throws IOException,
            JsonProcessingException {
        
        gen.writeStartObject();
        writeString(gen, LABEL_PROPERTY, schedule.getLabel());
        writeString(gen, CRON_TRIGGER_PROPERTY, schedule.getCronTrigger());
        if (schedule.getStartsOn() != null && schedule.getStartsOn() != 0L) {
            writeString(gen, STARTS_ON_PROPERTY, DateUtils.convertToISODateTime(schedule.getStartsOn()));    
        }
        if (schedule.getEndsOn() != null && schedule.getEndsOn() != 0L) {
            writeString(gen, ENDS_ON_PROPERTY, DateUtils.convertToISODateTime(schedule.getEndsOn()));    
        }
        if (schedule.getExpires() != null && schedule.getExpires() != 0L) {
            writeString(gen, EXPIRES_PROPERTY, DateUtils.convertToDuration(schedule.getExpires()));    
        }
        writeString(gen, SCHEDULE_TYPE_PROPERTY, schedule.getScheduleType().name().toLowerCase());
        if (schedule.getActivities() != null && !schedule.getActivities().isEmpty()) {
            gen.writeFieldName(ACTIVITIES_PROPERTY);
            gen.writeStartArray();
            for (Activity activity : schedule.getActivities()) {
                gen.writeObject(activity);
            }
            gen.writeEndArray();
            Activity act = schedule.getActivities().get(0);
            writeString(gen, ACTIVITY_TYPE_PROPERTY, act.getActivityType().name().toLowerCase());
            writeString(gen, ACTIVITY_REF_PROPERTY, act.getRef());
        }
        gen.writeStringField(TYPE_PROPERTY_NAME, SCHEDULE_TYPE_NAME);
        gen.writeEndObject();
    }
    
    private void writeString(JsonGenerator gen, String fieldName, String value) throws IOException {
        if (value != null) {
            gen.writeStringField(fieldName, value);
        }
    }

}
