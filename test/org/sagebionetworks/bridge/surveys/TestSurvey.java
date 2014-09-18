package org.sagebionetworks.bridge.surveys;

import java.util.List;
import java.util.UUID;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dynamodb.DynamoSurvey;
import org.sagebionetworks.bridge.dynamodb.DynamoSurveyQuestion;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.surveys.BooleanConstraints;
import org.sagebionetworks.bridge.models.surveys.DataType;
import org.sagebionetworks.bridge.models.surveys.DateConstraints;
import org.sagebionetworks.bridge.models.surveys.DateTimeConstraints;
import org.sagebionetworks.bridge.models.surveys.DecimalConstraints;
import org.sagebionetworks.bridge.models.surveys.DurationConstraints;
import org.sagebionetworks.bridge.models.surveys.IntegerConstraints;
import org.sagebionetworks.bridge.models.surveys.MultiValueConstraints;
import org.sagebionetworks.bridge.models.surveys.StringConstraints;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestion;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestionOption;
import org.sagebionetworks.bridge.models.surveys.SurveyRule;
import org.sagebionetworks.bridge.models.surveys.SurveyRule.Operator;
import org.sagebionetworks.bridge.models.surveys.TimeConstraints;
import org.sagebionetworks.bridge.models.surveys.UIHint;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;

/**
 * Surveys are complicated. Here's an example survey with nearly every type of question.
 *
 */
public class TestSurvey extends DynamoSurvey {
    
    private DynamoSurveyQuestion multiValueQuestion = new DynamoSurveyQuestion() {
        {
            MultiValueConstraints mvc = new MultiValueConstraints(DataType.INTEGER);
            List<SurveyQuestionOption> options = Lists.newArrayList(
                new SurveyQuestionOption("Terrible", 1),
                new SurveyQuestionOption("Poor", 2),
                new SurveyQuestionOption("OK", 3),
                new SurveyQuestionOption("Good", 4),
                new SurveyQuestionOption("Great", 5)
            );
            mvc.setEnumeration(options);
            mvc.setAllowOther(false);
            mvc.setAllowMultiple(true);
            setConstraints(mvc);
            setPrompt("How do you feel today?");
            setIdentifier("feeling");
            setUiHint(UIHint.LIST);
            setGuid(UUID.randomUUID().toString());
        }
    };
    
    private DynamoSurveyQuestion stringQuestion = new DynamoSurveyQuestion() {
        {
            StringConstraints c = new StringConstraints();
            c.setMinLength(2);
            c.setMaxLength(255);
            c.setPattern("\\d{3}-\\d{3}-\\{d}4");
            setPrompt("Please enter an emergency phone number (###-###-####)?");
            setIdentifier("name");
            setUiHint(UIHint.TEXTFIELD);
            setConstraints(c);
            setGuid(UUID.randomUUID().toString());
        }
    };
    
    private DynamoSurveyQuestion booleanQuestion = new DynamoSurveyQuestion() {
        {
            BooleanConstraints c = new BooleanConstraints();
            setPrompt("Do you have high blood pressure?");
            setIdentifier("high_bp");
            setUiHint(UIHint.CHECKBOX);
            setConstraints(c);
            setGuid(UUID.randomUUID().toString());
        }
    };
    
    private DynamoSurveyQuestion dateQuestion = new DynamoSurveyQuestion() {
        {
            DateConstraints c = new DateConstraints();
            setPrompt("When did you last have a medical check-up?");
            setIdentifier("last_checkup");
            setUiHint(UIHint.DATEPICKER);
            setConstraints(c);
            setGuid(UUID.randomUUID().toString());
        }
    };
    
    private DynamoSurveyQuestion dateTimeQuestion = new DynamoSurveyQuestion() {
        {
            DateTimeConstraints c = new DateTimeConstraints();
            c.setAllowFuture(true);
            setPrompt("When is your next medical check-up scheduled?");
            setIdentifier("last_reading");
            setUiHint(UIHint.DATETIMEPICKER);
            setConstraints(c);
            setGuid(UUID.randomUUID().toString());
        }
    };
    
