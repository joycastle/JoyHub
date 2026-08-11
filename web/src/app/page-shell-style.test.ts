import { describe, expect, it } from 'vitest'
import { APP_SHELL_PAGE_CLASS_NAME } from './page-shell-style'

describe('APP_SHELL_PAGE_CLASS_NAME', () => {
  it('keeps stable vertical spacing on app-shell pages', () => {
    expect(APP_SHELL_PAGE_CLASS_NAME).toBe('space-y-8')
  })
})
