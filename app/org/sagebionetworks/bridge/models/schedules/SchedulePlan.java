package org.sagebionetworks.bridge.models.schedules;

import java.util.List;

import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.User;

public interface SchedulePlan {

    public String getStudyKey();
    public void setStudyKey(String studyKey);
    
    // This is literally the class type of the SchedulePlan subclass we wish to deserialize to.
    // using Jackson's @JsonSubTypes({  @Type(value = A.class, name = "a") }) syntax, or
    // http://programmerbruce.blogspot.com/2011/05/deserialize-json-with-jackson-into.html
    public String getType();
    public void setType(String type);
    
    public long getModifiedOn();
    public void setModifiedOn(long modifiedOn);
    
    public List<Schedule> generateSchedules(Study study, List<User> users);
}
