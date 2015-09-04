package org.sagebionetworks.bridge.validators;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.junit.Test;
import org.sagebionetworks.bridge.dynamodb.DynamoSurveyQuestion;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.surveys.BooleanConstraints;
import org.sagebionetworks.bridge.models.surveys.Constraints;
import org.sagebionetworks.bridge.models.surveys.DataType;
import org.sagebionetworks.bridge.models.surveys.DateConstraints;
import org.sagebionetworks.bridge.models.surveys.DateTimeConstraints;
import org.sagebionetworks.bridge.models.surveys.DecimalConstraints;
import org.sagebionetworks.bridge.models.surveys.DurationConstraints;
import org.sagebionetworks.bridge.models.surveys.IntegerConstraints;
import org.sagebionetworks.bridge.models.surveys.MultiValueConstraints;
import org.sagebionetworks.bridge.models.surveys.StringConstraints;
import org.sagebionetworks.bridge.models.surveys.SurveyAnswer;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestionOption;
import org.sagebionetworks.bridge.models.surveys.TimeConstraints;
import org.sagebionetworks.bridge.models.surveys.Unit;

import com.google.common.collect.Lists;

public class SurveyAnswerValidatorTest {

    private SurveyAnswerValidator validator;
    
    private DynamoSurveyQuestion createQuestion(Constraints constraints) {
        DynamoSurveyQuestion question = new DynamoSurveyQuestion();
        question.setGuid("AAA");
        question.setIdentifier("Test Question");
        question.setPrompt("This is a test question?");
        question.setUiHint(constraints.getSupportedHints().iterator().next());
        question.setConstraints(constraints);
        return question;
    }
    
    private SurveyAnswer createAnswer(String value) {
        return createAnswers(Lists.newArrayList(value));
    }
    
    private SurveyAnswer createAnswers(List<String> values) {
        SurveyAnswer answer = new SurveyAnswer();
        answer.setAnswers(values);
        answer.setClient("mobile");
        answer.setAnsweredOn(DateUtils.getCurrentMillisFromEpoch());
        answer.setQuestionGuid("AAA");
        return answer;
    }
    
    private List<SurveyQuestionOption> getOptions() {
        return Lists.<SurveyQuestionOption>newArrayList(
            new SurveyQuestionOption("label 1", "Detail for label 1", "1", null),
            new SurveyQuestionOption("label 2", "Detail for label 2", "2", null),
            new SurveyQuestionOption("label 3", "Detail for label 3", "3", null)
        );        
    }
    @Test(expected = InvalidEntityException.class)
    public void validateDataType() {
        BooleanConstraints constraints = new BooleanConstraints();
        validator = new SurveyAnswerValidator(createQuestion(constraints));
        
        SurveyAnswer answer = createAnswer("This is not a boolean");
        Validate.entityThrowingException(validator, answer);
    }
    
    @Test
    public void validateBoolean() {
        BooleanConstraints constraints = new BooleanConstraints();
        validator = new SurveyAnswerValidator(createQuestion(constraints));
        
        SurveyAnswer answer = createAnswer("true");
        Validate.entityThrowingException(validator, answer);
    }
    @Test
    public void validateDateAllowFuture() {
        DateConstraints constraints = new DateConstraints();
        constraints.setAllowFuture(true);
        validator = new SurveyAnswerValidator(createQuestion(constraints));
        Long future = DateTime.now().plusMonths(1).getMillis();
        
        Validate.entityThrowingException(validator, createAnswer(DateUtils.convertToISODateTime(future)));
    }
    
