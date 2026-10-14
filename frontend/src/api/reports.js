import api from './client.js'

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