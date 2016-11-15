package org.sagebionetworks.bridge.models.schedules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import nl.jqno.equalsverifier.EqualsVerifier;

import org.joda.time.DateTime;
import org.junit.Test;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.fasterxml.jackson.databind.JsonNode;

public class ActivityTest {
    @Test
    public void equalsContract() {
        EqualsVerifier.forClass(Activity.class).allFieldsShouldBeUsed().verify();
    }

    @Test
    public void equalsForActivity() {
        EqualsVerifier.forClass(SurveyReference.class).allFieldsShouldBeUsed().verify();
    }

    @Test
    public void canSerializeCompoundActivity() throws Exception {
        // Start with JSON. For simplicity, only have the taskIdentifier in the CompoundActivity, so we aren't
        // sensitive to changes in that class.
        String jsonText = "{\n" +
                "   \"label\":\"My Activity\",\n" +
                "   \"labelDetail\":\"Description of activity\",\n" +
                "   \"guid\":\"test-guid\"\n," +
                "   \"compoundActivity\":{\"taskIdentifier\":\"combo-activity\"}\n" +
                "}";

        // convert to POJO
        Activity activity = BridgeObjectMapper.get().readValue(jsonText, Activity.class);
        assertEquals("My Activity", activity.getLabel());
        assertEquals("Description of activity", activity.getLabelDetail());
        assertEquals("test-guid", activity.getGuid());
        assertEquals("combo-activity", activity.getCompoundActivity().getTaskIdentifier());

        // convert back to JSON
        JsonNode jsonNode = BridgeObjectMapper.get().convertValue(activity, JsonNode.class);
        assertEquals("My Activity", jsonNode.get("label").textValue());
        assertEquals("Description of activity", jsonNode.get("labelDetail").textValue());
        assertEquals("test-guid", jsonNode.get("guid").textValue());
        assertEquals("combo-activity", jsonNode.get("compoundActivity").get("taskIdentifier").textValue());
        assertEquals("compound", jsonNode.get("activityType").textValue());
        assertEquals("Activity", jsonNode.get("type").textValue());
    }

    @Test
    public void canSerializeTaskActivity() throws Exception {
        Activity activity = new Activity.Builder().withLabel("Label")
            .withLabelDetail("Label Detail").withTask("taskId").build();
        
        BridgeObjectMapper mapper = BridgeObjectMapper.get();
        String json = mapper.writeValueAsString(activity);
        
        JsonNode node = mapper.readTree(json);
        assertEquals("Label", node.get("label").asText());
        assertEquals("Label Detail", node.get("labelDetail").asText());
        assertEquals("task", node.get("activityType").asText());
        assertEquals("taskId", node.get("task").get("identifier").asText());
        assertNotNull("guid", node.get("guid"));
        assertEquals("Activity", node.get("type").asText());
        
        JsonNode taskRef = node.get("task");
        assertEquals("taskId", taskRef.get("identifier").asText());
        assertEquals("TaskReference", taskRef.get("type").asText());
        
        activity = mapper.readValue(json, Activity.class);
        assertEquals("Label", activity.getLabel());
        assertEquals("Label Detail", activity.getLabelDetail());
        assertEquals(ActivityType.TASK, activity.getActivityType());
        assertEquals("taskId", activity.getTask().getIdentifier());
    }
    
