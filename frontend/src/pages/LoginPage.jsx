import { useState } from 'react'
import { Link, Navigate, useNavigate, useLocation } from 'react-router'
import { useAuth } from '../context/AuthContext.jsx'
import { getErrorMessage } from '../api/client.js'

export default function LoginPage()
{
  const { login, isLoggedIn } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  const [email, setEmail] = useState('')
  const [password,setPassword] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  //msg sent from register page
  const successMessage= location.state?.message

  //Already logged in ? Skip this page
  if(isLoggedIn)
  {
    return <Navigate to="/files" replace />
  }

  //On Submit
  async function handleSubmit(event)
  {
    event.preventDefault()
    setError('')
    setLoading(true)
    try{
      await login(email,password)
      navigate('/files',{ replace: true })
    }catch(err){
      setError(getErrorMessage(err))
    }finally{
       setLoading(false)
    }
  }

  //drawn on the page
  return(
    <div className="auth-page">
      <form className="auth-card" onSubmit={handleSubmit}>
        <h1>Welcome Back</h1>
        <p className="subtitle">Log in to DocuInsight</p>
        <p>No account? <Link to="/register">Register</Link></p>


        {successMessage && <div className="alert alert-success">{successMessage}</div>}
        {error && <div className="alert alert-error">{error}</div>}

        <div className="field">
                  <label htmlFor="email">Email</label>
                  <input
                    id="email"
                    type="email"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    autoComplete="email"
                    required
                  />
        </div>

        <div className="field">
                  <label htmlFor="password">Password</label>
                  <input
                    id="password"
                    type="password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    autoComplete="current-password"
                    required
                  />
        </div>

        <button className="btn" type="submit" disabled={loading}>
                  {loading ? 'Logging in…' : 'Log in'}
        </button>
      </form>
    </div>
  )
}