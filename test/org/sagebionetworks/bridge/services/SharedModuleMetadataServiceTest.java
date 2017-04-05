package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import org.sagebionetworks.bridge.dao.SharedModuleMetadataDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.sharedmodules.SharedModuleMetadata;

public class SharedModuleMetadataServiceTest {
    private static final String MODULE_ID = "test-module";
    private static final String MODULE_NAME = "Test Module";
    private static final int MODULE_VERSION = 3;
    private static final String SCHEMA_ID = "test-schema";
    private static final int SCHEMA_REV = 7;

    private SharedModuleMetadataDao mockDao;
    private SharedModuleMetadataService svc;

    @Before
    public void before() {
        mockDao = mock(SharedModuleMetadataDao.class);
        svc = new SharedModuleMetadataService();
        svc.setMetadataDao(mockDao);
    }

    @Test(expected = BadRequestException.class)
    public void createNullId() {
        SharedModuleMetadata svcInputMetadata = makeValidMetadata();
        svcInputMetadata.setId(null);
        svc.createMetadata(svcInputMetadata);
    }

    @Test(expected = BadRequestException.class)
    public void createEmptyId() {
        SharedModuleMetadata svcInputMetadata = makeValidMetadata();
        svcInputMetadata.setId("");
        svc.createMetadata(svcInputMetadata);
    }

    @Test(expected = BadRequestException.class)
    public void createBlankId() {
        SharedModuleMetadata svcInputMetadata = makeValidMetadata();
        svcInputMetadata.setId("   ");
        svc.createMetadata(svcInputMetadata);
    }

    @Test
    public void createInvalid() {
        // Only ID is specified, because that hits validation at a different level.
        SharedModuleMetadata svcInputMetadata = SharedModuleMetadata.create();
        svcInputMetadata.setId(MODULE_ID);

        try {
            svc.createMetadata(svcInputMetadata);
            fail("expected exception");
        } catch (InvalidEntityException ex) {
            // ID and version are guaranteed by the service. Only name fails validation.
            assertTrue(ex.getMessage().contains("name must be specified"));
        }
    }

    @Test
    public void create() {
        // no old module, create with default version
        createHelper(1, 0, null);

        // no old module, create with version 3
        createHelper(3, 3, null);

        // old module version 1, create with default version
        createHelper(2, 0, 1);

        // old module version 1, create with version 3
        createHelper(3, 3, 1);
    }

    private void createHelper(int expectedVersion, int inputVersion, Integer oldVersion) {
        // set up input
        SharedModuleMetadata svcInputMetadata = makeValidMetadata();
        svcInputMetadata.setVersion(inputVersion);

        // mock dao
        ArgumentCaptor<SharedModuleMetadata> daoInputMetadataCaptor = ArgumentCaptor.forClass(
                SharedModuleMetadata.class);
        SharedModuleMetadata daoOutputMetadata = makeValidMetadata();
        when(mockDao.createMetadata(daoInputMetadataCaptor.capture())).thenReturn(daoOutputMetadata);

        if (oldVersion != null) {
            // get should return something
            SharedModuleMetadata oldMetadata = makeValidMetadata();
            oldMetadata.setVersion(oldVersion);
            when(mockDao.getMetadataByIdLatestVersion(MODULE_ID)).thenReturn(oldMetadata);
        }

        // execute
        SharedModuleMetadata svcOutputMetadata = svc.createMetadata(svcInputMetadata);

        // Validate we set the version on the metadata we send do the dao.
        SharedModuleMetadata daoInputMetadata = daoInputMetadataCaptor.getValue();
        assertEquals(expectedVersion, daoInputMetadata.getVersion());

        // Validate DAO input is also svcOutput.
        assertSame(daoOutputMetadata, svcOutputMetadata);
    }

    @Test(expected = BadRequestException.class)
    public void deleteByIdAllVersionsNullId() {
        svc.deleteMetadataByIdAllVersions(null);
    }

    @Test(expected = BadRequestException.class)
    public void deleteByIdAllVersionsEmptyId() {
        svc.deleteMetadataByIdAllVersions("");
    }