    @Test
    public void canSerializeSurveyActivity() throws Exception {
        Activity activity = new Activity.Builder().withLabel("Label")
            .withLabelDetail("Label Detail").withSurvey("identifier", "guid", DateTime.parse("2015-01-01T10:10:10Z")).build();
        
        BridgeObjectMapper mapper = BridgeObjectMapper.get();
        String json = mapper.writeValueAsString(activity);
        
        JsonNode node = mapper.readTree(json);
        assertEquals("Label", node.get("label").asText());
        assertEquals("Label Detail", node.get("labelDetail").asText());
        assertEquals("survey", node.get("activityType").asText());
        String hrefString = node.get("survey").get("href").asText();
        assertTrue(hrefString.matches("http[s]?://.*/v3/surveys/guid/revisions/2015-01-01T10:10:10.000Z"));
        assertNotNull("guid", node.get("guid"));
        assertEquals("Activity", node.get("type").asText());
        
        JsonNode ref = node.get("survey");
        assertEquals("identifier", ref.get("identifier").asText());
        assertEquals("guid", ref.get("guid").asText());
        assertEquals("2015-01-01T10:10:10.000Z", ref.get("createdOn").asText());
        String href = ref.get("href").asText();
        assertTrue(href.matches("http[s]?://.*/v3/surveys/guid/revisions/2015-01-01T10:10:10.000Z"));
        assertEquals("SurveyReference", ref.get("type").asText());
        
        activity = mapper.readValue(json, Activity.class);
        assertEquals("Label", activity.getLabel());
        assertEquals("Label Detail", activity.getLabelDetail());
        assertEquals(ActivityType.SURVEY, activity.getActivityType());
        
        SurveyReference ref1 = activity.getSurvey();
        assertEquals("identifier", ref1.getIdentifier());
        assertEquals(DateTime.parse("2015-01-01T10:10:10.000Z"), ref1.getCreatedOn());
        assertEquals("guid", ref1.getGuid());
        assertTrue(ref1.getHref().matches("http[s]?://.*/v3/surveys/guid/revisions/2015-01-01T10:10:10.000Z"));
    }
    
    @Test
    public void canSerializePublishedSurveyActivity() throws Exception {
        Activity activity = new Activity.Builder().withLabel("Label")
            .withLabelDetail("Label Detail").withPublishedSurvey("identifier", "guid").build();
        
        BridgeObjectMapper mapper = BridgeObjectMapper.get();
        String json = mapper.writeValueAsString(activity);

        JsonNode node = mapper.readTree(json);
        assertEquals("Label", node.get("label").asText());
        assertEquals("Label Detail", node.get("labelDetail").asText());
        assertEquals("survey", node.get("activityType").asText());
        String hrefString = node.get("survey").get("href").asText();
        assertTrue(hrefString.matches("http[s]?://.*/v3/surveys/guid/revisions/published"));
        assertNotNull("guid", node.get("guid"));
        assertEquals("Activity", node.get("type").asText());
        
        JsonNode ref = node.get("survey");
        assertEquals("identifier", ref.get("identifier").asText());
        assertEquals("guid", ref.get("guid").asText());
        String href = ref.get("href").asText();
        assertTrue(href.matches("http[s]?://.*/v3/surveys/guid/revisions/published"));
        assertEquals("SurveyReference", ref.get("type").asText());
        
        activity = mapper.readValue(json, Activity.class);
        assertEquals("Label", activity.getLabel());
        assertEquals("Label Detail", activity.getLabelDetail());
        assertEquals(ActivityType.SURVEY, activity.getActivityType());
        
        SurveyReference ref1 = activity.getSurvey();
        assertEquals("identifier", ref1.getIdentifier());
        assertNull("createdOn", ref1.getCreatedOn());
        assertEquals("guid", ref1.getGuid());
        assertTrue(ref1.getHref().matches("http[s]?://.*/v3/surveys/guid/revisions/published"));
    }
    
    @Test
    public void olderPublishedActivitiesCanBeDeserialized() throws Exception {
        String oldJson = "{\"label\":\"Personal Health Survey\",\"ref\":\"https://webservices-staging.sagebridge.org/api/v2/surveys/ac1e57fd-5e8e-473f-b82f-bac7547b6783/revisions/published\",\"activityType\":\"survey\",\"survey\":{\"guid\":\"ac1e57fd-5e8e-473f-b82f-bac7547b6783\",\"identifier\":\"identifier\",\"type\":\"GuidCreatedOnVersionHolder\"},\"type\":\"Activity\"}";
        
        BridgeObjectMapper mapper = BridgeObjectMapper.get();
        Activity activity = mapper.readValue(oldJson, Activity.class);
        
        assertEquals("Personal Health Survey", activity.getLabel());
        assertEquals(ActivityType.SURVEY, activity.getActivityType());
        
        SurveyReference ref = activity.getSurvey();
        assertEquals("identifier", ref.getIdentifier());
        assertNull("createdOn null", ref.getCreatedOn());
        assertEquals("guid set", "ac1e57fd-5e8e-473f-b82f-bac7547b6783", ref.getGuid());
        assertTrue(ref.getHref().matches("http[s]?://.*/v3/surveys/ac1e57fd-5e8e-473f-b82f-bac7547b6783/revisions/published"));
    }
    
