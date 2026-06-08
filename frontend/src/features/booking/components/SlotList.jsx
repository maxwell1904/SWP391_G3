import { Badge, Group, Text } from '@mantine/core'
import { Clock3, MapPin } from 'lucide-react'
import { formatMoney, formatTimeRange } from '../../../utils/format'

const labelize = value => String(value || '').replace(/_/g, ' ').toLowerCase()

export function SlotList({ slots, selectedSlotId, onSelect }) {
  if (!slots.length) return <p className="emptyText">No slots found for this date.</p>
  return (
    <div className="slotList">
      {slots.map(slot => {
        const selected = Number(selectedSlotId) === slot.slotId
        return (
          <button
            key={slot.slotId}
            className={selected ? 'slotItem selected' : 'slotItem'}
            onClick={() => onSelect(slot.slotId)}
            disabled={!slot.available}
          >
            <Group justify="space-between" align="flex-start" gap="xs" wrap="nowrap">
              <Badge color={slot.available ? 'green' : 'gray'} variant={slot.available ? 'light' : 'outline'} radius="sm">
                {slot.available ? 'Available' : labelize(slot.status)}
              </Badge>
              <Text component="span" className="slotPrice">{formatMoney(slot.price)}</Text>
            </Group>
            <strong className="slotFieldName">{slot.fieldName}</strong>
            <span className="slotTimeLine"><Clock3 size={16} /> {formatTimeRange(slot.startTime, slot.endTime)}</span>
            <span className="slotMetaLine"><MapPin size={15} /> {slot.fieldType || 'Field'}</span>
          </button>
        )
      })}
    </div>
  )
}
