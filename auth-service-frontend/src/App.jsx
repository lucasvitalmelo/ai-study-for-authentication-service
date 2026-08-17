import { useState } from 'react'
import LoginScreen from './components/LoginScreen'
import AppShell from './components/AppShell'
import { api } from './api'

export default function App() {
  const [accessToken, setAccessToken] = useState(null)
  const [refreshToken, setRefreshToken] = useState(null)
  const [user, setUser] = useState(null)
  const [notice, setNotice] = useState(null)

  async function handleLogin(newAccessToken, newRefreshToken) {
    const me = await api.me(newAccessToken)
    setAccessToken(newAccessToken)
    setRefreshToken(newRefreshToken)
    setUser(me)
    setNotice(null)
  }

  async function handleLogout() {
    try {
      await api.logout(refreshToken)
    } catch {
      // mesmo se a chamada falhar, limpa a sessão localmente
    }
    setAccessToken(null)
    setRefreshToken(null)
    setUser(null)
  }

  function handlePasswordChanged() {
    // confirm() no backend revoga TODOS os refresh tokens do usuário — a sessão
    // atual também cai, então precisa logar de novo com a senha nova.
    setAccessToken(null)
    setRefreshToken(null)
    setUser(null)
    setNotice('Senha alterada. Faça login novamente com a nova senha.')
  }

  if (!user) {
    return <LoginScreen onLogin={handleLogin} notice={notice} />
  }

  return (
    <AppShell
      user={user}
      accessToken={accessToken}
      onLogout={handleLogout}
      onPasswordChanged={handlePasswordChanged}
    />
  )
}
