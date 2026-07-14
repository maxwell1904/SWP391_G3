import { Badge } from '@mantine/core'

export function BookingList({ bookings, selectedBookingId, onSelect }) {
  if (!bookings.length) return <p className="emptyText">No bookings yet.</p>
  return (
    <div className="bookingList">
      {bookings.map(booking => (
        <button
          key={booking.bookingId}
          className={Number(selectedBookingId) === booking.bookingId ? 'bookingItem selected' : 'bookingItem'}
          onClick={() => onSelect(booking.bookingId)}
        >
          <span>
            <strong>{booking.bookingCode}</strong>
            <small>{booking.customer} · {booking.fieldName} · {booking.startTime}</small>
          </span>
          <Badge className="listBadge" variant="light" color={statusColor(booking.status)}>{booking.status}</Badge>
        </button>
      ))}
    </div>
  )
}

function statusColor(status) {
  if (status === 'confirmed' || status === 'completed') return 'green'
  if (status === 'pending') return 'yellow'
  if (status === 'cancelled' || status === 'rejected' || status === 'no_show') return 'red'
  return 'gray'
}
