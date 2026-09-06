const AVATAR_COLORS = ['#0F7B6C', '#3B6E8F', '#8A5FBF', '#C9762F', '#B5484F', '#4C7A3D', '#286E8C']

export function avatarColorFor(userId: number): string {
  return AVATAR_COLORS[userId % AVATAR_COLORS.length]
}
