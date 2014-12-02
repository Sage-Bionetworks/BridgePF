package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.BridgeUtils.checkNewEntity;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.UncheckedExecutionException;

public class StudyServiceImpl extends CacheLoader<String,Study> implements StudyService {
    
    LoadingCache<String, Study> studyCache = CacheBuilder.newBuilder()
            .maximumSize(50)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build(this);
    
    public Study load(String identifier) throws EntityNotFoundException {
        return studyDao.getStudy(identifier);
    }
    
    private StudyDao studyDao;
    private DirectoryDao directoryDao;
    private HerokuApi herokuApi;
    private DnsDao dnsDao;
    private DistributedLockDao lockDao;
    private BridgeConfig config;
    private StudyValidator validator;
    private Map<String,Tracker> trackersByIdentifier = Maps.newHashMap();

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
        
        try {
            return studyCache.get(identifier);
        } catch (UncheckedExecutionException e) {
            throw new EntityNotFoundException(Study.class);
        } catch (ExecutionException e) {
            throw new BridgeServiceException(e);
        }
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
        List<Study> studies = studyDao.getStudies();
        for (Study study : studies) {
            studyCache.put(study.getIdentifier(), study);
        }
        return studies;
    }
    @Override
    public Study createStudy(Study study) {
        checkNotNull(study, Validate.CANNOT_BE_NULL, "study");
        checkNewEntity(study, study.getVersion(), "Study has a version value; it may already exist");

        Validate.entityThrowingException(validator, study);

        String id = study.getIdentifier();
        String lockId = null;
        try {
            lockId = lockDao.acquire(Study.class, id);
            
            if (studyDao.doesIdentifierExist(study.getIdentifier())) {
                throw new EntityAlreadyExistsException(study);
            }
            study.setResearcherRole(study.getIdentifier() + "_researcher");
            
            String directory = directoryDao.createDirectoryForStudy(study.getIdentifier());
            study.setStormpathHref(directory);
            if (!config.isLocal()) {
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
        } finally {
            lockDao.release(Study.class, id, lockId);
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
        studyCache.invalidate(study.getIdentifier());
        return updatedStudy;
    }
    @Override
    public void deleteStudy(String identifier) {
        checkArgument(isNotBlank(identifier), Validate.CANNOT_BE_BLANK, "identifier");
        
        String lockId = null;
        try {
            lockId = lockDao.acquire(Study.class, identifier);
            
            if (!config.isLocal()) {
                herokuApi.unregisterDomainForStudy(identifier);
                dnsDao.deleteDnsRecordForStudy(identifier);
            }
            directoryDao.deleteDirectoryForStudy(identifier);
            studyDao.deleteStudy(identifier);
            studyCache.invalidate(identifier);
        } finally {
            lockDao.release(Study.class, identifier, lockId);
        }
    }
}
