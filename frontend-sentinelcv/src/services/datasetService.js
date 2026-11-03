import { USE_MOCK } from './config'
import { http, delay } from './http'
import { datasetsFixture } from '@/data/datasets'

// In-memory mock state (resets on reload). Never persisted, never sent anywhere.
let mockStore = structuredClone(datasetsFixture)

export const datasetService = {
  /** Proposed: GET /api/datasets */
  async list() {
    if (USE_MOCK) { await delay(); return structuredClone(mockStore) }
    return http('/api/datasets')
  },

  /** Proposed: GET /api/datasets/{id} */
  async get(id) {
    if (USE_MOCK) { await delay(250); return structuredClone(mockStore.find((d) => d.id === id)) }
    return http(`/api/datasets/${encodeURIComponent(id)}`)
  },

  /** Proposed: POST /api/datasets (multipart: name, format, file) */
  async register({ name, format, file }) {
    if (USE_MOCK) {
      await delay(700)
      const item = {
        id: `ds-${String(mockStore.length + 1).padStart(3, '0')}`, name, format,
        images: null, annotations: null, classes: null,
        registeredAt: new Date().toISOString(), lastAuditAt: null,
        integrity: 'PENDING', decision: null, duplicates: null, annotationStats: null,
        checks: [], findings: [], history: [], manifestHash: null, mock: true,
      }
      mockStore = [item, ...mockStore]
      return structuredClone(item)
    }
    const fd = new FormData()
    fd.append('name', name); fd.append('format', format)
    if (file) fd.append('file', file)
    return http('/api/datasets', { method: 'POST', formData: fd })
  },

  /**
   * Proposed: POST /api/datasets/{id}/audit
   * Mock mode only marks the dataset as having a SIMULATED audit; it performs no real analysis.
   */
  async runAudit(id) {
    if (USE_MOCK) {
      await delay(1200)
      mockStore = mockStore.map((d) => d.id !== id ? d : {
        ...d, lastAuditAt: new Date().toISOString(), integrity: 'WARNING', decision: 'REVIEW',
        checks: [{ name: 'Simulated audit (mock mode)', status: 'WARNING', detail: 'No real analysis was performed.' }],
        findings: [{ severity: 'INFO', title: 'Mock-mode placeholder', detail: 'Connect the backend to produce real findings.' }],
        history: [{ id: `mock-${Date.now()}`, at: new Date().toISOString(), decision: 'REVIEW', note: 'Simulated (mock mode)' }, ...d.history],
      })
      return structuredClone(mockStore.find((d) => d.id === id))
    }
    return http(`/api/datasets/${encodeURIComponent(id)}/audit`, { method: 'POST' })
  },
}
