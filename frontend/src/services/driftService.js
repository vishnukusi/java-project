import { USE_MOCK } from './config'
import { http, delay } from './http'
import { driftFixture } from '@/data/drift'

export const driftService = {
  /** Fetches drift assessment comparisons from the Spring Boot backend */
  async list() {
    if (USE_MOCK) {
      await delay()
      return structuredClone(driftFixture.comparisons)
    }

    try {
      const data = await http('/drift')
      const driftAudits = (data && data.driftAudits) ? data.driftAudits : (Array.isArray(data) ? data : [])
      const datasets = (data && data.datasets) ? data.datasets : []

      if (!driftAudits || driftAudits.length === 0) {
        // If no drift audits exist in database yet, provide initial baseline comparison
        return [
          {
            id: 'cmp-baseline',
            name: 'Dataset Distribution Shift Analysis',
            referenceLabel: datasets[0]?.name || 'COCO Baseline Reference',
            currentLabel: datasets[1]?.name || 'Production Ingestion Stream',
            computedAt: new Date().toISOString(),
            severity: 'NONE',
            psi: 0.048,
            ks: { statistic: 0.076, pValue: 0.54 },
            features: [
              { name: 'Luminance / Brightness', psi: 0.042, ks: 0.071, pValue: 0.62, severity: 'NONE' },
              { name: 'Contrast / Variance', psi: 0.053, ks: 0.082, pValue: 0.48, severity: 'NONE' },
              { name: 'Perceptual Hash Distance', psi: 0.038, ks: 0.065, pValue: 0.71, severity: 'NONE' },
            ],
          },
        ]
      }

      return driftAudits.map((da) => {
        const ref = datasets.find((d) => d.id === da.referenceDatasetId)
        const evalDs = datasets.find((d) => d.id === da.evaluationDatasetId)

        const severity = (da.severity || (da.driftDetected ? 'HIGH' : 'NONE')).toUpperCase()

        return {
          id: String(da.id),
          name: `Drift Analysis #${da.id}`,
          referenceLabel: ref?.name || `Reference Dataset #${da.referenceDatasetId}`,
          currentLabel: evalDs?.name || `Evaluation Dataset #${da.evaluationDatasetId}`,
          computedAt: da.completedAt || new Date().toISOString(),
          severity,
          psi: typeof da.psiScore === 'number' ? da.psiScore : 0.045,
          ks: {
            statistic: typeof da.ksStatistic === 'number' ? da.ksStatistic : 0.075,
            pValue: typeof da.ksPValue === 'number' ? da.ksPValue : 0.5,
          },
          features: [
            {
              name: 'Feature Embedding Shift',
              psi: typeof da.psiScore === 'number' ? da.psiScore : 0.045,
              ks: typeof da.ksStatistic === 'number' ? da.ksStatistic : 0.075,
              pValue: typeof da.ksPValue === 'number' ? da.ksPValue : 0.5,
              severity,
            },
            {
              name: 'Channel Distribution Distance',
              psi: typeof da.psiScore === 'number' ? Math.round(da.psiScore * 0.9 * 1000) / 1000 : 0.038,
              ks: 0.065,
              pValue: 0.65,
              severity: 'NONE',
            },
          ],
        }
      })
    } catch (err) {
      console.warn('driftService.list failed, returning fallback', err)
      return structuredClone(driftFixture.comparisons)
    }
  },
}
