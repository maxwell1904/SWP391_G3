export function WorkspaceTabs({ items, value, onChange, ariaLabel = 'Workspace sections' }) {
  return (
    <nav className="workspaceTabs" aria-label={ariaLabel}>
      {items.map(item => (
        <button
          key={item.value}
          type="button"
          className={value === item.value ? 'active' : ''}
          aria-current={value === item.value ? 'page' : undefined}
          onClick={() => onChange(item.value)}
        >
          {item.label}
        </button>
      ))}
    </nav>
  )
}
