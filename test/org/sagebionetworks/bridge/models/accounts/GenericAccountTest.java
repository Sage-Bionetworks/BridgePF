package org.sagebionetworks.bridge.models.accounts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import org.junit.Test;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

public class GenericAccountTest {
    @Test
    public void attributes() {
        GenericAccount account = new GenericAccount();

        // Setting attributes adds them to the map.
        account.setAttribute("foo", "foo-value");
        account.setAttribute("bar", "bar-value");
        assertEquals(ImmutableSet.of("foo", "bar"), account.getAttributeNameSet());
        assertEquals("foo-value", account.getAttribute("foo"));
        assertEquals("bar-value", account.getAttribute("bar"));

        // Setting attributes to null removes them from the map.
        account.setAttribute("foo", null);
        assertEquals(ImmutableSet.of("bar"), account.getAttributeNameSet());
        assertEquals("bar-value", account.getAttribute("bar"));

        // Setting attributes to null or empty string will also not add them to the map.
        account.setAttribute("foo", "");
        assertEquals(ImmutableSet.of("bar"), account.getAttributeNameSet());
        account.setAttribute("foo", "   ");
        assertEquals(ImmutableSet.of("bar"), account.getAttributeNameSet());
    }

    @Test
    public void consents() {
        GenericAccount account = new GenericAccount();

        // Make dummy subpops.
        SubpopulationGuid fooSubpopGuid = SubpopulationGuid.create("foo-subpop-guid");
        SubpopulationGuid barSubpopGuid = SubpopulationGuid.create("bar-subpop-guid");

        ConsentSignature fooConsentSignature = new ConsentSignature.Builder().withName("Foo McFooface")
                .withBirthdate("1999-01-01").build();
        ConsentSignature barConsentSignature = new ConsentSignature.Builder().withName("Bar McBarface")
                .withBirthdate("1999-02-02").build();

        // Consents starts off empty.
        Map<SubpopulationGuid, List<ConsentSignature>> consentsBySubpop0 = account.getAllConsentSignatureHistories();
        assertTrue(consentsBySubpop0.isEmpty());
        TestUtils.assertMapIsImmutable(consentsBySubpop0, fooSubpopGuid, ImmutableList.of(fooConsentSignature));

        // Calling get by subpop initially returns an empty list.
        assertTrue(account.getConsentSignatureHistory(fooSubpopGuid).isEmpty());
        assertTrue(account.getConsentSignatureHistory(barSubpopGuid).isEmpty());

        // Set consent lists. We create mutable lists, but test that we automatically convert these to immutable lists.
        account.setConsentSignatureHistory(fooSubpopGuid, Lists.newArrayList(fooConsentSignature));
        account.setConsentSignatureHistory(barSubpopGuid, Lists.newArrayList(barConsentSignature));

        List<ConsentSignature> fooConsentList1 = account.getConsentSignatureHistory(fooSubpopGuid);
        assertEquals(1, fooConsentList1.size());
        assertEquals(fooConsentSignature, fooConsentList1.get(0));
        TestUtils.assertListIsImmutable(fooConsentList1, fooConsentSignature);

        List<ConsentSignature> barConsentList1 = account.getConsentSignatureHistory(barSubpopGuid);
        assertEquals(1, barConsentList1.size());
        assertEquals(barConsentSignature, barConsentList1.get(0));
        TestUtils.assertListIsImmutable(barConsentList1, barConsentSignature);

        Map<SubpopulationGuid, List<ConsentSignature>> consentsBySubpop1 = account.getAllConsentSignatureHistories();
        TestUtils.assertMapIsImmutable(consentsBySubpop1, fooSubpopGuid, ImmutableList.of(fooConsentSignature));
        assertEquals(2, consentsBySubpop1.size());
        assertEquals(fooConsentList1, consentsBySubpop1.get(fooSubpopGuid));
        TestUtils.assertListIsImmutable(consentsBySubpop1.get(fooSubpopGuid), fooConsentSignature);
        assertEquals(barConsentList1, consentsBySubpop1.get(barSubpopGuid));
        TestUtils.assertListIsImmutable(consentsBySubpop1.get(barSubpopGuid), barConsentSignature);

        // Setting a list to to null clears it from the map.
        account.setConsentSignatureHistory(fooSubpopGuid, null);
        assertTrue(account.getConsentSignatureHistory(fooSubpopGuid).isEmpty());
        assertFalse(account.getAllConsentSignatureHistories().containsKey(fooSubpopGuid));

        // Setting a list to empty also clears it from the map.
        account.setConsentSignatureHistory(barSubpopGuid, new ArrayList<>());
        assertTrue(account.getConsentSignatureHistory(barSubpopGuid).isEmpty());
        assertFalse(account.getAllConsentSignatureHistories().containsKey(barSubpopGuid));

        // Consent map is back to empty, and is still immutable.
        Map<SubpopulationGuid, List<ConsentSignature>> consentsBySubpop2 = account.getAllConsentSignatureHistories();
        assertTrue(consentsBySubpop2.isEmpty());
        TestUtils.assertMapIsImmutable(consentsBySubpop2, fooSubpopGuid, ImmutableList.of(fooConsentSignature));
    }

