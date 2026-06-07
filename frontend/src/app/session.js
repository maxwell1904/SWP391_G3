const storageKey = 'goalzone.currentUser'

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
