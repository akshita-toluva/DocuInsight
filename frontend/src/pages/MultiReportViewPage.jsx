import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import Markdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { getMultiReport, downloadMultiReport } from '../api/multiReports.js'
import { getErrorMessage } from '../api/client.js'
import { typeLabel } from '../constants.js'


export default function MultiReportViewPage() {
  const { id } = useParams()
  const [report, setReport] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [downloading, setDownloading] = useState(null) // 'pdf' | 'docx' | null
  const [downloadError, setDownloadError] = useState('')

  useEffect(() => {
    let ignore = false
    setLoading(true)
    setError('')
    getMultiReport(id)
      .then((data) => { if (!ignore) setReport(data) })
      .catch((err) => { if (!ignore) setError(getErrorMessage(err)) })
      .finally(() => { if (!ignore) setLoading(false) })
    return () => { ignore = true }
  }, [id])

  async function handleDownload(format) {
    setDownloading(format)
    setDownloadError('')
    try {
      await downloadMultiReport(report.id, format)
    } catch (err) {
      setDownloadError(getErrorMessage(err))
    } finally {
      setDownloading(null)
    }
  }

  if (loading) {
    return <p className="muted">Loading comparison…</p>
  }

  if (error) {
    return (
      <div className="narrow">
        <Link to="/reports" className="back-link">← Back to reports</Link>
        <div className="alert alert-error">{error}</div>
      </div>
    )
  }

  const completed = report.status === 'COMPLETED'

  return (
    <article className="report">
      <Link to="/reports" className="back-link">← Back to reports</Link>

      <header className="report-header">
        <span className="report-type">
          Comparison · {typeLabel(report.reportType)}
        </span>
        <h1>Comparison of {report.fileNames.length} documents</h1>
        <ul className="file-chips">
          {report.fileNames.map((name, index) => (
            <li key={`${index}-${name}`}>{name}</li>
          ))}
        </ul>
        <div className="report-meta">
          <span>Generated {new Date(report.createdAt).toLocaleString()}</span>
        </div>
      </header>

      <div className="report-body">
        {completed ? (
          <Markdown remarkPlugins={[remarkGfm]}>{report.reportContent}</Markdown>
        ) : (
          <div className="alert alert-error">Report status: {report.status}.</div>
        )}
      </div>

      <footer className="report-actions">
        {completed && (
          <>
            <button
              className="btn-small"
              onClick={() => handleDownload('pdf')}
              disabled={downloading !== null}
            >
              {downloading === 'pdf' ? 'Preparing…' : 'Download PDF'}
            </button>
            <button
              className="btn-small"
              onClick={() => handleDownload('docx')}
              disabled={downloading !== null}
            >
              {downloading === 'docx' ? 'Preparing…' : 'Download Word'}
            </button>
          </>
        )}
        <Link to="/compare" className="btn-small">Compare other files</Link>
      </footer>
      {downloadError && <div className="alert alert-error">{downloadError}</div>}
    </article>
  )
}