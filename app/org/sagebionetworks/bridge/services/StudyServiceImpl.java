package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.BridgeUtils.checkNewEntity;

import java.util.Collection;
import java.util.Collections;
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
import org.sagebionetworks.bridge.models.studies.Study2;
import org.sagebionetworks.bridge.validators.StudyValidator;
import org.sagebionetworks.bridge.validators.Validate;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.UncheckedExecutionException;

public class StudyServiceImpl extends CacheLoader<String,Study2> implements StudyService {
    
    LoadingCache<String, Study2> studyCache = CacheBuilder.newBuilder()
            .maximumSize(50)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build(this);
    
    public Study2 load(String identifier) throws EntityNotFoundException {
        return studyDao.getStudy(identifier);
    }
    
    private StudyDao studyDao;
    private DirectoryDao directoryDao;
    private HerokuApi herokuApi;
    private DnsDao dnsDao;
    private DistributedLockDao lockDao;
    private BridgeConfig config;
    private StudyValidator validator;

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
    
    // REMOVEME
    private Map<String,Study> studies = Maps.newHashMap();
    public void setStudies(List<Study> studiesList) {
        for (Study study : studiesList) {
            for (String hostname : study.getHostnames()) {
                studies.put(hostname, study);    
            }
        }
    }
    public Study getStudyByIdentifier(String key) {
        for (Study study : studies.values()) {
            if (study.getKey().equals(key)) {
                return study;
            }
        }
        return null;
    }
    public Study getStudyByHostname(String hostname) {
        Study study = studies.get(hostname);
        return (study == null) ? getStudyByIdentifier("api") : study;
    }
    public Collection<Study> getStudies() {
        return Collections.unmodifiableCollection(studies.values());
    }
    // END REMOVEME
    
    @Override
    public Study2 getStudy2ByIdentifier(String identifier) {
        checkArgument(isNotBlank(identifier), Validate.CANNOT_BE_BLANK, "identifier");
        
        try {
            return studyCache.get(identifier);
        } catch (UncheckedExecutionException e) {
            throw new EntityNotFoundException(Study2.class);
        } catch (ExecutionException e) {
            throw new BridgeServiceException(e);
        }
    }
    @Override
    public Study2 getStudy2ByHostname(String hostname) {
        checkArgument(isNotBlank(hostname), Validate.CANNOT_BE_BLANK, "hostname");
        
        String postfix = config.getStudyHostnamePostfix();
<<<<<<< HEAD
        
        String identifier = (postfix == null) ? "api" : hostname.split(postfix)[0];
=======
        String identifier = (postfix == null) ? "teststudy" : hostname.split(postfix)[0];
        
>>>>>>> c72ad370f209818037d76b174f226d84b8316c1d
        return getStudy2ByIdentifier(identifier);
    }
    @Override
    public List<Study2> getStudies2() {
        List<Study2> studies = studyDao.getStudies();
        for (Study2 study : studies) {
            studyCache.put(study.getIdentifier(), study);
        }
        return studies;
    }
    @Override
    public Study2 createStudy(Study2 study) {
        checkNotNull(study, Validate.CANNOT_BE_NULL, "study");
        checkNewEntity(study, study.getVersion(), "Study has a version value; it may already exist");

        Validate.entityThrowingException(validator, study);

        String id = study.getIdentifier();
        String lockId = null;
        try {
            lockId = lockDao.createLock(Study2.class, id);
            
            if (studyDao.doesIdentifierExist(study.getIdentifier())) {
                throw new EntityAlreadyExistsException(study);
            }
            study.setResearcherRole(study.getIdentifier() + "_researcher");
            
            String directory = directoryDao.createDirectoryForStudy(study.getIdentifier());
            study.setStormpathHref(directory);
            
            String record = dnsDao.createDnsRecordForStudy(study.getIdentifier());
            String domain = herokuApi.registerDomainForStudy(study.getIdentifier());
            
            if (record != null && record.equals(domain)) {
                study.setHostname(domain);    
            } else {
                String msg = String.format("DNS record (%s) and hostname as registered with Heroku (%s) don't match.", record, domain);
                throw new BridgeServiceException(msg);
            }
            study = studyDao.createStudy(study);    
        } finally {
            lockDao.releaseLock(Study2.class, id, lockId);
        }
        return study;
    }
    @Override
    public Study2 updateStudy(Study2 study) {
        checkNotNull(study, Validate.CANNOT_BE_NULL, "study");
        Validate.entityThrowingException(validator, study);
        
        // These cannot be set through the API and will be null here, so they are set on update
        Study2 originalStudy = studyDao.getStudy(study.getIdentifier());
        study.setHostname(originalStudy.getHostname());
        study.setStormpathHref(originalStudy.getStormpathHref());
        study.setResearcherRole(originalStudy.getResearcherRole());
        
        Study2 updatedStudy = studyDao.updateStudy(study);
        studyCache.invalidate(study.getIdentifier());
        return updatedStudy;
    }
    @Override
    public void deleteStudy(String identifier) {
        checkArgument(isNotBlank(identifier), Validate.CANNOT_BE_BLANK, "identifier");
        
        String lockId = null;
        try {
            lockId = lockDao.createLock(Study2.class, identifier);
            
            herokuApi.unregisterDomainForStudy(identifier);
            dnsDao.deleteDnsRecordForStudy(identifier);
            directoryDao.deleteDirectoryForStudy(identifier);
            studyDao.deleteStudy(identifier);
            studyCache.invalidate(identifier);
        } finally {
            lockDao.releaseLock(Study2.class, identifier, lockId);
        }
    }
}
