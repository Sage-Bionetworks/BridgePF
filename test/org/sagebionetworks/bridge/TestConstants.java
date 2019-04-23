package org.sagebionetworks.bridge;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.studies.AndroidAppLink;
import org.sagebionetworks.bridge.models.studies.AppleAppLink;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

public class TestConstants {
    
    public static final String ENCRYPTED_HEALTH_CODE = "TFMkaVFKPD48WissX0bgcD3esBMEshxb3MVgKxHnkXLSEPN4FQMKc01tDbBAVcXx94kMX6ckXVYUZ8wx4iICl08uE+oQr9gorE1hlgAyLAM=";

    public static final String TEST_STUDY_IDENTIFIER = "api";
    public static final StudyIdentifier TEST_STUDY = new StudyIdentifierImpl(TEST_STUDY_IDENTIFIER);
    public static final CriteriaContext TEST_CONTEXT = new CriteriaContext.Builder()
            .withUserId("user-id").withStudyIdentifier(TestConstants.TEST_STUDY).build();

    public static final String EMAIL = "email@email.com";
    public static final String PASSWORD = "password";

    /**
     * During tests, must sometimes pause because the underlying query uses a DynamoDB global 
     * secondary index, and this does not currently support consistent reads.
     */
    public static final int GSI_WAIT_DURATION = 2000;

    public static final ConsentStatus REQUIRED_SIGNED_CURRENT = new ConsentStatus.Builder().withName("Name1")
            .withGuid(SubpopulationGuid.create("foo1")).withRequired(true).withConsented(true)
            .withSignedMostRecentConsent(true).build();
    public static final ConsentStatus REQUIRED_UNSIGNED = new ConsentStatus.Builder().withName("Name1")
            .withGuid(SubpopulationGuid.create("foo5")).withRequired(true).withConsented(false)
            .withSignedMostRecentConsent(false).build();

    public static final Map<SubpopulationGuid, ConsentStatus> UNCONSENTED_STATUS_MAP = new ImmutableMap.Builder<SubpopulationGuid, ConsentStatus>()
            .put(SubpopulationGuid.create(REQUIRED_UNSIGNED.getSubpopulationGuid()), REQUIRED_UNSIGNED).build();
    
    public static final Set<String> USER_DATA_GROUPS = ImmutableSet.of("group1","group2");

    public static final Set<String> USER_SUBSTUDY_IDS = ImmutableSet.of("substudyA","substudyB");
    
    public static final List<String> LANGUAGES = ImmutableList.of("en","fr");
    
    public static final Phone PHONE = new Phone("9712486796", "US");
    
    public static final AndroidAppLink ANDROID_APP_LINK = new AndroidAppLink("namespace", "package_name",
            Lists.newArrayList("sha256_cert_fingerprints"));
    public static final AndroidAppLink ANDROID_APP_LINK_2 = new AndroidAppLink("namespace2", "package_name2",
            Lists.newArrayList("sha256_cert_fingerprints2"));
    public static final AndroidAppLink ANDROID_APP_LINK_3 = new AndroidAppLink("namespace3", "package_name3",
            Lists.newArrayList("sha256_cert_fingerprints3"));
    public static final AndroidAppLink ANDROID_APP_LINK_4 = new AndroidAppLink("namespace4", "package_name4",
            Lists.newArrayList("sha256_cert_fingerprints4"));
    public static final AppleAppLink APPLE_APP_LINK = new AppleAppLink("studyId",
            Lists.newArrayList("/appId/", "/appId/*"));
    public static final AppleAppLink APPLE_APP_LINK_2 = new AppleAppLink("studyId2",
            Lists.newArrayList("/appId2/", "/appId2/*"));
    public static final AppleAppLink APPLE_APP_LINK_3 = new AppleAppLink("studyId3",
            Lists.newArrayList("/appId3/", "/appId3/*"));
    public static final AppleAppLink APPLE_APP_LINK_4 = new AppleAppLink("studyId4",
            Lists.newArrayList("/appId4/", "/appId4/*"));

}
