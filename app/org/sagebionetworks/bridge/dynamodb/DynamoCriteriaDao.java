package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import javax.annotation.Resource;

import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.CriteriaDao;
import org.sagebionetworks.bridge.models.Criteria;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;

/**
 * The DAO for managing criteria, which are optionally associated with models that can be filtered by Criteria 
 * parameters. 
 */
@Component
public class DynamoCriteriaDao implements CriteriaDao {

    private DynamoDBMapper criteriaMapper;
    
    @Resource(name = "criteriaMapper")
    final void setCriteriaMapper(DynamoDBMapper criteriaMapper) {
        this.criteriaMapper = criteriaMapper;
    }
    
    @Override
    public Criteria createOrUpdateCriteria(Criteria criteria) {
        checkNotNull(criteria);
        checkArgument(isNotBlank(criteria.getKey()));

        criteriaMapper.save(criteria);
        return criteria;
    }
    
    @Override
    public Criteria getCriteria(String key) {
        checkArgument(isNotBlank(key));
        
        DynamoCriteria hashKey = new DynamoCriteria();
        hashKey.setKey(key);

        return criteriaMapper.load(hashKey);
    }

    @Override
    public void deleteCriteria(String key) {
        checkArgument(isNotBlank(key));
        
        DynamoCriteria hashKey = new DynamoCriteria();
        hashKey.setKey(key);
        
        Criteria criteria = criteriaMapper.load(hashKey);
        if (criteria != null) {
            criteriaMapper.delete(hashKey);
        }
    }

}
