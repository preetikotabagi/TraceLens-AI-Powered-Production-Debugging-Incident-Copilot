import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import EmptyState from '../components/EmptyState.jsx';
import ErrorBanner from '../components/ErrorBanner.jsx';
import PageHeader from '../components/PageHeader.jsx';
import { getIncidentDetail, getInvestigations, listIncidents, runInvestigation } from '../api/client.js';

export default function Investigation() {
  const [searchParams, setSearchParams] = useSearchParams();
  const urlIncidentId = searchParams.get('incidentId');

  const [incidents, setIncidents] = useState([]);
  const [selectedIncidentId, setSelectedIncidentId] = useState(urlIncidentId ? Number(urlIncidentId) : null);
  const [incidentDetail, setIncidentDetail] = useState(null);

  const [investigations, setInvestigations] = useState([]);
  const [activeInvestigation, setActiveInvestigation] = useState(null);

  const [notes, setNotes] = useState('');
  const [loading, setLoading] = useState(false);
  const [investigating, setInvestigating] = useState(false);
  const [error, setError] = useState(null);

  // 1. Fetch incidents list for dropdown selector
  useEffect(() => {
    async function loadIncidents() {
      try {
        const data = await listIncidents({ limit: 100 });
        const items = data.items || [];
        setIncidents(items);
        if (!selectedIncidentId && items.length > 0) {
          setSelectedIncidentId(items[0].id);
          setSearchParams({ incidentId: String(items[0].id) });
        }
      } catch (err) {
        setError(err.message || 'Failed to load incidents list');
      }
    }
    loadIncidents();
  }, []);

  // 2. Fetch selected incident details and previous investigations
  useEffect(() => {
    if (!selectedIncidentId) return;

    async function loadDetails() {
      try {
        setLoading(true);
        setError(null);
        const [detail, invList] = await Promise.all([
          getIncidentDetail(selectedIncidentId),
          getInvestigations(selectedIncidentId),
        ]);
        setIncidentDetail(detail);
        setInvestigations(invList || []);
        setActiveInvestigation(invList && invList.length > 0 ? invList[0] : null);
      } catch (err) {
        setError(err.message || 'Failed to load incident investigation details');
      } finally {
        setLoading(false);
      }
    }
    loadDetails();
  }, [selectedIncidentId]);

  const handleSelectIncident = (id) => {
    setSelectedIncidentId(id);
    setSearchParams({ incidentId: String(id) });
  };

  const handleRunInvestigation = async () => {
    if (!selectedIncidentId) return;

    try {
      setInvestigating(true);
      setError(null);
      const result = await runInvestigation(selectedIncidentId, { notes: notes.trim() || undefined });
      setInvestigations((prev) => [result, ...prev]);
      setActiveInvestigation(result);
      setNotes('');
    } catch (err) {
      setError(err.message || 'AI investigation execution failed');
    } finally {
      setInvestigating(false);
    }
  };

  return (
    <>
      <PageHeader
        title="AI-Assisted Incident Copilot"
        description="LLM-powered root-cause analysis distinguishing observed log evidence from inferred hypotheses."
      />

      {error && (
        <ErrorBanner title="Investigation Notice" onRetry={handleRunInvestigation}>
          {error}
        </ErrorBanner>
      )}

      {/* Incident Selector & Copilot Trigger */}
      <section className="card panel copilot-control-panel">
        <div className="copilot-control-grid">
          <div className="copilot-selector">
            <label htmlFor="incident-select">Select Incident to Investigate:</label>
            <select
              id="incident-select"
              className="select"
              value={selectedIncidentId || ''}
              onChange={(e) => handleSelectIncident(Number(e.target.value))}
            >
              {incidents.map((inc) => (
                <option key={inc.id} value={inc.id}>
                  #{inc.id} — [{inc.severity}] {(inc.title || '').replace(/^\[[A-Za-z]+\]\s*/, '')} ({inc.serviceName})
                </option>
              ))}
            </select>
          </div>

          <div className="copilot-notes">
            <label htmlFor="notes-input">Operator / SRE Notes (Optional context):</label>
            <input
              id="notes-input"
              type="text"
              className="input"
              placeholder="e.g. Gateway provider reported degraded latency at 15:00 UTC"
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
            />
          </div>

          <div className="copilot-action">
            <button
              type="button"
              className="button button--primary copilot-btn"
              onClick={handleRunInvestigation}
              disabled={investigating || !selectedIncidentId}
            >
              {investigating ? 'Analyzing with Gemini Copilot…' : '✨ Run AI Root-Cause Investigation'}
            </button>
          </div>
        </div>
      </section>

      {/* Incident Context Banner */}
      {incidentDetail && (
        <section className="card panel incident-context-card">
          <div className="incident-context-card__header">
            <span className={`badge badge--severity-${incidentDetail.incident.severity.toLowerCase()}`}>
              {incidentDetail.incident.severity}
            </span>
            <span className={`badge badge--status-${incidentDetail.incident.status.toLowerCase()}`}>
              {incidentDetail.incident.status}
            </span>
            <span className="meta-item">Service: <strong>{incidentDetail.incident.serviceName}</strong></span>
            <span className="meta-item">Occurrences: <strong>{incidentDetail.incident.occurrenceCount}</strong></span>
            <span className="meta-item">Trigger: <code>{incidentDetail.incident.triggerRule}</code></span>
            <span className="meta-item">Associated Logs: <strong>{incidentDetail.associatedLogs?.length || 0}</strong></span>
          </div>
          <p className="incident-context-summary">{incidentDetail.incident.summary}</p>
        </section>
      )}

      {/* Investigation Reports History Tabs */}
      {investigations.length > 1 && (
        <div className="tab-group" role="tablist" aria-label="Investigation reports history">
          {investigations.map((inv, idx) => (
            <button
              key={inv.id}
              type="button"
              role="tab"
              aria-selected={activeInvestigation?.id === inv.id}
              className={`tab-button${activeInvestigation?.id === inv.id ? ' tab-button--active' : ''}`}
              onClick={() => setActiveInvestigation(inv)}
            >
              Report #{inv.id} ({new Date(inv.createdAt).toLocaleTimeString()})
            </button>
          ))}
        </div>
      )}

      {/* Investigation Report Display */}
      {investigating ? (
        <div className="card panel loading-state">
          <div className="ai-investigating-animation">
            <span className="ai-icon">✨</span>
            <h3>Gemini 3.5 Flash Copilot is analyzing incident logs…</h3>
            <p className="hint">
              Synthesizing log sequences, correlating error fingerprints, formulating root-cause hypotheses, and cross-checking evidence against runtime logs.
            </p>
          </div>
        </div>
      ) : activeInvestigation ? (
        <div className="stack">
          {/* Executive Summary */}
          <section className="card panel report-section" aria-labelledby="summary-heading">
            <div className="panel__header-row">
              <h2 id="summary-heading" className="panel__title">Executive Context Summary</h2>
              <span className={`badge ${activeInvestigation.model?.toLowerCase().includes('fallback') || activeInvestigation.model?.toLowerCase().includes('rule') ? 'badge--warn' : 'badge--primary'}`}>
                {activeInvestigation.model?.toLowerCase().includes('fallback') || activeInvestigation.model?.toLowerCase().includes('rule')
                  ? '🛡️ Fallback Rule Engine'
                  : `✨ Gemini AI (${activeInvestigation.model})`}
              </span>
            </div>
            <p className="report-summary-text">{activeInvestigation.summary}</p>
          </section>

          {/* Suspicious Log Sequences */}
          <section className="card panel report-section" aria-labelledby="sequence-heading">
            <h2 id="sequence-heading" className="panel__title">Suspicious Log Sequences & Related Events</h2>
            {activeInvestigation.suspiciousSequence && activeInvestigation.suspiciousSequence.length > 0 ? (
              <div className="timeline">
                {activeInvestigation.suspiciousSequence.map((seq, idx) => (
                  <div key={idx} className="timeline-item">
                    <div className="timeline-marker" />
                    <div className="timeline-content">
                      <div className="timeline-header">
                        <span className="badge badge--neutral">{seq.service}</span>
                        <strong className="timeline-event">{seq.event}</strong>
                      </div>
                      <p className="timeline-reason">
                        <em>Suspicion reason:</em> {seq.suspicionReason}
                      </p>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <p className="hint">No suspicious sequence identified.</p>
            )}
          </section>

          {/* Root-Cause Hypotheses with STRICT Distinction of Evidence */}
          <section className="card panel report-section" aria-labelledby="hypotheses-heading">
            <h2 id="hypotheses-heading" className="panel__title">
              Root-Cause Hypotheses & Observed Log Evidence
            </h2>
            <div className="stack">
              {activeInvestigation.hypotheses?.map((hyp, idx) => (
                <div key={idx} className="hypothesis-card">
                  <div className="hypothesis-card__header">
                    <span className="hypothesis-card__number">Hypothesis {idx + 1}</span>
                    <h3 className="hypothesis-card__title">{hyp.hypothesis}</h3>
                    <span className={`badge badge--confidence-${hyp.confidence.toLowerCase()}`}>
                      Confidence: {hyp.confidence}
                    </span>
                  </div>

                  {/* Strictly Separated Observed Evidence Callout */}
                  <div className="evidence-callout">
                    <div className="evidence-callout__title">
                      <strong>🔍 Observed Evidence (Documented in Logs):</strong>
                    </div>
                    {hyp.supportingEvidence && hyp.supportingEvidence.length > 0 ? (
                      <ul className="evidence-list">
                        {hyp.supportingEvidence.map((ev, evIdx) => (
                          <li key={evIdx} className="evidence-item">
                            <code>{ev}</code>
                          </li>
                        ))}
                      </ul>
                    ) : (
                      <p className="hint">Inferred from statistical service thresholds.</p>
                    )}
                  </div>

                  <div className="hypothesis-card__reasoning">
                    <strong>Inferred Engineering Reasoning:</strong>
                    <p>{hyp.reasoning}</p>
                  </div>
                </div>
              ))}
            </div>
          </section>

          {/* Debugging Recommendations */}
          <section className="card panel report-section" aria-labelledby="recommendations-heading">
            <h2 id="recommendations-heading" className="panel__title">
              Actionable Debugging & Mitigation Recommendations
            </h2>
            <div className="recommendations-grid">
              {activeInvestigation.recommendations?.map((rec, idx) => (
                <div key={idx} className="recommendation-card">
                  <div className="recommendation-card__header">
                    <span className={`badge badge--priority-${rec.priority.toLowerCase()}`}>
                      {rec.priority}
                    </span>
                  </div>
                  <h3 className="recommendation-card__action">{rec.action}</h3>
                  <p className="recommendation-card__rationale">{rec.rationale}</p>
                </div>
              ))}
            </div>
          </section>
        </div>
      ) : (
        <section className="card panel">
          <EmptyState title="No investigation reports yet">
            Click &quot;Run AI Root-Cause Investigation&quot; above to launch TraceLens Copilot on this incident.
          </EmptyState>
        </section>
      )}
    </>
  );
}
