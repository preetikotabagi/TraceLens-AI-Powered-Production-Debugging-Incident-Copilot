import EmptyState from '../components/EmptyState.jsx';
import PageHeader from '../components/PageHeader.jsx';

export default function Incidents() {
  return (
    <>
      <PageHeader title="Incidents" description="Problems detected in your production systems." />
      <section className="card panel" aria-label="Incident list">
        <EmptyState title="No incidents">
          Nothing has been detected because incident detection has not been built yet. Once it is, grouped
          errors and their investigations will be listed here.
        </EmptyState>
      </section>
    </>
  );
}
