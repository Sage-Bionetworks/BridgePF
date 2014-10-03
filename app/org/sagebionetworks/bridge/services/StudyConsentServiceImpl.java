package org.sagebionetworks.bridge.services;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.dao.StudyConsentDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.StudyConsent;
import org.sagebionetworks.bridge.models.StudyConsentForm;

public class StudyConsentServiceImpl implements StudyConsentService {

    private StudyConsentDao studyConsentDao;

    public void setStudyConsentDao(StudyConsentDao studyConsentDao) {
        this.studyConsentDao = studyConsentDao;
    }

    @Override
    public StudyConsent addConsent(String studyKey, StudyConsentForm form) {
        validate(form);
        return studyConsentDao.addConsent(studyKey, form.getPath(), form.getMinAge());
    }

    private void validate(StudyConsentForm studyConsent) {
        if (StringUtils.isBlank(studyConsent.getPath())) {
            throw new InvalidEntityException(studyConsent, "Path field is null or blank.");
        } else if (studyConsent.getMinAge() <= 0) {
            throw new InvalidEntityException(studyConsent, "Minimum age must be a positive integer.");
        }
    }
    
    @Override
    public StudyConsent getActiveConsent(String studyKey) {
        StudyConsent consent = studyConsentDao.getConsent(studyKey);
        if (consent == null) {
            throw new EntityNotFoundException(StudyConsent.class);
        }
        return consent;
    }

    @Override
    public List<StudyConsent> getAllConsents(String studyKey) {
        List<StudyConsent> consents = studyConsentDao.getConsents(studyKey);
        if (consents == null || consents.isEmpty()) {
            throw new BadRequestException("There are no consent records.");
        }
        return consents;
    }

    @Override
    public StudyConsent getConsent(String studyKey, long timestamp) {
        StudyConsent consent = studyConsentDao.getConsent(studyKey, timestamp);
        if (consent == null) {
            throw new EntityNotFoundException(StudyConsent.class);
        }
        return consent;
    }

    @Override
    public StudyConsent activateConsent(String studyKey, long timestamp) {
        StudyConsent consent = studyConsentDao.getConsent(studyKey, timestamp);
        if (consent == null) {
            throw new EntityNotFoundException(StudyConsent.class);
        }
        return studyConsentDao.setActive(consent, true);
    }

    @Override
    public void deleteConsent(String studyKey, long timestamp) {
        if (studyConsentDao.getConsent(studyKey, timestamp).getActive()) {
            throw new BadRequestException("Cannot delete active consent document.");
        }
        studyConsentDao.deleteConsent(studyKey, timestamp);
    }
}
