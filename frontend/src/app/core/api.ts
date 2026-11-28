import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { ContextScope, scopeParams } from '@common/context/oneview-context';
import {
  ActionLog,
  AgileApplication,
  AgileAssessment,
  AgileAssessmentRequest,
  AgileModel,
  AgileProfileRequest,
  DepartmentPipelines,
  GlobalSettings,
  GlobalSettingsRequest,
  MonitoringOverview,
  MonitoringStatus,
  Pipeline,
  PipelineMonitoring,
  PipelineReport,
  PipelineRequest,
  PipelineType,
  PortfolioActivity,
  Product,
  QualityApplication,
  QualityApplicationRequest,
  QualityDefaults,
  QualityDefaultsRequest,
  ProductMonitoring,
  ProductRequest,
  ProductSummary,
  PullRequestDecoration,
  ServiceConfigImport,
  ServicePipelines,
  ServiceTemplate,
  ServiceTemplateRequest,
  UserProfile,
  DiscoveredStack,
  InspectRepoRequest,
  SammApplicationScore,
  SammDepartmentScore,
  SammOrganizationOverview,
  ConnectionCheck,
  CreateJenkinsJobRequest,
  CreatedJenkinsJob,
  JenkinsAutomation,
  JenkinsAutomationRequest,
  JenkinsJobSnapshot,
  SourceInspection,
} from './models';

function typeParam(pipelineType?: PipelineType): HttpParams | undefined {
  return pipelineType ? new HttpParams().set('pipelineType', pipelineType) : undefined;
}

@Injectable({ providedIn: 'root' })
export class ProductsApi {
  private readonly http = inject(HttpClient);

  list(search?: string): Observable<ProductSummary[]> {
    const params = search ? new HttpParams().set('search', search) : undefined;
    return this.http.get<ProductSummary[]>('/api/products', { params });
  }

  get(id: number): Observable<Product> {
    return this.http.get<Product>(`/api/products/${id}`);
  }

  create(request: ProductRequest, pipelineType?: PipelineType): Observable<Product> {
    return this.http.post<Product>('/api/products', request, { params: typeParam(pipelineType) });
  }

  update(id: number, request: ProductRequest, pipelineType?: PipelineType): Observable<Product> {
    return this.http.put<Product>(`/api/products/${id}`, request, {
      params: typeParam(pipelineType),
    });
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`/api/products/${id}`);
  }

  suggestCode(name: string): Observable<string> {
    return this.http
      .get<{ code: string }>('/api/products/code-suggestion', {
        params: new HttpParams().set('name', name),
      })
      .pipe(map((suggestion) => suggestion.code));
  }

  config(id: number): Observable<string> {
    return this.http.get(`/api/products/${id}/config`, { responseType: 'text' });
  }

  importServiceConfig(
    content: string,
    serviceName: string | null,
  ): Observable<ServiceConfigImport> {
    return this.http.post<ServiceConfigImport>('/api/service-configs/import', {
      content,
      serviceName,
    });
  }

  inspectRepo(request: InspectRepoRequest): Observable<DiscoveredStack> {
    return this.http.post<DiscoveredStack>('/api/catalog/inspect-repo', request);
  }
}

@Injectable({ providedIn: 'root' })
export class PipelinesApi {
  private readonly http = inject(HttpClient);

  /** The pipelines of one business service, or of every one without it. */
  listForDepartment(departmentId?: number | null): Observable<DepartmentPipelines> {
    const params = departmentId == null ? undefined : { departmentId };
    return this.http.get<DepartmentPipelines>('/api/pipelines', { params });
  }

  listForProduct(productId: number): Observable<ServicePipelines[]> {
    return this.http.get<ServicePipelines[]>(`/api/products/${productId}/pipelines`);
  }

  get(id: number): Observable<Pipeline> {
    return this.http.get<Pipeline>(`/api/pipelines/${id}`);
  }

  create(serviceId: number, request: PipelineRequest): Observable<Pipeline> {
    return this.http.post<Pipeline>(`/api/services/${serviceId}/pipelines`, request);
  }

