import { LogIn } from 'lucide-react'

export function AccessPanel({ title, text, onLogin }) {
  return (
    <section className="section accessSection">
      <div className="accessPanel">
        <span>Restricted area</span>
        <h2>{title}</h2>
        <p>{text}</p>
        <button className="primaryButton" onClick={onLogin}>
          <LogIn size={18} />
          <span>Login</span>
        </button>
      </div>
    </section>
  )
}
