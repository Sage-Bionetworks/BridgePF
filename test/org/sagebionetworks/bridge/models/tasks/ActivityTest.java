package org.sagebionetworks.bridge.models.tasks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

import org.joda.time.DateTime;
import org.junit.Test;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.ActivityType;
import org.sagebionetworks.bridge.models.schedules.SurveyReference;
import org.sagebionetworks.bridge.models.schedules.SurveyResponseReference;

import com.fasterxml.jackson.databind.JsonNode;

public class ActivityTest {
    
    @Test
    public void equalsContract() {
        EqualsVerifier.forClass(Activity.class).suppress(Warning.NONFINAL_FIELDS).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void equalsForActivity() {
        EqualsVerifier.forClass(SurveyReference.class).suppress(Warning.NONFINAL_FIELDS).allFieldsShouldBeUsed().verify();   
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
        assertEquals("taskId", node.get("ref").asText());
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
        String refString = node.get("ref").asText();
        assertTrue(refString.matches("http[s]?://.*/v3/surveys/guid/revisions/2015-01-01T10:10:10.000Z"));
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
        String refString = node.get("ref").asText();
        assertTrue(refString.matches("http[s]?://.*/v3/surveys/guid/revisions/published"));
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
    public void canSerializeSurveyResponseActivity() throws Exception {
        Activity activity = new Activity.Builder().withLabel("Label")
            .withLabelDetail("Label Detail").withPublishedSurvey("identifier", "guid")
            .withSurveyResponse("BBB").build();
        
        BridgeObjectMapper mapper = BridgeObjectMapper.get();
        String json = mapper.writeValueAsString(activity);

        JsonNode node = mapper.readTree(json);
        assertEquals("Label", node.get("label").asText());
        assertEquals("Label Detail", node.get("labelDetail").asText());
        assertEquals("survey", node.get("activityType").asText());
        String refString = node.get("ref").asText();
        assertTrue(refString.matches("http[s]?://.*/v3/surveys/guid/revisions/published"));
        assertNotNull("guid", node.get("guid"));
        assertEquals("Activity", node.get("type").asText());
        
        JsonNode ref = node.get("survey");
        assertEquals("identifier", ref.get("identifier").asText());
        assertEquals("guid", ref.get("guid").asText());
        String href = ref.get("href").asText();
        assertTrue(href.matches("http[s]?://.*/v3/surveys/guid/revisions/published"));
        assertEquals("SurveyReference", ref.get("type").asText());
        
        ref = node.get("surveyResponse");
        assertEquals("BBB", ref.get("identifier").asText());
        href = ref.get("href").asText();
        assertTrue(href.matches("http[s]?://.*/v3/surveyresponses/BBB"));
        assertEquals("SurveyResponseReference", ref.get("type").asText());
        
        activity = mapper.readValue(json, Activity.class);
        assertEquals("Label", activity.getLabel());
        assertEquals("Label Detail", activity.getLabelDetail());
        assertEquals(ActivityType.SURVEY, activity.getActivityType());
        
        SurveyReference ref1 = activity.getSurvey();
        assertEquals("identifier", ref1.getIdentifier());
        assertNull("createdOn", ref1.getCreatedOn());
        assertEquals("guid", ref1.getGuid());
        assertTrue(ref1.getHref().matches("http[s]?://.*/v3/surveys/guid/revisions/published"));
        
        SurveyResponseReference ref2 = activity.getSurveyResponse();
        assertEquals("BBB", ref2.getIdentifier());
        assertTrue(ref2.getHref().matches("http[s]?://.*/v3/surveyresponses/BBB"));
    }
    
    @SuppressWarnings("deprecation")
    @Test
    public void olderPublishedActivitiesCanBeDeserialized() throws Exception {
        String oldJson = "{\"label\":\"Personal Health Survey\",\"ref\":\"https://webservices-staging.sagebridge.org/api/v2/surveys/ac1e57fd-5e8e-473f-b82f-bac7547b6783/revisions/published\",\"activityType\":\"survey\",\"survey\":{\"guid\":\"ac1e57fd-5e8e-473f-b82f-bac7547b6783\",\"type\":\"GuidCreatedOnVersionHolder\"},\"type\":\"Activity\"}";
        
        BridgeObjectMapper mapper = BridgeObjectMapper.get();
        Activity activity = mapper.readValue(oldJson, Activity.class);
        
        assertEquals("Personal Health Survey", activity.getLabel());
        assertTrue(activity.getRef().matches("http[s]?://.*/v3/surveys/ac1e57fd-5e8e-473f-b82f-bac7547b6783/revisions/published"));
        assertEquals(ActivityType.SURVEY, activity.getActivityType());
        
        SurveyReference ref = activity.getSurvey();
        // This would be the issue... no identifier
        assertNull("identifier is null", ref.getIdentifier());
        assertNull("createdOn null", ref.getCreatedOn());
        assertEquals("guid set", "ac1e57fd-5e8e-473f-b82f-bac7547b6783", ref.getGuid());
        assertTrue(ref.getHref().matches("http[s]?://.*/v3/surveys/ac1e57fd-5e8e-473f-b82f-bac7547b6783/revisions/published"));
    }
    
    @Test
    public void submittingJsonWithHrefWillNotBreak() throws Exception {
        String oldJson = "{\"label\":\"Personal Health Survey\",\"ref\":\"https://webservices-staging.sagebridge.org/api/v2/surveys/ac1e57fd-5e8e-473f-b82f-bac7547b6783/revisions/published\",\"activityType\":\"survey\",\"survey\":{\"guid\":\"ac1e57fd-5e8e-473f-b82f-bac7547b6783\",\"href\":\"junk\",\"type\":\"SurveyReference\"},\"type\":\"Activity\"}";
        
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
        Activity activity = new Activity.Builder().withGuid("AAA").withTask("task").withLabel("Label").build();
        
        activity = new Activity.Builder().withTask("task").withLabel("Label").build();
        assertNotNull(activity.getGuid());
    }
    
    @Test
    public void activityFieldsAreDeserialized() throws Exception {
        String activityJSON = "{\"label\":\"Label\",\"guid\":\"AAA\",\"task\":{\"identifier\":\"task\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"ref\":\"task\",\"type\":\"Activity\"}";
        
        Activity activity = BridgeObjectMapper.get().readValue(activityJSON, Activity.class);
        assertEquals("AAA", activity.getGuid());
    }

}
