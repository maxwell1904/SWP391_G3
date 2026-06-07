import { useEffect, useMemo, useState } from 'react'
import { Badge, Button, Divider, Group, Image, Paper, SimpleGrid, Stack, Text, Title } from '@mantine/core'
import { CalendarDays, Clock3, MapPin, Ruler, UsersRound } from 'lucide-react'
import { FieldControl, SectionIntro } from '../components/common'
import api from '../services/api'
import { formatMoney, formatTimeRange } from '../utils/format'

const labelize = value => String(value || '').replace(/_/g, ' ').toLowerCase()

export function FieldsPage({
  fields,
  slots = [],
  searchDate,
  setSearchDate,
  setFieldTypeFilter,
  setSelectedSlotId,
  navigatePage
}) {
  const [selectedFieldId, setSelectedFieldId] = useState(null)
  const [fieldDetail, setFieldDetail] = useState(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [detailError, setDetailError] = useState('')
  const [availability, setAvailability] = useState({ loaded: false, items: [], error: '' })

  useEffect(() => {
    if (!fields.length) {
      setSelectedFieldId(null)
      return
    }
    if (!fields.some(field => Number(field.fieldId) === Number(selectedFieldId))) {
      setSelectedFieldId(fields[0].fieldId)
    }
  }, [fields, selectedFieldId])

  const selectedField = useMemo(() => {
    if (!fields.length) return null
    return fields.find(field => Number(field.fieldId) === Number(selectedFieldId)) || fields[0]
  }, [fields, selectedFieldId])

  useEffect(() => {
    if (!selectedField?.fieldId) {
      setFieldDetail(null)
      return undefined
    }

    let cancelled = false
    setDetailLoading(true)
    setDetailError('')
    api.get(`/fields/${selectedField.fieldId}`)
      .then(response => {
        if (!cancelled) setFieldDetail(response.data)
      })
      .catch(error => {
        if (!cancelled) {
          setFieldDetail(null)
          setDetailError(error.response?.data?.error || 'Could not load field details.')
        }
      })
      .finally(() => {
        if (!cancelled) setDetailLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [selectedField?.fieldId])

  useEffect(() => {
    if (!searchDate) return undefined

    let cancelled = false
    setAvailability({ loaded: false, items: [], error: '' })
    api.get('/slots/search', { params: { date: searchDate } })
      .then(response => {
        if (!cancelled) setAvailability({ loaded: true, items: response.data, error: '' })
      })
      .catch(error => {
        if (!cancelled) {
          setAvailability({
            loaded: true,
            items: [],
            error: error.response?.data?.error || 'Could not load availability.'
          })
        }
      })

    return () => {
      cancelled = true
    }
  }, [searchDate])

  const availabilityItems = availability.loaded ? availability.items : slots
  const selectedSlots = useMemo(() => {
    if (!selectedField) return []
    return availabilityItems.filter(slot => Number(slot.fieldId) === Number(selectedField.fieldId))
  }, [availabilityItems, selectedField])
  const availableSlots = selectedSlots.filter(slot => slot.available)
  const previewSlots = selectedSlots.slice(0, 5)
  const displayDetail = fieldDetail || selectedField

  function openBooking(slot) {
    setFieldTypeFilter?.('')
    if (slot?.slotId) setSelectedSlotId?.(slot.slotId)
    navigatePage?.('booking')
  }

  return (
    <section id="fields" className="section fieldSection">
      <SectionIntro
        kicker="Fields"
        title="Choose the pitch that fits the match"
        text="Guests can browse active fields, inspect pricing, and check open slots before signing in."
      />

      {!fields.length ? (
        <p className="emptyText">No active fields are available right now.</p>
      ) : (
        <div className="fieldBrowserLayout">
          <div className="fieldGrid fieldRail" aria-label="Active football fields">
            {fields.map(field => {
              const isSelected = Number(field.fieldId) === Number(selectedField?.fieldId)
              const fieldSlots = availabilityItems.filter(slot => Number(slot.fieldId) === Number(field.fieldId))
              const openSlotCount = fieldSlots.filter(slot => slot.available).length

              return (
                <button
                  type="button"
                  className={isSelected ? 'fieldCard fieldCardButton selected' : 'fieldCard fieldCardButton'}
                  key={field.fieldId}
                  onClick={() => setSelectedFieldId(field.fieldId)}
                  aria-pressed={isSelected}
                >
                  <img src={field.imageUrl} alt={`${field.fieldName} football pitch`} />
                  <div className="fieldCardBody">
                    <Group justify="space-between" gap="xs" wrap="nowrap">
                      <Badge color="green" variant={isSelected ? 'filled' : 'light'} radius="sm">{field.fieldType}</Badge>
                      <span className="fieldCapacity">{field.playerCapacity || '-'} players</span>
                    </Group>
                    <div>
                      <h3>{field.fieldName}</h3>
                      <p>{field.description}</p>
                    </div>
                    <dl>
                      <div><dt>Location</dt><dd>{field.location}</dd></div>
                      <div><dt>Surface</dt><dd>{field.surfaceType}</dd></div>
                    </dl>
                    <div className="fieldCardFooter">
                      <span>{availability.loaded ? `${openSlotCount} open slots` : 'Checking slots'}</span>
                      <strong>View details</strong>
                    </div>
                  </div>
                </button>
              )
            })}
          </div>

          <Paper className="fieldDetailPanel" withBorder shadow="sm" radius="md">
            <div className="fieldDetailHero">
              <Image
                className="fieldDetailImage"
                src={displayDetail.imageUrl}
                alt={`${displayDetail.fieldName} detail`}
                fallbackSrc={selectedField?.imageUrl}
              />
              <div className="fieldDetailContent">
                <Group justify="space-between" align="flex-start" gap="md">
                  <div>
                    <Badge color="orange" variant="light" radius="sm">{displayDetail.fieldType}</Badge>
                    <Title order={2}>{displayDetail.fieldName}</Title>
                  </div>
                  <Badge color={displayDetail.status === 'active' ? 'green' : 'gray'} variant="filled" radius="sm">
                    {labelize(displayDetail.status)}
                  </Badge>
                </Group>

                <Text c="dimmed" lh={1.55}>{displayDetail.description}</Text>

                <SimpleGrid cols={{ base: 1, sm: 3 }} spacing="xs" className="detailStatGrid">
                  <div className="detailStat">
                    <MapPin size={17} />
                    <span>Location</span>
                    <strong>{displayDetail.location}</strong>
                  </div>
                  <div className="detailStat">
                    <Ruler size={17} />
                    <span>Surface</span>
                    <strong>{displayDetail.surfaceType}</strong>
                  </div>
                  <div className="detailStat">
                    <UsersRound size={17} />
                    <span>Capacity</span>
                    <strong>{displayDetail.playerCapacity || '-'} players</strong>
                  </div>
                </SimpleGrid>
              </div>
            </div>

            <div className="fieldDetailBody">
              <Group justify="space-between" align="end" className="fieldDetailToolbar">
                <div>
                  <Text fw={850}>Availability</Text>
                  <Text size="sm" c="dimmed">Open slots for the selected playing date.</Text>
                </div>
                <FieldControl label="Date">
                  <input type="date" value={searchDate} onChange={event => setSearchDate?.(event.target.value)} />
                </FieldControl>
              </Group>

              {availability.error && <p className="errorText">{availability.error}</p>}

              <div className="miniSlotList">
                {previewSlots.length ? previewSlots.map(slot => (
                  <button
                    type="button"
                    className={slot.available ? 'miniSlotItem' : 'miniSlotItem unavailable'}
                    key={slot.slotId}
                    onClick={() => openBooking(slot)}
                    disabled={!slot.available}
                  >
                    <Clock3 size={18} />
                    <span>{formatTimeRange(slot.startTime, slot.endTime)}</span>
                    <strong>{formatMoney(slot.price)}</strong>
                    <Badge color={slot.available ? 'green' : 'gray'} variant={slot.available ? 'light' : 'outline'} radius="sm">
                      {slot.available ? 'Available' : labelize(slot.status)}
                    </Badge>
                  </button>
                )) : (
                  <p className="emptyText">No slots found for this field on the selected date.</p>
                )}
              </div>

              <Divider />

              <Stack gap="xs">
                <Group justify="space-between" align="center">
                  <div>
                    <Text fw={850}>Price bands</Text>
                    <Text size="sm" c="dimmed">Prices are loaded from the field pricing rules.</Text>
                  </div>
                  {detailLoading && <Badge color="gray" variant="light">Loading</Badge>}
                </Group>

                {detailError && <p className="errorText">{detailError}</p>}

                <div className="priceRows">
                  {(fieldDetail?.prices || []).length ? fieldDetail.prices.map(price => (
                    <div className="priceRow" key={`${price.dayType}-${price.startTime}-${price.endTime}`}>
                      <span>{labelize(price.dayType)}</span>
                      <strong>{formatTimeRange(price.startTime, price.endTime)}</strong>
                      <em>{formatMoney(price.price)}</em>
                    </div>
                  )) : (
                    <p className="emptyText">Pricing is not configured for this field yet.</p>
                  )}
                </div>
              </Stack>

              <div className="fieldDetailActions">
                <Button color="green" leftSection={<CalendarDays size={18} />} onClick={() => openBooking(availableSlots[0])}>
                  {availableSlots[0] ? 'Book earliest slot' : 'Open booking'}
                </Button>
                <Button variant="subtle" color="dark" onClick={() => openBooking()}>
                  View all slots
                </Button>
              </div>
            </div>
          </Paper>
        </div>
      )}
    </section>
  )
}
