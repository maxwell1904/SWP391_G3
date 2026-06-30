import axios from 'axios'
import { loadStoredToken } from '../app/session'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://127.0.0.1:8080/api'
})

api.interceptors.request.use(
  config => {
    const token = loadStoredToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

export default api

