package com.bbh.itss.coe.oneview.domain.jenkins;

/** A shared library that a Multibranch Pipeline job loads from Git by itself, under the name its Jenkinsfile uses. */
public record FolderLibrary(String name, String repositoryUrl, String version, String credentialsId) {
}
