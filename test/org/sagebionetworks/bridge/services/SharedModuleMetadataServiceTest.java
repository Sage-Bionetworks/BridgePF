package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.BridgeConstants.SHARED_STUDY_ID;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.sagebionetworks.bridge.dao.SharedModuleMetadataDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolderImpl;
import org.sagebionetworks.bridge.models.sharedmodules.SharedModuleMetadata;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;

@RunWith(MockitoJUnitRunner.class)
public class SharedModuleMetadataServiceTest {
    private static final String MODULE_ID = "test-module";
    private static final String MODULE_NAME = "Test Module";
    private static final int MODULE_VERSION = 3;
    private static final String SCHEMA_ID = "test-schema";
    private static final int SCHEMA_REV = 7;

    @Mock
    private SharedModuleMetadataDao mockDao;
    @Captor
    private ArgumentCaptor<Map<String,Object>> parametersCaptor;
    @Mock
    private UploadSchemaService mockUploadSchemaService;
    @Mock
    private SurveyService mockSurveyService;
    
    @Spy
    private SharedModuleMetadataService svc;

    @Before
    public void before() {
        svc.setMetadataDao(mockDao);
        svc.setUploadSchemaService(mockUploadSchemaService);
        svc.setSurveyService(mockSurveyService);
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

    @Test(expected = BadRequestException.class)
    public void createNotFoundSchema() {
        SharedModuleMetadata svcInputMetadata = makeValidMetadata();
        doThrow(EntityNotFoundException.class).when(mockUploadSchemaService).getUploadSchemaByIdAndRev(SHARED_STUDY_ID, SCHEMA_ID, SCHEMA_REV);
        svc.createMetadata(svcInputMetadata);
    }

    @Test(expected = BadRequestException.class)
    public void createNotFoundSurvey() {
        SharedModuleMetadata svcInputMetadata = makeValidMetadata();
        svcInputMetadata.setSchemaId(null);
        svcInputMetadata.setSchemaRevision(null);
        svcInputMetadata.setSurveyCreatedOn(1L);
        svcInputMetadata.setSurveyGuid("test-survey-guid");
        svc.createMetadata(svcInputMetadata);
    }

    @Test(expected = BadRequestException.class)
    public void createSurveyWithInvalidStudyId() {
        GuidCreatedOnVersionHolder holder = new GuidCreatedOnVersionHolderImpl("some-id", 1L);
        when(mockSurveyService.getSurvey(eq(new StudyIdentifierImpl("shared")), eq(holder), eq(false), eq(false))).thenReturn(null);
        
        SharedModuleMetadata svcInputMetadata = makeValidMetadata();
        svcInputMetadata.setSchemaId(null);
        svcInputMetadata.setSchemaRevision(null);
        svcInputMetadata.setSurveyCreatedOn(1L);
        svcInputMetadata.setSurveyGuid("some-id");
        
        svc.createMetadata(svcInputMetadata);
    }

    @Test
    public void createInvalid() {
        // Spy get latest query to return an empty list. This allows us to avoid having to depend on a bunch of complex
        // logic in a public API we're already testing elsewhere.
        doReturn(ImmutableList.of()).when(svc).queryMetadataById(MODULE_ID, true, false, null, null, null, false);

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
    
    @Test
    public void cannotCreateADeletedSharedModule() {
        // Only ID is specified, because that hits validation at a different level.
        SharedModuleMetadata metadata = makeValidMetadata();
        metadata.setDeleted(true);
        
        ArgumentCaptor<SharedModuleMetadata> daoInputMetadataCaptor = ArgumentCaptor.forClass(
                SharedModuleMetadata.class);
        when(mockDao.createMetadata(daoInputMetadataCaptor.capture())).thenReturn(metadata);        
        
        svc.createMetadata(metadata);
        
        verify(mockDao).createMetadata(daoInputMetadataCaptor.capture());
        assertFalse(daoInputMetadataCaptor.getValue().isDeleted());
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

        // Similarly, spy get latest query.
        List<SharedModuleMetadata> queryLatestMetadataList = new ArrayList<>();
        if (oldVersion != null) {
            SharedModuleMetadata oldMetadata = makeValidMetadata();
            oldMetadata.setVersion(oldVersion);
            queryLatestMetadataList.add(oldMetadata);
        }
        doReturn(queryLatestMetadataList).when(svc).queryMetadataById(MODULE_ID, true, false, null, null, null, false);

        // execute
        SharedModuleMetadata svcOutputMetadata = svc.createMetadata(svcInputMetadata);

        // Validate we set the version on the metadata we send do the dao.
        SharedModuleMetadata daoInputMetadata = daoInputMetadataCaptor.getValue();
        assertEquals(expectedVersion, daoInputMetadata.getVersion());

        // Validate DAO input is also svcOutput.
        assertSame(daoOutputMetadata, svcOutputMetadata);
    }

    @Test(expected = BadRequestException.class)
    public void deleteByIdAllVersionsPermanentlyNullId() {
        svc.deleteMetadataByIdAllVersionsPermanently(null);
    }

    @Test(expected = BadRequestException.class)
    public void deleteByIdAllVersionsPermanentlyEmptyId() {
        svc.deleteMetadataByIdAllVersionsPermanently("");
    }

    @Test(expected = BadRequestException.class)
    public void deleteByIdAllVersionsPermanentlyBlankId() {
        svc.deleteMetadataByIdAllVersionsPermanently("   ");
    }

    @Test(expected = EntityNotFoundException.class)
    public void deleteByIdAllVersionsPermanentlyNotFound() {
        svc.deleteMetadataByIdAllVersionsPermanently(MODULE_ID);
    }
    
    @Test
    public void deleteByIdAllVersionsPermanentlySuccess() {
        when(mockDao.queryMetadata(eq("id=:id"), any()))
                .thenReturn(ImmutableList.of(makeValidMetadata()));
        
        svc.deleteMetadataByIdAllVersionsPermanently(MODULE_ID);
        
        verify(mockDao).queryMetadata(eq("id=:id"), parametersCaptor.capture());
        verify(mockDao).deleteMetadataByIdAllVersionsPermanently(MODULE_ID);
        
        assertEquals(1, parametersCaptor.getValue().size());
        assertEquals(MODULE_ID, parametersCaptor.getValue().get("id"));
    }

    @Test(expected = BadRequestException.class)
    public void deleteByIdAndVersionPermanentlyNullId() {
        svc.deleteMetadataByIdAndVersionPermanently(null, MODULE_VERSION);
    }

    @Test(expected = BadRequestException.class)
    public void deleteByIdAndVersionPermanentlyEmptyId() {
        svc.deleteMetadataByIdAndVersionPermanently("", MODULE_VERSION);
    }

    @Test(expected = BadRequestException.class)
    public void deleteByIdAndVersionPermanentlyBlankId() {
        svc.deleteMetadataByIdAndVersionPermanently("   ", MODULE_VERSION);
    }

    @Test(expected = BadRequestException.class)
    public void deleteByIdAndVersionPermanentlyNegativeVersion() {
        svc.deleteMetadataByIdAndVersionPermanently(MODULE_ID, -1);
    }

    @Test(expected = BadRequestException.class)
    public void deleteByIdAndVersionPermanentlyZeroVersion() {
        svc.deleteMetadataByIdAndVersionPermanently(MODULE_ID, 0);
    }

    @Test(expected = EntityNotFoundException.class)
    public void deleteByIdAndVersionPermanentlyNotFound() {
        // mock dao to return null
        when(mockDao.getMetadataByIdAndVersion(MODULE_ID, MODULE_VERSION)).thenReturn(null);

        svc.deleteMetadataByIdAndVersionPermanently(MODULE_ID, MODULE_VERSION);
    }

    @Test
    public void deleteByIdAndVersionPermanentlySuccess() {
        // mock get
        when(mockDao.getMetadataByIdAndVersion(MODULE_ID, MODULE_VERSION)).thenReturn(makeValidMetadata());

        // execute and verify delete call
        svc.deleteMetadataByIdAndVersionPermanently(MODULE_ID, MODULE_VERSION);
        verify(mockDao).deleteMetadataByIdAndVersionPermanently(MODULE_ID, MODULE_VERSION);
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
        doReturn(ImmutableList.of()).when(svc).queryMetadataById(MODULE_ID, true, false, null, null, null, false);
        svc.deleteMetadataByIdAllVersions(MODULE_ID);
    }

    @Test
    public void deleteByIdAllVersionsSuccess() {
        doReturn(ImmutableList.of(makeValidMetadata())).when(svc).queryMetadataById(MODULE_ID, true, false, null, null, null, false);
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

    @Test(expected = EntityNotFoundException.class)
    public void deleteByIdAndVersionAlreadyLogicallyDeleted() {
        // mock get
        SharedModuleMetadata metadata = makeValidMetadata();
        metadata.setDeleted(true);
        when(mockDao.getMetadataByIdAndVersion(MODULE_ID, MODULE_VERSION)).thenReturn(metadata);

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
        doReturn(ImmutableList.of()).when(svc).queryMetadataById(MODULE_ID, true, false, null, null, null, false);
        svc.getMetadataByIdLatestVersion(MODULE_ID);
    }

    @Test
    public void byIdLatestVersionSuccess() {
        // set up mock dao
        SharedModuleMetadata daoOutputMetadata = makeValidMetadata();
        doReturn(ImmutableList.of(daoOutputMetadata)).when(svc).queryMetadataById(MODULE_ID, true, false, null, null, null, false);

        // execute and validate
        SharedModuleMetadata svcOutputMetadata = svc.getMetadataByIdLatestVersion(MODULE_ID);
        assertSame(daoOutputMetadata, svcOutputMetadata);
    }
    
    @Test
    public void queryMetadataIncludeDeleted() {
        svc.queryAllMetadata(false, false, null, null, null, false);
        
        verify(mockDao).queryMetadata("deleted!=true", null);
    }
    
    @Test
    public void queryMetadataIncludeDeletedWithOtherWhereClauseExpressions() {
        svc.queryAllMetadata(false, false, "foo='bar'", null, null, false);
        
        verify(mockDao).queryMetadata("foo='bar' AND deleted!=true", null);
    }
    
    @Test
    public void queryMetadataByIdIncludeDeleted() {
        svc.queryMetadataById(MODULE_ID, false, false, null, null, null, false);
        
        verify(mockDao).queryMetadata("deleted!=true", null);
    }
    
    @Test
    public void queryMetadataByIdIncludeDeletedWithOtherWhereClauseExpressions() {
        svc.queryMetadataById(MODULE_ID, false, false, "foo='bar'", null, null, false);
        
        verify(mockDao).queryMetadata("foo='bar' AND deleted!=true", null);
    }

    @Test(expected = BadRequestException.class)
    public void queryAllMostRecentWithWhere() {
        svc.queryAllMetadata(true, false, "foo='bar'", null, null, false);
    }

    @Test
    public void queryAllMostRecentPublished() {
        queryMostRecentHelper("published=true AND deleted!=true", null, true, false);
    }
    
    @Test
    public void queryAllMostRecent() {
        queryMostRecentHelper("deleted!=true", null, false, false);
    }
    
    private void queryMostRecentHelper(String expectedWhereClause, Map<String,Object> parameters, boolean published, boolean includeDeleted) {
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

        when(mockDao.queryMetadata(expectedWhereClause, parameters)).thenReturn(ImmutableList.of(moduleAVersion1, moduleAVersion2,
                moduleBVersion3, moduleBVersion4));

        // execute and validate
        List<SharedModuleMetadata> svcOutputMetadataList = svc.queryAllMetadata(true, published, null, null, null, includeDeleted);
        assertEquals(2, svcOutputMetadataList.size());
        assertTrue(svcOutputMetadataList.contains(moduleAVersion2));
        assertTrue(svcOutputMetadataList.contains(moduleBVersion4));
    }

    @Test
    public void queryAllPublishedAndWhere() {
        queryHelper("foo='bar' AND published=true AND deleted!=true", true, "foo='bar'", null, false);
    }
    
    @Test
    public void queryAllPublishedWithoutWhere() {
        queryHelper("published=true AND deleted!=true", true, null, null, false);
    }
    
    @Test
    public void queryAllWhereWithoutPublished() {
        queryHelper("foo='bar' AND deleted!=true", false, "foo='bar'", null, false);
    }
    
    @Test
    public void queryAllGetAll() {
        queryHelper("deleted!=true", false, null, null, false);
    }
    
    private void queryHelper(String expectedWhereClause, boolean published, String inputWhereClause, Map<String,Object> parameters, boolean includeDeleted) {
        // set up mock dao - Dummy list is fine.
        List<SharedModuleMetadata> daoOutputMetadataList = ImmutableList.of(makeValidMetadata());
        when(mockDao.queryMetadata(expectedWhereClause, parameters)).thenReturn(daoOutputMetadataList);

        // execute and validate
        List<SharedModuleMetadata> svcOutputMetadataList = svc.queryAllMetadata(false, published, inputWhereClause,
                parameters, null, includeDeleted);
        assertSame(daoOutputMetadataList, svcOutputMetadataList);
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
        when(mockDao.queryMetadata("foo='bar' AND deleted!=true", null)).thenReturn(daoOutputMetadataList);

        // execute and validate
        List<SharedModuleMetadata> svcOutputMetadataList = svc.queryAllMetadata(false, false, "foo='bar'", null, tags, false);
        assertEquals(4, svcOutputMetadataList.size());
        assertTrue(svcOutputMetadataList.contains(metadata2));
        assertTrue(svcOutputMetadataList.contains(metadata3));
        assertTrue(svcOutputMetadataList.contains(metadata4));
        assertTrue(svcOutputMetadataList.contains(metadata5));
    }
    
    @Test(expected = BadRequestException.class)
    public void queryByIdNullId() {
        svc.queryMetadataById(null, true, true, "foo='bar'", null, ImmutableSet.of("foo", "bar", "baz"), false);
    }
    
    @Test(expected = BadRequestException.class)
    public void queryByIdEmptyId() {
        svc.queryMetadataById("", true, true, "foo='bar'", null, ImmutableSet.of("foo", "bar", "baz"), false);
    }

    @Test(expected = BadRequestException.class)
    public void queryByIdBlankId() {
        svc.queryMetadataById("   ", true, true, "foo='bar'", null, ImmutableSet.of("foo", "bar", "baz"), false);
    }
    
    @Test
    public void queryByIdSuccess() {
        Set<String> tags = ImmutableSet.of("foo", "bar", "baz");

        // We want 2 different modules with 2 different versions each.
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

        // Spy query all, so we don't have to depend on that complex logic.
        doReturn(ImmutableList.of(moduleAVersion1, moduleAVersion2, moduleBVersion3, moduleBVersion4)).when(svc)
                .queryAllMetadata(true, true, "foo='bar'", null, tags, false);

        // execute and validate
        List<SharedModuleMetadata> svcOutputMetadataList = svc.queryMetadataById("module-B", true, true, "foo='bar'",
                null, tags, false);
        assertEquals(2, svcOutputMetadataList.size());
        assertTrue(svcOutputMetadataList.contains(moduleBVersion3));
        assertTrue(svcOutputMetadataList.contains(moduleBVersion4));
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

    @Test(expected = BadRequestException.class)
    public void updateNotFoundSchema() {
        SharedModuleMetadata svcInputMetadata = makeValidMetadata();
        when(mockDao.getMetadataByIdAndVersion(anyString(), anyInt())).thenReturn(svcInputMetadata);

        when(mockUploadSchemaService.getUploadSchemaByIdAndRev(SHARED_STUDY_ID, SCHEMA_ID, SCHEMA_REV)).thenThrow(EntityNotFoundException.class);
        svc.updateMetadata(MODULE_ID, MODULE_VERSION, svcInputMetadata);
    }

    @Test(expected = BadRequestException.class)
    public void updateNotFoundSurvey() {
        SharedModuleMetadata svcInputMetadata = makeValidMetadata();
        when(mockDao.getMetadataByIdAndVersion(anyString(), anyInt())).thenReturn(svcInputMetadata);
        svcInputMetadata.setSchemaId(null);
        svcInputMetadata.setSchemaRevision(null);
        svcInputMetadata.setSurveyCreatedOn(1L);
        svcInputMetadata.setSurveyGuid("test-survey-guid");
        svc.updateMetadata(MODULE_ID, MODULE_VERSION, svcInputMetadata);
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
    public void updateDeletedFails() {
        SharedModuleMetadata metadata = makeValidMetadata();
        metadata.setDeleted(true);
        when(mockDao.getMetadataByIdAndVersion(MODULE_ID, MODULE_VERSION)).thenReturn(metadata);

        try {
            SharedModuleMetadata updated = SharedModuleMetadata.create();
            updated.setDeleted(true);
            
            // Make obviously invalid metadata, such as one with no fields. (ID and version are set by the service.)
            svc.updateMetadata(MODULE_ID, MODULE_VERSION, updated);
            fail("expected exception");
        } catch (EntityNotFoundException ex) {
        }
    }
    
    @Test
    public void deleteByUpdatedWorks() {
        SharedModuleMetadata metadata = makeValidMetadata();
        when(mockDao.getMetadataByIdAndVersion(MODULE_ID, MODULE_VERSION)).thenReturn(metadata);

        SharedModuleMetadata updated = makeValidMetadata();
        updated.setDeleted(true);
        
        // Make obviously invalid metadata, such as one with no fields. (ID and version are set by the service.)
        svc.updateMetadata(MODULE_ID, MODULE_VERSION, updated);
        
        ArgumentCaptor<SharedModuleMetadata> metadataCaptor = ArgumentCaptor.forClass(SharedModuleMetadata.class);
        
        verify(mockDao).updateMetadata(metadataCaptor.capture());
        assertTrue(metadataCaptor.getValue().isDeleted());        
    }
    
    @Test
    public void undeleteByUpdatedWorks() {
        SharedModuleMetadata metadata = makeValidMetadata();
        metadata.setDeleted(true);
        when(mockDao.getMetadataByIdAndVersion(MODULE_ID, MODULE_VERSION)).thenReturn(metadata);

        SharedModuleMetadata updated = makeValidMetadata();
        
        // Make obviously invalid metadata, such as one with no fields. (ID and version are set by the service.)
        svc.updateMetadata(MODULE_ID, MODULE_VERSION, updated);
        
        ArgumentCaptor<SharedModuleMetadata> metadataCaptor = ArgumentCaptor.forClass(SharedModuleMetadata.class);
        
        verify(mockDao).updateMetadata(metadataCaptor.capture());
        assertFalse(metadataCaptor.getValue().isDeleted());
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void cannotLogicallyDeleteLogicallyDeletedModuleMetadata() throws Exception {
        SharedModuleMetadata metadata = makeValidMetadata();
        metadata.setDeleted(true);
        
        when(mockDao.getMetadataByIdAndVersion(MODULE_ID, MODULE_VERSION)).thenReturn(metadata);
        
        svc.deleteMetadataByIdAndVersion(MODULE_ID, MODULE_VERSION);
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

    static SharedModuleMetadata makeValidMetadata() {
        SharedModuleMetadata metadata = SharedModuleMetadata.create();
        metadata.setId(MODULE_ID);
        metadata.setName(MODULE_NAME);
        metadata.setVersion(MODULE_VERSION);
        metadata.setSchemaId(SCHEMA_ID);
        metadata.setSchemaRevision(SCHEMA_REV);
        return metadata;
    }
}
