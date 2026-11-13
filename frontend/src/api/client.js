import { API_BASE_URL, REQUEST_TIMEOUT_MS } from '../config.js';

/** The server answered, but with an error the caller cannot use. */
export class ApiError extends Error {
  constructor(message, { status, code, requestId, details, body } = {}) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.requestId = requestId;
    this.details = details ?? [];
    this.body = body;
  }
}

/** No usable response: the server is down, blocked by the network, or too slow. */
export class NetworkError extends Error {
  constructor(message, { timedOut = false, cause } = {}) {
    super(message);
    this.name = 'NetworkError';
    this.timedOut = timedOut;
    this.cause = cause;
  }
}

/**
 * Sends a request and returns { ok, status, data, requestId }.
 * HTTP error statuses do NOT throw: callers decide what a 4xx/5xx means.
 * Only a missing response (network failure, timeout, abort by the caller) throws.
 */
export async function request(path, { method = 'GET', body, timeoutMs = REQUEST_TIMEOUT_MS, signal } = {}) {
  const controller = new AbortController();
  let timedOut = false;
  const timer = setTimeout(() => {
    timedOut = true;
    controller.abort();
  }, timeoutMs);
  const onCallerAbort = () => controller.abort();
  if (signal) {
    if (signal.aborted) controller.abort();
    else signal.addEventListener('abort', onCallerAbort, { once: true });
  }

  try {
    const response = await fetch(`${API_BASE_URL}${path}`, {
      method,
      headers: {
        Accept: 'application/json',
        ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
      },
      body: body !== undefined ? JSON.stringify(body) : undefined,
      signal: controller.signal,
    });

    let data = null;
    try {
      data = await response.json();
    } catch {
      data = null; // empty or non-JSON body
    }
    return {
      ok: response.ok,
      status: response.status,
      data,
      requestId: response.headers?.get?.('X-Request-Id') ?? data?.requestId ?? null,
    };
  } catch (error) {
    if (signal?.aborted) throw error; // the caller cancelled on purpose
    throw new NetworkError(
      timedOut ? 'The backend did not respond in time.' : 'Could not reach the backend.',
      { timedOut, cause: error },
    );
  } finally {
    clearTimeout(timer);
    signal?.removeEventListener('abort', onCallerAbort);
  }
}

/**
 * Fetches backend health. The backend answers 503 *with a body* when the database is down,
 * so a body containing a status is returned as data in both cases.
 */
export async function getHealth({ signal } = {}) {
  const res = await request('/api/v1/health', { signal });
  if (res.data && typeof res.data.status === 'string') {
    return res.data;
  }
  throw new ApiError(res.data?.message || `Unexpected response (HTTP ${res.status}).`, {
    status: res.status,
    code: res.data?.code,
    requestId: res.requestId,
    body: res.data,
  });
}

/**
 * Triggers one of the backend's development-only demo scenarios.
 * Returns the raw outcome (including intentional failures) so the UI can display it.
 */
export async function runDemoScenario(scenario, { delayMs } = {}) {
  const startedAt = performance.now();
  const res = await request('/api/v1/demo/simulate', {
    method: 'POST',
    body: delayMs === undefined ? { scenario } : { scenario, delayMs },
    timeoutMs: 20000,
  });
  return { ...res, durationMs: Math.round(performance.now() - startedAt) };
}
