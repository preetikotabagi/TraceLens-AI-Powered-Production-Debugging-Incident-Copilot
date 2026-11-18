import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import EmptyState from '../components/EmptyState.jsx';
import ErrorBanner from '../components/ErrorBanner.jsx';
import PageHeader from '../components/PageHeader.jsx';
import { getServiceHealth } from '../api/client.js';

export default function Services() {
  const [services, setServices] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const fetchHealth = async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await getServiceHealth();
      setServices(data || []);
    } catch (err) {
      setError(err.message || 'Failed to fetch service health statuses');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchHealth();
  }, []);

  return (
    <>
      <PageHeader
        title="Service Health"
        description="Operational health, throughput, and error rates across all microservices reporting logs."
      />

      {error && (
        <ErrorBanner title="Service Status Notice" onRetry={fetchHealth}>
          {error}
        </ErrorBanner>
      )}

      {loading ? (
        <div className="card panel loading-state">
          <p>Analyzing service telemetry…</p>
        </div>
      ) : services.length > 0 ? (
        <div className="services-grid">
          {services.map((svc) => (
            <article key={svc.serviceName} className="card panel service-card">
              <div className="service-card__header">
                <span className={`badge badge--status-${svc.status.toLowerCase()}`}>
                  {svc.status}
                </span>
                <span className="service-card__last-seen">
                  {svc.lastSeen ? `Last active: ${new Date(svc.lastSeen).toLocaleTimeString()}` : 'No activity'}
                </span>
              </div>

              <h2 className="service-card__title">{svc.serviceName}</h2>

              <dl className="detail-list">
                <div className="detail-list__row">
                  <dt>Error Rate</dt>
                  <dd>
                    <strong className={svc.errorRate > 5 ? 'text-danger' : 'text-success'}>
                      {svc.errorRate}%
                    </strong>
                  </dd>
                </div>
                <div className="detail-list__row">
                  <dt>Errors / Total</dt>
                  <dd>{svc.errorCount} / {svc.totalLogs} logs</dd>
                </div>
              </dl>

              <div className="service-card__footer">
                <Link
                  to={`/logs?service=${svc.serviceName}`}
                  className="button button--secondary button--sm"
                >
                  Inspect Service Logs →
                </Link>
                <Link
                  to={`/incidents?service=${svc.serviceName}`}
                  className="button button--secondary button--sm"
                >
                  View Incidents
                </Link>
              </div>
            </article>
          ))}
        </div>
      ) : (
        <section className="card panel">
          <EmptyState title="No services registered">
            No service telemetry has been ingested yet. Click &quot;Seed 1,000+ Production Logs&quot; from the Overview to simulate realistic services.
          </EmptyState>
        </section>
      )}
    </>
  );
}
