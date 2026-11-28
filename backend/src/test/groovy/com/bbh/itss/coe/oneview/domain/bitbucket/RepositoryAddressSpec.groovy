package com.bbh.itss.coe.oneview.domain.bitbucket

import spock.lang.Specification

class RepositoryAddressSpec extends Specification {

    def "a repository on Bitbucket Server is read from its page or from its clone address"() {
        when:
        def address = RepositoryAddress.parse(url).get()

        then:
        !address.cloud()
        address.baseUrl() == base
        address.project() == project
        address.slug() == 'payments-api'
        address.cloneUrl() == clone

        where:
        url                                                                    | base                              | project | clone
        'https://bitbucket.bbh.com/projects/PAY/repos/payments-api/browse'     | 'https://bitbucket.bbh.com'       | 'PAY'   | 'https://bitbucket.bbh.com/scm/pay/payments-api.git'
        'https://bitbucket.bbh.com/projects/PAY/repos/payments-api'            | 'https://bitbucket.bbh.com'       | 'PAY'   | 'https://bitbucket.bbh.com/scm/pay/payments-api.git'
        ' https://bitbucket.bbh.com/scm/pay/payments-api.git '                 | 'https://bitbucket.bbh.com'       | 'pay'   | 'https://bitbucket.bbh.com/scm/pay/payments-api.git'
        'https://jdoe@bitbucket.bbh.com/scm/pay/payments-api.git'              | 'https://bitbucket.bbh.com'       | 'pay'   | 'https://bitbucket.bbh.com/scm/pay/payments-api.git'
        'https://tools.bbh.com/bitbucket/scm/PAY/payments-api'                 | 'https://tools.bbh.com/bitbucket' | 'PAY'   | 'https://tools.bbh.com/bitbucket/scm/pay/payments-api.git'
        'https://bitbucket.bbh.com/users/jdoe/repos/payments-api/browse'       | 'https://bitbucket.bbh.com'       | '~jdoe' | 'https://bitbucket.bbh.com/scm/~jdoe/payments-api.git'
    }

    def "a repository on Bitbucket Cloud has no base address"() {
        when:
        def address = RepositoryAddress.parse(url).get()

        then:
        address.cloud()
        address.project() == 'bbh'
        address.slug() == 'payments-api'
        address.cloneUrl() == 'https://bitbucket.org/bbh/payments-api.git'

        where:
        url << ['https://bitbucket.org/bbh/payments-api', 'https://jdoe@bitbucket.org/bbh/payments-api.git',
                'https://bitbucket.org/bbh/payments-api/src/main/', 'https://api.bitbucket.org/2.0/repositories/bbh/payments-api']
    }

    def "what is not a Bitbucket address is not a repository, and is cloned as given"() {
        expect:
        RepositoryAddress.parse(url) == Optional.empty()
        RepositoryAddress.cloneUrlOf(url) == clone

        where:
        url                                       | clone
        null                                      | ''
        ' https://github.com/bbh/payments.git '   | 'https://github.com/bbh/payments.git'
        'ssh://git@bitbucket.bbh.com:7999/p/a.git' | 'ssh://git@bitbucket.bbh.com:7999/p/a.git'
    }

    def "the page of a repository is cloned from its clone address"() {
        expect:
        RepositoryAddress.cloneUrlOf('https://bitbucket.bbh.com/projects/PAY/repos/payments-api/browse') ==
                'https://bitbucket.bbh.com/scm/pay/payments-api.git'
    }
}
