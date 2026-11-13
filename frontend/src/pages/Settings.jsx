import { useState } from 'react';
import PageHeader from '../components/PageHeader.jsx';
import { NetworkError, runDemoScenario } from '../api/client.js';
import { API_BASE_URL } from '../config.js';
import { useBackendStatusContext } from '../context/backendStatusContext.js';

const SCENARIOS = [
  {
    id: 'DATABASE_FAILURE',
    label: 'Simulate database failure',
    description: 'The backend raises a database error and answers 503.',
  },
  {
    id: 'INVALID_REQUEST',
    label: 'Simulate invalid request',
    description: 'The backend rejects a deliberately bad request with 400.',
  },
  {
    id: 'SLOW_REQUEST',
    label: 'Simulate slow request',
    description: 'The backend waits about 3 seconds before answering 200.',
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

function DemoPanel() {
  const [running, setRunning] = useState(null);
  const [outcome, setOutcome] = useState(null);

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

  return (
    <section className="card panel" aria-labelledby="demo-heading">
      <h2 id="demo-heading" className="panel__title">
        Demo scenarios <span className="badge">Development only</span>
      </h2>
      <p className="hint">
        These buttons ask the backend to produce controlled errors so later features have something to
        observe. They only work when the backend runs with demo mode enabled.
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
              disabled={running !== null}
              aria-busy={running === scenario.id}
              aria-label={`${running === scenario.id ? 'Running' : 'Run'}: ${scenario.label}`}
              onClick={() => run(scenario)}
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
  return (
    <>
      <PageHeader title="Settings" description="Connection details and development tools." />
      <div className="stack">
        <ConnectionPanel />
        <DemoPanel />
      </div>
    </>
  );
}
