import { Badge, Group, Paper, SimpleGrid, Text, Title } from '@mantine/core'

export function WorkspaceHeader({ kicker, title, text, status, metrics = [] }) {
  return (
    <Paper className="workspaceHeader" withBorder>
      <div className="workspaceIntro">
        <span>{kicker}</span>
        <Title order={2}>{title}</Title>
        <Text>{text}</Text>
      </div>
      <div className="workspaceSummary">
        {status && (
          <Group justify="flex-end">
            <Badge className={`workspaceStatus ${status.tone || ''}`} size="lg" variant="light">
              {status.label}: {status.value}
            </Badge>
          </Group>
        )}
        <SimpleGrid className="workspaceMetricStrip" cols={{ base: 2, sm: Math.min(metrics.length || 1, 4) }} spacing="xs">
          {metrics.map(metric => (
            <div className="workspaceMetric" key={metric.label}>
              <span>{metric.label}</span>
              <strong>{metric.value}</strong>
            </div>
          ))}
        </SimpleGrid>
      </div>
    </Paper>
  )
}
