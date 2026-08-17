import { useState } from 'react'
import { api } from '../api'

export default function LoginScreen({ onLogin, notice }) {
  const [email, setEmail] = useState('teste@exemplo.com')
  const [password, setPassword] = useState('senha123')
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(false)

  async function handleSubmit(e) {
    e.preventDefault()
    setError(null)
    setLoading(true)
    try {
      const { accessToken, refreshToken } = await api.login(email, password)
      await onLogin(accessToken, refreshToken)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="login-screen">
      <form className="login-card" onSubmit={handleSubmit}>
        <h1>auth-service</h1>
        <p className="subtitle">Entrar (funciona pra USER e ADMIN)</p>
        {notice && <div className="notice">{notice}</div>}
        <label>E-mail</label>
        <input value={email} onChange={(e) => setEmail(e.target.value)} type="email" required />
        <label>Senha</label>
        <input value={password} onChange={(e) => setPassword(e.target.value)} type="password" required />
        {error && <div className="error">{error}</div>}
        <button type="submit" disabled={loading}>{loading ? 'Entrando…' : 'Entrar'}</button>
      </form>
    </div>
  )
}
