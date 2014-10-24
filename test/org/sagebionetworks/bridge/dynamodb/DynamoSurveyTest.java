package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.sagebionetworks.bridge.json.JsonUtils;
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
    
    @Test
    public void yetAnotherSerializationTest() throws Exception {
        SurveyValidator validator = new SurveyValidator();
        DynamoSurvey survey = new TestSurvey(false);
        try {
            Validate.entityThrowingException(validator, survey);
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
            Validate.entityThrowingException(validator, newSurvey);
        } catch(Throwable t) {
            fail(t.getMessage());
        }
        
        assertEquals("Correct serialize/deserialize survey", survey.hashCode(), newSurvey.hashCode());
    }
    
}
