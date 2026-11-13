import EmptyState from '../components/EmptyState.jsx';
import ErrorBanner from '../components/ErrorBanner.jsx';
import MetricCard from '../components/MetricCard.jsx';
import PageHeader from '../components/PageHeader.jsx';
import { useBackendStatusContext } from '../context/backendStatusContext.js';

// Metrics that later milestones will fill in. Until then they honestly show "no data".
const METRICS = [
  { label: 'Log events (24h)', hint: 'No logs ingested yet' },
  { label: 'Open incidents', hint: 'No incidents detected yet' },
  { label: 'Error rate', hint: 'Needs log data to calculate' },
  { label: 'Services reporting', hint: 'No services connected yet' },
];

function formatTime(date) {
  return date ? date.toLocaleTimeString() : '—';
}

function SystemStatus({ status }) {
  const { state, health, checkedAt } = status;
  const loading = state === 'loading';
  const rows = [
    ['API', loading ? null : state === 'unreachable' ? 'Unreachable' : health?.status === 'UP' ? 'Healthy' : 'Degraded'],
    ['Database', loading ? null : health?.database?.status === 'UP' ? 'Connected' : 'Unavailable'],
    [
      'Database latency',
      loading ? null : health?.database?.latencyMs != null ? `${health.database.latencyMs} ms` : '—',
    ],
    ['Schema version', loading ? null : health?.database?.migrationVersion ?? '—'],
    ['Last checked', loading ? null : formatTime(checkedAt)],
  ];

  return (
    <section className="card panel" aria-labelledby="system-status-heading" aria-busy={loading}>
      <h2 id="system-status-heading" className="panel__title">
        System status
      </h2>
      <dl className="detail-list">
        {rows.map(([term, value]) => (
          <div className="detail-list__row" key={term}>
            <dt>{term}</dt>
            <dd>{value === null ? <span className="skeleton skeleton--text" aria-label="Loading" /> : value}</dd>
          </div>
        ))}
      </dl>
    </section>
  );
}

export default function Overview() {
  const status = useBackendStatusContext();
  const { state, refresh } = status;

  return (
    <>
      <PageHeader
        title="Overview"
        description="A live summary of your services, logs and incidents."
      />

      {state === 'unreachable' && (
        <ErrorBanner title="Cannot reach the TraceLens backend" onRetry={refresh}>
          Check that the backend is running and that VITE_API_BASE_URL points to it. The dashboard will keep
          retrying automatically.
        </ErrorBanner>
      )}
      {state === 'down' && (
        <ErrorBanner title="The backend is running but its database is unavailable" onRetry={refresh}>
          Data cannot be loaded or stored until the database connection is restored.
        </ErrorBanner>
      )}

      <section aria-label="Key metrics" className="metric-grid">
        {METRICS.map((metric) => (
          <MetricCard key={metric.label} label={metric.label} hint={metric.hint} />
        ))}
      </section>

      <div className="two-column">
        <SystemStatus status={status} />
        <section className="card panel" aria-labelledby="activity-heading">
          <h2 id="activity-heading" className="panel__title">
            Recent activity
          </h2>
          <EmptyState title="No activity yet">
            TraceLens has not received any logs or detected any incidents. Log ingestion and incident
            detection are not part of this foundation build, so this area stays empty for now.
          </EmptyState>
        </section>
      </div>
    </>
  );
}
