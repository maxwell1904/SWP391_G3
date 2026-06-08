export function SectionIntro({ kicker, title, text }) {
  return (
    <div className="sectionIntro">
      <span>{kicker}</span>
      <h2>{title}</h2>
      <p>{text}</p>
    </div>
  )
}
