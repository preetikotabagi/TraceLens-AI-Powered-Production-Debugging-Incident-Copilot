import { renderHook, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { useBackendStatus } from './useBackendStatus.js';
import { HEALTHY, jsonResponse } from '../test/helpers.jsx';

describe('useBackendStatus', () => {
  it('starts in loading and becomes up when the backend is healthy', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(HEALTHY)));

    const { result } = renderHook(() => useBackendStatus(60000));
    expect(result.current.state).toBe('loading');

    await waitFor(() => expect(result.current.state).toBe('up'));
    expect(result.current.health.database.migrationVersion).toBe('1');
    expect(result.current.checkedAt).toBeInstanceOf(Date);
  });

  it('reports down when the backend says its database is unavailable', async () => {
    const down = { ...HEALTHY, status: 'DOWN', database: { status: 'DOWN', error: 'Database connection failed' } };
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(down, { status: 503 })));

    const { result } = renderHook(() => useBackendStatus(60000));

    await waitFor(() => expect(result.current.state).toBe('down'));
  });

  it('reports unreachable when the request fails', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));

    const { result } = renderHook(() => useBackendStatus(60000));

    await waitFor(() => expect(result.current.state).toBe('unreachable'));
    expect(result.current.error).toBeTruthy();
  });

  it('recovers after a failure when refresh() is called', async () => {
    const fetchMock = vi
      .fn()
      .mockRejectedValueOnce(new TypeError('Failed to fetch'))
      .mockResolvedValue(jsonResponse(HEALTHY));
    vi.stubGlobal('fetch', fetchMock);

    const { result } = renderHook(() => useBackendStatus(60000));
    await waitFor(() => expect(result.current.state).toBe('unreachable'));

    result.current.refresh();

    await waitFor(() => expect(result.current.state).toBe('up'));
  });
});
