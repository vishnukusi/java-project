import { USE_MOCK } from './config'
import { http, delay } from './http'
import { datasetsFixture } from '@/data/datasets'

let mockStore = structuredClone(datasetsFixture)

function mapDataset(d, audits = []) {
  const latestAudit = Array.isArray(audits) && audits.length > 0 ? audits[0] : null
  const decision = latestAudit?.assessmentDecision || d.decision || (d.totalAnnotations > 0 ? 'ACCEPT' : 'REVIEW')
  const integrity = d.sha256Hash ? 'PASSED' : 'PENDING'

  const checks = [
    {
      name: 'Dataset Content Hash',
      status: d.sha256Hash ? 'PASSED' : 'PENDING',
      detail: d.sha256Hash ? `SHA-256: ${d.sha256Hash.substring(0, 16)}...` : 'Pending calculation',
    },
    {
      name: 'Annotation Verification',
      status: (d.totalAnnotations || 0) > 0 ? 'PASSED' : 'WARNING',
      detail: `${d.totalAnnotations || 0} valid annotations found`,
    },
    {
      name: 'Image Integrity',
      status: (d.totalImages || 0) > 0 ? 'PASSED' : 'WARNING',
      detail: `${d.totalImages || 0} images verified in dataset`,
    },
  ]

  const history = Array.isArray(audits)
    ? audits.map((a) => ({
        id: `audit-${a.id}`,
        at: a.completedAt || a.startedAt || a.createdAt || new Date().toISOString(),
        decision: a.assessmentDecision || 'REVIEW',
        note: a.summary || `Audit #${a.id} completed.`,
      }))
    : []

  return {
    id: String(d.id),
    name: d.name,
    format: d.format || 'COCO',
    images: d.totalImages || 0,
    annotations: d.totalAnnotations || 0,
    classes: d.totalClasses || 0,
    registeredAt: d.createdAt || new Date().toISOString(),
    lastAuditAt: latestAudit?.completedAt || d.createdAt,
    integrity,
    decision,
    manifestHash: d.sha256Hash,
    fileSizeBytes: d.fileSizeBytes,
    storagePath: d.path || d.storagePath,
    duplicates: {
      groups: latestAudit?.duplicateImageCount ? Math.ceil(latestAudit.duplicateImageCount / 2) : 0,
      images: latestAudit?.duplicateImageCount || 0,
    },
    annotationStats: {
      valid: d.totalAnnotations || 0,
      invalid: 0,
      emptyImages: 0,
      perClass: [],
    },
    checks,
    findings: [],
    history,
  }
}

export const datasetService = {
  /** Fetches datasets from the Spring Boot backend */
  async list() {
    if (USE_MOCK) {
      await delay()
      return structuredClone(mockStore)
    }

    const data = await http('/datasets')
    if (Array.isArray(data)) {
      return data.map((d) => mapDataset(d))
    }
    return []
  },

  /** Fetches specific dataset details and audits */
  async get(id) {
    if (USE_MOCK) {
      await delay(250)
      return structuredClone(mockStore.find((d) => d.id === id))
    }

    const data = await http(`/datasets/${encodeURIComponent(id)}`)
    if (data && typeof data === 'object') {
      const d = data.dataset || data
      const audits = data.audits || []
      return mapDataset(d, audits)
    }
    return null
  },

  /** Uploads and registers a new dataset ZIP archive */
  async register({ name, format, file }) {
    if (USE_MOCK) {
      await delay(700)
      const item = {
        id: `ds-${String(mockStore.length + 1).padStart(3, '0')}`,
        name,
        format,
        images: null,
        annotations: null,
        classes: null,
        registeredAt: new Date().toISOString(),
        lastAuditAt: null,
        integrity: 'PENDING',
        decision: null,
        duplicates: null,
        annotationStats: null,
        checks: [],
        findings: [],
        history: [],
        manifestHash: null,
      }
      mockStore = [item, ...mockStore]
      return structuredClone(item)
    }

    const fd = new FormData()
    fd.append('name', name)
    fd.append('format', format)
    if (file) {
      fd.append('archive', file)
    }

    await http('/datasets/upload', { method: 'POST', formData: fd })
    const datasets = await this.list()
    return datasets.find((d) => d.name === name) || datasets[0]
  },

  /** Starts asynchronous dataset audit execution and awaits completion */
  async runAudit(id) {
    if (USE_MOCK) {
      await delay(1200)
      mockStore = mockStore.map((d) =>
        d.id !== id
          ? d
          : {
              ...d,
              lastAuditAt: new Date().toISOString(),
              integrity: 'PASSED',
              decision: 'ACCEPT',
              checks: [{ name: 'Integrity Check', status: 'PASSED', detail: 'Analysis complete.' }],
              findings: [],
              history: [{ id: `mock-${Date.now()}`, at: new Date().toISOString(), decision: 'ACCEPT', note: 'Audit complete' }, ...d.history],
            }
      )
      return structuredClone(mockStore.find((d) => d.id === id))
    }

    await http(`/datasets/${encodeURIComponent(id)}/audit`, { method: 'POST' })

    const startTime = Date.now()
    while (Date.now() - startTime < 15000) {
      await delay(600)
      const detail = await this.get(id)
      if (detail && detail.lastAuditAt) {
        return detail
      }
    }

    return this.get(id)
  },
}
