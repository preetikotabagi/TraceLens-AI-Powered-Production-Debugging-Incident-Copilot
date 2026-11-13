import { describe, expect, it, vi } from 'vitest';
import { ApiError, NetworkError, getHealth, request, runDemoScenario } from './client.js';
import { HEALTHY, jsonResponse } from '../test/helpers.jsx';

describe('api client', () => {
  it('getHealth returns the body for a healthy backend', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(HEALTHY));
    vi.stubGlobal('fetch', fetchMock);

    const health = await getHealth();

    expect(health.status).toBe('UP');
    expect(fetchMock.mock.calls[0][0]).toMatch(/\/api\/v1\/health$/);
  });

  it('getHealth treats a 503 with a body as data, not as a crash', async () => {
    const down = { ...HEALTHY, status: 'DOWN', database: { status: 'DOWN', error: 'Database connection failed' } };
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(down, { status: 503 })));

    const health = await getHealth();

    expect(health.status).toBe('DOWN');
    expect(health.database.error).toBe('Database connection failed');
  });

  it('getHealth throws ApiError when the response is not a health report', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse({ code: 'INTERNAL_ERROR', message: 'boom' }, { status: 500 })),
    );

    await expect(getHealth()).rejects.toBeInstanceOf(ApiError);
  });

  it('throws NetworkError when the backend cannot be reached', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));

    await expect(request('/api/v1/health')).rejects.toMatchObject({
      name: 'NetworkError',
      timedOut: false,
    });
  });

  it('throws a timed-out NetworkError when the backend is too slow', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(
        (_url, { signal }) =>
          new Promise((_resolve, reject) => {
            signal.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')));
          }),
      ),
    );

    const promise = request('/api/v1/health', { timeoutMs: 20 });

    await expect(promise).rejects.toBeInstanceOf(NetworkError);
    await expect(promise).rejects.toMatchObject({ timedOut: true });
  });

  it('rethrows the original error when the caller aborts on purpose', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(
        (_url, { signal }) =>
          new Promise((_resolve, reject) => {
            signal.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')));
          }),
      ),
    );
    const controller = new AbortController();

    const promise = request('/api/v1/health', { signal: controller.signal });
    controller.abort();

    await expect(promise).rejects.toMatchObject({ name: 'AbortError' });
  });

  it('runDemoScenario returns intentional failures as results instead of throwing', async () => {
    const body = { status: 503, code: 'DATABASE_UNAVAILABLE', message: 'The database is currently unavailable.' };
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(body, { status: 503, headers: { 'X-Request-Id': 'abc' } }));
    vi.stubGlobal('fetch', fetchMock);

    const result = await runDemoScenario('DATABASE_FAILURE');

    expect(result.ok).toBe(false);
    expect(result.status).toBe(503);
    expect(result.data.code).toBe('DATABASE_UNAVAILABLE');
    expect(result.requestId).toBe('abc');
    const [, init] = fetchMock.mock.calls[0];
    expect(init.method).toBe('POST');
    expect(JSON.parse(init.body)).toEqual({ scenario: 'DATABASE_FAILURE' });
  });
});
