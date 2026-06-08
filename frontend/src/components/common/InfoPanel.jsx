import { Paper, Title } from '@mantine/core'

export function InfoPanel({ title, children }) {
  return (
    <Paper component="article" className="infoPanel" withBorder shadow="xs">
      <Title order={3}>{title}</Title>
      {children}
    </Paper>
  )
}
