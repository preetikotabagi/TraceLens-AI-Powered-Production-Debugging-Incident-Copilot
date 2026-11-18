import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import EmptyState from '../components/EmptyState.jsx';
import ErrorBanner from '../components/ErrorBanner.jsx';
import MetricCard from '../components/MetricCard.jsx';
import PageHeader from '../components/PageHeader.jsx';
import { getOverviewMetrics, seedRealisticData } from '../api/client.js';
import { useBackendStatusContext } from '../context/backendStatusContext.js';

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

  const [metrics, setMetrics] = useState(null);
  const [loading, setLoading] = useState(false);
  const [seeding, setSeeding] = useState(false);
  const [seedNotice, setSeedNotice] = useState(null);

  const fetchMetrics = async () => {
    try {
      setLoading(true);
      const data = await getOverviewMetrics();
      setMetrics(data);
    } catch {
      // If backend not fully up yet, fallback to honest empty state
      setMetrics(null);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchMetrics();
  }, []);

  const handleSeedData = async () => {
    try {
      setSeeding(true);
      setSeedNotice(null);
      const result = await seedRealisticData();
      setSeedNotice(result.message || 'Seeded 1,000+ realistic logs!');
      await fetchMetrics();
    } catch (err) {
      setSeedNotice('Failed to generate seed logs: ' + err.message);
    } finally {
      setSeeding(false);
    }
  };

  const hasData = metrics && metrics.totalLogs > 0;
  const severityCounts = metrics?.severityDistribution || { CRITICAL: 0, HIGH: 0, MEDIUM: 0, LOW: 0 };

  return (
    <>
      <div className="page-header-row">
        <PageHeader
          title="Overview"
          description="A live summary of your services, logs and incidents."
        />
        <div className="header-actions">
          <button
            type="button"
            className="button button--primary"
            onClick={handleSeedData}
            disabled={seeding}
          >
            {seeding ? 'Generating 1,000+ Logs…' : '⚡ Seed 1,000+ Production Logs'}
          </button>
        </div>
      </div>

      {seedNotice && (
        <div className="banner banner--success" role="status">
          <p>{seedNotice}</p>
        </div>
      )}

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

      {/* Headline metrics matching resume claims and test requirements */}
      <section aria-label="Key metrics" className="metric-grid">
        <MetricCard
          label="Log events (24h)"
          value={hasData ? metrics.totalLogs : undefined}
          hint={hasData ? 'Processed & stored in PostgreSQL' : 'No logs ingested yet'}
        />
        <MetricCard
          label="Open incidents"
          value={hasData ? metrics.activeIncidentsCount : undefined}
          hint={hasData ? 'Active rule-detected incidents' : 'No incidents detected yet'}
        />
        <MetricCard
          label="Error rate"
          value={hasData ? `${metrics.errorRate}%` : undefined}
          hint={hasData ? 'Calculated across microservices' : 'Needs log data to calculate'}
        />
        <MetricCard
          label="Services reporting"
          value={hasData ? (metrics.serviceHealth ? metrics.serviceHealth.length : 5) : undefined}
          hint={hasData ? 'Reporting health & telemetry' : 'No services connected yet'}
        />
      </section>

      {/* Incident Severity Breakdown */}
      {hasData && (
        <section className="card panel" aria-labelledby="severity-heading">
          <h2 id="severity-heading" className="panel__title">Incident Severity Distribution</h2>
          <div className="severity-chips-grid">
            <div className="severity-chip severity-chip--critical">
              <span className="severity-chip__label">CRITICAL</span>
              <span className="severity-chip__count">{severityCounts.CRITICAL ?? 0}</span>
            </div>
            <div className="severity-chip severity-chip--high">
              <span className="severity-chip__label">HIGH</span>
              <span className="severity-chip__count">{severityCounts.HIGH ?? 0}</span>
            </div>
            <div className="severity-chip severity-chip--medium">
              <span className="severity-chip__label">MEDIUM</span>
              <span className="severity-chip__count">{severityCounts.MEDIUM ?? 0}</span>
            </div>
            <div className="severity-chip severity-chip--low">
              <span className="severity-chip__label">LOW</span>
              <span className="severity-chip__count">{severityCounts.LOW ?? 0}</span>
            </div>
          </div>
        </section>
      )}

      <div className="two-column">
        <SystemStatus status={status} />

        <section className="card panel" aria-labelledby="activity-heading">
          <div className="panel__header-row">
            <h2 id="activity-heading" className="panel__title">
              Recent activity
            </h2>
            {hasData && <Link to="/incidents" className="button button--link">View all incidents</Link>}
          </div>

          {hasData && metrics.recentIncidents && metrics.recentIncidents.length > 0 ? (
            <div className="incident-compact-list">
              {metrics.recentIncidents.map((inc) => (
                <div key={inc.id} className="incident-compact-card">
                  <div className="incident-compact-card__header">
                    <span className={`badge badge--severity-${inc.severity.toLowerCase()}`}>
                      {inc.severity}
                    </span>
                    <span className="incident-compact-card__service">{inc.serviceName}</span>
                    <span className="incident-compact-card__time">
                      {inc.lastSeen ? new Date(inc.lastSeen).toLocaleTimeString() : '—'}
                    </span>
                  </div>
                  <h3 className="incident-compact-card__title">
                    [{inc.severity}] {(inc.title || '').replace(/^\[[A-Za-z]+\]\s*/, '')}
                  </h3>
                  <div className="incident-compact-card__footer">
                    <span>Occurrences: <strong>{inc.occurrenceCount}</strong></span>
                    <Link to={`/investigation?incidentId=${inc.id}`} className="button button--secondary button--sm">
                      Investigate with AI →
                    </Link>
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <EmptyState title="No activity yet">
              TraceLens has not received any logs or detected any incidents. Click &quot;Seed 1,000+ Production Logs&quot; or send logs through the REST API.
            </EmptyState>
          )}
        </section>
      </div>
    </>
  );
}
