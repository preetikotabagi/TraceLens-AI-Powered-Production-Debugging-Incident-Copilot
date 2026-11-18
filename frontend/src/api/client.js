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
    if (!response._bodyPromise) {
      try {
        response._bodyPromise = response.json().catch(() => null);
      } catch {
        response._bodyPromise = Promise.resolve(null);
      }
    }
    data = await response._bodyPromise;

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

/**
 * Fetches overview metrics for the main dashboard.
 */
export async function getOverviewMetrics({ signal } = {}) {
  const res = await request('/api/v1/metrics/overview', { signal });
  if (!res.ok) {
    throw new ApiError(res.data?.message || 'Failed to fetch overview metrics', { status: res.status, data: res.data });
  }
  return res.data;
}

/**
 * Searches and filters logs.
 */
export async function searchLogs({ service, level, query, fingerprint, incidentId, startTime, endTime, limit = 50, offset = 0 } = {}, { signal } = {}) {
  const params = new URLSearchParams();
  if (service) params.set('service', service);
  if (level && level !== 'ALL') params.set('level', level);
  if (query) params.set('query', query);
  if (fingerprint) params.set('fingerprint', fingerprint);
  if (incidentId) params.set('incidentId', incidentId);
  if (startTime) params.set('startTime', startTime);
  if (endTime) params.set('endTime', endTime);
  params.set('limit', String(limit));
  params.set('offset', String(offset));

  const res = await request(`/api/v1/logs?${params.toString()}`, { signal });
  if (!res.ok) {
    throw new ApiError(res.data?.message || 'Failed to search logs', { status: res.status, data: res.data });
  }
  return res.data;
}

/**
 * Fetches a single log record by ID.
 */
export async function getLogById(id, { signal } = {}) {
  const res = await request(`/api/v1/logs/${id}`, { signal });
  if (!res.ok) {
    throw new ApiError(res.data?.message || 'Log not found', { status: res.status, data: res.data });
  }
  return res.data;
}

/**
 * Ingests a single log record.
 */
export async function ingestLog(logEntry) {
  const res = await request('/api/v1/logs', { method: 'POST', body: logEntry });
  if (!res.ok) {
    throw new ApiError(res.data?.message || 'Failed to ingest log', { status: res.status, data: res.data });
  }
  return res.data;
}

/**
 * Lists incidents with filtering.
 */
export async function listIncidents({ service, severity, status, limit = 50, offset = 0 } = {}, { signal } = {}) {
  const params = new URLSearchParams();
  if (service) params.set('service', service);
  if (severity && severity !== 'ALL') params.set('severity', severity);
  if (status && status !== 'ALL') params.set('status', status);
  params.set('limit', String(limit));
  params.set('offset', String(offset));

  const res = await request(`/api/v1/incidents?${params.toString()}`, { signal });
  if (!res.ok) {
    throw new ApiError(res.data?.message || 'Failed to list incidents', { status: res.status, data: res.data });
  }
  return res.data;
}

/**
 * Gets incident detail by ID with associated error group and logs.
 */
export async function getIncidentDetail(id, { signal } = {}) {
  const res = await request(`/api/v1/incidents/${id}`, { signal });
  if (!res.ok) {
    throw new ApiError(res.data?.message || 'Failed to fetch incident details', { status: res.status, data: res.data });
  }
  return res.data;
}

/**
 * Updates an incident's status.
 */
export async function updateIncidentStatus(id, newStatus) {
  const res = await request(`/api/v1/incidents/${id}/status`, {
    method: 'PATCH',
    body: { status: newStatus },
  });
  if (!res.ok) {
    throw new ApiError(res.data?.message || 'Failed to update incident status', { status: res.status, data: res.data });
  }
  return res.data;
}

/**
 * Triggers an LLM investigation on an incident.
 */
export async function runInvestigation(incidentId, { notes, model } = {}) {
  const res = await request(`/api/v1/incidents/${incidentId}/investigate`, {
    method: 'POST',
    body: { notes, model },
    timeoutMs: 35000,
  });
  if (!res.ok) {
    throw new ApiError(res.data?.message || 'AI investigation failed', { status: res.status, data: res.data });
  }
  return res.data;
}

/**
 * Gets past investigations for an incident.
 */
export async function getInvestigations(incidentId, { signal } = {}) {
  const res = await request(`/api/v1/incidents/${incidentId}/investigations`, { signal });
  if (!res.ok) {
    throw new ApiError(res.data?.message || 'Failed to fetch investigations', { status: res.status, data: res.data });
  }
  return res.data;
}

/**
 * Lists error groups.
 */
export async function listErrorGroups({ service, limit = 50, offset = 0 } = {}, { signal } = {}) {
  const params = new URLSearchParams();
  if (service) params.set('service', service);
  params.set('limit', String(limit));
  params.set('offset', String(offset));

  const res = await request(`/api/v1/error-groups?${params.toString()}`, { signal });
  if (!res.ok) {
    throw new ApiError(res.data?.message || 'Failed to fetch error groups', { status: res.status, data: res.data });
  }
  return res.data;
}

/**
 * Gets error group details by fingerprint.
 */
export async function getErrorGroupDetail(fingerprint, { signal } = {}) {
  const res = await request(`/api/v1/error-groups/${fingerprint}`, { signal });
  if (!res.ok) {
    throw new ApiError(res.data?.message || 'Failed to fetch error group detail', { status: res.status, data: res.data });
  }
  return res.data;
}

/**
 * Gets service health statuses.
 */
export async function getServiceHealth({ signal } = {}) {
  const res = await request('/api/v1/services/health', { signal });
  if (!res.ok) {
    throw new ApiError(res.data?.message || 'Failed to fetch service health', { status: res.status, data: res.data });
  }
  return res.data;
}

/**
 * Generates 1,000+ realistic logs across multiple services with realistic incident patterns.
 */
export async function seedRealisticData() {
  const res = await request('/api/v1/seed', {
    method: 'POST',
    timeoutMs: 60000,
  });
  if (!res.ok) {
    throw new ApiError(res.data?.message || 'Failed to seed data', { status: res.status, data: res.data });
  }
  return res.data;
}
