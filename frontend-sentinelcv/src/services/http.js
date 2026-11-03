import { API_BASE_URL } from './config'

export class ApiError extends Error {
  constructor(message, status) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

/**
 * Thin fetch wrapper used ONLY in backend mode.
 * All paths passed in by services are PROPOSED contracts (see API_INTEGRATION.md),
 * not confirmed endpoints of the Spring Boot backend.
 */
export async function http(path, { method = 'GET', body, formData, signal } = {}) {
  const headers = {}
  let payload
  if (formData) payload = formData
  else if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
    payload = JSON.stringify(body)
  }
  let res
  try {
    res = await fetch(`${API_BASE_URL}${path}`, { method, headers, body: payload, signal })
  } catch {
    throw new ApiError(`Cannot reach backend at ${API_BASE_URL}. Is it running?`, 0)
  }
  if (!res.ok) {
    let detail = ''
    try { detail = (await res.text()).slice(0, 200) } catch { /* ignore */ }
    throw new ApiError(`Request failed (${res.status}) ${detail}`.trim(), res.status)
  }
  const type = res.headers.get('content-type') || ''
  return type.includes('application/json') ? res.json() : res.blob()
}

/** Simulated latency for mock mode so loading states are visible. */
export const delay = (ms = 450) => new Promise((r) => setTimeout(r, ms))