    @Test(expected = BadRequestException.class)
    public void deleteByIdAllVersionsBlankId() {
        svc.deleteMetadataByIdAllVersions("   ");
    }

    @Test(expected = EntityNotFoundException.class)
    public void deleteByIdAllVersionsNotFound() {
        // mock dao to return empty list
        when(mockDao.getMetadataByIdAllVersions(MODULE_ID)).thenReturn(ImmutableList.of());

        svc.deleteMetadataByIdAllVersions(MODULE_ID);
    }

    @Test
    public void deleteByIdAllVersionsSuccess() {
        // mock get
        when(mockDao.getMetadataByIdAllVersions(MODULE_ID)).thenReturn(ImmutableList.of(makeValidMetadata()));

        // execute and verify delete call
        svc.deleteMetadataByIdAllVersions(MODULE_ID);
        verify(mockDao).deleteMetadataByIdAllVersions(MODULE_ID);
    }

    @Test(expected = BadRequestException.class)
    public void deleteByIdAndVersionNullId() {
        svc.deleteMetadataByIdAndVersion(null, MODULE_VERSION);
    }

    @Test(expected = BadRequestException.class)
    public void deleteByIdAndVersionEmptyId() {
        svc.deleteMetadataByIdAndVersion("", MODULE_VERSION);
    }

    @Test(expected = BadRequestException.class)
    public void deleteByIdAndVersionBlankId() {
        svc.deleteMetadataByIdAndVersion("   ", MODULE_VERSION);
    }

    @Test(expected = BadRequestException.class)
    public void deleteByIdAndVersionNegativeVersion() {
        svc.deleteMetadataByIdAndVersion(MODULE_ID, -1);
    }

    @Test(expected = BadRequestException.class)
    public void deleteByIdAndVersionZeroVersion() {
        svc.deleteMetadataByIdAndVersion(MODULE_ID, 0);
    }

    @Test(expected = EntityNotFoundException.class)
    public void deleteByIdAndVersionNotFound() {
        // mock dao to return null
        when(mockDao.getMetadataByIdAndVersion(MODULE_ID, MODULE_VERSION)).thenReturn(null);

        svc.deleteMetadataByIdAndVersion(MODULE_ID, MODULE_VERSION);
    }

    @Test
    public void deleteByIdAndVersionSuccess() {
        // mock get
        when(mockDao.getMetadataByIdAndVersion(MODULE_ID, MODULE_VERSION)).thenReturn(makeValidMetadata());

        // execute and verify delete call
        svc.deleteMetadataByIdAndVersion(MODULE_ID, MODULE_VERSION);
        verify(mockDao).deleteMetadataByIdAndVersion(MODULE_ID, MODULE_VERSION);
    }

    @Test
    public void allMetadataAllVersions() {
        // set up mock dao
        List<SharedModuleMetadata> daoOutputMetadataList = ImmutableList.of(makeValidMetadata());
        when(mockDao.getAllMetadataAllVersions()).thenReturn(daoOutputMetadataList);

        // execute and validate
        List<SharedModuleMetadata> svcOutputMetadataList = svc.getAllMetadataAllVersions();
        assertSame(daoOutputMetadataList, svcOutputMetadataList);
    }

    @Test
    public void allMetadataLatestVersions() {
        // set up mock dao - We want 2 different modules with 2 different versions each.
        SharedModuleMetadata moduleAVersion1 = makeValidMetadata();
        moduleAVersion1.setId("module-A");
        moduleAVersion1.setVersion(1);

        SharedModuleMetadata moduleAVersion2 = makeValidMetadata();
        moduleAVersion2.setId("module-A");
        moduleAVersion2.setVersion(2);

        SharedModuleMetadata moduleBVersion3 = makeValidMetadata();
        moduleBVersion3.setId("module-B");
        moduleBVersion3.setVersion(3);

        SharedModuleMetadata moduleBVersion4 = makeValidMetadata();
        moduleBVersion4.setId("module-B");
        moduleBVersion4.setVersion(4);

        List<SharedModuleMetadata> daoOutputMetadataList = ImmutableList.of(moduleAVersion1, moduleAVersion2,
                moduleBVersion3, moduleBVersion4);
        when(mockDao.getAllMetadataAllVersions()).thenReturn(daoOutputMetadataList);

        // execute and validate
        List<SharedModuleMetadata> svcOutputMetadataList = svc.getAllMetadataLatestVersions();
        assertEquals(2, svcOutputMetadataList.size());

        // List might be in any order, so convert it to a map.
        Map<String, SharedModuleMetadata> svcOutputMetadataById = Maps.uniqueIndex(svcOutputMetadataList,
                SharedModuleMetadata::getId);
        assertEquals(2, svcOutputMetadataById.size());
        assertEquals(2, svcOutputMetadataById.get("module-A").getVersion());
        assertEquals(4, svcOutputMetadataById.get("module-B").getVersion());
    }

