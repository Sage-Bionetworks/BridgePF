package org.sagebionetworks.bridge.validators;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.surveys.Constraints;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestion;
import org.sagebionetworks.bridge.models.surveys.UIHint;

public class SurveyValidator implements Validator<Survey> {
    
    @Override
    public void validateNew(Survey survey) throws InvalidEntityException, EntityAlreadyExistsException {
        Messages messages = new Messages();
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
                messages.add("question #%s should not have a GUID", i);
            }
        }
        if (!messages.isEmpty()) {
            throw new EntityAlreadyExistsException(survey, "Survey does not appear to be new: " + messages.join());
        }
        doValidation(survey, true);
    }

    @Override
    public void validate(Survey survey) throws InvalidEntityException {
        doValidation(survey, false);
    }

    private void doValidation(Survey survey, boolean isNew) throws InvalidEntityException {
        Messages messages = new Messages();
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
            doValidateQuestion(question, isNew, i, messages);
        }
        if (!messages.isEmpty()) {
            throw new InvalidEntityException(survey, "Survey is not valid: " + messages.join());
        }
    }
    
    private void doValidateQuestion(SurveyQuestion question, boolean isNew, int pos, Messages messages) {
        if (!isNew && StringUtils.isBlank(question.getGuid())) {
            messages.add("question #%s is missing a GUID", pos);
        }
        if (isNew && StringUtils.isBlank(question.getIdentifier())) {
            messages.add("question #%s is missing an identifier", pos);
        }
        if (question.getConstraints() == null) {
            messages.add("question #%s is missing constraints, including the data type of the question", pos);
        }
        if (question.getUiHint() == null) {
            messages.add("question #%s is missing a UI hint", pos);
        }
        if (StringUtils.isBlank(question.getPrompt())) {
            messages.add("question #%s is missing a prompt/question text for the user", pos);
        }
        // Stop here if basic stuff is missing.
        if (!messages.isEmpty()) {
            return;
        }
        Constraints con = question.getConstraints();
        UIHint hint = question.getUiHint();
        if (!con.getSupportedHints().contains(hint)) {
            messages.add("question #%s has a data type of '%s' that doesn't match the UI hint of %s", pos,
                    con.getDataType(), hint.name().toLowerCase());
        }
    }

}
