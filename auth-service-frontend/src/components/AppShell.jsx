import { useState } from 'react'
import UserPopover from './UserPopover'
import ChangePasswordDialog from './ChangePasswordDialog'
import AdminPanel from './AdminPanel'
import UserPanel from './UserPanel'

export default function AppShell({ user, accessToken, onLogout, onPasswordChanged }) {
  const [popoverOpen, setPopoverOpen] = useState(false)
  const [changePasswordOpen, setChangePasswordOpen] = useState(false)
  const isAdmin = user.role === 'ADMIN'

  return (
    <div className="app-shell">
      <header className="app-bar">
        <div className="app-bar-left">
          {isAdmin ? 'Área do Administrador' : 'Área do Usuário'}
        </div>
        <div className="app-bar-right">
          <button className="avatar" onClick={() => setPopoverOpen(true)}>
            {user.email.charAt(0).toUpperCase()}
          </button>
        </div>
      </header>

      <main className="app-content">
        {isAdmin ? <AdminPanel accessToken={accessToken} /> : <UserPanel user={user} />}
      </main>

      {popoverOpen && (
        <UserPopover
          user={user}
          onClose={() => setPopoverOpen(false)}
          onLogout={onLogout}
          onOpenChangePassword={() => {
            setPopoverOpen(false)
            setChangePasswordOpen(true)
          }}
        />
      )}

      {changePasswordOpen && (
        <ChangePasswordDialog
          email={user.email}
          onClose={() => setChangePasswordOpen(false)}
          onPasswordChanged={() => {
            setChangePasswordOpen(false)
            onPasswordChanged()
          }}
        />
      )}
    </div>
  )
}
