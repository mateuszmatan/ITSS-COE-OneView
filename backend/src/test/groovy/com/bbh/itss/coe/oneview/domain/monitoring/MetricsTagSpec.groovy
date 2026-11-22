package com.bbh.itss.coe.oneview.domain.monitoring

import com.bbh.itss.coe.oneview.domain.catalog.MetricsSettings
import spock.lang.Specification

import static com.bbh.itss.coe.oneview.domain.pipeline.PipelineType.FULL
import static com.bbh.itss.coe.oneview.domain.pipeline.PipelineType.NEXUS_IQ
import static com.bbh.itss.coe.oneview.domain.pipeline.PipelineType.SAST
import static com.bbh.itss.coe.oneview.support.Fixtures.pipeline
import static com.bbh.itss.coe.oneview.support.Fixtures.product

class MetricsTagSpec extends Specification {

    def "a #type pipeline is tagged #project in the environment of its service"() {
        given:
        def service = product(code: 'CERT', services: [[name: 'gui', id: 10L,
                metrics: new MetricsSettings(true, null, 'uat', null, null)]]).services()[0]

        expect:
        MetricsTag.of(service, pipeline(serviceId: 10L, type: type)) == new MetricsTag(project, 'uat')

        where:
        type     || project
        FULL     || 'CERT-gui'
        SAST     || 'CERT-guisast'
        NEXUS_IQ || 'CERT-guinexusiq'
    }
}
