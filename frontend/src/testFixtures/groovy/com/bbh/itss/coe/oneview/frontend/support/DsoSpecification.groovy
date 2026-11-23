package com.bbh.itss.coe.oneview.frontend.support

abstract class OvSpecification extends GuiSpecification {

    static final List<String> MENU = ['Pipelines', 'Self-service', 'Pipeline Monitoring', 'OWASP SAMM', 'Action history', 'Admin']

    static final List<String> SIDE_MENU = ['Main Landing Page', 'DevSecOps', 'QM Metric Tool', 'Agile Assessment & Adoption']

    @Override
    StubApi newApi() {
        new OneViewStubApi()
    }
}
