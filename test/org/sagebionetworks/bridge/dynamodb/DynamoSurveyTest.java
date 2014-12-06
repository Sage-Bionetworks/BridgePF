package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.surveys.DataType;
import org.sagebionetworks.bridge.models.surveys.DateConstraints;
import org.sagebionetworks.bridge.models.surveys.DateTimeConstraints;
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
    
    private Survey survey;
    
    @Before
    public void before() throws Exception {
        SurveyValidator validator = new SurveyValidator();
        DynamoSurvey newSurvey = new TestSurvey(false);
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
        assertEquals("Correct serialize/deserialize survey", survey.hashCode(), survey.hashCode());
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
}