  update(id: number, request: PipelineRequest): Observable<Pipeline> {
    return this.http.put<Pipeline>(`/api/pipelines/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`/api/pipelines/${id}`);
  }

  revokeKey(id: number, reason: string): Observable<Pipeline> {
    return this.http.post<Pipeline>(`/api/pipelines/${id}/keys/revoke`, { reason });
  }

  issueKey(id: number): Observable<Pipeline> {
    return this.http.post<Pipeline>(`/api/pipelines/${id}/keys`, {});
  }

  config(id: number): Observable<string> {
    return this.http.get(`/api/pipelines/${id}/config`, { responseType: 'text' });
  }

  report(id: number, build?: number): Observable<PipelineReport | null> {
    const params = build ? new HttpParams().set('build', build) : undefined;
    return this.http.get<PipelineReport | null>(`/api/pipelines/${id}/report`, { params });
  }

  reportHtmlUrl(id: number, build?: number): string {
    return build
      ? `/api/pipelines/${id}/runs/${build}/report/html`
      : `/api/pipelines/${id}/report/html`;
  }

  /** The decorations of pull requests the pipeline library reported for the pipeline, the newest first. */
  pullRequestDecorations(id: number, limit = 20): Observable<PullRequestDecoration[]> {
    return this.http.get<PullRequestDecoration[]>(`/api/pipelines/${id}/pull-request-decorations`, {
      params: { limit },
    });
  }
}

@Injectable({ providedIn: 'root' })
export class MonitoringApi {
  private readonly http = inject(HttpClient);

  status(): Observable<MonitoringStatus> {
    return this.http.get<MonitoringStatus>('/api/monitoring/status');
  }

  overview(): Observable<MonitoringOverview> {
    return this.http.get<MonitoringOverview>('/api/monitoring/products');
  }

  product(id: number): Observable<ProductMonitoring> {
    return this.http.get<ProductMonitoring>(`/api/monitoring/products/${id}`);
  }

  pipeline(id: number, range: string): Observable<PipelineMonitoring> {
    return this.http.get<PipelineMonitoring>(`/api/monitoring/pipelines/${id}`, {
      params: { range },
    });
  }

  /** The DORA activity of every pipeline, or of those of one business service or application. */
  activity(range: string, scope?: ContextScope): Observable<PortfolioActivity> {
    return this.http.get<PortfolioActivity>('/api/monitoring/activity', {
      params: { range, ...scopeParams(scope) },
    });
  }
}

@Injectable({ providedIn: 'root' })
export class SettingsApi {
  private readonly http = inject(HttpClient);

  get(): Observable<GlobalSettings> {
    return this.http.get<GlobalSettings>('/api/settings');
  }

  update(request: GlobalSettingsRequest): Observable<GlobalSettings> {
    return this.http.put<GlobalSettings>('/api/settings', request);
  }

  config(): Observable<string> {
    return this.http.get('/api/settings/config', {
      params: { format: 'yaml' },
      responseType: 'text',
    });
  }
}

@Injectable({ providedIn: 'root' })
export class ServiceTemplateApi {
  private readonly http = inject(HttpClient);

  get(): Observable<ServiceTemplate> {
    return this.http.get<ServiceTemplate>('/api/service-template');
  }

  update(request: ServiceTemplateRequest): Observable<ServiceTemplate> {
    return this.http.put<ServiceTemplate>('/api/service-template', request);
  }
}

@Injectable({ providedIn: 'root' })
export class ActionHistoryApi {
  private readonly http = inject(HttpClient);

  list(): Observable<ActionLog[]> {
    return this.http.get<ActionLog[]>('/api/action-history');
  }
}

@Injectable({ providedIn: 'root' })
export class UserApi {
  private readonly http = inject(HttpClient);

  me(): Observable<UserProfile> {
    return this.http.get<UserProfile>('/api/user/me');
  }
}

@Injectable({ providedIn: 'root' })
export class SammApi {
  private readonly http = inject(HttpClient);

  /** The maturity of BBH, or of one business service or application. */
  overview(scope?: ContextScope): Observable<SammOrganizationOverview> {
    return this.http.get<SammOrganizationOverview>('/api/samm/overview', {
      params: scopeParams(scope),
    });
  }

  departments(): Observable<SammDepartmentScore[]> {
    return this.http.get<SammDepartmentScore[]>('/api/samm/departments');
  }

  department(id: number): Observable<SammDepartmentScore> {
    return this.http.get<SammDepartmentScore>(`/api/samm/departments/${id}`);
  }

  application(pipelineId: number): Observable<SammApplicationScore> {
    return this.http.get<SammApplicationScore>(`/api/samm/pipelines/${pipelineId}`);
  }
}

@Injectable({ providedIn: 'root' })
export class QualityApi {
  private readonly http = inject(HttpClient);

  defaults(): Observable<QualityDefaults> {
    return this.http.get<QualityDefaults>('/api/qa/defaults');
  }

  changeDefaults(request: QualityDefaultsRequest): Observable<QualityDefaults> {
    return this.http.put<QualityDefaults>('/api/qa/defaults', request);
  }

  applications(): Observable<QualityApplication[]> {
    return this.http.get<QualityApplication[]>('/api/qa/applications');
  }

  application(productId: number): Observable<QualityApplication> {
    return this.http.get<QualityApplication>(`/api/qa/applications/${productId}`);
  }

  change(productId: number, request: QualityApplicationRequest): Observable<QualityApplication> {
    return this.http.put<QualityApplication>(`/api/qa/applications/${productId}`, request);
  }
}

@Injectable({ providedIn: 'root' })
export class AgileApi {
  private readonly http = inject(HttpClient);

  model(): Observable<AgileModel> {
    return this.http.get<AgileModel>('/api/agile/model');
  }

  applications(): Observable<AgileApplication[]> {
    return this.http.get<AgileApplication[]>('/api/agile/applications');
  }

  application(productId: number): Observable<AgileApplication> {
    return this.http.get<AgileApplication>(`/api/agile/applications/${productId}`);
  }

  changeProfile(productId: number, request: AgileProfileRequest): Observable<AgileApplication> {
    return this.http.put<AgileApplication>(`/api/agile/applications/${productId}`, request);
  }

  recordAssessment(
    productId: number,
    request: AgileAssessmentRequest,
  ): Observable<AgileAssessment> {
    return this.http.post<AgileAssessment>(
      `/api/agile/applications/${productId}/assessments`,
      request,
    );
  }

  deleteAssessment(id: number): Observable<void> {
    return this.http.delete<void>(`/api/agile/assessments/${id}`);
  }
}

/** What the guided set-up of a pipeline does outside OneView: the two reads change nothing anywhere. */
@Injectable({ providedIn: 'root' })
export class PipelineSetupApi {
  private readonly http = inject(HttpClient);

  inspectSources(repositoryUrl: string, branch: string | null): Observable<SourceInspection> {
    return this.http.post<SourceInspection>('/api/pipeline-setup/sources', {
      repositoryUrl,
      branch,
    });
  }

  readJenkinsJob(jobUrl: string): Observable<JenkinsJobSnapshot> {
    return this.http.post<JenkinsJobSnapshot>('/api/pipeline-setup/jenkins-job', { jobUrl });
  }

  createJenkinsJob(
    pipelineId: number,
    request: CreateJenkinsJobRequest,
  ): Observable<CreatedJenkinsJob> {
    return this.http.post<CreatedJenkinsJob>(`/api/pipelines/${pipelineId}/jenkins-job`, request);
  }
}

@Injectable({ providedIn: 'root' })
export class JenkinsAutomationApi {
  private readonly http = inject(HttpClient);

  get(): Observable<JenkinsAutomation> {
    return this.http.get<JenkinsAutomation>('/api/jenkins-automation');
  }

  update(request: JenkinsAutomationRequest): Observable<JenkinsAutomation> {
    return this.http.put<JenkinsAutomation>('/api/jenkins-automation', request);
  }

  check(jenkinsUrl: string): Observable<ConnectionCheck> {
    return this.http.post<ConnectionCheck>('/api/jenkins-automation/check', { jenkinsUrl });
  }
}
