import { describe, expect, it } from 'vitest'
import {
  SELECT_CONTENT_CLASS_NAME,
  SELECT_ITEM_CLASS_NAME,
  SELECT_SCROLL_BUTTON_CLASS_NAME,
  SELECT_TRIGGER_CLASS_NAME,
  SELECT_VIEWPORT_POPPER_CLASS_NAME,
  normalizeSelectValue,
} from './select'

describe('shared select contract', () => {
  it('uses a restrained product filter trigger style', () => {
    expect(SELECT_TRIGGER_CLASS_NAME).toContain('h-9')
    expect(SELECT_TRIGGER_CLASS_NAME).toContain('rounded-xl')
    expect(SELECT_TRIGGER_CLASS_NAME).toContain('border-slate-200')
    expect(SELECT_TRIGGER_CLASS_NAME).toContain('bg-white')
    expect(SELECT_TRIGGER_CLASS_NAME).toContain('focus-visible:outline-none')
    expect(SELECT_TRIGGER_CLASS_NAME).toContain('focus-visible:ring-2')
    expect(SELECT_TRIGGER_CLASS_NAME).toContain('focus-visible:ring-slate-300')
    expect(SELECT_TRIGGER_CLASS_NAME).toContain('data-[state=open]:border-slate-300')
  })

  it('uses themed panel and item classes for the floating listbox', () => {
    expect(SELECT_CONTENT_CLASS_NAME).toContain('bg-popover')
    expect(SELECT_CONTENT_CLASS_NAME).toContain('text-popover-foreground')
    expect(SELECT_CONTENT_CLASS_NAME).toContain('max-h-[min(24rem,var(--radix-select-content-available-height))]')
    expect(SELECT_CONTENT_CLASS_NAME).toContain('pointer-events-auto')
    expect(SELECT_ITEM_CLASS_NAME).toContain('focus:bg-slate-50')
    expect(SELECT_ITEM_CLASS_NAME).toContain('data-[disabled]:opacity-50')
  })

  it('sizes the popper viewport from trigger width without locking height', () => {
    expect(SELECT_VIEWPORT_POPPER_CLASS_NAME).toContain('min-w-[var(--radix-select-trigger-width)]')
    expect(SELECT_VIEWPORT_POPPER_CLASS_NAME).not.toContain('h-[var(--radix-select-trigger-height)]')
  })

  it('keeps the dropdown and selected items visually discoverable', () => {
    expect(SELECT_CONTENT_CLASS_NAME).toContain('shadow-lg')
    expect(SELECT_ITEM_CLASS_NAME).toContain('pl-10')
    expect(SELECT_ITEM_CLASS_NAME).toContain('data-[state=checked]:bg-slate-100')
  })

  it('uses pointer cursors for expanded select interactions', () => {
    expect(SELECT_ITEM_CLASS_NAME).toContain('cursor-pointer')
    expect(SELECT_SCROLL_BUTTON_CLASS_NAME).toContain('cursor-pointer')
  })

  it('maps empty and nullish form state to an undefined Radix value', () => {
    expect(normalizeSelectValue('')).toBeUndefined()
    expect(normalizeSelectValue(null)).toBeUndefined()
    expect(normalizeSelectValue(undefined)).toBeUndefined()
    expect(normalizeSelectValue('PUBLIC')).toBe('PUBLIC')
  })
})
