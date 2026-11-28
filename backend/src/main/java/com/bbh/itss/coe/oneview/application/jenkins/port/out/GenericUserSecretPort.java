package com.bbh.itss.coe.oneview.application.jenkins.port.out;

/** The secret of the generic user, fetched from OIS with the certificate of OneView; it is never stored. */
public interface GenericUserSecretPort {

    String fetch(String oisHost, String apiKey);
}
