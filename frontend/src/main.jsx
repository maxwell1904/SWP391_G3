import React from 'react'
import ReactDOM from 'react-dom/client'
import { MantineProvider, createTheme } from '@mantine/core'
import '@mantine/core/styles.css'
import App from './App'

const theme = createTheme({
  primaryColor: 'green',
  fontFamily: "'Segoe UI', Arial, sans-serif",
  headings: {
    fontFamily: "'Segoe UI', Arial, sans-serif"
  },
  defaultRadius: 'xs',
  colors: {
    green: [
      '#eef9f0',
      '#dff1e5',
      '#b9dfc7',
      '#8fcba7',
      '#63b684',
      '#3e9d67',
      '#176f46',
      '#145c3c',
      '#0f3f2a',
      '#0a2a1c'
    ]
  }
})

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <MantineProvider theme={theme}>
      <App />
    </MantineProvider>
  </React.StrictMode>
)
