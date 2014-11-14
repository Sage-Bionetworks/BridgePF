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

import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.config.Environment;
import org.sagebionetworks.bridge.dao.DirectoryDao;
import org.sagebionetworks.bridge.dao.DistributedLockDao;
import org.sagebionetworks.bridge.dao.StudyDao;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy.StudyEnvironment;
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

public class StudyServiceImpl extends CacheLoader<String,Study2>  implements StudyService {
    
    private static Map<Environment,String> postfixes = Maps.newHashMap();
    static {
        postfixes.put(Environment.LOCAL, "");
        postfixes.put(Environment.DEV, "-develop.");
        postfixes.put(Environment.UAT, "-staging.");
        postfixes.put(Environment.PROD, ".");
    }

    LoadingCache<String, Study2> studyCache = CacheBuilder.newBuilder()
            .maximumSize(50)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build(this);
    
    public Study2 load(String identifier) throws EntityNotFoundException {
        return studyDao.getStudy(identifier);
    }
    
    private StudyDao studyDao;
    private DirectoryDao directoryDao;
    private DistributedLockDao lockDao;
    private StudyValidator validator;

    public void setDistributedLockDao(DistributedLockDao lockDao) {
        this.lockDao = lockDao;
    }
    
    public void setValidator(StudyValidator validator) {
        this.validator = validator;
    }
    public void setStudyDao(StudyDao studyDao) {
        this.studyDao = studyDao;
    }
    
    public void setDirectoryDao(DirectoryDao directoryDao) {
        this.directoryDao = directoryDao;
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
        return (study == null) ? getStudyByIdentifier("teststudy") : study;
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
        
        String postfix = postfixes.get(BridgeConfigFactory.getConfig().getEnvironment());
        String identifier = (postfix == null) ? "teststudy" : hostname.split(postfix)[0];
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
            
            if (!studyDao.doesIdentifierExist(study.getIdentifier())) {
                throw new EntityAlreadyExistsException(study);
            }
            for (Environment env : Environment.values()) {
                String href = directoryDao.createDirectory(env, study.getIdentifier());
                study.setStormpathUrl(env, href);
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
            
            DynamoStudy study = (DynamoStudy)studyDao.getStudy(identifier);
            for (Environment env : Environment.values()) {
                String href = study.getStudyEnvironments().get(env).getStormpathHref();
                directoryDao.deleteDirectory(env, href);
            }
            studyDao.deleteStudy(identifier);
            studyCache.invalidate(identifier);
        } finally {
            lockDao.releaseLock(Study2.class, identifier, lockId);
        }
    }
    @Override
    public Study2 changeStudyId(String oldIdentifier, String newIdentifier) {
        checkArgument(isNotBlank(oldIdentifier), Validate.CANNOT_BE_BLANK, "oldIdentifier");
        checkArgument(isNotBlank(newIdentifier), Validate.CANNOT_BE_BLANK, "newIdentifier");

        // The study object is loaded, copied, then deleted and created.
        // Study data is lost, that's a much more complicated thing to preserve
        DynamoStudy study = (DynamoStudy)studyDao.getStudy(oldIdentifier);
        
        DynamoStudy newStudy = new DynamoStudy();
        newStudy.setName(study.getName());
        newStudy.setIdentifier(newIdentifier);
        newStudy.setMaxParticipants(study.getMaxParticipants());
        newStudy.setMinAgeOfConsent(study.getMinAgeOfConsent());
        newStudy.getTrackerIdentifiers().addAll(study.getTrackerIdentifiers());
        for (Environment env: Environment.values()) {
            StudyEnvironment se = study.getStudyEnvironments().get(env);
            newStudy.getStudyEnvironments().put(env, se);
        }
        Validate.entityThrowingException(validator, newStudy);
        
        studyDao.createStudy(newStudy);
        // In stormpath so as to not lose users, rename the existing directories, rename
        // the researcher groups.
        for (Environment env : Environment.values()) {
            directoryDao.renameStudyIdentifier(env, study.getIdentifier(), newIdentifier);    
        }
        studyDao.deleteStudy(study.getIdentifier());
        
        return newStudy;
    }
}
