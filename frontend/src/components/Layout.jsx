import { useEffect, useState } from 'react';
import { Outlet } from 'react-router-dom';
import Sidebar from './Sidebar.jsx';
import StatusIndicator from './StatusIndicator.jsx';
import { CloseIcon, MenuIcon } from './icons.jsx';
import { useBackendStatusContext } from '../context/backendStatusContext.js';

const SIDEBAR_ID = 'app-sidebar';

export default function Layout() {
  const [navOpen, setNavOpen] = useState(false);
  const { state } = useBackendStatusContext();

  // Escape closes the mobile navigation drawer.
  useEffect(() => {
    if (!navOpen) return undefined;
    const onKeyDown = (event) => {
      if (event.key === 'Escape') setNavOpen(false);
    };
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [navOpen]);

  return (
    <div className="app-shell">
      <a className="skip-link" href="#main-content">
        Skip to main content
      </a>

      <Sidebar id={SIDEBAR_ID} open={navOpen} onNavigate={() => setNavOpen(false)} />
      {navOpen && <div className="scrim" aria-hidden="true" onClick={() => setNavOpen(false)} />}

      <div className="app-body">
        <div className="topbar">
          <button
            type="button"
            className="icon-button topbar__menu"
            aria-label={navOpen ? 'Close navigation' : 'Open navigation'}
            aria-expanded={navOpen}
            aria-controls={SIDEBAR_ID}
            onClick={() => setNavOpen((open) => !open)}
          >
            {navOpen ? <CloseIcon /> : <MenuIcon />}
          </button>
          <span className="topbar__title">TraceLens</span>
          <StatusIndicator state={state} />
        </div>
        <main id="main-content" className="main" tabIndex={-1}>
          <Outlet />
        </main>
      </div>
    </div>
  );
}
