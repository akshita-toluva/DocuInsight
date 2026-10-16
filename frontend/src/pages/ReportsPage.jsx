import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { getMyReports, deleteReport } from '../api/reports.js'
import { getErrorMessage } from '../api/client.js'
import ComparisonsList from '../components/ComparisonsList.jsx'

const TYPE_LABELS = {
  EXECUTIVE: 'Executive Summary',
  TECHNICAL: 'Technical Analysis',
  CUSTOM: 'Custom',
}

const STATUS_CLASS = {
  COMPLETED: 'badge-ok',
  FAILED: 'badge-error',
  PENDING: 'badge-pending',
  PROCESSING: 'badge-pending',
}

export default function ReportsPage() {
  const [reports, setReports] = useState([])
  const [confirmId, setConfirmId] = useState(null)
  const [deletingId, setDeletingId] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  async function loadReports() {
    setLoading(true)
    setError('')
    try {
      setReports(await getMyReports())
    } catch (err) {
      setError(getErrorMessage(err))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadReports()
  }, [])

  async function handleDelete(reportId) {
    setDeletingId(reportId)
    setError('')
    try {
      await deleteReport(reportId)
      // remove the row locally: no need to reload the whole list
      setReports((prev) => prev.filter((r) => r.reportId !== reportId))
    } catch (err) {
      setError(getErrorMessage(err))
    } finally {
      setDeletingId(null)
      setConfirmId(null)
    }
  }

  return (
    <div>
      <h1>My Reports</h1>

      {error && <div className="alert alert-error">{error}</div>}

      {loading && reports.length === 0 ? (
        <p className="muted">Loading reports…</p>
      ) : reports.length === 0 ? (
        <p className="muted">
          No reports yet. Go to <Link to="/files">My Files</Link> and click Report on a file.
        </p>
      ) : (
        <table className="file-table">
          <thead>
            <tr>
              <th>File</th>
              <th>Type</th>
              <th>Status</th>
              <th>Created</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {reports.map((report) => (
              <tr key={report.reportId}>
                <td>{report.fileName}</td>
                <td>{TYPE_LABELS[report.reportType] ?? report.reportType}</td>
                <td>
                  <span className={`badge ${STATUS_CLASS[report.status] ?? 'badge-pending'}`}>
                    {report.status.toLowerCase()}
                  </span>
                </td>
                <td>{new Date(report.createdAt).toLocaleString()}</td>
                <td>
                  <div className="row-actions">
                    <Link to={`/reports/${report.reportId}`} className="btn-small">View</Link>
                    {confirmId === report.reportId ? (
                      <>
                        <button
                          className="btn-small btn-danger"
                          onClick={() => handleDelete(report.reportId)}
                          disabled={deletingId !== null}
                        >
                          {deletingId === report.reportId ? 'Deleting…' : 'Confirm'}
                        </button>
                        <button
                          className="btn-small"
                          onClick={() => setConfirmId(null)}
                          disabled={deletingId !== null}
                        >
                          Cancel
                        </button>
                      </>
                    ) : (
                      <button
                        className="btn-small"
                        onClick={() => setConfirmId(report.reportId)}
                        disabled={deletingId !== null}
                      >
                        Delete
                      </button>
                    )}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <ComparisonsList />

    </div>
  )
}