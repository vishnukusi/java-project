import { USE_MOCK } from './config'
import { http, delay } from './http'
import { reportsFixture } from '@/data/reports'

export const reportService = {
  /** Proposed: GET /api/reports */
  async list() {
    if (USE_MOCK) { await delay(); return structuredClone(reportsFixture) }
    return http('/api/reports')
  },

  /**
   * Proposed: GET /api/reports/{id}/download → binary.
   * Mock mode downloads a small text file clearly marked as a mock export.
   */
  async download(report) {
    if (USE_MOCK) {
      await delay(300)
      const body = [
        'SENTINELCV — MOCK REPORT EXPORT',
        'This file was generated from sample fixture data. It is NOT a real audit result.',
        '', `Title: ${report.title}`, `Decision: ${report.decision ?? 'n/a'}`, `Summary: ${report.summary}`,
      ].join('\n')
      return { blob: new Blob([body], { type: 'text/plain' }), filename: `${report.id}-MOCK.txt` }
    }
    const blob = await http(`/api/reports/${encodeURIComponent(report.id)}/download`)
    return { blob, filename: `${report.id}.${(report.format || 'pdf').toLowerCase()}` }
  },
}
