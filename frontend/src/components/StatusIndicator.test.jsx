import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import StatusIndicator from './StatusIndicator.jsx';

describe('StatusIndicator', () => {
  it.each([
    ['loading', 'Checking backend…'],
    ['up', 'Backend connected'],
    ['down', 'Database unavailable'],
    ['unreachable', 'Backend unreachable'],
  ])('shows readable text for the %s state', (state, label) => {
    render(<StatusIndicator state={state} />);

    const indicator = screen.getByRole('status');
    expect(indicator).toHaveTextContent(label);
    expect(indicator).toHaveAttribute('aria-live', 'polite');
  });
});
