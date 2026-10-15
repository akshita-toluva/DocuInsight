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

// GET /api/reports/{reportId}/export?format=pdf|docx -> file bytes
export async function downloadReport(reportId, format) {
  try {
    const response = await api.get(`/api/reports/${reportId}/export`, {
      params: { format },
      responseType: 'blob', // binary file, not JSON
    })
    const fileName =
      fileNameFrom(response.headers['content-disposition']) ?? `report-${reportId}.${format}`
    saveBlob(response.data, fileName)
  } catch (err) {
    // With responseType 'blob', error bodies are Blobs too -> turn them back into { error }
    if (err.response?.data instanceof Blob) {
      const text = await err.response.data.text()
      try {
        err.response.data = JSON.parse(text)
      } catch {
        err.response.data = { error: text }
      }
    }
    throw err
  }
}

function fileNameFrom(header) {
  const match = header?.match(/filename="?([^";]+)"?/)
  return match ? match[1] : null
}

function saveBlob(blob, fileName) {
  const url = URL.createObjectURL(blob)        // temporary in-memory link to the file
  const link = document.createElement('a')
  link.href = url
  link.download = fileName                     // "save as" instead of opening
  document.body.appendChild(link)
  link.click()
  link.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000) // free the memory
}