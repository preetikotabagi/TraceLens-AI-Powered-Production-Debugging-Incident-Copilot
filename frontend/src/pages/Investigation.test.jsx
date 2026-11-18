import { screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import Investigation from './Investigation.jsx';
import { jsonResponse, renderWithStatus } from '../test/helpers.jsx';

describe('Investigation page', () => {
  it('renders heading and prompt when no incident is selected', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ items: [], total: 0 })));

    renderWithStatus(<Investigation />);

    expect(screen.getByRole('heading', { level: 1, name: 'AI-Assisted Incident Copilot' })).toBeInTheDocument();
    expect(await screen.findByText('No investigation reports yet')).toBeInTheDocument();
  });
});
