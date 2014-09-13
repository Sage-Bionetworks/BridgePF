package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.Ignore;
import org.junit.Test;
import org.sagebionetworks.bridge.models.surveys.DataType;
import org.sagebionetworks.bridge.models.surveys.DateConstraints;
import org.sagebionetworks.bridge.models.surveys.IntegerConstraints;
import org.sagebionetworks.bridge.models.surveys.MultiValueConstraints;
import org.sagebionetworks.bridge.models.surveys.StringConstraints;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestionOption;
import org.sagebionetworks.bridge.models.surveys.UIHint;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;

/**
 * Tests of the serialization and deserialization of all the data 
 * to/from JSON. This is complicated for surveys as we change their 
 * representation from the public API to the way they are stored in 
 * Dynamo.
 */
public class DynamoSurveyTest {

    private DynamoSurvey createSurvey() {
        DynamoSurvey survey = new DynamoSurvey();
        survey.setName("Health Study Overview");
        survey.setIdentifier("overview");
        survey.setStudyKey("test");
        survey.setGuid("guid");
        survey.setVersionedOn(1410456994338L);
        survey.setModifiedOn(1410456994338L);
        survey.setVersion(3L);
        survey.setPublished(true);
        return survey;
    }
    
    private DynamoSurveyQuestion createIntegerQuestion() {
        DynamoSurveyQuestion question = new DynamoSurveyQuestion();
        question.setPrompt("How days of the week do you exersize?");
        question.setSurveyKeyComponents("foo", 1L);
        question.setIdentifier("how_much_exersize");
        question.setGuid("guid2");
        question.setUiHint(UIHint.SLIDER);
        
        IntegerConstraints c = new IntegerConstraints();
        c.setMaxValue(7);
        question.setConstraints(c);
        
        return question;
    }
    
    private DynamoSurveyQuestion createStringQuestion() {
        DynamoSurveyQuestion question = new DynamoSurveyQuestion();
        question.setPrompt("How days of the week do you exersize?");
        question.setSurveyKeyComponents("foo", 1L);
        question.setIdentifier("how_much_exersize");
        question.setGuid("guid2");
        question.setUiHint(UIHint.TEXTFIELD);
        
        List<SurveyQuestionOption> options = Lists.newArrayList(
            new SurveyQuestionOption("1", "2", "3"),
            new SurveyQuestionOption("4", "5", "6")
        );
        
        MultiValueConstraints c = new MultiValueConstraints(DataType.STRING);
        c.setEnumeration(options);
        question.setConstraints(c);
        
        return question;
    }
    
    private DynamoSurveyQuestion createDateQuestion() {
        DynamoSurveyQuestion question = new DynamoSurveyQuestion();
        question.setPrompt("Please enter the last six calendar dates you had a medical appointment");
        question.setSurveyKeyComponents("foo", 1L);
        question.setIdentifier("visits");
        question.setGuid("guid3");
        question.setUiHint(UIHint.DATEPICKER);

        DateConstraints dc = new DateConstraints();
        question.setConstraints(dc);
        
        return question;
    }
    
    @Test
    @Ignore
    public void serializeAndDeserializeDoNotLoseInformation() throws Exception {
        DynamoSurvey survey = createSurvey();
        
        DynamoSurveyQuestion q1 = createIntegerQuestion();
        survey.getQuestions().add(q1);
        
        DynamoSurveyQuestion q2 = createStringQuestion();
        survey.getQuestions().add(q2);
        
        DynamoSurveyQuestion q3 = createDateQuestion();
        survey.getQuestions().add(q3);
        
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(survey);
        System.out.println(json);
        JsonNode node = mapper.readTree(json);
        DynamoSurvey newSurvey = DynamoSurvey.fromJson(node);
        
        assertEquals("Name is correct", survey.getName(), newSurvey.getName());
        assertEquals("Identifier is correct", survey.getIdentifier(), newSurvey.getIdentifier());
        assertEquals("version is correct", survey.getVersion(), newSurvey.getVersion());
        
        DynamoSurveyQuestion newQ1 = (DynamoSurveyQuestion)newSurvey.getQuestions().get(0); // integer question
        assertEquals("Q1 guid is correct", q1.getGuid(), newQ1.getGuid());
        // Order is never set externally, actually there are other fields like this as well.
        assertEquals("Q1 identifier is correct", q1.getIdentifier(), newQ1.getIdentifier());
        assertEquals("Q1 prompt is correct", q1.getPrompt(), newQ1.getPrompt());
        assertEquals("Q1 hints are correct", q1.getUiHint(), newQ1.getUiHint());
        assertEquals("Q1 constraints are correct", q1.getConstraints(), newQ1.getConstraints());
        
        // Just check constraints now. If these work, that's two cases of the constraints being
        // serialized correctly using Jackson, and that's enough.
        StringConstraints sc1 = (StringConstraints)survey.getQuestions().get(1).getConstraints();
        StringConstraints sc2 = (StringConstraints)newSurvey.getQuestions().get(1).getConstraints();
        assertEquals("Q1 constraints are correct", sc1, sc2);
    }

}
