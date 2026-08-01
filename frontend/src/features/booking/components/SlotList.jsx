import { useEffect, useState } from 'react'
import { Badge, Group, Text } from '@mantine/core'
import { Clock3, MapPin } from 'lucide-react'
import { formatMoney, formatTimeRange } from '../../../utils/format'

const labelize = value => String(value || '').replace(/_/g, ' ').toLowerCase()

export function SlotList({ slots, selectedSlotId, onSelect }) {
  const slotsByField = slots.reduce((groups, slot) => {
    const key = slot.fieldId || slot.fieldName
    const existing = groups.get(key) || {
      fieldName: slot.fieldName,
      fieldType: slot.fieldType || 'Football field',
      slots: []
    }
    existing.slots.push(slot)
    groups.set(key, existing)
    return groups
  }, new Map())
  const fieldGroups = [...slotsByField.entries()]
  const selectedFieldKey = fieldGroups.find(([, group]) => (
    group.slots.some(slot => Number(selectedSlotId) === slot.slotId)
  ))?.[0]
  const firstFieldKey = fieldGroups[0]?.[0]
  const [openFieldKey, setOpenFieldKey] = useState(null)
  const [showAllFields, setShowAllFields] = useState(() => window.matchMedia('(min-width: 721px)').matches)

  useEffect(() => {
    if (selectedFieldKey != null) setOpenFieldKey(selectedFieldKey)
    else if (firstFieldKey != null) setOpenFieldKey(firstFieldKey)
  }, [firstFieldKey, selectedFieldKey])

  useEffect(() => {
    const mediaQuery = window.matchMedia('(min-width: 721px)')
    const syncLayout = event => setShowAllFields(event.matches)
    mediaQuery.addEventListener('change', syncLayout)
    return () => mediaQuery.removeEventListener('change', syncLayout)
  }, [])

  if (!slots.length) return <p className="emptyText">No slots found for this date.</p>

  return (
    <div className="slotFieldGroups">
      {fieldGroups.map(([fieldKey, group]) => {
        const availableCount = group.slots.filter(slot => slot.available).length
        return (
          <details
            className="slotFieldGroup"
            key={fieldKey}
            open={showAllFields || fieldKey === openFieldKey}
            onToggle={event => {
              if (!showAllFields && event.currentTarget.open && fieldKey !== openFieldKey) setOpenFieldKey(fieldKey)
            }}
          >
            <summary className="slotFieldHeader" onClick={event => { if (showAllFields) event.preventDefault() }}>
              <span>
                <strong>{group.fieldName}</strong>
                <small><MapPin size={14} aria-hidden="true" />{group.fieldType}</small>
              </span>
              <span>{availableCount} of {group.slots.length} available</span>
            </summary>
            <div className="slotList">
              {group.slots.map(slot => {
                const selected = Number(selectedSlotId) === slot.slotId
                return (
                  <button
                    type="button"
                    key={slot.slotId}
                    className={selected ? 'slotItem selected' : 'slotItem'}
                    onClick={() => onSelect(slot.slotId)}
                    disabled={!slot.available}
                    aria-pressed={selected}
                    aria-label={`${group.fieldName}, ${formatTimeRange(slot.startTime, slot.endTime)}, ${formatMoney(slot.price)}, ${slot.available ? 'available' : labelize(slot.status)}`}
                  >
                    <span className="slotTimeLine"><Clock3 size={16} aria-hidden="true" /> {formatTimeRange(slot.startTime, slot.endTime)}</span>
                    <Group justify="space-between" gap="xs" wrap="nowrap">
                      <Text component="span" className="slotPrice">{formatMoney(slot.price)}</Text>
                      <Badge color={slot.available ? 'green' : 'gray'} variant={slot.available ? 'light' : 'outline'} radius="sm">
                        {selected ? 'Selected' : slot.available ? 'Free' : labelize(slot.status)}
                      </Badge>
                    </Group>
                  </button>
                )
              })}
            </div>
          </details>
        )
      })}
    </div>
  )
}
