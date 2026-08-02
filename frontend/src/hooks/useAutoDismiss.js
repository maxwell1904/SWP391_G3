import { useEffect, useRef } from 'react'

export function useAutoDismiss(value, onDismiss, delay = 5000, enabled = true) {
  const onDismissRef = useRef(onDismiss)

  useEffect(() => {
    onDismissRef.current = onDismiss
  }, [onDismiss])

  useEffect(() => {
    if (!value || !enabled) return undefined

    const timer = window.setTimeout(() => onDismissRef.current(), delay)
    return () => window.clearTimeout(timer)
  }, [value, delay, enabled])
}
