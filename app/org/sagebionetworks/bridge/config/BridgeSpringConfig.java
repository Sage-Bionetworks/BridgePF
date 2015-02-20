package org.sagebionetworks.bridge.config;

import javax.annotation.Resource;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Index;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.s3.AmazonS3Client;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import org.sagebionetworks.bridge.crypto.CmsEncryptor;
import org.sagebionetworks.bridge.crypto.CmsEncryptorCacheLoader;
import org.sagebionetworks.bridge.dynamodb.DynamoHealthDataRecord;
import org.sagebionetworks.bridge.dynamodb.DynamoIndexHelper;
import org.sagebionetworks.bridge.dynamodb.DynamoUpload;
import org.sagebionetworks.bridge.dynamodb.DynamoUpload2;
import org.sagebionetworks.bridge.dynamodb.DynamoUploadSchema;
import org.sagebionetworks.bridge.dynamodb.TableNameOverrideFactory;
import org.sagebionetworks.bridge.s3.S3Helper;
import org.sagebionetworks.bridge.upload.DecryptHandler;
import org.sagebionetworks.bridge.upload.ParseJsonHandler;
import org.sagebionetworks.bridge.upload.S3DownloadHandler;
import org.sagebionetworks.bridge.upload.UnzipHandler;
import org.sagebionetworks.bridge.upload.UploadValidationHandler;

@ComponentScan(basePackages = "org.sagebionetworks.bridge")
@Configuration
public class BridgeSpringConfig {
    @Bean(name = "asyncExecutorService")
    @Resource(name = "numAsyncWorkerThreads")
    public ExecutorService asyncExecutorService(Integer numAsyncWorkerThreads) {
        return Executors.newFixedThreadPool(numAsyncWorkerThreads);
    }

    @Bean(name = "cmsEncryptorCache")
    @Autowired
    public LoadingCache<String, CmsEncryptor> cmsEncryptorCache(CmsEncryptorCacheLoader cacheLoader) {
        return CacheBuilder.newBuilder().build(cacheLoader);
    }

    @Bean(name = "healthDataDdbMapper")
    @Autowired
    public DynamoDBMapper healthDataDdbMapper(AmazonDynamoDB client) {
        DynamoDBMapperConfig mapperConfig = new DynamoDBMapperConfig.Builder()
                .withSaveBehavior(DynamoDBMapperConfig.SaveBehavior.UPDATE)
                .withConsistentReads(DynamoDBMapperConfig.ConsistentReads.CONSISTENT)
                .withTableNameOverride(TableNameOverrideFactory.getTableNameOverride(DynamoHealthDataRecord.class))
                .build();
        return new DynamoDBMapper(client, mapperConfig);
    }

    @Bean(name = "healthDataHealthCodeIndex")
    @Autowired
    public DynamoIndexHelper healthDataHealthCodeIndex(AmazonDynamoDB client) {
        // DDB index
        String tableName = TableNameOverrideFactory.getTableName(DynamoHealthDataRecord.class);
        DynamoDB ddb = new DynamoDB(client);
        Table ddbTable = ddb.getTable(tableName);
        Index ddbIndex = ddbTable.getIndex("healthCode-index");

        // construct index helper
        DynamoIndexHelper indexHelper = new DynamoIndexHelper();
        indexHelper.setIndex(ddbIndex);
        indexHelper.setMapper(healthDataDdbMapper(client));
        return indexHelper;
    }

    @Bean(name = "healthDataUploadDateIndex")
    @Autowired
    public DynamoIndexHelper healthDataUploadDateIndex(AmazonDynamoDB client) {
        // DDB index
        String tableName = TableNameOverrideFactory.getTableName(DynamoHealthDataRecord.class);
        DynamoDB ddb = new DynamoDB(client);
        Table ddbTable = ddb.getTable(tableName);
        Index ddbIndex = ddbTable.getIndex("uploadDate-index");

        // construct index helper
        DynamoIndexHelper indexHelper = new DynamoIndexHelper();
        indexHelper.setIndex(ddbIndex);
        indexHelper.setMapper(healthDataDdbMapper(client));
        return indexHelper;
    }

