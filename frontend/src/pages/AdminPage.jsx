import { useEffect, useMemo, useState } from 'react'
import { Badge, Button, Group, Modal, Stack, Text } from '@mantine/core'
import { Plus } from 'lucide-react'
import { FieldControl, InfoPanel, WorkspaceHeader } from '../components/common'
import { DataList, MetricGrid } from '../components/data'
import api from '../services/api'
import { formatMoney, formatTimeRange } from '../utils/format'

const emptyFieldForm = {
  fieldTypeId: '',
  fieldName: '',
  description: '',
  imageUrl: '',
  location: '',
  surfaceType: '',
  status: 'active'
}

const emptyPriceForm = {
  dayType: 'weekday',
  startTime: '06:00',
  endTime: '08:00',
  price: '',
  effectiveFrom: '',
  effectiveTo: '',
  status: 'active'
}

const emptyServiceForm = {
  serviceName: '',
  serviceType: 'rental',
  description: '',
  unitName: 'item',
  unitPrice: '',
  stockQuantity: '',
  maxQuantityPerBooking: '',
  status: 'active'
}

const statusColor = status => status === 'active' ? 'green' : 'gray'

export function AdminPage({
  reports,
  settings,
  fieldTypes,
  updateDepositSetting,
  customers,
  updateCustomerRestriction,
  refreshAll
}) {
  const restrictedCustomers = customers.filter(customer => customer.bookingRestricted).length
  const [adminFields, setAdminFields] = useState([])
  const [selectedFieldId, setSelectedFieldId] = useState(null)
  const [fieldPrices, setFieldPrices] = useState([])
  const [fieldForm, setFieldForm] = useState(emptyFieldForm)
  const [priceForm, setPriceForm] = useState(emptyPriceForm)
  const [editingPriceId, setEditingPriceId] = useState(null)
  const [adminServices, setAdminServices] = useState([])
  const [selectedServiceId, setSelectedServiceId] = useState(null)
  const [serviceForm, setServiceForm] = useState(emptyServiceForm)
  const [fieldNotice, setFieldNotice] = useState('')
  const [feedbackModal, setFeedbackModal] = useState({ opened: false, title: '', message: '' })
  const [restrictionModal, setRestrictionModal] = useState({ opened: false, customer: null, reason: '' })

  const selectedField = useMemo(() => (
    selectedFieldId === 'new' ? null : adminFields.find(field => Number(field.fieldId) === Number(selectedFieldId))
  ), [adminFields, selectedFieldId])

  const selectedService = useMemo(() => (
    selectedServiceId === 'new' ? null : adminServices.find(service => Number(service.extraServiceId) === Number(selectedServiceId))
  ), [adminServices, selectedServiceId])

  useEffect(() => {
    loadAdminFields()
    loadAdminServices()
  }, [])

  useEffect(() => {
    if (!adminFields.length) {
      setSelectedFieldId(null)
      setFieldForm(emptyFieldForm)
      return
    }
    if (selectedFieldId === 'new') return
    if (!selectedFieldId || !adminFields.some(field => Number(field.fieldId) === Number(selectedFieldId))) {
      setSelectedFieldId(adminFields[0].fieldId)
    }
  }, [adminFields, selectedFieldId])

  useEffect(() => {
    if (selectedFieldId === 'new' || !selectedField) return
    setFieldForm({
      fieldTypeId: String(selectedField.fieldTypeId || ''),
      fieldName: selectedField.fieldName || '',
      description: selectedField.description || '',
      imageUrl: selectedField.imageUrl || '',
      location: selectedField.location || '',
      surfaceType: selectedField.surfaceType || '',
      status: selectedField.status || 'active'
    })
    loadFieldPrices(selectedField.fieldId)
    setEditingPriceId(null)
    setPriceForm(emptyPriceForm)
  }, [selectedField?.fieldId])

  useEffect(() => {
    if (!adminServices.length) {
      setSelectedServiceId(null)
      setServiceForm(emptyServiceForm)
      return
    }
    if (selectedServiceId === 'new') return
    if (!selectedServiceId || !adminServices.some(service => Number(service.extraServiceId) === Number(selectedServiceId))) {
      setSelectedServiceId(adminServices[0].extraServiceId)
    }
  }, [adminServices, selectedServiceId])

  useEffect(() => {
    if (selectedServiceId === 'new' || !selectedService) return
    setServiceForm({
      serviceName: selectedService.serviceName || '',
      serviceType: selectedService.serviceType || 'rental',
      description: selectedService.description || '',
      unitName: selectedService.unitName || 'item',
      unitPrice: String(selectedService.unitPrice ?? ''),
      stockQuantity: selectedService.stockQuantity == null ? '' : String(selectedService.stockQuantity),
      maxQuantityPerBooking: selectedService.maxQuantityPerBooking == null ? '' : String(selectedService.maxQuantityPerBooking),
      status: selectedService.status || 'active'
    })
  }, [selectedService?.extraServiceId])

  async function loadAdminFields() {
    const response = await api.get('/admin/fields')
    setAdminFields(response.data)
  }

  async function loadFieldPrices(fieldId) {
    const response = await api.get(`/admin/fields/${fieldId}/prices`)
    setFieldPrices(response.data)
  }

  async function loadAdminServices() {
    const response = await api.get('/admin/services')
    setAdminServices(response.data)
  }

  function updateFieldForm(name, value) {
    setFieldForm(form => ({ ...form, [name]: value }))
  }

  function updatePriceForm(name, value) {
    setPriceForm(form => ({ ...form, [name]: value }))
  }

  function updateServiceForm(name, value) {
    setServiceForm(form => ({ ...form, [name]: value }))
  }

  function openFeedbackModal(title, message) {
    setFeedbackModal({ opened: true, title, message })
  }

  function closeFeedbackModal() {
    setFeedbackModal({ opened: false, title: '', message: '' })
  }

  function openRestrictionModal(customer) {
    setRestrictionModal({
      opened: true,
      customer,
      reason: customer.restrictionReason || ''
    })
  }

  function closeRestrictionModal() {
    setRestrictionModal({ opened: false, customer: null, reason: '' })
  }

  async function confirmRestriction() {
    const reason = restrictionModal.reason.trim()
    if (!reason) {
      setFieldNotice('Restriction reason is required.')
      return
    }
    await updateCustomerRestriction(restrictionModal.customer, true, reason)
    closeRestrictionModal()
  }

  function startNewPriceRule() {
    setEditingPriceId(null)
    setPriceForm(emptyPriceForm)
    setFieldNotice('Ready to add a new price rule.')
    
    // Scroll to the pricing form for better UX
    const formElement = document.querySelector('.adminFormGrid.compact')
    if (formElement) {
      formElement.scrollIntoView({ behavior: 'smooth', block: 'center' })
    }
  }

  async function saveField() {
    const payload = {
      ...fieldForm,
      fieldTypeId: Number(fieldForm.fieldTypeId)
    }
    if (!payload.fieldTypeId || !payload.fieldName.trim()) {
      setFieldNotice('Choose a field type and enter a field name.')
      return
    }
    try {
      if (selectedField?.fieldId) {
        await api.put(`/admin/fields/${selectedField.fieldId}`, payload)
        setFieldNotice('Field updated.')
        openFeedbackModal('Field saved', 'Field details were saved successfully.')
      } else {
        const response = await api.post('/admin/fields', payload)
        setSelectedFieldId(response.data.fieldId)
        setFieldNotice('Field created.')
        openFeedbackModal('Field created', 'The new field was added successfully.')
      }
      await loadAdminFields()
      await refreshAll?.()
    } catch (error) {
      setFieldNotice(error.response?.data?.error || 'Could not save field.')
    }
  }

  async function createNewField() {
    setSelectedFieldId('new')
    setFieldPrices([])
    setFieldForm({
      ...emptyFieldForm,
      fieldTypeId: fieldTypes[0]?.fieldTypeId ? String(fieldTypes[0].fieldTypeId) : ''
    })
    setFieldNotice('Creating a new field.')
  }

  async function toggleFieldStatus(field) {
    const nextStatus = field.status === 'active' ? 'inactive' : 'active'
    await api.put(`/admin/fields/${field.fieldId}`, {
      ...field,
      status: nextStatus
    })
    setFieldNotice(nextStatus === 'active' ? 'Field activated.' : 'Field deactivated.')
    await loadAdminFields()
    await refreshAll?.()
  }

  function editPrice(price) {
    setEditingPriceId(price.fieldPriceId)
    setPriceForm({
      dayType: price.dayType,
      startTime: String(price.startTime).slice(0, 5),
      endTime: String(price.endTime).slice(0, 5),
      price: String(price.price),
      effectiveFrom: price.effectiveFrom || '',
      effectiveTo: price.effectiveTo || '',
      status: price.status
    })
  }

  async function savePrice() {
    if (!selectedField?.fieldId) {
      setFieldNotice('Select a field before saving prices.')
      return
    }
    const payload = {
      ...priceForm,
      price: Number(priceForm.price),
      effectiveFrom: priceForm.effectiveFrom || null,
      effectiveTo: priceForm.effectiveTo || null
    }

    // Client-side overlap validation
    const hasOverlap = fieldPrices.some(existing => {
      // Skip the one we are currently editing
      if (editingPriceId && Number(existing.fieldPriceId) === Number(editingPriceId)) return false
      
      // Check dayType compatibility (all overlaps with everything, weekday/weekend must match)
      const dayMismatch = (payload.dayType !== 'all' && existing.dayType !== 'all' && payload.dayType !== existing.dayType)
      if (dayMismatch) return false

      // Check time overlap: (StartA < EndB) and (EndA > StartB)
      const startA = payload.startTime
      const endA = payload.endTime
      const startB = String(existing.startTime).slice(0, 5)
      const endB = String(existing.endTime).slice(0, 5)

      return (startA < endB) && (endA > startB)
    })

    if (hasOverlap) {
      setFieldNotice('Conflict: This time slot overlaps with an existing rule.')
      openFeedbackModal('Conflict detected', 'The selected time and day type overlap with an existing pricing rule. Please adjust the hours.')
      return
    }

    try {
      if (editingPriceId) {
        await api.put(`/admin/field-prices/${editingPriceId}`, payload)
        setFieldNotice('Price rule updated.')
        openFeedbackModal('Price rule saved', 'The pricing rule was updated successfully.')
      } else {
        await api.post(`/admin/fields/${selectedField.fieldId}/prices`, payload)
        setFieldNotice('Price rule created.')
        openFeedbackModal('Price rule created', 'The new pricing rule was added successfully.')
      }
      setEditingPriceId(null)
      setPriceForm(emptyPriceForm)
      await loadFieldPrices(selectedField.fieldId)
      await refreshAll?.()
    } catch (error) {
      setFieldNotice(error.response?.data?.error || 'Could not save price rule.')
    }
  }

  function createNewService() {
    setSelectedServiceId('new')
    setServiceForm(emptyServiceForm)
    setFieldNotice('Creating a new extra service.')
  }

  async function saveService() {
    const payload = {
      ...serviceForm,
      unitPrice: Number(serviceForm.unitPrice),
      stockQuantity: serviceForm.stockQuantity === '' ? null : Number(serviceForm.stockQuantity),
      maxQuantityPerBooking: serviceForm.maxQuantityPerBooking === '' ? null : Number(serviceForm.maxQuantityPerBooking)
    }
    if (!payload.serviceName.trim()) {
      setFieldNotice('Service name is required.')
      return
    }
    try {
      if (selectedService?.extraServiceId) {
        await api.put(`/admin/services/${selectedService.extraServiceId}`, payload)
        openFeedbackModal('Service saved', 'Extra service details were saved successfully.')
      } else {
        const response = await api.post('/admin/services', payload)
        setSelectedServiceId(response.data.extraServiceId)
        openFeedbackModal('Service created', 'The new extra service was added successfully.')
      }
      await loadAdminServices()
      await refreshAll?.()
      setFieldNotice('Extra service saved.')
    } catch (error) {
      setFieldNotice(error.response?.data?.error || 'Could not save extra service.')
    }
  }

  async function toggleServiceStatus(service) {
    const nextStatus = service.status === 'active' ? 'inactive' : 'active'
    await api.put(`/admin/services/${service.extraServiceId}`, {
      ...service,
      status: nextStatus
    })
    setFieldNotice(nextStatus === 'active' ? 'Extra service activated.' : 'Extra service deactivated.')
    await loadAdminServices()
    await refreshAll?.()
  }

  return (
    <section id="admin" className="section adminSection">
      <WorkspaceHeader
        kicker="Admin workspace"
        title="Management and reports"
        text="Review revenue, booking utilization, field setup, pricing, customer access, and lightweight deposit configuration."
        status={{
          label: 'Scope',
          value: 'Demo admin',
          tone: 'success'
        }}
        metrics={[
          { label: 'Revenue', value: formatMoney(reports?.totalRevenue) },
          { label: 'Bookings', value: reports?.bookingCount || 0 },
          { label: 'Fields', value: adminFields.length },
          { label: 'Services', value: adminServices.length }
        ]}
      />

      <div className="roleGrid adminGrid">
        <InfoPanel title="Football fields" className="adminWidePanel">
          <div className="adminPanelHeader">
            <span>{fieldNotice || 'Create, edit, activate, or retire fields.'}</span>
            {selectedFieldId === 'new' && (
              <Button variant="subtle" size="xs" onClick={() => setSelectedFieldId(adminFields[0]?.fieldId || null)}>
                Back to list
              </Button>
            )}
          </div>
          <div className="customerAdminList">
            {adminFields.map(field => (
              <button
                type="button"
                className={Number(field.fieldId) === Number(selectedField?.fieldId) ? 'customerAdminRow selected' : 'customerAdminRow'}
                key={field.fieldId}
                onClick={() => setSelectedFieldId(field.fieldId)}
              >
                <span>
                  <strong>{field.fieldName}</strong>
                  <small>{field.fieldType} - {field.location || 'No location'}</small>
                </span>
                <Badge color={statusColor(field.status)} variant="light">{field.status}</Badge>
              </button>
            ))}
            <button type="button" className="addListButton" onClick={createNewField}>
              <Plus size={18} />
              <span>New football field</span>
            </button>
          </div>
          <div className="adminFormGrid">
            <FieldControl label="Field name">
              <input value={fieldForm.fieldName} onChange={event => updateFieldForm('fieldName', event.target.value)} />
            </FieldControl>
            <FieldControl label="Field type">
              <select value={fieldForm.fieldTypeId} onChange={event => updateFieldForm('fieldTypeId', event.target.value)}>
                <option value="">Choose type</option>
                {fieldTypes.map(type => <option key={type.fieldTypeId} value={type.fieldTypeId}>{type.typeName}</option>)}
              </select>
            </FieldControl>
            <FieldControl label="Location">
              <input value={fieldForm.location} onChange={event => updateFieldForm('location', event.target.value)} />
            </FieldControl>
            <FieldControl label="Surface">
              <input value={fieldForm.surfaceType} onChange={event => updateFieldForm('surfaceType', event.target.value)} />
            </FieldControl>
            <FieldControl label="Image URL">
              <input value={fieldForm.imageUrl} onChange={event => updateFieldForm('imageUrl', event.target.value)} />
            </FieldControl>
            <FieldControl label="Status">
              <select value={fieldForm.status} onChange={event => updateFieldForm('status', event.target.value)}>
                <option value="active">Active</option>
                <option value="inactive">Inactive</option>
              </select>
            </FieldControl>
            <FieldControl label="Description">
              <textarea value={fieldForm.description} onChange={event => updateFieldForm('description', event.target.value)} />
            </FieldControl>
          </div>
          <div className="buttonRow noMargin">
            <Button color="green" onClick={saveField}>{selectedField?.fieldId ? 'Save field' : 'Create field'}</Button>
            {selectedField?.fieldId && (
              <Button variant="light" color={selectedField.status === 'active' ? 'red' : 'green'} onClick={() => toggleFieldStatus(selectedField)}>
                {selectedField.status === 'active' ? 'Deactivate' : 'Activate'}
              </Button>
            )}
          </div>
        </InfoPanel>

        <InfoPanel title="Field pricing" className="adminWidePanel">
          <div className="adminPanelHeader">
            <span>{selectedField ? `Pricing rules for ${selectedField.fieldName}` : 'Select a field first.'}</span>
            {editingPriceId && <Button variant="subtle" onClick={startNewPriceRule}>Cancel edit</Button>}
          </div>
          <div className="priceRows">
            {fieldPrices.length ? fieldPrices.map(price => (
              <button type="button" className="priceRow adminPriceRow" key={price.fieldPriceId} onClick={() => editPrice(price)}>
                <span className="priceRowLabel">{price.dayType}</span>
                <strong className="priceRowRange">{formatTimeRange(price.startTime, price.endTime)}</strong>
                <em className="priceRowAmount">{formatMoney(price.price)}</em>
              </button>
            )) : (
              <p className="emptyText">No pricing rules yet.</p>
            )}
          </div>
          <div className="adminFormGrid compact">
            <FieldControl label="Day type">
              <select value={priceForm.dayType} onChange={event => updatePriceForm('dayType', event.target.value)}>
                <option value="weekday">Weekday</option>
                <option value="weekend">Weekend</option>
                <option value="all">All days</option>
              </select>
            </FieldControl>
            <FieldControl label="Start">
              <input type="time" value={priceForm.startTime} onChange={event => updatePriceForm('startTime', event.target.value)} />
            </FieldControl>
            <FieldControl label="End">
              <input type="time" value={priceForm.endTime} onChange={event => updatePriceForm('endTime', event.target.value)} />
            </FieldControl>
            <FieldControl label="Price">
              <input type="number" min="0" step="10000" value={priceForm.price} onChange={event => updatePriceForm('price', event.target.value)} />
            </FieldControl>
            <FieldControl label="Effective from">
              <input type="date" value={priceForm.effectiveFrom} onChange={event => updatePriceForm('effectiveFrom', event.target.value)} />
            </FieldControl>
            <FieldControl label="Effective to">
              <input type="date" value={priceForm.effectiveTo} onChange={event => updatePriceForm('effectiveTo', event.target.value)} />
            </FieldControl>
            <FieldControl label="Status">
              <select value={priceForm.status} onChange={event => updatePriceForm('status', event.target.value)}>
                <option value="active">Active</option>
                <option value="inactive">Inactive</option>
              </select>
            </FieldControl>
          </div>
          <Button color="green" onClick={savePrice} disabled={!selectedField?.fieldId}>
            {editingPriceId ? 'Save price rule' : 'Add price rule'}
          </Button>
        </InfoPanel>

        <InfoPanel title="Extra services" className="adminWidePanel">
          <div className="adminPanelHeader">
            <span>Manage rental, sale, and staff-supported services used during booking.</span>
            {selectedServiceId === 'new' && (
              <Button variant="subtle" size="xs" onClick={() => setSelectedServiceId(adminServices[0]?.extraServiceId || null)}>
                Back to list
              </Button>
            )}
          </div>
          <div className="adminManagerLayout">
            <div className="customerAdminList">
              {adminServices.map(service => (
                <button
                  type="button"
                  className={Number(service.extraServiceId) === Number(selectedService?.extraServiceId) ? 'customerAdminRow selected' : 'customerAdminRow'}
                  key={service.extraServiceId}
                  onClick={() => setSelectedServiceId(service.extraServiceId)}
                >
                  <span>
                    <strong>{service.serviceName}</strong>
                    <small>{service.serviceType?.replace(/_/g, ' ')} - {formatMoney(service.unitPrice)} / {service.unitName}</small>
                  </span>
                  <Badge color={statusColor(service.status)} variant="light">{service.status}</Badge>
                </button>
              ))}
              <button type="button" className="addListButton" onClick={createNewService}>
                <Plus size={18} />
                <span>New extra service</span>
              </button>
            </div>
            <div>
              <div className="adminFormGrid">
                <FieldControl label="Service name">
                  <input value={serviceForm.serviceName} onChange={event => updateServiceForm('serviceName', event.target.value)} />
                </FieldControl>
                <FieldControl label="Service type">
                  <select value={serviceForm.serviceType} onChange={event => updateServiceForm('serviceType', event.target.value)}>
                    <option value="rental">Rental</option>
                    <option value="sale">Sale</option>
                    <option value="staff_service">Staff service</option>
                  </select>
                </FieldControl>
                <FieldControl label="Unit">
                  <input value={serviceForm.unitName} onChange={event => updateServiceForm('unitName', event.target.value)} />
                </FieldControl>
                <FieldControl label="Unit price">
                  <input type="number" min="0" step="10000" value={serviceForm.unitPrice} onChange={event => updateServiceForm('unitPrice', event.target.value)} />
                </FieldControl>
                <FieldControl label="Stock quantity">
                  <input type="number" min="0" value={serviceForm.stockQuantity} onChange={event => updateServiceForm('stockQuantity', event.target.value)} />
                </FieldControl>
                <FieldControl label="Max per booking">
                  <input type="number" min="1" value={serviceForm.maxQuantityPerBooking} onChange={event => updateServiceForm('maxQuantityPerBooking', event.target.value)} />
                </FieldControl>
                <FieldControl label="Status">
                  <select value={serviceForm.status} onChange={event => updateServiceForm('status', event.target.value)}>
                    <option value="active">Active</option>
                    <option value="inactive">Inactive</option>
                  </select>
                </FieldControl>
                <FieldControl label="Description">
                  <textarea value={serviceForm.description} onChange={event => updateServiceForm('description', event.target.value)} />
                </FieldControl>
              </div>
              <div className="buttonRow noMargin">
                <Button color="green" onClick={saveService}>{selectedService?.extraServiceId ? 'Save service' : 'Create service'}</Button>
                {selectedService?.extraServiceId && (
                  <Button variant="light" color={selectedService.status === 'active' ? 'red' : 'green'} onClick={() => toggleServiceStatus(selectedService)}>
                    {selectedService.status === 'active' ? 'Deactivate' : 'Activate'}
                  </Button>
                )}
              </div>
            </div>
          </div>
        </InfoPanel>

        <InfoPanel title="Revenue report">
          <MetricGrid metrics={[
            ['Revenue', formatMoney(reports?.totalRevenue)],
            ['Bookings', reports?.bookingCount || 0],
            ['Completed', reports?.completedCount || 0],
            ['Cancelled', reports?.cancelledCount || 0]
          ]} />
        </InfoPanel>

        <InfoPanel title="Field utilization">
          <DataList items={Object.entries(reports?.fieldUtilization || {}).map(([field, count]) => ({
            title: field,
            meta: 'Bookings',
            value: count
          }))} />
        </InfoPanel>

        <InfoPanel title="Deposit rule">
          <DataList items={settings.map(setting => ({
            title: setting.settingKey,
            meta: setting.description,
            value: setting.settingValue
          }))} />
          <div className="buttonRow">
            <Button variant="light" onClick={() => updateDepositSetting(30)}>Set 30%</Button>
            <Button variant="light" onClick={() => updateDepositSetting(50)}>Set 50%</Button>
          </div>
        </InfoPanel>

        <InfoPanel title="Customer booking access">
          <div className="customerAdminList">
            {customers.map(customer => (
              <div className="customerAdminRow" key={customer.userId}>
                <span>
                  <strong>{customer.fullName}</strong>
                  <small>{customer.email} - {customer.phone || 'no phone'}</small>
                  {customer.bookingRestricted && <small>{customer.restrictionReason || 'Booking restricted'}</small>}
                </span>
                <Button
                  variant={customer.bookingRestricted ? 'filled' : 'light'}
                  color={customer.bookingRestricted ? 'green' : 'red'}
                  onClick={() => customer.bookingRestricted
                    ? updateCustomerRestriction(customer, false)
                    : openRestrictionModal(customer)}
                >
                  {customer.bookingRestricted ? 'Restore' : 'Restrict'}
                </Button>
              </div>
            ))}
          </div>
        </InfoPanel>
      </div>

      <Modal
        opened={feedbackModal.opened}
        onClose={closeFeedbackModal}
        centered
        title={feedbackModal.title || 'Status'}
      >
        <Stack gap="sm">
          <Text size="sm" c="dimmed">
            {feedbackModal.message}
          </Text>
          <Button onClick={closeFeedbackModal}>OK</Button>
        </Stack>
      </Modal>

      <Modal
        opened={restrictionModal.opened}
        onClose={closeRestrictionModal}
        centered
        title={`Restrict ${restrictionModal.customer?.fullName || 'customer'}`}
      >
        <Stack gap="sm">
          <Text size="sm" c="dimmed">
            The customer will be blocked from signing in. They will receive an email with this reason.
          </Text>
          <FieldControl label="Restriction reason">
            <textarea
              value={restrictionModal.reason}
              onChange={event => setRestrictionModal(modal => ({ ...modal, reason: event.target.value }))}
              placeholder="Example: Repeated no-shows and unpaid booking balance."
            />
          </FieldControl>
          <Group justify="flex-end">
            <Button variant="light" color="gray" onClick={closeRestrictionModal}>Cancel</Button>
            <Button color="red" onClick={confirmRestriction}>Restrict customer</Button>
          </Group>
        </Stack>
      </Modal>
    </section>
  )
}
