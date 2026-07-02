import { Paper, Title } from '@mantine/core'

export function InfoPanel({ title, children, className = '' }) {
  return (
    <Paper component="article" className={`infoPanel ${className}`.trim()} withBorder shadow="xs">
      <Title order={3}>{title}</Title>
      {children}
    </Paper>
  )
}
