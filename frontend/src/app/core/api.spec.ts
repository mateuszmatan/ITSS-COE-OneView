import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { pullRequestDecoration } from '../testing/fixtures';
import { PipelinesApi } from './api';

describe('PipelinesApi', () => {
  let api: PipelinesApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(PipelinesApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('reads the newest twenty pull request decorations of a pipeline unless asked for another number', async () => {
    const decorations = [pullRequestDecoration()];

    const newest = firstValueFrom(api.pullRequestDecorations(7));
    const request = http.expectOne('/api/pipelines/7/pull-request-decorations?limit=20');
    expect(request.request.method).toBe('GET');
    request.flush(decorations);
    expect(await newest).toEqual(decorations);

    const more = firstValueFrom(api.pullRequestDecorations(7, 100));
    http.expectOne('/api/pipelines/7/pull-request-decorations?limit=100').flush([]);
    expect(await more).toEqual([]);
  });
});
