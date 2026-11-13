import { NavLink } from 'react-router-dom';
import { IncidentsIcon, LogoMark, LogsIcon, OverviewIcon, SettingsIcon } from './icons.jsx';

const NAV_ITEMS = [
  { to: '/', label: 'Overview', Icon: OverviewIcon, end: true },
  { to: '/logs', label: 'Logs', Icon: LogsIcon },
  { to: '/incidents', label: 'Incidents', Icon: IncidentsIcon },
  { to: '/settings', label: 'Settings', Icon: SettingsIcon },
];

export default function Sidebar({ id, open, onNavigate }) {
  return (
    <aside id={id} className={`sidebar${open ? ' sidebar--open' : ''}`}>
      <div className="sidebar__brand">
        <LogoMark />
        <span className="sidebar__name">TraceLens</span>
      </div>
      <nav aria-label="Main">
        <ul className="nav-list">
          {NAV_ITEMS.map(({ to, label, Icon, end }) => (
            <li key={to}>
              <NavLink
                to={to}
                end={end}
                className={({ isActive }) => `nav-link${isActive ? ' nav-link--active' : ''}`}
                onClick={onNavigate}
              >
                <Icon />
                <span>{label}</span>
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>
      <p className="sidebar__footer">Production debugging copilot</p>
    </aside>
  );
}