    @Test
    public void submittingJsonWithHrefWillNotBreak() throws Exception {
        String oldJson = "{\"label\":\"Personal Health Survey\",\"ref\":\"https://webservices-staging.sagebridge.org/api/v2/surveys/ac1e57fd-5e8e-473f-b82f-bac7547b6783/revisions/published\",\"activityType\":\"survey\",\"survey\":{\"guid\":\"ac1e57fd-5e8e-473f-b82f-bac7547b6783\",\"href\":\"junk\",\"identifier\":\"identifier\",\"type\":\"SurveyReference\"},\"type\":\"Activity\"}";
        
        BridgeObjectMapper mapper = BridgeObjectMapper.get();
        Activity activity = mapper.readValue(oldJson, Activity.class);
        
        assertNotEquals("junk", activity.getSurvey().getHref());
    }
    
    @Test
    public void creatingSurveyWithoutCreatedOnIsExpressedAsPublished() throws Exception {
        Activity activity = new Activity.Builder().withSurvey("identifier", "guid", null).withLabel("Label").build();
        
        assertTrue(activity.getSurvey().getHref().matches("http[s]?://.*/v3/surveys/guid/revisions/published"));
    }
    
    @Test
    public void createAGuidIfNoneIsSet() throws Exception {
        Activity activity = new Activity.Builder().withTask("task").withLabel("Label").build();
        assertNotNull(activity.getGuid());
    }
    
    @Test
    public void activityFieldsAreDeserialized() throws Exception {
        String activityJSON = "{\"label\":\"Label\",\"guid\":\"AAA\",\"task\":{\"identifier\":\"task\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"ref\":\"task\",\"type\":\"Activity\"}";
        
        Activity activity = BridgeObjectMapper.get().readValue(activityJSON, Activity.class);
        assertEquals("AAA", activity.getGuid());
    }
    
    /**
     * Many of these cases should go away. The only thing we'll be interested in is the completion of an activity.
     * But it all works during the transition. 
     * @throws Exception
     */
    @Test
    public void activityKnowsWhenItIsPersistentlyScheduled() throws Exception {
        // This is persistently scheduled due to an activity
        Schedule schedule = BridgeObjectMapper.get().readValue("{\"scheduleType\":\"once\",\"eventId\":\"activity:HHH:finished\",\"activities\":[{\"label\":\"Label\",\"labelDetail\":\"Label Detail\",\"guid\":\"HHH\",\"task\":{\"identifier\":\"foo\"},\"activityType\":\"task\"}]}", Schedule.class);
        assertTrue(schedule.getActivities().get(0).isPersistentlyRescheduledBy(schedule));
        
        // This is persistently schedule due to an activity completion. We actually never generate this event, and it will go away.
        schedule = BridgeObjectMapper.get().readValue("{\"scheduleType\":\"once\",\"eventId\":\"task:foo:finished\",\"activities\":[{\"label\":\"Label\",\"labelDetail\":\"Label Detail\",\"guid\":\"HHH\",\"task\":{\"identifier\":\"foo\"},\"activityType\":\"task\"}]}", Schedule.class);
        assertTrue(schedule.getActivities().get(0).isPersistentlyRescheduledBy(schedule));
        
        // This is persistently schedule due to a survey completion. This should not match (it's not a survey)
        schedule = BridgeObjectMapper.get().readValue("{\"scheduleType\":\"once\",\"eventId\":\"survey:HHH:finished\",\"activities\":[{\"label\":\"Label\",\"labelDetail\":\"Label Detail\",\"guid\":\"HHH\",\"task\":{\"identifier\":\"foo\"},\"activityType\":\"task\"}]}", Schedule.class);
        assertFalse(schedule.getActivities().get(0).isPersistentlyRescheduledBy(schedule));
        
        // Wrong activity, not persistent
        schedule = BridgeObjectMapper.get().readValue("{\"scheduleType\":\"once\",\"eventId\":\"survey:HHH:finished\",\"activities\":[{\"label\":\"Label\",\"labelDetail\":\"Label Detail\",\"guid\":\"III\",\"task\":{\"identifier\":\"foo\"},\"activityType\":\"task\"}]}", Schedule.class);
        assertFalse(schedule.getActivities().get(0).isPersistentlyRescheduledBy(schedule));
    }

