import { ShieldCheck } from 'lucide-react'
import { formatMoney } from '../../../utils/format'

export function SelectedBooking({ booking }) {
  if (!booking) return <p className="emptyText">Select a booking first.</p>
  return (
    <div className="selectedBooking">
      <ShieldCheck size={19} />
      <div>
        <strong>{booking.bookingCode} · {booking.status}</strong>
        <p>{booking.customer} · {booking.fieldName} · {formatMoney(booking.totalAmount)}</p>
      </div>
    </div>
  )
}
