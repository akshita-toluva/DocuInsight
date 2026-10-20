import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { getMyMultiReports } from '../api/multiReports.js'
import { getErrorMessage } from '../api/client.js'
import { typeLabel, statusClass } from '../constants.js'

export default function ComparisonsList() {
  const [items, setItems] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    getMyMultiReports()
      // copy before sorting: sort() changes the array it is called on
      .then((data) => setItems([...data].sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))))
      .catch((err) => setError(getErrorMessage(err)))
      .finally(() => setLoading(false))
  }, [])

  return (
    <section className="comparisons">
      <h2>Comparisons</h2>

      {error && <div className="alert alert-error">{error}</div>}

      {loading ? (
        <p className="muted">Loading comparisons…</p>
      ) : items.length === 0 ? (
        <p className="muted">
          No comparisons yet. <Link to="/compare">Compare files</Link>
        </p>
      ) : (
        <table className="file-table">
          <thead>
            <tr>
              <th>Files</th>
              <th>Type</th>
              <th>Status</th>
              <th>Created</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {items.map((item) => (
              <tr key={item.id}>
                <td>{item.fileNames.join(', ')}</td>
                <td>{typeLabel(item.reportType)}</td>
                <td>
                  <span className={`badge ${statusClass(item.status)}`}>
                    {item.status.toLowerCase()}
                  </span>
                </td>
                <td>{new Date(item.createdAt).toLocaleString()}</td>
                <td>
                  <div className="row-actions">
                    <Link to={`/multi-reports/${item.id}`} className="btn-small">View</Link>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  )
}