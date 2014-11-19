package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.dao.StudyConsentDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.studies.StudyConsent;
import org.sagebionetworks.bridge.models.studies.StudyConsentForm;
import org.sagebionetworks.bridge.validators.Validate;
import org.springframework.validation.Validator;

public class StudyConsentServiceImpl implements StudyConsentService {

    private Validator validator;
    private StudyConsentDao studyConsentDao;

    public void setValidator(Validator validator) {
        this.validator = validator;
    }
    
    public void setStudyConsentDao(StudyConsentDao studyConsentDao) {
        this.studyConsentDao = studyConsentDao;
    }
    
    @Override
    public StudyConsent addConsent(String studyKey, StudyConsentForm form) {
        checkArgument(StringUtils.isNotBlank(studyKey), "Study key is blank or null");
        checkNotNull(form, "Study consent is null");
        
        Validate.entityThrowingException(validator, form);
        return studyConsentDao.addConsent(studyKey, form.getPath(), form.getMinAge());
    }

    @Override
    public StudyConsent getActiveConsent(String studyKey) {
        checkArgument(StringUtils.isNotBlank(studyKey), "Study key is blank or null");
        
        StudyConsent consent = studyConsentDao.getConsent(studyKey);
        if (consent == null) {
            throw new EntityNotFoundException(StudyConsent.class);
        }
        return consent;
    }

    @Override
    public List<StudyConsent> getAllConsents(String studyKey) {
        checkArgument(StringUtils.isNotBlank(studyKey), "Study key is blank or null");
        
        List<StudyConsent> consents = studyConsentDao.getConsents(studyKey);
        if (consents == null || consents.isEmpty()) {
            throw new BadRequestException("There are no consent records.");
        }
        return consents;
    }

    @Override
    public StudyConsent getConsent(String studyKey, long timestamp) {
        checkArgument(StringUtils.isNotBlank(studyKey), "Study key is blank or null");
        checkArgument(timestamp > 0, "Timestamp is 0");
        
        StudyConsent consent = studyConsentDao.getConsent(studyKey, timestamp);
        if (consent == null) {
            throw new EntityNotFoundException(StudyConsent.class);
        }
        return consent;
    }

    @Override
    public StudyConsent activateConsent(String studyKey, long timestamp) {
        checkArgument(StringUtils.isNotBlank(studyKey), "Study key is blank or null");
        checkArgument(timestamp > 0, "Timestamp is 0");
        
        StudyConsent consent = studyConsentDao.getConsent(studyKey, timestamp);
        if (consent == null) {
            throw new EntityNotFoundException(StudyConsent.class);
        }
        return studyConsentDao.setActive(consent, true);
    }

    @Override
    public void deleteConsent(String studyKey, long timestamp) {
        checkArgument(StringUtils.isNotBlank(studyKey), "Study key is blank or null");
        checkArgument(timestamp > 0, "Timestamp is 0");
        
        if (studyConsentDao.getConsent(studyKey, timestamp).getActive()) {
            throw new BadRequestException("Cannot delete active consent document.");
        }
        studyConsentDao.deleteConsent(studyKey, timestamp);
    }
}
