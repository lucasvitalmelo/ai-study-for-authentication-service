export default function UserPanel({ user }) {
  return (
    <div className="panel">
      <h2>Minha área</h2>
      <p>Bem-vindo(a), {user.email}.</p>
      <p className="panel-note">
        Não há funcionalidades adicionais para o papel USER neste projeto — esta tela
        existe só pra confirmar visualmente que o RBAC te trouxe pra cá, e não pra
        área de administrador.
      </p>
    </div>
  )
}
