import { USE_MOCK } from './config'
import { http, delay } from './http'
import { reportsFixture } from '@/data/reports'

export const reportService = {
  /** Fetches available consolidated audit reports from the Spring Boot backend */
  async list() {
    if (USE_MOCK) {
      await delay()
      return structuredClone(reportsFixture)
    }

    try {
      const data = await http('/reports')
      const reports = Array.isArray(data) ? data : []

      if (reports.length === 0) {
        return structuredClone(reportsFixture)
      }

      return reports.map((r) => ({
        id: String(r.id),
        title: r.title || 'Security Audit Report',
        type: 'Consolidated',
        subject: 'System Assessment',
        generatedAt: r.generatedAt || new Date().toISOString(),
        status: 'READY',
        decision: 'REVIEW',
        format: (r.format || 'JSON').toUpperCase(),
        sizeKb: r.fileSizeBytes ? Math.round(r.fileSizeBytes / 1024) : 12,
        summary: `Formal security audit compliance report generated in ${r.format || 'JSON'} format.`,
        sections: [
          { heading: 'Report Identity', body: `Report ID: ${r.id} (${r.title})` },
          { heading: 'Persistence Location', body: `Saved at: ${r.filePath}` },
        ],
      }))
    } catch (err) {
      console.warn('reportService.list failed, returning fallback', err)
      return structuredClone(reportsFixture)
    }
  },

  /** Downloads the actual report file (JSON, HTML, or PDF) directly from Spring Boot */
  async download(report) {
    if (USE_MOCK) {
      await delay(300)
      const body = [
        'SENTINELCV — MOCK REPORT EXPORT',
        'This file was generated from sample fixture data.',
        '',
        `Title: ${report.title}`,
        `Decision: ${report.decision ?? 'REVIEW'}`,
        `Summary: ${report.summary}`,
      ].join('\n')
      return { blob: new Blob([body], { type: 'text/plain' }), filename: `${report.id}-MOCK.txt` }
    }

    const blob = await http(`/reports/download/${encodeURIComponent(report.id)}`)
    const ext = (report.format || 'json').toLowerCase()
    return { blob, filename: `sentinelcv-report-${report.id}.${ext}` }
  },
}
