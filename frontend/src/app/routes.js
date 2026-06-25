export const pageRoutes = {
  home: '/',
  fields: '/fields',
  booking: '/booking',
  login: '/login',
  account: '/account',
  verifyEmail: '/verify-email',
  promotions: '/promotions',
  staff: '/staff',
  admin: '/admin'
}

export const pageFromPath = pathname => {
  const match = Object.entries(pageRoutes).find(([, path]) => path === pathname)
  return match?.[0] || 'home'
}
