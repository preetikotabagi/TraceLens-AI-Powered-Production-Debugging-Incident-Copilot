import { screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import Services from './Services.jsx';
import { jsonResponse, renderWithStatus } from '../test/helpers.jsx';

describe('Services page', () => {
  it('renders service health page and displays services telemetry', async () => {
    const mockServices = [
      {
        serviceName: 'payment-service',
        status: 'DEGRADED',
        totalLogs: 450,
        errorCount: 15,
        errorRate: 3.33,
        activeIncidents: 1,
        lastReportedAt: '2026-10-24T10:15:00Z',
      },
      {
        serviceName: 'order-service',
        status: 'HEALTHY',
        totalLogs: 300,
        errorCount: 0,
        errorRate: 0.0,
        activeIncidents: 0,
        lastReportedAt: '2026-10-24T10:15:00Z',
      },
    ];

    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(mockServices)));

    renderWithStatus(<Services />);

    expect(screen.getByRole('heading', { level: 1, name: 'Service Health' })).toBeInTheDocument();
    expect(await screen.findByText('payment-service')).toBeInTheDocument();
    expect(screen.getByText('order-service')).toBeInTheDocument();
    expect(screen.getByText('DEGRADED')).toBeInTheDocument();
    expect(screen.getByText('HEALTHY')).toBeInTheDocument();
  });
});
