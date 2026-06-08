import { Button } from '@mantine/core'
import { InfoPanel, WorkspaceHeader } from '../components/common'
import { DataList, MetricGrid } from '../components/data'
import { formatMoney } from '../utils/format'

export function AdminPage({ reports, settings, updateDepositSetting, customers, updateCustomerRestriction }) {
  const restrictedCustomers = customers.filter(customer => customer.bookingRestricted).length

  return (
    <section id="admin" className="section adminSection">
      <WorkspaceHeader
        kicker="Admin workspace"
        title="Management and reports"
        text="Review revenue, booking utilization, customer access, and lightweight deposit configuration."
        status={{
          label: 'Scope',
          value: 'Demo admin',
          tone: 'success'
        }}
        metrics={[
          { label: 'Revenue', value: formatMoney(reports?.totalRevenue) },
          { label: 'Bookings', value: reports?.bookingCount || 0 },
          { label: 'Restricted', value: restrictedCustomers },
          { label: 'Settings', value: settings.length }
        ]}
      />
      <div className="roleGrid adminGrid">
        <InfoPanel title="Revenue report">
          <MetricGrid metrics={[
            ['Revenue', formatMoney(reports?.totalRevenue)],
            ['Bookings', reports?.bookingCount || 0],
            ['Completed', reports?.completedCount || 0],
            ['Cancelled', reports?.cancelledCount || 0]
          ]} />
        </InfoPanel>
        <InfoPanel title="Field utilization">
          <DataList items={Object.entries(reports?.fieldUtilization || {}).map(([field, count]) => ({
            title: field,
            meta: 'Bookings',
            value: count
          }))} />
        </InfoPanel>
        <InfoPanel title="Deposit rule">
          <DataList items={settings.map(setting => ({
            title: setting.settingKey,
            meta: setting.description,
            value: setting.settingValue
          }))} />
          <div className="buttonRow">
            <Button variant="light" onClick={() => updateDepositSetting(30)}>Set 30%</Button>
            <Button variant="light" onClick={() => updateDepositSetting(50)}>Set 50%</Button>
          </div>
        </InfoPanel>
        <InfoPanel title="Customer booking access">
          <div className="customerAdminList">
            {customers.map(customer => (
              <div className="customerAdminRow" key={customer.userId}>
                <span>
                  <strong>{customer.fullName}</strong>
                  <small>{customer.email} · {customer.phone || 'no phone'}</small>
                  {customer.bookingRestricted && <small>{customer.restrictionReason || 'Booking restricted'}</small>}
                </span>
                <Button
                  variant={customer.bookingRestricted ? 'filled' : 'light'}
                  color={customer.bookingRestricted ? 'green' : 'red'}
                  onClick={() => updateCustomerRestriction(customer, !customer.bookingRestricted)}
                >
                  {customer.bookingRestricted ? 'Restore' : 'Restrict'}
                </Button>
              </div>
            ))}
          </div>
        </InfoPanel>
      </div>
    </section>
  )
}
