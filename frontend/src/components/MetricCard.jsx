/**
 * One headline metric. When there is no data the value is an em dash and the hint says why,
 * so nothing looks like a real production number.
 */
export default function MetricCard({ label, value, hint, loading = false }) {
  const hasValue = value !== undefined && value !== null;
  return (
    <article className="card metric-card" aria-busy={loading}>
      <h3 className="metric-card__label">{label}</h3>
      {loading ? (
        <div className="skeleton skeleton--value" aria-hidden="true" />
      ) : (
        <p className="metric-card__value" aria-label={hasValue ? undefined : 'No data'}>
          {hasValue ? value : '—'}
        </p>
      )}
      {hint && <p className="metric-card__hint">{hint}</p>}
    </article>
  );
}
