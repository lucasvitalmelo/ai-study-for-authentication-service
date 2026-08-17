import { useState } from 'react'
import { api } from '../api'

// O backend nao tem um endpoint dedicado de "trocar senha estando logado" —
// so o fluxo de esqueci-minha-senha (token gerado + logado no console, sem
// e-mail real no v1). Esta tela reaproveita esse fluxo em vez de inventar
// um endpoint que nao existe.
export default function ChangePasswordDialog({ email, onClose, onPasswordChanged }) {
  const [step, setStep] = useState('request')
  const [token, setToken] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [message, setMessage] = useState(null)
  const [error, setError] = useState(null)

  async function handleRequest() {
    setError(null)
    try {
      await api.requestPasswordReset(email)
      setMessage('Código gerado — veja o console do backend (mvn spring-boot:run) para copiar o token.')
      setStep('confirm')
    } catch (err) {
      setError(err.message)
    }
  }

  async function handleConfirm(e) {
    e.preventDefault()
    setError(null)
    try {
      await api.confirmPasswordReset(token, newPassword)
      onPasswordChanged()
    } catch (err) {
      setError(err.message)
    }
  }

  return (
    <div className="dialog-backdrop" onClick={onClose}>
      <div className="dialog" onClick={(e) => e.stopPropagation()}>
        <h2>Alterar senha</h2>
        <p className="dialog-note">
          Reaproveita o fluxo de reset de senha — o backend ainda não tem um
          endpoint de troca de senha para usuário já logado.
        </p>

        {step === 'request' && (
          <button onClick={handleRequest}>Gerar código de confirmação</button>
        )}

        {step === 'confirm' && (
          <form onSubmit={handleConfirm}>
            {message && <div className="notice">{message}</div>}
            <label>Token (colado do console do backend)</label>
            <input value={token} onChange={(e) => setToken(e.target.value)} required />
            <label>Nova senha</label>
            <input
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              type="password"
              required
            />
            <button type="submit">Confirmar</button>
          </form>
        )}

        {error && <div className="error">{error}</div>}
        <button className="link-button" onClick={onClose}>Cancelar</button>
      </div>
    </div>
  )
}
