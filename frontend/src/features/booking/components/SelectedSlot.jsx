import { Clock } from 'lucide-react'
import { formatDate, formatMoney, formatTimeRange } from '../../../utils/format'

export function SelectedSlot({ slot }) {
  if (!slot) return <p className="emptyText">Select an available slot first.</p>
  return (
    <div className="selectedSlot">
      <Clock size={18} />
      <div>
        <strong>{slot.fieldName}</strong>
        <p>{formatDate(slot.slotDate)} · {formatTimeRange(slot.startTime, slot.endTime)}</p>
      </div>
      <span>{formatMoney(slot.price)}</span>
    </div>
  )
}
