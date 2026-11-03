import { USE_MOCK } from './config'
import { http, delay } from './http'
import { driftFixture } from '@/data/drift'

export const driftService = {
  /** Proposed: GET /api/drift/comparisons */
  async list() {
    if (USE_MOCK) { await delay(); return structuredClone(driftFixture.comparisons) }
    return http('/api/drift/comparisons')
  },
}
