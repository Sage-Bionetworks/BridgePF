package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Assert;
import org.junit.Test;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.schedules.SimpleScheduleStrategy;

import com.fasterxml.jackson.databind.JsonNode;

public class DynamoScheduleTest {

    
    @Test
    public void canDeserializeV1Schedule() throws Exception {
        String surveyUrl = "https://parkinson-staging.sagebridge.org/api/v1/surveys/7f45f172-32bb-4302-8096-3ac33736dbfb/2014-12-04T18:35:47.894Z";
        String dataString = "{\"type\":\"SimpleScheduleStrategy\",\"schedule\":{\"label\":\"Enrollment survey\",\"activityType\":\"survey\",\"activityRef\":\"https://parkinson-staging.sagebridge.org/api/v1/surveys/7f45f172-32bb-4302-8096-3ac33736dbfb/2014-12-04T18:35:47.894Z\",\"scheduleType\":\"once\",\"type\":\"Schedule\"}}";

        SimpleScheduleStrategy strategy = new BridgeObjectMapper().readValue(dataString, SimpleScheduleStrategy.class);
        
        assertEquals("Should be one activity", 1, strategy.getSchedule().getActivities().size());
        
        Activity activity = strategy.getSchedule().getActivities().get(0);
        assertEquals("Shows first activity in v1 activityType field", "survey", strategy.getSchedule().getActivityType());
        assertEquals("Shows first activity in v1 activityType field", "survey", activity.getType());
        assertEquals("Shows first activity in v1 activityRef field", surveyUrl, strategy.getSchedule().getActivityRef());
        assertEquals("Shows first activity in v1 activityRef field", surveyUrl, activity.getRef());
    }
    
    @Test
    public void canDeserializeFromClientJson() throws Exception {
        // must have schedule plan JSON for this.
        String schedulePlanString = "{\"guid\":\"98f423c6-d614-4874-8002-35eca2181685\",\"studyKey\":\"parkinson\",\"version\":3,\"modifiedOn\":\"2014-12-04T18:35:47.308Z\",\"strategy\":{\"type\":\"SimpleScheduleStrategy\",\"schedule\":{\"label\":\"Monthly Survey\",\"activityType\":\"survey\",\"activityRef\":\"https://parkinson-staging.sagebridge.org/api/v1/surveys/260666a8-e9fe-46df-8832-75d3e4f7448e/2014-12-04T18:35:46.587Z\",\"scheduleType\":\"recurring\",\"cronTrigger\":\"0 0 6 ? 1/1 THU#1 *\",\"expires\":\"PT168H\",\"type\":\"Schedule\"}},\"type\":\"SchedulePlan\"}";

        JsonNode node = new BridgeObjectMapper().readTree(schedulePlanString);
        
        SchedulePlan plan = DynamoSchedulePlan.fromJson(node);
        fail("Not implemented");
        
    }
    
}
