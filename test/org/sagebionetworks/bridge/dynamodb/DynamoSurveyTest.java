package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.surveys.DataType;
import org.sagebionetworks.bridge.models.surveys.DateConstraints;
import org.sagebionetworks.bridge.models.surveys.DateTimeConstraints;
import org.sagebionetworks.bridge.models.surveys.Image;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.TestSurvey;
import org.sagebionetworks.bridge.validators.SurveyValidator;
import org.sagebionetworks.bridge.validators.Validate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Tests of the serialization and deserialization of all the data 
 * to/from JSON. This is complicated for surveys as we change their 
 * representation from the public API to the way they are stored in 
 * Dynamo.
 */
public class DynamoSurveyTest {
    
    private Survey newSurvey;
    private Survey survey;
    
    @Before
    public void before() throws Exception {
        SurveyValidator validator = new SurveyValidator();
        newSurvey = new TestSurvey(false);
        try {
            Validate.entityThrowingException(validator, newSurvey);
        } catch(Throwable t) {
            fail(t.getMessage());
        }
        
        String string = JsonUtils.toJSON(newSurvey);
        
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readTree(string);
        survey = DynamoSurvey.fromJson(node);
        survey.setStudyIdentifier(newSurvey.getStudyIdentifier());
        survey.setGuid(newSurvey.getGuid());
        survey.setCreatedOn(newSurvey.getCreatedOn());
        survey.setModifiedOn(newSurvey.getModifiedOn());
        survey.setPublished(newSurvey.isPublished());
    }
    
    @Test
    public void yetAnotherSerializationTest() {
        // What? What does this even test?
        assertEquals("Correct serialize/deserialize survey", newSurvey.hashCode(), survey.hashCode());
    }
    
    @Test
    public void dateConstraintsPersisted() {
        DateConstraints dc = (DateConstraints)TestSurvey.selectBy(survey, DataType.DATE).getConstraints();
        assertNotNull("Earliest date exists", dc.getEarliestValue());
        assertNotNull("Latest date exists", dc.getLatestValue());

        DateTimeConstraints dtc = (DateTimeConstraints) TestSurvey.selectBy(survey, DataType.DATETIME).getConstraints();
        assertNotNull("Earliest date exists", dtc.getEarliestValue());
        assertNotNull("Latest date exists", dtc.getLatestValue());
    }
    
    @Test
    public void serializationTestWithInfoScreen() throws Exception {
        String guid = "24299c51-bb70-4f94-bcb1-c6667c0f0294";
        
        DynamoSurvey survey = new DynamoSurvey();
        survey.setCreatedOn(DateTime.now().getMillis());
        survey.setModifiedOn(DateTime.now().getMillis());
        survey.setGuid(guid);
        survey.setIdentifier("identify");
        survey.setName("The name of my survey");
        survey.setStudyIdentifier("api");
        survey.setVersion(2L);
        
        Image image = new Image("http://foo.bar", 100, 100);
        
        DynamoSurveyInfoScreen screen = new DynamoSurveyInfoScreen();
        screen.setGuid(guid);
        screen.setIdentifier("screenA");
        screen.setOrder(0);
        screen.setTitle("The title of the screen");
        screen.setPrompt("This is the prompt");
        screen.setPromptDetail("This is further explanation of the prompt.");
        screen.setImage(image);
        survey.getElements().add(screen);
        
        survey.getElements().add(new DynamoSurveyQuestion());
        
        BridgeObjectMapper mapper = BridgeObjectMapper.get();
        String jsonString = mapper.writeValueAsString(survey);
        JsonNode node = mapper.readTree(jsonString);
        
        JsonNode sn = node.get("elements").get(0);
        assertEquals(guid, JsonUtils.asText(sn, "guid"));
        assertEquals("screenA", JsonUtils.asText(sn, "identifier"));
        assertEquals("SurveyInfoScreen", JsonUtils.asText(sn, "type"));
        assertEquals(0, JsonUtils.asIntPrimitive(sn, "order"));
        assertEquals("This is the prompt", JsonUtils.asText(sn, "prompt"));
        assertEquals("This is further explanation of the prompt.", JsonUtils.asText(sn, "promptDetail"));
        assertEquals("The title of the screen", JsonUtils.asText(sn, "title"));
        assertEquals(image, JsonUtils.asImage(sn, "image"));
        Survey survey2 = DynamoSurvey.fromJson(node);
        survey2.setGuid(survey.getGuid());
        survey2.setStudyIdentifier(survey.getStudyIdentifier());
        survey2.setCreatedOn(survey.getCreatedOn());
        survey2.setModifiedOn(survey.getModifiedOn());
        
        assertEquals(survey, survey2);
        
        // There's only one question in the question list
        assertEquals(1, survey.getUnmodifiableQuestionList().size());
        assertEquals(DynamoSurveyQuestion.class, survey.getUnmodifiableQuestionList().get(0).getClass());
    }
    
    // This was a legacy property that we want removed. It cuts the size of surveys by half 
    // (before compression)
    @Test
    public void surveyJsonDoesNotIncludeQuestionsProperty() throws Exception {
        ObjectMapper mapper = BridgeObjectMapper.get();
        String json = mapper.writeValueAsString(survey);
        
        assertFalse(json.contains("\"questions\""));
    }
}