    private DynamoSurveyQuestion decimalQuestion = new DynamoSurveyQuestion() {
        {
            DecimalConstraints c = new DecimalConstraints();
            c.setMinValue(0.0d);
            c.setMaxValue(10.0d);
            c.setStep(0.1d);
            setPrompt("What dosage (in grams) do you take of deleuterium each day?");
            setIdentifier("deleuterium_dosage");
            setUiHint(UIHint.SLIDER);
            setConstraints(c);
            setGuid(UUID.randomUUID().toString());
        }
    };
    
    private DynamoSurveyQuestion durationQuestion = new DynamoSurveyQuestion() {
        {
            DurationConstraints c = new DurationConstraints();
            setPrompt("How log does your appointment take, on average?");
            setIdentifier("time_for_appt");
            setUiHint(UIHint.TIMEPICKER);
            setConstraints(c);
            setGuid(UUID.randomUUID().toString());
        }
    };
    
    private DynamoSurveyQuestion integerQuestion = new DynamoSurveyQuestion() {
        {
            IntegerConstraints c = new IntegerConstraints();
            c.setMinValue(0L);
            c.setMaxValue(4L);
            c.getRules().add(new SurveyRule(Operator.LE, 2, "question_after_depth_questions"));
            c.getRules().add(new SurveyRule(Operator.DE, null, "question_after_depth_questions"));
            
            setPrompt("How many times a day do you take your blood pressure?");
            setIdentifier("bp_x_day");
            setUiHint(UIHint.NUMBERFIELD);
            setConstraints(c);
            setGuid(UUID.randomUUID().toString());
        }
    };
    
    private DynamoSurveyQuestion timeQuestion = new DynamoSurveyQuestion() {
        {
            TimeConstraints c = new TimeConstraints();
            setPrompt("What times of the day do you take deleuterium?");
            setIdentifier("deleuterium_x_day");
            setUiHint(UIHint.TIMEPICKER);
            setConstraints(c);
            setGuid(UUID.randomUUID().toString());
        }
    };
    
    public TestSurvey(boolean makeNew) {
        setGuid(UUID.randomUUID().toString());
        setName("General Blood Pressure Survey");
        setIdentifier("bloodpressure");
        setModifiedOn(DateUtils.getCurrentMillisFromEpoch());
        setVersionedOn(DateUtils.getCurrentMillisFromEpoch());
        setVersion(2L);
        setPublished(true);
        setStudyKey(TestConstants.SECOND_STUDY.getKey());
        List<SurveyQuestion> questions = getQuestions();
        questions.add(booleanQuestion);
        questions.add(dateQuestion);
        questions.add(dateTimeQuestion);
        questions.add(decimalQuestion);
        questions.add(integerQuestion);
        questions.add(durationQuestion);
        questions.add(timeQuestion);
        questions.add(multiValueQuestion);
        questions.add(stringQuestion);
        
        if (makeNew) {
            setGuid(null);
            setPublished(false);
            setVersion(null);
            setVersionedOn(0L);
            for (SurveyQuestion question : getQuestions()) {
                question.setGuid(null);
            }
        }
    }

    @DynamoDBIgnore
    public DynamoSurveyQuestion getBooleanQuestion() {
        return booleanQuestion;
    }

    @DynamoDBIgnore
    public DynamoSurveyQuestion getDateQuestion() {
        return dateQuestion;
    }

    @DynamoDBIgnore
    public DynamoSurveyQuestion getDateTimeQuestion() {
        return dateTimeQuestion;
    }

    @DynamoDBIgnore
    public DynamoSurveyQuestion getDecimalQuestion() {
        return decimalQuestion;
    }

    @DynamoDBIgnore
    public DynamoSurveyQuestion getIntegerQuestion() {
        return integerQuestion;
    }

    @DynamoDBIgnore
    public DynamoSurveyQuestion getDurationQuestion() {
        return durationQuestion;
    }

    @DynamoDBIgnore
    public DynamoSurveyQuestion getTimeQuestion() {
        return timeQuestion;
    }

    @DynamoDBIgnore
    public DynamoSurveyQuestion getMultiValueQuestion() {
        return multiValueQuestion;
    }

    @DynamoDBIgnore
    public DynamoSurveyQuestion getStringQuestion() {
        return stringQuestion;
    }
    
    public String toJSON() throws Exception {
        return new ObjectMapper().writeValueAsString(this);
    }
    
}
