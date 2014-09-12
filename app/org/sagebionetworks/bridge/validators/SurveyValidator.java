package org.sagebionetworks.bridge.validators;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.surveys.Constraints;
import org.sagebionetworks.bridge.models.surveys.EnumerableConstraints;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestion;
import org.sagebionetworks.bridge.models.surveys.UIHint;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

public class SurveyValidator implements Validator<Survey> {

    @Override
    public void validateNew(Survey survey) throws InvalidEntityException, EntityAlreadyExistsException {
        List<String> messages = Lists.newArrayList();
        if (StringUtils.isNotBlank(survey.getGuid())) {
            messages.add("should not have a GUID");
        } else if (survey.isPublished()) {
            messages.add("should not be marked as published");
        } else if (survey.getVersion() != null) {
            messages.add("should not have a lock version number");
        } else if (survey.getVersionedOn() != 0L) {
            messages.add("should not have a versionedOn date");
        }
        for (int i=0; i < survey.getQuestions().size(); i++) {
            SurveyQuestion question = survey.getQuestions().get(i);
            if (StringUtils.isNotBlank(question.getGuid())) {
                messages.add("question #"+i+" should not have a GUID");
            }
        }
        if (!messages.isEmpty()) {
            throw new EntityAlreadyExistsException(survey, "Survey does not appear to be new: " + Joiner.on("; ").join(messages));
        }
        doValidation(survey, true);
    }

    @Override
    public void validateExisting(Survey survey) throws InvalidEntityException {
        doValidation(survey, false);
    }

    private void doValidation(Survey survey, boolean isNew) throws InvalidEntityException {
        List<String> messages = Lists.newArrayList();
        if (StringUtils.isBlank(survey.getIdentifier())) {
            messages.add("missing an identifier");
        } else if (StringUtils.isBlank(survey.getStudyKey())) {
            messages.add("missing a study key");
        }
        if (!isNew && StringUtils.isBlank(survey.getGuid())) {
            messages.add("missing a GUID");
        }
        for (int i=0; i < survey.getQuestions().size(); i++) {
            SurveyQuestion question = survey.getQuestions().get(i);
            doValidateQuestion(question, isNew, i, question.getUiHint(), messages);
        }
        if (!messages.isEmpty()) {
            throw new InvalidEntityException(survey, "Survey is not valid: " + Joiner.on("; ").join(messages) + " - " + survey.toString());
        }
    }
    
    private static final List<UIHint> HINTS_REQUIRING_ENUMERATION = Lists.newArrayList(UIHint.CHECKBOX,
            UIHint.COMBOBOX, UIHint.LIST, UIHint.RADIOBUTTON, UIHint.SELECT);
    
    /**
     * @param question
     * @param hint
     * @param messages
     */
    private void doValidateQuestion(SurveyQuestion question, boolean isNew, int pos, UIHint hint, List<String> messages) {
        if (!isNew && StringUtils.isBlank(question.getGuid())) {
            messages.add("question #"+pos+" is missing a GUID");
        }
        if (isNew && StringUtils.isBlank(question.getIdentifier())) {
            messages.add("question #"+pos+" is missing an identifier");
        }
        if (question.getConstraints() == null) {
            messages.add("question #"+pos+" is missing constraints, including the data type of the question");
        }
        if (question.getUiHint() == null) {
            messages.add("question #"+pos+" is missing a UI hint");
        }
        if (StringUtils.isBlank(question.getPrompt())) {
            messages.add("question #"+pos+" is missing a prompt/question text for the user");
        }
        // Stop here if basic stuff is missing.
        if (!messages.isEmpty()) {
            return;
        }
        // TODO: Validate that a SurveyQuestionOption doesn't ask to skip to a question that is prior to the current
        // question, in the list. That would create a loop.
        
        // TODO: Validate that the UI hint is appropriate for the data type constraint.
        
        if (HINTS_REQUIRING_ENUMERATION.contains(question.getUiHint())) {
            Constraints con = question.getConstraints();
            boolean hasEnumeration = (con instanceof EnumerableConstraints);
            if (!hasEnumeration || 
               ((EnumerableConstraints)con).getEnumeration() == null || 
               ((EnumerableConstraints)con).getEnumeration().isEmpty()) {
                // the ui type requires an enumeration, which is missing
                messages.add("question #"+pos+" suggests a UI that requires an enumeration of all possible values, but that is not included with the question");
            }
        } else if (question.getUiHint() == UIHint.SLIDER) {
            // And finally, you cannot specify a non-numeric slider without also specifying 
            // some enumerated values. If this is even legal?
            Constraints con = question.getConstraints();
            boolean hasEnumeration = (con instanceof EnumerableConstraints);
            if (!hasEnumeration && !con.getDataType().equals("integer") && !con.getDataType().equals("decimal")) {
                messages.add("question #"+pos+" suggests a slider UI but it is not a number; this should include the enumerated values for the slider");
            }
        }
    }
}
