package org.sagebionetworks.bridge.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;

import javax.annotation.Resource;

import org.sagebionetworks.bridge.dao.HealthCodeDao;
import org.springframework.stereotype.Component;

@Component
public class DynamoHealthCodeDao implements HealthCodeDao {

    private DynamoDBMapper mapper;
    
    @Resource(name = "healthCodeDdbMapper")
    public final void setMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String getStudyIdentifier(final String code) {
        DynamoHealthCode key = new DynamoHealthCode();
        key.setCode(code);
        DynamoHealthCode loaded = mapper.load(key);
        if (loaded == null) {
            return null;
        }
        return loaded.getStudyIdentifier();
    }
}
