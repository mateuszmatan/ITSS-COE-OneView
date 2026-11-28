package com.bbh.itss.coe.oneview.domain.jenkins;

import static java.util.Objects.requireNonNull;

/** The generic user OneView acts as in Jenkins and Bitbucket, with the secret fetched from OIS for this request. */
public record GenericLogin(String user, String secret) {

    public GenericLogin {
        requireNonNull(user, "a login needs its user");
        requireNonNull(secret, "a login needs its secret");
    }

    /** Never prints the secret, so that it cannot reach a log. */
    @Override
    public String toString() {
        return "GenericLogin[user=" + user + "]";
    }
}
