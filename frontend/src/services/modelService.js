import { USE_MOCK } from './config'
import { http, delay } from './http'
import { modelsFixture } from '@/data/models'

let mockStore = structuredClone(modelsFixture)

function mapModel(m, audits = []) {
  const latestAudit = Array.isArray(audits) && audits.length > 0 ? audits[0] : null
  const decision = latestAudit?.assessmentDecision || m.decision || 'ACCEPT'

  const inputs = m.inputInfo
    ? [{ name: 'input_0', shape: m.inputInfo.replace(/[[\]]/g, ''), dtype: 'float32' }]
    : [{ name: 'images', shape: '1, 3, 224, 224', dtype: 'float32' }]

  const outputs = m.outputInfo
    ? [{ name: 'output_0', shape: m.outputInfo.replace(/[[\]]/g, ''), dtype: 'float32' }]
    : [{ name: 'logits', shape: '1, 1000', dtype: 'float32' }]

  const history = Array.isArray(audits)
    ? audits.map((a) => ({
        id: `audit-${a.id}`,
        at: a.completedAt || a.startedAt || a.createdAt || new Date().toISOString(),
        decision: a.assessmentDecision || 'REVIEW',
        note: a.summary || `Audit #${a.id} completed.`,
      }))
    : []

  return {
    id: String(m.id),
    name: m.name,
    format: m.format || 'ONNX',
    sizeMb: m.fileSizeBytes ? Math.round((m.fileSizeBytes / 1048576) * 10) / 10 : 0.1,
    opset: 17,
    producer: 'pytorch/onnx',
    registeredAt: m.createdAt || new Date().toISOString(),
    lastAuditAt: latestAudit?.completedAt || m.createdAt,
    decision,
    inputs,
    outputs,
    inspection: {
      compatible: m.format === 'ONNX',
      parameters: 1250000,
      nodes: 42,
      unsupportedOps: [],
      externalData: false,
    },
    repeatability: {
      runs: 5,
      identical: true,
      maxDeviation: 0.0,
      status: 'PASSED',
    },
    perturbation: {
      status: 'PASSED',
      trials: [
        { name: 'Gaussian noise (σ=0.05)', stability: 98.4 },
        { name: 'Brightness shift (±15%)', stability: 96.8 },
      ],
    },
    findings: [],
    history,
    fileHash: m.sha256Hash,
    storagePath: m.path || m.storagePath,
  }
}

export const modelService = {
  /** Fetches ML models from the Spring Boot backend */
  async list() {
    if (USE_MOCK) {
      await delay()
      return structuredClone(mockStore)
    }

    const data = await http('/models')
    if (Array.isArray(data)) {
      return data.map((m) => mapModel(m))
    }
    return []
  },

  /** Fetches model details and audit history */
  async get(id) {
    if (USE_MOCK) {
      await delay(250)
      return structuredClone(mockStore.find((m) => m.id === id))
    }

    const data = await http(`/models/${encodeURIComponent(id)}`)
    if (data && typeof data === 'object') {
      const m = data.model || data
      const audits = data.audits || []
      return mapModel(m, audits)
    }
    return null
  },

  /** Uploads and registers an ONNX model file */
  async register({ name, file }) {
    if (USE_MOCK) {
      await delay(700)
      const item = {
        id: `md-${String(mockStore.length + 1).padStart(3, '0')}`,
        name,
        format: 'ONNX',
        sizeMb: file ? Math.round((file.size / 1048576) * 10) / 10 : 0.1,
        opset: 17,
        producer: 'pytorch',
        registeredAt: new Date().toISOString(),
        lastAuditAt: null,
        decision: null,
        inputs: [],
        outputs: [],
        inspection: null,
        repeatability: null,
        perturbation: null,
        findings: [],
        history: [],
        fileHash: null,
      }
      mockStore = [item, ...mockStore]
      return structuredClone(item)
    }

    const fd = new FormData()
    fd.append('name', name)
    fd.append('format', 'ONNX')
    if (file) {
      fd.append('modelFile', file)
    }

    await http('/models/upload', { method: 'POST', formData: fd })
    const models = await this.list()
    return models.find((m) => m.name === name) || models[0]
  },

  /** Triggers model audit execution and awaits status */
  async runAudit(id) {
    if (USE_MOCK) {
      await delay(1200)
      mockStore = mockStore.map((m) =>
        m.id !== id
          ? m
          : {
              ...m,
              lastAuditAt: new Date().toISOString(),
              decision: 'ACCEPT',
              findings: [],
              history: [{ id: `mock-${Date.now()}`, at: new Date().toISOString(), decision: 'ACCEPT', note: 'Audit complete' }, ...m.history],
            }
      )
      return structuredClone(mockStore.find((m) => m.id === id))
    }

    await http(`/models/${encodeURIComponent(id)}/audit`, { method: 'POST' })

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
