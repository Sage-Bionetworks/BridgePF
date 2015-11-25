package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.models.CriteriaUtils.SPECIFICITY_SORTER;

import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.SubpopulationDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.CriteriaUtils;
import org.sagebionetworks.bridge.models.schedules.ScheduleContext;
import org.sagebionetworks.bridge.models.studies.Subpopulation;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.google.common.collect.Lists;

@Component
public class DynamoSubpopulationDao implements SubpopulationDao {
    
    private DynamoDBMapper mapper;

    /** DynamoDB mapper for the HealthDataRecord table. This is configured by Spring. */
    @Resource(name = "subpopulationDdbMapper")
    public void setMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }
    
    @Override
    public Subpopulation createSubpopulation(Subpopulation subpop) {
        checkNotNull(subpop);
        checkNotNull(subpop.getStudyIdentifier());

        subpop.setVersion(null);
        subpop.setGuid(BridgeUtils.generateGuid());
        mapper.save(subpop);
        return subpop;
    }

    @Override
    public Subpopulation updateSubpopulation(Subpopulation subpop) {
        checkNotNull(subpop);
        checkNotNull(subpop.getStudyIdentifier());
        checkNotNull(subpop.getVersion());
        checkNotNull(subpop.getGuid());
        
        mapper.save(subpop);
        return subpop;
    }

    /**
     * If no subpopulations can be found by this method, a default subpop will be created using
     * a key that will match the key of the existing consent stream.
     */
    @Override
    public List<Subpopulation> getSubpopulations(StudyIdentifier studyId, boolean createDefault) {
        DynamoSubpopulation hashKey = new DynamoSubpopulation();
        hashKey.setStudyIdentifier(studyId.getIdentifier());
        
        DynamoDBQueryExpression<DynamoSubpopulation> query = 
                new DynamoDBQueryExpression<DynamoSubpopulation>().withHashKeyValues(hashKey);
        
        List<DynamoSubpopulation> subpops = mapper.query(DynamoSubpopulation.class, query);
        if (createDefault && subpops.isEmpty()) {
            // Using the studyId as the GUID ensures this first subpop is linked to the existing
            // stream of consent documents. Future subpops will have a GUID instead.
            DynamoSubpopulation subpop = new DynamoSubpopulation();
            subpop.setStudyIdentifier(studyId.getIdentifier());
            subpop.setGuid(studyId.getIdentifier());
            subpop.setName("Default Consent Group");
            subpop.setMinAppVersion(0);
            subpop.setRequired(true);
            mapper.save(subpop);
            
            subpops = mapper.query(DynamoSubpopulation.class, query);
        }
        return subpops.stream().sorted(SPECIFICITY_SORTER).collect(Collectors.toList());
    }
    
    @Override
    public Subpopulation getSubpopulation(StudyIdentifier studyId, String guid) {
        DynamoSubpopulation hashKey = new DynamoSubpopulation();
        hashKey.setStudyIdentifier(studyId.getIdentifier());
        hashKey.setGuid(guid);
        
        Subpopulation subpop = mapper.load(hashKey);
        if (subpop == null) {
            throw new EntityNotFoundException(Subpopulation.class);
        }
        return subpop;
    }

    @Override
    public Subpopulation getSubpopulationForUser(ScheduleContext context) {
        List<Subpopulation> found = Lists.newArrayList();

        List<Subpopulation> subpops = getSubpopulations(context.getStudyIdentifier(), true);
        for (Subpopulation subpop : subpops) {
            boolean matches = CriteriaUtils.matchCriteria(context, subpop);
            if (matches) {
                found.add(subpop);
            }
        }
        if (found.isEmpty()) {
            throw new EntityNotFoundException(Subpopulation.class);
        }
        // Return the most specific match. Eventually we may return them all
        return found.stream().sorted(SPECIFICITY_SORTER).collect(Collectors.toList()).get(0);
    }
    
    @Override
    public void deleteSubpopulation(StudyIdentifier studyId, String guid) {
        Subpopulation subpop = getSubpopulation(studyId, guid);
        mapper.delete(subpop);
    }
    
}
