import { USE_MOCK } from './config'
import { http, delay } from './http'
import { modelsFixture } from '@/data/models'

let mockStore = structuredClone(modelsFixture)

export const modelService = {
  /** Proposed: GET /api/models */
  async list() {
    if (USE_MOCK) { await delay(); return structuredClone(mockStore) }
    return http('/api/models')
  },

  /** Proposed: GET /api/models/{id} */
  async get(id) {
    if (USE_MOCK) { await delay(250); return structuredClone(mockStore.find((m) => m.id === id)) }
    return http(`/api/models/${encodeURIComponent(id)}`)
  },

  /** Proposed: POST /api/models (multipart: name, file) */
  async register({ name, file }) {
    if (USE_MOCK) {
      await delay(700)
      const item = {
        id: `md-${String(mockStore.length + 1).padStart(3, '0')}`, name, format: 'ONNX',
        sizeMb: file ? Math.round((file.size / 1048576) * 10) / 10 : null, opset: null, producer: null,
        registeredAt: new Date().toISOString(), lastAuditAt: null, decision: null,
        inputs: [], outputs: [], inspection: null, repeatability: null, perturbation: null,
        findings: [], history: [], fileHash: null, mock: true,
      }
      mockStore = [item, ...mockStore]
      return structuredClone(item)
    }
    const fd = new FormData()
    fd.append('name', name)
    if (file) fd.append('file', file)
    return http('/api/models', { method: 'POST', formData: fd })
  },

  /** Proposed: POST /api/models/{id}/audit. Mock mode performs no real analysis. */
  async runAudit(id) {
    if (USE_MOCK) {
      await delay(1200)
      mockStore = mockStore.map((m) => m.id !== id ? m : {
        ...m, lastAuditAt: new Date().toISOString(), decision: 'REVIEW',
        findings: [{ severity: 'INFO', title: 'Mock-mode placeholder', detail: 'No real inspection was performed.' }],
        history: [{ id: `mock-${Date.now()}`, at: new Date().toISOString(), decision: 'REVIEW', note: 'Simulated (mock mode)' }, ...m.history],
      })
      return structuredClone(mockStore.find((m) => m.id === id))
    }
    return http(`/api/models/${encodeURIComponent(id)}/audit`, { method: 'POST' })
  },
}
