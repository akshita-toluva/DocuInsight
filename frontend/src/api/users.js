import api from './client.js'

// GET /api/users/profile -> { id, name, email, createdAt }
export async function getProfile() {
  const { data } = await api.get('/api/users/profile')
  return data
}

// PUT /api/users/profile  body: { name } -> updated profile
export async function updateProfile(name) {
  const { data } = await api.put('/api/users/profile', { name })
  return data
}

// PUT /api/users/changePassword  body: { currentPassword, newPassword } -> { message }
export async function changePassword(currentPassword, newPassword) {
  const { data } = await api.put('/api/users/changePassword', { currentPassword, newPassword })
  return data
}