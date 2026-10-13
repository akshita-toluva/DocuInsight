import { useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import { useAuth } from '../context/AuthContext.jsx'
import { getErrorMessage } from '../api/client.js'
import FormField from '../components/FormField.jsx'

export default function RegisterPage() {
  const { register, isLoggedIn } = useAuth()
  const navigate = useNavigate()

  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  if (isLoggedIn) {
    return <Navigate to="/files" replace />
  }

  async function handleSubmit(event) {
    event.preventDefault()
    setError('')

    // Frontend-only check: backend never sees confirmPassword
    if (password !== confirmPassword) {
      setError('Passwords do not match')
      return
    }

    setLoading(true)
    try {
      const message = await register(name, email, password)
      navigate('/login', { state: { message: `${message}. Please log in.` } })
    } catch (err) {
      setError(getErrorMessage(err))
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="auth-page">
      <form className="auth-card" onSubmit={handleSubmit}>
        <h1>Create account</h1>
        <p className="subtitle">Start using DocuInsight</p>

        {error && <div className="alert alert-error">{error}</div>}

        <FormField id="name" label="Name" value={name} onChange={setName} autoComplete="name" />
        <FormField id="email" label="Email" type="email" value={email} onChange={setEmail} autoComplete="email" />
        <FormField id="password" label="Password" type="password" value={password}
          onChange={setPassword} autoComplete="new-password" minLength={6} />
        <FormField id="confirmPassword" label="Confirm password" type="password" value={confirmPassword}
          onChange={setConfirmPassword} autoComplete="new-password" minLength={6} />

        <button className="btn" type="submit" disabled={loading}>
          {loading ? 'Creating account…' : 'Register'}
        </button>

        <p className="auth-switch">
          Already have an account? <Link to="/login">Log in</Link>
        </p>
      </form>
    </div>
  )
}