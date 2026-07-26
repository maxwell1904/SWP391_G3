import { useEffect, useMemo, useState } from 'react'
import { Badge, Button, Group, Modal, Stack, Text } from '@mantine/core'
import { Plus } from 'lucide-react'
import { FieldControl, ImageUploadField, InfoPanel, WorkspaceHeader, WorkspaceTabs } from '../components/common'
import { DataList, MetricGrid } from '../components/data'
import { BillingDetails } from '../features/payments/components'
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

const emptyStaffForm = {
  fullName: '',
  email: '',
  phone: '',
  status: 'active'
}

const statusColor = status => status === 'active' ? 'green' : status === 'locked' ? 'red' : 'gray'

export function AdminPage({
  reports,
  bookings = [],
  payments = [],
  refunds = [],
  issues = [],
  settings,
  fieldTypes,
  updateDepositSetting,
  updatePolicySetting,
  customers,
  updateCustomerLock,
  refreshAll,
  navigatePage,
  loadReports
}) {
  const lockedCustomers = customers.filter(customer => customer.accountLocked).length
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
  const [lockModal, setLockModal] = useState({ opened: false, customer: null, reason: '' })
  const [staffAccounts, setStaffAccounts] = useState([])
  const [selectedStaffId, setSelectedStaffId] = useState('new')
  const [staffForm, setStaffForm] = useState(emptyStaffForm)
  const [staffErrors, setStaffErrors] = useState({})
  const [staffSaving, setStaffSaving] = useState(false)
  const [customerActivity, setCustomerActivity] = useState(null)
  const [policyValues, setPolicyValues] = useState({})
  const [activePanel, setActivePanel] = useState('overview')
  const [reportRange, setReportRange] = useState({ from: '', to: '' })
  const [selectedAuditBookingId, setSelectedAuditBookingId] = useState(null)
  const [auditBookingDetail, setAuditBookingDetail] = useState(null)
  const [auditBookingLoading, setAuditBookingLoading] = useState(false)
  const [auditBookingError, setAuditBookingError] = useState('')

  const panelClass = panel => activePanel === panel ? '' : 'workspacePanelHidden'

  useEffect(() => {
    setPolicyValues(Object.fromEntries(settings.map(setting => [setting.settingKey, setting.settingValue])))
  }, [settings])
  const slotGenerationSettings = settings.filter(setting => setting.settingKey.startsWith('slot.'))
  const bookingPolicySettings = settings.filter(setting => !setting.settingKey.startsWith('slot.'))

  function settingInputProps(key) {
    if (key === 'slot.opening_time' || key === 'slot.closing_time') return { type: 'time' }
    if (key === 'slot.duration_minutes') return { type: 'number', min: 30, max: 360, step: 30 }
    if (key === 'slot.generation_horizon_days') return { type: 'number', min: 1, max: 90, step: 1 }
    return { type: 'number', min: 0 }
  }

  const selectedField = useMemo(() => (
    selectedFieldId === 'new' ? null : adminFields.find(field => Number(field.fieldId) === Number(selectedFieldId))
  ), [adminFields, selectedFieldId])

  const selectedService = useMemo(() => (
    selectedServiceId === 'new' ? null : adminServices.find(service => Number(service.extraServiceId) === Number(selectedServiceId))
  ), [adminServices, selectedServiceId])

  useEffect(() => {
    loadAdminFields()
    loadAdminServices()
    loadStaff()
  }, [])

  useEffect(() => {
    setStaffErrors({})
    const staff = staffAccounts.find(account => Number(account.userId) === Number(selectedStaffId))
    setStaffForm(staff ? {
      fullName: staff.fullName || '', email: staff.email || '', phone: staff.phone || '', status: staff.status || 'active'
    } : emptyStaffForm)
  }, [selectedStaffId, staffAccounts])

  useEffect(() => {
    if (!bookings.length) {
      setSelectedAuditBookingId(null)
      setAuditBookingDetail(null)
      return
    }
    setSelectedAuditBookingId(current => bookings.some(booking => booking.bookingId === Number(current))
      ? current
      : bookings[0].bookingId)
  }, [bookings])

  useEffect(() => {
    if (!selectedAuditBookingId) return undefined
    let cancelled = false
    setAuditBookingLoading(true)
    setAuditBookingError('')
    api.get(`/bookings/${selectedAuditBookingId}`)
      .then(response => { if (!cancelled) setAuditBookingDetail(response.data) })
      .catch(error => {
        if (!cancelled) {
          setAuditBookingDetail(null)
          setAuditBookingError(error.response?.data?.error || 'Could not load booking invoice details.')
        }
      })
      .finally(() => { if (!cancelled) setAuditBookingLoading(false) })
    return () => { cancelled = true }
  }, [selectedAuditBookingId])

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

  async function loadStaff() {
    const response = await api.get('/account/staff')
    setStaffAccounts(response.data)
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

  function openLockModal(customer) {
    setLockModal({
      opened: true,
      customer,
      reason: customer.lockReason || ''
    })
  }

  function closeLockModal() {
    setLockModal({ opened: false, customer: null, reason: '' })
  }

  async function confirmLock() {
    const reason = lockModal.reason.trim()
    if (!reason) {
      setFieldNotice('Lock reason is required.')
      return
    }
    await updateCustomerLock(lockModal.customer, true, reason)
    closeLockModal()
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

  async function saveStaff() {
    const errors = {}
    if (!staffForm.fullName.trim()) errors.fullName = 'Full name is required.'
    if (selectedStaffId === 'new') {
      if (!staffForm.email.trim()) errors.email = 'Email is required.'
      else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(staffForm.email.trim())) errors.email = 'Email format is invalid.'
    }
    if (!staffForm.phone.trim()) errors.phone = 'Phone is required.'
    else if (!/^0\d{9}$/.test(staffForm.phone.trim())) errors.phone = 'Phone must be 10 digits and start with 0.'
    if (Object.keys(errors).length) {
      setStaffErrors(errors)
      return
    }
    setStaffErrors({})
    setStaffSaving(true)
    try {
      const staff = staffAccounts.find(account => Number(account.userId) === Number(selectedStaffId))
      const response = staff
        ? await api.put(`/account/staff/${staff.userId}`, staffForm)
        : await api.post('/account/staff', staffForm)
      await loadStaff()
      await refreshAll?.()
      setFieldNotice(staff ? 'Staff account updated.' : (response.data.message || 'Staff invitation sent.'))
    } catch (error) {
      const message = error.response?.data?.error || 'Could not save staff account.'
      const fieldErrors = {}
      if (/email/i.test(message) && /exists/i.test(message)) fieldErrors.email = message
      else if (/phone/i.test(message) && /exists/i.test(message)) fieldErrors.phone = message
      if (Object.keys(fieldErrors).length) {
        setStaffErrors(fieldErrors)
      } else {
        setFieldNotice(message)
      }
    } finally {
      setStaffSaving(false)
    }
  }

  async function viewCustomerActivity(customer) {
    try {
      const response = await api.get(`/account/users/${customer.userId}/activity`)
      setCustomerActivity(response.data)
    } catch (error) {
      openFeedbackModal('Could not load customer', error.response?.data?.error || 'Could not load customer activity.')
    }
  }

  return (
    <section id="admin" className="section adminSection">
      <WorkspaceHeader
        kicker="Admin workspace"
        title="Management and reports"
        text="Review revenue, booking utilization, field setup, pricing, customer access, and booking/refund policies."
        status={{
          label: 'Scope',
          value: 'Operations',
          tone: 'success'
        }}
        metrics={[
          { label: 'Revenue', value: formatMoney(reports?.totalRevenue) },
          { label: 'Bookings', value: reports?.bookingCount || 0 },
          { label: 'Fields', value: adminFields.length },
          { label: 'Services', value: adminServices.length }
        ]}
      />

      <Group className="adminDestinationBar" gap="sm">
        <Button variant="light" onClick={() => navigatePage('promotions')}>Manage promotions</Button>
        <Button variant="light" onClick={() => navigatePage('membership-rules')}>Manage membership tiers</Button>
      </Group>

      <InfoPanel title="Report date range" className={`adminWidePanel ${panelClass('overview')}`}>
        <div className="buttonRow reportRangeControls">
          <FieldControl label="From"><input type="date" value={reportRange.from} onChange={e => setReportRange(range => ({ ...range, from: e.target.value }))} /></FieldControl>
          <FieldControl label="To"><input type="date" value={reportRange.to} onChange={e => setReportRange(range => ({ ...range, to: e.target.value }))} /></FieldControl>
          <Button onClick={() => loadReports?.(reportRange.from, reportRange.to)}>Apply range</Button>
          <Button variant="light" onClick={() => { setReportRange({ from: '', to: '' }); loadReports?.('', '') }}>All time</Button>
        </div>
      </InfoPanel>

      <WorkspaceTabs
        value={activePanel}
        onChange={setActivePanel}
        ariaLabel="Admin workspace sections"
        items={[
          { value: 'overview', label: 'Overview' },
          { value: 'venues', label: 'Fields & pricing' },
          { value: 'services', label: 'Services' },
          { value: 'access', label: 'People & access' },
          { value: 'operations', label: 'Bookings & billing' },
          { value: 'support', label: 'Issue audit' },
          { value: 'policies', label: 'Policies' }
        ]}
      />

      <div className="roleGrid adminGrid">
        <InfoPanel title="Football fields" className={`adminWidePanel ${panelClass('venues')}`}>
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
            <ImageUploadField label="Field image" value={fieldForm.imageUrl} onChange={value => updateFieldForm('imageUrl', value)} />
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

        <InfoPanel title="Field pricing" className={`adminWidePanel ${panelClass('venues')}`}>
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
              <input type="number" min="0" step="0.01" value={priceForm.price} onChange={event => updatePriceForm('price', event.target.value)} />
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

        <InfoPanel title="Extra services" className={`adminWidePanel ${panelClass('services')}`}>
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
                  <input type="number" min="0" step="0.01" value={serviceForm.unitPrice} onChange={event => updateServiceForm('unitPrice', event.target.value)} />
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

        <InfoPanel title="Revenue report" className={`adminWidePanel adminOverviewPrimary ${panelClass('overview')}`}>
          <MetricGrid metrics={[
            ['Net revenue', formatMoney(reports?.totalRevenue)],
            ['Gross collected', formatMoney(reports?.grossRevenue)],
            ['Refunded', formatMoney(reports?.refundTotal)],
            ['PayPal fees', formatMoney(reports?.providerFeeTotal)],
            ['Bookings', reports?.bookingCount || 0],
            ['Completed', reports?.completedCount || 0],
            ['Cancelled', reports?.cancelledCount || 0]
          ]} />
          <DataList items={[
            ['Field value', formatMoney(reports?.fieldRevenue)],
            ['Service value', formatMoney(reports?.serviceRevenue)],
            ['Promotion discounts', formatMoney(reports?.promotionDiscountTotal)],
            ['Membership discounts', formatMoney(reports?.membershipDiscountTotal)]
          ].map(([title, value]) => ({ title, meta: 'Booking portfolio', value }))} />
          {Number(reports?.providerFeeUntrackedCount || 0) > 0 && (
            <p className="panelHint">{reports.providerFeeUntrackedCount} legacy PayPal payment(s) do not have an exact processor-fee breakdown yet.</p>
          )}
        </InfoPanel>

        <InfoPanel title="Booking & invoice register" className={`adminWidePanel ${panelClass('operations')}`}>
          <p className="panelHint">Select a booking to inspect the full invoice, payment transactions, and refund trail.</p>
          <div className="customerAdminList adminAuditList">
            {bookings.map(booking => (
              <button
                type="button"
                className={Number(selectedAuditBookingId) === Number(booking.bookingId) ? 'customerAdminRow selected' : 'customerAdminRow'}
                key={booking.bookingId}
                onClick={() => setSelectedAuditBookingId(booking.bookingId)}
              >
                <span>
                  <strong>{booking.bookingCode} · {booking.customer}</strong>
                  <small>{booking.fieldName} · {booking.slotDate} · {String(booking.startTime).slice(0, 5)}</small>
                </span>
                <span>
                  <Badge color={booking.status === 'completed' ? 'green' : booking.status === 'cancelled' ? 'red' : 'blue'} variant="light">{booking.status}</Badge>
                  <small>{formatMoney(booking.totalAmount)}</small>
                </span>
              </button>
            ))}
          </div>
        </InfoPanel>

        <InfoPanel title="Selected invoice & transactions" className={`adminWidePanel ${panelClass('operations')}`}>
          <BillingDetails detail={auditBookingDetail} loading={auditBookingLoading} error={auditBookingError} />
        </InfoPanel>

        <InfoPanel title="Payment & refund control totals" className={`adminWidePanel ${panelClass('operations')}`}>
          <MetricGrid metrics={[
            ['Payment records', payments.length],
            ['Refund cases', refunds.length],
            ['Pending review', refunds.filter(refund => refund.status === 'requested').length],
            ['Processing', refunds.filter(refund => ['approved', 'processing'].includes(refund.status)).length]
          ]} />
          <DataList items={refunds.slice(0, 10).map(refund => ({
            title: `${refund.refundCode} · ${refund.bookingCode}`,
            meta: refund.gatewayMessage || refund.refundReason || 'Refund case',
            value: `${refund.status} · ${formatMoney(refund.refundAmount)}`
          }))} />
        </InfoPanel>

        <InfoPanel title="Issue history" className={`adminWidePanel ${panelClass('support')}`}>
          <p className="panelHint">This is the complete audit trail, including issues already resolved by staff.</p>
          <DataList items={issues.map(issue => ({
            title: issue.title,
            meta: `${issue.reporter} · ${issue.bookingCode || issue.fieldName || issue.extraServiceName || 'General'}${issue.assignedStaff ? ` · ${issue.assignedStaff}` : ''}${issue.resolutionNote ? ` · ${issue.resolutionNote}` : ''}`,
            value: issue.status
          }))} />
        </InfoPanel>

        <InfoPanel title="Field utilization" className={`adminOverviewSecondary ${panelClass('overview')}`}>
          <DataList items={Object.entries(reports?.fieldUtilization || {}).map(([field, count]) => ({
            title: field,
            meta: 'Bookings',
            value: count
          }))} />
        </InfoPanel>

        <InfoPanel title="Booking trends" className={`adminOverviewSecondary ${panelClass('overview')}`}>
          <DataList items={Object.entries(reports?.bookingStatusCounts || {}).map(([status, count]) => ({
            title: status.replace(/_/g, ' '),
            meta: 'Bookings by lifecycle status',
            value: count
          }))} />
          <DataList items={Object.entries(reports?.peakSlots || {}).map(([time, count]) => ({
            title: `${time} start`,
            meta: 'Peak start-time demand',
            value: count
          }))} />
        </InfoPanel>

        <InfoPanel title="Customer activity" className={`adminWidePanel ${panelClass('overview')}`}>
          <MetricGrid metrics={[
            ['Returning customers', reports?.returningCustomerCount || 0],
            ['Tracked customers', (reports?.topCustomers || []).length]
          ]} />
          <DataList items={(reports?.topCustomers || []).slice(0, 5).map(customer => ({
            title: customer.fullName,
            meta: 'Bookings made',
            value: customer.bookingCount
          }))} />
          <DataList items={Object.entries(reports?.membershipDistribution || {}).map(([level, count]) => ({
            title: level,
            meta: 'Membership distribution',
            value: count
          }))} />
        </InfoPanel>

        <InfoPanel title="Booking, cancellation and notification policies" className={`adminWidePanel ${panelClass('policies')}`}>
          <div className="buttonRow">
            <Button variant="light" onClick={() => updateDepositSetting(30)}>Set 30%</Button>
            <Button variant="light" onClick={() => updateDepositSetting(50)}>Set 50%</Button>
          </div>
          <div className="policyFormGrid">
            {bookingPolicySettings.map(setting => (
              <div className="policyEditor" key={setting.settingKey}>
                <strong>{setting.settingKey}</strong>
                <FieldControl label={setting.description || setting.settingKey}>
                  <div className="policyInputRow">
                    <input {...settingInputProps(setting.settingKey)} value={policyValues[setting.settingKey] ?? ''} onChange={event => setPolicyValues(values => ({ ...values, [setting.settingKey]: event.target.value }))} />
                    <Button size="xs" onClick={() => updatePolicySetting(setting.settingKey, policyValues[setting.settingKey])}>Save</Button>
                  </div>
                </FieldControl>
              </div>
            ))}
          </div>
        </InfoPanel>

        <InfoPanel title="Automatic slot generation" className={`adminWidePanel ${panelClass('policies')}`}>
          <p className="panelHint">GoalZone materializes a rolling booking calendar from these rules. Staff Block/Unblock remains available for maintenance, private events, and other exceptions.</p>
          <div className="policyFormGrid">
            {slotGenerationSettings.map(setting => (
              <div className="policyEditor" key={setting.settingKey}>
                <strong>{setting.settingKey}</strong>
                <FieldControl label={setting.description || setting.settingKey}>
                  <div className="policyInputRow">
                    <input {...settingInputProps(setting.settingKey)} value={policyValues[setting.settingKey] ?? ''} onChange={event => setPolicyValues(values => ({ ...values, [setting.settingKey]: event.target.value }))} />
                    <Button size="xs" onClick={() => updatePolicySetting(setting.settingKey, policyValues[setting.settingKey])}>Save</Button>
                  </div>
                </FieldControl>
              </div>
            ))}
          </div>
        </InfoPanel>

        <InfoPanel title="Customer accounts" className={panelClass('access')}>
          <div className="adminPanelHeader">
            <span>{lockedCustomers ? `${lockedCustomers} customer account(s) locked.` : 'All customer accounts can sign in.'}</span>
          </div>
          <div className="customerAdminList">
            {customers.map(customer => (
              <div className="customerAdminRow" key={customer.userId}>
                <span>
                  <strong>{customer.fullName}</strong>
                  <small>{customer.email}</small>
                  <small>{customer.phone || 'no phone'}</small>
                  {customer.accountLocked && <small>{customer.lockReason || 'Account locked'}</small>}
                </span>
                <Group gap="xs" wrap="nowrap">
                  <Button size="xs" variant="light" color="gray" onClick={() => viewCustomerActivity(customer)}>
                    View
                  </Button>
                  <Button
                    size="xs"
                    variant={customer.accountLocked ? 'filled' : 'light'}
                    color={customer.accountLocked ? 'green' : 'red'}
                    onClick={() => customer.accountLocked
                      ? updateCustomerLock(customer, false)
                      : openLockModal(customer)}
                  >
                    {customer.accountLocked ? 'Unlock' : 'Lock'}
                  </Button>
                </Group>
              </div>
            ))}
          </div>
        </InfoPanel>

        <InfoPanel title="Staff accounts" className={panelClass('access')}>
          <div className="customerAdminList">
            <button type="button" className={selectedStaffId === 'new' ? 'customerAdminRow selected' : 'customerAdminRow'} onClick={() => setSelectedStaffId('new')}>
              <span><strong>New staff account</strong><small>Send an invitation so the staff member sets their own password.</small></span>
            </button>
            {staffAccounts.map(account => (
              <button type="button" className={Number(selectedStaffId) === Number(account.userId) ? 'customerAdminRow selected' : 'customerAdminRow'} key={account.userId} onClick={() => setSelectedStaffId(account.userId)}>
                <span><strong>{account.fullName}</strong><small>{account.email} · {account.phone}</small></span>
                <Badge color={statusColor(account.status)} variant="light">{account.status}</Badge>
              </button>
            ))}
          </div>
          <div className="adminFormGrid compact">
            <FieldControl label="Full name" error={staffErrors.fullName}><input value={staffForm.fullName} onChange={event => { setStaffErrors(errors => ({ ...errors, fullName: '' })); setStaffForm(form => ({ ...form, fullName: event.target.value })) }} /></FieldControl>
            <FieldControl label="Email" error={staffErrors.email}><input type="email" value={staffForm.email} onChange={event => { setStaffErrors(errors => ({ ...errors, email: '' })); setStaffForm(form => ({ ...form, email: event.target.value })) }} disabled={selectedStaffId !== 'new'} /></FieldControl>
            <FieldControl label="Phone" error={staffErrors.phone}><input value={staffForm.phone} onChange={event => { setStaffErrors(errors => ({ ...errors, phone: '' })); setStaffForm(form => ({ ...form, phone: event.target.value })) }} /></FieldControl>
            <FieldControl label="Status"><select value={staffForm.status} onChange={event => setStaffForm(form => ({ ...form, status: event.target.value }))}><option value="active">Active</option><option value="inactive">Inactive</option></select></FieldControl>
          </div>
          {selectedStaffId !== 'new' && <p className="panelHint">* Email cannot be changed after account creation.</p>}
          {selectedStaffId === 'new' && <p className="panelHint">GoalZone emails a one-time password setup link. Administrators cannot view or replace the staff member's password.</p>}
          <Button color="green" onClick={saveStaff} loading={staffSaving} disabled={staffSaving}>
            {selectedStaffId === 'new' ? 'Create & send invitation' : 'Save staff account'}
          </Button>
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
        opened={Boolean(customerActivity)}
        onClose={() => setCustomerActivity(null)}
        centered
        size="lg"
        title={customerActivity?.user?.fullName || 'Customer details'}
      >
        {customerActivity && (
          <Stack gap="md">
            <MetricGrid metrics={[
              ['Recent bookings', customerActivity.bookingCount || 0],
              ['Completed', customerActivity.completedBookingCount || 0],
              ['Reported issues', customerActivity.issues?.length || 0]
            ]} />
            <div className="profileForm">
              <p><strong>Email: </strong><span>{customerActivity.user.email}</span></p>
              <p><strong>Phone: </strong><span>{customerActivity.user.phone || 'Not provided'}</span></p>
              <p><strong>Address: </strong><span>{customerActivity.user.address || 'Not provided'}</span></p>
              <p><strong>Status: </strong><span>{customerActivity.user.accountLocked ? 'Locked' : customerActivity.user.status}</span></p>
              {customerActivity.user.accountLocked && (
                <p><strong>Lock reason: </strong><span>{customerActivity.user.lockReason || 'Not provided'}</span></p>
              )}
            </div>
            <div>
              <Text fw={700} mb="xs">Recent bookings</Text>
              {customerActivity.bookings.length ? (
                <DataList items={customerActivity.bookings.map(booking => ({
                  title: `${booking.bookingCode} - ${booking.fieldName}`,
                  meta: `${booking.slotDate} - ${String(booking.startTime).slice(0, 5)}`,
                  value: booking.status
                }))} />
              ) : <Text size="sm" c="dimmed">No bookings recorded.</Text>}
            </div>
            <div>
              <Text fw={700} mb="xs">Reported issues</Text>
              {customerActivity.issues.length ? (
                <DataList items={customerActivity.issues.map(issue => ({
                  title: issue.title,
                  meta: issue.bookingCode || issue.fieldName || 'General issue',
                  value: issue.status
                }))} />
              ) : <Text size="sm" c="dimmed">No issues reported.</Text>}
            </div>
          </Stack>
        )}
      </Modal>

      <Modal
        opened={lockModal.opened}
        onClose={closeLockModal}
        centered
        title={`Lock ${lockModal.customer?.fullName || 'customer'}`}
      >
        <Stack gap="sm">
          <Text size="sm" c="dimmed">
            The customer will be blocked from signing in and will receive an email with this reason.
          </Text>
          <FieldControl label="Lock reason">
            <textarea
              value={lockModal.reason}
              onChange={event => setLockModal(modal => ({ ...modal, reason: event.target.value }))}
              placeholder="Example: Repeated no-shows and unpaid booking balance."
            />
          </FieldControl>
          <Group justify="flex-end">
            <Button variant="light" color="gray" onClick={closeLockModal}>Cancel</Button>
            <Button color="red" onClick={confirmLock} disabled={!lockModal.reason.trim()}>Lock customer</Button>
          </Group>
        </Stack>
      </Modal>
    </section>
  )
}
