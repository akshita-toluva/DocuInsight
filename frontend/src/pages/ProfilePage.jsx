import { useEffect, useState } from 'react'
import FormField from '../components/FormField.jsx'
import { getProfile, updateProfile, changePassword } from '../api/users.js'
import { getErrorMessage } from '../api/client.js'

export default function ProfilePage() {
  const [profile, setProfile] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    getProfile()
      .then(setProfile)
      .catch((err) => setError(getErrorMessage(err)))
      .finally(() => setLoading(false))
  }, [])

  if (loading) {
    return <p className="muted">Loading profile…</p>
  }

  if (error) {
    return <div className="alert alert-error">{error}</div>
  }

  return (
    <div className="narrow">
      <h1>Profile</h1>

      <section className="card profile-summary">
        <dl>
          <dt>Name</dt>
          <dd>{profile.name}</dd>
          <dt>Email</dt>
          <dd>{profile.email}</dd>
          <dt>Member since</dt>
          <dd>{new Date(profile.createdAt).toLocaleDateString()}</dd>
        </dl>
      </section>

      <NameForm currentName={profile.name} onSaved={setProfile} />
      <PasswordForm />
    </div>
  )
}

// ---------- Change name ----------
function NameForm({ currentName, onSaved }) {
  const [name, setName] = useState(currentName)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')

  const trimmed = name.trim()
  const changed = trimmed !== currentName
  const valid = trimmed.length > 0 && trimmed.length <= 100

  async function handleSubmit(e) {
    e.preventDefault()
    if (!changed || !valid) return
    setSaving(true)
    setError('')
    setMessage('')
    try {
      const updated = await updateProfile(trimmed)
      onSaved(updated) // parent shows the new name in the summary card
      setMessage('Name updated.')
    } catch (err) {
      setError(getErrorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  return (
    <form className="card profile-form" onSubmit={handleSubmit}>
      <h2>Change name</h2>
      <FormField id="profileName" label="Name" value={name} onChange={setName} autoComplete="name" />
      {trimmed.length > 100 && <p className="text-error small">Name must be at most 100 characters.</p>}
      <button className="btn btn-inline" type="submit" disabled={!changed || !valid || saving}>
        {saving ? 'Saving…' : 'Save name'}
      </button>
      {error && <div className="alert alert-error">{error}</div>}
      {message && <div className="alert alert-success">{message}</div>}
    </form>
  )
}

// ---------- Change password ----------
function PasswordForm() {
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')

  async function handleSubmit(e) {
    e.preventDefault()
    setError('')
    setMessage('')

    // Browser-only checks: fast feedback; the backend checks again
    if (newPassword !== confirmPassword) {
      setError('New passwords do not match.')
      return
    }
    if (newPassword === currentPassword) {
      setError('New password must be different from the current one.')
      return
    }

    setSaving(true)
    try {
      await changePassword(currentPassword, newPassword)
      setCurrentPassword('')
      setNewPassword('')
      setConfirmPassword('')
      setMessage('Password changed. Use the new password next time you log in.')
    } catch (err) {
      setError(getErrorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  return (
    <form className="card profile-form" onSubmit={handleSubmit}>
      <h2>Change password</h2>
      <FormField id="currentPassword" label="Current password" type="password"
        value={currentPassword} onChange={setCurrentPassword} autoComplete="current-password" />
      <FormField id="newPassword" label="New password" type="password"
        value={newPassword} onChange={setNewPassword} autoComplete="new-password" minLength={6} />
      <FormField id="confirmNewPassword" label="Confirm new password" type="password"
        value={confirmPassword} onChange={setConfirmPassword} autoComplete="new-password" minLength={6} />
      <button className="btn btn-inline" type="submit" disabled={saving}>
        {saving ? 'Saving…' : 'Change password'}
      </button>
      {error && <div className="alert alert-error">{error}</div>}
      {message && <div className="alert alert-success">{message}</div>}
    </form>
  )
}