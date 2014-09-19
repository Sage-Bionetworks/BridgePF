package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.*;

import org.junit.Test;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.surveys.TestSurvey;
import org.sagebionetworks.bridge.validators.SurveyValidator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Tests of the serialization and deserialization of all the data 
 * to/from JSON. This is complicated for surveys as we change their 
 * representation from the public API to the way they are stored in 
 * Dynamo.
 */
public class DynamoSurveyTest {
    
    @Test
    public void yetAnotherSerializationTest() throws Exception {
        SurveyValidator validator = new SurveyValidator();
        DynamoSurvey survey = new TestSurvey(false);
        try {
            validator.validate(survey);
        } catch(Throwable t) {
            fail(t.getMessage());
        }
        
        String string = JsonUtils.toJSON(survey);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readTree(string);
        DynamoSurvey newSurvey = DynamoSurvey.fromJson(node);
        // These are purposefully not copied over
        newSurvey.setStudyKey(survey.getStudyKey());
        newSurvey.setGuid(survey.getGuid());
        newSurvey.setVersionedOn(survey.getVersionedOn());
        newSurvey.setModifiedOn(survey.getModifiedOn());
        newSurvey.setPublished(survey.isPublished());
        try {
            validator.validate(newSurvey);
        } catch(Throwable t) {
            fail(t.getMessage());
        }
        
        assertEquals("Correct serialize/deserialize survey", survey.hashCode(), newSurvey.hashCode());
    }

}
