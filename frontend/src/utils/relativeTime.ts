export function formatRelativeTime(isoDate: string, now: number = Date.now()): string {
  const diffMs = Math.max(0, now - new Date(isoDate).getTime())
  const minutes = Math.floor(diffMs / 60000)

  if (minutes < 1) return 'たった今'
  if (minutes < 60) return `${minutes}分前`

  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}時間前`

  const days = Math.floor(hours / 24)
  return `${days}日前`
}
