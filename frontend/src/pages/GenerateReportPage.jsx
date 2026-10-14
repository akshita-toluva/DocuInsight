import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router'
import { generateReport } from '../api/reports.js'
import { getErrorMessage } from '../api/client.js'

const REPORT_TYPES = [
  { value: 'EXECUTIVE', label: 'Executive Summary', hint: 'Business view: overview, key findings, impact, recommendations' },
  { value: 'TECHNICAL', label: 'Technical Analysis', hint: 'Engineering view: details, data, risks, dependencies' },
  { value: 'CUSTOM', label: 'Custom', hint: 'Write your own instructions for the AI' },
]

export default function GenerateReportPage() {
  const { fileId } = useParams()
  const location = useLocation()
  const navigate = useNavigate()
  const fileName = location.state?.fileName ?? `File #${fileId}`

  const [reportType, setReportType] = useState('EXECUTIVE')
  const [customPrompt, setCustomPrompt] = useState('')
  const [forceRegenerate, setForceRegenerate] = useState(false)
  const [generating, setGenerating] = useState(false)
  const [seconds, setSeconds] = useState(0)
  const [error, setError] = useState('')

  // Live "Generating… 12s" counter while waiting for the AI
  useEffect(() => {
    if (!generating) return
    setSeconds(0)
    const timer = setInterval(() => setSeconds((s) => s + 1), 1000)
    return () => clearInterval(timer)          // cleanup: stop the timer
  }, [generating])

  const isCustom = reportType === 'CUSTOM'
  const promptLength = customPrompt.trim().length
  const promptValid = !isCustom || (promptLength >= 10 && promptLength <= 2000)

  async function handleSubmit(e) {
    e.preventDefault()
    if (!promptValid) return
    setError('')
    setGenerating(true)
    try {
      const report = await generateReport(fileId, {
        reportType,
        customPrompt: customPrompt.trim(),
        forceRegenerate,
      })
      navigate(`/reports/${report.reportId}`)
    } catch (err) {
      setError(getErrorMessage(err))
    } finally {
      setGenerating(false)
    }
  }

  return (
    <div className="narrow">
      <Link to="/files" className="back-link">← Back to files</Link>
      <h1>Generate report</h1>
      <p className="muted">File: <strong>{fileName}</strong></p>

      <form className="card" onSubmit={handleSubmit}>
        <fieldset className="type-options" disabled={generating}>
          <legend>Report type</legend>
          {REPORT_TYPES.map((t) => (
            <label key={t.value} className={`type-option ${reportType === t.value ? 'selected' : ''}`}>
              <input
                type="radio"
                name="reportType"
                value={t.value}
                checked={reportType === t.value}
                onChange={(e) => setReportType(e.target.value)}
              />
              <span>
                <strong>{t.label}</strong>
                <small>{t.hint}</small>
              </span>
            </label>
          ))}
        </fieldset>

        {isCustom && (
          <div className="field">
            <label htmlFor="customPrompt">Your instructions</label>
            <textarea
              id="customPrompt"
              rows={5}
              value={customPrompt}
              onChange={(e) => setCustomPrompt(e.target.value)}
              placeholder="e.g. List every deadline and who owns it, as a table."
              disabled={generating}
            />
            <small className={promptValid ? 'muted' : 'text-error'}>
              {promptLength} / 2000 characters (minimum 10)
            </small>
          </div>
        )}

        {!isCustom && (
          <label className="checkbox">
            <input
              type="checkbox"
              checked={forceRegenerate}
              onChange={(e) => setForceRegenerate(e.target.checked)}
              disabled={generating}
            />
            Generate a fresh report (ignore the saved one)
          </label>
        )}

        {error && <div className="alert alert-error">{error}</div>}

        <button className="btn" type="submit" disabled={generating || !promptValid}>
          {generating ? `Generating… ${seconds}s` : 'Generate report'}
        </button>
        {generating && (
          <p className="muted small">AI reports usually take 5–30 seconds. Please keep this page open.</p>
        )}
      </form>
    </div>
  )
}