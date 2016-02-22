package org.sagebionetworks.bridge.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.MissingResourceException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.Resource;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.PredefinedClientConfigurations;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClient;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.stormpath.sdk.api.ApiKey;
import com.stormpath.sdk.api.ApiKeys;
import com.stormpath.sdk.application.Application;
import com.stormpath.sdk.client.Client;
import com.stormpath.sdk.client.ClientBuilder;
import com.stormpath.sdk.client.Clients;
import com.stormpath.sdk.impl.client.DefaultClientBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import org.sagebionetworks.bridge.crypto.AesGcmEncryptor;
import org.sagebionetworks.bridge.crypto.BridgeEncryptor;
import org.sagebionetworks.bridge.crypto.CmsEncryptor;
import org.sagebionetworks.bridge.crypto.CmsEncryptorCacheLoader;
import org.sagebionetworks.bridge.dynamodb.DynamoHealthDataAttachment;
import org.sagebionetworks.bridge.dynamodb.DynamoHealthDataRecord;
import org.sagebionetworks.bridge.dynamodb.DynamoIndexHelper;
import org.sagebionetworks.bridge.dynamodb.DynamoMpowerVisualization;
import org.sagebionetworks.bridge.dynamodb.DynamoSchedulePlan;
import org.sagebionetworks.bridge.dynamodb.DynamoStudyConsent1;
import org.sagebionetworks.bridge.dynamodb.DynamoSurvey;
import org.sagebionetworks.bridge.dynamodb.DynamoSurveyElement;
import org.sagebionetworks.bridge.dynamodb.DynamoSurveyResponse;
import org.sagebionetworks.bridge.dynamodb.DynamoScheduledActivity;
import org.sagebionetworks.bridge.dynamodb.DynamoSubpopulation;
import org.sagebionetworks.bridge.dynamodb.DynamoActivityEvent;
import org.sagebionetworks.bridge.dynamodb.DynamoCriteria;
import org.sagebionetworks.bridge.dynamodb.DynamoFPHSExternalIdentifier;
import org.sagebionetworks.bridge.dynamodb.DynamoUpload2;
import org.sagebionetworks.bridge.dynamodb.DynamoUploadDedupe;
import org.sagebionetworks.bridge.dynamodb.DynamoUploadSchema;
import org.sagebionetworks.bridge.dynamodb.DynamoUserConsent3;
import org.sagebionetworks.bridge.dynamodb.DynamoUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.redis.JedisOps;
import org.sagebionetworks.bridge.s3.S3Helper;
import org.sagebionetworks.bridge.upload.DecryptHandler;
import org.sagebionetworks.bridge.upload.IosSchemaValidationHandler2;
import org.sagebionetworks.bridge.upload.ParseJsonHandler;
import org.sagebionetworks.bridge.upload.S3DownloadHandler;
import org.sagebionetworks.bridge.upload.StrictValidationHandler;
import org.sagebionetworks.bridge.upload.TranscribeConsentHandler;
import org.sagebionetworks.bridge.upload.UnzipHandler;
import org.sagebionetworks.bridge.upload.UploadArtifactsHandler;
import org.sagebionetworks.bridge.upload.UploadValidationHandler;

@ComponentScan({"org.sagebionetworks.bridge"})
@Configuration
public class BridgeSpringConfig {

    private static Logger logger = LoggerFactory.getLogger(BridgeSpringConfig.class);
    private static final List<String> REDIS_PROVIDERS = Lists.newArrayList("REDISCLOUD_URL", "REDISTOGO_URL");
    
    @Bean(name = "bridgeObjectMapper")
    public BridgeObjectMapper bridgeObjectMapper() {
        return BridgeObjectMapper.get();
    }

    @Bean(name = "bridgeConfig")
    public BridgeConfig bridgeConfig() {
        return BridgeConfigFactory.getConfig();
    }

