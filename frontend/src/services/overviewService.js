import { USE_MOCK } from './config'
import { http, delay } from './http'
import { overviewFixture } from '@/data/overview'

export const overviewService = {
  /**
   * Fetches overview and system risk metrics from the backend.
   * Pulls metrics directly from the Spring Boot Dashboard and Findings controllers.
   */
  async get() {
    if (USE_MOCK) {
      await delay()
      return structuredClone(overviewFixture)
    }

    try {
      // 1. Fetch dashboard assessment & counts
      const dashboardData = await http('/dashboard')

      let assessment = {}
      let totalDatasets = 0
      let totalModels = 0
      let totalFindings = 0
      let criticalFindings = 0
      let highFindings = 0
      let recentAudits = []

      if (dashboardData && typeof dashboardData === 'object') {
        assessment = dashboardData.assessment || {}
        totalDatasets = Number(dashboardData.totalDatasets) || 0
        totalModels = Number(dashboardData.totalModels) || 0
        totalFindings = Number(dashboardData.totalFindings) || 0
        criticalFindings = Number(dashboardData.criticalFindings) || 0
        highFindings = Number(dashboardData.highFindings) || 0
        recentAudits = Array.isArray(dashboardData.recentAudits) ? dashboardData.recentAudits : []
      }

      // 2. Fetch security findings for the recent findings list
      let findings = []
      try {
        const findingsData = await http('/findings')
        if (Array.isArray(findingsData)) {
          findings = findingsData
        }
      } catch (e) {
        console.warn('Could not fetch findings for overview', e)
      }

      // Calculate accepted/review/quarantine distribution from recent audits or default state
      let accepted = 0
      let review = 0
      let quarantined = 0

      recentAudits.forEach((a) => {
        const dec = (a.assessmentDecision || '').toUpperCase()
        if (dec === 'ACCEPT') accepted++
        else if (dec === 'QUARANTINE') quarantined++
        else if (dec) review++
      })

      // If no audits yet, provide a baseline matching system assessment
      if (accepted === 0 && review === 0 && quarantined === 0) {
        const globalDecision = (assessment.overallDecision || 'REVIEW').toUpperCase()
        if (globalDecision === 'ACCEPT') accepted = 1
        else if (globalDecision === 'QUARANTINE') quarantined = 1
        else review = 1
      }

      const riskDistribution = [
        { name: 'ACCEPT', value: accepted },
        { name: 'REVIEW', value: review },
        { name: 'QUARANTINE', value: quarantined },
      ]

      const assessmentsByType = [
        { type: 'Datasets', ACCEPT: accepted, REVIEW: review, QUARANTINE: quarantined },
        { type: 'Models', ACCEPT: Math.max(0, totalModels - quarantined), REVIEW: 0, QUARANTINE: quarantined },
      ]

      const recentActivity = recentAudits.slice(0, 8).map((a) => ({
        id: `audit-${a.id}`,
        kind: a.auditType || 'Audit',
        subject: a.targetName || `Target #${a.targetId}`,
        decision: a.assessmentDecision || 'REVIEW',
        at: a.completedAt || a.startedAt || a.createdAt || new Date().toISOString(),
        summary: a.summary,
      }))

      const recentFindings = findings.slice(0, 6).map((f) => ({
        severity: (f.severity || 'INFO').toUpperCase() === 'CRITICAL' ? 'HIGH' : (f.severity || 'INFO').toUpperCase(),
        title: f.description || f.category || f.findingId || 'Security issue detected',
        subject: f.affectedAsset || f.module || 'System',
        at: f.detectedAt || new Date().toISOString(),
      }))

      return {
        totals: {
          datasets: totalDatasets,
          models: totalModels,
          accepted,
          review,
          quarantined,
        },
        riskDistribution,
        assessmentsByType,
        recentActivity,
        recentFindings,
      }
    } catch (err) {
      console.error('overviewService.get failed, fallback to baseline fixture', err)
      return structuredClone(overviewFixture)
    }
  },
}
