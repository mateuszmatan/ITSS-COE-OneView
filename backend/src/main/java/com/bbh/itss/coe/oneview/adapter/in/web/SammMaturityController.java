package com.bbh.itss.coe.oneview.adapter.in.web;

import com.bbh.itss.coe.oneview.application.samm.port.in.GetSammMaturityUseCase;
import com.bbh.itss.coe.oneview.domain.samm.SammApplicationScore;
import com.bbh.itss.coe.oneview.domain.samm.SammDepartmentScore;
import com.bbh.itss.coe.oneview.domain.samm.SammOrganizationOverview;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/samm")
@RequiredArgsConstructor
public class SammMaturityController {

    private final GetSammMaturityUseCase samm;

    @GetMapping("/overview")
    public SammOrganizationOverview overview(@RequestParam(required = false) Long departmentId,
                                             @RequestParam(required = false) Long productId) {
        return samm.overview(departmentId, productId);
    }

    @GetMapping("/departments")
    public List<SammDepartmentScore> departments() {
        return samm.departmentScores();
    }

    @GetMapping("/departments/{departmentId}")
    public ResponseEntity<SammDepartmentScore> department(@PathVariable Long departmentId) {
        return samm.departmentScore(departmentId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @GetMapping("/pipelines/{pipelineId}")
    public ResponseEntity<SammApplicationScore> application(@PathVariable Long pipelineId) {
        return samm.applicationScore(pipelineId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }
}
