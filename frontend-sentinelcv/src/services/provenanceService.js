import { USE_MOCK } from './config'
import { http, delay } from './http'
import { provenanceFixture } from '@/data/provenance'

export const provenanceService = {
  /** Proposed: GET /api/provenance/records  → { chain, records } */
  async list() {
    if (USE_MOCK) { await delay(); return structuredClone(provenanceFixture) }
    return http('/api/provenance/records')
  },

  /**
   * Proposed: POST /api/provenance/verify
   * Mock mode returns the fixture unchanged; the frontend NEVER performs cryptographic verification itself.
   */
  async verifyChain() {
    if (USE_MOCK) { await delay(900); return structuredClone(provenanceFixture) }
    return http('/api/provenance/verify', { method: 'POST' })
  },
}
