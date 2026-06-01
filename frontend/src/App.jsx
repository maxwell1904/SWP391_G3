import { useEffect, useMemo, useState } from 'react'
import {
  ArrowRight,
  CalendarDays,
  Clock,
  Eye,
  EyeOff,
  LogIn,
  LogOut,
  MailCheck,
  Menu,
  Search,
  ShieldCheck,
  UserRound,
  Wrench
} from 'lucide-react'
import api from './services/api'
import './styles/app.css'

const heroImage = 'https://images.unsplash.com/photo-1556056504-5c7696c4c28d?auto=format&fit=crop&w=2200&q=85'

const tomorrow = () => {
  const date = new Date()
  date.setDate(date.getDate() + 1)
  return date.toISOString().slice(0, 10)
}

const formatMoney = value => new Intl.NumberFormat('vi-VN', {
  style: 'currency',
  currency: 'VND',
  maximumFractionDigits: 0
}).format(Number(value || 0))

const pageRoutes = {
  home: '/',
  fields: '/fields',
  booking: '/booking',
  login: '/login',
  account: '/account',
  promotions: '/promotions',
  staff: '/staff',
  admin: '/admin'
}

const pageFromPath = pathname => {
  const match = Object.entries(pageRoutes).find(([, path]) => path === pathname)
  return match?.[0] || 'home'
}

const loadStoredUser = () => {
  try {
    return JSON.parse(window.localStorage.getItem('goalzone.currentUser')) || null
  } catch {
    return null
  }
}

