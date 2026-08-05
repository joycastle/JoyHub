import { useMutation } from '@tanstack/react-query'
import { discoveryApi } from '@/api/client'

export function useDiscoveryAssistant() {
  return useMutation({ mutationFn: discoveryApi.assist })
}
