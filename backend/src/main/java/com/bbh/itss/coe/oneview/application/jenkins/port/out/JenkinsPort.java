package com.bbh.itss.coe.oneview.application.jenkins.port.out;

import com.bbh.itss.coe.oneview.domain.jenkins.GenericLogin;
import com.bbh.itss.coe.oneview.domain.jenkins.JenkinsJobAddress;
import com.bbh.itss.coe.oneview.domain.jenkins.JenkinsJobSnapshot;
import com.bbh.itss.coe.oneview.domain.jenkins.JenkinsJobSpec;

/**
 * Jenkins as OneView uses it. Reading changes nothing in Jenkins; the only change OneView ever makes is to create a
 * new job, with the folders it is in when they are missing. It never changes or removes what exists. A login of null
 * reads anonymously.
 */
public interface JenkinsPort {

    /** The name Jenkins knows the login by: proof that the address, the user and the secret work together. */
    String whoAmI(String jenkinsUrl, GenericLogin login);

    JenkinsJobSnapshot readJob(JenkinsJobAddress job, GenericLogin login);

    boolean exists(JenkinsJobAddress job, GenericLogin login);

    /** Creates the job in the folder, and the folders that are missing; directly in Jenkins without names. */
    void createJob(JenkinsJobAddress folder, JenkinsJobSpec job, GenericLogin login);
}
