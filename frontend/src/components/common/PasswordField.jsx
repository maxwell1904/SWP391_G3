import { Eye, EyeOff } from 'lucide-react'
import { FieldControl } from './FieldControl'

export function PasswordField({ label, value, visible, error, hint, autoComplete = 'current-password', onToggle, onChange }) {
  return (
    <FieldControl label={label} error={error} hint={hint}>
      <span className="passwordInput">
        <input
          type={visible ? 'text' : 'password'}
          autoComplete={autoComplete}
          value={value}
          onChange={event => onChange(event.target.value)}
        />
        <button type="button" onClick={onToggle} aria-label={visible ? 'Hide password' : 'Show password'}>
          {visible ? <EyeOff size={18} /> : <Eye size={18} />}
        </button>
      </span>
    </FieldControl>
  )
}
