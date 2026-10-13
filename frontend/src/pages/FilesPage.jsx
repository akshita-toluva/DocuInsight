import { useEffect, useState } from 'react'
import { getMyFiles, extractText } from '../api/files.js'
import { getErrorMessage } from '../api/client.js'
import UploadForm from '../components/UploadForm.jsx'

function formatSize(bytes) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

// "report.final.pdf" -> "PDF"
function fileExtension(name) {
  return name.split('.').pop().toUpperCase()
}

export default function FilesPage()
{
  const [ files, setFiles] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [extractingId, setExtractingId] = useState(null)

  async function loadFiles() {
      setLoading(true)
      setError('')
      try {
        setFiles(await getMyFiles())
      } catch (err) {
        setError(getErrorMessage(err))
      } finally {
        setLoading(false)
      }
    }

  async function handleExtract(fileId) {
    setExtractingId(fileId)
    setError('')
    try {
      await extractText(fileId)
      await loadFiles()          // refresh: badge turns to "Extracted"
    } catch (err) {
      setError(getErrorMessage(err))
    } finally {
      setExtractingId(null)
    }
  }

    // Load once when the page opens
    useEffect(() => {
      loadFiles()
    }, [])


  return (
    <div>
      <h1>My Files</h1>
      <UploadForm onUploaded={loadFiles} />
      {loading && files.length === 0 && <p className="muted">Loading files…</p>}

            {error && <div className="alert alert-error">{error}</div>}

            {!loading && !error && files.length === 0 && (
              <p className="muted">No files yet. Upload your first document.</p>
            )}

            {files.length > 0 && (
              <table className="file-table">
                <thead>
                  <tr>
                    <th>Name</th>
                    <th>Type</th>
                    <th>Size</th>
                    <th>Uploaded</th>
                    <th>Text</th>
                  </tr>
                </thead>
                <tbody>
                  {files.map((file) => (
                    <tr key={file.id}>
                      <td>{file.fileName}</td>
                      <td>{fileExtension(file.fileName)}</td>
                      <td>{formatSize(file.fileSize)}</td>
                      <td>{new Date(file.uploadedAt).toLocaleString()}</td>
                      <td>
                        {file.extracted ? (
                          <span className="badge badge-ok">Extracted</span>
                        ) : (
                          <button
                            className="btn-small"
                            onClick={() => handleExtract(file.id)}
                            disabled={extractingId !== null}
                          >
                            {extractingId === file.id ? 'Extracting…' : 'Extract text'}
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
    </div>
  )
}