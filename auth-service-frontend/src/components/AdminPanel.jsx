import { useEffect, useState } from 'react'
import { api } from '../api'

export default function AdminPanel({ accessToken }) {
  const [users, setUsers] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [newEmail, setNewEmail] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [creating, setCreating] = useState(false)

  async function loadUsers() {
    setLoading(true)
    setError(null)
    try {
      const data = await api.listUsers(accessToken)
      setUsers(data)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadUsers()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  async function handleCreate(e) {
    e.preventDefault()
    setCreating(true)
    setError(null)
    try {
      await api.register(newEmail, newPassword)
      setNewEmail('')
      setNewPassword('')
      await loadUsers()
    } catch (err) {
      setError(err.message)
    } finally {
      setCreating(false)
    }
  }

  return (
    <div className="panel">
      <h2>Usuários</h2>

      <form className="inline-form" onSubmit={handleCreate}>
        <input
          placeholder="e-mail do novo usuário"
          value={newEmail}
          onChange={(e) => setNewEmail(e.target.value)}
          required
        />
        <input
          placeholder="senha"
          type="password"
          value={newPassword}
          onChange={(e) => setNewPassword(e.target.value)}
          required
        />
        <button type="submit" disabled={creating}>Criar usuário</button>
      </form>
      <p className="panel-note">
        Novo usuário sempre entra com papel USER — o endpoint de cadastro do backend
        não permite escolher ADMIN na criação.
      </p>

      {error && <div className="error">{error}</div>}

      {loading ? (
        <p>Carregando…</p>
      ) : (
        <table>
          <thead>
            <tr><th>ID</th><th>E-mail</th><th>Papel</th></tr>
          </thead>
          <tbody>
            {users.map((u) => (
              <tr key={u.id}>
                <td>{u.id}</td>
                <td>{u.email}</td>
                <td>{u.role}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <button className="link-button" onClick={loadUsers}>Atualizar lista</button>
    </div>
  )
}
