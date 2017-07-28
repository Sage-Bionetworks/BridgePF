package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.models.surveys.SurveyElementConstants.SURVEY_QUESTION_TYPE;

import java.util.List;
import java.util.Set;

import com.google.common.collect.Sets;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.models.surveys.Constraints;
import org.sagebionetworks.bridge.models.surveys.MultiValueConstraints;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyElement;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestion;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestionOption;
import org.sagebionetworks.bridge.models.surveys.UIHint;

@Component
public class SurveyPublishValidator implements Validator {
    @Override
    public boolean supports(Class<?> clazz) {
        return Survey.class.isAssignableFrom(clazz);
    }

    @Override 
    public void validate(Object target, Errors errors) {
        Survey survey = (Survey) target;
        // Validate that no identifier has been duplicated.
        Set<String> foundIdentifiers = Sets.newHashSet();
        Set<String> foundGuids = Sets.newHashSet();
        for (int i = 0; i < survey.getElements().size(); i++) {
            SurveyElement element = survey.getElements().get(i);
            errors.pushNestedPath("elements["+i+"]");

            if (SURVEY_QUESTION_TYPE.equals(element.getType())) {
                doValidateQuestion((SurveyQuestion) element, errors);
            }
            if (foundIdentifiers.contains(element.getIdentifier())) {
                errors.rejectValue("identifier", "exists in an earlier survey element");
            }
            if (element.getGuid() != null && foundGuids.contains(element.getGuid())) {
                errors.rejectValue("guid", "exists in an earlier survey element");
            }
            foundIdentifiers.add(element.getIdentifier());
            if (element.getGuid() != null) {
                foundGuids.add(element.getGuid());    
            }
            errors.popNestedPath();
        }
    }

    private void doValidateQuestion(SurveyQuestion question, Errors errors) {
        if (question.getConstraints() != null) {
            errors.pushNestedPath("constraints");
            doValidateConstraints(question, question.getConstraints(), errors);
            errors.popNestedPath();
        }
    }

    private void doValidateConstraints(SurveyQuestion question, Constraints con, Errors errors) {
        if (con.getDataType() == null) {
            errors.rejectValue("dataType", "is required");
            return;
        }
        UIHint hint = question.getUiHint();
        if (hint == null) {
            return; // will have been validated above, skip this
        }
        if (con instanceof MultiValueConstraints) {
            doValidateConstraintsType(errors, hint, (MultiValueConstraints) con);
        }
    }

    private void doValidateConstraintsType(Errors errors, UIHint hint, MultiValueConstraints mcon) {
        // must have an enumeration (list of question options)
        List<SurveyQuestionOption> optionList = mcon.getEnumeration();
        if (optionList == null || optionList.isEmpty()) {
            errors.rejectValue("enumeration", "must have non-null, non-empty choices list");
        }
    }
}
