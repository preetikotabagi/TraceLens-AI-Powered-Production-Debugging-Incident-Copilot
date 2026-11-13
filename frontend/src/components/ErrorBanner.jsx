/** Inline error message. Announced immediately to assistive technology. */
export default function ErrorBanner({ title, children, onRetry }) {
  return (
    <div className="banner banner--error" role="alert">
      <div className="banner__body">
        <p className="banner__title">{title}</p>
        {children && <p className="banner__text">{children}</p>}
      </div>
      {onRetry && (
        <button type="button" className="button button--secondary" onClick={onRetry}>
          Retry now
        </button>
      )}
    </div>
  );
}
