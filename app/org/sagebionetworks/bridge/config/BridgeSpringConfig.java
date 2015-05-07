package org.sagebionetworks.bridge.config;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.Resource;

import org.sagebionetworks.bridge.crypto.AesGcmEncryptor;
import org.sagebionetworks.bridge.crypto.CmsEncryptor;
import org.sagebionetworks.bridge.crypto.CmsEncryptorCacheLoader;
import org.sagebionetworks.bridge.dynamodb.DynamoHealthDataAttachment;
import org.sagebionetworks.bridge.dynamodb.DynamoHealthDataRecord;
import org.sagebionetworks.bridge.dynamodb.DynamoIndexHelper;
import org.sagebionetworks.bridge.dynamodb.DynamoSurveyResponse;
import org.sagebionetworks.bridge.dynamodb.DynamoTask;
import org.sagebionetworks.bridge.dynamodb.DynamoTaskEvent;
import org.sagebionetworks.bridge.dynamodb.DynamoUpload2;
import org.sagebionetworks.bridge.dynamodb.DynamoUploadSchema;
import org.sagebionetworks.bridge.dynamodb.TableNameOverrideFactory;
import org.sagebionetworks.bridge.s3.S3Helper;
import org.sagebionetworks.bridge.upload.DecryptHandler;
import org.sagebionetworks.bridge.upload.IosSchemaContextValidator;
import org.sagebionetworks.bridge.upload.IosSchemaValidationHandler;
import org.sagebionetworks.bridge.upload.IosSchemaValidationHandler2;
import org.sagebionetworks.bridge.upload.ParseJsonHandler;
import org.sagebionetworks.bridge.upload.S3DownloadHandler;
import org.sagebionetworks.bridge.upload.TestingHandler;
import org.sagebionetworks.bridge.upload.TranscribeConsentHandler;
import org.sagebionetworks.bridge.upload.UnzipHandler;
import org.sagebionetworks.bridge.upload.UploadArtifactsHandler;
import org.sagebionetworks.bridge.upload.UploadValidationHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Index;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClient;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.stormpath.sdk.api.ApiKey;
import com.stormpath.sdk.api.ApiKeys;
import com.stormpath.sdk.application.Application;
import com.stormpath.sdk.client.Client;
import com.stormpath.sdk.client.ClientBuilder;
import com.stormpath.sdk.client.Clients;
import com.stormpath.sdk.impl.client.DefaultClientBuilder;

@ComponentScan({"controllers","filters","interceptors","models","org.sagebionetworks.bridge"})
@Configuration
public class BridgeSpringConfig {
    
    @Bean(name = "healthCodeEncryptor")
    @Resource(name = "bridgeConfig")
    public AesGcmEncryptor healthCodeEncryptor(BridgeConfig bridgeConfig) {
        return new AesGcmEncryptor(bridgeConfig.getHealthCodeKey());
    }
    
    @Bean(name = "awsCredentials")
    @Resource(name = "bridgeConfig")
    public BasicAWSCredentials awsCredentials(BridgeConfig bridgeConfig) {
        return new BasicAWSCredentials(bridgeConfig.getProperty("aws.key"), bridgeConfig.getProperty("aws.secret.key"));
    }
    
    @Bean(name = "s3UploadCredentials")
    @Resource(name = "bridgeConfig")
    public BasicAWSCredentials s3UploadCredentials(BridgeConfig bridgeConfig) {
        return new BasicAWSCredentials(bridgeConfig.getProperty("aws.key.upload"), bridgeConfig.getProperty("aws.secret.key.upload"));
    }
    
    @Bean(name = "s3CmsCredentials")
    @Resource(name = "bridgeConfig")
    public BasicAWSCredentials s3CmsCredentials(BridgeConfig bridgeConfig) {
        return new BasicAWSCredentials(bridgeConfig.getProperty("aws.key.upload.cms"), bridgeConfig.getProperty("aws.secret.key.upload.cms"));
    }
    
