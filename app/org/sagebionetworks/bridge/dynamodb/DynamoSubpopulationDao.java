package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.util.BridgeCollectors.toImmutableList;

import java.util.List;

import javax.annotation.Resource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.CriteriaDao;
import org.sagebionetworks.bridge.dao.StudyConsentDao;
import org.sagebionetworks.bridge.dao.SubpopulationDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.CriteriaUtils;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper.FailedBatch;
import com.google.common.collect.ImmutableList;

@Component
public class DynamoSubpopulationDao implements SubpopulationDao {
    
    private DynamoDBMapper mapper;
    private StudyConsentDao studyConsentDao;
    private CriteriaDao criteriaDao;

    @Resource(name = "subpopulationDdbMapper")
    final void setMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }
    
    @Autowired
    final void setStudyConsentDao(StudyConsentDao studyConsentDao) {
        this.studyConsentDao = studyConsentDao;
    }
    
    @Autowired
    final void setCriteriaDao(CriteriaDao criteriaDao) {
        this.criteriaDao = criteriaDao;
    }
    
    @Override
    public Subpopulation createSubpopulation(Subpopulation subpop) {
        checkNotNull(subpop);
        checkNotNull(subpop.getGuidString());
        checkNotNull(subpop.getStudyIdentifier());

        // guid should always be set in service, so it's okay to check with a checkNotNull (returns 500). 
        // But if version is present, that's a bad submission from the service user, return a 400
        if (subpop.getVersion() != null) { 
            throw new BadRequestException("Subpopulation does not appear to be new (includes version number).");
        }
        
        persistCriteria(subpop);
        
        // these are ignored if submitted. delete remains what it was
        subpop.setDeleted(false); 
        subpop.setDefaultGroup(false);
        mapper.save(subpop);
        return subpop;
    }

    @Override
    public Subpopulation updateSubpopulation(Subpopulation subpop) {
        checkNotNull(subpop);
        checkNotNull(subpop.getStudyIdentifier());
        
        // These have to be supplied by the user so if they don't exist, we want a 400-level exception,
        if (subpop.getVersion() == null || subpop.getGuidString() == null) {
            throw new BadRequestException("Subpopulation appears to be a new object (no guid or version).");
        }
        StudyIdentifier studyId = new StudyIdentifierImpl(subpop.getStudyIdentifier());
        Subpopulation existing = getSubpopulation(studyId, subpop.getGuid());
        if (existing == null || existing.isDeleted()) {
            throw new EntityNotFoundException(Subpopulation.class);
        }
        
        persistCriteria(subpop);
        
        // these are ignored if submitted. delete remains what it was
        subpop.setDefaultGroup(existing.isDefaultGroup()); 
        subpop.setDeleted(false);
        mapper.save(subpop);
        return subpop;
    }

    @Override
    public List<Subpopulation> getSubpopulations(StudyIdentifier studyId, boolean createDefault, boolean includeDeleted) {
        DynamoSubpopulation hashKey = new DynamoSubpopulation();
        hashKey.setStudyIdentifier(studyId.getIdentifier());
        
        DynamoDBQueryExpression<DynamoSubpopulation> query = 
                new DynamoDBQueryExpression<DynamoSubpopulation>().withHashKeyValues(hashKey);
        
        // Get all the records because we only create a default if there are no physical records, 
        // regardless of the deletion status.
        List<DynamoSubpopulation> subpops = mapper.query(DynamoSubpopulation.class, query);
        if (createDefault && subpops.isEmpty()) {
            Subpopulation subpop = createDefaultSubpopulation(studyId);
            return ImmutableList.of(subpop);
        }
        // Now filter out deleted subpopulations, if requested
        List<Subpopulation> subpopulations = subpops.stream()
                .filter(subpop -> includeDeleted || !subpop.isDeleted())
                .collect(toImmutableList());
        
        for (Subpopulation subpop : subpopulations) {
            loadCriteria(subpop);
        }
        return subpopulations;
    }
    
    @Override
    public Subpopulation createDefaultSubpopulation(StudyIdentifier studyId) {
        DynamoSubpopulation subpop = new DynamoSubpopulation();
        subpop.setStudyIdentifier(studyId.getIdentifier());
        subpop.setGuidString(studyId.getIdentifier());
        subpop.setName("Default Consent Group");
        subpop.setMinAppVersion(0);
        subpop.setDefaultGroup(true);
        // The first group is required until the study designers say otherwise
        subpop.setRequired(true);
        
        // We know in this case that criteria do not exist
        Criteria criteria = criteriaDao.copyCriteria(getKey(subpop), subpop);
        criteriaDao.createOrUpdateCriteria(criteria);
        subpop.setCriteria(criteria);
        
        mapper.save(subpop);
        return subpop;
    }
    
    @Override
    public Subpopulation getSubpopulation(StudyIdentifier studyId, SubpopulationGuid subpopGuid) {
        DynamoSubpopulation hashKey = new DynamoSubpopulation();
        hashKey.setStudyIdentifier(studyId.getIdentifier());
        hashKey.setGuidString(subpopGuid.getGuid());
        
        Subpopulation subpop = mapper.load(hashKey);
        if (subpop == null) {
            throw new EntityNotFoundException(Subpopulation.class);
        }
        loadCriteria(subpop);
        
        return subpop;
    }

    @Override
    public List<Subpopulation> getSubpopulationsForUser(CriteriaContext context) {
        List<Subpopulation> subpops = getSubpopulations(context.getStudyIdentifier(), true, false);

        return subpops.stream().filter(subpop -> {
            return CriteriaUtils.matchCriteria(context, subpop.getCriteria());
        }).collect(toImmutableList());
    }
    
    @Override
    public void deleteSubpopulation(StudyIdentifier studyId, SubpopulationGuid subpopGuid, boolean physicalDelete) {
        Subpopulation subpop = getSubpopulation(studyId, subpopGuid);
        if (subpop == null || (!physicalDelete && subpop.isDeleted())) {
            throw new EntityNotFoundException(Subpopulation.class);
        }
        if (subpop.isDefaultGroup()) {
            throw new BadRequestException("Cannot delete the default subpopulation for a study.");
        }
        if (physicalDelete) {
            studyConsentDao.deleteAllConsents(subpopGuid);
            criteriaDao.deleteCriteria(subpop.getKey());
            mapper.delete(subpop);
        } else {
            subpop.setDeleted(true);
            mapper.save(subpop);
        }
    }

    @Override
    public void deleteAllSubpopulations(StudyIdentifier studyId) {
        List<Subpopulation> subpops = getSubpopulations(studyId, false, true);
        if (!subpops.isEmpty()) {
            for (Subpopulation subpop : subpops) {
                studyConsentDao.deleteAllConsents(subpop.getGuid());
                criteriaDao.deleteCriteria(subpop.getKey());
            }
            List<FailedBatch> failures = mapper.batchDelete(subpops);
            BridgeUtils.ifFailuresThrowException(failures);
        }
    }
    
    private String getKey(Subpopulation subpop) {
        return "subpopulation:" + subpop.getGuidString();
    }
    
    // Save the criteria object if it exists. If it does not, copy the criteria data from the subpopulation
    // and save that. Set that on subpopulation. Once all models have a criteria object, this will be removed 
    // along with the fields on subpopulation.
    private void persistCriteria(Subpopulation subpop) {
        Criteria criteria = subpop.getCriteria();
        Criteria makeCopyOf = (criteria == null) ? subpop : criteria;

        Criteria copy = criteriaDao.copyCriteria(getKey(subpop), makeCopyOf);
        criteriaDao.createOrUpdateCriteria(copy);
        subpop.setCriteria(copy);
    }

    // Load the criteria object. If it doesn't exist, assemble it from the criteria data on the subpopulation
    // object. Once all models have a criteria object, this will be removed along with the fields on subpopulation.
    private void loadCriteria(Subpopulation subpop) {
        Criteria criteria = criteriaDao.getCriteria(getKey(subpop));
        if (criteria == null) {
            criteria = criteriaDao.copyCriteria(getKey(subpop), subpop);
        }
        subpop.setCriteria(criteria);
    }
}
