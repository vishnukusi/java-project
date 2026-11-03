import { USE_MOCK } from './config'
import { http, delay } from './http'
import { overviewFixture } from '@/data/overview'

export const overviewService = {
  /** Proposed: GET /api/overview */
  async get() {
    if (USE_MOCK) { await delay(); return structuredClone(overviewFixture) }
    return http('/api/overview')
  },
}
