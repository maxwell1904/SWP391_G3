import { useEffect, useState } from 'react'
import api from './services/api'

function App() {
  const [message, setMessage] = useState('Loading...')

  useEffect(() => {
    api.get('/test')
      .then(res => setMessage(res.data))
      .catch(() => setMessage('Cannot connect backend'))
  }, [])

  return (
    <div style={{ padding: '40px', fontFamily: 'Arial' }}>
      <h1>SWP391 - Football Field Booking System</h1>
      <h2>{message}</h2>
    </div>
  )
}

export default App