function App() {
  const [loading, setLoading] = useState(true)
  const [notice, setNotice] = useState('')
  const [actionPanel, setActionPanel] = useState(null)
  const [currentPage, setCurrentPage] = useState(() => pageFromPath(window.location.pathname))
  const [currentUser, setCurrentUser] = useState(loadStoredUser)
  const [authMode, setAuthMode] = useState('login')
  const [mobileOpen, setMobileOpen] = useState(false)
  const [showLoginPassword, setShowLoginPassword] = useState(false)
  const [showRegisterPassword, setShowRegisterPassword] = useState(false)

  const [users, setUsers] = useState([])
  const [fields, setFields] = useState([])
  const [fieldTypes, setFieldTypes] = useState([])
  const [slots, setSlots] = useState([])
  const [services, setServices] = useState([])
  const [bookings, setBookings] = useState([])
  const [payments, setPayments] = useState([])
  const [refunds, setRefunds] = useState([])
  const [issues, setIssues] = useState([])
  const [promotions, setPromotions] = useState([])
  const [membership, setMembership] = useState(null)
  const [reports, setReports] = useState(null)
  const [settings, setSettings] = useState([])
  const [notifications, setNotifications] = useState([])

  const [searchDate, setSearchDate] = useState(tomorrow())
  const [fieldTypeFilter, setFieldTypeFilter] = useState('')
  const [selectedSlotId, setSelectedSlotId] = useState(null)
  const [selectedBookingId, setSelectedBookingId] = useState(null)
  const [selectedCustomerId, setSelectedCustomerId] = useState(1)
  const [selectedStaffId, setSelectedStaffId] = useState(3)
  const [promotionCode, setPromotionCode] = useState('')
  const [selectedServices, setSelectedServices] = useState({})
  const [checkout, setCheckout] = useState(null)
  const [checkoutLoading, setCheckoutLoading] = useState(false)
  const [checkoutError, setCheckoutError] = useState('')
  const [verificationCode, setVerificationCode] = useState('')
  const [demoVerificationCode, setDemoVerificationCode] = useState('')

  const [loginForm, setLoginForm] = useState({ emailOrPhone: 'customer@goalzone.local', password: '123456' })
  const [registerForm, setRegisterForm] = useState({
    fullName: 'New Customer',
    email: `customer${Date.now()}@goalzone.local`,
    phone: `09${String(Date.now()).slice(-8)}`,
    password: '123456'
  })
  const [issueDraft, setIssueDraft] = useState({
    title: 'Loose goal net',
    description: 'Customer reported a field issue before check-in.'
  })

  const customers = useMemo(() => users.filter(user => user.role === 'Customer'), [users])
  const selectedSlot = slots.find(slot => slot.slotId === Number(selectedSlotId))
  const selectedBooking = bookings.find(booking => booking.bookingId === Number(selectedBookingId)) || bookings[0]
  const isCustomer = currentUser?.role === 'Customer'
  const isStaff = currentUser?.role === 'Staff'
  const isAdmin = currentUser?.role === 'Admin'
  const canOperate = isStaff || isAdmin

  useEffect(() => {
    refreshAll()
  }, [])

  useEffect(() => {
    const handlePopState = () => setCurrentPage(pageFromPath(window.location.pathname))
    window.addEventListener('popstate', handlePopState)
    return () => window.removeEventListener('popstate', handlePopState)
  }, [])

  useEffect(() => {
    if (currentUser) {
      window.localStorage.setItem('goalzone.currentUser', JSON.stringify(currentUser))
    } else {
      window.localStorage.removeItem('goalzone.currentUser')
    }
  }, [currentUser])

  useEffect(() => {
    loadSlots()
  }, [searchDate, fieldTypeFilter])

  useEffect(() => {
    let cancelled = false
    if (currentPage !== 'booking' || !selectedSlotId) {
      setCheckout(null)
      setCheckoutError('')
      return undefined
    }

    const timer = window.setTimeout(async () => {
      setCheckoutLoading(true)
      setCheckoutError('')
      try {
        const response = await api.post('/bookings/checkout-preview', checkoutPayload())
        if (!cancelled) {
          setCheckout(response.data)
        }
      } catch (error) {
        if (!cancelled) {
          setCheckout(null)
          setCheckoutError(error.response?.data?.error || 'Could not update checkout')
        }
      } finally {
        if (!cancelled) {
          setCheckoutLoading(false)
        }
      }
    }, 250)

    return () => {
      cancelled = true
      window.clearTimeout(timer)
    }
  }, [currentPage, selectedSlotId, selectedCustomerId, currentUser?.userId, currentUser?.role, promotionCode, selectedServices, canOperate])

  useEffect(() => {
    if (currentUser?.role === 'Customer') {
      setSelectedCustomerId(currentUser.userId)
      loadMembership(currentUser.userId)
      loadNotifications(currentUser.userId)
    }
  }, [currentUser])

  async function refreshAll() {
    setLoading(true)
    try {
      const [
        userRes,
        fieldRes,
        typeRes,
        serviceRes,
        bookingRes,
        paymentRes,
        refundRes,
        issueRes,
        promoRes,
        reportRes,
        settingRes
      ] = await Promise.all([
        api.get('/account/users'),
        api.get('/fields'),
        api.get('/field-types'),
        api.get('/services'),
        api.get('/bookings'),
        api.get('/payments'),
        api.get('/refunds'),
        api.get('/issues'),
        api.get('/promotions'),
        api.get('/reports'),
        api.get('/settings')
      ])
      setUsers(userRes.data)
      setFields(fieldRes.data)
      setFieldTypes(typeRes.data)
      setServices(serviceRes.data)
      setBookings(bookingRes.data)
      setPayments(paymentRes.data)
      setRefunds(refundRes.data)
      setIssues(issueRes.data)
      setPromotions(promoRes.data)
      setReports(reportRes.data)
      setSettings(settingRes.data)
      if (bookingRes.data[0]) setSelectedBookingId(bookingRes.data[0].bookingId)
      if (currentUser?.role === 'Customer') {
        await Promise.all([loadMembership(currentUser.userId), loadNotifications(currentUser.userId)])
      }
    } catch (error) {
      setNotice(error.response?.data?.error || 'Could not connect to the booking server')
    } finally {
      setLoading(false)
    }
  }

  async function loadSlots() {
    try {
      const params = { date: searchDate }
      if (fieldTypeFilter) params.fieldTypeId = fieldTypeFilter
      const response = await api.get('/slots/search', { params })
      setSlots(response.data)
      const firstAvailable = response.data.find(slot => slot.available)
      setSelectedSlotId(prev => response.data.some(slot => slot.slotId === Number(prev) && slot.available) ? prev : firstAvailable?.slotId || null)
    } catch (error) {
      setNotice(error.response?.data?.error || 'Could not load availability')
    }
  }

  async function loadMembership(customerId) {
    const response = await api.get(`/membership/${customerId}/progress`)
    setMembership(response.data)
  }

  async function loadNotifications(customerId) {
    const response = await api.get(`/notifications/${customerId}`)
    setNotifications(response.data)
  }

  async function runAction(action, successMessage) {
    setLoading(true)
    try {
      const result = await action()
      setNotice(successMessage)
      setActionPanel({
        kind: 'success',
        title: successMessage,
        message: actionMessage(successMessage)
      })
      await refreshAll()
      await loadSlots()
      return result
    } catch (error) {
      const message = error.response?.data?.error || error.message
      setNotice(message)
      setActionPanel({
        kind: 'error',
        title: 'Action failed',
        message
      })
      return null
    } finally {
      setLoading(false)
    }
  }

  function actionMessage(successMessage) {
    if (successMessage.includes('Checkout')) return 'The cost breakdown is ready in the checkout panel.'
    if (successMessage.includes('Booking created')) return 'The booking has been saved and the slot will now be treated as unavailable.'
    if (successMessage.includes('Signed in')) return 'Your role-specific workspace is ready.'
    if (successMessage.includes('Email verified')) return 'You can continue with online booking now.'
    if (successMessage.includes('Refund')) return 'Refund information was recorded for the selected booking.'
    return 'The latest data has been refreshed.'
  }

  function navigatePage(page) {
    const nextPath = pageRoutes[page] || pageRoutes.home
    setCurrentPage(pageRoutes[page] ? page : 'home')
    setMobileOpen(false)
    if (window.location.pathname !== nextPath) {
      window.history.pushState({}, '', nextPath)
    }
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  function bookingCustomerId() {
    if (currentUser?.role === 'Customer') return currentUser.userId
    return Number(selectedCustomerId)
  }

  function checkoutCustomerId() {
    if (currentUser?.role === 'Customer') return currentUser.userId
    if (canOperate) return Number(selectedCustomerId)
    return null
  }

  function checkoutBookingSource() {
    return canOperate ? 'walk_in' : 'online'
  }

  function checkoutPayload() {
    return {
      customerId: checkoutCustomerId(),
      slotId: Number(selectedSlotId),
      promotionCode,
      bookingSource: checkoutBookingSource(),
      services: serviceSelections()
    }
  }

  function serviceSelections() {
    return Object.entries(selectedServices)
      .filter(([, quantity]) => Number(quantity) > 0)
      .map(([serviceId, quantity]) => ({ serviceId: Number(serviceId), quantity: Number(quantity) }))
  }

  async function login(emailOrPhone = loginForm.emailOrPhone) {
    const response = await runAction(async () => api.post('/account/login', {
      emailOrPhone,
      password: loginForm.password || '123456'
    }), 'Signed in')
    if (response?.data?.user) {
      setCurrentUser(response.data.user)
      setAuthMode('login')
      setVerificationCode('')
      setDemoVerificationCode('')
      navigatePage('account')
    }
  }

  async function register() {
    const response = await runAction(async () => api.post('/account/register', registerForm), 'Account created')
    if (response?.data) {
      const user = response.data.user || response.data
      setCurrentUser(user)
      setAuthMode('login')
      setVerificationCode(response.data.verificationCode || '')
      setDemoVerificationCode(response.data.verificationCode || '')
      if (response.data.verificationRequired) {
        setNotice('Account created. Verify email before online booking')
      }
      navigatePage('account')
    }
  }

  async function verifyEmail() {
    if (!currentUser) return
    const response = await runAction(async () => api.post('/account/email/verify', {
      userId: currentUser.userId,
      token: verificationCode
    }), 'Email verified')
    if (response?.data?.user) {
      setCurrentUser(response.data.user)
      setVerificationCode('')
      setDemoVerificationCode('')
    }
  }

  async function resendVerification() {
    if (!currentUser) return
    const response = await runAction(async () => api.post('/account/email/resend', {
      userId: currentUser.userId
    }), 'Verification code sent')
    if (response?.data?.verificationCode) {
      setVerificationCode(response.data.verificationCode)
      setDemoVerificationCode(response.data.verificationCode)
    }
  }

  function logout() {
    setCurrentUser(null)
    setMembership(null)
    setNotifications([])
    setSelectedCustomerId(customers[0]?.userId || 1)
    setNotice('Signed out')
    setActionPanel({
      kind: 'success',
      title: 'Signed out',
      message: 'You are back in guest browsing mode.'
    })
    navigatePage('home')
  }

  async function createBooking(source = 'online') {
    if (!currentUser) {
      setAuthMode('login')
      navigatePage('login')
      setNotice('Sign in or create an account before booking')
      return
    }
    if (currentUser.role === 'Customer' && !currentUser.emailVerified) {
      navigatePage('account')
      setNotice('Verify your email before online booking')
      return
    }
    await runAction(async () => api.post('/bookings', {
      customerId: bookingCustomerId(),
      staffId: source === 'walk_in' ? Number(selectedStaffId) : null,
      slotId: Number(selectedSlotId),
      bookingSource: source,
      promotionCode,
      services: serviceSelections(),
      note: source === 'walk_in' ? 'Created by staff at venue' : 'Created from customer website'
    }), source === 'walk_in' ? 'Walk-in booking created' : 'Booking created')
  }

  async function updateBooking(status) {
    if (!selectedBooking) return
    await runAction(async () => api.put(`/bookings/${selectedBooking.bookingId}/status`, {
      status,
      staffId: Number(selectedStaffId),
      note: 'Updated from staff operation screen'
    }), `Booking updated to ${status}`)
  }

  async function capturePayment(option = 'deposit') {
    if (!selectedBooking) return
    await runAction(async () => api.post('/payments/capture', {
      bookingId: selectedBooking.bookingId,
      createdById: currentUser?.userId || bookingCustomerId(),
      paymentOption: option,
      paymentMethod: option === 'remaining' ? 'cash' : 'online_sandbox',
      amount: option === 'full' ? selectedBooking.remainingAmount || selectedBooking.totalAmount : null,
      success: true
    }), 'Payment captured')
  }

  async function createRefund() {
    if (!selectedBooking) return
    await runAction(async () => api.post('/refunds', {
      bookingId: selectedBooking.bookingId,
      requestedById: selectedBooking.customerId,
      processedById: Number(selectedStaffId),
      refundAmount: selectedBooking.refundableAmount || selectedBooking.depositAmount || 0,
      refundReason: 'Support-approved refund',
      approveNow: true
    }), 'Refund processed')
  }

  async function createIssue() {
    if (!currentUser) {
      setAuthMode('login')
      navigatePage('login')
      setNotice('Sign in before reporting an issue')
      return
    }
    await runAction(async () => api.post('/issues', {
      reporterId: currentUser.userId,
      bookingId: selectedBooking?.bookingId,
      fieldId: selectedBooking ? null : selectedSlot?.fieldId,
      assignedStaffId: Number(selectedStaffId),
      title: issueDraft.title,
      description: issueDraft.description
    }), 'Issue reported')
  }

  async function updateDepositSetting(value) {
    await runAction(async () => api.put('/settings/deposit.default_percent', {
      settingValue: String(value),
      updatedById: currentUser?.userId || 4
    }), 'Deposit rule updated')
  }

  const userBookings = currentUser?.role === 'Customer'
    ? bookings.filter(booking => booking.customerId === currentUser.userId)
    : bookings

  return (
    <div className="siteShell">
      <a className="skipLink" href="#main">Skip to content</a>
      <header className="siteHeader">
        <button className="brandButton" onClick={() => navigatePage('home')} aria-label="Go to home">
          <span className="brandMark">GZ</span>
          <span>GoalZone</span>
        </button>
        <nav className={mobileOpen ? 'mainNav open' : 'mainNav'} aria-label="Primary navigation">
          <button className={currentPage === 'fields' ? 'active' : ''} onClick={() => navigatePage('fields')}>Fields</button>
          <button className={currentPage === 'booking' ? 'active' : ''} onClick={() => navigatePage('booking')}>Booking</button>
          <button className={currentPage === 'promotions' ? 'active' : ''} onClick={() => navigatePage('promotions')}>Promotions</button>
          {currentUser && <button className={currentPage === 'account' ? 'active' : ''} onClick={() => navigatePage('account')}>My account</button>}
          {canOperate && <button className={currentPage === 'staff' ? 'active' : ''} onClick={() => navigatePage('staff')}>Staff</button>}
          {isAdmin && <button className={currentPage === 'admin' ? 'active' : ''} onClick={() => navigatePage('admin')}>Admin</button>}
        </nav>
        <div className="headerActions">
          {currentUser ? (
            <>
              <button
                className="accountButton"
                onClick={() => navigatePage('account')}
                aria-label={`Open account for ${currentUser.fullName}`}
              >
                <UserRound size={18} />
                <span>{currentUser.fullName}</span>
              </button>
              <button className="iconOnlyButton" onClick={logout} aria-label="Log out">
                <LogOut size={18} />
              </button>
            </>
          ) : (
            <button className="outlineButton" onClick={() => navigatePage('login')}>
              <LogIn size={18} />
              <span>Sign in</span>
            </button>
          )}
          <button className="menuButton" onClick={() => setMobileOpen(!mobileOpen)} aria-label="Toggle navigation">
            <Menu size={22} />
          </button>
        </div>
      </header>

      <main id="main" className={currentPage === 'home' ? '' : 'pageMain'}>
        {currentPage === 'home' && (
        <section id="home" className="hero" style={{ '--hero-image': `url(${heroImage})` }}>
          <div className="heroContent">
            <p className="venueLabel">Football field booking for local venues</p>
            <h1>GoalZone</h1>
            <p className="heroCopy">Book a field, add match services, pay the deposit, and manage the booking from one place.</p>
            <div className="heroActions">
              <button className="primaryButton" onClick={() => navigatePage('booking')}>
                <span>Book a field</span>
                <ArrowRight size={18} />
              </button>
              <button className="ghostButton" onClick={() => navigatePage('fields')}>View fields</button>
            </div>
          </div>
          <div className="availabilityStrip" aria-label="Quick availability search">
            <FieldControl label="Date">
              <input type="date" value={searchDate} onChange={event => setSearchDate(event.target.value)} />
            </FieldControl>
            <FieldControl label="Field type">
              <select value={fieldTypeFilter} onChange={event => setFieldTypeFilter(event.target.value)}>
                <option value="">All field types</option>
                {fieldTypes.map(type => <option key={type.fieldTypeId} value={type.fieldTypeId}>{type.typeName}</option>)}
              </select>
            </FieldControl>
            <button className="stripButton" onClick={() => navigatePage('booking')}>
              <SearchIcon />
              <span>Find slots</span>
            </button>
          </div>
        </section>
        )}

        {(currentPage === 'home' || currentPage === 'fields') && (
        <section id="fields" className="section">
          <SectionIntro
            kicker="Fields"
            title="Choose the pitch that fits the match"
            text="Guests can browse active fields and availability before signing in. Booking is required only at checkout."
          />
          <div className="fieldGrid">
            {fields.map(field => (
              <article className="fieldCard" key={field.fieldId}>
                <img src={field.imageUrl} alt={`${field.fieldName} football pitch`} />
                <div>
                  <span>{field.fieldType}</span>
                  <h3>{field.fieldName}</h3>
                  <p>{field.description}</p>
                  <dl>
                    <div><dt>Location</dt><dd>{field.location}</dd></div>
                    <div><dt>Surface</dt><dd>{field.surfaceType}</dd></div>
                  </dl>
                </div>
              </article>
            ))}
          </div>
        </section>
        )}

        {currentPage === 'booking' && (
        <section id="booking" className="section bookingSection">
          <SectionIntro
            kicker="Booking"
            title="Search, price, and reserve"
            text="Pick a free slot, add match services, apply a promotion, and see the deposit before the booking is saved."
          />
          <div className="bookingLayout">
            <div className="bookingMain">
              <div className="filterRow">
                <FieldControl label="Playing date">
                  <input type="date" value={searchDate} onChange={event => setSearchDate(event.target.value)} />
                </FieldControl>
                <FieldControl label="Field type">
                  <select value={fieldTypeFilter} onChange={event => setFieldTypeFilter(event.target.value)}>
                    <option value="">All</option>
                    {fieldTypes.map(type => <option key={type.fieldTypeId} value={type.fieldTypeId}>{type.typeName}</option>)}
                  </select>
                </FieldControl>
                {canOperate && (
                  <FieldControl label="Customer">
                    <select value={selectedCustomerId} onChange={event => setSelectedCustomerId(Number(event.target.value))}>
                      {customers.map(user => <option key={user.userId} value={user.userId}>{user.fullName}</option>)}
                    </select>
                  </FieldControl>
                )}
              </div>
              <SlotList slots={slots} selectedSlotId={selectedSlotId} onSelect={setSelectedSlotId} />
              <ServicePicker services={services} selectedServices={selectedServices} setSelectedServices={setSelectedServices} />
            </div>
            <aside className="checkoutPanel">
              <h3>Checkout preview</h3>
              <SelectedSlot slot={selectedSlot} />
              <FieldControl label="Promotion code">
                <input
                  value={promotionCode}
                  placeholder="WELCOME10"
                  onChange={event => setPromotionCode(event.target.value.toUpperCase())}
                />
              </FieldControl>
              <CheckoutSummary checkout={checkout} loading={checkoutLoading} error={checkoutError} />
              <div className="stackedActions">
                {canOperate ? (
                  <button className="ghostDarkButton" onClick={() => createBooking('walk_in')}>Create walk-in booking</button>
                ) : (
                  <button className="primaryButton wide" onClick={() => createBooking('online')}>
                    <CalendarDays size={18} />
                    <span>{currentUser ? 'Reserve field' : 'Sign in to book'}</span>
                  </button>
                )}
              </div>
              {!currentUser && <p className="hintText">You can browse prices now. Login or register is required before the booking is saved.</p>}
            </aside>
          </div>
        </section>
        )}

        {(currentPage === 'login' || (currentPage === 'account' && !currentUser)) && (
        <section id="login" className="section authSection">
          <SectionIntro
            kicker="Account"
            title={currentUser ? 'Account ready for checkout' : 'Sign in before checkout'}
            text={currentUser
              ? 'Reserve selected slots and track booking, payment, membership, and support updates from your account.'
              : 'Customers need an account to hold a field and receive payment, cancellation, and refund updates.'}
          />
          <div className="authGrid">
            <article className="authPanel">
              {currentUser ? (
                <div className="signedInCard">
                  <span>{currentUser.emailVerified ? 'Signed in' : 'Email verification needed'}</span>
                  <h3>{currentUser.fullName}</h3>
                  <p>{currentUser.role} account · {currentUser.emailVerified ? 'verified email' : 'unverified email'}</p>
                  {!currentUser.emailVerified && (
                    <EmailVerificationPanel
                      code={verificationCode}
                      setCode={setVerificationCode}
                      demoCode={demoVerificationCode}
                      onVerify={verifyEmail}
                      onResend={resendVerification}
                    />
                  )}
                  <div className="buttonRow noMargin">
                    <button className="primaryButton wide" onClick={() => navigatePage('booking')}>Continue booking</button>
                    <button className="ghostDarkButton wide" onClick={logout}>
                      <LogOut size={18} />
                      <span>Log out</span>
                    </button>
                  </div>
                </div>
              ) : (
                <>
                  <div className="segmented">
                    <button className={authMode === 'login' ? 'active' : ''} onClick={() => setAuthMode('login')}>Login</button>
                    <button className={authMode === 'register' ? 'active' : ''} onClick={() => setAuthMode('register')}>Register</button>
                  </div>
                  {authMode === 'login' ? (
                <div className="formStack">
                  <FieldControl label="Email or phone">
                    <input value={loginForm.emailOrPhone} onChange={event => setLoginForm({ ...loginForm, emailOrPhone: event.target.value })} />
                  </FieldControl>
                  <PasswordField
                    label="Password"
                    value={loginForm.password}
                    visible={showLoginPassword}
                    onToggle={() => setShowLoginPassword(!showLoginPassword)}
                    onChange={value => setLoginForm({ ...loginForm, password: value })}
                  />
                  <button className="primaryButton wide" onClick={() => login()}>
                    <LogIn size={18} />
                    <span>Login</span>
                  </button>
                </div>
              ) : (
                <div className="formStack">
                  <FieldControl label="Full name">
                    <input value={registerForm.fullName} onChange={event => setRegisterForm({ ...registerForm, fullName: event.target.value })} />
                  </FieldControl>
                  <FieldControl label="Email">
                    <input value={registerForm.email} onChange={event => setRegisterForm({ ...registerForm, email: event.target.value })} />
                  </FieldControl>
                  <FieldControl label="Phone">
                    <input value={registerForm.phone} onChange={event => setRegisterForm({ ...registerForm, phone: event.target.value })} />
                  </FieldControl>
                  <PasswordField
                    label="Password"
                    value={registerForm.password}
                    visible={showRegisterPassword}
                    onToggle={() => setShowRegisterPassword(!showRegisterPassword)}
                    onChange={value => setRegisterForm({ ...registerForm, password: value })}
                  />
                  <button className="primaryButton wide" onClick={register}>Create account</button>
                </div>
                  )}
                </>
              )}
            </article>
          </div>
        </section>
        )}

        {currentPage === 'account' && currentUser && (
          <section id="account" className="section accountSection">
            <SectionIntro
              kicker="My account"
              title={`Welcome, ${currentUser.fullName}`}
              text="Track bookings, payment history, membership progress, and support messages."
            />
            <div className="accountGrid">
              <InfoPanel title="My bookings">
                <BookingList bookings={userBookings} selectedBookingId={selectedBookingId} onSelect={setSelectedBookingId} />
              </InfoPanel>
              <InfoPanel title="Payment history">
                <DataList items={payments
                  .filter(payment => currentUser.role !== 'Customer' || userBookings.some(booking => booking.bookingCode === payment.bookingCode))
                  .map(payment => ({
                    title: payment.paymentCode,
                    meta: `${payment.bookingCode} · ${payment.paymentMethod}`,
                    value: `${payment.status} · ${formatMoney(payment.amount)}`
                  }))}
                />
              </InfoPanel>
              <InfoPanel title="Membership">
                {membership ? (
                  <MetricGrid metrics={[
                    ['Level', membership.currentLevel],
                    ['Completed', membership.completedBookingCount],
                    ['Discount', `${membership.discountPercent}%`],
                    ['To next', membership.bookingsToNextLevel]
                  ]} />
                ) : <p className="emptyText">Login as customer to view membership.</p>}
              </InfoPanel>
              <InfoPanel title="Notifications">
                <DataList items={notifications.map(item => ({
                  title: item.title,
                  meta: item.message,
                  value: item.type
                }))} />
              </InfoPanel>
            </div>
          </section>
        )}

        {(currentPage === 'home' || currentPage === 'promotions') && (
        <section id="promotions" className="section promotionSection">
          <SectionIntro
            kicker="Promotions"
            title="Active offers and membership benefits"
            text="Save on selected matches and unlock member discounts after completed bookings."
          />
          <div className="promoGrid">
            {promotions.map(promotion => (
              <article className="promoCard" key={promotion.promotionId}>
                <span>{promotion.promotionCode}</span>
                <h3>{promotion.promotionName}</h3>
                <p>{promotion.description}</p>
                <strong>{promotion.discountType === 'percent' ? `${promotion.discountValue}% off` : `${formatMoney(promotion.discountValue)} off`}</strong>
              </article>
            ))}
          </div>
        </section>
        )}

        {currentPage === 'staff' && canOperate && (
          <section id="staff" className="section staffSection">
            <SectionIntro
              kicker="Staff operation"
              title="Daily field operation"
              text="This role-only area supports walk-in booking, check-in, completion, cancellation, no-show, issue handling, and refund cases."
            />
            <div className="operationGrid">
              <InfoPanel title="Booking calendar">
                <BookingList bookings={bookings} selectedBookingId={selectedBookingId} onSelect={setSelectedBookingId} />
              </InfoPanel>
              <InfoPanel title="Lifecycle actions">
                <SelectedBooking booking={selectedBooking} />
                <div className="actionGrid">
                  <button onClick={() => updateBooking('confirmed')}>Confirm</button>
                  <button onClick={() => updateBooking('checked_in')}>Check-in</button>
                  <button onClick={() => updateBooking('completed')}>Complete</button>
                  <button onClick={() => updateBooking('cancelled')}>Cancel</button>
                  <button onClick={() => updateBooking('no_show')}>No-show</button>
                  <button onClick={() => capturePayment('remaining')}>Remaining payment</button>
                </div>
              </InfoPanel>
              <InfoPanel title="Issue report">
                <FieldControl label="Title">
                  <input value={issueDraft.title} onChange={event => setIssueDraft({ ...issueDraft, title: event.target.value })} />
                </FieldControl>
                <FieldControl label="Description">
                  <textarea value={issueDraft.description} onChange={event => setIssueDraft({ ...issueDraft, description: event.target.value })} />
                </FieldControl>
                <button className="secondaryButton" onClick={createIssue}>
                  <Wrench size={18} />
                  <span>Save issue</span>
                </button>
                <DataList items={issues.map(issue => ({
                  title: issue.title,
                  meta: `${issue.reporter} · ${issue.bookingCode || issue.fieldName || 'general'}`,
                  value: issue.status
                }))} />
              </InfoPanel>
              <InfoPanel title="Refunds">
                <button className="secondaryButton" onClick={createRefund}>Process refund</button>
                <DataList items={refunds.map(refund => ({
                  title: refund.refundCode,
                  meta: `${refund.bookingCode} · ${refund.refundReason || 'support case'}`,
                  value: `${refund.status} · ${formatMoney(refund.refundAmount)}`
                }))} />
              </InfoPanel>
            </div>
          </section>
        )}

        {currentPage === 'staff' && !canOperate && (
          <AccessPanel title="Staff access" text="Login with a staff or admin account to use daily operation tools." onLogin={() => navigatePage('login')} />
        )}

        {currentPage === 'admin' && isAdmin && (
          <section id="admin" className="section adminSection">
            <SectionIntro
              kicker="Admin"
              title="Management and reports"
              text="Admin can review revenue, booking activity, customer activity, and lightweight deposit/refund configuration."
            />
            <div className="adminGrid">
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
                  <button onClick={() => updateDepositSetting(30)}>Set 30%</button>
                  <button onClick={() => updateDepositSetting(50)}>Set 50%</button>
                </div>
              </InfoPanel>
            </div>
          </section>
        )}

        {currentPage === 'admin' && !isAdmin && (
          <AccessPanel title="Admin access" text="Login with an admin account to view reports and configuration." onLogin={() => navigatePage('login')} />
        )}
      </main>

      <footer className="siteFooter">
        <div>
          <strong>GoalZone</strong>
          <p>Book local football fields, manage payments, and keep match-day service in one place.</p>
        </div>
        <div className="footerLinks">
          <button onClick={() => navigatePage('fields')}>Fields</button>
          <button onClick={() => navigatePage('booking')}>Booking</button>
          <button onClick={() => navigatePage(currentUser ? 'account' : 'login')}>{currentUser ? 'Account' : 'Login'}</button>
        </div>
      </footer>

      {notice && !actionPanel && (
        <div className={loading ? 'toast loading' : 'toast'}>
          <span>{loading ? 'Working' : 'Status'}</span>
          <p>{notice}</p>
        </div>
      )}

      {actionPanel && (
        <ActionPanel
          panel={actionPanel}
          onClose={() => setActionPanel(null)}
        />
      )}
    </div>
  )
}

function SearchIcon() {
  return <Search size={18} />
}

function AccessPanel({ title, text, onLogin }) {
  return (
    <section className="section accessSection">
      <div className="accessPanel">
        <span>Restricted area</span>
        <h2>{title}</h2>
        <p>{text}</p>
        <button className="primaryButton" onClick={onLogin}>
          <LogIn size={18} />
          <span>Login</span>
        </button>
      </div>
    </section>
  )
}

function ActionPanel({ panel, onClose }) {
  return (
    <div className={panel.kind === 'error' ? 'actionPanel error' : 'actionPanel'} role="status" aria-live="polite">
      <div>
        <span>{panel.kind === 'error' ? 'Needs attention' : 'Done'}</span>
        <h3>{panel.title}</h3>
        <p>{panel.message}</p>
      </div>
      <button onClick={onClose}>Close</button>
    </div>
  )
}

function SectionIntro({ kicker, title, text }) {
  return (
    <div className="sectionIntro">
      <span>{kicker}</span>
      <h2>{title}</h2>
      <p>{text}</p>
    </div>
  )
}

function FieldControl({ label, children }) {
  return (
    <label className="fieldControl">
      <span>{label}</span>
      {children}
    </label>
  )
}

function PasswordField({ label, value, visible, onToggle, onChange }) {
  return (
    <FieldControl label={label}>
      <span className="passwordInput">
        <input
          type={visible ? 'text' : 'password'}
          value={value}
          onChange={event => onChange(event.target.value)}
        />
        <button type="button" onClick={onToggle} aria-label={visible ? 'Hide password' : 'Show password'}>
          {visible ? <EyeOff size={18} /> : <Eye size={18} />}
        </button>
      </span>
    </FieldControl>
  )
}

function EmailVerificationPanel({ code, setCode, demoCode, onVerify, onResend }) {
  return (
    <div className="verificationPanel">
      <div>
        <MailCheck size={20} />
        <span>Email verification</span>
      </div>
      <p>For this local demo, the verification code is shown here instead of being sent by SMTP.</p>
      {demoCode && <strong>Demo code: {demoCode}</strong>}
      <FieldControl label="Verification code">
        <input value={code} onChange={event => setCode(event.target.value)} />
      </FieldControl>
      <div className="buttonRow noMargin">
        <button className="secondaryButton" onClick={onVerify}>Verify email</button>
        <button className="ghostDarkButton" onClick={onResend}>Resend code</button>
      </div>
    </div>
  )
}

function SlotList({ slots, selectedSlotId, onSelect }) {
  if (!slots.length) return <p className="emptyText">No slots found for this date.</p>
  return (
    <div className="slotList">
      {slots.map(slot => (
        <button
          key={slot.slotId}
          className={Number(selectedSlotId) === slot.slotId ? 'slotItem selected' : 'slotItem'}
          onClick={() => onSelect(slot.slotId)}
          disabled={!slot.available}
        >
          <span>{slot.fieldName}</span>
          <strong>{slot.startTime} - {slot.endTime}</strong>
          <em>{formatMoney(slot.price)}</em>
          <small>{slot.status}</small>
        </button>
      ))}
    </div>
  )
}

function ServicePicker({ services, selectedServices, setSelectedServices }) {
  return (
    <div className="serviceBlock">
      <h3>Add services</h3>
      <div className="serviceGrid">
        {services.map(service => (
          <label key={service.extraServiceId} className="serviceOption">
            <span>
              <strong>{service.serviceName}</strong>
              <small>{formatMoney(service.unitPrice)} / {service.unitName}</small>
            </span>
            <input
              type="number"
              min="0"
              max={service.maxQuantityPerBooking || 5}
              value={selectedServices[service.extraServiceId] || 0}
              onChange={event => setSelectedServices({
                ...selectedServices,
                [service.extraServiceId]: event.target.value
              })}
            />
          </label>
        ))}
      </div>
    </div>
  )
}

function SelectedSlot({ slot }) {
  if (!slot) return <p className="emptyText">Select an available slot first.</p>
  return (
    <div className="selectedSlot">
      <Clock size={18} />
      <div>
        <strong>{slot.fieldName}</strong>
        <p>{slot.slotDate} · {slot.startTime} - {slot.endTime}</p>
      </div>
      <span>{formatMoney(slot.price)}</span>
    </div>
  )
}

function CheckoutSummary({ checkout, loading, error }) {
  if (loading) return <p className="emptyText">Updating checkout...</p>
  if (error) return <p className="errorText">{error}</p>
  if (!checkout) return <p className="emptyText">Choose an available slot to see pricing.</p>
  const promotionDiscount = Number(checkout.promotionDiscountAmount || 0)
  const membershipDiscount = Number(checkout.membershipDiscountAmount || 0)
  return (
    <div className="checkoutSummary">
      <Line label="Field price" value={formatMoney(checkout.fieldPriceAmount)} />
      <Line label="Services" value={formatMoney(checkout.serviceTotalAmount)} />
      {promotionDiscount > 0 && <Line label="Promotion" value={`-${formatMoney(checkout.promotionDiscountAmount)}`} />}
      {membershipDiscount > 0 && <Line label="Membership" value={`-${formatMoney(checkout.membershipDiscountAmount)}`} />}
      <Line label="Deposit" value={formatMoney(checkout.depositAmount)} />
      <Line label="Total" value={formatMoney(checkout.totalAmount)} strong />
    </div>
  )
}

function Line({ label, value, strong }) {
  return (
    <div className={strong ? 'summaryLine strong' : 'summaryLine'}>
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  )
}

function InfoPanel({ title, children }) {
  return (
    <article className="infoPanel">
      <h3>{title}</h3>
      {children}
    </article>
  )
}

function BookingList({ bookings, selectedBookingId, onSelect }) {
  if (!bookings.length) return <p className="emptyText">No bookings yet.</p>
  return (
    <div className="bookingList">
      {bookings.map(booking => (
        <button
          key={booking.bookingId}
          className={Number(selectedBookingId) === booking.bookingId ? 'bookingItem selected' : 'bookingItem'}
          onClick={() => onSelect(booking.bookingId)}
        >
          <span>
            <strong>{booking.bookingCode}</strong>
            <small>{booking.customer} · {booking.fieldName} · {booking.startTime}</small>
          </span>
          <em>{booking.status}</em>
        </button>
      ))}
    </div>
  )
}

function SelectedBooking({ booking }) {
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

function DataList({ items }) {
  if (!items.length) return <p className="emptyText">No records yet.</p>
  return (
    <div className="dataList">
      {items.map((item, index) => (
        <div className="dataRow" key={`${item.title}-${index}`}>
          <span>
            <strong>{item.title}</strong>
            <small>{item.meta}</small>
          </span>
          <em>{item.value}</em>
        </div>
      ))}
    </div>
  )
}

function MetricGrid({ metrics }) {
  return (
    <div className="metricGrid">
      {metrics.map(([label, value]) => (
        <div className="metric" key={label}>
          <span>{label}</span>
          <strong>{value}</strong>
        </div>
      ))}
    </div>
  )
}

export default App
