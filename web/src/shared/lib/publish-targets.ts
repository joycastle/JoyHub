import type { PublishTarget } from '@/api/types'

export function resolveDefaultPublishTarget(targets: PublishTarget[]): PublishTarget | undefined {
  return targets.find((target) => target.slug === 'global') ?? targets[0]
}
