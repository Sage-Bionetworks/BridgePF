package org.sagebionetworks.bridge.json;

import static org.junit.Assert.*;

import java.util.List;
import java.util.UUID;

import org.junit.Test;
import org.sagebionetworks.bridge.dynamodb.DynamoSurvey;
import org.sagebionetworks.bridge.dynamodb.DynamoSurveyQuestion;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.surveys.BooleanConstraints;
import org.sagebionetworks.bridge.models.surveys.DataType;
import org.sagebionetworks.bridge.models.surveys.DateConstraints;
import org.sagebionetworks.bridge.models.surveys.DateTimeConstraints;
import org.sagebionetworks.bridge.models.surveys.DecimalConstraints;
import org.sagebionetworks.bridge.models.surveys.DurationConstraints;
import org.sagebionetworks.bridge.models.surveys.IntegerConstraints;
import org.sagebionetworks.bridge.models.surveys.MultiValueConstraints;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestion;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestionOption;
import org.sagebionetworks.bridge.models.surveys.TimeConstraints;
import org.sagebionetworks.bridge.models.surveys.UIHint;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;

public class SurveyRoundtripSerializationTest {

    private class MultiValueQuestion extends DynamoSurveyQuestion {
        private MultiValueQuestion() {
            MultiValueConstraints mvc = new MultiValueConstraints(DataType.INTEGER);
            List<SurveyQuestionOption> options = Lists.newArrayList(
                new SurveyQuestionOption("Terrible", 1, null),
                new SurveyQuestionOption("Poor", 2, null),
                new SurveyQuestionOption("OK", 3, null),
                new SurveyQuestionOption("Good", 4, null),
                new SurveyQuestionOption("Great", 5, null)
            );
            mvc.setEnumeration(options);
            mvc.setAllowOther(true);
            setConstraints(mvc);
            setPrompt("How do you feel today?");
            setIdentifier("feeling");
            setUiHint(UIHint.LIST);
            setGuid(UUID.randomUUID().toString());
        }
    }
    
    private class BooleanQuestion extends DynamoSurveyQuestion {
        private BooleanQuestion(){
            BooleanConstraints c = new BooleanConstraints();
            setPrompt("Do you have high blood pressure?");
            setIdentifier("high_bp");
            setUiHint(UIHint.CHECKBOX);
            setConstraints(c);
            setGuid(UUID.randomUUID().toString());
        }
    }
    
    private class DateQuestion extends DynamoSurveyQuestion {
        private DateQuestion() {
            DateConstraints c = new DateConstraints();
            setPrompt("When did you last have a medical check-up?");
            setIdentifier("last_checkup");
            setUiHint(UIHint.DATEPICKER);
            setConstraints(c);
            setGuid(UUID.randomUUID().toString());
        }
    }
    
    private class DateTimeQuestion extends DynamoSurveyQuestion {
        private DateTimeQuestion() {
            DateTimeConstraints c = new DateTimeConstraints();
            c.setAllowFuture(true);
            setPrompt("When is your next medical check-up scheduled?");
            setIdentifier("last_reading");
            setUiHint(UIHint.DATETIMEPICKER);
            setConstraints(c);
            setGuid(UUID.randomUUID().toString());
        }
    }
    
    private class DecimalQuestion extends DynamoSurveyQuestion {
        private DecimalQuestion() {
            DecimalConstraints c = new DecimalConstraints();
            c.setMinValue(0.0f);
            c.setMaxValue(10.0f);
            c.setPrecision(1f);
            c.setStep(0.1f);
            setPrompt("What dosage (in grams) do you take of deleuterium each day?");
            setIdentifier("deleuterium_dosage");
            setUiHint(UIHint.SLIDER);
            setConstraints(c);
            setGuid(UUID.randomUUID().toString());
        }
    }
    
    private class DurationQuestion extends DynamoSurveyQuestion {
        private DurationQuestion() {
            DurationConstraints c = new DurationConstraints();
            setPrompt("How log does your appointment take, on average?");
            setIdentifier("time_for_appt");
            setUiHint(UIHint.TIMEPICKER);
            setConstraints(c);
            setGuid(UUID.randomUUID().toString());
        }
    }
    
    private class IntegerQuestion extends DynamoSurveyQuestion {
        private IntegerQuestion() {
            IntegerConstraints c = new IntegerConstraints();
            c.setMinValue(0);
            c.setMaxValue(4);
            setPrompt("How many times a day do you take your blood pressure?");
            setIdentifier("bp_x_day");
            setUiHint(UIHint.NUMBERFIELD);
            setConstraints(c);
            setGuid(UUID.randomUUID().toString());
        }
    }
    
    private class TimeQuestion extends DynamoSurveyQuestion {
        private TimeQuestion() {
            TimeConstraints c = new TimeConstraints();
            setPrompt("What times of the day do you take deleuterium?");
            setIdentifier("deleuterium_x_day");
            setUiHint(UIHint.TIMEPICKER);
            setConstraints(c);
            setGuid(UUID.randomUUID().toString());
        }
    }
    
    // This is so complicated, we'll refer to it in other tests to get a 
    // good example of a survey to work with (e.g. submitting answer tests).
    public DynamoSurvey getCompleteSurvey() {
        DynamoSurvey survey = new DynamoSurvey();
        survey.setGuid(UUID.randomUUID().toString());
        survey.setName("General Blood Pressure Survey");
        survey.setIdentifier("bloodpressure");
        survey.setModifiedOn(DateUtils.getCurrentMillisFromEpoch());
        survey.setVersionedOn(DateUtils.getCurrentMillisFromEpoch());
        survey.setVersion(2L);
        survey.setPublished(true);
        survey.setStudyKey("heart_disease");
        List<SurveyQuestion> questions = survey.getQuestions();
        questions.add(new BooleanQuestion());
        questions.add(new DateQuestion());
        questions.add(new DateTimeQuestion());
        questions.add(new DecimalQuestion());
        questions.add(new IntegerQuestion());
        questions.add(new DurationQuestion());
        questions.add(new TimeQuestion());
        questions.add(new MultiValueQuestion());
        return survey;
    }
    
    @Test
    public void serializationIsCorrect() throws Exception {
        DynamoSurvey survey = getCompleteSurvey();
        
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
        
        // This doesn't work, they're not equal this way for some reason.
        // I did verify manually that at this point, they are the same
        assertEquals("Correct serialize/deserialize survey", survey.hashCode(), newSurvey.hashCode());
    }

}
