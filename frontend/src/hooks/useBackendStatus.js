import { useCallback, useEffect, useState } from 'react';
import { getHealth } from '../api/client.js';
import { STATUS_POLL_INTERVAL_MS } from '../config.js';

/**
 * Connection state of the dashboard to the backend:
 *  - 'loading'      first check has not finished yet
 *  - 'up'           backend reachable and database healthy
 *  - 'down'         backend reachable but reports an unhealthy dependency (e.g. database)
 *  - 'unreachable'  no response from the backend at all
 */
export function useBackendStatus(intervalMs = STATUS_POLL_INTERVAL_MS) {
  const [status, setStatus] = useState({
    state: 'loading',
    health: null,
    error: null,
    checkedAt: null,
  });
  const [attempt, setAttempt] = useState(0);

  const refresh = useCallback(() => setAttempt((n) => n + 1), []);

  useEffect(() => {
    const controller = new AbortController();

    async function check() {
      try {
        const health = await getHealth({ signal: controller.signal });
        if (controller.signal.aborted) return;
        setStatus({
          state: health.status === 'UP' ? 'up' : 'down',
          health,
          error: null,
          checkedAt: new Date(),
        });
      } catch (error) {
        if (controller.signal.aborted) return;
        setStatus({ state: 'unreachable', health: null, error, checkedAt: new Date() });
      }
    }

    check();
    const timer = setInterval(check, intervalMs);
    return () => {
      controller.abort();
      clearInterval(timer);
    };
  }, [intervalMs, attempt]);

  return { ...status, refresh };
}
