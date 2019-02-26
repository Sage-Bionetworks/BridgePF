package org.sagebionetworks.bridge.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.sagebionetworks.bridge.models.OperatingSystem.ANDROID;
import static org.sagebionetworks.bridge.models.OperatingSystem.IOS;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.springframework.validation.Errors;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.validators.Validate;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class CriteriaUtilsTest {
    
    private static final String KEY = "key";
    private static final Set<String> EMPTY_SET = ImmutableSet.of();
    
    // All tests are against v4 of the app.
    private static ClientInfo IOS_SHORT_INFO = ClientInfo.fromUserAgentCache("Unknown Client/14");
    private static ClientInfo IOS_CLIENT_INFO = ClientInfo.fromUserAgentCache("app/4 (deviceName; iPhone OS/3.9) BridgeJavaSDK/12");
    private static ClientInfo ANDROID_CLIENT_INFO = ClientInfo.fromUserAgentCache("app/4 (deviceName; Android/3.9) BridgeJavaSDK/12");
    
    private Criteria criteria;
    
    @Before
    public void before() {
        criteria = Criteria.create();
        criteria.setKey(KEY);
    }
    
    @Test
    public void matchesAgainstNothing() {
        CriteriaContext context = getContext(IOS_CLIENT_INFO);
        
        assertTrue(CriteriaUtils.matchCriteria(context, criteria));
    }
    
    @Test
    public void matchesAppRange() {
        CriteriaContext context = getContext(IOS_CLIENT_INFO);
        
        // These should all match v4
        assertTrue(CriteriaUtils.matchCriteria(context, getCriteria(EMPTY_SET, EMPTY_SET, IOS, null, 4)));
        assertTrue(CriteriaUtils.matchCriteria(context, getCriteria(EMPTY_SET, EMPTY_SET, IOS, 1, null)));
        assertTrue(CriteriaUtils.matchCriteria(context, getCriteria(EMPTY_SET, EMPTY_SET, IOS, 1, 4)));
    }
    
    @Test
    public void filtersAppRange() {
        CriteriaContext context = getContext(IOS_CLIENT_INFO);
        
        // None of these match v4 of an app
        assertFalse(CriteriaUtils.matchCriteria(context, getCriteria(EMPTY_SET, EMPTY_SET, IOS, null, 2)));
        assertFalse(CriteriaUtils.matchCriteria(context, getCriteria(EMPTY_SET, EMPTY_SET, IOS, 5, null)));
        assertFalse(CriteriaUtils.matchCriteria(context, getCriteria(EMPTY_SET, EMPTY_SET, IOS, 6, 11)));
    }
    
    @Test
    public void matchesAndroidAppRange() {
        CriteriaContext context = getContext(ANDROID_CLIENT_INFO);
        
        // These all match because the os name matches and so matching is used
        assertTrue(CriteriaUtils.matchCriteria(context, getCriteria(EMPTY_SET, EMPTY_SET, ANDROID, null, 4)));
        assertTrue(CriteriaUtils.matchCriteria(context, getCriteria(EMPTY_SET, EMPTY_SET, ANDROID, 1, null)));
        assertTrue(CriteriaUtils.matchCriteria(context, getCriteria(EMPTY_SET, EMPTY_SET, ANDROID, 1, 4)));
    }
    
    @Test
    public void filtersAppRangeWithAndroid() {
        CriteriaContext context = getContext(ANDROID_CLIENT_INFO);
        
        // These do not match because the os name matches and so matching is applied
        assertFalse(CriteriaUtils.matchCriteria(context, getCriteria(EMPTY_SET, EMPTY_SET, ANDROID, null, 2)));
        assertFalse(CriteriaUtils.matchCriteria(context, getCriteria(EMPTY_SET, EMPTY_SET, ANDROID, 5, null)));
        assertFalse(CriteriaUtils.matchCriteria(context, getCriteria(EMPTY_SET, EMPTY_SET, ANDROID, 6, 11)));
    }
    
    @Test
    public void doesNotFilterOutIosWithAndroidAppRange() {
        CriteriaContext context = getContext(IOS_CLIENT_INFO);
        
        // But although these do not match the version, the client is different so no filtering occurs
        assertTrue(CriteriaUtils.matchCriteria(context, getCriteria(EMPTY_SET, EMPTY_SET, ANDROID, null, 2)));
        assertTrue(CriteriaUtils.matchCriteria(context, getCriteria(EMPTY_SET, EMPTY_SET, ANDROID, 5, null)));
        assertTrue(CriteriaUtils.matchCriteria(context, getCriteria(EMPTY_SET, EMPTY_SET, ANDROID, 6, 11)));
    }
    
    @Test
    public void matchesAppRangeIfNoPlatformDeclared() {
        CriteriaContext context = new CriteriaContext.Builder()
            .withContext(getContext(IOS_SHORT_INFO))
            .withClientInfo(ClientInfo.fromUserAgentCache("app/4")).build();
        
        // When the user agent doesn't include platform information, then filtering is not applied
        assertTrue(CriteriaUtils.matchCriteria(context, getCriteria(EMPTY_SET, EMPTY_SET, IOS, null, 2)));
        assertTrue(CriteriaUtils.matchCriteria(context, getCriteria(EMPTY_SET, EMPTY_SET, IOS, 5, null)));
        assertTrue(CriteriaUtils.matchCriteria(context, getCriteria(EMPTY_SET, EMPTY_SET, IOS, 6, 11)));
    }
    
    @Test
    public void allOfGroupsMatch() {
        CriteriaContext context = getContext(IOS_CLIENT_INFO); // has group1, and group2
        assertTrue(CriteriaUtils.matchCriteria(context, getCriteria(ImmutableSet.of("group1"), EMPTY_SET, null, null, null)));
        // Two groups are required, that still matches
        assertTrue(CriteriaUtils.matchCriteria(context, getCriteria(ImmutableSet.of("group1", "group2"), EMPTY_SET, null, null, null)));
        // but this doesn't
        assertFalse(CriteriaUtils.matchCriteria(context, getCriteria(ImmutableSet.of("group1", "group3"), EMPTY_SET, null, null, null)));
    }
    
    @Test
    public void noneOfGroupsMatch() {
        CriteriaContext context = getContext(IOS_CLIENT_INFO); // has group1, and group2
        // Here, any group at all prevents a match.
        assertFalse(CriteriaUtils.matchCriteria(context, getCriteria(EMPTY_SET, ImmutableSet.of("group3", "group1"), null, null, null)));
    }

    @Test
    public void noneOfGroupsDefinedButDontPreventMatch() {
        CriteriaContext context = getContext(IOS_CLIENT_INFO); // does not have group3, so it is matched
        assertTrue(CriteriaUtils.matchCriteria(context, getCriteria(EMPTY_SET, ImmutableSet.of("group3"), null, null, null)));
    }
    
    @Test
    public void allOfSubstudyIdsMatch() {
        CriteriaContext context = getSubstudyContext(IOS_CLIENT_INFO); // has substudyA, and substudyB
        assertTrue(CriteriaUtils.matchCriteria(context, getSubstudyCriteria(ImmutableSet.of("substudyA"), EMPTY_SET, null, null, null)));
        // Two groups are required, that still matches
        assertTrue(CriteriaUtils.matchCriteria(context, getSubstudyCriteria(ImmutableSet.of("substudyA", "substudyB"), EMPTY_SET, null, null, null)));
        // but this doesn't
        assertFalse(CriteriaUtils.matchCriteria(context, getSubstudyCriteria(ImmutableSet.of("substudyA", "substudyC"), EMPTY_SET, null, null, null)));
    }
    
    @Test
    public void noneOfSubstudyIdsMatch() {
        CriteriaContext context = getSubstudyContext(IOS_CLIENT_INFO); // has substudyA, and substudyB
        // Here, any group at all prevents a match.
        assertFalse(CriteriaUtils.matchCriteria(context, getSubstudyCriteria(EMPTY_SET, ImmutableSet.of("substudyC", "substudyA"), null, null, null)));
    }

    @Test
    public void noneOfSubstudyIdsDefinedButDontPreventMatch() {
        CriteriaContext context = getSubstudyContext(IOS_CLIENT_INFO); // does not have substudyC, so it is matched
        assertTrue(CriteriaUtils.matchCriteria(context, getSubstudyCriteria(EMPTY_SET, ImmutableSet.of("substudyC"), null, null, null)));
    }
    
    @Test
    public void matchingWithMinimalContextDoesNotCrash() {
        CriteriaContext context = new CriteriaContext.Builder()
                .withStudyIdentifier(TestConstants.TEST_STUDY)
                .withClientInfo(ClientInfo.UNKNOWN_CLIENT).build();
        assertTrue(CriteriaUtils.matchCriteria(context, getCriteria(EMPTY_SET, EMPTY_SET, null, null, null)));
        assertFalse(CriteriaUtils.matchCriteria(context, getCriteria(ImmutableSet.of("group1"), EMPTY_SET, null, null, null)));
    }
    
    @Test
    public void validateIosMinMaxSameVersionOK() {
        Criteria criteria = getCriteria(EMPTY_SET, EMPTY_SET, IOS, 1, 1);
        
        Errors errors = Validate.getErrorsFor(criteria);
        CriteriaUtils.validate(criteria, EMPTY_SET, EMPTY_SET, errors);
        assertFalse(errors.hasErrors());
    }

    @Test
    public void validateIosCannotSetMaxUnderMinAppVersion() {
        Criteria criteria = getCriteria(EMPTY_SET, EMPTY_SET, IOS, 2, 1);
        
        Errors errors = Validate.getErrorsFor(criteria);
        CriteriaUtils.validate(criteria, EMPTY_SET, EMPTY_SET, errors);
        assertEquals("cannot be less than minAppVersions.iphone_os", errors.getFieldErrors("maxAppVersions.iphone_os").get(0).getCode());
    }
    
    @Test
    public void validateIosCannotSetMinLessThanZero() {
        Criteria criteria = getCriteria(EMPTY_SET, EMPTY_SET, IOS, -2, null);
        
        Errors errors = Validate.getErrorsFor(criteria);
        CriteriaUtils.validate(criteria, EMPTY_SET, EMPTY_SET, errors);
        assertEquals("cannot be negative", errors.getFieldErrors("minAppVersions.iphone_os").get(0).getCode());
    }
    
    // Try these again with a different os name. If two different values work, any value should work.
    
    @Test
    public void validateAndroidMinMaxSameVersionOK() {
        Criteria criteria = getCriteria(EMPTY_SET, EMPTY_SET, ANDROID, 1, 1);
        
        Errors errors = Validate.getErrorsFor(criteria);
        CriteriaUtils.validate(criteria, EMPTY_SET, EMPTY_SET, errors);
        assertFalse(errors.hasErrors());
    }

    @Test
    public void validateAndroidCannotSetMaxUnderMinAppVersion() {
        Criteria criteria = getCriteria(EMPTY_SET, EMPTY_SET, ANDROID, 2, 1);
        
        Errors errors = Validate.getErrorsFor(criteria);
        CriteriaUtils.validate(criteria, EMPTY_SET, EMPTY_SET, errors);
        assertEquals("cannot be less than minAppVersions.android", errors.getFieldErrors("maxAppVersions.android").get(0).getCode());
    }
    
    @Test
    public void validateAndroidCannotSetMinLessThanZero() {
        Criteria criteria = getCriteria(EMPTY_SET, EMPTY_SET, ANDROID, -2, null);
        
        Errors errors = Validate.getErrorsFor(criteria);
        CriteriaUtils.validate(criteria, EMPTY_SET, EMPTY_SET, errors);
        assertEquals("cannot be negative", errors.getFieldErrors("minAppVersions.android").get(0).getCode());
    }
    
    @Test
    public void validateDataGroupSetsCannotBeNull() {
        // Here's an implementation that allows these fields to be null
        Criteria criteria = new Criteria() {
            private String key;
            private String language;
            private Map<String,Integer> minAppVersions = Maps.newHashMap();
            private Map<String,Integer> maxAppVersions = Maps.newHashMap();
            private Set<String> allOfGroups;
            private Set<String> noneOfGroups;
            private Set<String> allOfSubstudies;
            private Set<String> noneOfSubstudies;
            public void setKey(String key) { this.key = key; }
            public String getKey() { return key; }
            public void setLanguage(String language) { this.language = language; }
            public String getLanguage() { return language; }
            public void setMinAppVersion(String osName, Integer minAppVersion) { this.minAppVersions.put(osName, minAppVersion); }
            public Integer getMinAppVersion(String osName) { return minAppVersions.get(osName); }
            public void setMaxAppVersion(String osName, Integer maxAppVersion) { this.maxAppVersions.put(osName, maxAppVersion); }
            public Integer getMaxAppVersion(String osName) { return maxAppVersions.get(osName); }
            public void setAllOfGroups(Set<String> allOfGroups) { this.allOfGroups = allOfGroups; }
            public Set<String> getAllOfGroups() { return allOfGroups; }
            public void setNoneOfGroups(Set<String> noneOfGroups) { this.noneOfGroups = noneOfGroups; }
            public Set<String> getNoneOfGroups() { return noneOfGroups; }
            public void setAllOfSubstudyIds(Set<String> allOfSubstudies) { this.allOfSubstudies = allOfSubstudies; }
            public Set<String> getAllOfSubstudyIds() { return allOfSubstudies; }
            public void setNoneOfSubstudyIds(Set<String> noneOfSubstudies) { this.noneOfSubstudies = noneOfSubstudies; }
            public Set<String> getNoneOfSubstudyIds() { return noneOfSubstudies; }
            public Set<String> getAppVersionOperatingSystems() { return new ImmutableSet.Builder<String>()
                .addAll(minAppVersions.keySet()).addAll(maxAppVersions.keySet()).build(); }
        };
        
        Errors errors = Validate.getErrorsFor(criteria);
        CriteriaUtils.validate(criteria, EMPTY_SET, EMPTY_SET, errors);
        assertEquals("cannot be null", errors.getFieldErrors("allOfGroups").get(0).getCode());
        assertEquals("cannot be null", errors.getFieldErrors("noneOfGroups").get(0).getCode());
        assertEquals("cannot be null", errors.getFieldErrors("allOfSubstudyIds").get(0).getCode());
        assertEquals("cannot be null", errors.getFieldErrors("noneOfSubstudyIds").get(0).getCode());
    }
    
    @Test
    public void validateDataGroupCannotBeWrong() {
        Criteria criteria = getCriteria(ImmutableSet.of("group1"), ImmutableSet.of("group2"), null, null, null);
        Errors errors = Validate.getErrorsFor(criteria);
        CriteriaUtils.validate(criteria, ImmutableSet.of("group3"), EMPTY_SET, errors);
        assertEquals("'group1' is not in enumeration: group3", errors.getFieldErrors("allOfGroups").get(0).getCode());
        assertEquals("'group2' is not in enumeration: group3", errors.getFieldErrors("noneOfGroups").get(0).getCode());
    }
    
    @Test
    public void validateDataGroupNotBothRequiredAndProhibited() {
        Criteria criteria = getCriteria(ImmutableSet.of("group1","group2","group3"), ImmutableSet.of("group2","group3"), null, null, null);
        Errors errors = Validate.getErrorsFor(criteria);
        CriteriaUtils.validate(criteria, ImmutableSet.of("group1","group2","group3","group4"), EMPTY_SET, errors);
        // It's a set so validate without describing the order of the groups in the error message
        assertTrue(errors.getFieldErrors("allOfGroups").get(0).getCode().contains("includes these excluded data groups: "));
        assertTrue(errors.getFieldErrors("allOfGroups").get(0).getCode().contains("group2"));
        assertTrue(errors.getFieldErrors("allOfGroups").get(0).getCode().contains("group3"));
    }
    
    @Test
    public void validateSubstudyIdCannotBeWrong() {
        Criteria criteria = getCriteria(ImmutableSet.of(), ImmutableSet.of(), null, null, null);
        criteria.setAllOfSubstudyIds(ImmutableSet.of("substudyA"));
        criteria.setNoneOfSubstudyIds(ImmutableSet.of("substudyB"));
        
        Errors errors = Validate.getErrorsFor(criteria);
        CriteriaUtils.validate(criteria, EMPTY_SET, ImmutableSet.of("substudyC"), errors);
        assertEquals("'substudyA' is not in enumeration: substudyC", errors.getFieldErrors("allOfSubstudyIds").get(0).getCode());
        assertEquals("'substudyB' is not in enumeration: substudyC", errors.getFieldErrors("noneOfSubstudyIds").get(0).getCode());
    }
    
    @Test
    public void validateSubstudyIdNotBothRequiredAndProhibited() {
        Criteria criteria = getCriteria(ImmutableSet.of(), ImmutableSet.of(), null, null, null);
        criteria.setAllOfSubstudyIds(ImmutableSet.of("substudyA", "substudyB", "substudyC"));
        criteria.setNoneOfSubstudyIds(ImmutableSet.of("substudyB", "substudyC"));
        Errors errors = Validate.getErrorsFor(criteria);
        CriteriaUtils.validate(criteria, EMPTY_SET, ImmutableSet.of("substudyA", "substudyB", "substudyC"), errors);
        // It's a set so validate without describing the order of the groups in the error message
        assertTrue(errors.getFieldErrors("allOfSubstudyIds").get(0).getCode().contains("includes these excluded substudies: "));
        assertTrue(errors.getFieldErrors("allOfSubstudyIds").get(0).getCode().contains("substudyB"));
        assertTrue(errors.getFieldErrors("allOfSubstudyIds").get(0).getCode().contains("substudyC"));
    }
    
    @Test
    public void matchesLanguage() {
        // If a language is declared, the user has to match it.
        Criteria criteria = getCriteria(EMPTY_SET, EMPTY_SET, IOS, -2, null);
        criteria.setLanguage("en");
        
        // Requires English, user declares English, it matches
        CriteriaContext context = getContextWithLanguage("en");
        assertTrue(CriteriaUtils.matchCriteria(context, criteria));
        
        // Requires English, user declares Spanish, it does not match
        context = getContextWithLanguage("es");
        assertFalse(CriteriaUtils.matchCriteria(context, criteria));
        
        // Doesn't require a language, so we do not care about the user's language to select this
        criteria.setLanguage(null);
        assertTrue(CriteriaUtils.matchCriteria(context, criteria));
        
        // Requires English, but the user declares no language, this does NOT match.
        criteria.setLanguage("en");
        context = getContextWithLanguage(null);
        assertFalse(CriteriaUtils.matchCriteria(context, criteria));
    }
    
    @Test
    public void matchesLanguageRegardlessOfCase() {
        Criteria criteria = getCriteria(null, null, IOS, -2, null);
        criteria.setLanguage("EN");
        
        CriteriaContext context = getContextWithLanguage("en");
        assertTrue(CriteriaUtils.matchCriteria(context, criteria));
        
        criteria.setLanguage("en");
        
        context = getContextWithLanguage("EN");
        assertTrue(CriteriaUtils.matchCriteria(context, criteria));
    }
    
    // We had a report that this was not working so I mocked this out... it filters correctly.
    @Test
    public void testThatMoleMapperConfigurationWorks() {
        ClientInfo info = ClientInfo.parseUserAgentString("MoleMapper/4 (iPhone 6S+; iPhone OS/9.3.4) BridgeSDK/7");
        
        Criteria oldCriteria = Criteria.create();
        oldCriteria.setMinAppVersion("iPhone OS", 0);
        oldCriteria.setMaxAppVersion("iPhone OS", 3);
        
        oldCriteria.setMinAppVersion("Android", 0);
        oldCriteria.setMaxAppVersion("Android", 0);
        
        CriteriaContext context = getContext(info);
        assertFalse(CriteriaUtils.matchCriteria(context, oldCriteria));
        
        Criteria newCriteria = Criteria.create();
        newCriteria.setMinAppVersion("iPhone OS", 4);
        
        assertTrue(CriteriaUtils.matchCriteria(context, newCriteria));
    }

    private Criteria getCriteria(Set<String> required, Set<String> prohibited, String os, Integer min, Integer max) {
        Criteria criteria = Criteria.create();
        criteria.setKey(KEY);
        if (min != null) {
            criteria.setMinAppVersion(os, min);    
        }
        if (max != null) {
            criteria.setMaxAppVersion(os, max);    
        }
        criteria.setAllOfGroups(required);
        criteria.setNoneOfGroups(prohibited);
        return criteria;
    }
    
    private Criteria getSubstudyCriteria(Set<String> required, Set<String> prohibited, String os, Integer min, Integer max) {
        Criteria criteria = Criteria.create();
        criteria.setKey(KEY);
        if (min != null) {
            criteria.setMinAppVersion(os, min);    
        }
        if (max != null) {
            criteria.setMaxAppVersion(os, max);    
        }
        criteria.setAllOfSubstudyIds(required);
        criteria.setNoneOfSubstudyIds(prohibited);
        return criteria;
    }    
    
    private CriteriaContext getContextWithLanguage(String lang) {
        List<String> list = (lang == null) ? Lists.newArrayList() : Lists.newArrayList(lang);
        return new CriteriaContext.Builder().withStudyIdentifier(TestConstants.TEST_STUDY)
            .withLanguages(list).build();
    }
    
    private CriteriaContext getContext(ClientInfo clientInfo) {
        return new CriteriaContext.Builder()
                .withStudyIdentifier(TestConstants.TEST_STUDY)
                .withClientInfo(clientInfo)
                .withUserDataGroups(ImmutableSet.of("group1", "group2")).build();
    }
    
    private CriteriaContext getSubstudyContext(ClientInfo clientInfo) {
        return new CriteriaContext.Builder()
                .withStudyIdentifier(TestConstants.TEST_STUDY)
                .withClientInfo(clientInfo)
                .withUserSubstudyIds(TestConstants.USER_SUBSTUDY_IDS).build();
    }
    
}
