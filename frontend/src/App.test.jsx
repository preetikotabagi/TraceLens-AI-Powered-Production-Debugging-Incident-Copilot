import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import App from './App.jsx';
import { BackendStatusProvider } from './context/BackendStatusProvider.jsx';
import { HEALTHY, jsonResponse } from './test/helpers.jsx';

function renderApp(route = '/') {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(HEALTHY)));
  return render(
    <MemoryRouter initialEntries={[route]}>
      <BackendStatusProvider>
        <App />
      </BackendStatusProvider>
    </MemoryRouter>,
  );
}

describe('App shell', () => {
  it('has a sidebar with Overview, Logs, Incidents and Settings', async () => {
    renderApp();

    const nav = screen.getByRole('navigation', { name: 'Main' });
    for (const name of ['Overview', 'Logs', 'Incidents', 'Settings']) {
      expect(within(nav).getByRole('link', { name })).toBeInTheDocument();
    }
    expect(await screen.findByText('Backend connected')).toBeInTheDocument();
  });

  it('marks the current page and navigates between pages', async () => {
    renderApp();
    const nav = screen.getByRole('navigation', { name: 'Main' });

    expect(within(nav).getByRole('link', { name: 'Overview' })).toHaveAttribute('aria-current', 'page');

    await userEvent.click(within(nav).getByRole('link', { name: 'Logs' }));
    expect(screen.getByRole('heading', { level: 1, name: 'Logs' })).toBeInTheDocument();
    expect(within(nav).getByRole('link', { name: 'Logs' })).toHaveAttribute('aria-current', 'page');

    await userEvent.click(within(nav).getByRole('link', { name: 'Incidents' }));
    expect(screen.getByRole('heading', { level: 1, name: 'Incidents' })).toBeInTheDocument();
    expect(screen.getByText('No incidents')).toBeInTheDocument();
  });

  it('redirects unknown routes to the overview', () => {
    renderApp('/does-not-exist');

    expect(screen.getByRole('heading', { level: 1, name: 'Overview' })).toBeInTheDocument();
  });

  it('offers a skip link and a labelled mobile menu button', () => {
    renderApp();

    expect(screen.getByRole('link', { name: 'Skip to main content' })).toHaveAttribute('href', '#main-content');
    const menu = screen.getByRole('button', { name: 'Open navigation' });
    expect(menu).toHaveAttribute('aria-expanded', 'false');
  });

  it('opens the navigation drawer and closes it with Escape', async () => {
    renderApp();

    await userEvent.click(screen.getByRole('button', { name: 'Open navigation' }));
    const close = screen.getByRole('button', { name: 'Close navigation' });
    expect(close).toHaveAttribute('aria-expanded', 'true');

    await userEvent.keyboard('{Escape}');
    expect(screen.getByRole('button', { name: 'Open navigation' })).toHaveAttribute('aria-expanded', 'false');
  });

  it('shows the unreachable state in the top bar when the backend is down', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));
    render(
      <MemoryRouter>
        <BackendStatusProvider>
          <App />
        </BackendStatusProvider>
      </MemoryRouter>,
    );

    expect(await screen.findAllByText('Backend unreachable')).not.toHaveLength(0);
    expect(await screen.findByRole('alert')).toHaveTextContent('Cannot reach the TraceLens backend');
  });
});