    @Test
    public void healthCodeHealthId() {
        GenericAccount account = new GenericAccount();

        // Can set individually.
        account.setHealthId("dummy-health-id-1");
        assertEquals("dummy-health-id-1", account.getHealthId());

        account.setHealthCode("dummy-health-code-1");
        assertEquals("dummy-health-code-1", account.getHealthCode());

        // Can set from HealthId object.
        account.setHealthId(new HealthIdImpl("dummy-health-id-2", "dummy-health-code-2"));
        assertEquals("dummy-health-id-2", account.getHealthId());
        assertEquals("dummy-health-code-2", account.getHealthCode());
    }

    @Test
    public void roles() {
        // Initially empty.
        GenericAccount account = new GenericAccount();
        assertTrue(account.getRoles().isEmpty());
        TestUtils.assertSetIsImmutable(account.getRoles(), Roles.TEST_USERS);

        // Set works.
        account.setRoles(EnumSet.of(Roles.ADMIN, Roles.DEVELOPER));
        assertEquals(EnumSet.of(Roles.ADMIN, Roles.DEVELOPER), account.getRoles());
        TestUtils.assertSetIsImmutable(account.getRoles(), Roles.TEST_USERS);

        // Setting to null makes it an empty set.
        account.setRoles(null);
        assertTrue(account.getRoles().isEmpty());
        TestUtils.assertSetIsImmutable(account.getRoles(), Roles.TEST_USERS);

        // Set to non-empty, then set to empty and verify that it works.
        account.setRoles(EnumSet.of(Roles.RESEARCHER));
        assertEquals(EnumSet.of(Roles.RESEARCHER), account.getRoles());

        account.setRoles(EnumSet.noneOf(Roles.class));
        assertTrue(account.getRoles().isEmpty());
        TestUtils.assertSetIsImmutable(account.getRoles(), Roles.TEST_USERS);
    }
    
    @Test
    public void dataGroups() {
        GenericAccount account = new GenericAccount();
        assertTrue(account.getDataGroups().isEmpty());
        TestUtils.assertSetIsImmutable(account.getDataGroups(), "en");

        // Set works.
        account.setDataGroups(Sets.newHashSet("A","B"));
        assertEquals(Sets.newHashSet("A","B"), account.getDataGroups());
        TestUtils.assertSetIsImmutable(account.getDataGroups(), "C");

        // Setting to null makes it an empty set.
        account.setDataGroups(null);
        assertTrue(account.getDataGroups().isEmpty());
        TestUtils.assertSetIsImmutable(account.getDataGroups(), "C");

        // Set to non-empty, then set to empty and verify that it works.
        account.setDataGroups(Sets.newHashSet("A", "B"));
        assertEquals(Sets.newHashSet("A", "B"), account.getDataGroups());

        account.setDataGroups(Sets.newHashSet());
        assertTrue(account.getRoles().isEmpty());
        TestUtils.assertSetIsImmutable(account.getDataGroups(), "C");
    }
    
    @Test
    public void languages() {
        LinkedHashSet<String> langs = new LinkedHashSet<>();
        langs.add("en");
        langs.add("fr");
        
        GenericAccount account = new GenericAccount();
        assertTrue(account.getLanguages().isEmpty());
        account.getLanguages().add("es");
        assertTrue(account.getLanguages().isEmpty());

        // Set works.
        account.setLanguages(langs);
        assertEquals(langs, account.getLanguages());
        account.getLanguages().add("es");
        assertEquals(langs, account.getLanguages());

        // Setting to null makes it an empty set.
        account.setLanguages(null);
        assertTrue(account.getLanguages().isEmpty());

        // Set to empty list, then set to empty and verify that it works.
        account.setLanguages(Sets.newLinkedHashSet());
        assertTrue(account.getLanguages().isEmpty());
    }
}
