package com.bbh.itss.coe.oneview.adapter.in.web;

import com.bbh.itss.coe.oneview.application.actionlog.port.in.ActionRecord;
import com.bbh.itss.coe.oneview.application.qa.port.in.QualityApplicationView;
import com.bbh.itss.coe.oneview.application.qa.port.in.QualityManagementUseCase;
import com.bbh.itss.coe.oneview.domain.qa.QualityDefaults;
import com.bbh.itss.coe.oneview.domain.qa.QualityProfile;
import com.bbh.itss.coe.oneview.domain.qa.QualityTargets;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/** The QM Metric Tool: the quality targets of BBH and how every application takes part. */
@RestController
@RequestMapping("/api/qa")
@RequiredArgsConstructor
public class QualityController {

    private final QualityManagementUseCase quality;
    private final ActionRecorder actions;

    @GetMapping("/defaults")
    public DefaultsResponse defaults() {
        return DefaultsResponse.of(quality.defaults());
    }

    @PutMapping("/defaults")
    public DefaultsResponse changeDefaults(@RequestBody DefaultsRequest request) {
        return actions.record(ActionRecord.of("UPDATE_QA_DEFAULTS", "QA_SETTINGS", "QM Metric Tool targets", null),
                () -> DefaultsResponse.of(quality.changeDefaults(request.version(), request.targets())),
                (action, saved) -> action.done("Saved the BBH quality targets: " + describe(saved.targets())));
    }

    @GetMapping("/applications")
    public List<ApplicationResponse> applications() {
        return quality.applications().stream().map(ApplicationResponse::of).toList();
    }

    @GetMapping("/applications/{productId}")
    public ApplicationResponse application(@PathVariable long productId) {
        return ApplicationResponse.of(quality.application(productId));
    }

    @PutMapping("/applications/{productId}")
    public ApplicationResponse change(@PathVariable long productId, @RequestBody ApplicationRequest request) {
        QualityApplicationView current = quality.application(productId);
        return actions.record(ActionRecord.of("UPDATE_QA_APPLICATION", "QA_APPLICATION", current.name(),
                                current.departmentId()).withProductId(productId),
                () -> ApplicationResponse.of(quality.change(productId, request.version(), request.profile(productId))),
                (action, saved) -> action.done(saved.enabled()
                        ? "Takes part in the QM Metric Tool with the targets " + describe(saved.effectiveTargets())
                        : "Does not take part in the QM Metric Tool"));
    }

    static String describe(QualityTargets targets) {
        return "code coverage at least " + targets.coveragePercent() + "%, test pass rate at least "
                + targets.passRatePercent() + "%, at most " + targets.maxCriticalDefects() + " open critical defects";
    }

    /** The targets as the defaults read them, and the version they were read at. */
    public record DefaultsRequest(QualityTargets targets, Long version) {
    }

    public record DefaultsResponse(QualityTargets targets, Long version, Instant updatedAt) {

        static DefaultsResponse of(QualityDefaults defaults) {
            return new DefaultsResponse(defaults.targets(), defaults.version(), defaults.updatedAt());
        }
    }

    public record ApplicationRequest(boolean enabled, String qaLead, String sonarProjectKey, String defectProjectKey,
                                     String dashboardUrl, QualityTargets targets, Long version) {

        QualityProfile profile(long productId) {
            return new QualityProfile(productId, enabled, qaLead, sonarProjectKey, defectProjectKey, dashboardUrl,
                    targets, version, null);
        }
    }

    public record ApplicationResponse(long productId, String code, String name, Long departmentId,
                                      String departmentName, boolean enabled, String qaLead, String sonarProjectKey,
                                      String defectProjectKey, String dashboardUrl, QualityTargets targets,
                                      QualityTargets effectiveTargets, Long version, Instant updatedAt) {

        static ApplicationResponse of(QualityApplicationView view) {
            QualityProfile profile = view.profile();
            return new ApplicationResponse(view.productId(), view.code(), view.name(), view.departmentId(),
                    view.departmentName(), profile.enabled(), profile.qaLead(), profile.sonarProjectKey(),
                    profile.defectProjectKey(), profile.dashboardUrl(), profile.targets(), view.effectiveTargets(),
                    profile.version(), profile.updatedAt());
        }
    }
}
