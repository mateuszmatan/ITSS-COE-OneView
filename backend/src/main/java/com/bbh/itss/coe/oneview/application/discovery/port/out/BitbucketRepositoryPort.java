package com.bbh.itss.coe.oneview.application.discovery.port.out;

import com.bbh.itss.coe.oneview.domain.discovery.RepoFileTree;
import com.bbh.itss.coe.oneview.domain.jenkins.GenericLogin;

public interface BitbucketRepositoryPort {

    RepoFileTree fetchRepositoryTree(String repositoryUrl, String branch, String credentialsId);

    /**
     * Reads the files at the root of the repository and the contents of those that tell its stack and its pipeline,
     * as the login or anonymously without one. Changes nothing. Fails with the reason when the repository cannot be
     * read, and never guesses.
     */
    RepoFileTree readSources(String repositoryUrl, String branch, GenericLogin login);
}
