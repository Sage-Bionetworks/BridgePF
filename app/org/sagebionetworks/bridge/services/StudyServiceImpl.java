package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.BridgeUtils.checkNewEntity;

import java.util.List;
import java.util.Map;

import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dao.DirectoryDao;
import org.sagebionetworks.bridge.dao.DistributedLockDao;
import org.sagebionetworks.bridge.dao.DnsDao;
import org.sagebionetworks.bridge.dao.HerokuApi;
import org.sagebionetworks.bridge.dao.StudyDao;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.Tracker;
import org.sagebionetworks.bridge.validators.StudyValidator;
import org.sagebionetworks.bridge.validators.Validate;

import com.google.common.collect.Maps;

public class StudyServiceImpl implements StudyService {

    public Study load(String identifier) throws EntityNotFoundException {
        return studyDao.getStudy(identifier);
    }

    private UploadCertificateService uploadCertService;
    private StudyDao studyDao;
    private DirectoryDao directoryDao;
    private HerokuApi herokuApi;
    private DnsDao dnsDao;
    private DistributedLockDao lockDao;
    private BridgeConfig config;
    private StudyValidator validator;
    private CacheProvider cacheProvider;
    private Map<String,Tracker> trackersByIdentifier = Maps.newHashMap();
    
    public void setUploadCertificateService(UploadCertificateService uploadCertService) {
        this.uploadCertService = uploadCertService;
    }
    
    public void setDistributedLockDao(DistributedLockDao lockDao) {
        this.lockDao = lockDao;
    }

    public void setValidator(StudyValidator validator) {
        this.validator = validator;
    }

    public void setHerokuApi(HerokuApi herokuApi) {
        this.herokuApi = herokuApi;
    }

    public void setDnsDao(DnsDao dnsDao) {
        this.dnsDao = dnsDao;
    }

    public void setStudyDao(StudyDao studyDao) {
        this.studyDao = studyDao;
    }

    public void setDirectoryDao(DirectoryDao directoryDao) {
        this.directoryDao = directoryDao;
    }

    public void setBridgeConfig(BridgeConfig bridgeConfig) {
        this.config = bridgeConfig;
    }
    
    public void setCacheProvider(CacheProvider cacheProvider) {
        this.cacheProvider = cacheProvider;
    }

    public void setTrackers(List<Tracker> trackers) {
        if (trackers != null) {
            for (Tracker tracker : trackers) {
                trackersByIdentifier.put(tracker.getIdentifier(), tracker);
            }
        }
    }

    @Override
    public Tracker getTrackerByIdentifier(String trackerId) {
        return trackersByIdentifier.get(trackerId);
    }
    @Override
    public Study getStudyByIdentifier(String identifier) {
        checkArgument(isNotBlank(identifier), Validate.CANNOT_BE_BLANK, "identifier");
        
        Study study = cacheProvider.getStudy(identifier);
        if (study == null) {
            study = studyDao.getStudy(identifier);
            cacheProvider.setStudy(study);
        }
        return study;
    }
    @Override
    public Study getStudyByHostname(String hostname) {
        checkArgument(isNotBlank(hostname), Validate.CANNOT_BE_BLANK, "hostname");

        String postfix = config.getStudyHostnamePostfix();
        String identifier = (postfix == null) ? "api" : hostname.split(postfix)[0];

        return getStudyByIdentifier(identifier);
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
            if (!config.isLocal()) {
                uploadCertService.createCmsKeyPair(study.getIdentifier());

                String record = dnsDao.createDnsRecordForStudy(study.getIdentifier());
                String domain = herokuApi.registerDomainForStudy(study.getIdentifier());

                if (record != null && record.equals(domain)) {
                    study.setHostname(domain);
                } else {
                    String msg = String.format("DNS record (%s) and hostname as registered with Heroku (%s) don't match.", record, domain);
                    throw new BridgeServiceException(msg);
                }
            } else {
                study.setHostname(study.getIdentifier() + config.getStudyHostnamePostfix());
            }
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
            if (!config.isLocal()) {
                herokuApi.unregisterDomainForStudy(identifier);
                dnsDao.deleteDnsRecordForStudy(identifier);
            }
            directoryDao.deleteDirectoryForStudy(identifier);
            studyDao.deleteStudy(identifier);
            cacheProvider.removeStudy(identifier);
        } finally {
            lockDao.releaseLock(Study.class, identifier, lockId);
        }
    }
}
