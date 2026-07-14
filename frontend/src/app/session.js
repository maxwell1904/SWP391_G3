const storageKey = 'goalzone.currentUser'
const tokenKey = 'goalzone.token'

export const loadStoredUser = () => {
  try {
    return JSON.parse(window.localStorage.getItem(storageKey)) || null
  } catch {
    return null
  }
}

export const saveStoredUser = user => {
  if (user) {
    window.localStorage.setItem(storageKey, JSON.stringify(user))
  } else {
    window.localStorage.removeItem(storageKey)
  }
}

export const loadStoredToken = () => {
  return window.localStorage.getItem(tokenKey) || null
}

export const saveStoredToken = token => {
  if (token) {
    window.localStorage.setItem(tokenKey, token)
  } else {
    window.localStorage.removeItem(tokenKey)
  }
}

