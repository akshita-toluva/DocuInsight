import { Navigate, Outlet, Link, useNavigate } from 'react-router'
import { useAuth } from '../context/AuthContext.jsx'

export default function ProtectedLayout() {
  // Hooks first (rule of hooks)
  const { isLoggedIn, logout } = useAuth()
  const navigate = useNavigate()

  // Guard: not logged in -> go to login
  if (!isLoggedIn) {
    return <Navigate to="/login" replace />
  }

  function handleLogout() {
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <div>
      <header className="navbar">
        <Link to="/files" className="brand">DocuInsight</Link>
        <button className="btn-link" onClick={handleLogout}>Log out</button>
      </header>
      <main className="page">
        <Outlet />
      </main>
    </div>
  )
}