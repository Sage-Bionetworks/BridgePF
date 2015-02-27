package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.BridgeUtils.checkNewEntity;

import java.util.List;

import javax.annotation.Resource;

import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dao.DirectoryDao;
import org.sagebionetworks.bridge.dao.DistributedLockDao;
import org.sagebionetworks.bridge.dao.StudyDao;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.validators.StudyValidator;
import org.sagebionetworks.bridge.validators.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class StudyServiceImpl implements StudyService {

    public Study load(String identifier) throws EntityNotFoundException {
        return studyDao.getStudy(identifier);
    }

    private UploadCertificateService uploadCertService;
    private StudyDao studyDao;
    private DirectoryDao directoryDao;
    private DistributedLockDao lockDao;
    private BridgeConfig config;
    private StudyValidator validator;
    private CacheProvider cacheProvider;
    
    @Resource(name="uploadCertificateService")
    public void setUploadCertificateService(UploadCertificateService uploadCertService) {
        this.uploadCertService = uploadCertService;
    }
    @Autowired
    public void setDistributedLockDao(DistributedLockDao lockDao) {
        this.lockDao = lockDao;
    }
    @Autowired
    public void setValidator(StudyValidator validator) {
        this.validator = validator;
    }
    @Autowired
    public void setStudyDao(StudyDao studyDao) {
        this.studyDao = studyDao;
    }
    @Autowired
    public void setDirectoryDao(DirectoryDao directoryDao) {
        this.directoryDao = directoryDao;
    }
    @Autowired
    public void setBridgeConfig(BridgeConfig bridgeConfig) {
        this.config = bridgeConfig;
    }
    @Autowired
    public void setCacheProvider(CacheProvider cacheProvider) {
        this.cacheProvider = cacheProvider;
    }
    
    @Override
    public Study getStudy(String identifier) {
        checkArgument(isNotBlank(identifier), Validate.CANNOT_BE_BLANK, "identifier");
        
        Study study = cacheProvider.getStudy(identifier);
        if (study == null) {
            study = studyDao.getStudy(identifier);
            cacheProvider.setStudy(study);
        }
        return study;
    }
    @Override
    public Study getStudy(StudyIdentifier studyId) {
        checkNotNull(studyId, Validate.CANNOT_BE_NULL, "studyIdentifier");
        
        return getStudy(studyId.getIdentifier());
    }
    @Override
    public List<Study> getStudies() {
        return studyDao.getStudies();
    }
    @Override
    public Study createStudy(Study study) {
        checkNotNull(study, Validate.CANNOT_BE_NULL, "study");
        checkNewEntity(study, study.getVersion(), "Study has a version value; it may already exist");

        Validate.entityThrowingException(validator, study);

        String id = study.getIdentifier();
        String lockId = null;
        try {
            lockId = lockDao.acquireLock(Study.class, id);

            if (studyDao.doesIdentifierExist(study.getIdentifier())) {
                throw new EntityAlreadyExistsException(study);
            }
            study.setResearcherRole(study.getIdentifier() + "_researcher");

            String directory = directoryDao.createDirectoryForStudy(study.getIdentifier());
            study.setStormpathHref(directory);
            uploadCertService.createCmsKeyPair(study.getIdentifier());
            // This may be removable at some point, as we're deprecating this means of communicating the study
            study.setHostname(study.getIdentifier() + config.getStudyHostnamePostfix());
            study = studyDao.createStudy(study);
            cacheProvider.setStudy(study);

        } finally {
            lockDao.releaseLock(Study.class, id, lockId);
        }
        return study;
    }
    @Override
    public Study updateStudy(Study study) {
        checkNotNull(study, Validate.CANNOT_BE_NULL, "study");
        Validate.entityThrowingException(validator, study);

        // These cannot be set through the API and will be null here, so they are set on update
        Study originalStudy = studyDao.getStudy(study.getIdentifier());
        study.setHostname(originalStudy.getHostname());
        study.setStormpathHref(originalStudy.getStormpathHref());
        study.setResearcherRole(originalStudy.getResearcherRole());

        Study updatedStudy = studyDao.updateStudy(study);
        cacheProvider.setStudy(updatedStudy);
        
        return updatedStudy;
    }
    @Override
    public void deleteStudy(String identifier) {
        checkArgument(isNotBlank(identifier), Validate.CANNOT_BE_BLANK, "identifier");

        String lockId = null;
        try {
            lockId = lockDao.acquireLock(Study.class, identifier);
            directoryDao.deleteDirectoryForStudy(identifier);
            studyDao.deleteStudy(identifier);
            cacheProvider.removeStudy(identifier);
        } finally {
            lockDao.releaseLock(Study.class, identifier, lockId);
        }
    }
}
