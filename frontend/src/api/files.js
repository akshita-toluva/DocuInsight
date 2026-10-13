import api from './client.js'

// GET /api/files/my-files -> [{ id, fileName, fileType, fileSize, uploadedAt, extracted }]
export async function getMyFiles() {
  const { data } = await api.get('/api/files/my-files')
  return data
}

// POST /api/files/upload (multipart) -> { fileId, fileName, fileType, fileSize, message }
export async function uploadFile(file) {
  const formData = new FormData()
  formData.append('file', file)          // must match @RequestParam("file")
  const { data } = await api.post('/api/files/upload', formData)
  return data
}

// POST /api/files/{fileId}/extract -> { fileId, fileName, extractedText, characterCount, message }
export async function extractText(fileId) {
  const { data } = await api.post(`/api/files/${fileId}/extract`)
  return data
}