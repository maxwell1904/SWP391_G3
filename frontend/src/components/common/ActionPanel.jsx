export function ActionPanel({ panel, onClose }) {
  return (
    <div className={panel.kind === 'error' ? 'actionPanel error' : 'actionPanel'} role="status" aria-live="polite">
      <div>
        <span>{panel.kind === 'error' ? 'Needs attention' : 'Done'}</span>
        <h3>{panel.title}</h3>
        <p>{panel.message}</p>
      </div>
      <button onClick={onClose}>Close</button>
    </div>
  )
}
