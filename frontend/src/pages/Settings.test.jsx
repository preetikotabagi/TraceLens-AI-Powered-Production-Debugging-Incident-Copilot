import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import Settings from './Settings.jsx';
import { jsonResponse, renderWithStatus } from '../test/helpers.jsx';

describe('Settings page', () => {
  it('shows connection details', () => {
    renderWithStatus(<Settings />);

    expect(screen.getByRole('heading', { name: 'Backend connection' })).toBeInTheDocument();
    expect(screen.getByText('Connected')).toBeInTheDocument();
    expect(screen.getByText('0.1.0')).toBeInTheDocument();
  });

  it('runs a demo scenario and shows the backend response', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse(
        { status: 400, code: 'DEMO_INVALID_REQUEST', message: 'Simulated (demo): the request was deliberately invalid.' },
        { status: 400, headers: { 'X-Request-Id': 'req-42' } },
      ),
    );
    vi.stubGlobal('fetch', fetchMock);
    renderWithStatus(<Settings />);

    await userEvent.click(screen.getByRole('button', { name: /^Run: Simulate invalid request/ }));

    expect(await screen.findByText(/HTTP 400 · DEMO_INVALID_REQUEST/)).toBeInTheDocument();
    expect(screen.getByText('req-42')).toBeInTheDocument();
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toMatch(/\/api\/v1\/demo\/simulate$/);
    expect(JSON.parse(init.body)).toEqual({ scenario: 'INVALID_REQUEST' });
  });

  it('sends the delay for the slow-request scenario', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({ scenario: 'SLOW_REQUEST', elapsedMs: 3001, message: 'Simulated (demo): slow request finished.' }),
    );
    vi.stubGlobal('fetch', fetchMock);
    renderWithStatus(<Settings />);

    await userEvent.click(screen.getByRole('button', { name: /^Run: Simulate slow request/ }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ scenario: 'SLOW_REQUEST', delayMs: 3000 });
    expect(await screen.findByText(/HTTP 200/)).toBeInTheDocument();
  });

  it('explains that demo mode is off when the backend answers 404', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse({ status: 404, code: 'NOT_FOUND', message: 'not found' }, { status: 404 })),
    );
    renderWithStatus(<Settings />);

    await userEvent.click(screen.getByRole('button', { name: /^Run: Simulate database failure/ }));

    expect(await screen.findByText(/Demo endpoints are disabled on this backend/)).toBeInTheDocument();
  });

  it('shows a clear message when the backend cannot be reached', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));
    renderWithStatus(<Settings />);

    await userEvent.click(screen.getByRole('button', { name: /^Run: Simulate database failure/ }));

    expect(await screen.findByText('Could not reach the backend.')).toBeInTheDocument();
  });

  it('disables every scenario button while one is running', async () => {
    let finish;
    vi.stubGlobal(
      'fetch',
      vi.fn().mockReturnValue(new Promise((resolve) => (finish = resolve))),
    );
    renderWithStatus(<Settings />);

    await userEvent.click(screen.getByRole('button', { name: /^Run: Simulate invalid request/ }));

    for (const button of screen.getAllByRole('button')) {
      expect(button).toBeDisabled();
    }
    finish(jsonResponse({ status: 400, code: 'DEMO_INVALID_REQUEST' }, { status: 400 }));
    await waitFor(() => expect(screen.getAllByRole('button')[0]).toBeEnabled());
  });
});
