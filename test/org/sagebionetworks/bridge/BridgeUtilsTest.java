package org.sagebionetworks.bridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableMap;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;
import org.junit.Test;

import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.ActivityType;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.studies.Study;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class BridgeUtilsTest {
    
    private static final LocalDateTime LOCAL_DATE_TIME = LocalDateTime.parse("2010-10-10T10:10:10.111");
    
    @Test
    public void studyTemplateVariblesWorks() {
        Study study = Study.create();
        study.setName("name1");
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
        assertEquals("identifier2", map.get("studyId"));
        assertEquals("sponsorName2", map.get("sponsorName"));
        assertEquals("supportEmail2", map.get("supportEmail"));
        assertEquals("technicalEmail2", map.get("technicalEmail"));
        assertEquals("consentNotificationEmail2", map.get("consentEmail"));
        assertEquals("isMutable", map.get("thisMap"));
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
