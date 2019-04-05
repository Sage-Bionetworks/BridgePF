package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.util.BridgeCollectors.toImmutableList;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.validation.Validator;

import com.fasterxml.jackson.core.type.TypeReference;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.cache.CacheKey;
import org.sagebionetworks.bridge.dao.StudyConsentDao;
import org.sagebionetworks.bridge.dao.SubpopulationDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.CriteriaUtils;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsent;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsentForm;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsentView;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.validators.SubpopulationValidator;
import org.sagebionetworks.bridge.validators.Validate;

@Component
public class SubpopulationService {

    private static final TypeReference<List<Subpopulation>> SURVEY_LIST_REF = new TypeReference<List<Subpopulation>>() {};

    private SubpopulationDao subpopDao;
    private StudyConsentDao studyConsentDao;
    private StudyConsentService studyConsentService;
    private SubstudyService substudyService;
    private StudyConsentForm defaultConsentDocument;
    private CacheProvider cacheProvider;
    
    @Autowired
    final void setSubpopulationDao(SubpopulationDao subpopDao) {
        this.subpopDao = subpopDao;
    }
    @Autowired
    final void setStudyConsentDao(StudyConsentDao studyConsentDao) {
        this.studyConsentDao = studyConsentDao;
    }
    @Autowired
    final void setStudyConsentService(StudyConsentService studyConsentService) {
        this.studyConsentService = studyConsentService;
    }
    @Autowired
    final void setCacheProvider(CacheProvider cacheProvider) {
        this.cacheProvider = cacheProvider;
    }
    @Autowired
    final void setSubstudyService(SubstudyService substudyService) {
        this.substudyService = substudyService;
    }
    @Value("classpath:study-defaults/consent-body.xhtml")
    final void setDefaultConsentDocument(org.springframework.core.io.Resource resource) throws IOException {
        this.defaultConsentDocument = new StudyConsentForm(IOUtils.toString(resource.getInputStream(), StandardCharsets.UTF_8));
    }
    // For testing to stub out this object rather than loading from disk
    final void setDefaultConsentForm(StudyConsentForm form) {
        this.defaultConsentDocument = form;
    }
    
    /**
     * Create subpopulation.
     * @param study 
     * @param subpop
     * @return
     */
    public Subpopulation createSubpopulation(Study study, Subpopulation subpop) {
        checkNotNull(study);
        checkNotNull(subpop);

        subpop.setGuidString(BridgeUtils.generateGuid());
        subpop.setStudyIdentifier(study.getIdentifier());

        Set<String> substudyIds = substudyService.getSubstudyIds(study.getStudyIdentifier());
        
        Validator validator = new SubpopulationValidator(study.getDataGroups(), substudyIds);
        Validate.entityThrowingException(validator, subpop);
        
        Subpopulation created = subpopDao.createSubpopulation(subpop);
        
        // Create a default consent for this subpopulation.
        StudyConsentView view = studyConsentService.addConsent(subpop.getGuid(), defaultConsentDocument);
        studyConsentService.publishConsent(study, subpop, view.getCreatedOn());
        
        cacheProvider.removeObject(CacheKey.subpopList(study.getStudyIdentifier()));
        return created;
    }
    
    /**
     * Create a default subpopulation for a new study
     * @param study
     * @return
     */
    public Subpopulation createDefaultSubpopulation(Study study) {
        SubpopulationGuid subpopGuid = SubpopulationGuid.create(study.getIdentifier());
        Subpopulation created = subpopDao.createDefaultSubpopulation(study.getStudyIdentifier());
        
        // It should no longer be necessary to check that there are no consents yet, but not harmful to keep doing it.
        if (studyConsentService.getAllConsents(subpopGuid).isEmpty()) {
            StudyConsentView view = studyConsentService.addConsent(subpopGuid, defaultConsentDocument);
            studyConsentService.publishConsent(study, created, view.getCreatedOn());
        }
        
        cacheProvider.removeObject(CacheKey.subpopList(study.getStudyIdentifier()));
        return created;
    }
    
