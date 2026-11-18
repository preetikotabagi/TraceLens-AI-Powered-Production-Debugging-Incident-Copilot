import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import ErrorGroups from './ErrorGroups.jsx';
import { jsonResponse, renderWithStatus } from '../test/helpers.jsx';

describe('ErrorGroups page', () => {
  it('renders heading and shows empty state when no error groups exist', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse({ items: [], total: 0 })),
    );

    renderWithStatus(<ErrorGroups />);

    expect(screen.getByRole('heading', { level: 1, name: 'Error Groups' })).toBeInTheDocument();
    expect(await screen.findByText('No recurring error groups')).toBeInTheDocument();
  });

  it('renders error groups list and opens inspection drawer on click', async () => {
    const mockGroups = {
      items: [
        {
          id: 'grp-1',
          fingerprint: 'fp_payment_timeout_123',
          errorType: 'TimeoutException',
          messageTemplate: 'Connection timed out to stripe-gateway:443 after 5000ms',
          serviceName: 'payment-service',
          firstSeen: '2026-10-24T10:00:00Z',
          lastSeen: '2026-10-24T10:15:00Z',
          occurrenceCount: 14,
        },
      ],
      total: 1,
    };

    const mockDetail = {
      group: {
        id: 'grp-1',
        fingerprint: 'fp_payment_timeout_123',
        errorType: 'TimeoutException',
        normalizedMessage: 'Connection timed out to stripe-gateway:443 after 5000ms',
        serviceName: 'payment-service',
        firstSeen: '2026-10-24T10:00:00Z',
        lastSeen: '2026-10-24T10:15:00Z',
        occurrenceCount: 14,
        sampleStackTrace: 'java.util.concurrent.TimeoutException: Connection timed out\n  at com.payment.StripeClient.call(StripeClient.java:55)',
      },
      recentLogs: [],
    };

    vi.stubGlobal('fetch', vi.fn().mockImplementation((url) => {
      if (url.includes('/api/v1/error-groups/fp_')) {
        return Promise.resolve(jsonResponse(mockDetail));
      }
      return Promise.resolve(jsonResponse(mockGroups));
    }));

    renderWithStatus(<ErrorGroups />);

    expect(await screen.findByText('TimeoutException')).toBeInTheDocument();
    expect(screen.getByText('fp_payment_timeout_123')).toBeInTheDocument();

    // Click "Inspect" button
    await userEvent.click(screen.getByRole('button', { name: 'Inspect' }));

    // Drawer should open and display sample stack trace
    expect(await screen.findByText('Sample Stack Trace')).toBeInTheDocument();
    expect(screen.getByText(/com\.payment\.StripeClient\.call/)).toBeInTheDocument();
  });
});
