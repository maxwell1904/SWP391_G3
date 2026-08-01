import { Badge } from '@mantine/core'
import { formatDate, formatTime } from '../../../utils/format'

export function BookingList({ bookings, selectedBookingId, onSelect }) {
  if (!bookings.length) return <p className="emptyText">No bookings yet.</p>
  return (
    <div className="bookingList">
      {bookings.map(booking => (
        <button
          type="button"
          key={booking.bookingId}
          className={Number(selectedBookingId) === booking.bookingId ? 'bookingItem selected' : 'bookingItem'}
          onClick={() => onSelect(booking.bookingId)}
          aria-pressed={Number(selectedBookingId) === booking.bookingId}
        >
          <span>
            <strong>{booking.bookingCode}</strong>
            <small>{formatDate(booking.slotDate)} · {formatTime(booking.startTime)} · {booking.fieldName}</small>
            {booking.customer && <em>{booking.customer}</em>}
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
