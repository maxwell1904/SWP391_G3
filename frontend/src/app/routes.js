export const pageRoutes = {
  home: '/',
  fields: '/fields',
  booking: '/booking',
  login: '/login',
  account: '/account',
  verifyEmail: '/verify-email',
  forgotPassword: '/forgot-password',
  resetPassword: '/reset-password',
  promotions: '/promotions',
  'membership-rules': '/membership/rules',
  'membership-benefits': '/membership/benefits',
  staff: '/staff',
  admin: '/admin'
}

export const pageFromPath = pathname => {
  const match = Object.entries(pageRoutes).find(([, path]) => path === pathname)
  return match?.[0] || 'home'
}