    @Bean(name = "dynamoDbClient")
    @Resource(name = "awsCredentials")
    public AmazonDynamoDBClient dynamoDbClient(BasicAWSCredentials awsCredentials) {
        return new AmazonDynamoDBClient(awsCredentials);
    }
    
    @Bean(name = "s3Client")
    @Resource(name = "awsCredentials")
    public AmazonS3Client s3Client(BasicAWSCredentials awsCredentials) {
        return new AmazonS3Client(awsCredentials);
    }
    
    @Bean(name = "s3UploadClient")
    @Resource(name = "s3UploadCredentials")
    public AmazonS3Client s3UploadClient(BasicAWSCredentials s3UploadCredentials) {
        return new AmazonS3Client(s3UploadCredentials);
    }

    @Bean(name = "s3CmsClient")
    @Resource(name = "s3CmsCredentials")
    public AmazonS3Client s3CmsClient(BasicAWSCredentials s3CmsCredentials) {
        return new AmazonS3Client(s3CmsCredentials);
    }
    
    @Bean(name ="uploadTokenServiceClient")
    @Resource(name = "s3UploadCredentials")
    public AWSSecurityTokenServiceClient uploadTokenServiceClient(BasicAWSCredentials s3UploadCredentials) {
        return new AWSSecurityTokenServiceClient(s3UploadCredentials);
    }

    @Bean(name = "bridgeConfig")
    public BridgeConfig bridgeConfig() {
        return BridgeConfigFactory.getConfig();
    }

    @Bean(name = "asyncExecutorService")
    @Resource(name = "bridgeConfig")
    public ExecutorService asyncExecutorService(BridgeConfig bridgeConfig) {
        return Executors.newFixedThreadPool(bridgeConfig.getPropertyAsInt("async.worker.thread.count"));
    }

    @Bean(name = "supportEmail")
    @Resource(name = "bridgeConfig")
    public String supportEmail(BridgeConfig bridgeConfig) {
        return bridgeConfig.getProperty("support.email");
    }

    @Bean(name = "cmsEncryptorCache")
    @Autowired
    public LoadingCache<String, CmsEncryptor> cmsEncryptorCache(CmsEncryptorCacheLoader cacheLoader) {
        return CacheBuilder.newBuilder().build(cacheLoader);
    }

    @Bean(name = "healthDataAttachmentDdbMapper")
    @Autowired
    public DynamoDBMapper healthDataAttachmentDdbMapper(AmazonDynamoDB client) {
        return getMapperForClass(client, DynamoHealthDataAttachment.class);
    }

    @Bean(name = "healthDataDdbMapper")
    @Autowired
    public DynamoDBMapper healthDataDdbMapper(AmazonDynamoDB client) {
        return getMapperForClass(client, DynamoHealthDataRecord.class);
    }

