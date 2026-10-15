import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import Markdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { getReport, downloadReport } from '../api/reports.js'
import { getErrorMessage } from '../api/client.js'

const TYPE_LABELS = {
  EXECUTIVE: 'Executive Summary',
  TECHNICAL: 'Technical Analysis',
  CUSTOM: 'Custom Report',
}

export default function ReportViewPage() {
  const { reportId } = useParams()
  const [report, setReport] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [downloading, setDownloading] = useState(null)
  const [downloadError, setDownloadError] = useState('')

  // Load the report; reload when the URL's reportId changes
  useEffect(() => {
    let ignore = false
    setLoading(true)
    setError('')
    getReport(reportId)
      .then((data) => { if (!ignore) setReport(data) })
      .catch((err) => { if (!ignore) setError(getErrorMessage(err)) })
      .finally(() => { if (!ignore) setLoading(false) })
    return () => { ignore = true }       // drop late answers for an old reportId
  }, [reportId])

  if (loading) {
    return <p className="muted">Loading report…</p>
  }

  async function handleDownload(format) {
    setDownloading(format)
    setDownloadError('')
    try {
      await downloadReport(report.reportId, format)
    } catch (err) {
      setDownloadError(getErrorMessage(err))
    } finally {
      setDownloading(null)
    }
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
        <span className="report-type">{TYPE_LABELS[report.reportType] ?? report.reportType}</span>
        <h1>{report.fileName}</h1>
        <div className="report-meta">
          <span>Generated {new Date(report.createdAt).toLocaleString()}</span>
          {report.tokenCount != null && <span>{report.tokenCount.toLocaleString()} tokens</span>}
        </div>
      </header>

      {completed ? (
        <div className="report-body">
          <Markdown remarkPlugins={[remarkGfm]}>{report.reportContent}</Markdown>
        </div>
      ) : (
        <div className="report-body">
          <div className="alert alert-error">
            Report status: {report.status}. {report.errorMessage ?? ''}
          </div>
        </div>
      )}

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
           <Link
             to={`/files/${report.fileId}/report`}
             state={{ fileName: report.fileName }}
             className="btn-small"
            >
            Generate another report for this file
           </Link>
       </footer>
       {downloadError && <div className="alert alert-error">{downloadError}</div>}
    </article>
  )
}