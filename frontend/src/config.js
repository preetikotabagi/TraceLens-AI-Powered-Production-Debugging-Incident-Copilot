// Runtime configuration. Vite replaces import.meta.env.VITE_* at build time.
const rawBaseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

/** Base URL of the TraceLens backend, without a trailing slash. */
export const API_BASE_URL = rawBaseUrl.replace(/\/+$/, '');

/** How often the dashboard re-checks backend health. */
export const STATUS_POLL_INTERVAL_MS = 15000;

/** Default timeout for API calls. */
export const REQUEST_TIMEOUT_MS = 8000;
