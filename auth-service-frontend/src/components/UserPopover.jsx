export default function UserPopover({ user, onClose, onLogout, onOpenChangePassword }) {
  // O backend nao tem um campo "nome" no User (so id/email/role) — o nome exibido aqui
  // e so a parte local do e-mail, nao um dado real armazenado.
  const displayName = user.email.split('@')[0]

  return (
    <div className="popover-backdrop" onClick={onClose}>
      <div className="popover" onClick={(e) => e.stopPropagation()}>
        <div className="popover-name">{displayName}</div>
        <div className="popover-email">{user.email}</div>
        <hr />
        <button className="popover-item" onClick={onOpenChangePassword}>Alterar senha</button>
        <button className="popover-item danger" onClick={onLogout}>Sair</button>
      </div>
    </div>
  )
}
