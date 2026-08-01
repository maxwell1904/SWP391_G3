import { Badge } from '@mantine/core'

export function DataList({ items }) {
  if (!items.length) return <p className="emptyText">No records yet.</p>
  return (
    <div className="dataList">
      {items.map((item, index) => (
        <div className={`dataRow${item.wrapMeta ? ' dataRowWrap' : ''}`} key={`${item.title}-${index}`}>
          <span>
            <strong>{item.title}</strong>
            <small>{item.meta}</small>
          </span>
          <Badge className="listBadge" variant="light" color="green">{item.value}</Badge>
        </div>
      ))}
    </div>
  )
}
