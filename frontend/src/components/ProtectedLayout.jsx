import { Navigate, Outlet, Link, NavLink, useNavigate } from 'react-router'
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
        <nav className="nav-left">
          <Link to="/files" className="brand">DocuInsight</Link>
          <NavLink to="/files" className="nav-link">My Files</NavLink>
          <NavLink to="/reports" end className="nav-link">My Reports</NavLink>
        </nav>
        <button className="btn-link" onClick={handleLogout}>Log out</button>
      </header>
      <main className="page">
        <Outlet />
      </main>
    </div>
  )
}