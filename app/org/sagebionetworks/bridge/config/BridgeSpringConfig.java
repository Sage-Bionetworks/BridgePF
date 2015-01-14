package org.sagebionetworks.bridge.config;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import org.sagebionetworks.bridge.dynamodb.DynamoUploadSchema;
import org.sagebionetworks.bridge.dynamodb.TableNameOverrideFactory;

@ComponentScan(basePackages = "org.sagebionetworks.bridge")
@Configuration
public class BridgeSpringConfig {
    @Bean(name = "UploadSchemaDdbMapper")
    @Autowired
    public DynamoDBMapper uploadSchemaDdbMapper(AmazonDynamoDB client) {
        DynamoDBMapperConfig mapperConfig = new DynamoDBMapperConfig.Builder()
                .withTableNameOverride(TableNameOverrideFactory.getTableNameOverride(DynamoUploadSchema.class))
                .build();
        return new DynamoDBMapper(client, mapperConfig);
    }
}