    @Bean(name = "jedisPool")
    public JedisPool jedisPool() {
        // configure Jedis pool
        BridgeConfig config = BridgeConfigFactory.getConfig();
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(config.getPropertyAsInt("redis.max.total"));
        String host = config.getProperty("redis.host");
        int port = config.getPropertyAsInt("redis.port");
        int timeout = config.getPropertyAsInt("redis.timeout");

        // create Jedis pool
        final JedisPool jedisPool;
        if (config.isLocal()) {
            jedisPool = new JedisPool(poolConfig, host, port, timeout);
        } else {
            String password = config.getProperty("redis.password");
            jedisPool = new JedisPool(poolConfig, host, port, timeout, password);
        }

        // configure shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                jedisPool.destroy();
            }
        }));

        return jedisPool;
    }

    @Bean(name = "s3CmsHelper")
    @Resource(name = "s3CmsClient")
    public S3Helper s3CmsHelper(AmazonS3Client s3CmsClient) {
        S3Helper s3CmsHelper = new S3Helper();
        s3CmsHelper.setS3Client(s3CmsClient);
        return s3CmsHelper;
    }

    @Bean(name = "s3Helper")
    @Resource(name = "s3Client")
    public S3Helper s3Helper(AmazonS3Client s3Client) {
        S3Helper s3Helper = new S3Helper();
        s3Helper.setS3Client(s3Client);
        return s3Helper;
    }

    @Bean(name = "uploadDdbMapper")
    @Autowired
    public DynamoDBMapper uploadDdbMapper(AmazonDynamoDB client) {
        DynamoDBMapperConfig mapperConfig = new DynamoDBMapperConfig.Builder().withSaveBehavior(
                DynamoDBMapperConfig.SaveBehavior.UPDATE)
                .withConsistentReads(DynamoDBMapperConfig.ConsistentReads.CONSISTENT)
                .withTableNameOverride(TableNameOverrideFactory.getTableNameOverride(DynamoUpload2.class)).build();
        return new DynamoDBMapper(client, mapperConfig);
    }

    // TODO: Remove this when the migration is done
    @Bean(name = "uploadDdbMapperOld")
    @Autowired
    public DynamoDBMapper uploadDdbMapperOld(AmazonDynamoDB client) {
        DynamoDBMapperConfig mapperConfig = new DynamoDBMapperConfig.Builder().withSaveBehavior(
                DynamoDBMapperConfig.SaveBehavior.UPDATE)
                .withConsistentReads(DynamoDBMapperConfig.ConsistentReads.CONSISTENT)
                .withTableNameOverride(TableNameOverrideFactory.getTableNameOverride(DynamoUpload.class)).build();
        return new DynamoDBMapper(client, mapperConfig);
    }

    @Bean(name = "uploadValidationHandlerList")
    @Autowired
    public List<UploadValidationHandler> uploadValidationHandlerList(S3DownloadHandler s3DownloadHandler,
            DecryptHandler decryptHandler, UnzipHandler unzipHandler, ParseJsonHandler parseJsonHandler) {
        // TODO: add handlers for the following:
        // * validate against schemas
        // * write intermediate artifacts
        return ImmutableList.of(s3DownloadHandler, decryptHandler, unzipHandler, parseJsonHandler);
    }

    @Bean(name = "uploadSchemaDdbMapper")
    @Autowired
    public DynamoDBMapper uploadSchemaDdbMapper(AmazonDynamoDB client) {
        DynamoDBMapperConfig mapperConfig = new DynamoDBMapperConfig.Builder()
                .withTableNameOverride(TableNameOverrideFactory.getTableNameOverride(DynamoUploadSchema.class))
                .build();
        return new DynamoDBMapper(client, mapperConfig);
    }
}
