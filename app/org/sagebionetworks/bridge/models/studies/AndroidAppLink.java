package org.sagebionetworks.bridge.models.studies;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class AndroidAppLink {
    private final String namespace;
    private final String packageName;
    private final List<String> sha256CertFingerprints;
    
    @JsonCreator
    public AndroidAppLink(@JsonProperty("namespace") String namespace, @JsonProperty("package_name") String packageName,
            @JsonProperty("sha256_cert_fingerprints") List<String> sha256CertFingerprints) {
        this.namespace = namespace;
        this.packageName = packageName;
        this.sha256CertFingerprints = sha256CertFingerprints;
    }
    public String getNamespace() {
        return namespace;
    }
    @JsonProperty("package_name") 
    public String getPackageName() {
        return packageName;
    }
    @JsonProperty("sha256_cert_fingerprints")
    public List<String> getFingerprints() {
        return sha256CertFingerprints;
    }
    @Override
    public int hashCode() {
        return Objects.hash(namespace, packageName, sha256CertFingerprints);
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        AndroidAppLink other = (AndroidAppLink) obj;
        return Objects.equals(namespace, other.namespace) && 
            Objects.equals(packageName, other.packageName) && 
            Objects.equals(sha256CertFingerprints, other.sha256CertFingerprints);
    }
}
