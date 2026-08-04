import { Link, useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import {
  ArrowRight,
  Bot,
  Boxes,
  Briefcase,
  Code2,
  Database,
  FileText,
  Palette,
  Search,
  ShieldCheck,
  Sparkles,
  UploadCloud,
  Wrench,
  type LucideIcon,
} from 'lucide-react'
import { SearchBar } from '@/features/search/search-bar'
import { normalizeSearchQuery } from '@/shared/lib/search-query'

interface HomeEntry {
  key: 'agents' | 'skills' | 'tools'
  to: '/agents' | '/skills' | '/tools'
  icon: LucideIcon
  accentClassName: string
}

const CENTER_ENTRIES: HomeEntry[] = [
  { key: 'agents', to: '/agents', icon: Bot, accentClassName: 'from-blue-500/15 to-cyan-400/5 text-blue-600' },
  { key: 'skills', to: '/skills', icon: Boxes, accentClassName: 'from-violet-500/15 to-fuchsia-400/5 text-violet-600' },
  { key: 'tools', to: '/tools', icon: Wrench, accentClassName: 'from-emerald-500/15 to-teal-400/5 text-emerald-600' },
]

const SCENARIOS: Array<{ key: string; query: string; icon: LucideIcon }> = [
  { key: 'content', query: '内容生产', icon: Sparkles },
  { key: 'data', query: '数据分析', icon: Database },
  { key: 'project', query: '项目管理', icon: Briefcase },
  { key: 'development', query: '研发提效', icon: Code2 },
  { key: 'art', query: '美术资产处理', icon: Palette },
]

const PLATFORM_FEATURES: Array<{ key: string; icon: LucideIcon }> = [
  { key: 'visibility', icon: ShieldCheck },
  { key: 'documentation', icon: FileText },
  { key: 'publishing', icon: UploadCloud },
]

/** JoyHub product home: a unified starting point for every internal AI capability. */
export function LandingPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()

  const handleSearch = (query: string) => {
    navigate({
      to: '/search',
      search: { q: normalizeSearchQuery(query), sort: 'relevance', page: 0, starredOnly: false },
    })
  }

  const searchScenario = (query: string) => {
    navigate({ to: '/search', search: { q: query, sort: 'relevance', page: 0, starredOnly: false } })
  }

  return (
    <div className="relative z-10">
      <section className="px-5 pb-16 pt-16 md:px-10 md:pb-24 md:pt-24">
        <div className="mx-auto max-w-6xl overflow-hidden rounded-[2rem] border border-primary/15 bg-gradient-to-br from-blue-100/90 via-white to-violet-100/70 px-6 py-14 shadow-[0_30px_100px_-55px_rgba(37,99,235,0.55)] md:px-14 md:py-20">
          <div className="mx-auto max-w-4xl text-center">
            <div className="mb-5 inline-flex items-center gap-2 rounded-full border border-primary/20 bg-white/80 px-4 py-2 text-sm font-semibold text-primary shadow-sm">
              <Sparkles className="h-4 w-4" />
              {t('joyhubHome.badge')}
            </div>
            <h1 className="text-4xl font-bold tracking-tight text-foreground md:text-6xl">
              {t('joyhubHome.title')}
            </h1>
            <p className="mx-auto mt-6 max-w-3xl text-lg leading-8 text-muted-foreground md:text-xl">
              {t('joyhubHome.description')}
            </p>
            <div className="mx-auto mt-10 max-w-3xl text-left">
              <SearchBar placeholder={t('joyhubHome.searchPlaceholder')} onSearch={handleSearch} />
            </div>
            <div className="mt-5 flex flex-wrap items-center justify-center gap-2 text-sm text-muted-foreground">
              <Search className="h-4 w-4" />
              <span>{t('joyhubHome.searchHint')}</span>
            </div>
          </div>
        </div>
      </section>

      <section className="px-5 py-12 md:px-10 md:py-16">
        <div className="mx-auto max-w-6xl">
          <div className="mb-8 max-w-3xl">
            <div className="text-sm font-semibold uppercase tracking-[0.18em] text-primary">{t('joyhubHome.centersEyebrow')}</div>
            <h2 className="mt-3 text-3xl font-bold tracking-tight md:text-4xl">{t('joyhubHome.centersTitle')}</h2>
            <p className="mt-3 text-base leading-7 text-muted-foreground">{t('joyhubHome.centersDescription')}</p>
          </div>
          <div className="grid gap-5 md:grid-cols-3">
            {CENTER_ENTRIES.map((entry) => {
              const Icon = entry.icon
              return (
                <Link
                  key={entry.key}
                  to={entry.to}
                  className="group rounded-3xl border bg-white p-7 shadow-sm transition-all hover:-translate-y-1 hover:border-primary/30 hover:shadow-lg"
                >
                  <div className={`inline-flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br ${entry.accentClassName}`}>
                    <Icon className="h-6 w-6" />
                  </div>
                  <h3 className="mt-6 text-2xl font-semibold">{t(`joyhubHome.centers.${entry.key}.title`)}</h3>
                  <p className="mt-3 min-h-[5rem] leading-7 text-muted-foreground">{t(`joyhubHome.centers.${entry.key}.description`)}</p>
                  <span className="mt-6 inline-flex items-center gap-2 text-sm font-semibold text-primary">
                    {t('joyhubHome.enterCenter')}
                    <ArrowRight className="h-4 w-4 transition-transform group-hover:translate-x-1" />
                  </span>
                </Link>
              )
            })}
          </div>
        </div>
      </section>

      <section className="bg-slate-50/80 px-5 py-16 md:px-10 md:py-20">
        <div className="mx-auto max-w-6xl">
          <div className="flex flex-col gap-8 lg:flex-row lg:items-end lg:justify-between">
            <div className="max-w-2xl">
              <div className="text-sm font-semibold uppercase tracking-[0.18em] text-primary">{t('joyhubHome.scenariosEyebrow')}</div>
              <h2 className="mt-3 text-3xl font-bold tracking-tight md:text-4xl">{t('joyhubHome.scenariosTitle')}</h2>
              <p className="mt-3 leading-7 text-muted-foreground">{t('joyhubHome.scenariosDescription')}</p>
            </div>
          </div>
          <div className="mt-9 grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
            {SCENARIOS.map((scenario) => {
              const Icon = scenario.icon
              return (
                <button
                  key={scenario.key}
                  type="button"
                  onClick={() => searchScenario(scenario.query)}
                  className="group flex min-h-36 flex-col justify-between rounded-2xl border bg-white p-5 text-left shadow-sm transition-all hover:-translate-y-0.5 hover:border-primary/30 hover:shadow-md"
                >
                  <Icon className="h-6 w-6 text-primary" />
                  <span className="mt-7 flex items-center justify-between gap-3 font-semibold">
                    {t(`joyhubHome.scenarios.${scenario.key}`)}
                    <ArrowRight className="h-4 w-4 text-muted-foreground transition-transform group-hover:translate-x-1" />
                  </span>
                </button>
              )
            })}
          </div>
        </div>
      </section>

      <section className="px-5 py-16 md:px-10 md:py-20">
        <div className="mx-auto max-w-6xl rounded-3xl border bg-white p-8 md:p-12">
          <div className="grid gap-10 lg:grid-cols-[0.85fr_1.5fr] lg:items-start">
            <div>
              <div className="text-sm font-semibold uppercase tracking-[0.18em] text-primary">{t('joyhubHome.platformEyebrow')}</div>
              <h2 className="mt-3 text-3xl font-bold tracking-tight">{t('joyhubHome.platformTitle')}</h2>
              <p className="mt-4 leading-7 text-muted-foreground">{t('joyhubHome.platformDescription')}</p>
            </div>
            <div className="grid gap-5 sm:grid-cols-3">
              {PLATFORM_FEATURES.map((feature) => {
                const Icon = feature.icon
                return (
                  <div key={feature.key} className="rounded-2xl bg-secondary/45 p-5">
                    <Icon className="h-6 w-6 text-primary" />
                    <h3 className="mt-4 font-semibold">{t(`joyhubHome.platform.${feature.key}.title`)}</h3>
                    <p className="mt-2 text-sm leading-6 text-muted-foreground">{t(`joyhubHome.platform.${feature.key}.description`)}</p>
                  </div>
                )
              })}
            </div>
          </div>
        </div>
      </section>
    </div>
  )
}
