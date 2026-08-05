import { useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { ArrowRight, Bot, Check, ChevronDown, Copy, ExternalLink, FileText, Loader2, MessageSquarePlus, Sparkles, X } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { useCatalogResources } from '@/features/catalog/use-catalog-queries'
import { buildInstallCommand, getBaseUrl } from '@/features/skill/install-command'
import { useSearchSkills } from '@/shared/hooks/use-skill-queries'
import { useCopyToClipboard } from '@/shared/lib/clipboard'
import { cn } from '@/shared/lib/utils'
import { Button, buttonVariants } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'
import { buildDiscoveryRecommendation, type DiscoverySuggestion } from './recommendation-engine'
import { useDiscoveryAssistant } from './use-discovery-assistant'

const QUICK_PROMPTS = ['discoveryAssistant.promptAgent', 'discoveryAssistant.promptData', 'discoveryAssistant.promptWriting'] as const

type ConversationTurn = {
  question: string
  answer: string
  model?: string
  steps: Array<{
    objective: string
    suggestions: DiscoverySuggestion[]
  }>
  suggestions: DiscoverySuggestion[]
}

function toDiscoverySuggestions(suggestions: Array<{
  type: 'catalog' | 'skill'
  id: number
  title: string
  description: string
  namespace?: string
  slug: string
  kind: string
  accessUrl?: string
  usage?: string
  evidence?: string
  source?: string
}>): DiscoverySuggestion[] {
  return suggestions.flatMap((suggestion): DiscoverySuggestion[] => {
    if (suggestion.type === 'skill' && suggestion.namespace) {
      return [{
        type: 'skill', id: suggestion.id, title: suggestion.title, description: suggestion.description,
        namespace: suggestion.namespace, slug: suggestion.slug, usage: suggestion.usage,
        evidence: suggestion.evidence, source: suggestion.source,
      }]
    }
    if (suggestion.type === 'catalog') {
      return [{
        type: 'catalog', id: suggestion.id, title: suggestion.title, description: suggestion.description,
        kind: suggestion.kind as Extract<DiscoverySuggestion, { type: 'catalog' }>['kind'], slug: suggestion.slug,
        accessUrl: suggestion.accessUrl, usage: suggestion.usage,
        evidence: suggestion.evidence, source: suggestion.source,
      }]
    }
    return []
  })
}

export function DiscoveryAssistant({ isAuthenticated }: { isAuthenticated: boolean }) {
  const { t, i18n } = useTranslation()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const [draft, setDraft] = useState('')
  const [question, setQuestion] = useState('')
  const [conversationId, setConversationId] = useState<string>()
  const [previousTurns, setPreviousTurns] = useState<ConversationTurn[]>([])
  const assistant = useDiscoveryAssistant()
  const enabled = open && question.trim().length > 0
  const skillSearch = useSearchSkills({ q: question, sort: 'relevance', size: 4 }, enabled)
  const catalogSearch = useCatalogResources({ q: question, size: 4, enabled: enabled && isAuthenticated })
  const isLoading = enabled && (skillSearch.isLoading || (isAuthenticated && catalogSearch.isLoading))
  const recommendation = useMemo(() => buildDiscoveryRecommendation({
    question,
    catalog: catalogSearch.data?.items ?? [],
    skills: skillSearch.data?.items ?? [],
    language: i18n.language,
  }), [catalogSearch.data?.items, i18n.language, question, skillSearch.data?.items])
  const aiSuggestions = useMemo(
    () => toDiscoverySuggestions(assistant.data?.suggestions ?? []),
    [assistant.data?.suggestions],
  )
  const aiSteps = useMemo(() => assistant.data?.steps.map((step) => ({
    objective: step.objective,
    suggestions: toDiscoverySuggestions(step.suggestions),
  })) ?? [], [assistant.data?.steps])
  const shownSuggestions = assistant.data ? aiSuggestions : recommendation.suggestions
  const shownSummary = assistant.data?.answer ?? recommendation.summary
  const isThinking = isLoading || assistant.isPending

  const ask = (value: string) => {
    const next = value.trim()
    if (!next || assistant.isPending) return
    if (question && assistant.data) {
      setPreviousTurns((turns) => [...turns, {
        question,
        answer: assistant.data.answer,
        model: assistant.data.modelGenerated ? assistant.data.model : undefined,
        steps: assistant.data.steps.map((step) => ({
          objective: step.objective,
          suggestions: toDiscoverySuggestions(step.suggestions),
        })),
        suggestions: toDiscoverySuggestions(assistant.data.suggestions),
      }].slice(-5))
    }
    setDraft('')
    setQuestion(next)
    assistant.reset()
    if (isAuthenticated) {
      assistant.mutate({ question: next, language: i18n.language, conversationId }, {
        onSuccess: (response) => setConversationId(response.conversationId),
      })
    }
  }

  const startNewConversation = () => {
    assistant.reset()
    setConversationId(undefined)
    setPreviousTurns([])
    setQuestion('')
    setDraft('')
  }

  const submit = (event: FormEvent) => {
    event.preventDefault()
    ask(draft)
  }

  const openSuggestion = (suggestion: DiscoverySuggestion) => {
    if (suggestion.type === 'catalog') {
      navigate({ to: '/catalog/$slug', params: { slug: suggestion.slug } })
      return
    }
    navigate({ to: `/space/${suggestion.namespace}/${encodeURIComponent(suggestion.slug)}` })
  }

  return (
    <>
      <Button
        type="button"
        size="lg"
        className="fixed bottom-6 right-6 z-40 gap-2 rounded-full px-5 shadow-xl"
        onClick={() => setOpen(true)}
        aria-label={t('discoveryAssistant.open')}
      >
        <Sparkles className="h-5 w-5" />
        <span className="hidden sm:inline">{t('discoveryAssistant.button')}</span>
      </Button>

      {open ? (
        <div className="fixed inset-0 z-50 flex justify-end bg-slate-950/25" role="presentation" onMouseDown={() => setOpen(false)}>
          <aside
            className="flex h-full w-full max-w-2xl flex-col border-l bg-slate-50 shadow-2xl dark:bg-background"
            role="dialog"
            aria-modal="true"
            aria-label={t('discoveryAssistant.title')}
            onMouseDown={(event) => event.stopPropagation()}
          >
            <header className="flex items-start justify-between border-b bg-background px-6 py-5 sm:px-8">
              <div className="flex gap-3">
                <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-primary/10 text-primary">
                  <Bot className="h-6 w-6" />
                </div>
                <div>
                  <div className="flex flex-wrap items-center gap-2">
                    <h2 className="font-semibold">{t('discoveryAssistant.title')}</h2>
                    <span className="rounded-full bg-emerald-50 px-2 py-0.5 text-[10px] font-medium text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300">
                      {t('discoveryAssistant.permissionSafe')}
                    </span>
                  </div>
                  <p className="mt-1 text-sm text-muted-foreground">{t('discoveryAssistant.subtitle')}</p>
                </div>
              </div>
              <div className="flex items-center gap-1">
                {question ? (
                  <Button type="button" variant="ghost" size="sm" className="gap-1.5" onClick={startNewConversation}>
                    <MessageSquarePlus className="h-4 w-4" />
                    {t('discoveryAssistant.newConversation')}
                  </Button>
                ) : null}
                <Button type="button" variant="ghost" size="icon" onClick={() => setOpen(false)} aria-label={t('discoveryAssistant.close')}>
                  <X className="h-5 w-5" />
                </Button>
              </div>
            </header>

            <div className="flex-1 space-y-6 overflow-y-auto px-5 py-6 sm:px-8">
              {!question ? (
                <div className="rounded-2xl border bg-secondary/30 p-5">
                  <p className="text-sm leading-6">{t('discoveryAssistant.welcome')}</p>
                  <div className="mt-4 flex flex-wrap gap-2">
                    {QUICK_PROMPTS.map((key) => (
                      <Button key={key} type="button" variant="outline" size="sm" onClick={() => ask(t(key))}>
                        {t(key)}
                      </Button>
                    ))}
                  </div>
                </div>
              ) : (
                <>
                  {previousTurns.map((turn, index) => (
                    <div key={`${index}-${turn.question}`} className="space-y-3 opacity-80">
                      <div className="ml-12 rounded-2xl rounded-br-md bg-primary px-4 py-3 text-sm text-primary-foreground">
                        {turn.question}
                      </div>
                      <div className="mr-8 rounded-2xl rounded-bl-md border bg-card px-4 py-3">
                        <p className="text-sm leading-6 text-muted-foreground">{turn.answer}</p>
                        {turn.steps.length > 0 ? (
                          <details className="mt-4 rounded-xl border bg-muted/15" open>
                            <summary className="cursor-pointer list-none px-3.5 py-2.5 text-xs font-semibold marker:content-none">
                              {t('discoveryAssistant.planTitle')}
                            </summary>
                            <div className="space-y-4 border-t px-3.5 py-3">
                              {turn.steps.map((step, stepIndex) => (
                                <div key={`${stepIndex}-${step.objective}`}>
                                  <p className="text-xs font-medium leading-5">
                                    {stepIndex + 1}. {step.objective}
                                  </p>
                                  {step.suggestions.length > 0 ? (
                                    <StepRecommendations suggestions={step.suggestions} onOpen={openSuggestion} />
                                  ) : (
                                    <p className="mt-2 text-xs leading-5 text-muted-foreground">
                                      {t('discoveryAssistant.noStepMatch')}
                                    </p>
                                  )}
                                </div>
                              ))}
                            </div>
                          </details>
                        ) : turn.suggestions.length > 0 ? (
                          <details className="mt-4 rounded-xl border bg-muted/15" open>
                            <summary className="cursor-pointer list-none px-3.5 py-2.5 text-xs font-semibold marker:content-none">
                              {t('discoveryAssistant.recommendationsTitle')}
                            </summary>
                            <div className="border-t px-3.5 py-3">
                              <StepRecommendations suggestions={turn.suggestions} onOpen={openSuggestion} />
                            </div>
                          </details>
                        ) : null}
                        {turn.model ? (
                          <p className="mt-2 text-right text-[10px] text-muted-foreground/60">
                            {t('discoveryAssistant.model', { model: turn.model })}
                          </p>
                        ) : null}
                      </div>
                    </div>
                  ))}
                  <div className="ml-12 rounded-2xl rounded-br-md bg-primary px-4 py-3 text-sm text-primary-foreground">
                    {question}
                  </div>
                  <div className="rounded-3xl rounded-bl-md border bg-card p-5 shadow-sm sm:p-6">
                    {isThinking ? (
                      <div className="flex items-center gap-2 text-sm text-muted-foreground">
                        <Loader2 className="h-4 w-4 animate-spin" />
                        {t('discoveryAssistant.thinking')}
                      </div>
                    ) : (
                      <div className="space-y-6">
                        <p className="whitespace-pre-line text-sm leading-6 text-muted-foreground">{shownSummary}</p>
                        {aiSteps.length > 0 ? (
                          <div>
                            <div className="flex items-end justify-between gap-4">
                              <div>
                                <h3 className="text-base font-semibold">{t('discoveryAssistant.planTitle')}</h3>
                                <p className="mt-1 text-xs text-muted-foreground">{t('discoveryAssistant.planHint')}</p>
                              </div>
                              <span className="shrink-0 text-xs text-muted-foreground">
                                {t('discoveryAssistant.stepCount', { count: aiSteps.length })}
                              </span>
                            </div>
                            <div className="relative mt-5">
                              <span className="absolute bottom-5 left-[15px] top-4 w-px bg-primary/15" aria-hidden="true" />
                              {aiSteps.map((step, index) => (
                                <section key={`${index}-${step.objective}`} className="relative flex gap-4 pb-7 last:pb-0">
                                  <span className="relative z-10 flex h-8 w-8 shrink-0 items-center justify-center rounded-full border-4 border-card bg-primary text-xs font-semibold text-primary-foreground shadow-sm">
                                    {index + 1}
                                  </span>
                                  <div className="min-w-0 flex-1">
                                    <h4 className="pt-1 text-sm font-semibold leading-5">{step.objective}</h4>
                                    {step.suggestions.length === 0 ? (
                                      <div className="mt-3 rounded-xl border border-dashed bg-muted/25 px-4 py-3 text-xs leading-5 text-muted-foreground">
                                        <span className="font-medium text-foreground">{t('discoveryAssistant.noStepMatch')}</span>
                                        <span className="ml-1">{t('discoveryAssistant.noStepMatchHint')}</span>
                                      </div>
                                    ) : (
                                      <StepRecommendations suggestions={step.suggestions} onOpen={openSuggestion} />
                                    )}
                                  </div>
                                </section>
                              ))}
                            </div>
                          </div>
                        ) : shownSuggestions.length > 0 ? (
                          <StepRecommendations suggestions={shownSuggestions} onOpen={openSuggestion} />
                        ) : null}
                        <section className="border-t pt-5">
                          <div>
                            <h3 className="text-sm font-semibold">{t('discoveryAssistant.refineTitle')}</h3>
                            <p className="mt-1 text-xs text-muted-foreground">{t('discoveryAssistant.refineHint')}</p>
                          </div>
                          <div className="mt-3 flex flex-wrap gap-2">
                            {recommendation.followUps.slice(0, 3).map((prompt) => (
                              <button
                                key={prompt}
                                type="button"
                                className="rounded-full border bg-background px-3 py-1.5 text-left text-xs text-foreground transition hover:border-primary/40 hover:text-primary"
                                onClick={() => ask(prompt)}
                              >
                                {prompt}
                              </button>
                            ))}
                          </div>
                        </section>
                        {assistant.data?.modelGenerated && assistant.data.model ? (
                          <p className="text-right text-[10px] text-muted-foreground/60">
                            {t('discoveryAssistant.model', { model: assistant.data.model })}
                          </p>
                        ) : null}
                      </div>
                    )}
                  </div>
                </>
              )}
            </div>

            <form className="border-t bg-background p-4 sm:px-8" onSubmit={submit}>
              <div className="flex gap-2">
                <Input
                  value={draft}
                  onChange={(event) => setDraft(event.target.value)}
                  placeholder={t('discoveryAssistant.placeholder')}
                  maxLength={200}
                  disabled={assistant.isPending}
                />
                <Button type="submit" size="icon" disabled={!draft.trim() || assistant.isPending} aria-label={t('discoveryAssistant.ask')}>
                  <ArrowRight className="h-4 w-4" />
                </Button>
              </div>
              {!isAuthenticated ? <p className="mt-2 text-xs text-muted-foreground">{t('discoveryAssistant.loginHint')}</p> : null}
            </form>
          </aside>
        </div>
      ) : null}
    </>
  )
}

function StepRecommendations({ suggestions, onOpen }: {
  suggestions: DiscoverySuggestion[]
  onOpen: (suggestion: DiscoverySuggestion) => void
}) {
  const { t } = useTranslation()
  const [primary, ...alternatives] = suggestions

  return (
    <div className="mt-3 space-y-2.5">
      <SuggestionCard suggestion={primary} onOpen={onOpen} primary />
      {alternatives.length > 0 ? (
        <details className="group/alternatives rounded-xl border bg-muted/15">
          <summary className="flex cursor-pointer list-none items-center justify-between gap-3 px-3.5 py-2.5 text-xs font-medium text-muted-foreground marker:content-none hover:text-foreground">
            <span>{t('discoveryAssistant.alternatives', { count: alternatives.length })}</span>
            <ChevronDown className="h-4 w-4 transition group-open/alternatives:rotate-180" />
          </summary>
          <div className="space-y-2 border-t p-2.5">
            {alternatives.map((suggestion) => (
              <SuggestionCard key={`${suggestion.type}-${suggestion.id}`} suggestion={suggestion} onOpen={onOpen} />
            ))}
          </div>
        </details>
      ) : null}
    </div>
  )
}

function SuggestionCard({ suggestion, onOpen, primary = false }: {
  suggestion: DiscoverySuggestion
  onOpen: (suggestion: DiscoverySuggestion) => void
  primary?: boolean
}) {
  const { t } = useTranslation()
  const [copied, copy] = useCopyToClipboard()
  const installCommand = suggestion.type === 'skill'
    ? buildInstallCommand(suggestion.namespace, suggestion.slug, getBaseUrl())
    : null
  const accessUrl = suggestion.type === 'catalog' ? suggestion.accessUrl : undefined

  const copyInstallCommand = async () => {
    if (!installCommand) return
    try {
      await copy(installCommand)
    } catch (error) {
      console.error('Failed to copy install command:', error)
    }
  }

  return (
    <article className={cn('rounded-2xl border bg-background p-4', primary && 'border-primary/25 shadow-sm')}>
      <div className="flex min-w-0 items-start justify-between gap-3">
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            {primary ? (
              <span className="inline-flex items-center gap-1 rounded-full bg-primary px-2 py-0.5 text-[9px] font-semibold text-primary-foreground">
                <Check className="h-2.5 w-2.5" /> {t('discoveryAssistant.primary')}
              </span>
            ) : null}
          <span className="rounded-md bg-primary/10 px-1.5 py-0.5 text-[9px] font-semibold uppercase tracking-wide text-primary">
            {suggestion.type === 'skill' ? 'Skill' : suggestion.kind}
          </span>
            <h5 className="min-w-0 truncate text-sm font-semibold">{suggestion.title}</h5>
          </div>
          <p className="mt-2 text-[10px] font-medium text-muted-foreground">{t('discoveryAssistant.whatItDoes')}</p>
          <p className="mt-1 text-xs leading-5 text-foreground/80">{suggestion.description}</p>
        </div>
        <Button type="button" variant={primary ? 'default' : 'outline'} size="sm" className="shrink-0 gap-1.5" onClick={() => onOpen(suggestion)}>
          {t('discoveryAssistant.viewDetails')}
          <ExternalLink className="h-3 w-3" />
        </Button>
      </div>
      <div className="mt-3 rounded-xl bg-slate-50 px-3 py-3 dark:bg-muted/40">
        <p className="text-xs font-medium text-primary">{t('discoveryAssistant.howToUse')}</p>
        {installCommand ? (
          <div className="mt-2">
            <p className="text-xs leading-5 text-foreground/75">{t('discoveryAssistant.skillInstallHint')}</p>
            <div className="mt-2 flex items-center gap-2 rounded-lg border bg-background px-3 py-2">
              <code className="min-w-0 flex-1 break-all font-mono text-[11px] leading-5">{installCommand}</code>
              <button
                type="button"
                className="shrink-0 rounded-md p-1.5 text-muted-foreground transition hover:bg-muted hover:text-foreground"
                onClick={copyInstallCommand}
                aria-label={copied ? t('copyButton.copied') : t('copyButton.copy')}
                title={copied ? t('copyButton.copied') : t('copyButton.copy')}
              >
                {copied ? <Check className="h-3.5 w-3.5 text-emerald-600" /> : <Copy className="h-3.5 w-3.5" />}
              </button>
            </div>
          </div>
        ) : (
          <div className="mt-2">
            <p className="text-xs leading-5 text-foreground/75">
              {suggestion.usage || (accessUrl ? t('discoveryAssistant.accessHint') : t('discoveryAssistant.openForInstructions'))}
            </p>
            {accessUrl ? (
              <a
                href={accessUrl}
                target="_blank"
                rel="noreferrer"
                className={cn(buttonVariants({ variant: 'outline', size: 'sm' }), 'mt-2 gap-1.5 bg-background')}
              >
                {t('discoveryAssistant.accessNow')}
                <ExternalLink className="h-3 w-3" />
              </a>
            ) : null}
          </div>
        )}
      </div>
      <p className="mt-2 flex items-center gap-1.5 text-[10px] text-muted-foreground/75">
        <FileText className="h-3 w-3" />
        {t('discoveryAssistant.basedOn', { source: suggestion.source ?? t('discoveryAssistant.document') })}
      </p>
    </article>
  )
}
