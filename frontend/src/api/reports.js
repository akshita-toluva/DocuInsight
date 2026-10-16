import api from './client.js'
import { downloadFile } from './download.js'

// POST /api/reports/generate/{fileId}
// body: { reportType: 'EXECUTIVE' | 'TECHNICAL' | 'CUSTOM', customPrompt?, forceRegenerate }
// -> { reportId, fileId, fileName, reportType, status, reportContent, errorMessage,
//      createdAt, completedAt, tokenCount, cached }
export async function generateReport(fileId, { reportType, customPrompt, forceRegenerate = false }) {
  const body = { reportType, forceRegenerate }
  if (reportType === 'CUSTOM') {
    body.customPrompt = customPrompt   // only CUSTOM sends it (@Size min 10 rejects "")
  }
  const { data } = await api.post(`/api/reports/generate/${fileId}`, body)
  return data
}

// GET /api/reports/{reportId} -> same shape as above
export async function getReport(reportId) {
  const { data } = await api.get(`/api/reports/${reportId}`)
  return data
}

// GET /api/reports/my-reports -> { reports: [...], count }
export async function getMyReports() {
  const { data } = await api.get('/api/reports/my-reports')
  return data.reports
}

// DELETE /api/reports/{reportId} -> { message }
export async function deleteReport(reportId) {
  const { data } = await api.delete(`/api/reports/${reportId}`)
  return data
}

// GET /api/reports/{reportId}/export?format=pdf|docx -> file download
export function downloadReport(reportId, format) {
  return downloadFile(`/api/reports/${reportId}/export`, { format }, `report-${reportId}.${format}`)
}

// POST /api/reports/{reportId}/ask  body: { question }
// -> { reportId, fileName, question, answer, answeredAt }
export async function askQuestion(reportId, question) {
  const { data } = await api.post(`/api/reports/${reportId}/ask`, { question })
  return data
}