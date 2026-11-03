import { API_BASE_URL } from './config'

export class ApiError extends Error {
  constructor(message, status) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

/**
 * Fetch wrapper for SentinelCV Spring Boot backend.
 * Handles both direct JSON API endpoints and embedded JSON state from Thymeleaf views.
 */
export async function http(path, { method = 'GET', body, formData, signal } = {}) {
  const headers = {}
  let payload
  if (formData) {
    payload = formData
  } else if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
    payload = JSON.stringify(body)
  }

  let res
  try {
    res = await fetch(`${API_BASE_URL}${path}`, { method, headers, body: payload, signal })
  } catch (err) {
    throw new ApiError(`Cannot reach backend at ${API_BASE_URL || window.location.origin}. Is it running?`, 0)
  }

  if (!res.ok) {
    let detail = ''
    try {
      const text = await res.text()
      // Try to parse error message if HTML or JSON
      const match = text.match(/<p class="text-secondary[^>]*>([\s\S]*?)<\/p>/i)
      if (match) {
        detail = match[1].trim()
      } else {
        detail = text.slice(0, 200)
      }
    } catch { /* ignore */ }
    throw new ApiError(`Request failed (${res.status}) ${detail}`.trim(), res.status)
  }

  const type = res.headers.get('content-type') || ''
  if (type.includes('application/json')) {
    return res.json()
  }

  if (type.includes('text/html')) {
    const text = await res.text()
    // Check if the HTML template embedded a <script id="__DATA__" type="application/json">
    const dataMatch = text.match(/<script[^>]*id="__DATA__"[^>]*>([\s\S]*?)<\/script>/i)
    if (dataMatch) {
      try {
        return JSON.parse(dataMatch[1].trim())
      } catch (e) {
        console.warn('Failed to parse embedded JSON from __DATA__ script tag', e)
      }
    }

    // Check for overview initial data
    const overviewMatch = text.match(/<script[^>]*id="__INITIAL_DASHBOARD_DATA__"[^>]*>([\s\S]*?)<\/script>/i)
    if (overviewMatch) {
      try {
        return JSON.parse(overviewMatch[1].trim())
      } catch (e) {
        console.warn('Failed to parse embedded JSON from __INITIAL_DASHBOARD_DATA__ script tag', e)
      }
    }

    return text
  }

  return res.blob()
}

/** Simulated latency for mock mode if enabled. */
export const delay = (ms = 450) => new Promise((r) => setTimeout(r, ms))
