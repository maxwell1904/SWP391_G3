export const tomorrow = () => {
  const date = new Date()
  date.setDate(date.getDate() + 1)
  return date.toISOString().slice(0, 10)
}

export const formatMoney = value => new Intl.NumberFormat('vi-VN', {
  style: 'currency',
  currency: 'VND',
  maximumFractionDigits: 0
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
