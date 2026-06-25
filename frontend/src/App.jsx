import { useEffect, useMemo, useState } from 'react'
import {
  LogIn,
  LogOut,
  Menu,
  UserRound
} from 'lucide-react'
import { pageFromPath, pageRoutes } from './app/routes'
import { loadStoredUser, saveStoredUser } from './app/session'
import { AccessPanel, ActionPanel } from './components/common'
import { demoPassword, emailPattern, passwordIssues, phonePattern } from './features/auth/authRules'
import {
  AccountPage,
  AdminPage,
  AuthPage,
  BookingPage,
  FieldsPage,
  ForgotPasswordPage,
  HomePage,
  PromotionsPage,
  ResetPasswordPage,
  StaffPage,
  VerifyEmailPage
} from './pages'
import api from './services/api'
import './styles/app.css'
import { tomorrow } from './utils/format'

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
  const [paymentOption, setPaymentOption] = useState('deposit')
  const [checkout, setCheckout] = useState(null)
  const [checkoutLoading, setCheckoutLoading] = useState(false)
  const [checkoutError, setCheckoutError] = useState('')
  const [paypalConfig, setPaypalConfig] = useState(null)
  const [paypalConfigError, setPaypalConfigError] = useState('')
  const [selectedBookingDetail, setSelectedBookingDetail] = useState(null)
  const [billingLoading, setBillingLoading] = useState(false)
  const [billingError, setBillingError] = useState('')
  const [verifyResult, setVerifyResult] = useState({ status: 'idle', message: '' })
  const [loginErrors, setLoginErrors] = useState({})
  const [registerErrors, setRegisterErrors] = useState({})
  const [registerLoading, setRegisterLoading] = useState(false)
  const [forgotEmail, setForgotEmail] = useState('')
  const [forgotSent, setForgotSent] = useState(false)
  const [forgotLoading, setForgotLoading] = useState(false)
  const [forgotError, setForgotError] = useState('')
  const [resetPassword, setResetPassword] = useState('')
  const [resetConfirmPassword, setResetConfirmPassword] = useState('')
  const [showResetPassword, setShowResetPassword] = useState(false)
  const [resetResult, setResetResult] = useState(null)
  const [resetLoading, setResetLoading] = useState(false)
  const [resetErrors, setResetErrors] = useState({})
  const [resetPasswordToken, setResetPasswordToken] = useState(null)
  const [resetTokenChecking, setResetTokenChecking] = useState(true)

  const [loginForm, setLoginForm] = useState({ emailOrPhone: 'customer@goalzone.local', password: demoPassword })
  const [registerForm, setRegisterForm] = useState({
    fullName: '',
    email: '',
    phone: '',
    password: '',
    confirmPassword: ''
  })
  const [issueDraft, setIssueDraft] = useState({
    title: 'Loose goal net',
    description: 'Customer reported a field issue before check-in.'
  })

  const customers = useMemo(() => users.filter(user => user.role === 'Customer'), [users])
  const selectedSlot = slots.find(slot => slot.slotId === Number(selectedSlotId))
  const selectedBooking = bookings.find(booking => booking.bookingId === Number(selectedBookingId)) || bookings[0]
  const isStaff = currentUser?.role === 'Staff'
  const isAdmin = currentUser?.role === 'Admin'
  const canOperate = isStaff
  const userBookings = currentUser?.role === 'Customer'
    ? bookings.filter(booking => booking.customerId === currentUser.userId)
    : []
  const brandRoute = useMemo(() => {
    if (currentUser?.role === 'Admin') return 'admin'
    if (currentUser?.role === 'Staff') return 'staff'
    return 'home'
  }, [currentUser])

  useEffect(() => {
    refreshAll()
  }, [])

  useEffect(() => {
    let cancelled = false
    api.get('/payments/paypal/config')
      .then(response => {
        if (!cancelled) setPaypalConfig(response.data)
      })
      .catch(error => {
        if (!cancelled) {
          setPaypalConfigError(error.response?.data?.error || 'PayPal Sandbox is not available')
        }
      })
    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    const handlePopState = () => setCurrentPage(pageFromPath(window.location.pathname))
    window.addEventListener('popstate', handlePopState)
    return () => window.removeEventListener('popstate', handlePopState)
  }, [])

  useEffect(() => {
    saveStoredUser(currentUser)
  }, [currentUser])

  useEffect(() => {
    loadSlots()
  }, [searchDate, fieldTypeFilter])

  useEffect(() => {
    if (currentPage !== 'verifyEmail') return undefined
    let cancelled = false
    const params = new URLSearchParams(window.location.search)
    const userId = params.get('userId')
    const token = params.get('token')

    if (!userId || !token) {
      setVerifyResult({ status: 'error', message: 'Verification link is missing required information.' })
      return undefined
    }

    setVerifyResult({ status: 'loading', message: 'Verifying your email...' })
    api.post('/account/email/verify', { userId: Number(userId), token })
      .then(response => {
        if (cancelled) return
        setVerifyResult({ status: 'success', message: response.data.message || 'Email verified.' })
        if (response.data.user) {
          setCurrentUser(response.data.user)
        }
      })
      .catch(error => {
        if (cancelled) return
        setVerifyResult({ status: 'error', message: error.response?.data?.error || 'Verification link is invalid or expired.' })
      })

    return () => {
      cancelled = true
    }
  }, [currentPage])

  useEffect(() => {
    if (currentPage !== 'resetPassword') return undefined
    let cancelled = false
    setResetTokenChecking(true)
    setResetPasswordToken(null)
    setResetResult(null)
    const params = new URLSearchParams(window.location.search)
    const userId = params.get('userId')
    const token = params.get('token')

    if (!userId || !token) {
      setResetTokenChecking(false)
      setResetResult({ status: 'error', message: 'Reset link is missing required information.' })
      return undefined
    }

    api.post('/account/validate-reset-token', { userId: Number(userId), token })
      .then(() => {
        if (!cancelled) {
          setResetPasswordToken({ userId: Number(userId), token })
          setResetTokenChecking(false)
        }
      })
      .catch(error => {
        if (!cancelled) {
          setResetPasswordToken(null)
          setResetTokenChecking(false)
          setResetResult({ status: 'error', message: error.response?.data?.error || 'Reset link is invalid or expired.' })
        }
      })

    return () => { cancelled = true }
  }, [currentPage])

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

  useEffect(() => {
    if (!currentUser || !bookings.length) {
      setSelectedBookingId(null)
      return
    }
    const accessibleBookings = currentUser.role === 'Customer'
      ? bookings.filter(booking => booking.customerId === currentUser.userId)
      : bookings
    setSelectedBookingId(current => accessibleBookings.some(booking => booking.bookingId === Number(current))
      ? current
      : accessibleBookings[0]?.bookingId || null)
  }, [bookings, currentUser?.role, currentUser?.userId])

  useEffect(() => {
    const summary = bookings.find(booking => booking.bookingId === Number(selectedBookingId))
    const canView = summary && currentUser && (
      currentUser.role !== 'Customer' || summary.customerId === currentUser.userId
    )
    if (!canView) {
      setSelectedBookingDetail(null)
      setBillingError('')
      return undefined
    }

    let cancelled = false
    setBillingLoading(true)
    setBillingError('')
    api.get(`/bookings/${selectedBookingId}`)
      .then(response => {
        if (!cancelled) setSelectedBookingDetail(response.data)
      })
      .catch(error => {
        if (!cancelled) {
          setSelectedBookingDetail(null)
          setBillingError(error.response?.data?.error || 'Could not load invoice details')
        }
      })
      .finally(() => {
        if (!cancelled) setBillingLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [bookings, currentUser?.role, currentUser?.userId, selectedBookingId])

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
      if (currentUser) {
        const freshUser = userRes.data.find(u => u.userId === currentUser.userId)
        if (freshUser) {
          setCurrentUser(freshUser)
        }
      }
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
      setSelectedBookingId(current => bookingRes.data.some(booking => booking.bookingId === Number(current))
        ? current
        : bookingRes.data[0]?.bookingId || null)
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
      const serverMessage = result?.data?.message
      setNotice(serverMessage || successMessage)
      setActionPanel({
        kind: 'success',
        title: successMessage,
        message: serverMessage || actionMessage(successMessage)
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
    if (successMessage.includes('Account created')) return 'Check your inbox to verify email before online booking.'
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

  function validateLoginForm() {
    const errors = {}
    if (!loginForm.emailOrPhone.trim()) errors.emailOrPhone = 'Email or phone is required.'
    if (!loginForm.password) errors.password = 'Password is required.'
    return errors
  }

  function validateRegisterForm() {
    const errors = {}
    const name = registerForm.fullName.trim()
    const email = registerForm.email.trim()
    const phone = registerForm.phone.trim()
    if (name.length < 2) errors.fullName = 'Enter at least 2 characters.'
    if (!emailPattern.test(email)) errors.email = 'Enter a valid email address.'
    if (!phonePattern.test(phone)) errors.phone = 'Use a 10-digit phone number that starts with 0.'
    const issues = passwordIssues(registerForm.password)
    if (issues.length) errors.password = issues[0]
    if (registerForm.password !== registerForm.confirmPassword) errors.confirmPassword = 'Passwords do not match.'
    return errors
  }

  function updateLoginForm(field, value) {
    setLoginForm({ ...loginForm, [field]: value })
    setLoginErrors(({ [field]: _ignored, ...rest }) => rest)
  }

  function updateRegisterForm(field, value) {
    setRegisterForm({ ...registerForm, [field]: value })
    setRegisterErrors(({ [field]: _ignored, ...rest }) => rest)
  }

  async function login(emailOrPhone = loginForm.emailOrPhone) {
    const errors = validateLoginForm()
    if (Object.keys(errors).length) {
      setLoginErrors(errors)
      setActionPanel({
        kind: 'error',
        title: 'Check login details',
        message: Object.values(errors)[0]
      })
      return
    }
    const response = await runAction(async () => api.post('/account/login', {
      emailOrPhone: emailOrPhone.trim(),
      password: loginForm.password
    }), 'Signed in')
    if (response?.data?.user) {
      const user = response.data.user
      setCurrentUser(user)
      setLoginForm({ emailOrPhone: '', password: '' })
      setLoginErrors({})
      setAuthMode('login')
      if (user.role === 'Admin') {
        navigatePage('admin')
      } else if (user.role === 'Staff') {
        navigatePage('staff')
      } else {
        navigatePage('account')
      }
    }
  }

  async function register() {
    const errors = validateRegisterForm()
    if (Object.keys(errors).length) {
      setRegisterErrors(errors)
      setActionPanel({
        kind: 'error',
        title: 'Check registration details',
        message: Object.values(errors)[0]
      })
      return
    }
    const payload = {
      ...registerForm,
      fullName: registerForm.fullName.trim(),
      email: registerForm.email.trim(),
      phone: registerForm.phone.trim()
    }
    setRegisterLoading(true)
    try {
      const response = await runAction(async () => api.post('/account/register', payload), 'Account created')
      if (response?.data) {
        const user = response.data.user || response.data
        setCurrentUser(user)
        setAuthMode('login')
        setRegisterForm({ fullName: '', email: '', phone: '', password: '', confirmPassword: '' })
        if (response.data.verificationRequired) {
          setNotice(response.data.message || 'Account created. Check your inbox to verify email before online booking.')
        }
        navigatePage('account')
      }
    } finally {
      setRegisterLoading(false)
    }
  }

  async function resendVerification() {
    if (!currentUser) return
    await runAction(async () => api.post('/account/email/resend', {
      userId: currentUser.userId
    }), 'Verification email sent')
  }

  async function sendResetLink() {
    const email = forgotEmail.trim()
    if (!emailPattern.test(email)) {
      setForgotError('Enter a valid email address.')
      setActionPanel({ kind: 'error', title: 'Check email', message: 'Enter a valid email address.' })
      return
    }
    setForgotLoading(true)
    setForgotError('')
    try {
      const response = await api.post('/account/forgot-password', { email })
      setForgotSent(true)
      setActionPanel({ kind: 'success', title: 'Reset link sent', message: response.data?.message || 'Check your inbox for the reset link.' })
    } catch (error) {
      const message = error.response?.data?.error || 'Could not send reset link'
      setForgotError(message)
      setActionPanel({ kind: 'error', title: 'Failed to send', message })
    } finally {
      setForgotLoading(false)
    }
  }

  async function submitReset() {
    if (!resetPasswordToken) return
    const errors = {}
    const issues = passwordIssues(resetPassword)
    if (issues.length) errors.password = issues[0]
    if (resetPassword !== resetConfirmPassword) errors.confirmPassword = 'Passwords do not match.'
    if (Object.keys(errors).length) {
      setResetErrors(errors)
      return
    }
    setResetLoading(true)
    setResetErrors({})
    try {
      const response = await api.post('/account/reset-password', {
        userId: resetPasswordToken.userId,
        token: resetPasswordToken.token,
        newPassword: resetPassword,
        confirmPassword: resetConfirmPassword
      })
      setResetResult({ status: 'success', message: response.data?.message || 'Password reset successfully.' })
      setActionPanel({ kind: 'success', title: 'Password reset', message: response.data?.message || 'Password reset successfully.' })
    } catch (error) {
      const message = error.response?.data?.error || 'Could not reset password'
      setResetResult({ status: 'error', message })
      setActionPanel({ kind: 'error', title: 'Reset failed', message })
    } finally {
      setResetLoading(false)
    }
  }

  function updateResetForm(field, value) {
    if (field === 'resetPassword') setResetPassword(value)
    if (field === 'resetConfirmPassword') setResetConfirmPassword(value)
    setResetErrors(({ [field]: _ignored, ...rest }) => rest)
  }

  async function saveProfile(profile) {
    if (!currentUser) return
    const response = await runAction(async () => api.put(`/account/users/${currentUser.userId}/profile`, profile), 'Profile updated')
    if (response?.data) {
      setCurrentUser(response.data)
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

  async function createBooking(source = 'online', option = 'deposit') {
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
    const response = await runAction(async () => {
      const bookingResponse = await api.post('/bookings', {
        customerId: bookingCustomerId(),
        staffId: source === 'walk_in' ? Number(selectedStaffId) : null,
        slotId: Number(selectedSlotId),
        bookingSource: source,
        promotionCode,
        services: serviceSelections(),
        note: source === 'walk_in' ? 'Created by staff at venue' : 'Created from customer website'
      })
      if (source !== 'walk_in') return bookingResponse
      return api.post('/payments/capture', {
        bookingId: bookingResponse.data.bookingId,
        createdById: currentUser.userId,
        paymentOption: option,
        paymentMethod: 'cash',
        amount: null,
        success: true
      })
    }, source === 'walk_in' ? 'Walk-in booking and payment recorded' : 'Booking created')

    if (response?.data) {
      setSelectedBookingId(response.data.bookingId)
      setSelectedBookingDetail(response.data)
      setSelectedServices({})
      setPromotionCode('')
      navigatePage(source === 'walk_in' ? 'staff' : 'account')
    }
  }

  async function preparePayPalBooking() {
    if (!currentUser) {
      navigatePage('login')
      throw new Error('Sign in before starting PayPal checkout')
    }
    if (currentUser.role === 'Customer' && !currentUser.emailVerified) {
      navigatePage('account')
      throw new Error('Verify your email before online booking')
    }

    setLoading(true)
    try {
      const response = await api.post('/bookings', {
        customerId: bookingCustomerId(),
        staffId: null,
        slotId: Number(selectedSlotId),
        bookingSource: 'online',
        promotionCode,
        services: serviceSelections(),
        note: 'Created for PayPal Sandbox checkout'
      })
      setSelectedBookingId(response.data.bookingId)
      setSelectedBookingDetail(response.data)
      setNotice('Booking reserved. Complete approval in PayPal Sandbox.')
      return response.data
    } finally {
      setLoading(false)
    }
  }

  async function completePayPalPayment(detail) {
    setSelectedBookingId(detail.bookingId)
    setSelectedBookingDetail(detail)
    setSelectedServices({})
    setPromotionCode('')
    setNotice('PayPal Sandbox payment completed')
    setActionPanel({
      kind: 'success',
      title: 'PayPal payment completed',
      message: `${detail.bookingCode} is confirmed and its invoice is ready.`
    })
    await Promise.all([refreshAll(), loadSlots()])
    navigatePage('account')
  }

  function cancelPayPalPayment() {
    setNotice('PayPal checkout was cancelled and the slot was released.')
    setActionPanel({
      kind: 'error',
      title: 'PayPal checkout cancelled',
      message: 'No payment was captured. The pending booking expired and the slot is available again.'
    })
    refreshAll()
    loadSlots()
  }

  function failPayPalPayment(message) {
    setNotice(message)
    setActionPanel({
      kind: 'error',
      title: 'PayPal checkout failed',
      message
    })
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

  async function updateCustomerRestriction(customer, bookingRestricted) {
    await runAction(async () => api.put(`/account/users/${customer.userId}/restriction`, {
      bookingRestricted,
      restrictionReason: bookingRestricted ? 'Restricted by admin from account management' : ''
    }), bookingRestricted ? 'Customer booking restricted' : 'Customer booking restored')
  }

  return (
    <div className="siteShell">
      <a className="skipLink" href="#main">Skip to content</a>
      <header className="siteHeader">
        <button className="brandButton" onClick={() => navigatePage(brandRoute)} aria-label="Go to home">
          <span className="brandMark">GZ</span>
          <span>GoalZone</span>
        </button>
        <nav className={mobileOpen ? 'mainNav open' : 'mainNav'} aria-label="Primary navigation">
          {(!currentUser || currentUser.role !== 'Admin') && (
            <>
              <button className={currentPage === 'fields' ? 'active' : ''} onClick={() => navigatePage('fields')}>Fields</button>
              <button className={currentPage === 'booking' ? 'active' : ''} onClick={() => navigatePage('booking')}>Booking</button>
            </>
          )}
          {(!currentUser || currentUser.role === 'Customer') && (
            <button className={currentPage === 'promotions' ? 'active' : ''} onClick={() => navigatePage('promotions')}>Promotions</button>
          )}
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
          <HomePage
            searchDate={searchDate}
            setSearchDate={setSearchDate}
            fieldTypeFilter={fieldTypeFilter}
            setFieldTypeFilter={setFieldTypeFilter}
            fieldTypes={fieldTypes}
            navigatePage={navigatePage}
          />
        )}

        {(currentPage === 'home' || currentPage === 'fields') && (
          <FieldsPage
            fields={fields}
            slots={slots}
            searchDate={searchDate}
            setSearchDate={setSearchDate}
            setFieldTypeFilter={setFieldTypeFilter}
            setSelectedSlotId={setSelectedSlotId}
            navigatePage={navigatePage}
          />
        )}

        {currentPage === 'booking' && (
          <BookingPage
            searchDate={searchDate}
            setSearchDate={setSearchDate}
            fieldTypeFilter={fieldTypeFilter}
            setFieldTypeFilter={setFieldTypeFilter}
            fieldTypes={fieldTypes}
            canOperate={canOperate}
            selectedCustomerId={selectedCustomerId}
            setSelectedCustomerId={setSelectedCustomerId}
            customers={customers}
            slots={slots}
            selectedSlotId={selectedSlotId}
            setSelectedSlotId={setSelectedSlotId}
            services={services}
            selectedServices={selectedServices}
            setSelectedServices={setSelectedServices}
            selectedSlot={selectedSlot}
            promotionCode={promotionCode}
            setPromotionCode={setPromotionCode}
            checkout={checkout}
            checkoutLoading={checkoutLoading}
            checkoutError={checkoutError}
            paymentOption={paymentOption}
            setPaymentOption={setPaymentOption}
            paypalConfig={paypalConfig}
            paypalConfigError={paypalConfigError}
            preparePayPalBooking={preparePayPalBooking}
            completePayPalPayment={completePayPalPayment}
            cancelPayPalPayment={cancelPayPalPayment}
            failPayPalPayment={failPayPalPayment}
            createBooking={createBooking}
            currentUser={currentUser}
          />
        )}

        {(currentPage === 'login' || (currentPage === 'account' && !currentUser)) && (
          <AuthPage
            currentUser={currentUser}
            authMode={authMode}
            setAuthMode={setAuthMode}
            loginErrors={loginErrors}
            registerErrors={registerErrors}
            loginForm={loginForm}
            registerForm={registerForm}
            updateLoginForm={updateLoginForm}
            updateRegisterForm={updateRegisterForm}
            showLoginPassword={showLoginPassword}
            setShowLoginPassword={setShowLoginPassword}
            showRegisterPassword={showRegisterPassword}
            setShowRegisterPassword={setShowRegisterPassword}
            login={login}
            register={register}
            registerLoading={registerLoading}
            logout={logout}
            navigatePage={navigatePage}
            resendVerification={resendVerification}
          />
        )}

        {currentPage === 'account' && currentUser && (
          <AccountPage
            currentUser={currentUser}
            userBookings={userBookings}
            payments={payments}
            membership={membership}
            notifications={notifications}
            selectedBookingId={selectedBookingId}
            setSelectedBookingId={setSelectedBookingId}
            selectedBookingDetail={selectedBookingDetail}
            billingLoading={billingLoading}
            billingError={billingError}
            resendVerification={resendVerification}
            onSaveProfile={saveProfile}
          />
        )}

        {currentPage === 'verifyEmail' && (
          <VerifyEmailPage
            verifyResult={verifyResult}
            currentUser={currentUser}
            navigatePage={navigatePage}
            resendVerification={resendVerification}
          />
        )}

        {currentPage === 'forgotPassword' && (
          <ForgotPasswordPage
            forgotEmail={forgotEmail}
            setForgotEmail={setForgotEmail}
            forgotSent={forgotSent}
            forgotLoading={forgotLoading}
            forgotError={forgotError}
            sendResetLink={sendResetLink}
            navigatePage={navigatePage}
          />
        )}

        {currentPage === 'resetPassword' && (
          <ResetPasswordPage
            resetPassword={resetPassword}
            resetConfirmPassword={resetConfirmPassword}
            resetResult={resetResult}
            resetLoading={resetLoading}
            resetErrors={resetErrors}
            showResetPassword={showResetPassword}
            setShowResetPassword={setShowResetPassword}
            resetPasswordToken={resetPasswordToken}
            resetTokenChecking={resetTokenChecking}
            updateResetForm={updateResetForm}
            submitReset={submitReset}
          />
        )}

        {(currentPage === 'home' || currentPage === 'promotions') && (
          <PromotionsPage promotions={promotions} />
        )}

        {currentPage === 'staff' && canOperate && (
          <StaffPage
            bookings={bookings}
            selectedBookingId={selectedBookingId}
            setSelectedBookingId={setSelectedBookingId}
            selectedBooking={selectedBooking}
            selectedBookingDetail={selectedBookingDetail}
            billingLoading={billingLoading}
            billingError={billingError}
            updateBooking={updateBooking}
            capturePayment={capturePayment}
            issueDraft={issueDraft}
            setIssueDraft={setIssueDraft}
            createIssue={createIssue}
            issues={issues}
            createRefund={createRefund}
            refunds={refunds}
          />
        )}

        {currentPage === 'staff' && !canOperate && (
          <AccessPanel title="Staff access" text="Login with a staff or admin account to use daily operation tools." onLogin={() => navigatePage('login')} />
        )}

        {currentPage === 'admin' && isAdmin && (
          <AdminPage
            reports={reports}
            settings={settings}
            fieldTypes={fieldTypes}
            updateDepositSetting={updateDepositSetting}
            customers={customers}
            updateCustomerRestriction={updateCustomerRestriction}
            refreshAll={refreshAll}
          />
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
          {(!currentUser || currentUser.role !== 'Admin') && (
            <>
              <button onClick={() => navigatePage('fields')}>Fields</button>
              <button onClick={() => navigatePage('booking')}>Booking</button>
            </>
          )}
          {currentUser?.role === 'Admin' && <button onClick={() => navigatePage('admin')}>Admin</button>}
          {currentUser?.role === 'Staff' && <button onClick={() => navigatePage('staff')}>Staff</button>}
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

export default App