    @Bean(name = "taskEventDdbMapper")
    @Autowired
    public DynamoDBMapper taskEventDdbMapper(AmazonDynamoDB client) {
        return getMapperForClass(client, DynamoTaskEvent.class);
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
    public JedisPool jedisPool(BridgeConfig config) {
        // configure Jedis pool
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
        return getMapperForClass(client, DynamoUpload2.class);
    }

    @Bean(name = "uploadValidationHandlerList")
    @Autowired
    public List<UploadValidationHandler> uploadValidationHandlerList(S3DownloadHandler s3DownloadHandler,
            DecryptHandler decryptHandler, UnzipHandler unzipHandler, ParseJsonHandler parseJsonHandler,
            IosSchemaValidationHandler iosSchemaValidationHandler,
            IosSchemaValidationHandler2 iosSchemaValidationHandler2, TranscribeConsentHandler transcribeConsentHandler,
            UploadArtifactsHandler uploadArtifactsHandler) {

        // iOS schema v1 vs v2 test
        TestingHandler iosSchemaTestingHandler = new TestingHandler();
        iosSchemaTestingHandler.setContextValidator(IosSchemaContextValidator.INSTANCE);
        iosSchemaTestingHandler.setProductionHandler(iosSchemaValidationHandler);
        iosSchemaTestingHandler.setTestHandler(iosSchemaValidationHandler2);

        return ImmutableList.of(s3DownloadHandler, decryptHandler, unzipHandler, parseJsonHandler,
                iosSchemaTestingHandler, transcribeConsentHandler, uploadArtifactsHandler);
    }

    @Bean(name = "uploadSchemaDdbMapper")
    @Autowired
    public DynamoDBMapper uploadSchemaDdbMapper(AmazonDynamoDB client) {
        DynamoDBMapperConfig mapperConfig = new DynamoDBMapperConfig.Builder()
                .withTableNameOverride(TableNameOverrideFactory.getTableNameOverride(DynamoUploadSchema.class))
                .build();
        return new DynamoDBMapper(client, mapperConfig);
    }

    @Bean(name = "taskDdbMapper")
    @Autowired
    public DynamoDBMapper taskDdbMapper(AmazonDynamoDB client) {
        return getMapperForClass(client, DynamoTask.class);
    }
    
    @Bean(name = "surveyResponseDdbMapper")
    @Autowired
    public DynamoDBMapper surveyResponseDdbMapper(AmazonDynamoDB client) {
        return getMapperForClass(client, DynamoSurveyResponse.class);
    }
    
    @Bean(name = "uploadSchemaStudyIdIndex")
    @Autowired
    public DynamoIndexHelper uploadSchemaStudyIdIndex(AmazonDynamoDB client) {
        // DDB index
        DynamoDBMapperConfig.TableNameOverride tableNameOverride = TableNameOverrideFactory.getTableNameOverride(
                DynamoUploadSchema.class);
        DynamoDB ddb = new DynamoDB(client);
        Table ddbTable = ddb.getTable(tableNameOverride.getTableName());
        Index ddbIndex = ddbTable.getIndex("studyId-index");

        // construct index helper
        DynamoIndexHelper indexHelper = new DynamoIndexHelper();
        indexHelper.setIndex(ddbIndex);
        indexHelper.setMapper(uploadSchemaDdbMapper(client));
        return indexHelper;
    }

    // Do NOT reference this bean outside of StormpathAccountDao. Injected for testing purposes.
    @Bean(name = "stormpathClient")
    @Autowired
    public Client getStormpathClient(BridgeConfig bridgeConfig) {
        ApiKey apiKey = ApiKeys.builder()
            .setId(bridgeConfig.getStormpathId().trim())
            .setSecret(bridgeConfig.getStormpathSecret().trim()).build();
        
        ClientBuilder clientBuilder = Clients.builder().setApiKey(apiKey);
        ((DefaultClientBuilder)clientBuilder).setBaseUrl("https://enterprise.stormpath.io/v1");
        return clientBuilder.build();        
    }

    // Do NOT reference this bean outside of StormpathAccountDao. Injected for testing purposes.
    @Bean(name = "stormpathApplication")
    @Autowired
    public Application getStormpathApplication(BridgeConfig bridgeConfig, Client stormpathClient) {
        return stormpathClient.getResource(bridgeConfig.getStormpathApplicationHref().trim(), Application.class);
    }
    
    @Bean(name = "sesClient")
    @Resource(name="awsCredentials")
    public AmazonSimpleEmailServiceClient sesClient(BasicAWSCredentials awsCredentials) {
        return new AmazonSimpleEmailServiceClient(awsCredentials);
    }
    
    private static DynamoDBMapper getMapperForClass(AmazonDynamoDB client, Class<?> clazz) {
        DynamoDBMapperConfig mapperConfig = new DynamoDBMapperConfig.Builder()
                .withSaveBehavior(DynamoDBMapperConfig.SaveBehavior.UPDATE)
                .withConsistentReads(DynamoDBMapperConfig.ConsistentReads.CONSISTENT)
                .withTableNameOverride(TableNameOverrideFactory.getTableNameOverride(clazz)).build();
        return new DynamoDBMapper(client, mapperConfig);
    }
}
