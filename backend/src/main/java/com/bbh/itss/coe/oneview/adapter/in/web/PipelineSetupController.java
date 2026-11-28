package com.bbh.itss.coe.oneview.adapter.in.web;

import com.bbh.itss.coe.oneview.application.actionlog.port.in.ActionRecord;
import com.bbh.itss.coe.oneview.application.jenkins.port.in.GuidedPipelineSetupUseCase;
import com.bbh.itss.coe.oneview.application.jenkins.port.in.GuidedPipelineSetupUseCase.CreateJenkinsJobCommand;
import com.bbh.itss.coe.oneview.application.jenkins.port.in.GuidedPipelineSetupUseCase.CreatedJenkinsJob;
import com.bbh.itss.coe.oneview.application.jenkins.port.in.GuidedPipelineSetupUseCase.SourceInspection;
import com.bbh.itss.coe.oneview.application.pipeline.port.in.PipelineView;
import com.bbh.itss.coe.oneview.application.pipeline.port.in.PipelinesUseCase;
import com.bbh.itss.coe.oneview.domain.jenkins.BuildRetention;
import com.bbh.itss.coe.oneview.domain.jenkins.JenkinsJobSnapshot;
import com.bbh.itss.coe.oneview.domain.jenkins.JobKind;
import com.bbh.itss.coe.oneview.domain.jenkins.JobParameter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.bbh.itss.coe.oneview.adapter.in.web.InputFormats.URL;
import static com.bbh.itss.coe.oneview.adapter.in.web.InputFormats.URL_MESSAGE;
import static org.springframework.http.HttpStatus.CREATED;

/**
 * What the guided set-up of a pipeline does outside OneView. The two reading calls are POSTs only because they carry
 * addresses in their bodies: they change nothing, in OneView, Bitbucket or Jenkins.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PipelineSetupController {

    private final GuidedPipelineSetupUseCase setup;
    private final PipelinesUseCase pipelines;
    private final ActionRecorder actions;

    @PostMapping("/pipeline-setup/sources")
    public SourceInspection inspectSources(@Valid @RequestBody SourcesRequest request) {
        return setup.inspectSources(request.repositoryUrl(), request.branch());
    }

    @PostMapping("/pipeline-setup/jenkins-job")
    public JenkinsJobSnapshot readJenkinsJob(@Valid @RequestBody JobRequest request) {
        return setup.readJenkinsJob(request.jobUrl());
    }

    @PostMapping("/pipelines/{id}/jenkins-job")
    @ResponseStatus(CREATED)
    public CreatedJenkinsJob createJenkinsJob(@PathVariable long id, @Valid @RequestBody CreateJobRequest request) {
        PipelineView view = pipelines.get(id);
        ActionRecord action = ActionRecord.of("CREATE_JENKINS_JOB", "PIPELINE", null, null)
                .withResourceName(view.service().name() + " · " + view.pipeline().type().label())
                .withDepartmentId(view.product().departmentId()).withProductId(view.product().id());
        return actions.record(action, () -> setup.createJenkinsJob(id, request.toCommand()),
                (recorded, created) -> recorded.done("Created the Jenkins job " + created.jobUrl()));
    }

    public record SourcesRequest(@NotBlank @Size(max = 1000) String repositoryUrl, @Size(max = 500) String branch) {
    }

    public record JobRequest(@NotBlank @Size(max = 2000) @Pattern(regexp = URL, message = URL_MESSAGE) String jobUrl) {
    }

    public record CreateJobRequest(
            @NotBlank @Size(max = 500) @Pattern(regexp = URL, message = URL_MESSAGE) String jenkinsUrl,
            @Size(max = 500) String folder,
            @NotBlank @Size(max = 200) String name,
            @NotNull JobKind kind,
            @NotBlank @Size(max = 1000) String repositoryUrl,
            @Size(max = 500) String branch,
            @Size(max = 500) String scriptPath,
            @Size(max = 200) String credentialsId,
            @Size(max = 4000) String description,
            @Size(max = 100) List<@NotNull @Valid ParameterDto> parameters,
            Integer daysToKeep,
            Integer buildsToKeep,
            Boolean concurrentBuildsDisabled,
            @Size(max = 100) String timerSchedule) {

        CreateJenkinsJobCommand toCommand() {
            return new CreateJenkinsJobCommand(jenkinsUrl, folder, name, kind, repositoryUrl, branch, scriptPath,
                    credentialsId, description,
                    parameters == null ? List.of() : parameters.stream().map(ParameterDto::toParameter).toList(),
                    new BuildRetention(daysToKeep, buildsToKeep), Boolean.TRUE.equals(concurrentBuildsDisabled),
                    timerSchedule);
        }
    }

    public record ParameterDto(@NotNull JobParameter.Type type,
                               @NotBlank @Size(max = 200) @Pattern(regexp = "^[A-Za-z_][A-Za-z0-9_.-]*$",
                                       message = "must be a Jenkins parameter name") String name,
                               @Size(max = 4000) String defaultValue,
                               @Size(max = 4000) String description,
                               @Size(max = 200) List<@NotBlank @Size(max = 500) String> choices) {

        JobParameter toParameter() {
            return new JobParameter(type, name, defaultValue, description, choices);
        }
    }
}
