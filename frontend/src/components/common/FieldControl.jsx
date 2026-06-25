export function FieldControl({ label, children, error, hint }) {
  return (
    <label className={error ? 'fieldControl invalid' : 'fieldControl'}>
      <span>{label}</span>
      {children}
      {error && <small className="fieldError">{error}</small>}
      {!error && hint && <small className="fieldHint">{hint}</small>}
    </label>
  )
}
