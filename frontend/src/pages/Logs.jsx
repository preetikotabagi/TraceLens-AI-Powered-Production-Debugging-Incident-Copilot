import EmptyState from '../components/EmptyState.jsx';
import PageHeader from '../components/PageHeader.jsx';

export default function Logs() {
  return (
    <>
      <PageHeader title="Logs" description="Search and filter log events from your services." />
      <section className="card panel" aria-label="Log events">
        <EmptyState title="No logs to show">
          No log events have been received. Log ingestion is planned for an upcoming milestone; once it
          exists, events from your services will appear here.
        </EmptyState>
      </section>
    </>
  );
}