    @Test
    public void compoundActivity() {
        CompoundActivity compoundActivity = new CompoundActivity.Builder().withTaskIdentifier("combo-activity")
                .build();
        Activity activity = new Activity.Builder().withLabel("My Label").withLabelDetail("My Label Detail")
                .withGuid("AAA").withCompoundActivity(compoundActivity).build();
        assertEquals("My Label", activity.getLabel());
        assertEquals("My Label Detail", activity.getLabelDetail());
        assertEquals("AAA", activity.getGuid());
        assertEquals(compoundActivity, activity.getCompoundActivity());
        assertEquals(ActivityType.COMPOUND, activity.getActivityType());
        assertEquals("compound:combo-activity:finished", activity.getSelfFinishedEventId());

        // toString() gives a lot of stuff and depends on two other classes. To make the tests robust and resilient to
        // changes in encapsulated classes, just test a few keywords
        String activityString = activity.toString();
        assertTrue(activityString.contains("My Label"));
        assertTrue(activityString.contains("My Label Detail"));
        assertTrue(activityString.contains("AAA"));
        assertTrue(activityString.contains("combo-activity"));
        assertTrue(activityString.contains("COMPOUND"));

        // test copy constructor
        Activity copy = new Activity.Builder().withActivity(activity).build();
        assertEquals(activity, copy);
    }

    @Test
    public void taskActivityByRef() {
        TaskReference task = new TaskReference("my-task");
        Activity activity = new Activity.Builder().withTask(task).build();
        assertEquals(task, activity.getTask());
        assertEquals(ActivityType.TASK, activity.getActivityType());
        assertEquals("task:my-task:finished", activity.getSelfFinishedEventId());

        String activityString = activity.toString();
        assertTrue(activityString.contains("my-task"));
        assertTrue(activityString.contains("TASK"));

        // test copy constructor
        Activity copy = new Activity.Builder().withActivity(activity).build();
        assertEquals(activity, copy);
    }

    @Test
    public void taskActivityById() {
        // This is already mostly tested above. Just test passing in task ID sets the task correctly.
        Activity activity = new Activity.Builder().withTask("my-task").build();
        assertEquals(new TaskReference("my-task"), activity.getTask());
    }

    @Test
    public void surveyActivityByRef() {
        SurveyReference survey = new SurveyReference("my-survey", "BBB", null);
        Activity activity = new Activity.Builder().withSurvey(survey).build();
        assertEquals(survey, activity.getSurvey());
        assertEquals(ActivityType.SURVEY, activity.getActivityType());
        assertEquals("survey:BBB:finished", activity.getSelfFinishedEventId());

        String activityString = activity.toString();
        assertTrue(activityString.contains("my-survey"));
        assertTrue(activityString.contains("BBB"));
        assertTrue(activityString.contains("SURVEY"));

        // test copy constructor
        Activity copy = new Activity.Builder().withActivity(activity).build();
        assertEquals(activity, copy);
    }

    @Test
    public void surveyActivityByIdGuidCreatedOn() {
        // most of this tested above
        DateTime createdOn = DateTime.now();
        Activity activity = new Activity.Builder().withSurvey("my-survey", "BBB", createdOn).build();
        assertEquals(new SurveyReference("my-survey", "BBB", createdOn), activity.getSurvey());
    }

    @Test
    public void publishedSurveyActivity() {
        // most of this tested above
        Activity activity = new Activity.Builder().withPublishedSurvey("my-published-survey", "CCC").build();
        assertEquals(new SurveyReference("my-published-survey", "CCC", null), activity.getSurvey());
    }
}
