package org.sagebionetworks.bridge.config;

import javax.annotation.Resource;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.s3.AmazonS3Client;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import org.sagebionetworks.bridge.crypto.CmsEncryptor;
import org.sagebionetworks.bridge.crypto.CmsEncryptorCacheLoader;
import org.sagebionetworks.bridge.dynamodb.DynamoUpload;
import org.sagebionetworks.bridge.dynamodb.DynamoUpload2;
import org.sagebionetworks.bridge.dynamodb.DynamoUploadSchema;
import org.sagebionetworks.bridge.dynamodb.TableNameOverrideFactory;
import org.sagebionetworks.bridge.s3.S3Helper;

@ComponentScan(basePackages = "org.sagebionetworks.bridge")
@Configuration
public class BridgeSpringConfig {
    @Bean(name = "AsyncExecutorService")
    @Resource(name = "numAsyncWorkerThreads")
    public ExecutorService asyncExecutorService(int numAsyncWorkerThreads) {
        return Executors.newFixedThreadPool(numAsyncWorkerThreads);
    }

    @Bean(name = "CmsEncryptorCache")
    @Autowired
    public LoadingCache<String, CmsEncryptor> cmsEncryptorCache(CmsEncryptorCacheLoader cacheLoader) {
        return CacheBuilder.newBuilder().build(cacheLoader);
    }

    @Bean(name = "s3CmsHelper")
    @Resource(name = "s3CmsClient")
    public S3Helper s3CmsHelper(AmazonS3Client s3CmsClient) {
        S3Helper s3CmsHelper = new S3Helper();
        s3CmsHelper.setS3Client(s3CmsClient);
        return s3CmsHelper;
    }

    @Bean(name = "UploadDdbMapper")
    @Autowired
    public DynamoDBMapper uploadDdbMapper(AmazonDynamoDB client) {
        DynamoDBMapperConfig mapperConfig = new DynamoDBMapperConfig.Builder().withSaveBehavior(
                DynamoDBMapperConfig.SaveBehavior.UPDATE)
                .withConsistentReads(DynamoDBMapperConfig.ConsistentReads.CONSISTENT)
                .withTableNameOverride(TableNameOverrideFactory.getTableNameOverride(DynamoUpload2.class)).build();
        return new DynamoDBMapper(client, mapperConfig);
    }

    // TODO: Remove this when the migration is done
    @Bean(name = "UploadDdbMapperOld")
    @Autowired
    public DynamoDBMapper uploadDdbMapperOld(AmazonDynamoDB client) {
        DynamoDBMapperConfig mapperConfig = new DynamoDBMapperConfig.Builder().withSaveBehavior(
                DynamoDBMapperConfig.SaveBehavior.UPDATE)
                .withConsistentReads(DynamoDBMapperConfig.ConsistentReads.CONSISTENT)
                .withTableNameOverride(TableNameOverrideFactory.getTableNameOverride(DynamoUpload.class)).build();
        return new DynamoDBMapper(client, mapperConfig);
    }

    @Bean(name = "UploadSchemaDdbMapper")
    @Autowired
    public DynamoDBMapper uploadSchemaDdbMapper(AmazonDynamoDB client) {
        DynamoDBMapperConfig mapperConfig = new DynamoDBMapperConfig.Builder()
                .withTableNameOverride(TableNameOverrideFactory.getTableNameOverride(DynamoUploadSchema.class))
                .build();
        return new DynamoDBMapper(client, mapperConfig);
    }
}
