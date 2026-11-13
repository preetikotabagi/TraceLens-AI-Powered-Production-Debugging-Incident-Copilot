import { createContext, useContext } from 'react';

export const BackendStatusContext = createContext(null);

/** Shared backend connection state (one poller for the whole app). */
export function useBackendStatusContext() {
  const value = useContext(BackendStatusContext);
  if (!value) {
    throw new Error('useBackendStatusContext must be used inside <BackendStatusProvider>.');
  }
  return value;
}
