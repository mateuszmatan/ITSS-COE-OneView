import { ChangeDetectionStrategy, Component, effect, inject, signal } from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
import { FormGroup, ReactiveFormsModule } from '@angular/forms';
import { catchError, finalize, of } from 'rxjs';
import { Notifier } from '@common/core/notifier';
import { HasUnsavedChanges } from '@common/core/unsaved-changes';
import { failureText, max, optional, saveFailure, text } from '@common/shared/form-controls';
import { errorText } from '@common/shared/form-errors';
import { RelativeTimePipe } from '@common/shared/formatting';
import { FORM_FIELD } from '@common/ui/form-field';
import { LoadError } from '@common/ui/load-error';
import { OvLoading } from '@common/ui/loading';
import { JenkinsAutomationApi, SettingsApi } from '../core/api';
import { ConnectionCheck, JenkinsAutomation, JenkinsAutomationRequest } from '../core/models';
import { PIPELINE_WIZARD } from '../core/sections';

const pattern = (expression: RegExp, message: string) => (control: { value: unknown }) => {
  const value = String(control.value ?? '').trim();
  return !value || expression.test(value) ? null : { rule: message };
};

/**
 * Admin, Jenkins automation: the generic user OneView acts as in Jenkins and Bitbucket, and how it
 * fetches the secret of that user from OIS. The secret itself is never entered or stored here.
 */
