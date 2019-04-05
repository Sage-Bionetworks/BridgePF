package org.sagebionetworks.bridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;
import org.junit.After;
import org.junit.Test;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.hibernate.HibernateAccount;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.ActivityType;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.substudies.AccountSubstudy;
import org.sagebionetworks.bridge.util.BridgeCollectors;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class BridgeUtilsTest {
    
    private static final LocalDateTime LOCAL_DATE_TIME = LocalDateTime.parse("2010-10-10T10:10:10.111");
    
    @After
    public void after() {
        BridgeUtils.setRequestContext(RequestContext.NULL_INSTANCE);
    }
    
    @Test
    public void mapSubstudyMemberships() {
        Account account = Account.create();
        AccountSubstudy substudy1 = AccountSubstudy.create("studyId", "subA", "accountId");
        AccountSubstudy substudy2 = AccountSubstudy.create("studyId", "subB", "accountId");
        substudy2.setExternalId("extB");
        AccountSubstudy substudy3 = AccountSubstudy.create("studyId", "subC", "accountId");
        substudy3.setExternalId("extC");
        AccountSubstudy substudy4 = AccountSubstudy.create("studyId", "subD", "accountId");
        account.setAccountSubstudies(ImmutableSet.of(substudy1, substudy2, substudy3, substudy4));
        
        Map<String, String> results = BridgeUtils.mapSubstudyMemberships(account);
        assertEquals(4, results.size());
        assertEquals("<none>", results.get("subA"));
        assertEquals("extB", results.get("subB"));
        assertEquals("extC", results.get("subC"));
        assertEquals("<none>", results.get("subD"));
    }
    
    @Test
    public void mapSubstudyMembershipsOneEntry() {
        Account account = Account.create();
        AccountSubstudy substudy2 = AccountSubstudy.create("studyId", "subB", "accountId");
        substudy2.setExternalId("extB");
        account.setAccountSubstudies(ImmutableSet.of(substudy2));
        
        Map<String, String> results = BridgeUtils.mapSubstudyMemberships(account);
        assertEquals(1, results.size());
        assertEquals("extB", results.get("subB"));
    }
    
    @Test
    public void mapSubstudyMembershipsNull() {
        Account account = Account.create();
        account.setAccountSubstudies(null);
        
        Map<String, String> results = BridgeUtils.mapSubstudyMemberships(account);
        assertTrue(results.isEmpty());
    }
    
    @Test
    public void mapSubstudyMembershipsBlank() {
        Account account = Account.create();
        account.setAccountSubstudies(ImmutableSet.of());
        
        Map<String, String> results = BridgeUtils.mapSubstudyMemberships(account);
        assertTrue(results.isEmpty());
    }

    @Test
    public void substudyIdsVisibleToCallerFilters() {
        Set<String> callerSubstudies = ImmutableSet.of("substudyA", "substudyB", "substudyD");
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerSubstudies(callerSubstudies).build());

        AccountSubstudy asA = AccountSubstudy.create(TestConstants.TEST_STUDY_IDENTIFIER, "substudyA", "id");
        AccountSubstudy asB = AccountSubstudy.create(TestConstants.TEST_STUDY_IDENTIFIER, "substudyB", "id");
        AccountSubstudy asC = AccountSubstudy.create(TestConstants.TEST_STUDY_IDENTIFIER, "substudyC", "id");
        Set<AccountSubstudy> accountSubstudies = ImmutableSet.of(asA, asB, asC);
        
        Set<String> visibles = BridgeUtils.substudyAssociationsVisibleToCaller(accountSubstudies)
                .getSubstudyIdsVisibleToCaller();
        
        assertEquals(ImmutableSet.of("substudyA", "substudyB"), visibles);
    }
    
    @Test
    public void substudyIdsVisibleToCallerNoFilterWhenSubstudiesEmpty() {
        AccountSubstudy asA = AccountSubstudy.create(TestConstants.TEST_STUDY_IDENTIFIER, "substudyA", "id");
        AccountSubstudy asB = AccountSubstudy.create(TestConstants.TEST_STUDY_IDENTIFIER, "substudyB", "id");
        AccountSubstudy asC = AccountSubstudy.create(TestConstants.TEST_STUDY_IDENTIFIER, "substudyC", "id");
        Set<AccountSubstudy> accountSubstudies = ImmutableSet.of(asA, asB, asC);
        
        Set<String> visibles = BridgeUtils.substudyAssociationsVisibleToCaller(accountSubstudies)
                .getSubstudyIdsVisibleToCaller();
        
        assertEquals(ImmutableSet.of("substudyA", "substudyB", "substudyC"), visibles);
    }
    
    @Test
    public void substudyIdsVisibleToCallerEmpty() {
        Set<String> callerSubstudies = ImmutableSet.of("substudyA", "substudyB", "substudyD");
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerSubstudies(callerSubstudies).build());
        
        Set<String> visibles = BridgeUtils.substudyAssociationsVisibleToCaller(ImmutableSet.of())
                .getSubstudyIdsVisibleToCaller();
        
        assertEquals(ImmutableSet.of(), visibles);
    }    
    
    @Test
    public void substudyIdsVisibleToCallerNull() {
        Set<String> callerSubstudies = ImmutableSet.of("substudyA", "substudyB", "substudyD");
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerSubstudies(callerSubstudies).build());
        
        Set<String> visibles = BridgeUtils.substudyAssociationsVisibleToCaller(null)
                .getSubstudyIdsVisibleToCaller();
        
        assertEquals(ImmutableSet.of(), visibles);
    }
    
    @Test
    public void externalIdsVisibleToCaller() {
        Set<String> callerSubstudies = ImmutableSet.of("substudyA", "substudyB", "substudyD");
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerSubstudies(callerSubstudies).build());

        AccountSubstudy asA = AccountSubstudy.create(TestConstants.TEST_STUDY_IDENTIFIER, "substudyA", "id");
        asA.setExternalId("extA");
        AccountSubstudy asB = AccountSubstudy.create(TestConstants.TEST_STUDY_IDENTIFIER, "substudyB", "id");
        asB.setExternalId("extB");
        AccountSubstudy asC = AccountSubstudy.create(TestConstants.TEST_STUDY_IDENTIFIER, "substudyC", "id");
        asC.setExternalId("extC");
        Set<AccountSubstudy> accountSubstudies = ImmutableSet.of(asA, asB, asC);
        
        Map<String, String> visibles = BridgeUtils.substudyAssociationsVisibleToCaller(accountSubstudies)
                .getExternalIdsVisibleToCaller();
        
        assertEquals(ImmutableMap.of("substudyA", "extA", "substudyB", "extB"), visibles);
    }
    
    @Test
    public void externalIdsVisibleToCallerNoFilterWhenSubstudiesEmpty() {
        AccountSubstudy asA = AccountSubstudy.create(TestConstants.TEST_STUDY_IDENTIFIER, "substudyA", "id");
        asA.setExternalId("extA");
        AccountSubstudy asB = AccountSubstudy.create(TestConstants.TEST_STUDY_IDENTIFIER, "substudyB", "id");
        asB.setExternalId("extB");
        AccountSubstudy asC = AccountSubstudy.create(TestConstants.TEST_STUDY_IDENTIFIER, "substudyC", "id");
        asC.setExternalId("extC");
        Set<AccountSubstudy> accountSubstudies = ImmutableSet.of(asA, asB, asC);
        
        Map<String, String> visibles = BridgeUtils.substudyAssociationsVisibleToCaller(accountSubstudies)
                .getExternalIdsVisibleToCaller();
        
        assertEquals(ImmutableMap.of("substudyA", "extA", "substudyB", "extB", "substudyC", "extC"), visibles);
    }    

    @Test
    public void externalIdsVisibleToCallerEmpty() {
        Set<String> callerSubstudies = ImmutableSet.of("substudyA", "substudyB", "substudyD");
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerSubstudies(callerSubstudies).build());
        
        Map<String, String> visibles = BridgeUtils.substudyAssociationsVisibleToCaller(ImmutableSet.of())
                .getExternalIdsVisibleToCaller();
        
        assertEquals(ImmutableMap.of(), visibles);
    }      
    
    @Test
    public void externalIdsVisibleToCallerNull() {
        Set<String> callerSubstudies = ImmutableSet.of("substudyA", "substudyB", "substudyD");
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerSubstudies(callerSubstudies).build());
        
        Map<String, String> visibles = BridgeUtils.substudyAssociationsVisibleToCaller(null)
                .getExternalIdsVisibleToCaller();
        
        assertEquals(ImmutableMap.of(), visibles);
    }
    
    @Test
    public void collectExternalIds() {
        Account account = Account.create();
        AccountSubstudy as1 = AccountSubstudy.create(TestConstants.TEST_STUDY_IDENTIFIER, "substudyA", "userId");
        as1.setExternalId("subAextId");
        AccountSubstudy as2 = AccountSubstudy.create(TestConstants.TEST_STUDY_IDENTIFIER, "substudyB", "userId");
        as2.setExternalId("subBextId");
        AccountSubstudy as3 = AccountSubstudy.create(TestConstants.TEST_STUDY_IDENTIFIER, "substudyC", "userId");
        account.setAccountSubstudies(ImmutableSet.of(as1, as2, as3));
        account.setExternalId("subDextId");
        
        Set<String> externalIds = BridgeUtils.collectExternalIds(account);
        assertEquals(ImmutableSet.of("subAextId","subBextId","subDextId"), externalIds);
    }
    
    @Test
    public void collectExternalIdsNullsAreIgnored() {
        Set<String> externalIds = BridgeUtils.collectExternalIds(Account.create());
        assertEquals(ImmutableSet.of(), externalIds);
    }    
    
    @Test
    public void filterForSubstudyAccountRemovesUnsharedSubstudyIds() {
        Set<String> substudies = ImmutableSet.of("substudyA");
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(substudies).build());
        
        Account account = BridgeUtils.filterForSubstudy(getAccountWithSubstudy("substudyB", "substudyA"));
        assertEquals(1, account.getAccountSubstudies().size());
        assertEquals("substudyA", Iterables.getFirst(account.getAccountSubstudies(), null).getSubstudyId());
        
        BridgeUtils.setRequestContext(null);
    }
    
    @Test
    public void filterForSubstudyAccountReturnsAllUnsharedSubstudyIdsForNonSubstudyCaller() {
        Account account = BridgeUtils.filterForSubstudy(getAccountWithSubstudy("substudyB", "substudyA"));
        assertEquals(2, account.getAccountSubstudies().size());
    }
    
    @Test
    public void filterForSubstudyAccountNullReturnsNull() {
        assertNull(BridgeUtils.filterForSubstudy((Account)null));
    }
    
    @Test
    public void filterForSubstudyAccountNoContextReturnsNormalAccount() {
        assertNotNull(BridgeUtils.filterForSubstudy(getAccountWithSubstudy()));
    }
    
    @Test
    public void filterForSubstudyAccountNoContextReturnsSubstudyAccount() {
        assertNotNull(BridgeUtils.filterForSubstudy(getAccountWithSubstudy("substudyA")));
    }
    
    @Test
    public void filterForSubstudyAccountWithSubstudiesHidesNormalAccount() {
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerSubstudies(ImmutableSet.of("substudyA")).build());
        assertNull(BridgeUtils.filterForSubstudy(getAccountWithSubstudy()));
        BridgeUtils.setRequestContext(null);
    }

    @Test
    public void filterForSubstudyAccountWithMatchingSubstudiesReturnsSubstudyAccount() {
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerSubstudies(ImmutableSet.of("substudyA")).build());
        assertNotNull(BridgeUtils.filterForSubstudy(getAccountWithSubstudy("substudyA")));
    }
    
    @Test
    public void filterForSubstudyAccountWithMismatchedSubstudiesHidesSubstudyAccount() {
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerSubstudies(ImmutableSet.of("notSubstudyA")).build());
        assertNull(BridgeUtils.filterForSubstudy(getAccountWithSubstudy("substudyA")));
    }

    @Test
    public void filterForSubstudyExtIdNullReturnsNull() {
        assertNull(BridgeUtils.filterForSubstudy((ExternalIdentifier)null));
    }
    
    @Test
    public void filterForSubstudyExtIdNoContextReturnsExtId() {
        assertNotNull(BridgeUtils.filterForSubstudy(getExternalIdentifierWithSubstudy("substudyA")));
    }
    
    @Test
    public void filterForSubstudyExtIdWithSubstudiesHidesNormalExtId() {
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerSubstudies(ImmutableSet.of("substudyA")).build());
        assertNull(BridgeUtils.filterForSubstudy(getExternalIdentifierWithSubstudy(null)));
        BridgeUtils.setRequestContext(null);
    }

    @Test
    public void filterForSubstudyExtIdWithMatchingSubstudiesReturnsExtId() {
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerSubstudies(ImmutableSet.of("substudyA")).build());
        assertNotNull(BridgeUtils.filterForSubstudy(getExternalIdentifierWithSubstudy("substudyA")));
    }
    
    @Test
    public void filterForSubstudyExtIdWithMismatchedSubstudiesHidesExtId() {
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerSubstudies(ImmutableSet.of("substudyA")).build());
        assertNull(BridgeUtils.filterForSubstudy(getExternalIdentifierWithSubstudy("substudyB")));
    }
    
    private HibernateAccount getAccountWithSubstudy(String... substudyIds) {
        HibernateAccount account = new HibernateAccount();
        Set<AccountSubstudy> accountSubstudies = Arrays.asList(substudyIds)
                .stream().map((id) -> {
            return AccountSubstudy.create("studyId", id, "accountId");
        }).collect(BridgeCollectors.toImmutableSet());
        account.setAccountSubstudies(accountSubstudies);
        return account;
    }
    
    private ExternalIdentifier getExternalIdentifierWithSubstudy(String substudyId) {
        ExternalIdentifier id = ExternalIdentifier.create(TestConstants.TEST_STUDY, "identifier");
        id.setSubstudyId(substudyId);
        return id;
    }
    
    @Test
    public void isExternalIdAccount() {
        StudyParticipant participant = new StudyParticipant.Builder().withExternalId("id").build();
        assertTrue(BridgeUtils.isExternalIdAccount(participant));
    }
    
    @Test
    public void isNotExternalIdAccount() {
        StudyParticipant participant = new StudyParticipant.Builder().withEmail("email@email.com").withExternalId("id").build();
        assertFalse(BridgeUtils.isExternalIdAccount(participant));
    }

    @Test
    public void getRequestContext() throws Exception {
        // Can set request ID in this thread.
        RequestContext context = new RequestContext.Builder().withRequestId("main request ID").build();
        RequestContext otherContext = new RequestContext.Builder().withRequestId("other request ID").build();
        
        BridgeUtils.setRequestContext(context);
        assertEquals("main request ID", BridgeUtils.getRequestContext().getId());

        // Request ID is thread local, so a separate thread should see a different request ID.
        Runnable runnable = () -> {
            assertEquals(RequestContext.NULL_INSTANCE, BridgeUtils.getRequestContext());
            BridgeUtils.setRequestContext(otherContext);
            assertEquals("other request ID", BridgeUtils.getRequestContext().getId());
        };
        Thread otherThread = new Thread(runnable);
        otherThread.start();
        otherThread.join();

        // Other thread doesn't affect this thread.
        assertEquals("main request ID", BridgeUtils.getRequestContext().getId());

        // Setting request ID to null is fine.
        BridgeUtils.setRequestContext(null);
        assertEquals(RequestContext.NULL_INSTANCE, BridgeUtils.getRequestContext());
    }

    @Test
    public void secondsToPeriodString() {
        assertEquals("30 seconds", BridgeUtils.secondsToPeriodString(30));
        assertEquals("1 minute", BridgeUtils.secondsToPeriodString(60));
        assertEquals("90 seconds", BridgeUtils.secondsToPeriodString(90));
        assertEquals("5 minutes", BridgeUtils.secondsToPeriodString(60*5));
        assertEquals("25 minutes", BridgeUtils.secondsToPeriodString(60*25));
        assertEquals("90 minutes", BridgeUtils.secondsToPeriodString(60*90));
        assertEquals("1 hour", BridgeUtils.secondsToPeriodString(60*60));
        assertEquals("2 hours", BridgeUtils.secondsToPeriodString(60*60*2));
        assertEquals("36 hours", BridgeUtils.secondsToPeriodString(60*60*36));
        assertEquals("1 day", BridgeUtils.secondsToPeriodString(60*60*24));
        assertEquals("2 days", BridgeUtils.secondsToPeriodString(60*60*24*2));
        assertEquals("7 days", BridgeUtils.secondsToPeriodString(60*60*24*7));
    }
    
    @Test
    public void parseAccountId() {
        // Identifier has upper-case letter to ensure we don't downcase or otherwise change it.
        AccountId accountId = BridgeUtils.parseAccountId("test", "IdentifierA9");
        assertEquals("test", accountId.getStudyId());
        assertEquals("IdentifierA9", accountId.getId());
        
        accountId = BridgeUtils.parseAccountId("test", "externalid:IdentifierA9");
        assertEquals("test", accountId.getStudyId());
        assertEquals("IdentifierA9", accountId.getExternalId());
        
        accountId = BridgeUtils.parseAccountId("test", "externalId:IdentifierA9");
        assertEquals("test", accountId.getStudyId());
        assertEquals("IdentifierA9", accountId.getExternalId());
        
        accountId = BridgeUtils.parseAccountId("test", "healthcode:IdentifierA9");
        assertEquals("test", accountId.getStudyId());
        assertEquals("IdentifierA9", accountId.getHealthCode());
        
        accountId = BridgeUtils.parseAccountId("test", "healthCode:IdentifierA9");
        assertEquals("test", accountId.getStudyId());
        assertEquals("IdentifierA9", accountId.getHealthCode());
        
        // Unrecognized prefix is just part of the userId
        accountId = BridgeUtils.parseAccountId("test", "unk:IdentifierA9");
        assertEquals("test", accountId.getStudyId());
        assertEquals("unk:IdentifierA9", accountId.getId());
    }
    
    @Test
    public void studyTemplateVariblesWorks() {
        String host = BridgeConfigFactory.getConfig().getHostnameWithPostfix("ws");
        assertTrue(StringUtils.isNotBlank(host));
        
        Study study = Study.create();
        study.setName("name1");
        study.setShortName("shortName");
        study.setIdentifier("identifier1");
        study.setSponsorName("sponsorName1");
        study.setSupportEmail("supportEmail1");
        study.setTechnicalEmail("technicalEmail1");
        study.setConsentNotificationEmail("consentNotificationEmail1");
        Map<String,String> map = BridgeUtils.studyTemplateVariables(study, (value) -> {
            return value.replaceAll("1", "2");
        });
        map.put("thisMap", "isMutable");
        
        assertEquals("name2", map.get("studyName"));
        assertEquals("shortName", map.get("studyShortName"));
        assertEquals("identifier2", map.get("studyId"));
        assertEquals("sponsorName2", map.get("sponsorName"));
        assertEquals("supportEmail2", map.get("supportEmail"));
        assertEquals("technicalEmail2", map.get("technicalEmail"));
        assertEquals("consentNotificationEmail2", map.get("consentEmail"));
        assertEquals("isMutable", map.get("thisMap"));
        assertEquals(host, map.get("host"));
    }
    
    @Test
    public void templateResolverHandlesNullConsentEmail() {
        Study study = TestUtils.getValidStudy(BridgeUtilsTest.class);
        study.setConsentNotificationEmail(null);
        
        Map<String,String> map = BridgeUtils.studyTemplateVariables(study);
        assertNull(map.get("consentEmail"));
    }
    
    @Test
    public void templateResolverWorks() {
        Map<String,String> map = Maps.newHashMap();
        map.put("baz", "Belgium");
        map.put("box", "Albuquerque");
        map.put("foo", "This is unused");
        
        // In particular, verifying that replacement of multiple identical tokens occurs,
        // unmatched variables are left alone
        String result = BridgeUtils.resolveTemplate("foo ${baz} bar ${baz} ${box} ${unused}", map);
        assertEquals("foo Belgium bar Belgium Albuquerque ${unused}", result);
    }
    
    @Test
    public void templateResolverHandlesSomeJunkValues() {
        Map<String,String> map = Maps.newHashMap();
        map.put("baz", null);
        
        // In particular, verifying that replacement of multiple identical tokens occurs,
        // unmatched variables are left alone
        String result = BridgeUtils.resolveTemplate("foo ${baz}", map);
        assertEquals("foo ${baz}", result);
        
        result = BridgeUtils.resolveTemplate(" ", map);
        assertEquals(" ", result);
    }
    
    @Test
    public void periodsNotInterpretedAsRegex() {
        Map<String,String> map = Maps.newHashMap();
        map.put("b.z", "bar");
        
        String result = BridgeUtils.resolveTemplate("${baz}", map);
        assertEquals("${baz}", result);
    }
    
    @Test
    public void commaListToSet() {
        Set<String> set = BridgeUtils.commaListToOrderedSet("a, b , c");
        orderedSetsEqual(TestUtils.newLinkedHashSet("a","b","c"), set);
        
        set = BridgeUtils.commaListToOrderedSet("a,b,c");
        orderedSetsEqual(TestUtils.newLinkedHashSet("a","b","c"), set);
        
        set = BridgeUtils.commaListToOrderedSet("");
        orderedSetsEqual(TestUtils.newLinkedHashSet(), set);
        
        set = BridgeUtils.commaListToOrderedSet(null);
        assertNotNull(set);
        
        set = BridgeUtils.commaListToOrderedSet(" a");
        orderedSetsEqual(TestUtils.newLinkedHashSet("a"), set);
        
        // Does not produce a null value.
        set = BridgeUtils.commaListToOrderedSet("a,,b");
        orderedSetsEqual(TestUtils.newLinkedHashSet("a","b"), set);
        
        set = BridgeUtils.commaListToOrderedSet("b,a");
        orderedSetsEqual(TestUtils.newLinkedHashSet("b","a"), set);
    }
    
    @Test
    public void setToCommaList() {
        Set<String> set = Sets.newHashSet("a", null, "", "b");
        
        assertEquals("a,b", BridgeUtils.setToCommaList(set));
        assertNull(BridgeUtils.setToCommaList(null));
        assertNull(BridgeUtils.setToCommaList(Sets.newHashSet()));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void nullsafeImmutableSet() {
        assertEquals(0, BridgeUtils.nullSafeImmutableSet(null).size());
        assertEquals(Sets.newHashSet("A"), BridgeUtils.nullSafeImmutableSet(Sets.newHashSet("A")));
        
        // This should throw an UnsupportedOperationException
        Set<String> set = BridgeUtils.nullSafeImmutableSet(Sets.newHashSet("A"));
        set.add("B");
    }
    
    @Test
    public void nullsAreRemovedFromSet() {
        // nulls are removed. They have to be to create ImmutableSet
        assertEquals(Sets.newHashSet("A"), BridgeUtils.nullSafeImmutableSet(Sets.newHashSet(null, "A")));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void nullsafeImmutableList() {
        assertEquals(0, BridgeUtils.nullSafeImmutableList(null).size());
        
        assertEquals(Lists.newArrayList("A","B"), BridgeUtils.nullSafeImmutableList(Lists.newArrayList("A","B")));
        
        List<String> list = BridgeUtils.nullSafeImmutableList(Lists.newArrayList("A","B"));
        list.add("C");
    }
    
    @Test
    public void nullsAreRemovedFromList() {
        List<String> list = BridgeUtils.nullSafeImmutableList(Lists.newArrayList(null,"A",null,"B"));
        assertEquals(Lists.newArrayList("A","B"), list);
    }    
    
    @Test(expected = UnsupportedOperationException.class)
    public void nullsafeImmutableMap() {
        assertEquals(0, BridgeUtils.nullSafeImmutableMap(null).size());
        
        Map<String,String> map = Maps.newHashMap();
        map.put("A", "B");
        map.put("C", "D");
        
        assertEquals("B", map.get("A"));
        assertEquals("D", map.get("C"));
        assertEquals(map, BridgeUtils.nullSafeImmutableMap(map));
        
        Map<String,String> newMap = BridgeUtils.nullSafeImmutableMap(map);
        newMap.put("E","F");
    }
    
    @Test
    public void nullsAreRemovedFromMap() {
        Map<String,String> map = Maps.newHashMap();
        map.put("A", "B");
        map.put("C", null);
        
        Map<String,String> mapWithoutNulls = Maps.newHashMap();
        mapWithoutNulls.put("A", "B");
        
        assertEquals(mapWithoutNulls, BridgeUtils.nullSafeImmutableMap(map));
    }    
    
    @Test
    public void textToErrorKey() {
        assertEquals("iphone_os", BridgeUtils.textToErrorKey("iPhone OS"));
        assertEquals("android", BridgeUtils.textToErrorKey("Android"));
        assertEquals("testers_operating_system_v2", BridgeUtils.textToErrorKey("Tester's Operating System v2"));
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void textToErrorKeyRejectsNull() {
        BridgeUtils.textToErrorKey(null);
    }
            
    @Test(expected = IllegalArgumentException.class)
    public void textToErrorKeyRejectsEmptyString() {
        BridgeUtils.textToErrorKey(" ");
    }
    
    @Test
    public void parseIntegerOrDefault() {
        assertEquals(3, BridgeUtils.getIntOrDefault(null, 3));
        assertEquals(3, BridgeUtils.getIntOrDefault("  ", 3));
        assertEquals(1, BridgeUtils.getIntOrDefault("1", 3));
    }
    
    @Test(expected = BadRequestException.class)
    public void parseIntegerOrDefaultThrowsException() {
        BridgeUtils.getIntOrDefault("asdf", 3);
    }

    @Test(expected = NullPointerException.class)
    public void withoutNullEntriesNullMap() {
        BridgeUtils.withoutNullEntries(null);
    }

    @Test
    public void withoutNullEntriesEmptyMap() {
        Map<String, String> outputMap = BridgeUtils.withoutNullEntries(ImmutableMap.of());
        assertTrue(outputMap.isEmpty());
    }

    @Test
    public void withoutNullEntries() {
        Map<String, String> inputMap = new HashMap<>();
        inputMap.put("AAA", "111");
        inputMap.put("BBB", null);
        inputMap.put("CCC", "333");

        Map<String, String> outputMap = BridgeUtils.withoutNullEntries(inputMap);
        assertEquals(2, outputMap.size());
        assertEquals("111", outputMap.get("AAA"));
        assertEquals("333", outputMap.get("CCC"));

        // validate that modifying the input map doesn't affect the output map
        inputMap.put("new key", "new value");
        assertEquals(2, outputMap.size());
    }

    @Test(expected = NullPointerException.class)
    public void putOrRemoveNullMap() {
        BridgeUtils.putOrRemove(null, "key", "value");
    }

    @Test(expected = NullPointerException.class)
    public void putOrRemoveNullKey() {
        BridgeUtils.putOrRemove(new HashMap<>(), null, "value");
    }

    @Test
    public void putOrRemove() {
        Map<String, String> map = new HashMap<>();

        // put some values and verify
        BridgeUtils.putOrRemove(map, "AAA", "111");
        BridgeUtils.putOrRemove(map, "BBB", "222");
        BridgeUtils.putOrRemove(map, "CCC", "333");
        assertEquals(3, map.size());
        assertEquals("111", map.get("AAA"));
        assertEquals("222", map.get("BBB"));
        assertEquals("333", map.get("CCC"));

        // replace a value and verify
        BridgeUtils.putOrRemove(map, "CCC", "not 333");
        assertEquals(3, map.size());
        assertEquals("111", map.get("AAA"));
        assertEquals("222", map.get("BBB"));
        assertEquals("not 333", map.get("CCC"));

        // remove a value and verify
        BridgeUtils.putOrRemove(map, "BBB", null);
        assertEquals(2, map.size());
        assertEquals("111", map.get("AAA"));
        assertEquals("not 333", map.get("CCC"));
    }

    @Test
    public void testGetLongOrDefault() {
        assertNull(BridgeUtils.getLongOrDefault(null, null));
        assertEquals(new Long(10), BridgeUtils.getLongOrDefault(null, 10L));
        assertEquals(new Long(20), BridgeUtils.getLongOrDefault("20", null));
    }
    
    @Test(expected = BadRequestException.class)
    public void testGetLongWithNonLongValue() {
        BridgeUtils.getLongOrDefault("asdf20", 10L);
    }
    
    @Test
    public void testGetDateTimeOrDefault() {
        DateTime dateTime = DateTime.now();
        assertNull(BridgeUtils.getDateTimeOrDefault(null, null));
        assertEquals(dateTime, BridgeUtils.getDateTimeOrDefault(null, dateTime));
        assertTrue(dateTime.isEqual(BridgeUtils.getDateTimeOrDefault(dateTime.toString(), null)));
    }

    @Test(expected = BadRequestException.class)
    public void testGetDateTimeWithInvalidDateTime() {
        BridgeUtils.getDateTimeOrDefault("asdf", null);
    }
    
    @Test
    public void encodeURIComponent() {
        assertEquals("tester%2B4%40tester.com", BridgeUtils.encodeURIComponent("tester+4@tester.com"));
    }
    
    @Test
    public void encodeURIComponentEmpty() {
        assertEquals("", BridgeUtils.encodeURIComponent(""));
    }
    
    @Test
    public void encodeURIComponentNull() {
        assertEquals(null, BridgeUtils.encodeURIComponent(null));
    }
    
    @Test
    public void encodeURIComponentNoEscaping() {
        assertEquals("foo-bar", BridgeUtils.encodeURIComponent("foo-bar"));
    }
    
    @Test
    public void passwordPolicyDescription() {
        PasswordPolicy policy = new PasswordPolicy(8, false, true, false, true);
        String description = BridgeUtils.passwordPolicyDescription(policy);
        assertEquals("Password must be 8 or more characters, and must contain at least one upper-case letter, and one symbolic character (non-alphanumerics like #$%&@).", description);
        
        policy = new PasswordPolicy(2, false, false, false, false);
        description = BridgeUtils.passwordPolicyDescription(policy);
        assertEquals("Password must be 2 or more characters.", description);
    }
    
    @Test
    public void returnPasswordInURI() throws Exception {
        URI uri = new URI("redis://rediscloud:thisisapassword@pub-redis-555.us-east-1-4.1.ec2.garantiadata.com:555");
        String password = BridgeUtils.extractPasswordFromURI(uri);
        assertEquals("thisisapassword", password);
    }
    
    @Test
    public void returnNullWhenNoPasswordInURI() throws Exception {
        URI uri = new URI("redis://pub-redis-555.us-east-1-4.1.ec2.garantiadata.com:555");
        String password = BridgeUtils.extractPasswordFromURI(uri);
        assertNull(password);
    }
    
    @Test
    public void createReferentGuid() {
        Activity activity = TestUtils.getActivity2();
        
        String referent = BridgeUtils.createReferentGuidIndex(activity, LOCAL_DATE_TIME);
        assertEquals("BBB:survey:2010-10-10T10:10:10.111", referent);
    }
    
    @Test
    public void createReferentGuid2() {
        String referent = BridgeUtils.createReferentGuidIndex(ActivityType.TASK, "foo", LOCAL_DATE_TIME.toString());
        assertEquals("foo:task:2010-10-10T10:10:10.111", referent);
    }
    
    @Test
    public void getLocalDateWithValue() throws Exception {
        LocalDate localDate = LocalDate.parse("2017-05-10");
        LocalDate parsed = BridgeUtils.getLocalDateOrDefault(localDate.toString(), null);
        
        assertEquals(localDate, parsed);
    }
    
    @Test
    public void getLocalDateWithDefault() {
        LocalDate localDate = LocalDate.parse("2017-05-10");
        LocalDate parsed = BridgeUtils.getLocalDateOrDefault(null, localDate);
        
        assertEquals(localDate, parsed);
    }
    
    @Test(expected = BadRequestException.class)
    public void getLocalDateWithError() {
        BridgeUtils.getLocalDateOrDefault("2017-05-10T05:05:10.000Z", null);
    }
    
    @Test
    public void toSynapseFriendlyName() {
        assertEquals("This is a .-_ synapse Friendly Name3",
                BridgeUtils.toSynapseFriendlyName("This (is a).-_ synapse Friendly Name3 "));
    }
    
    @Test(expected = NullPointerException.class)
    public void nullToSynapseFriendlyNameThrowsException() {
        BridgeUtils.toSynapseFriendlyName(null);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void emptyStringToSynapseFriendlyName() {
        BridgeUtils.toSynapseFriendlyName("  #");
    }
    
    // assertEquals with two sets doesn't verify the order is the same... hence this test method.
    private <T> void orderedSetsEqual(Set<T> first, Set<T> second) {
        assertEquals(first.size(), second.size());
        
        Iterator<T> firstIterator = first.iterator();
        Iterator<T> secondIterator = second.iterator();
        while(firstIterator.hasNext()) {
            assertEquals(firstIterator.next(), secondIterator.next());
        }
    }
    
}
