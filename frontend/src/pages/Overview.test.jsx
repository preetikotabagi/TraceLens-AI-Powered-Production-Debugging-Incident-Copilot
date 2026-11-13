import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import Overview from './Overview.jsx';
import { renderWithStatus } from '../test/helpers.jsx';

describe('Overview page', () => {
  it('shows honest empty states instead of fake metrics', () => {
    renderWithStatus(<Overview />);

    expect(screen.getByRole('heading', { level: 1, name: 'Overview' })).toBeInTheDocument();
    for (const label of ['Log events (24h)', 'Open incidents', 'Error rate', 'Services reporting']) {
      expect(screen.getByRole('heading', { name: label })).toBeInTheDocument();
    }
    // Every metric card shows "no data" rather than a number.
    expect(screen.getAllByLabelText('No data')).toHaveLength(4);
    expect(screen.getByText('No activity yet')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('shows real system status details from the health check', () => {
    renderWithStatus(<Overview />);

    expect(screen.getByText('Healthy')).toBeInTheDocument();
    expect(screen.getByText('Connected')).toBeInTheDocument();
    expect(screen.getByText('7 ms')).toBeInTheDocument();
    expect(screen.getByText('1')).toBeInTheDocument(); // schema version
  });

  it('shows loading placeholders while the first check is running', () => {
    renderWithStatus(<Overview />, { status: { state: 'loading', health: null, checkedAt: null } });

    expect(screen.getAllByLabelText('Loading').length).toBeGreaterThan(0);
  });

  it('shows an error with a working retry button when the backend is unreachable', async () => {
    const refresh = vi.fn();
    renderWithStatus(<Overview />, { status: { state: 'unreachable', health: null, refresh } });

    expect(screen.getByRole('alert')).toHaveTextContent('Cannot reach the TraceLens backend');
    await userEvent.click(screen.getByRole('button', { name: 'Retry now' }));

    expect(refresh).toHaveBeenCalledTimes(1);
  });

  it('explains a database outage when the backend is up but unhealthy', () => {
    renderWithStatus(<Overview />, {
      state: 'down',
      status: {
        state: 'down',
        health: { status: 'DOWN', database: { status: 'DOWN', error: 'Database connection failed' } },
      },
    });

    expect(screen.getByRole('alert')).toHaveTextContent('database is unavailable');
    expect(screen.getByText('Unavailable')).toBeInTheDocument();
  });
});
