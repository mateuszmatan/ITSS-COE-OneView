--liquibase formatted sql

-- How OneView reaches Jenkins and Bitbucket by itself for the guided set-up of a pipeline: the generic user, the OIS
-- host and the OIS API key its secret is fetched with, the schedule on which a job it creates looks for changes, and
-- the Git repository of the DevSecOps library for a Multibranch Pipeline job. The secret of the generic user is never
-- stored: OneView fetches it from OIS with its certificate for every request.

--changeset oneview:053-jenkins-automation dbms:oracle,h2
CREATE TABLE DSO_JENKINS_AUTOMATION (
    ID                          NUMBER(19)      NOT NULL,
    GENERIC_USER                VARCHAR2(200),
    OIS_HOST                    VARCHAR2(255),
    OIS_API_KEY                 VARCHAR2(500),
    POLL_SCHEDULE               VARCHAR2(100)   NOT NULL,
    LIBRARY_REPOSITORY_URL      VARCHAR2(1000),
    LIBRARY_VERSION             VARCHAR2(200)   NOT NULL,
    LIBRARY_CREDENTIALS_ID      VARCHAR2(200),
    CREATED_AT                  TIMESTAMP       NOT NULL,
    UPDATED_AT                  TIMESTAMP       NOT NULL,
    VERSION                     NUMBER(19)      DEFAULT 0 NOT NULL,
    CONSTRAINT PK_DSO_JENKINS_AUTOMATION PRIMARY KEY (ID),
    CONSTRAINT CK_DSO_JENKINS_AUTOMATION_ONE_ROW CHECK (ID = 1)
);
--rollback DROP TABLE DSO_JENKINS_AUTOMATION;
