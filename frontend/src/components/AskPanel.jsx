import { useEffect,useState } from 'react'
import Markdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { askQuestion } from '../api/reports.js'
import { getErrorMessage } from '../api/client.js'
import { getUsage } from '../api/users.js'

const MAX_LENGTH = 1000

const SNIPPET_CHARS = 200

// Display-only cleanup: the stored chunk text is unchanged
function cleanSource(text) {
  return text
    .replace(/\*\*/g, '')          // bold markers from the vision model's markdown
    .replace(/^#+\s*/gm, '')       // markdown heading markers at line start
    .replace(/\n{3,}/g, '\n\n')    // collapse big blank gaps
    .trim()
}

// Display-only: hide the [1][2] citation markers; the backend still uses them to pick sources
function stripCitations(text) {
  return text.replace(/\s?(\[\d+\]|【[^】]*】)/g, '')
}

// One source passage: short snippet first, "Show more" for the full chunk
function SourceText({ text }) {
  const [expanded, setExpanded] = useState(false)
  const clean = cleanSource(text)
  const isLong = clean.length > SNIPPET_CHARS
  const shown = expanded || !isLong ? clean : clean.slice(0, SNIPPET_CHARS).trimEnd() + '…'

  return (
    <>
      <blockquote>{shown}</blockquote>
      {isLong && (
        <button type="button" className="link-btn" onClick={() => setExpanded(!expanded)}>
          {expanded ? 'Show less' : 'Show more'}
        </button>
      )}
    </>
  )
}

export default function AskPanel({ reportId }) {
  const [question, setQuestion] = useState('')
  const [history, setHistory] = useState([]) // [{ id, question, answer }], oldest first
  const [asking, setAsking] = useState(false)
  const [error, setError] = useState('')

  const [usage, setUsage] = useState(null)

  // The counter is a convenience: if it fails to load, asking still works
  function refreshUsage() {
      getUsage().then(setUsage).catch(() => {})
  }
  useEffect(() => { refreshUsage() }, [])

  const questionsLeft = usage ? Math.max(usage.questionsLimit - usage.questionsUsed, 0) : null
  const trimmed = question.trim()
  const canAsk = trimmed.length > 0 && trimmed.length <= MAX_LENGTH && !asking && questionsLeft !== 0

  async function handleSubmit(e) {
    e.preventDefault()
    if (!canAsk) return
    setAsking(true)
    setError('')
    try {
      const result = await askQuestion(reportId, trimmed)
      setHistory((prev) => [
              ...prev,
              { id: Date.now(), question: result.question, answer: result.answer, sources: result.sources ?? [] },
            ])
      setQuestion('')
    } catch (err) {
      setError(getErrorMessage(err))
    } finally {
      setAsking(false)
      refreshUsage()
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
                 <Markdown remarkPlugins={[remarkGfm]}>{stripCitations(item.answer)}</Markdown>
               </div>
              {item.sources.length > 0 && (
                 <details className="qa-sources">
                    <summary>Where this answer came from ({item.sources.length})</summary>
                    <ul>
                    {item.sources.map((s) => (
                     <li key={s.number}>
                      <span className="qa-source-label">
                        {s.page != null ? `Page ${s.page}` : `Passage ${s.number}`}
                      </span>
                       <SourceText text={s.text} />
                     </li>
                     ))}
                    </ul>
                 </details>
              )}
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
            {questionsLeft !== null && ` · ${questionsLeft} of ${usage.questionsLimit} questions left today`}
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