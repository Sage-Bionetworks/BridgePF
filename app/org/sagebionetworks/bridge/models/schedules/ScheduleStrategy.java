package org.sagebionetworks.bridge.models.schedules;

import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.springframework.validation.Errors;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo( use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
    @Type(name="SimpleScheduleStrategy", value=SimpleScheduleStrategy.class),
    @Type(name="ABTestScheduleStrategy", value=ABTestScheduleStrategy.class)
})
public interface ScheduleStrategy {
    
    public Schedule getScheduleForUser(StudyIdentifier studyIdentifier, SchedulePlan plan, User user);
    
    public boolean doesScheduleSurvey(GuidCreatedOnVersionHolder keys);
    
    public void validate(Errors errors);

}
