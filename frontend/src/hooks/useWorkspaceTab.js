import { useCallback, useEffect, useState } from 'react'

function tabFromUrl(defaultTab, allowedTabs, queryKey) {
  const requestedTab = new URLSearchParams(window.location.search).get(queryKey)
  return allowedTabs.includes(requestedTab) ? requestedTab : defaultTab
}

export function useWorkspaceTab(defaultTab, allowedTabs, queryKey = 'tab') {
  const [activeTab, setActiveTab] = useState(() => tabFromUrl(defaultTab, allowedTabs, queryKey))

  useEffect(() => {
    const syncTabFromHistory = () => setActiveTab(tabFromUrl(defaultTab, allowedTabs, queryKey))
    window.addEventListener('popstate', syncTabFromHistory)
    return () => window.removeEventListener('popstate', syncTabFromHistory)
  }, [allowedTabs, defaultTab, queryKey])

  const selectTab = useCallback((nextTab) => {
    if (!allowedTabs.includes(nextTab)) return
    setActiveTab(nextTab)

    const nextUrl = new URL(window.location.href)
    if (nextTab === defaultTab) nextUrl.searchParams.delete(queryKey)
    else nextUrl.searchParams.set(queryKey, nextTab)

    const nextLocation = `${nextUrl.pathname}${nextUrl.search}${nextUrl.hash}`
    const currentLocation = `${window.location.pathname}${window.location.search}${window.location.hash}`
    if (nextLocation !== currentLocation) window.history.pushState({}, '', nextLocation)
  }, [allowedTabs, defaultTab, queryKey])

  return [activeTab, selectTab]
}
