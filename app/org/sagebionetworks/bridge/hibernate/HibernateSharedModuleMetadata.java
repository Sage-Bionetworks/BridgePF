package org.sagebionetworks.bridge.hibernate;

import java.util.Set;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.JoinColumn;
import javax.persistence.Table;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.collect.ImmutableSet;

import org.sagebionetworks.bridge.json.DateTimeToLongSerializer;
import org.sagebionetworks.bridge.json.DateTimeToPrimitiveLongDeserializer;
import org.sagebionetworks.bridge.models.sharedmodules.SharedModuleMetadata;

/** Implementation of SharedModuleMetadata, using Hibernate backed by a SQL database. */
@Entity
@IdClass(HibernateSharedModuleMetadataKey.class)
@Table(name = "SharedModuleMetadata")
public class HibernateSharedModuleMetadata implements SharedModuleMetadata {
    private String id;
    private boolean licenseRestricted;
    private String name;
    private String notes;
    private String os;
    private boolean published;
    private String schemaId;
    private Integer schemaRevision;
    private Long surveyCreatedOn;
    private String surveyGuid;
    private Set<String> tags;
    private boolean deleted;
    private int version;

    /** {@inheritDoc} */
    @Id
    @Override
    public String getId() {
        return id;
    }

    /** {@inheritDoc} */
    @Override
    public void setId(String id) {
        this.id = id;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isLicenseRestricted() {
        return licenseRestricted;
    }

    /** {@inheritDoc} */
    @Override
    public void setLicenseRestricted(boolean licenseRestricted) {
        this.licenseRestricted = licenseRestricted;
    }

    /** {@inheritDoc} */
    @Override
    public String getName() {
        return name;
    }

    /** {@inheritDoc} */
    @Override
    public void setName(String name) {
        this.name = name;
    }

    /** {@inheritDoc} */
    @Override
    public String getNotes() {
        return notes;
    }

    /** {@inheritDoc} */
    @Override
    public void setNotes(String notes) {
        this.notes = notes;
    }

    /** {@inheritDoc} */
    @Override
    public String getOs() {
        return os;
    }

    /** {@inheritDoc} */
    @Override
    public void setOs(String os) {
        this.os = os;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isPublished() {
        return published;
    }

    /** {@inheritDoc} */
    @Override
    public void setPublished(boolean published) {
        this.published = published;
    }

    /** {@inheritDoc} */
    @Override
    public String getSchemaId() {
        return schemaId;
    }

    /** {@inheritDoc} */
    @Override
    public void setSchemaId(String schemaId) {
        this.schemaId = schemaId;
    }

    /** {@inheritDoc} */
    @Override
    public Integer getSchemaRevision() {
        return schemaRevision;
    }

    /** {@inheritDoc} */
    @Override
    public void setSchemaRevision(Integer schemaRevision) {
        this.schemaRevision = schemaRevision;
    }

    /** {@inheritDoc} */
    @JsonSerialize(using = DateTimeToLongSerializer.class)
    @Override
    public Long getSurveyCreatedOn() {
        return surveyCreatedOn;
    }

    /** {@inheritDoc} */
    @JsonDeserialize(using = DateTimeToPrimitiveLongDeserializer.class)
    @Override
    public void setSurveyCreatedOn(Long surveyCreatedOn) {
        this.surveyCreatedOn = surveyCreatedOn;
    }

    /** {@inheritDoc} */
    @Override
    public String getSurveyGuid() {
        return surveyGuid;
    }

    /** {@inheritDoc} */
    @Override
    public void setSurveyGuid(String surveyGuid) {
        this.surveyGuid = surveyGuid;
    }

    /** {@inheritDoc} */
    @CollectionTable(name = "SharedModuleTags",
            joinColumns = { @JoinColumn(name = "id"), @JoinColumn(name = "version") })
    @Column(name = "tag")
    @ElementCollection(fetch = FetchType.EAGER)
    @Override
    public Set<String> getTags() {
        // Never return null.
        return tags != null ? tags : ImmutableSet.of();
    }

    /** {@inheritDoc} */
    @Override
    public void setTags(Set<String> tags) {
        // Normally we'd want to make this an immutable copy so that (1) modifying the original set doesn't change this
        // object and (2) callers who call getTag() can't modify the value. However, Hibernate does really weird things
        // if we try to copy this to an ImmutableSet, so we'll just use the value as is.
        this.tags = tags;
    }
    
    /** {@inheritDoc} */
    @Override
    public boolean isDeleted() {
        return deleted;
    }
    
    /** {@inheritDoc} */
    @Override
    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    /** {@inheritDoc} */
    @Id
    @Override
    public int getVersion() {
        return version;
    }

    /** {@inheritDoc} */
    @Override
    public void setVersion(int version) {
        this.version = version;
    }
}
