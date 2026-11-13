import { render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { BackendStatusContext } from '../context/backendStatusContext.js';

/** Builds a JSON Response-like object for stubbing fetch. */
export function jsonResponse(body, { status = 200, headers = {} } = {}) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', ...headers },
  });
}

export const HEALTHY = {
  status: 'UP',
  application: { name: 'TraceLens', version: '0.1.0' },
  database: { status: 'UP', latencyMs: 7, migrationVersion: '1' },
  timestamp: '2026-10-24T06:00:00Z',
};

/** Renders UI inside a router and a fixed backend-status context. */
export function renderWithStatus(ui, { status, route = '/' } = {}) {
  const value = {
    state: 'up',
    health: HEALTHY,
    error: null,
    checkedAt: new Date('2026-10-24T06:00:00Z'),
    refresh: () => {},
    ...status,
  };
  return render(
    <MemoryRouter initialEntries={[route]}>
      <BackendStatusContext.Provider value={value}>{ui}</BackendStatusContext.Provider>
    </MemoryRouter>,
  );
}
