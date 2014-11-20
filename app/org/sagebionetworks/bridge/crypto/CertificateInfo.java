package org.sagebionetworks.bridge.crypto;

/**
 * Captures the minimum information asked by the openssl interactive interface
 * when creating a self-signed certificate.
 * <p>
 * Example:
 * <p>
 * <li>Country Name (2 letter code) [AU]:US
 * <li>State or Province Name (full name) [Some-State]:WA
 * <li>Locality Name (eg, city) []:Seattle
 * <li>Organization Name (eg, company) [Internet Widgits Pty Ltd]:Sage Bionetworks
 * <li>Organizational Unit Name (eg, section) []:Platform
 * <li>Common Name (e.g. server FQDN or YOUR name) []:www.sagebridge.org
 * <li>Email Address []:bridge@sagebridge.org
 */
class CertificateInfo {

    private String country;
    private String state;
    private String city;
    private String organization;
    private String team;
    private String fqdn;
    private String email;

    String getCountry() {
        return country;
    }
    void setCountry(String country) {
        this.country = country;
    }
    CertificateInfo withCountry(String country) {
        setCountry(country);
        return this;
    }

    String getState() {
        return state;
    }
    void setState(String state) {
        this.state = state;
    }
    CertificateInfo withState(String state) {
        setState(state);
        return this;
    }

    String getCity() {
        return city;
    }
    void setCity(String city) {
        this.city = city;
    }
    CertificateInfo withCity(String city) {
        setCity(city);
        return this;
    }

    String getOrganization() {
        return organization;
    }
    void setOrganization(String organization) {
        this.organization = organization;
    }
    CertificateInfo withOrganization(String organization) {
        setOrganization(organization);
        return this;
    }

    String getTeam() {
        return team;
    }
    void setTeam(String team) {
        this.team = team;
    }
    CertificateInfo withTeam(String team) {
        setTeam(team);
        return this;
    }

    String getFqdn() {
        return fqdn;
    }
    void setFqdn(String fqdn) {
        this.fqdn = fqdn;
    }
    CertificateInfo withFqdn(String fqdn) {
        setFqdn(fqdn);
        return this;
    }

    String getEmail() {
        return email;
    }
    void setEmail(String email) {
        this.email = email;
    }
    CertificateInfo withEmail(String email) {
        setEmail(email);
        return this;
    }
}
