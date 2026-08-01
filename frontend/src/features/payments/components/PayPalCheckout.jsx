import { useEffect, useRef, useState } from 'react'
import api from '../../../services/api'

let paypalSdkPromise
let paypalSdkKey

export function PayPalCheckout({
  config,
  disabled,
  paymentOption,
  onPrepareBooking,
  onPaymentComplete,
  onCancel,
  onError
}) {
  const containerRef = useRef(null)
  const bookingRef = useRef(null)
  const callbacksRef = useRef({ onPrepareBooking, onPaymentComplete, onCancel, onError })
  const [status, setStatus] = useState('loading')
  const [error, setError] = useState('')

  useEffect(() => {
    callbacksRef.current = { onPrepareBooking, onPaymentComplete, onCancel, onError }
  }, [onPrepareBooking, onPaymentComplete, onCancel, onError])

  useEffect(() => {
    if (!config || disabled || !config.enabled) {
      setStatus(config && !config.enabled ? 'unavailable' : 'loading')
      return undefined
    }

    let active = true
    let buttons
    setStatus('loading')
    setError('')

    loadPayPalSdk(config)
      .then(paypal => {
        if (!active || !containerRef.current) return
        buttons = paypal.Buttons({
          style: {
            layout: 'vertical',
            color: 'gold',
            shape: 'rect',
            label: 'paypal',
            height: 44
          },
          onInit: () => {
            if (active) setStatus('ready')
          },
          createOrder: async () => {
            try {
              setStatus('processing')
              setError('')
              const booking = bookingRef.current || await callbacksRef.current.onPrepareBooking()
              bookingRef.current = booking
              const response = await api.post(`/bookings/${booking.bookingId}/paypal/orders`, {
                paymentOption
              })
              setStatus('ready')
              return response.data.orderId
            } catch (checkoutError) {
              const message = errorMessage(checkoutError)
              setError(message)
              setStatus('error')
              throw new Error(message)
            }
          },
          onApprove: async data => {
            setStatus('processing')
            const booking = bookingRef.current
            const response = await api.post(`/bookings/${booking.bookingId}/paypal/orders/${data.orderID}/capture`, {})
            await callbacksRef.current.onPaymentComplete(response.data)
            setStatus('complete')
          },
          onCancel: async data => {
            try {
              const booking = bookingRef.current
              if (booking && data.orderID) {
                await api.post(`/bookings/${booking.bookingId}/paypal/orders/${data.orderID}/cancel`)
                bookingRef.current = null
              }
              setStatus('ready')
              callbacksRef.current.onCancel?.(data)
            } catch (checkoutError) {
              const message = errorMessage(checkoutError)
              setError(message)
              setStatus('error')
              callbacksRef.current.onError?.(message)
            }
          },
          onError: checkoutError => {
            const message = errorMessage(checkoutError)
            setError(message)
            setStatus('error')
            callbacksRef.current.onError?.(message)
          }
        })
        return buttons.render(containerRef.current)
      })
      .then(() => {
        if (active) setStatus(current => current === 'processing' ? current : 'ready')
      })
      .catch(checkoutError => {
        if (!active) return
        const message = errorMessage(checkoutError)
        setError(message)
        setStatus('error')
        callbacksRef.current.onError?.(message)
      })

    return () => {
      active = false
      if (containerRef.current) containerRef.current.innerHTML = ''
    }
  }, [config, disabled, paymentOption])

  if (!config) return <p className="emptyText">Loading PayPal...</p>
  if (!config.enabled) {
    return <p className="errorText">Online payment is currently unavailable. Please try again later.</p>
  }

  return (
    <div className="paypalCheckout">
      <div ref={containerRef} />
      {status === 'loading' && <p className="emptyText">Loading PayPal...</p>}
      {status === 'processing' && <p className="paypalStatus">Processing secure payment...</p>}
      {error && <p className="errorText">{error}</p>}
    </div>
  )
}

function loadPayPalSdk(config) {
  const key = `${config.clientId}:${config.currency}`
  if (typeof window.paypal?.Buttons === 'function' && paypalSdkKey === key) {
    return Promise.resolve(window.paypal)
  }
  if (paypalSdkPromise && paypalSdkKey === key) return paypalSdkPromise

  document.getElementById('goalzone-paypal-sdk')?.remove()
  delete window.paypal
  paypalSdkKey = key
  paypalSdkPromise = new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.id = 'goalzone-paypal-sdk'
    script.src = `https://www.paypal.com/sdk/js?client-id=${encodeURIComponent(config.clientId)}&currency=${encodeURIComponent(config.currency)}&intent=capture&components=buttons`
    script.async = true
    script.onload = () => {
      if (typeof window.paypal?.Buttons !== 'function') {
        paypalSdkPromise = undefined
        reject(new Error('PayPal SDK loaded without the Buttons component'))
        return
      }
      resolve(window.paypal)
    }
    script.onerror = () => {
      paypalSdkPromise = undefined
      reject(new Error('Could not load the PayPal SDK'))
    }
    document.head.appendChild(script)
  })
  return paypalSdkPromise
}

function errorMessage(error) {
  return error?.response?.data?.error
    || error?.response?.data?.message
    || error?.message
    || 'PayPal checkout failed'
}
