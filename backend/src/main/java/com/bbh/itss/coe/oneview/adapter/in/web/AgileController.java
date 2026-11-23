package com.bbh.itss.coe.oneview.adapter.in.web;

import com.bbh.itss.coe.oneview.application.actionlog.port.in.ActionRecord;
import com.bbh.itss.coe.oneview.application.agile.port.in.AgileApplicationView;
import com.bbh.itss.coe.oneview.application.agile.port.in.AgileAssessmentUseCase;
import com.bbh.itss.coe.oneview.domain.agile.AgileAssessment;
import com.bbh.itss.coe.oneview.domain.agile.AgileDimension;
import com.bbh.itss.coe.oneview.domain.agile.AgileFramework;
import com.bbh.itss.coe.oneview.domain.agile.AgileProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.ResponseEntity.created;
import static org.springframework.web.servlet.support.ServletUriComponentsBuilder.fromCurrentContextPath;

/** Agile Assessment &amp; Adoption: how every application takes part and the maturity assessments of its team. */
@RestController
@RequestMapping("/api/agile")
@RequiredArgsConstructor
public class AgileController {

    private static final String APPLICATION = "AGILE_APPLICATION";
    private static final String ASSESSMENT = "AGILE_ASSESSMENT";

    private final AgileAssessmentUseCase agile;
    private final ActionRecorder actions;

    @GetMapping("/model")
    public ModelResponse model() {
        return new ModelResponse(
                Arrays.stream(AgileDimension.values())
                        .map(dimension -> new Choice(dimension.name(), dimension.label(), dimension.description()))
                        .toList(),
                Arrays.stream(AgileFramework.values())
                        .map(framework -> new Choice(framework.name(), framework.label(), null))
                        .toList(),
                AgileAssessment.MIN_LEVEL, AgileAssessment.MAX_LEVEL);
    }

    @GetMapping("/applications")
    public List<ApplicationResponse> applications() {
        return agile.applications().stream().map(ApplicationResponse::of).toList();
    }

    @GetMapping("/applications/{productId}")
    public ApplicationResponse application(@PathVariable long productId) {
        return ApplicationResponse.of(agile.application(productId));
    }

    @PutMapping("/applications/{productId}")
    public ApplicationResponse changeProfile(@PathVariable long productId, @RequestBody ProfileRequest request) {
        AgileApplicationView current = agile.application(productId);
        return actions.record(ActionRecord.of("UPDATE_AGILE_APPLICATION", APPLICATION, current.name(),
                                current.departmentId()).withProductId(productId),
                () -> ApplicationResponse.of(agile.changeProfile(productId, request.version(),
                        request.profile(productId))),
                (action, saved) -> action.done(saved.enabled()
                        ? "Takes part in Agile Assessment & Adoption" + team(saved)
                        : "Does not take part in Agile Assessment & Adoption"));
    }

    @PostMapping("/applications/{productId}/assessments")
    public ResponseEntity<AssessmentResponse> recordAssessment(@PathVariable long productId,
                                                               @RequestBody AssessmentRequest request) {
        AgileApplicationView application = agile.application(productId);
        AssessmentResponse saved = actions.record(ActionRecord.of("RECORD_AGILE_ASSESSMENT", ASSESSMENT,
                                application.name(), application.departmentId()).withProductId(productId),
                () -> AssessmentResponse.of(agile.recordAssessment(productId, request.assessedOn(),
                        request.assessor(), request.levels(), request.notes())),
                (action, assessment) -> action.done("Recorded the assessment of " + assessment.assessedOn() + " by "
                        + assessment.assessor() + ": overall level " + assessment.overallLevel()));
        return created(fromCurrentContextPath().path("/api/agile/assessments/{id}").buildAndExpand(saved.id()).toUri())
                .body(saved);
    }

    @GetMapping("/assessments/{id}")
    public AssessmentResponse assessment(@PathVariable long id) {
        return AssessmentResponse.of(agile.assessment(id));
    }

    @DeleteMapping("/assessments/{id}")
    @ResponseStatus(NO_CONTENT)
    public void deleteAssessment(@PathVariable long id) {
        AgileAssessment assessment = agile.assessment(id);
        AgileApplicationView application = agile.application(assessment.productId());
        actions.record(ActionRecord.of("DELETE_AGILE_ASSESSMENT", ASSESSMENT, application.name(),
                        application.departmentId()).withProductId(application.productId()),
                () -> agile.deleteAssessment(id),
                "Deleted the assessment of " + assessment.assessedOn() + " by " + assessment.assessor());
    }

    private static String team(ApplicationResponse saved) {
        return saved.teamName() == null ? "" : " with the team " + saved.teamName();
    }

    public record Choice(String code, String label, String description) {
    }

    public record ModelResponse(List<Choice> dimensions, List<Choice> frameworks, int minLevel, int maxLevel) {
    }

    public record ProfileRequest(boolean enabled, String teamName, AgileFramework framework, String coach,
                                 Long version) {

        AgileProfile profile(long productId) {
            return new AgileProfile(productId, enabled, teamName, framework, coach, version, null);
        }
    }

    public record AssessmentRequest(LocalDate assessedOn, String assessor, Map<AgileDimension, Integer> levels,
                                    String notes) {
    }

    public record AssessmentResponse(long id, long productId, LocalDate assessedOn, String assessor,
                                     Map<AgileDimension, Integer> levels, BigDecimal overallLevel, String notes) {

        static AssessmentResponse of(AgileAssessment assessment) {
            return new AssessmentResponse(assessment.id(), assessment.productId(), assessment.assessedOn(),
                    assessment.assessor(), assessment.levels(), assessment.overallLevel(), assessment.notes());
        }
    }

    public record ApplicationResponse(long productId, String code, String name, Long departmentId,
                                      String departmentName, boolean enabled, String teamName,
                                      AgileFramework framework, String coach, Long version, Instant updatedAt,
                                      List<AssessmentResponse> assessments) {

        static ApplicationResponse of(AgileApplicationView view) {
            AgileProfile profile = view.profile();
            return new ApplicationResponse(view.productId(), view.code(), view.name(), view.departmentId(),
                    view.departmentName(), profile.enabled(), profile.teamName(), profile.framework(),
                    profile.coach(), profile.version(), profile.updatedAt(),
                    view.assessments().stream().map(AssessmentResponse::of).toList());
        }
    }
}
