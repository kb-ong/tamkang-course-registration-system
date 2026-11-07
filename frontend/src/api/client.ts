export class ApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    Object.setPrototypeOf(this, ApiError.prototype);
  }
}

const CSRF_COOKIE = 'XSRF-TOKEN';
const CSRF_HEADER = 'X-XSRF-TOKEN';

/** Returns the decoded value of the XSRF-TOKEN cookie, or null if it is not set. */
export function getCsrfToken(): string | null {
  if (typeof document === 'undefined' || !document.cookie) {
    return null;
  }
  for (const part of document.cookie.split(';')) {
    const trimmed = part.trim();
    const eq = trimmed.indexOf('=');
    if (eq < 0) continue;
    if (trimmed.substring(0, eq) === CSRF_COOKIE) {
      const raw = trimmed.substring(eq + 1);
      if (!raw) return null;
      try {
        return decodeURIComponent(raw);
      } catch {
        return raw;
      }
    }
  }
  return null;
}

export async function apiFetch<T>(
  path: string,
  options: { method?: string; body?: unknown } = {},
): Promise<T> {
  const method = (options.method ?? 'GET').toUpperCase();
  const headers: Record<string, string> = { Accept: 'application/json' };
  const init: RequestInit = { method, credentials: 'same-origin', headers };

  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json';
    init.body = JSON.stringify(options.body);
  }

  if (method !== 'GET' && method !== 'HEAD') {
    let token = getCsrfToken();
    if (token === null) {
      await fetch('/api/auth/csrf', { method: 'GET', credentials: 'same-origin' });
      token = getCsrfToken();
    }
    if (token !== null) {
      headers[CSRF_HEADER] = token;
    }
  }

  const response = await fetch(path, init);

  if (response.ok) {
    if (response.status === 204 || response.headers?.get?.('Content-Length') === '0') {
      return undefined as T;
    }
    try {
      return (await response.json()) as T;
    } catch (err) {
      if (err instanceof SyntaxError) {
        // empty (or non-JSON) success body
        return undefined as T;
      }
      throw err;
    }
  }

  let message = `Request failed with status ${response.status}`;
  try {
    const data: unknown = await response.json();
    if (
      data !== null &&
      typeof data === 'object' &&
      typeof (data as { message?: unknown }).message === 'string' &&
      (data as { message: string }).message
    ) {
      message = (data as { message: string }).message;
    }
  } catch {
    // body is not JSON: keep the generic message
  }
  throw new ApiError(response.status, message);
}