    @Test(expected = BadRequestException.class)
    public void byIdAllVersionsNullId() {
        svc.getMetadataByIdAllVersions(null);
    }

    @Test(expected = BadRequestException.class)
    public void byIdAllVersionsEmptyId() {
        svc.getMetadataByIdAllVersions("");
    }

    @Test(expected = BadRequestException.class)
    public void byIdAllVersionsBlankId() {
        svc.getMetadataByIdAllVersions("   ");
    }

    @Test(expected = EntityNotFoundException.class)
    public void byIdAllVersionsNotFound() {
        when(mockDao.getMetadataByIdAllVersions(MODULE_ID)).thenReturn(ImmutableList.of());
        svc.getMetadataByIdAllVersions(MODULE_ID);
    }

    @Test
    public void byIdAllVersionsSuccess() {
        // set up mock dao
        List<SharedModuleMetadata> daoOutputMetadataList = ImmutableList.of(makeValidMetadata());
        when(mockDao.getMetadataByIdAllVersions(MODULE_ID)).thenReturn(daoOutputMetadataList);

        // execute and validate
        List<SharedModuleMetadata> svcOutputMetadataList = svc.getMetadataByIdAllVersions(MODULE_ID);
        assertSame(daoOutputMetadataList, svcOutputMetadataList);
    }

    @Test(expected = BadRequestException.class)
    public void byIdAndVersionNullId() {
        svc.getMetadataByIdAndVersion(null, MODULE_VERSION);
    }

    @Test(expected = BadRequestException.class)
    public void byIdAndVersionEmptyId() {
        svc.getMetadataByIdAndVersion("", MODULE_VERSION);
    }

    @Test(expected = BadRequestException.class)
    public void byIdAndVersionBlankId() {
        svc.getMetadataByIdAndVersion("   ", MODULE_VERSION);
    }

    @Test(expected = BadRequestException.class)
    public void byIdAndVersionNegativeVersion() {
        svc.getMetadataByIdAndVersion(MODULE_ID, -1);
    }

    @Test(expected = BadRequestException.class)
    public void byIdAndVersionZeroVersion() {
        svc.getMetadataByIdAndVersion(MODULE_ID, 0);
    }

    @Test(expected = EntityNotFoundException.class)
    public void byIdAndVersionNotFound() {
        when(mockDao.getMetadataByIdAndVersion(MODULE_ID, MODULE_VERSION)).thenReturn(null);
        svc.getMetadataByIdAndVersion(MODULE_ID, MODULE_VERSION);
    }

    @Test
    public void byIdAndVersionSuccess() {
        // set up mock dao
        SharedModuleMetadata daoOutputMetadata = makeValidMetadata();
        when(mockDao.getMetadataByIdAndVersion(MODULE_ID, MODULE_VERSION)).thenReturn(daoOutputMetadata);

        // execute and validate
        SharedModuleMetadata svcOutputMetadata = svc.getMetadataByIdAndVersion(MODULE_ID, MODULE_VERSION);
        assertSame(daoOutputMetadata, svcOutputMetadata);
    }

    @Test(expected = BadRequestException.class)
    public void byIdLatestVersionNullId() {
        svc.getMetadataByIdLatestVersion(null);
    }

    @Test(expected = BadRequestException.class)
    public void byIdLatestVersionEmptyId() {
        svc.getMetadataByIdLatestVersion("");
    }

    @Test(expected = BadRequestException.class)
    public void byIdLatestVersionBlankId() {
        svc.getMetadataByIdLatestVersion("   ");
    }

