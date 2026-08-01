import { useEffect, useRef } from 'react'

export function ActionPanel({ panel, onClose }) {
  const onCloseRef = useRef(onClose)
  useEffect(() => {
    onCloseRef.current = onClose
  }, [onClose])

  useEffect(() => {
    if (panel.kind === 'error') return undefined
    const timer = window.setTimeout(() => onCloseRef.current(), 4500)
    return () => window.clearTimeout(timer)
  }, [panel])

  const isError = panel.kind === 'error'
  return (
    <div
      className={isError ? 'actionPanel error' : 'actionPanel'}
      role={isError ? 'alert' : 'status'}
      aria-live={isError ? 'assertive' : 'polite'}
    >
      <div>
        <span>{isError ? 'Needs attention' : 'Done'}</span>
        <h3>{panel.title}</h3>
        <p>{panel.message}</p>
      </div>
      <button onClick={onClose} aria-label={`Dismiss ${panel.title}`}>Close</button>
    </div>
  )
}
