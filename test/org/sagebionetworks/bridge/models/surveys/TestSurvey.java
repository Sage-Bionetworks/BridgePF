package org.sagebionetworks.bridge.models.surveys;

import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;

import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoSurvey;
import org.sagebionetworks.bridge.dynamodb.DynamoSurveyQuestion;
import org.sagebionetworks.bridge.time.DateUtils;
import org.sagebionetworks.bridge.models.surveys.SurveyRule.Operator;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Lists;

/**
 * Surveys are complicated. Here's an example survey with nearly every type of question.
 *
 */
public class TestSurvey extends DynamoSurvey {

    public static SurveyQuestion selectBy(Survey survey, DataType type) {
        for (SurveyQuestion question : survey.getUnmodifiableQuestionList()) {
            if (question.getConstraints().getDataType() == type) {
                return question;
            }
        }
        return null;
    }
    
    private DynamoSurveyQuestion multiValueQuestion = new DynamoSurveyQuestion() {
        {
            Image terrible = new Image("http://terrible.svg", 600, 300);
            Image poor = new Image("http://poor.svg", 600, 300);
            Image ok = new Image("http://ok.svg", 600, 300);
            Image good = new Image("http://good.svg", 600, 300);
            Image great = new Image("http://great.svg", 600, 300);
            MultiValueConstraints mvc = new MultiValueConstraints(DataType.INTEGER);
            List<SurveyQuestionOption> options = Lists.newArrayList(
                new SurveyQuestionOption("Terrible", null, "1", terrible),
                new SurveyQuestionOption("Poor", null, "2", poor),
                new SurveyQuestionOption("OK", null, "3", ok),
                new SurveyQuestionOption("Good", null, "4", good),
                new SurveyQuestionOption("Great", null, "5", great)
            );
            mvc.setEnumeration(options);
            mvc.setAllowOther(false);
            mvc.setAllowMultiple(true);
            setConstraints(mvc);
            setPrompt("How do you feel today?");
            setPromptDetail("Is that how you really feel?");
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
            c.setPattern("\\d{3}-\\d{3}-\\d{4}");
            c.setPatternErrorMessage("Provide phone number in format ###-###-####");
            setPrompt("Please enter an emergency phone number");
            setPromptDetail("This should be for someone besides yourself.");
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
            setPromptDetail("Be honest: do you have high blood pressue?");
            setUiHint(UIHint.CHECKBOX);
            setConstraints(c);
            setGuid(UUID.randomUUID().toString());
        }
    };
    
    private DynamoSurveyQuestion dateQuestion = new DynamoSurveyQuestion() {
        {
            DateConstraints c = new DateConstraints();
            c.setEarliestValue(LocalDate.parse("2010-10-10"));
            c.setLatestValue(new LocalDate(DateUtils.getCurrentMillisFromEpoch()));
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
            c.setEarliestValue(new DateTime(DateUtils.convertToMillisFromEpoch("2010-10-10T00:00:00.000Z")));
            c.setLatestValue(new DateTime());
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
            setUiHint(UIHint.NUMBERFIELD);
            setConstraints(c);
            setGuid(UUID.randomUUID().toString());
        }
    };
    
    private DynamoSurveyQuestion integerQuestion = new DynamoSurveyQuestion() {
        {
            IntegerConstraints c = new IntegerConstraints();
            c.setMinValue(0d);
            c.setMaxValue(4d);
            c.getRules().add(new SurveyRule.Builder().withOperator(Operator.LE).withValue(2).withSkipToTarget("name").build());
            c.getRules().add(new SurveyRule.Builder().withOperator(Operator.DE).withSkipToTarget("name").build());
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
    
    private DynamoSurveyQuestion yearMonthQuestion = new DynamoSurveyQuestion() {
        {
            YearMonthConstraints c = new YearMonthConstraints();
            c.setAllowFuture(true);
            setPrompt("What year and month did you get a diagnosis?");
            setIdentifier("diagnosis-year-month");
            setUiHint(UIHint.YEARMONTH);
            setConstraints(c);
            setGuid(UUID.randomUUID().toString());
        }
    };
    
    private DynamoSurveyQuestion postalCodeQuestion = new DynamoSurveyQuestion() {
        {
            PostalCodeConstraints pcc = new PostalCodeConstraints();
            pcc.setCountryCode(CountryCode.US);
            setPrompt("What are the first 3 digits of your zip code?");
            setIdentifier("postal-code");
            setUiHint(UIHint.POSTALCODE);
            setConstraints(pcc);
            setGuid(UUID.randomUUID().toString());
        }
    };
    
    public TestSurvey(Class<?> cls, boolean makeNew) {
        setGuid(UUID.randomUUID().toString());
        setName("General Blood Pressure Survey");
        setIdentifier(TestUtils.randomName(cls));
        setModifiedOn(DateUtils.getCurrentMillisFromEpoch());
        setCreatedOn(DateUtils.getCurrentMillisFromEpoch());
        setVersion(2L);
        setPublished(true);
        setSchemaRevision(42);
        setStudyIdentifier(TEST_STUDY_IDENTIFIER);
        List<SurveyElement> elements = getElements();
        elements.add(booleanQuestion);
        elements.add(dateQuestion);
        elements.add(dateTimeQuestion);
        elements.add(decimalQuestion);
        elements.add(integerQuestion);
        elements.add(durationQuestion);
        elements.add(timeQuestion);
        elements.add(multiValueQuestion);
        elements.add(stringQuestion);
        elements.add(yearMonthQuestion);
        elements.add(postalCodeQuestion);
        
        if (makeNew) {
            setGuid(null);
            setPublished(false);
            setVersion(null);
            setCreatedOn(0L);
            for (SurveyElement element : getElements()) {
                element.setGuid(null);
            }
        }
    }
    
    @DynamoDBIgnore
    @JsonIgnore
    public SurveyQuestion getMultiValueQuestion() {
        return multiValueQuestion;
    }
    
    @DynamoDBIgnore
    @JsonIgnore
    public SurveyQuestion getStringQuestion() {
        return stringQuestion;
    }
    
    @DynamoDBIgnore
    @JsonIgnore
    public SurveyQuestion getBooleanQuestion() {
        return booleanQuestion;
    }
    
    @DynamoDBIgnore
    @JsonIgnore
    public SurveyQuestion getDateQuestion() {
        return dateQuestion;
    }
    
    @DynamoDBIgnore
    @JsonIgnore
    public SurveyQuestion getDateTimeQuestion() {
        return dateTimeQuestion;
    }
    
    @DynamoDBIgnore
    @JsonIgnore
    public SurveyQuestion getDecimalQuestion() {
        return decimalQuestion;
    }
    
    @DynamoDBIgnore
    @JsonIgnore
    public SurveyQuestion getIntegerQuestion() {
        return integerQuestion;
    }
    
    @DynamoDBIgnore
    @JsonIgnore
    public SurveyQuestion getDurationQuestion() {
        return durationQuestion;
    }

    @DynamoDBIgnore
    @JsonIgnore
    public SurveyQuestion getTimeQuestion() {
        return timeQuestion;
    }
    
    @DynamoDBIgnore
    @JsonIgnore
    public SurveyQuestion getYearMonthQuestion() {
        return yearMonthQuestion;
    }
}