    @Test(expected = EntityNotFoundException.class)
    public void byIdLatestVersionNotFound() {
        when(mockDao.getMetadataByIdLatestVersion(MODULE_ID)).thenReturn(null);
        svc.getMetadataByIdLatestVersion(MODULE_ID);
    }

    @Test
    public void byIdLatestVersionSuccess() {
        // set up mock dao
        SharedModuleMetadata daoOutputMetadata = makeValidMetadata();
        when(mockDao.getMetadataByIdLatestVersion(MODULE_ID)).thenReturn(daoOutputMetadata);

        // execute and validate
        SharedModuleMetadata svcOutputMetadata = svc.getMetadataByIdLatestVersion(MODULE_ID);
        assertSame(daoOutputMetadata, svcOutputMetadata);
    }

    @Test
    public void queryPassthroughNullWhereClause() {
        queryPassthrough(null, null);
    }

    @Test
    public void queryPassthroughEmptyWhereClause() {
        queryPassthrough("", null);
    }

    @Test
    public void queryPassthroughBlankWhereClause() {
        queryPassthrough("   ", null);
    }

    @Test
    public void queryPassthroughValidWhereClause() {
        queryPassthrough("foo='bar'", null);
    }

    @Test
    public void queryPassthroughEmptyTags() {
        queryPassthrough(null, ImmutableSet.of());
    }

    private void queryPassthrough(String whereClause, Set<String> tags) {
        // set up mock dao
        List<SharedModuleMetadata> daoOutputMetadataList = makeMetadataListForQueryTest();
        when(mockDao.queryMetadata(whereClause)).thenReturn(daoOutputMetadataList);

        // execute and validate
        List<SharedModuleMetadata> svcOutputMetadataList = svc.queryMetadata(whereClause, tags);
        assertSame(daoOutputMetadataList, svcOutputMetadataList);
    }

    private static List<SharedModuleMetadata> makeMetadataListForQueryTest() {
        SharedModuleMetadata fooMetadata = makeValidMetadata();
        fooMetadata.setId("foo-module");
        fooMetadata.setTags(ImmutableSet.of("foo"));

        SharedModuleMetadata barMetadata = makeValidMetadata();
        barMetadata.setId("bar-module");
        barMetadata.setTags(ImmutableSet.of("bar"));

        return ImmutableList.of(fooMetadata, barMetadata);
    }

    @Test
    public void queryWithTags() {
        // null query with 2 tags
        Set<String> tags = ImmutableSet.of("foo", "bar");

        // Our test cases:
        // 1. no tags (out)
        // 2. one tag (in)
        // 3. both tags (in)
        // 4. superset of tags (in)
        // 5. intersection of tags (in)
        // 6. disjunction of tags (out)

        // 1. no tags (out)
        SharedModuleMetadata metadata1 = makeValidMetadata();
        metadata1.setId("module-1");

        // 2. one tag (in)
        SharedModuleMetadata metadata2 = makeValidMetadata();
        metadata2.setId("module-2");
        metadata2.setTags(ImmutableSet.of("foo"));

        // 3. both tags (in)
        SharedModuleMetadata metadata3 = makeValidMetadata();
        metadata3.setId("module-3");
        metadata3.setTags(ImmutableSet.of("foo", "bar"));

        // 4. superset of tags (in)
        SharedModuleMetadata metadata4 = makeValidMetadata();
        metadata4.setId("module-4");
        metadata4.setTags(ImmutableSet.of("foo", "bar", "baz"));

        // 5. intersection of tags (in)
        SharedModuleMetadata metadata5 = makeValidMetadata();
        metadata5.setId("module-5");
        metadata5.setTags(ImmutableSet.of("foo", "baz"));

        // 6. disjunction of tags (out)
        SharedModuleMetadata metadata6 = makeValidMetadata();
        metadata6.setId("module-6");
        metadata6.setTags(ImmutableSet.of("asdf", "jkl;"));

        // set up mock dao
        List<SharedModuleMetadata> daoOutputMetadataList = ImmutableList.of(metadata1, metadata2, metadata3, metadata4,
                metadata5, metadata6);
        when(mockDao.queryMetadata(null)).thenReturn(daoOutputMetadataList);

        // execute and validate
        List<SharedModuleMetadata> svcOutputMetadataList = svc.queryMetadata(null, tags);
        assertEquals(4, svcOutputMetadataList.size());
        assertTrue(svcOutputMetadataList.contains(metadata2));
        assertTrue(svcOutputMetadataList.contains(metadata3));
        assertTrue(svcOutputMetadataList.contains(metadata4));
        assertTrue(svcOutputMetadataList.contains(metadata5));
    }

