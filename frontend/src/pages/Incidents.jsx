import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import EmptyState from '../components/EmptyState.jsx';
import ErrorBanner from '../components/ErrorBanner.jsx';
import PageHeader from '../components/PageHeader.jsx';
import { listIncidents, updateIncidentStatus } from '../api/client.js';

const STATUSES = ['ALL', 'OPEN', 'INVESTIGATING', 'RESOLVED', 'MUTED'];
const SEVERITIES = ['ALL', 'CRITICAL', 'HIGH', 'MEDIUM', 'LOW'];
const SERVICES = ['ALL', 'payment-service', 'order-service', 'auth-service', 'inventory-service', 'notification-service'];

export default function Incidents() {
  const [searchParams, setSearchParams] = useSearchParams();
  const serviceParam = searchParams.get('service') || 'ALL';

  const [incidents, setIncidents] = useState([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const [selectedStatus, setSelectedStatus] = useState('ALL');
  const [selectedSeverity, setSelectedSeverity] = useState('ALL');
  const [selectedService, setSelectedService] = useState(serviceParam);
  const [statusUpdatingId, setStatusUpdatingId] = useState(null);

  // Sync state if URL query params change (e.g. from Service Health links)
  useEffect(() => {
    const s = searchParams.get('service') || 'ALL';
    setSelectedService(s);
  }, [searchParams]);

  const fetchIncidents = async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await listIncidents({
        service: selectedService === 'ALL' ? undefined : selectedService,
        status: selectedStatus === 'ALL' ? undefined : selectedStatus,
        severity: selectedSeverity === 'ALL' ? undefined : selectedSeverity,
        limit: 50,
      });
      setIncidents(data.items || []);
      setTotal(data.total || 0);
    } catch (err) {
      setError(err.message || 'Failed to fetch incidents');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchIncidents();
  }, [selectedStatus, selectedSeverity, selectedService]);

  const handleServiceChange = (newService) => {
    setSelectedService(newService);
    const next = new URLSearchParams(searchParams);
    if (newService && newService !== 'ALL') {
      next.set('service', newService);
    } else {
      next.delete('service');
    }
    setSearchParams(next, { replace: true });
  };

  const handleStatusChange = async (incidentId, newStatus) => {
    try {
      setStatusUpdatingId(incidentId);
      await updateIncidentStatus(incidentId, newStatus);
      await fetchIncidents();
    } catch (err) {
      setError(err.message || 'Failed to update incident status');
    } finally {
      setStatusUpdatingId(null);
    }
  };

  const cleanIncidentTitle = (rawTitle) => {
    return (rawTitle || '').replace(/^\[[A-Za-z]+\]\s*/, '');
  };

  return (
    <>
      <PageHeader
        title="Incidents"
        description="Rule-detected production incidents prioritized by severity, recurring frequency, and service impact."
      />

      {error && (
        <ErrorBanner title="Incident Error" onRetry={fetchIncidents}>
          {error}
        </ErrorBanner>
      )}

      {/* Filter Tabs & Controls */}
      <section className="card panel filter-bar" aria-label="Incident filters">
        <div className="tab-group" role="tablist" aria-label="Filter by incident status">
          {STATUSES.map((st) => (
            <button
              key={st}
              type="button"
              role="tab"
              aria-selected={selectedStatus === st}
              className={`tab-button${selectedStatus === st ? ' tab-button--active' : ''}`}
              onClick={() => setSelectedStatus(st)}
            >
              {st}
            </button>
          ))}
        </div>

        <div className="filter-controls">
          <div className="filter-group">
            <label htmlFor="incident-service-filter">Service:</label>
            <select
              id="incident-service-filter"
              className="select"
              value={selectedService}
              onChange={(e) => handleServiceChange(e.target.value)}
            >
              {SERVICES.map((s) => (
                <option key={s} value={s}>{s}</option>
              ))}
            </select>
          </div>

          <div className="filter-group">
            <label htmlFor="severity-filter">Severity:</label>
            <select
              id="severity-filter"
              className="select"
              value={selectedSeverity}
              onChange={(e) => setSelectedSeverity(e.target.value)}
            >
              {SEVERITIES.map((sev) => (
                <option key={sev} value={sev}>{sev}</option>
              ))}
            </select>
          </div>

          {(selectedService !== 'ALL' || selectedSeverity !== 'ALL' || selectedStatus !== 'ALL') && (
            <button
              type="button"
              className="button button--secondary button--sm"
              onClick={() => {
                setSelectedService('ALL');
                setSelectedSeverity('ALL');
                setSelectedStatus('ALL');
                setSearchParams({}, { replace: true });
              }}
              title="Reset all filters"
            >
              Clear Filters
            </button>
          )}

          <span className="log-count-summary">
            Total Incidents: <strong>{total}</strong>
          </span>
        </div>
      </section>

      {/* Incident List */}
      <section className="stack" aria-label="Incident cards">
        {loading ? (
          <div className="card panel loading-state">
            <p>Loading detected incidents…</p>
          </div>
        ) : incidents.length > 0 ? (
          incidents.map((incident) => (
            <article key={incident.id} className="card panel incident-card">
              <div className="incident-card__header">
                <div className="incident-card__badges">
                  <span className={`badge badge--severity-${incident.severity.toLowerCase()}`}>
                    {incident.severity}
                  </span>
                  <span className={`badge badge--status-${incident.status.toLowerCase()}`}>
                    {incident.status}
                  </span>
                  <span className="incident-card__service">
                    Service: <strong>{incident.serviceName}</strong>
                  </span>
                </div>
                <div className="incident-card__status-control">
                  <label htmlFor={`status-select-${incident.id}`} className="visually-hidden">
                    Change status
                  </label>
                  <select
                    id={`status-select-${incident.id}`}
                    className="select select--sm"
                    value={incident.status}
                    disabled={statusUpdatingId === incident.id}
                    onChange={(e) => handleStatusChange(incident.id, e.target.value)}
                  >
                    <option value="OPEN">Mark as OPEN</option>
                    <option value="INVESTIGATING">Mark as INVESTIGATING</option>
                    <option value="RESOLVED">Mark as RESOLVED</option>
                    <option value="MUTED">Mark as MUTED</option>
                  </select>
                </div>
              </div>

              <h3 className="incident-card__title">
                #{incident.id} — [{incident.severity}] {cleanIncidentTitle(incident.title)}
              </h3>
              <p className="incident-card__summary">{incident.summary}</p>

              <div className="incident-card__meta">
                <div className="meta-item">
                  <span className="meta-label">Trigger Rule:</span>
                  <code>{incident.triggerRule}</code>
                </div>
                <div className="meta-item">
                  <span className="meta-label">Error Fingerprint:</span>
                  <code>{incident.fingerprint || '—'}</code>
                </div>
                <div className="meta-item">
                  <span className="meta-label">Occurrences:</span>
                  <strong>{incident.occurrenceCount}</strong>
                </div>
                <div className="meta-item">
                  <span className="meta-label">Last Seen:</span>
                  <span>{incident.lastSeen ? new Date(incident.lastSeen).toLocaleString() : '—'}</span>
                </div>
              </div>

              <div className="incident-card__actions">
                <Link
                  to={`/investigation?incidentId=${incident.id}`}
                  className="button button--primary"
                >
                  🤖 Investigate with AI Copilot
                </Link>
                <Link
                  to={`/logs?service=${incident.serviceName}`}
                  className="button button--secondary"
                >
                  View Related Logs
                </Link>
              </div>
            </article>
          ))
        ) : (
          <div className="card panel">
            <EmptyState title="No incidents">
              {selectedStatus !== 'ALL' || selectedSeverity !== 'ALL' || selectedService !== 'ALL'
                ? 'No incidents match the active filter criteria.'
                : 'No production incidents currently detected. Use the Overview or Settings to seed realistic failure scenarios.'}
            </EmptyState>
          </div>
        )}
      </section>
    </>
  );
}
