import { Link, useSearch } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { LoginButton } from '@/features/auth/login-button'
import { useAuthMethods } from '@/features/auth/use-auth-methods'

/**
 * Authentication entry page.
 *
 * It combines password login, OAuth entry points, and optional session-bootstrap support while
 * preserving the route the user originally intended to visit.
 */
export function LoginPage() {
  const { t, i18n } = useTranslation()
  const search = useSearch({ from: '/login' })
  const isChinese = i18n.resolvedLanguage?.split('-')[0] === 'zh'
  const { data: authMethods } = useAuthMethods(search.returnTo)

  // The old fallback sent a fresh OAuth login to the legacy account dashboard. Keep the
  // rebuilt JoyHub home as the canonical post-login destination when no route was requested.
  const returnTo = search.returnTo && search.returnTo.startsWith('/') ? search.returnTo : '/'
  const disabledMessage = search.reason === 'accountDisabled' ? t('apiError.auth.accountDisabled') : null
  const hasFeishuLogin = authMethods?.some((method) => method.methodType === 'OAUTH_REDIRECT' && method.provider === 'feishu')
  const hasLoginMethod = authMethods === undefined || hasFeishuLogin

  return (
    <div className="flex min-h-[70vh] items-center justify-center">
      <div className="w-full max-w-md space-y-8 animate-fade-up">
        <div className="text-center space-y-3">
          <div className="inline-flex w-16 h-16 rounded-2xl bg-gradient-to-br from-primary to-primary/70 items-center justify-center shadow-glow mb-4">
            <span className="text-primary-foreground font-bold text-2xl">J</span>
          </div>
          <h1 className="text-4xl font-bold font-heading text-foreground">{t('login.title')}</h1>
          <p className="text-muted-foreground text-lg">
            {t('login.subtitle')}
          </p>
        </div>

        <div className="glass-strong p-8 rounded-2xl">
          <div className="space-y-6">
            {disabledMessage ? (
              <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
                {disabledMessage}
              </div>
            ) : null}
            {hasFeishuLogin ? <p className="text-sm text-muted-foreground">{t('login.oauthHint')}</p> : null}
            <LoginButton returnTo={returnTo} />
            {!hasLoginMethod ? (
              <p className="text-sm text-red-600">
                {t('login.noMethodConfigured')}
              </p>
            ) : null}
          </div>
        </div>

        <p className="text-center text-xs text-muted-foreground">
          {t('login.agreementPrefix')}
          {isChinese ? null : ' '}
          <Link to="/terms" className="text-primary hover:underline">
            {t('login.terms')}
          </Link>
          {isChinese ? null : ' '}
          {t('login.and')}
          {isChinese ? null : ' '}
          <Link to="/privacy" className="text-primary hover:underline">
            {t('login.privacy')}
          </Link>
        </p>
      </div>
    </div>
  )
}
