package org.sagebionetworks.bridge.validators;

import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.dynamodb.DynamoSurveyQuestion;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.surveys.MultiValueConstraints;
import org.sagebionetworks.bridge.models.surveys.StringConstraints;
import org.sagebionetworks.bridge.models.surveys.SurveyRule;
import org.sagebionetworks.bridge.models.surveys.TestSurvey;
import org.sagebionetworks.bridge.models.surveys.UIHint;
import org.sagebionetworks.bridge.models.surveys.SurveyRule.Operator;

public class SurveyValidatorTest {

    SurveyValidator validator;
    
    @Before
    public void before() {
        this.validator = new SurveyValidator();
    }
    
    @Test(expected=InvalidEntityException.class)
    public void wontAllowRulesToBackrefernceQuestions() {
        TestSurvey survey = new TestSurvey(false);
        
        // boolean (high_bp) question comes before integer (bp_x_day) question.
        DynamoSurveyQuestion q = survey.getBooleanQuestion();
        survey.getIntegerQuestion().getConstraints().getRules().add(new SurveyRule(Operator.EQ, 3, q.getIdentifier()));
        
        validator.validate(survey);
    }
    
    @Test(expected=InvalidEntityException.class)
    public void detectsOrphanedGotoReferences() {
        TestSurvey survey = new TestSurvey(false);
        
        survey.getIntegerQuestion().getConstraints().getRules().add(new SurveyRule(Operator.EQ, 3, "notARealIdentifier"));
        
        validator.validate(survey);
    }
    
    @Test(expected=InvalidEntityException.class)
    public void hintDoesNotMatchDataType() {
        TestSurvey survey = new TestSurvey(false);
        
        survey.getBooleanQuestion().setUiHint(UIHint.TEXTFIELD);
        
        validator.validate(survey);
    }
    
    @Test(expected=InvalidEntityException.class)
    public void multiValueComboboxConstraints() {
        TestSurvey survey = new TestSurvey(false);

        DynamoSurveyQuestion question = survey.getMultiValueQuestion();
        question.setUiHint(UIHint.COMBOBOX);
        MultiValueConstraints mvc = (MultiValueConstraints)question.getConstraints();
        mvc.setAllowOther(false);
        
        validator.validate(survey);
    }
    
    @Test(expected=InvalidEntityException.class)
    public void multiValueCheckboxListConstraints() {
        TestSurvey survey = new TestSurvey(false);

        DynamoSurveyQuestion question = survey.getMultiValueQuestion();
        question.setUiHint(UIHint.LIST);
        MultiValueConstraints mvc = (MultiValueConstraints)question.getConstraints();
        mvc.setAllowMultiple(false);
        
        validator.validate(survey);
    }
    
    @Test(expected=InvalidEntityException.class)
    public void multiValueRadiobuttonConstraints() {
        TestSurvey survey = new TestSurvey(false);

        DynamoSurveyQuestion question = survey.getMultiValueQuestion();
        question.setUiHint(UIHint.RADIOBUTTON);
        MultiValueConstraints mvc = (MultiValueConstraints)question.getConstraints();
        mvc.setAllowMultiple(true);
        
        validator.validate(survey);
    }
    
    @Test(expected=InvalidEntityException.class)
    public void stringConstraintPatternCorrect() {
        TestSurvey survey = new TestSurvey(false);
        
        StringConstraints sc = (StringConstraints)survey.getStringQuestion().getConstraints();
        sc.setPattern("%$*(^/");
        
        validator.validate(survey);
    }
}
