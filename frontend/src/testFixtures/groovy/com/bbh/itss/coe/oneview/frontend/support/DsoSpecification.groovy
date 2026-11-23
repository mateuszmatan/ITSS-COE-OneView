package com.bbh.itss.coe.oneview.frontend.support

import com.microsoft.playwright.Locator

abstract class OvSpecification extends GuiSpecification {

    /** The sections of the DevSecOps area, in the menu at the top while one of them is open. */
    static final List<String> MENU = ['Pipelines', 'Self-service', 'Pipeline Monitoring', 'OWASP SAMM']

    /** The areas of OneView, in the side menu. */
    static final List<String> AREAS = ['DevSecOps', 'QM Metric Tool', 'Agile Assessment & Adoption']

    /** The pages of every area, below the areas in the side menu. */
    static final List<String> TOOLS = ['Action history', 'Admin']

    static final List<String> SIDE_MENU = ['Main Landing Page'] + AREAS + TOOLS

    @Override
    StubApi newApi() {
        new OneViewStubApi()
    }

    /** The business service and application chosen above the pages that follow them. */
    Locator contextBar() {
        page.locator('ov-context-bar')
    }

    void chooseBusinessService(String name) {
        choose(contextBar(), 'Business service', name)
    }

    void chooseApplication(String name) {
        choose(contextBar(), 'Application', name)
    }
}
