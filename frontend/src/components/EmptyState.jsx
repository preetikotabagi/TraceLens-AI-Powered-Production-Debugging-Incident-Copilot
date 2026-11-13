import { InboxIcon } from './icons.jsx';

/** A friendly, honest placeholder for a section that has no data yet. */
export default function EmptyState({ title, children }) {
  return (
    <div className="empty-state">
      <div className="empty-state__icon">
        <InboxIcon />
      </div>
      <h3 className="empty-state__title">{title}</h3>
      <p className="empty-state__text">{children}</p>
    </div>
  );
}
