import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import EmptyState from '../components/EmptyState.jsx';
import ErrorBanner from '../components/ErrorBanner.jsx';
import PageHeader from '../components/PageHeader.jsx';
import { getErrorGroupDetail, listErrorGroups } from '../api/client.js';

const SERVICES = ['ALL', 'payment-service', 'order-service', 'auth-service', 'inventory-service', 'notification-service'];

export default function ErrorGroups() {
  const [searchParams, setSearchParams] = useSearchParams();
  const serviceParam = searchParams.get('service') || 'ALL';

  const [groups, setGroups] = useState([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const [selectedService, setSelectedService] = useState(serviceParam);
  const [selectedGroup, setSelectedGroup] = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);

  useEffect(() => {
    const s = searchParams.get('service') || 'ALL';
    setSelectedService(s);
  }, [searchParams]);

  const fetchGroups = async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await listErrorGroups({
        service: selectedService === 'ALL' ? undefined : selectedService,
        limit: 50,
      });
      setGroups(data.items || []);
      setTotal(data.total || 0);
    } catch (err) {
      setError(err.message || 'Failed to fetch error groups');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchGroups();
  }, [selectedService]);

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

  const openGroupDetail = async (fingerprint) => {
    try {
      setDetailLoading(true);
      const detail = await getErrorGroupDetail(fingerprint);
      setSelectedGroup(detail);
    } catch (err) {
      setError(err.message || 'Failed to load error group details');
    } finally {
      setDetailLoading(false);
    }
  };

  return (
    <>
      <PageHeader
        title="Error Groups"
        description="Normalized error fingerprints clustering recurring production exceptions, lock timeouts, and failures."
      />

      {error && (
        <ErrorBanner title="Error Group failure" onRetry={fetchGroups}>
          {error}
        </ErrorBanner>
      )}

      <section className="card panel" aria-label="Error groups table">
        <div className="panel__header-row">
          <h2 className="panel__title">Fingerprinted Failure Clusters</h2>
          <div className="filter-controls">
            <div className="filter-group">
              <label htmlFor="group-service-filter">Service:</label>
              <select
                id="group-service-filter"
                className="select"
                value={selectedService}
                onChange={(e) => handleServiceChange(e.target.value)}
              >
                {SERVICES.map((s) => (
                  <option key={s} value={s}>{s}</option>
                ))}
              </select>
            </div>
            {selectedService !== 'ALL' && (
              <button
                type="button"
                className="button button--secondary button--sm"
                onClick={() => {
                  setSelectedService('ALL');
                  setSearchParams({}, { replace: true });
                }}
              >
                Clear Filter
              </button>
            )}
            <span className="log-count-summary">Total Clusters: <strong>{total}</strong></span>
          </div>
        </div>

        {loading ? (
          <div className="loading-state"><p>Loading error groups…</p></div>
        ) : groups.length > 0 ? (
          <div className="table-responsive">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Fingerprint</th>
                  <th>Error Type</th>
                  <th>Service</th>
                  <th>Message Template</th>
                  <th>Occurrences</th>
                  <th>Last Seen</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {groups.map((grp) => (
                  <tr key={grp.fingerprint}>
                    <td><code>{grp.fingerprint}</code></td>
                    <td><span className="badge badge--error">{grp.errorType}</span></td>
                    <td><strong>{grp.serviceName}</strong></td>
                    <td className="log-cell--message">
                      <span className="message-text">{grp.messageTemplate}</span>
                    </td>
                    <td><strong>{grp.occurrenceCount}</strong></td>
                    <td>{grp.lastSeen ? new Date(grp.lastSeen).toLocaleString() : '—'}</td>
                    <td>
                      <button
                        type="button"
                        className="button button--secondary button--sm"
                        onClick={() => openGroupDetail(grp.fingerprint)}
                        aria-label="Inspect"
                      >
                        Inspect
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <EmptyState title="No recurring error groups">
            {selectedService !== 'ALL'
              ? `No recurring error groups detected for service "${selectedService}".`
              : 'No recurring error groups detected yet. Seed sample logs or wait for incoming failure events.'}
          </EmptyState>
        )}
      </section>

      {/* Drawer / Modal for Error Group Details */}
      {selectedGroup && (
        <div className="modal-backdrop" onClick={() => setSelectedGroup(null)}>
          <div
            className="modal-drawer"
            role="dialog"
            aria-labelledby="group-drawer-title"
            aria-modal="true"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="modal-drawer__header">
              <h2 id="group-drawer-title">
                Fingerprint Details: <code>{selectedGroup.group.fingerprint}</code>
              </h2>
              <button
                type="button"
                className="button button--secondary button--sm"
                onClick={() => setSelectedGroup(null)}
              >
                Close
              </button>
            </div>

            <div className="modal-drawer__body">
              <dl className="detail-list">
                <div className="detail-list__row">
                  <dt>Error Type</dt>
                  <dd><span className="badge badge--error">{selectedGroup.group.errorType}</span></dd>
                </div>
                <div className="detail-list__row">
                  <dt>Service Name</dt>
                  <dd><code>{selectedGroup.group.serviceName}</code></dd>
                </div>
                <div className="detail-list__row">
                  <dt>Total Occurrences</dt>
                  <dd><strong>{selectedGroup.group.occurrenceCount}</strong></dd>
                </div>
                <div className="detail-list__row">
                  <dt>First Seen</dt>
                  <dd>{selectedGroup.group.firstSeen ? new Date(selectedGroup.group.firstSeen).toLocaleString() : '—'}</dd>
                </div>
                <div className="detail-list__row">
                  <dt>Last Seen</dt>
                  <dd>{selectedGroup.group.lastSeen ? new Date(selectedGroup.group.lastSeen).toLocaleString() : '—'}</dd>
                </div>
              </dl>

              <div className="detail-block">
                <h3>Normalized Message Template</h3>
                <pre className="code-block">{selectedGroup.group.messageTemplate}</pre>
              </div>

              <div className="detail-block">
                <h3>Sample Stack Trace</h3>
                {selectedGroup.group.sampleStackTrace ? (
                  <pre className="code-block code-block--stack">{selectedGroup.group.sampleStackTrace}</pre>
                ) : (
                  <p className="hint text-muted" style={{ fontStyle: 'italic', padding: '0.5rem 0' }}>
                    No stack trace captured for this error pattern (log event recorded at message level).
                  </p>
                )}
              </div>

              <div className="detail-block">
                <h3>Recent Sample Occurrences ({selectedGroup.sampleLogs?.length || 0})</h3>
                {selectedGroup.sampleLogs && selectedGroup.sampleLogs.length > 0 ? (
                  <ul className="sample-logs-list">
                    {selectedGroup.sampleLogs.map((l) => (
                      <li key={l.id} className="sample-log-item">
                        <span className="sample-log-time">{new Date(l.timestamp).toLocaleTimeString()}</span>
                        {l.requestId ? (
                          <Link
                            to={`/logs?query=${encodeURIComponent(l.requestId)}&service=${encodeURIComponent(selectedGroup.group.serviceName)}`}
                            className="sample-log-req link--mono"
                            title="Inspect this trace in Logs"
                          >
                            <code>{l.requestId}</code>
                          </Link>
                        ) : (
                          <code className="sample-log-req">no-req-id</code>
                        )}
                        <span className="sample-log-msg">{l.message}</span>
                      </li>
                    ))}
                  </ul>
                ) : (
                  <p className="hint">No sample occurrences logged.</p>
                )}
              </div>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