    @Bean(name = "jedisPool")
    public JedisPool jedisPool(final BridgeConfig config) throws Exception {
        // Configure pool
        final JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(config.getPropertyAsInt("redis.max.total"));

        // Create pool.
        final String url = getRedisURL(config);
        final JedisPool jedisPool = constructJedisPool(url, poolConfig, config);
        
        // Test pool
        try (Jedis jedis = jedisPool.getResource()) {
            final String result = jedis.ping();
            if (result == null || !"PONG".equalsIgnoreCase(result.trim())) {
                throw new MissingResourceException("No PONG from PINGing Redis" + result + ".",
                        JedisPool.class.getName(), jedis.getClient().getHost() + ":" + jedis.getClient().getPort());
            }
        }

        // Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                jedisPool.destroy();
            }
        }));

        return jedisPool;
    }
    
    /**
     * Try Redis providers to find one that is provisioned. Using this URL in the environment variables 
     * is the documented way to interact with these services.
     * @param config
     * @return
     */
    private String getRedisURL(final BridgeConfig config) {
        for (String provider : REDIS_PROVIDERS) {
            if (System.getenv(provider) != null) {
                logger.info("Using Redis Provider: " + provider);
                return System.getenv(provider);
            }
        }
        logger.info("Using Redis Provider: redis.url");
        return config.getProperty("redis.url");
    }
    
    private JedisPool constructJedisPool(final String url, final JedisPoolConfig poolConfig, final BridgeConfig config)
            throws URISyntaxException {
        
        // With changes in Redis provisioning, passwords are now parseable by Java's URI class.
        URI redisURI = new URI(url);
        String password = redisURI.getUserInfo().split(":",2)[1];
        
        if (config.isLocal()) {
            return new JedisPool(poolConfig, redisURI.getHost(), redisURI.getPort(),
                    config.getPropertyAsInt("redis.timeout"));
        } else {
            return new JedisPool(poolConfig, redisURI.getHost(), redisURI.getPort(),
                    config.getPropertyAsInt("redis.timeout"), password);
        }
    }

    @Bean(name = "jedisOps")
    @Resource(name = "jedisPool")
    public JedisOps jedisOps(final JedisPool jedisPool) {
        return new JedisOps(jedisPool);
    }

    @Bean(name = "healthCodeEncryptor")
    @Resource(name = "bridgeConfig")
    public BridgeEncryptor healthCodeEncryptor(BridgeConfig bridgeConfig) {
        return new BridgeEncryptor(new AesGcmEncryptor(bridgeConfig.getHealthCodeKey()));
    }

    @Bean(name = "awsCredentials")
    public BasicAWSCredentials awsCredentials() {
        BridgeConfig bridgeConfig = bridgeConfig();
        return new BasicAWSCredentials(bridgeConfig.getProperty("aws.key"),
                bridgeConfig.getProperty("aws.secret.key"));
    }

    @Bean(name = "mpowerVisualizationDdbMapper")
    public DynamoDBMapper mpowerVisualizationDdbMapper() {
        return DynamoUtils.getMapper(DynamoMpowerVisualization.class, bridgeConfig(), dynamoDbClient());
    }

    @Bean(name = "s3UploadCredentials")
    @Resource(name = "bridgeConfig")
    public BasicAWSCredentials s3UploadCredentials(BridgeConfig bridgeConfig) {
        return new BasicAWSCredentials(bridgeConfig.getProperty("aws.key.upload"),
                bridgeConfig.getProperty("aws.secret.key.upload"));
    }

    @Bean(name = "s3CmsCredentials")
    @Resource(name = "bridgeConfig")
    public BasicAWSCredentials s3CmsCredentials(BridgeConfig bridgeConfig) {
        return new BasicAWSCredentials(bridgeConfig.getProperty("aws.key.upload.cms"),
                bridgeConfig.getProperty("aws.secret.key.upload.cms"));
    }

    @Bean(name = "dynamoDbClient")
    public AmazonDynamoDBClient dynamoDbClient() {
        int maxRetries = bridgeConfig().getPropertyAsInt("ddb.max.retries");
        ClientConfiguration awsClientConfig = PredefinedClientConfigurations.dynamoDefault()
                .withMaxErrorRetry(maxRetries);
        return new AmazonDynamoDBClient(awsCredentials(), awsClientConfig);
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

    @Bean(name = "s3ConsentsCredentials")
    @Resource(name = "bridgeConfig")
    public BasicAWSCredentials s3ConsentsCredentials(BridgeConfig bridgeConfig) {
        return new BasicAWSCredentials(bridgeConfig.getProperty("aws.key.consents"),
                bridgeConfig.getProperty("aws.secret.key.consents"));
    }

    @Bean(name = "s3ConsentsClient")
    @Resource(name = "s3ConsentsCredentials")
    public AmazonS3Client s3ConsentsClient(BasicAWSCredentials awsCredentials) {
        return new AmazonS3Client(awsCredentials);
    }

    @Bean(name = "s3ConsentsHelper")
    @Resource(name = "s3ConsentsClient")
    public S3Helper s3ConsentsHelper(AmazonS3Client s3Client) {
        S3Helper s3Helper = new S3Helper();
        s3Helper.setS3Client(s3Client);
        return s3Helper;
    }

    @Bean(name = "sesClient")
    @Resource(name="awsCredentials")
    public AmazonSimpleEmailServiceClient sesClient(BasicAWSCredentials awsCredentials) {
        return new AmazonSimpleEmailServiceClient(awsCredentials);
    }

    @Bean(name = "sqsClient")
    @Resource(name = "awsCredentials")
    public AmazonSQSClient sqsClient(BasicAWSCredentials awsCredentials) {
        return new AmazonSQSClient(awsCredentials);
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
    public DynamoDBMapper healthDataAttachmentDdbMapper(final BridgeConfig bridgeConfig, final AmazonDynamoDB client) {
        return DynamoUtils.getMapper(DynamoHealthDataAttachment.class, bridgeConfig, client);
    }

    @Bean(name = "healthDataDdbMapper")
    @Autowired
    public DynamoDBMapper healthDataDdbMapper(final BridgeConfig bridgeConfig, final AmazonDynamoDB client) {
        return DynamoUtils.getMapper(DynamoHealthDataRecord.class, bridgeConfig, client);
    }

    @Bean(name = "activityEventDdbMapper")
    @Autowired
    public DynamoDBMapper activityEventDdbMapper(final BridgeConfig bridgeConfig, final AmazonDynamoDB client) {
        return DynamoUtils.getMapper(DynamoActivityEvent.class, bridgeConfig, client);
    }

    @Bean(name = "studyConsentDdbMapper")
    @Autowired
    public DynamoDBMapper studyConsentDdbMapper(final BridgeConfig bridgeConfig, final AmazonDynamoDB client) {
        return DynamoUtils.getMapper(DynamoStudyConsent1.class, bridgeConfig, client);
    }

    @Bean(name = "subpopulationDdbMapper")
    @Autowired
    public DynamoDBMapper subpopulationDdbMapper(final BridgeConfig bridgeConfig, final AmazonDynamoDB client) {
        return DynamoUtils.getMapper(DynamoSubpopulation.class, bridgeConfig, client);
    }
    
    @Bean(name = "surveyMapper")
    @Autowired
    public DynamoDBMapper surveyDdbMapper(final BridgeConfig bridgeConfig, final AmazonDynamoDB client) {
        return DynamoUtils.getMapper(DynamoSurvey.class, bridgeConfig, client);
    }

    @Bean(name = "surveyElementMapper")
    @Autowired
    public DynamoDBMapper surveyElementDdbMapper(final BridgeConfig bridgeConfig, final AmazonDynamoDB client) {
        return DynamoUtils.getMapper(DynamoSurveyElement.class, bridgeConfig, client);
    }

    @Bean(name = "criteriaMapper")
    @Autowired
    public DynamoDBMapper criteriaMapper(final BridgeConfig bridgeConfig, final AmazonDynamoDB client) {
        return DynamoUtils.getMapper(DynamoCriteria.class, bridgeConfig, client);
    }
    
    @Bean(name = "schedulePlanMapper")
    @Autowired
    public DynamoDBMapper schedulePlanMapper(final BridgeConfig bridgeConfig, final AmazonDynamoDB client) {
        return DynamoUtils.getMapper(DynamoSchedulePlan.class, bridgeConfig, client);
    }
    
    @Bean(name = "healthDataHealthCodeIndex")
    @Autowired
    public DynamoIndexHelper healthDataHealthCodeIndex(final BridgeConfig bridgeConfig, final AmazonDynamoDB client) {
        return DynamoIndexHelper.create(DynamoHealthDataRecord.class, "healthCode-index", bridgeConfig, client);
    }

    @Bean(name = "healthDataUploadDateIndex")
    @Autowired
    public DynamoIndexHelper healthDataUploadDateIndex(final BridgeConfig bridgeConfig, final AmazonDynamoDB client) {
        return DynamoIndexHelper.create(DynamoHealthDataRecord.class, "uploadDate-index", bridgeConfig, client);
    }

    @Bean(name = "uploadDdbMapper")
    @Autowired
    public DynamoDBMapper uploadDdbMapper(final BridgeConfig bridgeConfig, final AmazonDynamoDB client) {
        return DynamoUtils.getMapper(DynamoUpload2.class, bridgeConfig, client);
    }

    @Bean(name = "uploadDedupeDdbMapper")
    public DynamoDBMapper uploadDedupeDdbMapper() {
        return DynamoUtils.getMapper(DynamoUploadDedupe.class, bridgeConfig(), dynamoDbClient());
    }
    
    @Bean(name = "fphsExternalIdDdbMapper")
    @Autowired
    public DynamoDBMapper fphsExternalIdDdbMapper(final BridgeConfig bridgeConfig, final AmazonDynamoDB client) {
        return DynamoUtils.getMapper(DynamoFPHSExternalIdentifier.class, bridgeConfig, client);
    }

    @Bean(name = "uploadValidationHandlerList")
    @Autowired
    public List<UploadValidationHandler> uploadValidationHandlerList(S3DownloadHandler s3DownloadHandler,
            DecryptHandler decryptHandler, UnzipHandler unzipHandler, ParseJsonHandler parseJsonHandler,
            IosSchemaValidationHandler2 iosSchemaValidationHandler2,
            StrictValidationHandler strictValidationHandler, TranscribeConsentHandler transcribeConsentHandler,
            UploadArtifactsHandler uploadArtifactsHandler) {
        return ImmutableList.of(s3DownloadHandler, decryptHandler, unzipHandler, parseJsonHandler,
                iosSchemaValidationHandler2, strictValidationHandler, transcribeConsentHandler,
                uploadArtifactsHandler);
    }

    @Bean(name = "uploadSchemaDdbMapper")
    @Autowired
    public DynamoDBMapper uploadSchemaDdbMapper(final BridgeConfig bridgeConfig, final AmazonDynamoDB client) {
        return DynamoUtils.getMapper(DynamoUploadSchema.class, bridgeConfig, client);
    }

    @Bean(name = "activityDdbMapper")
    @Autowired
    public DynamoDBMapper activityDdbMapper(final BridgeConfig bridgeConfig, final AmazonDynamoDB client) {
        return DynamoUtils.getMapper(DynamoScheduledActivity.class, bridgeConfig, client);
    }
    
    @Bean(name = "activitySchedulePlanGuidIndex")
    @Autowired
    public DynamoIndexHelper activitySchedulePlanGuidIndex(final BridgeConfig bridgeConfig, final AmazonDynamoDB client) {
        return DynamoIndexHelper.create(DynamoScheduledActivity.class, "schedulePlanGuid-index", bridgeConfig, client);
    }

    @Bean(name = "surveyResponseDdbMapper")
    @Autowired
    public DynamoDBMapper surveyResponseDdbMapper(final BridgeConfig bridgeConfig, final AmazonDynamoDB client) {
        return DynamoUtils.getMapper(DynamoSurveyResponse.class, bridgeConfig, client);
    }
    
    @Bean(name = "userConsentDdbMapper")
    @Autowired
    public DynamoDBMapper userConsentDdbMapper(final BridgeConfig bridgeConfig, final AmazonDynamoDB client) {
        return DynamoUtils.getMapper(DynamoUserConsent3.class, bridgeConfig, client);
    }
    
    @Bean(name = "uploadSchemaStudyIdIndex")
    @Autowired
    public DynamoIndexHelper uploadSchemaStudyIdIndex(final BridgeConfig bridgeConfig, final AmazonDynamoDB client) {
        return DynamoIndexHelper.create(DynamoUploadSchema.class, "studyId-index", bridgeConfig, client);
    }

    // Do NOT reference this bean outside of StormpathAccountDao. Injected for testing purposes.
    @Bean(name = "stormpathClient")
    @Autowired
    public Client getStormpathClient(BridgeConfig bridgeConfig) {
        ApiKey apiKey = ApiKeys.builder()
            .setId(bridgeConfig.getStormpathId())
            .setSecret(bridgeConfig.getStormpathSecret()).build();
        ClientBuilder clientBuilder = Clients.builder().setApiKey(apiKey);
        ((DefaultClientBuilder)clientBuilder).setBaseUrl("https://enterprise.stormpath.io/v1");
        return clientBuilder.build();        
    }

    // Do NOT reference this bean outside of StormpathAccountDao. Injected for testing purposes.
    @Bean(name = "stormpathApplication")
    @Autowired
    public Application getStormpathApplication(BridgeConfig bridgeConfig, Client stormpathClient) {
        return stormpathClient.getResource(bridgeConfig.getStormpathApplicationHref(), Application.class);
    }
}
