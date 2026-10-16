import { useState } from 'react'
import Markdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { askQuestion } from '../api/reports.js'
import { getErrorMessage } from '../api/client.js'

const MAX_LENGTH = 1000

export default function AskPanel({ reportId }) {
  const [question, setQuestion] = useState('')
  const [history, setHistory] = useState([]) // [{ id, question, answer }], oldest first
  const [asking, setAsking] = useState(false)
  const [error, setError] = useState('')

  const trimmed = question.trim()
  const canAsk = trimmed.length > 0 && trimmed.length <= MAX_LENGTH && !asking

  async function handleSubmit(e) {
    e.preventDefault()
    if (!canAsk) return
    setAsking(true)
    setError('')
    try {
      const result = await askQuestion(reportId, trimmed)
      setHistory((prev) => [...prev, { id: Date.now(), question: result.question, answer: result.answer }])
      setQuestion('')
    } catch (err) {
      setError(getErrorMessage(err))
    } finally {
      setAsking(false)
    }
  }

  // Ctrl + Enter (Cmd + Enter on Mac) sends; plain Enter makes a new line
  function handleKeyDown(e) {
    if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
      handleSubmit(e)
    }
  }

  return (
    <section className="ask-panel">
      <h2>Ask about this document</h2>
      <p className="muted small">
        Answers come only from the document. Questions are not saved — they clear when you leave this page.
      </p>

      {history.length > 0 && (
        <ol className="qa-list">
          {history.map((item) => (
            <li key={item.id}>
              <p className="qa-question">{item.question}</p>
              <div className="qa-answer">
                <Markdown remarkPlugins={[remarkGfm]}>{item.answer}</Markdown>
              </div>
            </li>
          ))}
        </ol>
      )}

      <form className="ask-form" onSubmit={handleSubmit}>
        <textarea
          rows={3}
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="e.g. What are the main risks mentioned?"
          disabled={asking}
        />
        <div className="ask-row">
          <span className={trimmed.length > MAX_LENGTH ? 'text-error small' : 'muted small'}>
            {trimmed.length}/{MAX_LENGTH} · Ctrl + Enter to send
          </span>
          <button className="btn btn-inline" type="submit" disabled={!canAsk}>
            {asking ? 'Thinking…' : 'Ask'}
          </button>
        </div>
        {error && <div className="alert alert-error">{error}</div>}
      </form>
    </section>
  )
}