@Component({
  selector: 'ov-jenkins-automation-page',
  imports: [ReactiveFormsModule, FORM_FIELD, OvLoading, LoadError, RelativeTimePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="card automation">
      <header class="list-header">
        <div class="list-title">
          <h2>Jenkins automation</h2>
          @if (stored.hasValue()) {
            <span class="tag" [class.new]="stored.value().ready" data-testid="automation-state">{{
              stored.value().ready ? 'Ready' : 'Not set up'
            }}</span>
            @if (stored.value().updatedAt; as saved) {
              <span class="summary">Last saved {{ saved | relative }}</span>
            }
          }
        </div>
      </header>
      <p class="section-help">
        With a generic user, {{ wizard }} reads the sources in Bitbucket, reads existing Jenkins
        jobs and creates the Jenkins job of a new pipeline by itself. OneView never stores the
        secret of that user: it fetches it from OIS with its certificate for every request, the way
        the DevSecOps library fetches its proxy password. Without a generic user everything still
        works; people then enter their settings and create their jobs themselves.
      </p>

      @if (stored.isLoading()) {
        <ov-loading />
      }
      @if (stored.error(); as error) {
        <ov-load-error what="The Jenkins automation" [error]="error" (retry)="stored.reload()" />
      } @else if (stored.hasValue()) {
        @if (!stored.value().ready) {
          <div class="banner info" role="status">
            <div class="banner-text">
              Still needed before OneView acts by itself: {{ stored.value().missing.join(', ') }}.
            </div>
          </div>
        }
        <form [formGroup]="form" (ngSubmit)="save()" novalidate>
          <h3>Generic user and its secret</h3>
          <div class="fields">
            <ov-form-field
              help="The technical account OneView signs in with to Jenkins and Bitbucket. It needs Job/Create and Job/ExtendedRead in Jenkins and read access to the repositories in Bitbucket."
            >
              <ov-label>Generic user</ov-label>
              <input ovInput formControlName="genericUser" autocomplete="off" />
              <ov-hint>Empty turns the automation off and removes the OIS API key</ov-hint>
              <ov-error>{{ errorText(form.controls.genericUser) }}</ov-error>
            </ov-form-field>
            <ov-form-field
              help="The host of the OIS appliance OneView asks for the secret, for example the OIS of the QC environment."
            >
              <ov-label>OIS host</ov-label>
              <input ovInput formControlName="oisHost" placeholder="oisapi.bbh.com" />
              <ov-hint>Host name only; add :port when OIS does not listen on 443</ov-hint>
              <ov-error>{{ errorText(form.controls.oisHost) }}</ov-error>
            </ov-form-field>
            <ov-form-field
              help="The application-to-application (A2A) API key that OIS issued for the secret of the generic user. It is never shown again."
            >
              <ov-label>OIS API key</ov-label>
              <input
                ovInput
                type="password"
                formControlName="oisApiKey"
                autocomplete="new-password"
                [placeholder]="stored.value().oisApiKeySet ? 'Stored; type to replace it' : ''"
              />
              <ov-hint>{{
                stored.value().oisApiKeySet
                  ? 'A key is stored. Leave this empty to keep it.'
                  : 'No key is stored yet'
              }}</ov-hint>
              <ov-error>{{ errorText(form.controls.oisApiKey) }}</ov-error>
            </ov-form-field>
          </div>
          <p class="section-help">
            The certificate OneView shows to OIS is not set here: it belongs to the deployment of
            OneView, as the SSL bundle <code>ois</code> (<code>spring.ssl.bundle</code>).
          </p>

          <h3>Jobs OneView creates</h3>
          <div class="fields">
            <ov-form-field
              help="How often a job OneView creates looks for changes of the sources, as a Jenkins schedule. A Multibranch Pipeline job scans its branches at the interval of its minutes."
            >
              <ov-label>Looks for changes</ov-label>
              <input ovInput formControlName="pollSchedule" placeholder="H/5 * * * *" />
              <ov-hint>H/5 * * * * is every five minutes</ov-hint>
              <ov-error>{{ errorText(form.controls.pollSchedule) }}</ov-error>
            </ov-form-field>
          </div>

          <h3>DevSecOps library of a Multibranch Pipeline job</h3>
          <p class="section-help">
            Optional. With the Git repository of the library, a Multibranch Pipeline job that
            OneView creates carries the library itself, under the name every Jenkinsfile loads ({{
              library()
            }}). A Pipeline job always uses the library registered in Jenkins under that name.
          </p>
          <div class="fields">
            <ov-form-field help="The Git address of the DevSecOps library.">
              <ov-label>Library repository</ov-label>
              <input ovInput formControlName="libraryRepositoryUrl" />
              <ov-error>{{ errorText(form.controls.libraryRepositoryUrl) }}</ov-error>
            </ov-form-field>
            <ov-form-field help="The branch or tag of the library the jobs load.">
              <ov-label>Library version</ov-label>
              <input ovInput formControlName="libraryVersion" placeholder="main" />
              <ov-error>{{ errorText(form.controls.libraryVersion) }}</ov-error>
            </ov-form-field>
            <ov-form-field
              help="The ID of the credential in Jenkins with which a job checks out the library."
            >
              <ov-label>Jenkins credential for the library</ov-label>
              <input ovInput formControlName="libraryCredentialsId" />
              <ov-error>{{ errorText(form.controls.libraryCredentialsId) }}</ov-error>
            </ov-form-field>
          </div>

          @if (saveError(); as message) {
            <div class="banner danger" role="alert">{{ message }}</div>
          }
          <div class="form-actions">
            <button type="submit" class="btn btn-primary" [disabled]="saving() || !form.dirty">
              Save
            </button>
            <button
              type="button"
              class="btn btn-link"
              [disabled]="saving() || !form.dirty"
              (click)="discard()"
            >
              Discard changes
            </button>
          </div>
        </form>

        <h3>Check the connection</h3>
        <p class="section-help">
          Fetches the secret from OIS and signs in to Jenkins with it. It changes nothing. Save your
          changes first: the check uses what is stored.
        </p>
        <div class="check">
          <ov-form-field help="The Jenkins to sign in to.">
            <ov-label>Jenkins address</ov-label>
            <input ovInput [formControl]="jenkinsUrl" placeholder="https://jenkins.bbh.com" />
            <ov-error>{{ errorText(jenkinsUrl) }}</ov-error>
          </ov-form-field>
          <button
            type="button"
            class="btn btn-outline-primary"
            [disabled]="checking() || !stored.value().ready || form.dirty"
            (click)="check()"
          >
            Check the connection
          </button>
        </div>
        @if (checking()) {
          <ov-loading />
        }
        @if (checked(); as result) {
          <div
            class="banner"
            [class.success]="result.ok"
            [class.danger]="!result.ok"
            role="status"
            data-testid="check-result"
          >
            {{ result.message }}
          </div>
        }
      }
    </section>
  `,
  styles: `
    .automation {
      display: flex;
      flex-direction: column;
      gap: 10px;
      padding: 16px 20px;
    }

    h3 {
      margin: 8px 0 0;
      font-size: 14px;
    }

    form {
      display: flex;
      flex-direction: column;
      gap: 10px;
    }

    .fields {
      display: grid;
      grid-template-columns: repeat(3, minmax(0, 1fr));
      gap: 10px 14px;
      max-width: 1000px;
    }

    .section-help,
    .banner {
      margin: 0;
    }

    .check {
      display: flex;
      align-items: flex-end;
      gap: 12px;
      max-width: 700px;

      ov-form-field {
        flex: 1;
      }
    }

    @media (max-width: 900px) {
      .fields {
        grid-template-columns: minmax(0, 1fr);
      }
    }
  `,
})
export class JenkinsAutomationPage implements HasUnsavedChanges {
  private readonly api = inject(JenkinsAutomationApi);
  private readonly notifier = inject(Notifier);

  protected readonly wizard = PIPELINE_WIZARD.label;
  protected readonly errorText = errorText;
  protected readonly stored = rxResource({ stream: () => this.api.get() });
  private readonly settings = toSignal(
    inject(SettingsApi)
      .get()
      .pipe(catchError(() => of(null))),
    { initialValue: null },
  );
  protected readonly library = () =>
    this.settings()?.platform.jenkinsLibrary ?? 'DevSecOpsJenkinsLibrary';

  protected readonly form = new FormGroup({
    genericUser: text('', max(200)),
    oisHost: text(
      '',
      max(255),
      pattern(/^[A-Za-z0-9.-]+(:\d{1,5})?$/, 'Give a host name, without https://'),
    ),
    oisApiKey: text('', max(500)),
    pollSchedule: text(
      '',
      pattern(/^[A-Za-z0-9 */,@()-]{1,100}$/, 'Give a Jenkins schedule such as H/5 * * * *'),
    ),
    libraryRepositoryUrl: text(
      '',
      max(1000),
      pattern(
        /^(https?|ssh):\/\/[^\s$`"\\]+$/,
        'Give the http, https or ssh address of the repository',
      ),
    ),
    libraryVersion: text('', max(200)),
    libraryCredentialsId: text('', max(200)),
  });
  protected readonly jenkinsUrl = text(
    '',
    pattern(/^https?:\/\/[^\s$`"\\]+$/, 'Give the http or https address of Jenkins'),
  );
  protected readonly saving = signal(false);
  protected readonly saveError = signal<string | null>(null);
  protected readonly checking = signal(false);
  protected readonly checked = signal<ConnectionCheck | null>(null);

  constructor() {
    effect(() => {
      if (this.stored.hasValue()) {
        this.show(this.stored.value());
      }
    });
    effect(() => {
      const url = this.settings()?.platform.jenkinsUrl;
      if (url && !this.jenkinsUrl.dirty && !this.jenkinsUrl.value) {
        this.jenkinsUrl.setValue(url);
      }
    });
  }

  hasUnsavedChanges(): boolean {
    return this.form.dirty;
  }

  protected save(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.saving() || !this.stored.hasValue()) {
      return;
    }
    const value = this.form.getRawValue();
    const request: JenkinsAutomationRequest = {
      version: this.stored.value().version,
      genericUser: optional(value.genericUser),
      oisHost: optional(value.oisHost),
      oisApiKey: optional(value.oisApiKey),
      pollSchedule: value.pollSchedule.trim(),
      libraryRepositoryUrl: optional(value.libraryRepositoryUrl),
      libraryVersion: value.libraryVersion.trim(),
      libraryCredentialsId: optional(value.libraryCredentialsId),
    };
    this.saving.set(true);
    this.saveError.set(null);
    this.api
      .update(request)
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (saved) => {
          this.stored.set(saved);
          this.checked.set(null);
          this.notifier.success(
            saved.ready
              ? 'Jenkins automation saved. OneView can now read and create Jenkins jobs by itself.'
              : 'Jenkins automation saved. It is not complete yet, so OneView does nothing by itself.',
          );
        },
        error: (error) =>
          this.saveError.set(
            failureText(
              saveFailure(
                this.form,
                error,
                'Someone else saved the Jenkins automation after you opened this page, so nothing was saved. Reload the page and make your changes again.',
              ),
            ),
          ),
      });
  }

  protected discard(): void {
    if (this.stored.hasValue()) {
      this.show(this.stored.value());
    }
    this.saveError.set(null);
  }

  protected check(): void {
    this.jenkinsUrl.markAsTouched();
    if (!this.jenkinsUrl.value.trim()) {
      this.checked.set({ ok: false, message: 'Give the address of Jenkins first.' });
      return;
    }
    if (this.jenkinsUrl.invalid || this.checking()) {
      return;
    }
    this.checking.set(true);
    this.checked.set(null);
    this.api
      .check(this.jenkinsUrl.value.trim())
      .pipe(
        catchError(() =>
          of({ ok: false, message: 'OneView could not run the check. Try again in a moment.' }),
        ),
        finalize(() => this.checking.set(false)),
      )
      .subscribe((result) => this.checked.set(result));
  }

  private show(stored: JenkinsAutomation): void {
    this.form.reset({
      genericUser: stored.genericUser ?? '',
      oisHost: stored.oisHost ?? '',
      oisApiKey: '',
      pollSchedule: stored.pollSchedule,
      libraryRepositoryUrl: stored.libraryRepositoryUrl ?? '',
      libraryVersion: stored.libraryVersion,
      libraryCredentialsId: stored.libraryCredentialsId ?? '',
    });
  }
}
