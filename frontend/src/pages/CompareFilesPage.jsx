import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { getMyFiles } from '../api/files.js'
import { generateMultiReport } from '../api/multiReports.js'
import { getErrorMessage } from '../api/client.js'

const REPORT_TYPES = [
  { value: 'EXECUTIVE', label: 'Executive Summary — common themes, differences, combined recommendations' },
  { value: 'TECHNICAL', label: 'Technical Analysis — agreements, contradictions, gaps' },
  { value: 'CUSTOM', label: 'Custom — write your own instructions' },
]

export default function CompareFilesPage() {
  const navigate = useNavigate()
  const [files, setFiles] = useState([])
  const [selected, setSelected] = useState(() => new Set()) // ids of ticked files
  const [reportType, setReportType] = useState('EXECUTIVE')
  const [customPrompt, setCustomPrompt] = useState('')
  const [loading, setLoading] = useState(true)
  const [generating, setGenerating] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    getMyFiles()
      .then(setFiles)
      .catch((err) => setError(getErrorMessage(err)))
      .finally(() => setLoading(false))
  }, [])

  function toggle(fileId) {
    setSelected((prev) => {
      const next = new Set(prev) // copy: never change the old Set
      if (next.has(fileId)) {
        next.delete(fileId)
      } else {
        next.add(fileId)
      }
      return next
    })
  }

  const isCustom = reportType === 'CUSTOM'
  const promptLength = customPrompt.trim().length
  const promptValid = !isCustom || (promptLength >= 10 && promptLength <= 2000)
  const canGenerate = selected.size >= 2 && promptValid && !generating

  async function handleSubmit(e) {
    e.preventDefault()
    if (!canGenerate) return
    setGenerating(true)
    setError('')
    try {
      const report = await generateMultiReport({
        fileIds: [...selected],
        reportType,
        customPrompt: customPrompt.trim(),
      })
      navigate(`/multi-reports/${report.id}`)
    } catch (err) {
      setError(getErrorMessage(err))
    } finally {
      setGenerating(false)
    }
  }

  if (loading) {
    return <p className="muted">Loading files…</p>
  }

  return (
    <div className="narrow">
      <h1>Compare documents</h1>
      <p className="muted">
        Pick at least 2 files. The AI reads them together and reports common themes, differences and gaps.
      </p>

      {error && <div className="alert alert-error">{error}</div>}

      {files.length < 2 ? (
        <p className="muted">
          You need at least 2 uploaded files. <Link to="/files">Upload files</Link>
        </p>
      ) : (
        <form className="card" onSubmit={handleSubmit}>
          <fieldset className="plain-fieldset" disabled={generating}>
            <span className="field-label">Files ({selected.size} selected)</span>
            <ul className="pick-list">
              {files.map((file) => (
                <li key={file.id}>
                  <label className="checkbox">
                    <input
                      type="checkbox"
                      checked={selected.has(file.id)}
                      onChange={() => toggle(file.id)}
                    />
                    {file.fileName}
                  </label>
                </li>
              ))}
            </ul>

            <label className="field-label" htmlFor="multiType">Report type</label>
            <select id="multiType" value={reportType} onChange={(e) => setReportType(e.target.value)}>
              {REPORT_TYPES.map((t) => (
                <option key={t.value} value={t.value}>{t.label}</option>
              ))}
            </select>

            {isCustom && (
              <div className="field" style={{ marginTop: 14 }}>
                <label htmlFor="multiPrompt">Your instructions</label>
                <textarea
                  id="multiPrompt"
                  rows={4}
                  value={customPrompt}
                  onChange={(e) => setCustomPrompt(e.target.value)}
                  placeholder="e.g. Compare the prices in each document as a table."
                />
                <small className={promptValid ? 'muted' : 'text-error'}>
                  {promptLength}/2000 · at least 10 characters
                </small>
              </div>
            )}
          </fieldset>

          <button className="btn" type="submit" disabled={!canGenerate}>
          {generating ? 'Comparing… this can take up to a minute' : `Compare ${selected.size} file${selected.size === 1 ? '' : 's'}`}          </button>
        </form>
      )}
    </div>
  )
}