    @Test(expected = BadRequestException.class)
    public void updateNullId() {
        svc.updateMetadata(null, MODULE_VERSION, makeValidMetadata());
    }

    @Test(expected = BadRequestException.class)
    public void updateEmptyId() {
        svc.updateMetadata("", MODULE_VERSION, makeValidMetadata());
    }

    @Test(expected = BadRequestException.class)
    public void updateBlankId() {
        svc.updateMetadata("   ", MODULE_VERSION, makeValidMetadata());
    }

    @Test(expected = BadRequestException.class)
    public void updateNegativeVersion() {
        svc.updateMetadata(MODULE_ID, -1, makeValidMetadata());
    }

    @Test(expected = BadRequestException.class)
    public void updateZeroVersion() {
        svc.updateMetadata(MODULE_ID, 0, makeValidMetadata());
    }

    @Test(expected = EntityNotFoundException.class)
    public void updateNotFound() {
        // mock dao to return null
        when(mockDao.getMetadataByIdAndVersion(MODULE_ID, MODULE_VERSION)).thenReturn(null);

        svc.updateMetadata(MODULE_ID, MODULE_VERSION, makeValidMetadata());
    }

    @Test
    public void updateInvalid() {
        // mock get
        when(mockDao.getMetadataByIdAndVersion(MODULE_ID, MODULE_VERSION)).thenReturn(makeValidMetadata());

        try {
            // Make obviously invalid metadata, such as one with no fields. (ID and version are set by the service.)
            svc.updateMetadata(MODULE_ID, MODULE_VERSION, SharedModuleMetadata.create());
            fail("expected exception");
        } catch (InvalidEntityException ex) {
            // ID and version are guaranteed by the service. Only name fails validation.
            assertTrue(ex.getMessage().contains("name must be specified"));
        }
    }

    @Test
    public void update() {
        // mock dao (update and get)
        ArgumentCaptor<SharedModuleMetadata> daoInputMetadataCaptor = ArgumentCaptor.forClass(
                SharedModuleMetadata.class);
        SharedModuleMetadata daoOutputMetadata = makeValidMetadata();
        when(mockDao.updateMetadata(daoInputMetadataCaptor.capture())).thenReturn(daoOutputMetadata);

        when(mockDao.getMetadataByIdAndVersion(MODULE_ID, MODULE_VERSION)).thenReturn(makeValidMetadata());

        // setup input - leave off module ID and version to see if we automatically fill it in
        SharedModuleMetadata svcInputMetadata = makeValidMetadata();
        svcInputMetadata.setId(null);
        svcInputMetadata.setVersion(0);

        // execute
        SharedModuleMetadata svcOutputMetadata = svc.updateMetadata(MODULE_ID, MODULE_VERSION, svcInputMetadata);

        // Validate we set the ID and version before passing it to the DAO.
        SharedModuleMetadata daoInputMetadata = daoInputMetadataCaptor.getValue();
        assertEquals(MODULE_ID, daoInputMetadata.getId());
        assertEquals(MODULE_VERSION, daoInputMetadata.getVersion());

        // Validate DAO input is also svcOutput.
        assertSame(daoOutputMetadata, svcOutputMetadata);
    }

    private static SharedModuleMetadata makeValidMetadata() {
        SharedModuleMetadata metadata = SharedModuleMetadata.create();
        metadata.setId(MODULE_ID);
        metadata.setName(MODULE_NAME);
        metadata.setVersion(MODULE_VERSION);
        metadata.setSchemaId(SCHEMA_ID);
        metadata.setSchemaRevision(SCHEMA_REV);
        return metadata;
    }
}
