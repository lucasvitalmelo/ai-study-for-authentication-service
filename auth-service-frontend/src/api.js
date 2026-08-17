const BASE_URL = 'http://localhost:8080'

async function request(method, path, body, token) {
  const headers = { 'Content-Type': 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`

  const res = await fetch(`${BASE_URL}${path}`, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  })

  const text = await res.text()
  const data = text ? JSON.parse(text) : null

  if (!res.ok) {
    const message = data?.detail || data?.title || `Erro ${res.status}`
    const error = new Error(message)
    error.status = res.status
    error.body = data
    throw error
  }

  return data
}

export const api = {
  register: (email, password) => request('POST', '/auth/register', { email, password }),
  login: (email, password) => request('POST', '/auth/login', { email, password }),
  me: (token) => request('GET', '/users/me', null, token),
  listUsers: (token) => request('GET', '/users', null, token),
  refresh: (refreshToken) => request('POST', '/auth/refresh', { refreshToken }),
  logout: (refreshToken) => request('POST', '/auth/logout', { refreshToken }),
  requestPasswordReset: (email) => request('POST', '/auth/password-reset', { email }),
  confirmPasswordReset: (token, newPassword) =>
    request('POST', '/auth/password-reset/confirm', { token, newPassword }),
}
