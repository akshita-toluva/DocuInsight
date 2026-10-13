import { useRef, useState } from 'react'
import { uploadFile } from '../api/files.js'
import { getErrorMessage } from '../api/client.js'

const MAX_SIZE = 10 * 1024 * 1024 // must match spring.servlet.multipart.max-file-size
const ACCEPT = '.pdf,.docx,.txt,.csv,.png,.jpg,.jpeg,.webp'

export default function UploadForm({ onUploaded }) {
  const [file, setFile] = useState(null)
  const [uploading, setUploading] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const inputRef = useRef(null)

  function handleFileChange(e) {
    setError('')
    setMessage('')
    const chosen = e.target.files[0] ?? null
    if (chosen && chosen.size > MAX_SIZE) {
      setError('File is larger than 10 MB.')
      setFile(null)
      e.target.value = ''
      return
    }
    setFile(chosen)
  }

  async function handleSubmit(e) {
    e.preventDefault()
    if (!file) return
    setUploading(true)
    setError('')
    setMessage('')
    try {
      const result = await uploadFile(file)
      setMessage(`${result.fileName} uploaded.`)
      setFile(null)
      inputRef.current.value = ''   // clear the file picker
      onUploaded()                  // tell the parent to reload the list
    } catch (err) {
      setError(getErrorMessage(err))
    } finally {
      setUploading(false)
    }
  }

  return (
    <form className="upload-card" onSubmit={handleSubmit}>
      <input
        ref={inputRef}
        type="file"
        accept={ACCEPT}
        onChange={handleFileChange}
        disabled={uploading}
      />
      <button className="btn btn-inline" type="submit" disabled={!file || uploading}>
        {uploading ? 'Uploading…' : 'Upload'}
      </button>
      <p className="hint">PDF, DOCX, TXT, CSV or image · max 10 MB</p>
      {error && <div className="alert alert-error">{error}</div>}
      {message && <div className="alert alert-success">{message}</div>}
    </form>
  )
}