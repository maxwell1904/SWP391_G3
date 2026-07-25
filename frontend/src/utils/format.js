export const dateInputValue = value => {
  const date = new Date(value)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export const today = () => dateInputValue(new Date())

export const tomorrow = () => {
  const date = new Date()
  date.setDate(date.getDate() + 1)
  return dateInputValue(date)
}

export const formatMoney = value => new Intl.NumberFormat('en-US', {
  style: 'currency',
  currency: 'USD',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2
}).format(Number(value || 0))

export const formatTime = value => String(value || '').slice(0, 5)

export const formatTimeRange = (startTime, endTime) => `${formatTime(startTime)} - ${formatTime(endTime)}`

export const formatDateTime = value => {
  if (!value) return 'Pending'
  return new Intl.DateTimeFormat('vi-VN', {
    dateStyle: 'medium',
    timeStyle: 'short'
  }).format(new Date(value))
}

export const formatDate = value => {
  if (!value) return 'Not set'
  return new Intl.DateTimeFormat('en-US', { month: 'short', day: 'numeric', year: 'numeric' }).format(new Date(`${value}T00:00:00`))
}

export const resolveAssetUrl = value => {
  if (!value || !String(value).startsWith('/api/')) return value || ''
  const baseUrl = import.meta.env.VITE_API_BASE_URL || 'http://127.0.0.1:8080/api'
  try {
    return `${new URL(baseUrl).origin}${value}`
  } catch {
    return value
  }
}