    @Test(expected = InvalidEntityException.class)
    public void validateDateDoNotAllowFuture() {
        DateConstraints constraints = new DateConstraints();
        constraints.setAllowFuture(false);
        validator = new SurveyAnswerValidator(createQuestion(constraints));
        Long future = DateTime.now().plusMonths(1).getMillis();
        
        Validate.entityThrowingException(validator, createAnswer(DateUtils.convertToISODateTime(future)));
    }
    @Test
    public void validateDateTimeAllowFuture() {
        DateTimeConstraints constraints = new DateTimeConstraints();
        constraints.setAllowFuture(true);
        validator = new SurveyAnswerValidator(createQuestion(constraints));
        Long future = DateTime.now().plusMonths(1).getMillis();
        
        Validate.entityThrowingException(validator, createAnswer(DateUtils.convertToISODateTime(future)));
    }
    @Test(expected = InvalidEntityException.class)
    public void validateDateTimeDoNotAllowFuture() {
        DateTimeConstraints constraints = new DateTimeConstraints();
        constraints.setAllowFuture(false);
        validator = new SurveyAnswerValidator(createQuestion(constraints));
        Long future = DateTime.now().plusMonths(1).getMillis();
        
        Validate.entityThrowingException(validator, createAnswer(DateUtils.convertToISODateTime(future)));
    }
    @Test(expected = InvalidEntityException.class)
    public void validateDecimalMinValue() {
        DecimalConstraints constraints = new DecimalConstraints();
        constraints.setMinValue(10d);
        validator = new SurveyAnswerValidator(createQuestion(constraints));
        
        Validate.entityThrowingException(validator, createAnswer("5.001"));
    }
    @Test(expected = InvalidEntityException.class)
    public void validateDecimalMaxValue() {
        DecimalConstraints constraints = new DecimalConstraints();
        constraints.setMaxValue(10d); 
        validator = new SurveyAnswerValidator(createQuestion(constraints));
        Validate.entityThrowingException(validator, createAnswer("15.0"));
    }
    @Test(expected = InvalidEntityException.class)
    public void validateInvalidDecimalStep() {
        DecimalConstraints constraints = new DecimalConstraints();
        constraints.setMaxValue(10d);
        constraints.setStep(0.5d);
        validator = new SurveyAnswerValidator(createQuestion(constraints));
        
        Validate.entityThrowingException(validator, createAnswer("15.2"));
    }
    @Test
    public void validateValidDecimalStep() {
        DecimalConstraints constraints = new DecimalConstraints();
        constraints.setMaxValue(10d);
        constraints.setStep(0.5d);
        validator = new SurveyAnswerValidator(createQuestion(constraints));
        
        Validate.entityThrowingException(validator, createAnswer("8.5"));
    }
    @Test(expected = InvalidEntityException.class)
    public void validateDuration() {
        DurationConstraints constraints = new DurationConstraints();
        validator = new SurveyAnswerValidator(createQuestion(constraints));
        
        SurveyAnswer answer = createAnswer("14000");
        Validate.entityThrowingException(validator, answer);
    }
    @Test
    public void validateValidDuration() {
        DurationConstraints constraints = new DurationConstraints();
        constraints.setUnit(Unit.HOURS);
        validator = new SurveyAnswerValidator(createQuestion(constraints));
        
        SurveyAnswer answer = createAnswer("PT2H");
        Validate.entityThrowingException(validator, answer);
    }
    @Test
    public void validateDurationInsideUnitRange() {
        DurationConstraints constraints = new DurationConstraints();
        constraints.setUnit(Unit.HOURS);
        constraints.setMinValue(3d);
        validator = new SurveyAnswerValidator(createQuestion(constraints));
        
        SurveyAnswer answer = createAnswer("PT4H");
        Validate.entityThrowingException(validator, answer);    
    }
    @Test
    public void validateDurationIsOutsideUnitRange() {
        DurationConstraints constraints = new DurationConstraints();
        constraints.setUnit(Unit.HOURS);
        constraints.setMinValue(3d);
        validator = new SurveyAnswerValidator(createQuestion(constraints));

        SurveyAnswer answer = createAnswer("PT2H"); // this should throw an exception
        try {
            Validate.entityThrowingException(validator, answer);    
        } catch(InvalidEntityException e) {
            String message = e.getErrors().get("Test Question.constraints").get(0);
            assertEquals("2 is lower than the minimum value of 3.0 hours", message);
        }
    }
    @Test
    public void validateDurationWithoutTimeUnitsIsInvalid() {
        DurationConstraints constraints = new DurationConstraints();
        constraints.setUnit(Unit.MILLILITERS);
        validator = new SurveyAnswerValidator(createQuestion(constraints));

        // Wrong type of unit (not a time unit)
        SurveyAnswer answer = createAnswer("4");
        try {
            constraints.setMinValue(3d);
            Validate.entityThrowingException(validator, answer);    
        } catch(InvalidEntityException e) {
            assertTrue(e.getMessage().contains("milliliters is not a time unit"));
        }
        
        // No unit at all.
        try {
            constraints.setUnit(null);
            Validate.entityThrowingException(validator, answer);    
        } catch(InvalidEntityException e) {
            assertTrue(e.getMessage().contains("unit is required"));
        }
    }
    @Test(expected = InvalidEntityException.class)
    public void validateDurationInDifferentUnitsIsInvalid() {
        DurationConstraints constraints = new DurationConstraints();
        constraints.setUnit(Unit.HOURS);
        constraints.setMinValue(3d);
        validator = new SurveyAnswerValidator(createQuestion(constraints));
        
        SurveyAnswer answer = createAnswer("PT190M");
        Validate.entityThrowingException(validator, answer);
    }
    @Test(expected = InvalidEntityException.class)
    public void validateDurationOutOfRangeInDifferentUnits() {
        DurationConstraints constraints = new DurationConstraints();
        constraints.setUnit(Unit.HOURS);
        constraints.setMinValue(3d);
        validator = new SurveyAnswerValidator(createQuestion(constraints));
        
        SurveyAnswer answer = createAnswer("PT20M");
        Validate.entityThrowingException(validator, answer);
    }
    @Test
    public void validateAlxDoesntBelieveDurationWillWorkForYears() {
        // and it didn't... this test changed the conversion logic for cases where the 
        // survey asks for something like months, and the answer is returned in a different
        // time unit, like months: P36M instead of P3Y for example.
        DurationConstraints constraints = new DurationConstraints();
        constraints.setUnit(Unit.YEARS);
        constraints.setMinValue(3d);
        validator = new SurveyAnswerValidator(createQuestion(constraints));
        
        SurveyAnswer answer = createAnswer("P3Y");
        Validate.entityThrowingException(validator, answer);
    }
    @Test(expected = InvalidEntityException.class)
    public void validateIntegerMinValue() {
        IntegerConstraints constraints = new IntegerConstraints();
        constraints.setMinValue(15d);
        validator = new SurveyAnswerValidator(createQuestion(constraints));
        
        SurveyAnswer answer = createAnswer("10");
        Validate.entityThrowingException(validator, answer);
    }
    @Test(expected = InvalidEntityException.class)
    public void validateIntegerMaxValue() {
        IntegerConstraints constraints = new IntegerConstraints();
        constraints.setMaxValue(10d);
        validator = new SurveyAnswerValidator(createQuestion(constraints));

        SurveyAnswer answer = createAnswer("12");
        Validate.entityThrowingException(validator, answer);
    }
    @Test(expected = InvalidEntityException.class)
    public void validateIntegerInvalidStep() {
        IntegerConstraints constraints = new IntegerConstraints();
        constraints.setMaxValue(10d);
        constraints.setStep(5d);
        validator = new SurveyAnswerValidator(createQuestion(constraints));

        SurveyAnswer answer = createAnswer("12");
        Validate.entityThrowingException(validator, answer);
    }
    @Test
    public void validateIntegerValidStep() {
        IntegerConstraints constraints = new IntegerConstraints();
        constraints.setMaxValue(20d);
        constraints.setStep(5d);
        validator = new SurveyAnswerValidator(createQuestion(constraints));

        SurveyAnswer answer = createAnswer("15");
        Validate.entityThrowingException(validator, answer);
    }
    @Test
    public void validateIntegerNoConstraints() {
        IntegerConstraints constraints = new IntegerConstraints();
        validator = new SurveyAnswerValidator(createQuestion(constraints));
        
        SurveyAnswer answer = createAnswer("12");
        Validate.entityThrowingException(validator, answer);
    }
    @Test
    public void validateIntegerWithNonIntegerValueIsInvalid() {
        IntegerConstraints constraints = new IntegerConstraints();
        validator = new SurveyAnswerValidator(createQuestion(constraints));
        
        try {
            SurveyAnswer answer = createAnswer("asdf");
            Validate.entityThrowingException(validator, answer);
        } catch(InvalidEntityException e) {
            assertTrue(e.getMessage().contains("asdf is not a valid integer"));
        }
    }
    @Test
    public void validateDecimalWithNonDecimalValueIsInvalid() {
        DecimalConstraints constraints = new DecimalConstraints();
        validator = new SurveyAnswerValidator(createQuestion(constraints));
        
        try {
            SurveyAnswer answer = createAnswer("asdf");
            Validate.entityThrowingException(validator, answer);
        } catch(InvalidEntityException e) {
            assertTrue(e.getMessage().contains("asdf is not a valid decimal"));
        }
        
        // But this is okay, it's treated as 3.0
        SurveyAnswer answer = createAnswer("3");
        Validate.entityThrowingException(validator, answer);
        
    }
    @Test(expected=InvalidEntityException.class)
    public void validateMultiValueWithNoOther() {
        MultiValueConstraints constraints = new MultiValueConstraints();
        constraints.setDataType(DataType.INTEGER);
        constraints.setEnumeration( getOptions() );
        validator = new SurveyAnswerValidator(createQuestion(constraints));
        
        SurveyAnswer answer = createAnswer("6");
        Validate.entityThrowingException(validator, answer);
    }
    @Test
    public void validateMultiValueWithOther() {
        MultiValueConstraints constraints = new MultiValueConstraints();
        constraints.setDataType(DataType.INTEGER);
        constraints.setAllowOther(true);
        constraints.setEnumeration( getOptions() );
        validator = new SurveyAnswerValidator(createQuestion(constraints));
        
        // The only validation that can happen is type, so any value is okay here
        SurveyAnswer answer = createAnswer("6");
        Validate.entityThrowingException(validator, answer);
    }
    @Test
    public void validateMultiValueWithMultipleValues() {
        MultiValueConstraints constraints = new MultiValueConstraints();
        constraints.setDataType(DataType.INTEGER);
        constraints.setAllowMultiple(true);
        constraints.setEnumeration( getOptions() );
        validator = new SurveyAnswerValidator(createQuestion(constraints));
        
        SurveyAnswer answer = createAnswers(Lists.<String>newArrayList("1", "2"));
        Validate.entityThrowingException(validator, answer);
    }
    @Test
    public void validateMultiValueOtherAllowMultiple() {
        MultiValueConstraints constraints = new MultiValueConstraints();
        
        constraints.setDataType(DataType.INTEGER);
        constraints.setAllowMultiple(true);
        constraints.setAllowOther(true);
        constraints.setEnumeration( getOptions() );
        validator = new SurveyAnswerValidator(createQuestion(constraints));
        
        // The only validation that can happen is type, so any value is okay here
        SurveyAnswer answer = createAnswers(Lists.<String>newArrayList("1", "2", "10"));
        Validate.entityThrowingException(validator, answer);
    }
    @Test(expected = InvalidEntityException.class)
    public void validateStringTooShort() {
        StringConstraints constraints = new StringConstraints();
        constraints.setMinLength(5);
        validator = new SurveyAnswerValidator(createQuestion(constraints));
        
        SurveyAnswer answer = createAnswer("axe");
        Validate.entityThrowingException(validator, answer);
    }
    @Test(expected = InvalidEntityException.class)
    public void validateStringTooLong() {
        StringConstraints constraints = new StringConstraints();
        constraints.setMaxLength(5);
        validator = new SurveyAnswerValidator(createQuestion(constraints));
        
        SurveyAnswer answer = createAnswer("belgium");
        Validate.entityThrowingException(validator, answer);
    }
    @Test(expected = InvalidEntityException.class)
    public void validateStringFailsPatternMatch() {
        StringConstraints constraints = new StringConstraints();
        constraints.setPattern("\\d{3}-\\d{3}-\\d{4}");
        validator = new SurveyAnswerValidator(createQuestion(constraints));
        
        SurveyAnswer answer = createAnswer("123-a67-9870");
        Validate.entityThrowingException(validator, answer);
    }
    @Test(expected = InvalidEntityException.class)
    public void validateInvalidTime() {
        TimeConstraints constraints = new TimeConstraints();
        validator = new SurveyAnswerValidator(createQuestion(constraints));
        
        SurveyAnswer answer = createAnswer("asdf");
        Validate.entityThrowingException(validator, answer);
    }
    @Test
    public void validateValidTimeWithSeconds() {
        TimeConstraints constraints = new TimeConstraints();
        validator = new SurveyAnswerValidator(createQuestion(constraints));
        
        SurveyAnswer answer = createAnswer("2:00:03"); // two hours, 3 seconds, it can happen
        Validate.entityThrowingException(validator, answer);
    }
    @Test
    public void validateTimeNoSeconds() {
        TimeConstraints constraints = new TimeConstraints();
        validator = new SurveyAnswerValidator(createQuestion(constraints));
        
        SurveyAnswer answer = createAnswer("13:00"); // no seconds, that's okay
        Validate.entityThrowingException(validator, answer);
    }
    @Test(expected = InvalidEntityException.class)
    public void validateTimeWithTimeZone() {
        TimeConstraints constraints = new TimeConstraints();
        validator = new SurveyAnswerValidator(createQuestion(constraints));
        
        SurveyAnswer answer = createAnswer("13:47:30Z"); // time zone, verboten
        Validate.entityThrowingException(validator, answer);
    }
    @Test(expected = InvalidEntityException.class)
    public void validateTimeBasedEarliestConstraints() {
        DateConstraints constraints = new DateConstraints();
        constraints.setEarliestValue(LocalDate.parse("2010-10-10"));

        validator = new SurveyAnswerValidator(createQuestion(constraints));
        SurveyAnswer answer = createAnswer("2008-08-08"); // Earlier than earliest date
        Validate.entityThrowingException(validator, answer);
    }
    @Test(expected = InvalidEntityException.class)
    public void validateTimeBasedLatestConstraints() {
        DateTimeConstraints constraints = new DateTimeConstraints();
        constraints.setAllowFuture(true);
        constraints.setLatestValue(DateTime.parse("2010-10-10T00:00:00.000Z"));

        validator = new SurveyAnswerValidator(createQuestion(constraints));
        SurveyAnswer answer = createAnswer(DateUtils.getCurrentISODateTime()); // later than latest date
        Validate.entityThrowingException(validator, answer);
    }
}
