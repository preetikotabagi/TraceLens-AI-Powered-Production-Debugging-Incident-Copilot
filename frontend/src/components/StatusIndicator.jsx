const LABELS = {
  loading: 'Checking backend…',
  up: 'Backend connected',
  down: 'Database unavailable',
  unreachable: 'Backend unreachable',
};

/**
 * Compact connection indicator. The state is conveyed by text as well as colour,
 * and announced politely to screen readers when it changes.
 */
export default function StatusIndicator({ state }) {
  const label = LABELS[state] ?? LABELS.loading;
  return (
    <div className={`status-indicator status-indicator--${state}`} role="status" aria-live="polite">
      <span className="status-indicator__dot" aria-hidden="true" />
      <span>{label}</span>
    </div>
  );
}
