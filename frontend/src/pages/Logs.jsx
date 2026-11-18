import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import EmptyState from '../components/EmptyState.jsx';
import ErrorBanner from '../components/ErrorBanner.jsx';
import PageHeader from '../components/PageHeader.jsx';
import { searchLogs } from '../api/client.js';

const LEVELS = ['ALL', 'ERROR', 'WARN', 'INFO', 'DEBUG'];
const SERVICES = ['ALL', 'payment-service', 'order-service', 'auth-service', 'inventory-service', 'notification-service'];

export default function Logs() {
  const [searchParams, setSearchParams] = useSearchParams();
  const serviceParam = searchParams.get('service') || 'ALL';
  const queryParam = searchParams.get('query') || '';

  const [logs, setLogs] = useState([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // Filters
  const [searchQuery, setSearchQuery] = useState(queryParam);
  const [selectedLevel, setSelectedLevel] = useState('ALL');
  const [selectedService, setSelectedService] = useState(serviceParam);
  const [page, setPage] = useState(0);
  const pageSize = 25;

  // Selected log for inspection drawer
  const [inspectingLog, setInspectingLog] = useState(null);

  // Sync state if URL query params change (e.g. navigation from other pages)
  useEffect(() => {
    const s = searchParams.get('service') || 'ALL';
    const q = searchParams.get('query') || '';
    setSelectedService(s);
    setSearchQuery(q);
    setPage(0);
  }, [searchParams]);

  const fetchLogs = async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await searchLogs({
        service: selectedService === 'ALL' ? undefined : selectedService,
        level: selectedLevel === 'ALL' ? undefined : selectedLevel,
        query: searchQuery.trim() || undefined,
        limit: pageSize,
        offset: page * pageSize,
      });
      setLogs(data.items || []);
      setTotal(data.total || 0);
    } catch (err) {
      setError(err.message || 'Failed to fetch logs');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchLogs();
  }, [selectedLevel, selectedService, page]);

  const handleSearchSubmit = (e) => {
    e.preventDefault();
    setPage(0);
    const next = new URLSearchParams(searchParams);
    if (searchQuery.trim()) {
      next.set('query', searchQuery.trim());
    } else {
      next.delete('query');
    }
    setSearchParams(next, { replace: true });
    fetchLogs();
  };

  const handleServiceChange = (newService) => {
    setSelectedService(newService);
    setPage(0);
    const next = new URLSearchParams(searchParams);
    if (newService && newService !== 'ALL') {
      next.set('service', newService);
    } else {
      next.delete('service');
    }
    setSearchParams(next, { replace: true });
  };

  const handleClearFilters = () => {
    setSelectedService('ALL');
    setSelectedLevel('ALL');
    setSearchQuery('');
    setPage(0);
    setSearchParams({}, { replace: true });
  };

  const totalPages = Math.ceil(total / pageSize);

  return (
    <>
      <PageHeader
        title="Logs"
        description="Search, filter, paginate, and inspect production log events across all services."
      />

      {error && (
        <ErrorBanner title="Failed to load logs" onRetry={fetchLogs}>
          {error}
        </ErrorBanner>
      )}

      {/* Filter and Search Bar */}
      <section className="card panel log-filter-bar" aria-label="Log search and filters">
        <form onSubmit={handleSearchSubmit} className="search-form">
          <input
            type="search"
            className="input"
            placeholder="Search log messages, error types, request IDs..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            aria-label="Search logs"
          />
          <button type="submit" className="button button--primary">
            Search
          </button>
        </form>

        <div className="filter-controls">
          <div className="filter-group">
            <label htmlFor="service-select">Service:</label>
            <select
              id="service-select"
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
            <label htmlFor="level-select">Level:</label>
            <select
              id="level-select"
              className="select"
              value={selectedLevel}
              onChange={(e) => {
                setSelectedLevel(e.target.value);
                setPage(0);
              }}
            >
              {LEVELS.map((lvl) => (
                <option key={lvl} value={lvl}>{lvl}</option>
              ))}
            </select>
          </div>

          {(selectedService !== 'ALL' || selectedLevel !== 'ALL' || searchQuery) && (
            <button
              type="button"
              className="button button--secondary button--sm"
              onClick={handleClearFilters}
              title="Reset all filters"
            >
              Clear Filters
            </button>
          )}

          <span className="log-count-summary">
            Found <strong>{total}</strong> {total === 1 ? 'event' : 'events'}
          </span>
        </div>
      </section>

      {/* Logs Table */}
      <section className="card panel" aria-label="Log events table">
        {loading ? (
          <div className="loading-state">
            <p>Loading log events…</p>
          </div>
        ) : logs.length > 0 ? (
          <>
            <div className="table-responsive">
              <table className="data-table log-table">
                <thead>
                  <tr>
                    <th>Timestamp</th>
                    <th>Level</th>
                    <th>Service</th>
                    <th>Message</th>
                    <th>Request ID</th>
                    <th>Fingerprint</th>
                  </tr>
                </thead>
                <tbody>
                  {logs.map((log) => (
                    <tr
                      key={log.id}
                      onClick={() => setInspectingLog(log)}
                      className={`log-row log-row--${log.level.toLowerCase()}`}
                      tabIndex={0}
                      role="button"
                      aria-label={`Inspect log from ${log.serviceName}`}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter' || e.key === ' ') setInspectingLog(log);
                      }}
                    >
                      <td className="log-cell--time">
                        {log.timestamp ? new Date(log.timestamp).toLocaleTimeString() : '—'}
                      </td>
                      <td>
                        <span className={`badge badge--level-${log.level.toLowerCase()}`}>
                          {log.level}
                        </span>
                      </td>
                      <td className="log-cell--service">
                        <strong>{log.serviceName}</strong>
                      </td>
                      <td className="log-cell--message">
                        <span className="message-text">{log.message}</span>
                      </td>
                      <td className="log-cell--mono">
                        {log.requestId ? <code>{log.requestId}</code> : '—'}
                      </td>
                      <td className="log-cell--mono">
                        {log.fingerprint ? <code>{log.fingerprint}</code> : '—'}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {/* Pagination Controls */}
            {totalPages > 1 && (
              <div className="pagination">
                <button
                  type="button"
                  className="button button--secondary"
                  disabled={page === 0}
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                >
                  ← Previous
                </button>
                <span className="pagination__info">
                  Page <strong>{page + 1}</strong> of <strong>{totalPages}</strong>
                </span>
                <button
                  type="button"
                  className="button button--secondary"
                  disabled={page >= totalPages - 1}
                  onClick={() => setPage((p) => p + 1)}
                >
                  Next →
                </button>
              </div>
            )}
          </>
        ) : (
          <EmptyState title="No logs found">
            {searchQuery || selectedLevel !== 'ALL' || selectedService !== 'ALL'
              ? 'No log records match your search criteria. Try broadening your filters.'
              : 'No logs have been ingested yet. Seed sample logs or send logs through the REST API.'}
          </EmptyState>
        )}
      </section>

      {/* Log Inspector Drawer Modal */}
      {inspectingLog && (
        <div className="modal-backdrop" onClick={() => setInspectingLog(null)}>
          <div
            className="modal-drawer"
            role="dialog"
            aria-labelledby="log-drawer-title"
            aria-modal="true"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="modal-drawer__header">
              <h2 id="log-drawer-title">Log Event Details #{inspectingLog.id}</h2>
              <button
                type="button"
                className="button button--secondary button--sm"
                onClick={() => setInspectingLog(null)}
              >
                Close
              </button>
            </div>
            <div className="modal-drawer__body">
              <dl className="detail-list">
                <div className="detail-list__row">
                  <dt>Timestamp</dt>
                  <dd>{inspectingLog.timestamp}</dd>
                </div>
                <div className="detail-list__row">
                  <dt>Level</dt>
                  <dd>
                    <span className={`badge badge--level-${inspectingLog.level.toLowerCase()}`}>
                      {inspectingLog.level}
                    </span>
                  </dd>
                </div>
                <div className="detail-list__row">
                  <dt>Service</dt>
                  <dd><code>{inspectingLog.serviceName}</code></dd>
                </div>
                <div className="detail-list__row">
                  <dt>Request ID</dt>
                  <dd><code>{inspectingLog.requestId || '—'}</code></dd>
                </div>
                <div className="detail-list__row">
                  <dt>Error Fingerprint</dt>
                  <dd><code>{inspectingLog.fingerprint || '—'}</code></dd>
                </div>
                <div className="detail-list__row">
                  <dt>Associated Incident ID</dt>
                  <dd>{inspectingLog.incidentId ? `#${inspectingLog.incidentId}` : 'None'}</dd>
                </div>
                <div className="detail-list__row">
                  <dt>Logger / Thread</dt>
                  <dd>{inspectingLog.logger || '—'} ({inspectingLog.threadName || 'main'})</dd>
                </div>
              </dl>

              <div className="detail-block">
                <h3>Message</h3>
                <pre className="code-block">{inspectingLog.message}</pre>
              </div>

              {inspectingLog.stackTrace && (
                <div className="detail-block">
                  <h3>Stack Trace</h3>
                  <pre className="code-block code-block--stack">{inspectingLog.stackTrace}</pre>
                </div>
              )}

              {inspectingLog.metadata && (
                <div className="detail-block">
                  <h3>Metadata</h3>
                  <pre className="code-block">{inspectingLog.metadata}</pre>
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </>
  );
}
