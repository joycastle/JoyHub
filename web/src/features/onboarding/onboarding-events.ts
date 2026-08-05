export const RESUME_PLATFORM_ONBOARDING_EVENT = 'joyhub:resume-platform-onboarding'

export function resumePlatformOnboarding() {
  window.dispatchEvent(new Event(RESUME_PLATFORM_ONBOARDING_EVENT))
}
