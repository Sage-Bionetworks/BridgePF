package org.sagebionetworks.bridge.validators;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.ScheduleType;

public class ScheduleValidator implements Validator<Schedule> {

    @Override
    public void validateNew(Schedule schedule) throws InvalidEntityException, EntityAlreadyExistsException {
    }

    @Override
    public void validate(Schedule schedule) throws InvalidEntityException {
        Messages messages = new Messages();
        if (StringUtils.isBlank(schedule.getLabel())) {
            messages.add("Label cannot be empty");
        }
        if (StringUtils.isBlank(schedule.getActivityRef())) {
            messages.add("Activity ref cannot be empty");
        }
        if (StringUtils.isBlank(schedule.getSchedule())) {
            messages.add("Schedule cannot be empty");
        }
        if (StringUtils.isBlank(schedule.getSchedulePlanGuid())) {
            messages.add("The schedule plan GUID cannot be empty");
        }
        if (StringUtils.isBlank(schedule.getStudyUserCompoundKey())) {
            messages.add("Study/user compound key cannot be empty");
        }
        if (schedule.getActivityType() == null) {
            messages.add("Activty type cannot be null");
        }
        if (schedule.getScheduleType() == null) {
            messages.add("Schedule type cannot be null");
        }
        if (schedule.getScheduleType() == ScheduleType.ONCE) {
            if (StringUtils.isNotBlank(schedule.getSchedule())) {
                messages.add("Schedule should not be set when the schedule type is 'once'");
            }
        } else {
            if (StringUtils.isBlank(schedule.getSchedule())) {
                messages.add("Schedule cannot be empty");
            }
        }
        if (!messages.isEmpty()) {
            throw new InvalidEntityException(schedule, "Schedule is invalid: " + messages.join());
        }
    }

}
