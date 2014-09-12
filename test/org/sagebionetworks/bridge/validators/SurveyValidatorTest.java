package org.sagebionetworks.bridge.validators;

import java.util.List;
import java.util.UUID;

import org.sagebionetworks.bridge.dynamodb.DynamoSurvey;
import org.sagebionetworks.bridge.dynamodb.DynamoSurveyQuestion;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.surveys.BooleanConstraints;
import org.sagebionetworks.bridge.models.surveys.DateConstraints;
import org.sagebionetworks.bridge.models.surveys.DateTimeConstraints;
import org.sagebionetworks.bridge.models.surveys.DecimalConstraints;
import org.sagebionetworks.bridge.models.surveys.DurationConstraints;
import org.sagebionetworks.bridge.models.surveys.IntegerConstraints;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestion;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestionOption;
import org.sagebionetworks.bridge.models.surveys.TimeConstraints;
import org.sagebionetworks.bridge.models.surveys.UIHint;

import com.google.common.collect.Lists;

public class SurveyValidatorTest {

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
            setUiHint(UIHint.SLIDER);
            setConstraints(c);
            setGuid(UUID.randomUUID().toString());
        }
    }
    
    private class IntegerQuestion extends DynamoSurveyQuestion {
        private IntegerQuestion() {
            IntegerConstraints c = new IntegerConstraints();
            c.setMaxValue(4);
            List<SurveyQuestionOption> options = Lists.newArrayList(
                new SurveyQuestionOption("Never", 0, null),
                new SurveyQuestionOption("1x/day", 1, null),
                new SurveyQuestionOption("2x/day", 2, null),
                new SurveyQuestionOption("3x/day", 3, null),
                new SurveyQuestionOption("4 or more times/day", 4, null)
            );
            c.setEnumeration(options);
            setPrompt("How many times a day do you take your blood pressure?");
            setIdentifier("bp_x_day");
            setUiHint(UIHint.RADIOBUTTON);
            setConstraints(c);
            setGuid(UUID.randomUUID().toString());
        }
    }
    
    private class TimeQuestion extends DynamoSurveyQuestion {
        private TimeQuestion() {
            TimeConstraints c = new TimeConstraints();
            c.setAllowMultiple(true);
            setPrompt("What times of the day do you take deleuterium?");
            setIdentifier("deleuterium_x_day");
            setUiHint(UIHint.TIMEPICKER);
            setConstraints(c);
            setGuid(UUID.randomUUID().toString());
        }
    }
    
    public void test() {
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
        
        // This generates the JSON in order to look at it and assess it from 
        // and end-users perspective.
        System.out.println( JsonUtils.toJSON(survey) );
    }

}
