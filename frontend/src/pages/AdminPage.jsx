import { useEffect, useMemo, useState } from 'react'
import { Badge, Button, Group, Modal, Stack, Text } from '@mantine/core'
import { Plus } from 'lucide-react'
import { FieldControl, ImageUploadField, InfoPanel, WorkspaceHeader, WorkspaceTabs } from '../components/common'
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

const emptyStaffForm = {
  fullName: '',
  email: '',
  phone: '',
  password: '',
  status: 'active'
}

const statusColor = status => status === 'active' ? 'green' : 'gray'

export function AdminPage({
  reports,
  settings,
  fieldTypes,
  updateDepositSetting,
  updatePolicySetting,
  customers,
  updateCustomerRestriction,
  refreshAll,
  navigatePage
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
  const [staffAccounts, setStaffAccounts] = useState([])
  const [selectedStaffId, setSelectedStaffId] = useState('new')
  const [staffForm, setStaffForm] = useState(emptyStaffForm)
  const [customerEditor, setCustomerEditor] = useState({ opened: false, customer: null })
  const [customerActivity, setCustomerActivity] = useState(null)
  const [policyValues, setPolicyValues] = useState({})
  const [activePanel, setActivePanel] = useState('overview')

  const panelClass = panel => activePanel === panel ? '' : 'workspacePanelHidden'

  useEffect(() => {
    setPolicyValues(Object.fromEntries(settings.map(setting => [setting.settingKey, setting.settingValue])))
  }, [settings])

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
    const staff = staffAccounts.find(account => Number(account.userId) === Number(selectedStaffId))
    setStaffForm(staff ? {
      fullName: staff.fullName || '', email: staff.email || '', phone: staff.phone || '', password: '', status: staff.status || 'active'
    } : emptyStaffForm)
  }, [selectedStaffId, staffAccounts])

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

  async function saveStaff() {
    try {
      const staff = staffAccounts.find(account => Number(account.userId) === Number(selectedStaffId))
      if (staff) await api.put(`/account/staff/${staff.userId}`, staffForm)
      else await api.post('/account/staff', staffForm)
      await loadStaff()
      await refreshAll?.()
      setFieldNotice(staff ? 'Staff account updated.' : 'Staff account created.')
    } catch (error) {
      setFieldNotice(error.response?.data?.error || 'Could not save staff account.')
    }
  }

  async function updateCustomerStatus(customer) {
    const status = customer.status === 'active' ? 'inactive' : 'active'
    try {
      await api.put(`/account/users/${customer.userId}/status`, { status })
      await refreshAll?.()
      setFieldNotice(status === 'active' ? 'Customer account unlocked.' : 'Customer account locked.')
    } catch (error) {
      setFieldNotice(error.response?.data?.error || 'Could not update customer account.')
    }
  }

  async function saveCustomerProfile() {
    const customer = customerEditor.customer
    if (!customer) return
    try {
      await api.put(`/account/users/${customer.userId}/profile`, customer)
      setCustomerEditor({ opened: false, customer: null })
      await refreshAll?.()
      setFieldNotice('Customer profile updated.')
    } catch (error) {
      setFieldNotice(error.response?.data?.error || 'Could not update customer profile.')
    }
  }

  async function viewCustomerActivity(customer) {
    try {
      const response = await api.get(`/account/users/${customer.userId}/activity`)
      setCustomerActivity(response.data)
    } catch (error) {
      setFieldNotice(error.response?.data?.error || 'Could not load customer activity.')
    }
  }

  return (
    <section id="admin" className="section adminSection">
      <WorkspaceHeader
        kicker="Admin workspace"
        title="Management and reports"
        text="Review revenue, booking utilization, field setup, pricing, customer access, and lightweight deposit configuration."
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

      <WorkspaceTabs
        value={activePanel}
        onChange={setActivePanel}
        ariaLabel="Admin workspace sections"
        items={[
          { value: 'overview', label: 'Overview' },
          { value: 'venues', label: 'Fields & pricing' },
          { value: 'services', label: 'Services' },
          { value: 'access', label: 'People & access' },
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
          <DataList items={settings.map(setting => ({
            title: setting.settingKey,
            meta: setting.description,
            value: setting.settingValue
          }))} />
          <div className="buttonRow">
            <Button variant="light" onClick={() => updateDepositSetting(30)}>Set 30%</Button>
            <Button variant="light" onClick={() => updateDepositSetting(50)}>Set 50%</Button>
          </div>
          <div className="profileForm">
            {settings.map(setting => (
              <FieldControl key={setting.settingKey} label={setting.description || setting.settingKey}>
                <div className="buttonRow noMargin">
                  <input type="number" min="0" value={policyValues[setting.settingKey] ?? ''} onChange={event => setPolicyValues(values => ({ ...values, [setting.settingKey]: event.target.value }))} />
                  <Button size="xs" onClick={() => updatePolicySetting(setting.settingKey, policyValues[setting.settingKey])}>Save</Button>
                </div>
              </FieldControl>
            ))}
          </div>
        </InfoPanel>

        <InfoPanel title="Customer booking access" className={panelClass('access')}>
          <div className="customerAdminList">
            {customers.map(customer => (
              <div className="customerAdminRow" key={customer.userId}>
                <span>
                  <strong>{customer.fullName}</strong>
                  <small title={`${customer.email} - ${customer.phone || 'no phone'}`}>{customer.email} - {customer.phone || 'no phone'}</small>
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
                <Button size="xs" variant="subtle" onClick={() => setCustomerEditor({ opened: true, customer: { ...customer } })}>Edit</Button>
                <Button size="xs" variant="subtle" onClick={() => viewCustomerActivity(customer)}>Activity</Button>
                <Button
                  size="xs"
                  variant="light"
                  color={customer.status === 'active' ? 'red' : 'green'}
                  onClick={() => updateCustomerStatus(customer)}
                >{customer.status === 'active' ? 'Lock' : 'Unlock'}</Button>
              </div>
            ))}
          </div>
        </InfoPanel>

        <InfoPanel title="Staff accounts" className={panelClass('access')}>
          <div className="customerAdminList">
            <button type="button" className={selectedStaffId === 'new' ? 'customerAdminRow selected' : 'customerAdminRow'} onClick={() => setSelectedStaffId('new')}>
              <span><strong>New staff account</strong><small>Create a staff login and set its access status.</small></span>
            </button>
            {staffAccounts.map(account => (
              <button type="button" className={Number(selectedStaffId) === Number(account.userId) ? 'customerAdminRow selected' : 'customerAdminRow'} key={account.userId} onClick={() => setSelectedStaffId(account.userId)}>
                <span><strong>{account.fullName}</strong><small>{account.email} · {account.phone}</small></span>
                <Badge color={statusColor(account.status)} variant="light">{account.status}</Badge>
              </button>
            ))}
          </div>
          <div className="adminFormGrid compact">
            <FieldControl label="Full name"><input value={staffForm.fullName} onChange={event => setStaffForm(form => ({ ...form, fullName: event.target.value }))} /></FieldControl>
            <FieldControl label="Email"><input type="email" value={staffForm.email} onChange={event => setStaffForm(form => ({ ...form, email: event.target.value }))} /></FieldControl>
            <FieldControl label="Phone"><input value={staffForm.phone} onChange={event => setStaffForm(form => ({ ...form, phone: event.target.value }))} /></FieldControl>
            <FieldControl label={selectedStaffId === 'new' ? 'Initial password' : 'New password (optional)'}><input type="password" value={staffForm.password} onChange={event => setStaffForm(form => ({ ...form, password: event.target.value }))} /></FieldControl>
            <FieldControl label="Status"><select value={staffForm.status} onChange={event => setStaffForm(form => ({ ...form, status: event.target.value }))}><option value="active">Active</option><option value="inactive">Inactive</option></select></FieldControl>
          </div>
          <Button color="green" onClick={saveStaff}>{selectedStaffId === 'new' ? 'Create staff account' : 'Save staff account'}</Button>
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

      <Modal opened={customerEditor.opened} onClose={() => setCustomerEditor({ opened: false, customer: null })} centered title="Edit customer profile">
        <Stack gap="sm">
          <FieldControl label="Full name"><input value={customerEditor.customer?.fullName || ''} onChange={event => setCustomerEditor(editor => ({ ...editor, customer: { ...editor.customer, fullName: event.target.value } }))} /></FieldControl>
          <FieldControl label="Phone"><input value={customerEditor.customer?.phone || ''} onChange={event => setCustomerEditor(editor => ({ ...editor, customer: { ...editor.customer, phone: event.target.value } }))} /></FieldControl>
          <FieldControl label="Address"><textarea value={customerEditor.customer?.address || ''} onChange={event => setCustomerEditor(editor => ({ ...editor, customer: { ...editor.customer, address: event.target.value } }))} /></FieldControl>
          <Button onClick={saveCustomerProfile}>Save customer profile</Button>
        </Stack>
      </Modal>

      <Modal opened={Boolean(customerActivity)} onClose={() => setCustomerActivity(null)} centered title={`${customerActivity?.user?.fullName || 'Customer'} activity`}>
        <Stack gap="sm">
          <Text size="sm">Completed bookings: {customerActivity?.completedBookingCount || 0}</Text>
          <DataList items={(customerActivity?.bookings || []).map(booking => ({ title: booking.bookingCode, meta: `${booking.fieldName} · ${booking.slotDate}`, value: booking.status }))} />
          <DataList items={(customerActivity?.issues || []).map(issue => ({ title: issue.title, meta: issue.resolutionNote || 'No resolution note', value: issue.status }))} />
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
            The customer can still sign in and review existing bookings, but cannot create a new booking. They will receive an email with this reason.
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
