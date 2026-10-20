import { Link } from 'react-router'

export default function NotFoundPage() {
  return (
    <div className="not-found">
      <h1>404</h1>
      <p className="muted">This page does not exist.</p>
      <Link to="/files" className="btn btn-inline">Go to My Files</Link>
    </div>
  )
}