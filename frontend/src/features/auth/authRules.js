export const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
export const phonePattern = /^0\d{9}$/

export const passwordIssues = password => {
  const issues = []
  if (!password) return ['Password is required.']
  if (password.length < 8) issues.push('Use at least 8 characters.')
  if (/\s/.test(password)) issues.push('Remove spaces.')
  if (!/[A-Z]/.test(password)) issues.push('Add one uppercase letter.')
  if (!/[a-z]/.test(password)) issues.push('Add one lowercase letter.')
  if (!/\d/.test(password)) issues.push('Add one number.')
  if (!/[^A-Za-z0-9]/.test(password)) issues.push('Add one special character.')
  return issues
}
