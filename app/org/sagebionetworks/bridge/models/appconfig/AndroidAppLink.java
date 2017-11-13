package org.sagebionetworks.bridge.models.appconfig;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Lists;

public class AndroidAppLink {
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
        return Lists.newArrayList(sha256CertFingerprints);
    }
}
