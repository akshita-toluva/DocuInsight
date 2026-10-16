import api from './client.js'

// GET a protected file (JWT header added by the interceptor) and save it via the browser
export async function downloadFile(path, params, fallbackName) {
  try {
    const response = await api.get(path, { params, responseType: 'blob' })
    const fileName = fileNameFrom(response.headers['content-disposition']) ?? fallbackName
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
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  link.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}