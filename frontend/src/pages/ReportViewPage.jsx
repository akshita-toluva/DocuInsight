import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import Markdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { getReport } from '../api/reports.js'
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

  if (error) {
    return (
      <div className="narrow">
        <Link to="/files" className="back-link">← Back to files</Link>
        <div className="alert alert-error">{error}</div>
      </div>
    )
  }

  const completed = report.status === 'COMPLETED'

  return (
    <article className="report">
      <Link to="/files" className="back-link">← Back to files</Link>

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
        <Link
          to={`/files/${report.fileId}/report`}
          state={{ fileName: report.fileName }}
          className="btn-small"
        >
          Generate another report for this file
        </Link>
      </footer>
    </article>
  )
}