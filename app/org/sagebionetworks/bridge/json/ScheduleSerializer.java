package org.sagebionetworks.bridge.json;

import static org.sagebionetworks.bridge.models.schedules.Schedule.ACTIVITIES_PROPERTY;
import static org.sagebionetworks.bridge.models.schedules.Schedule.CRON_TRIGGER_PROPERTY;
import static org.sagebionetworks.bridge.models.schedules.Schedule.DELAY_PROPERTY;
import static org.sagebionetworks.bridge.models.schedules.Schedule.ENDS_ON_PROPERTY;
import static org.sagebionetworks.bridge.models.schedules.Schedule.EVENT_ID_PROPERTY;
import static org.sagebionetworks.bridge.models.schedules.Schedule.EXPIRES_PROPERTY;
import static org.sagebionetworks.bridge.models.schedules.Schedule.INTERVAL_PROPERTY;
import static org.sagebionetworks.bridge.models.schedules.Schedule.LABEL_PROPERTY;
import static org.sagebionetworks.bridge.models.schedules.Schedule.SCHEDULE_TYPE_NAME;
import static org.sagebionetworks.bridge.models.schedules.Schedule.SCHEDULE_TYPE_PROPERTY;
import static org.sagebionetworks.bridge.models.schedules.Schedule.STARTS_ON_PROPERTY;
import static org.sagebionetworks.bridge.models.schedules.Schedule.TIMES_PROPERTY;
import static org.sagebionetworks.bridge.models.schedules.Schedule.TYPE_PROPERTY_NAME;

import java.io.IOException;

import org.joda.time.LocalTime;
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
        if (schedule.getStartsOn() != null) {
            writeString(gen, STARTS_ON_PROPERTY, DateUtils.getISODateTime(schedule.getStartsOn()));    
        }
        if (schedule.getEndsOn() != null) {
            writeString(gen, ENDS_ON_PROPERTY, DateUtils.getISODateTime(schedule.getEndsOn()));    
        }
        if (schedule.getExpires() != null) {
            writeString(gen, EXPIRES_PROPERTY, schedule.getExpires().toString());    
        }
        if (schedule.getEventId() != null) {
            writeString(gen, EVENT_ID_PROPERTY, schedule.getEventId());
        }
        if (schedule.getDelay() != null) {
            writeString(gen, DELAY_PROPERTY, schedule.getDelay().toString());    
        }
        if (schedule.getInterval() != null) {
            writeString(gen, INTERVAL_PROPERTY, schedule.getInterval().toString());    
        }
        writeString(gen, SCHEDULE_TYPE_PROPERTY, schedule.getScheduleType().name().toLowerCase());
        if (schedule.getActivities() != null && !schedule.getActivities().isEmpty()) {
            gen.writeFieldName(ACTIVITIES_PROPERTY);
            gen.writeStartArray();
            for (Activity activity : schedule.getActivities()) {
                gen.writeObject(activity);
            }
            gen.writeEndArray();
        }
        if (schedule.getTimes() != null && !schedule.getTimes().isEmpty()) {
            gen.writeFieldName(TIMES_PROPERTY);
            gen.writeStartArray();
            for (LocalTime time : schedule.getTimes()) {
                gen.writeString(time.toString());
            }
            gen.writeEndArray();
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
