import { useAutoDismiss } from '../../hooks/useAutoDismiss'

export function ActionPanel({ panel, onClose }) {
  const isError = panel.kind === 'error'
  useAutoDismiss(panel, onClose, isError ? 7000 : 4500)

  return (
    <div
      className={isError ? 'actionPanel error' : 'actionPanel'}
      role={isError ? 'alert' : 'status'}
      aria-live={isError ? 'assertive' : 'polite'}
      aria-atomic="true"
    >
      <div>
        <span>{isError ? 'Needs attention' : 'Done'}</span>
        <h3>{panel.title}</h3>
        <p>{panel.message}</p>
      </div>
      <button type="button" onClick={onClose} aria-label={`Dismiss ${panel.title}`}>Close</button>
    </div>
  )
}
