package com.bbh.itss.coe.oneview.application.discovery.port.out;

import com.bbh.itss.coe.oneview.domain.discovery.RepoFileTree;

public interface BitbucketRepositoryPort {

    RepoFileTree fetchRepositoryTree(String repositoryUrl, String branch, String credentialsId);
}