    /**
     * Update a subpopulation.
     * @param study
     * @param subpop
     * @return
     */
    public Subpopulation updateSubpopulation(Study study, Subpopulation subpop) {
        checkNotNull(study);
        checkNotNull(subpop);
        
        subpop.setStudyIdentifier(study.getIdentifier());

        // Verify this subpopulation is part of the study. Existing code also doesn't submit
        // this publication timestamp back to the server, so set if it doesn't exist.
        Subpopulation existingSubpop = getSubpopulation(study, subpop.getGuid());
        if (subpop.getPublishedConsentCreatedOn() == 0L) {
            subpop.setPublishedConsentCreatedOn(existingSubpop.getPublishedConsentCreatedOn());
        }
        // Verify that the publishedConsentCreatedOn field points to a real study consent. Don't use the service
        // because it loads the document from S3.
        StudyConsent consent = studyConsentDao.getConsent(subpop.getGuid(), subpop.getPublishedConsentCreatedOn());
        if (consent == null) {
            throw new EntityNotFoundException(StudyConsent.class);
        }
        
        Set<String> substudyIds = substudyService.getSubstudyIds(study.getStudyIdentifier());
        
        Validator validator = new SubpopulationValidator(study.getDataGroups(), substudyIds);
        Validate.entityThrowingException(validator, subpop);
        
        Subpopulation updated = subpopDao.updateSubpopulation(subpop);
        cacheProvider.removeObject(CacheKey.subpop(updated.getGuid(), study.getStudyIdentifier()));
        cacheProvider.removeObject(CacheKey.subpopList(study.getStudyIdentifier()));
        return updated;
    }
    
    /**
     * Get all subpopulations defined for this study that have not been deleted. If 
     * there are no subpopulations, a default subpopulation will be created with a 
     * default consent.
     */
    public List<Subpopulation> getSubpopulations(StudyIdentifier studyId, boolean includeDeleted) {
        checkNotNull(studyId);
        
        // If retrieving all subpopulations, which is unusual, do not use the cache. Cache is always
        // of the undeleted subpopulations only.
        if (includeDeleted) {
            return subpopDao.getSubpopulations(studyId, true, includeDeleted);
        }
        CacheKey subpopListKey = CacheKey.subpopList(studyId);
        List<Subpopulation> subpops = cacheProvider.getObject(subpopListKey, SURVEY_LIST_REF);
        if (subpops == null) {
            subpops = subpopDao.getSubpopulations(studyId, true, includeDeleted);
            cacheProvider.setObject(subpopListKey, subpops);
        }
        return subpops;
    }
    
    /**
     * Get a specific subpopulation.
     * @param studyId
     * @param subpopGuid
     * @return subpopulation
     */
    public Subpopulation getSubpopulation(StudyIdentifier studyId, SubpopulationGuid subpopGuid) {
        checkNotNull(studyId);
        checkNotNull(subpopGuid);
        
        CacheKey subpopKey = CacheKey.subpop(subpopGuid, studyId);
        Subpopulation subpop = cacheProvider.getObject(subpopKey, Subpopulation.class);
        if (subpop == null) {
            subpop = subpopDao.getSubpopulation(studyId, subpopGuid);
            cacheProvider.setObject(subpopKey, subpop);
        }
        return subpop;
    }
    
    /**
     * Get all subpopulations for a user that match the provided CriteriaContext information. 
     * Returns an empty list if no subpopulations match.
     */
    public List<Subpopulation> getSubpopulationsForUser(CriteriaContext context) {
        checkNotNull(context);
        
        List<Subpopulation> subpops = getSubpopulations(context.getStudyIdentifier(), false);

        return subpops.stream().filter(subpop -> {
            return CriteriaUtils.matchCriteria(context, subpop.getCriteria());
        }).collect(toImmutableList());
    }

    /**
     * Delete a subpopulation and remove it from cache.
     */
    public void deleteSubpopulation(StudyIdentifier studyId, SubpopulationGuid subpopGuid) {
        checkNotNull(studyId);
        checkNotNull(subpopGuid);
        
        subpopDao.deleteSubpopulation(studyId, subpopGuid);
        cacheProvider.removeObject(CacheKey.subpop(subpopGuid, studyId));
        cacheProvider.removeObject(CacheKey.subpopList(studyId));
    }
    
    /**
     * Permanently delete a subpopulation and remove it from cache.
     */
    public void deleteSubpopulationPermanently(StudyIdentifier studyId, SubpopulationGuid subpopGuid) {
        checkNotNull(studyId);
        checkNotNull(subpopGuid);

        subpopDao.deleteSubpopulationPermanently(studyId, subpopGuid);
        studyConsentService.deleteAllConsentsPermanently(subpopGuid);
        cacheProvider.removeObject(CacheKey.subpop(subpopGuid, studyId));
        cacheProvider.removeObject(CacheKey.subpopList(studyId));
    }
    
    /**
     * Delete all subpopulations. This is a physical delete and not a logical delete, and is not exposed 
     * in the API. This deletes everything, including the default subpopulation. This is used when 
     * deleting a study, as part of a test, for example.
     */
    public void deleteAllSubpopulations(StudyIdentifier studyId) {
        checkNotNull(studyId);
        
        List<Subpopulation> subpops = getSubpopulations(studyId, true);
        if (!subpops.isEmpty()) {
            for (Subpopulation subpop : subpops) {
                deleteSubpopulationPermanently(studyId, subpop.getGuid());
            }
        }
    }

}
