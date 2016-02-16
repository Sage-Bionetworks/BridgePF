package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import javax.annotation.Resource;

import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.CriteriaDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.Criteria;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;

@Component
public class DynamoCriteriaDao implements CriteriaDao {

    DynamoDBMapper criteriaMapper;
    
    @Resource(name = "criteriaMapper")
    public void setSurveyElementMapper(DynamoDBMapper criteriaMapper) {
        this.criteriaMapper = criteriaMapper;
    }
    
    // Until criteria is converted from an interface to a concrete class, we need to copy it.
    private DynamoCriteria copyCriteria(Criteria criteria) {
        DynamoCriteria actualCriteria = new DynamoCriteria();
        actualCriteria.setKey(criteria.getKey());
        actualCriteria.setMinAppVersion(criteria.getMinAppVersion());
        actualCriteria.setMaxAppVersion(criteria.getMaxAppVersion());
        actualCriteria.setAllOfGroups(criteria.getAllOfGroups());
        actualCriteria.setNoneOfGroups(criteria.getNoneOfGroups());
        return actualCriteria;        
    }
    
    @Override
    public void createOrUpdateCriteria(Criteria criteria) {
        checkNotNull(criteria);
        
        DynamoCriteria actualCriteria = copyCriteria(criteria);
        criteriaMapper.save(actualCriteria);
    }
    
    @Override
    public Criteria getCriteria(String key) {
        isNotBlank(key);
        DynamoCriteria criteria = new DynamoCriteria();
        criteria.setKey(key);

        DynamoCriteria existing = criteriaMapper.load(criteria);
        if (existing == null) {
            throw new EntityNotFoundException(Criteria.class);
        }
        return existing;
    }

    @Override
    public void deleteCriteria(String key) {
        isNotBlank(key);
        DynamoCriteria criteria = new DynamoCriteria();
        criteria.setKey(key);
        
        Criteria existing = criteriaMapper.load(criteria);
        if (existing == null) {
            throw new EntityNotFoundException(Criteria.class);
        }
        criteriaMapper.delete(criteria);
    }

}
