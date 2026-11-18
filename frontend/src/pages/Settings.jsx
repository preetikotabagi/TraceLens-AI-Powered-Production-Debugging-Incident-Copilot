import { useState } from 'react';
import PageHeader from '../components/PageHeader.jsx';
import { NetworkError, runDemoScenario, seedRealisticData } from '../api/client.js';
import { API_BASE_URL } from '../config.js';
import { useBackendStatusContext } from '../context/backendStatusContext.js';

const SCENARIOS = [
  {
    id: 'DATABASE_FAILURE',
    label: 'Simulate database failure',
    description: 'The backend records an error log and raises a database failure (503).',
  },
  {
    id: 'INVALID_REQUEST',
    label: 'Simulate invalid request',
    description: 'The backend rejects a deliberately bad request with 400 and logs a warning.',
  },
  {
    id: 'SLOW_REQUEST',
    label: 'Simulate slow request',
    description: 'The backend waits about 3 seconds before answering 200 and logs a latency warning.',
    options: { delayMs: 3000 },
  },
];

function ConnectionPanel() {
  const { state, health } = useBackendStatusContext();
  const stateText = {
    loading: 'Checking…',
    up: 'Connected',
    down: 'Connected, database unavailable',
    unreachable: 'Not reachable',
  }[state];

  return (
    <section className="card panel" aria-labelledby="connection-heading">
      <h2 id="connection-heading" className="panel__title">
        Backend connection
      </h2>
      <dl className="detail-list">
        <div className="detail-list__row">
          <dt>API base URL</dt>
          <dd>
            <code>{API_BASE_URL}</code>
          </dd>
        </div>
        <div className="detail-list__row">
          <dt>Status</dt>
          <dd>{stateText}</dd>
        </div>
        <div className="detail-list__row">
          <dt>Backend version</dt>
          <dd>{health?.application?.version ?? '—'}</dd>
        </div>
      </dl>
      <p className="hint">
        The API address is set at build time with the <code>VITE_API_BASE_URL</code> environment variable.
      </p>
    </section>
  );
}

function SeedDataPanel({ disabled, seeding, onSeed, result }) {
  return (
    <section className="card panel" aria-labelledby="seed-heading">
      <h2 id="seed-heading" className="panel__title">
        Realistic Dataset Generator (1,000+ Logs)
      </h2>
      <p className="hint">
        Seeds at least 1,000 realistic logs across 5 services with real failure cascades:
        Stripe 504 Gateway Timeout, PostgreSQL Deadlock during checkout, and JWT Token Expiration Storm.
      </p>
      <div style={{ marginTop: '0.75rem' }}>
        <button
          type="button"
          className="button button--primary"
          onClick={onSeed}
          disabled={disabled}
        >
          {seeding ? 'Seeding 1,000+ Records…' : '⚡ Generate 1,000+ Production Logs'}
        </button>
      </div>
      {result && (
        <div style={{ marginTop: '0.75rem' }} className={`banner banner--${result.success ? 'success' : 'error'}`}>
          <p>{result.message}</p>
        </div>
      )}
    </section>
  );
}

function DemoPanel({ disabled, running, onRun, outcome }) {
  return (
    <section className="card panel" aria-labelledby="demo-heading">
      <h2 id="demo-heading" className="panel__title">
        Demo scenarios <span className="badge">Development only</span>
      </h2>
      <p className="hint">
        These buttons ask the backend to produce controlled errors and ingest live logs into the debugging platform.
      </p>
      <ul className="scenario-list">
        {SCENARIOS.map((scenario) => (
          <li key={scenario.id} className="scenario">
            <div>
              <p className="scenario__label">{scenario.label}</p>
              <p className="scenario__description">{scenario.description}</p>
            </div>
            <button
              type="button"
              className="button button--secondary"
              disabled={disabled}
              aria-busy={running === scenario.id}
              aria-label={`${running === scenario.id ? 'Running' : 'Run'}: ${scenario.label}`}
              onClick={() => onRun(scenario)}
            >
              {running === scenario.id ? 'Running…' : 'Run'}
            </button>
          </li>
        ))}
      </ul>
      <DemoOutcome outcome={outcome} />
    </section>
  );
}

function DemoOutcome({ outcome }) {
  if (!outcome) return <div aria-live="polite" className="outcome-slot" />;

  const { scenario, result, failure } = outcome;
  let summary;
  let tone;

  if (failure) {
    summary = failure;
    tone = 'error';
  } else if (result.status === 404) {
    summary = 'Demo endpoints are disabled on this backend (HTTP 404). Start it with demo mode enabled to use them.';
    tone = 'warning';
  } else {
    summary = `HTTP ${result.status}${result.data?.code ? ` · ${result.data.code}` : ''} after ${result.durationMs} ms`;
    tone = result.ok ? 'success' : 'neutral';
  }

  return (
    <div aria-live="polite" className="outcome-slot">
      <div className={`outcome outcome--${tone}`}>
        <p className="outcome__title">{scenario.label}</p>
        <p>{summary}</p>
        {result?.data?.message && <p className="outcome__detail">{result.data.message}</p>}
        {result?.requestId && (
          <p className="outcome__detail">
            Request id: <code>{result.requestId}</code>
          </p>
        )}
      </div>
    </div>
  );
}

export default function Settings() {
  const [running, setRunning] = useState(null);
  const [outcome, setOutcome] = useState(null);
  const [seeding, setSeeding] = useState(false);
  const [seedResult, setSeedResult] = useState(null);

  const isBusy = running !== null || seeding;

  async function run(scenario) {
    setRunning(scenario.id);
    setOutcome(null);
    try {
      const result = await runDemoScenario(scenario.id, scenario.options);
      setOutcome({ scenario, result });
    } catch (error) {
      setOutcome({
        scenario,
        failure:
          error instanceof NetworkError ? error.message : 'Something went wrong while calling the backend.',
      });
    } finally {
      setRunning(null);
    }
  }

  const handleSeed = async () => {
    try {
      setSeeding(true);
      setSeedResult(null);
      const res = await seedRealisticData();
      setSeedResult({ success: true, message: res.message });
    } catch (err) {
      setSeedResult({ success: false, message: err.message || 'Seeding failed' });
    } finally {
      setSeeding(false);
    }
  };

  return (
    <>
      <PageHeader title="Settings" description="Connection details, dataset seeding, and demo test scenarios." />
      <div className="stack">
        <ConnectionPanel />
        <SeedDataPanel disabled={isBusy} seeding={seeding} onSeed={handleSeed} result={seedResult} />
        <DemoPanel disabled={isBusy} running={running} onRun={run} outcome={outcome} />
      </div>
    </>
  );
}
