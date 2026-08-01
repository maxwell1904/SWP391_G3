import { useEffect, useMemo, useState } from 'react'
import { Badge, Button, Group, Image, Modal, Paper, SimpleGrid, Stack, Text, Title } from '@mantine/core'
import { CalendarDays, MapPin, Ruler, UsersRound, Wrench } from 'lucide-react'
import { SectionIntro } from '../components/common'
import api from '../services/api'
import { formatMoney, formatTimeRange, resolveAssetUrl } from '../utils/format'

const labelize = value => String(value || '').replace(/_/g, ' ').toLowerCase()

export function FieldsPage({
  fields,
  currentUser,
  setFieldTypeFilter,
  setFieldFilter,
  setSelectedSlotId,
  navigatePage
}) {
  const [selectedFieldId, setSelectedFieldId] = useState(null)
  const [fieldDetail, setFieldDetail] = useState(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [detailError, setDetailError] = useState('')
  const [detailOpen, setDetailOpen] = useState(false)

  useEffect(() => {
    if (!fields.length || !fields.some(field => Number(field.fieldId) === Number(selectedFieldId))) {
      setSelectedFieldId(null)
      setDetailOpen(false)
    }
  }, [fields, selectedFieldId])

  const selectedField = useMemo(() => {
    if (!fields.length) return null
    return fields.find(field => Number(field.fieldId) === Number(selectedFieldId)) || null
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

  const displayDetail = fieldDetail || selectedField
  const priceRange = fieldDetail?.priceRange
  const detailServices = fieldDetail?.services || []

  function openBooking() {
    if (currentUser?.role === 'Admin') {
      setDetailOpen(false)
      navigatePage?.('admin')
      return
    }
    setFieldTypeFilter?.(String(selectedField?.fieldTypeId || ''))
    setFieldFilter?.(String(selectedField?.fieldId || ''))
    setSelectedSlotId?.(null)
    setDetailOpen(false)
    navigatePage?.('booking')
  }

  function openFieldDetail(field) {
    setSelectedFieldId(field.fieldId)
    setDetailOpen(true)
  }

  return (
    <section id="fields" className="section fieldSection">
      <SectionIntro
        kicker="Fields"
        title="Choose the pitch that fits the match"
        text="Compare field size, surface, pricing, and optional match-day services before checking availability."
      />

      {!fields.length ? (
        <p className="emptyText">No active fields are available right now.</p>
      ) : (
        <>
          <div className="fieldGrid fieldGridCards" aria-label="Active football fields">
            {fields.map(field => {
              const isSelected = detailOpen && Number(field.fieldId) === Number(selectedField?.fieldId)
              return (
                <button
                  type="button"
                  className={isSelected ? 'fieldCard fieldCardButton selected' : 'fieldCard fieldCardButton'}
                  key={field.fieldId}
                  onClick={() => openFieldDetail(field)}
                  aria-pressed={isSelected}
                >
                  <img src={resolveAssetUrl(field.imageUrl)} alt={`${field.fieldName} football pitch`} width="640" height="360" loading="lazy" />
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
                      <span>Pricing & amenities</span>
                      <strong>View details</strong>
                    </div>
                  </div>
                </button>
              )
            })}
          </div>

          <Modal
            opened={detailOpen && Boolean(displayDetail)}
            onClose={() => setDetailOpen(false)}
            size="xl"
            centered
            title="Field details"
            closeButtonProps={{ 'aria-label': 'Close field details' }}
          >
          {displayDetail && (
          <Paper className="fieldDetailPanel fieldDetailModal" withBorder shadow="sm" radius="md">
            <div className="fieldDetailHero">
              <Image
                className="fieldDetailImage"
                src={resolveAssetUrl(displayDetail.imageUrl)}
                alt={`${displayDetail.fieldName} detail`}
                fallbackSrc={resolveAssetUrl(selectedField?.imageUrl)}
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

                <SimpleGrid cols={2} spacing="xs" className="detailStatGrid">
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
                  <div className="detailStat">
                    <CalendarDays size={17} />
                    <span>Price range</span>
                    <strong>
                      {priceRange ? `${formatMoney(priceRange.min)} - ${formatMoney(priceRange.max)}` : 'Loading'}
                    </strong>
                  </div>
                </SimpleGrid>
              </div>
            </div>

            <div className="fieldDetailBody">
              <div className="fieldDetailOverviewGrid">
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
              <Stack gap="xs">
                <Group justify="space-between" align="center">
                  <div>
                    <Text fw={850}>Available add-ons</Text>
                    <Text size="sm" c="dimmed">Optional equipment and match-day services.</Text>
                  </div>
                  <Badge color="green" variant="light">{detailServices.length} services</Badge>
                </Group>
                <div className="fieldServiceList">
                  {detailServices.length ? detailServices.map(service => (
                    <div className="fieldServiceItem" key={service.extraServiceId}>
                      <Wrench size={17} />
                      <span>
                        <strong>{service.serviceName}</strong>
                        <small>{service.serviceType?.replace(/_/g, ' ')} · {formatMoney(service.unitPrice)} / {service.unitName}</small>
                      </span>
                    </div>
                  )) : (
                    <p className="emptyText">No active services are configured.</p>
                  )}
                </div>
              </Stack>
              </div>

              <div className="fieldDetailActions">
                <Button color="green" leftSection={<CalendarDays size={18} />} onClick={openBooking}>
                  {currentUser?.role === 'Admin' ? 'Manage field' : 'Check availability'}
                </Button>
                <Text size="sm" c="dimmed">
                  {currentUser?.role === 'Admin' ? 'Open field setup and pricing.' : 'Choose a date and time on the booking page.'}
                </Text>
              </div>
            </div>
          </Paper>
          )}
          </Modal>
        </>
      )}
    </section>
  )
}
