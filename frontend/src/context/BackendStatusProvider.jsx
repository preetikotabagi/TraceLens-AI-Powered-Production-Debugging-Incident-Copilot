import { BackendStatusContext } from './backendStatusContext.js';
import { useBackendStatus } from '../hooks/useBackendStatus.js';

export function BackendStatusProvider({ children }) {
  const status = useBackendStatus();
  return <BackendStatusContext.Provider value={status}>{children}</BackendStatusContext.Provider>;
}
