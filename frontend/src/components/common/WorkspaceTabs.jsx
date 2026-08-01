import { useRef } from 'react'

export function WorkspaceTabs({ items, value, onChange, ariaLabel = 'Workspace sections' }) {
  const tabListRef = useRef(null)

  function handleKeyDown(event, currentIndex) {
    const supportedKeys = ['ArrowLeft', 'ArrowRight', 'Home', 'End']
    if (!supportedKeys.includes(event.key)) return
    event.preventDefault()

    let nextIndex = currentIndex
    if (event.key === 'ArrowLeft') nextIndex = (currentIndex - 1 + items.length) % items.length
    if (event.key === 'ArrowRight') nextIndex = (currentIndex + 1) % items.length
    if (event.key === 'Home') nextIndex = 0
    if (event.key === 'End') nextIndex = items.length - 1

    onChange(items[nextIndex].value)
    tabListRef.current?.querySelectorAll('[role="tab"]')[nextIndex]?.focus()
  }

  return (
    <div ref={tabListRef} className="workspaceTabs" role="tablist" aria-label={ariaLabel}>
      {items.map((item, index) => (
        <button
          key={item.value}
          type="button"
          role="tab"
          className={value === item.value ? 'active' : ''}
          aria-selected={value === item.value}
          tabIndex={value === item.value ? 0 : -1}
          onClick={() => onChange(item.value)}
          onKeyDown={event => handleKeyDown(event, index)}
        >
          {item.label}
        </button>
      ))}
    </div>
  )
}
