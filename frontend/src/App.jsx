import { Navigate, Route, Routes } from 'react-router-dom';
import Layout from './components/Layout.jsx';
import ErrorGroups from './pages/ErrorGroups.jsx';
import Incidents from './pages/Incidents.jsx';
import Investigation from './pages/Investigation.jsx';
import Logs from './pages/Logs.jsx';
import Overview from './pages/Overview.jsx';
import Services from './pages/Services.jsx';
import Settings from './pages/Settings.jsx';

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<Overview />} />
        <Route path="logs" element={<Logs />} />
        <Route path="incidents" element={<Incidents />} />
        <Route path="error-groups" element={<ErrorGroups />} />
        <Route path="investigation" element={<Investigation />} />
        <Route path="services" element={<Services />} />
        <Route path="settings" element={<Settings />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}
