import api from './client.js'
import { downloadFile } from './download.js'

// POST /api/multi-reports/generate  body: { fileIds: [..2+], reportType, customPrompt? }
export async function generateMultiReport({ fileIds, reportType, customPrompt }) {
  const body = { fileIds, reportType }
  if (reportType === 'CUSTOM') {
    body.customPrompt = customPrompt
  }
  const { data } = await api.post('/api/multi-reports/generate', body)
  return data
}

// GET /api/multi-reports/my-reports -> { reports: [...], count }
export async function getMyMultiReports() {
  const { data } = await api.get('/api/multi-reports/my-reports')
  return data.reports
}

// GET /api/multi-reports/{id}
export async function getMultiReport(id) {
  const { data } = await api.get(`/api/multi-reports/${id}`)
  return data
}

// GET /api/multi-reports/{id}/export?format=pdf|docx
export function downloadMultiReport(id, format) {
  return downloadFile(`/api/multi-reports/${id}/export`, { format }, `multi-report-${id}.${format}`)
}