import { type Plan, type TenantQuotaResponse, type TenantResponse, type UUID } from '@virtualrift/types';
import { useEffect, useState } from 'react';
import { useSession } from '../../session';
import { toErrorMessage } from '../../shared/errors';

type PlanCatalogItem = {
  plan: Plan;
  label: string;
  price: string;
  cadence: string;
  tagline: string;
  bestFor: string;
  features: readonly string[];
  cta: string;
  recommended?: boolean;
};

const PLAN_CATALOG: readonly PlanCatalogItem[] = [
  {
    plan: 'TRIAL',
    label: 'Trial',
    price: 'R$ 0',
    cadence: '/14 dias',
    tagline: 'Para experimentar.',
    bestFor: 'Quem está conhecendo a plataforma e quer rodar os primeiros scans.',
    features: [
      '10 scans por dia',
      '1 scan rodando por vez',
      'Até 3 alvos cadastrados',
      'Análise de código não inclusa',
    ],
    cta: 'Começar trial',
  },
  {
    plan: 'STARTER',
    label: 'Starter',
    price: 'R$ 399',
    cadence: '/mês',
    tagline: 'Para times pequenos.',
    bestFor: 'Cobrir um site, uma API e alguns repositórios sem complicação.',
    features: [
      '60 scans por dia',
      '3 scans em paralelo',
      'Até 15 alvos cadastrados',
      'Relatórios guardados por 30 dias',
    ],
    cta: 'Quero o Starter',
  },
  {
    plan: 'PROFESSIONAL',
    label: 'Professional',
    price: 'R$ 1.290',
    cadence: '/mês',
    tagline: 'Para times que já rodam scans no dia a dia.',
    bestFor: 'Várias superfícies, análise de código ativa e volume contínuo.',
    features: [
      '250 scans por dia',
      '10 scans em paralelo',
      'Até 60 alvos cadastrados',
      'Análise de código (SAST), Web, API e rede',
    ],
    cta: 'Quero o Professional',
    recommended: true,
  },
  {
    plan: 'ENTERPRISE',
    label: 'Enterprise',
    price: 'Sob consulta',
    cadence: '',
    tagline: 'Para empresas com governança avançada.',
    bestFor: 'Vários times, retenção maior, integrações e SLA dedicado.',
    features: [
      'Limites sob medida',
      'SLA e retenção dedicados',
      'Onboarding com nosso time',
      'Integrações e políticas sob contrato',
    ],
    cta: 'Falar com vendas',
  },
] as const;

const FAQ: ReadonlyArray<{ question: string; answer: string }> = [
  {
    question: 'Como faço para mudar de plano?',
    answer:
      'Nesta beta, a troca ainda é manual. Clique no plano desejado e nosso time entra em contato em até 1 dia útil para acertar a migração.',
  },
  {
    question: 'O que conta como um scan?',
    answer:
      'Cada execução iniciada conta como um scan, independente do tipo (Web, API, SAST ou rede). Reanálises automáticas em janela curta não são contabilizadas.',
  },
  {
    question: 'Posso cancelar quando quiser?',
    answer:
      'Sim. O cancelamento entra em vigor no fim do ciclo atual e os relatórios continuam disponíveis durante o período de retenção do plano.',
  },
];

const workspaceStatusLabel = (status: 'loading' | 'ready'): string => {
  switch (status) {
    case 'loading':
      return 'Carregando seu plano…';
    case 'ready':
      return 'Plano em dia';
  }
};

export function PlansPanel() {
  const { client, session } = useSession();
  const [tenant, setTenant] = useState<TenantResponse | null>(null);
  const [quota, setQuota] = useState<TenantQuotaResponse | null>(null);
  const [status, setStatus] = useState<'loading' | 'ready'>('loading');
  const [error, setError] = useState<string | null>(null);
  const [hint, setHint] = useState<string | null>(null);

  const tenantId: UUID | null = session?.tenantId ?? null;

  useEffect(() => {
    if (!tenantId) {
      return;
    }

    const loadWorkspace = async () => {
      setStatus('loading');
      setError(null);

      try {
        const [nextTenant, nextQuota] = await Promise.all([
          client.tenants.getById(tenantId),
          client.tenants.getQuota(tenantId),
        ]);

        setTenant(nextTenant);
        setQuota(nextQuota);
        setStatus('ready');
      } catch (loadError) {
        setStatus('ready');
        setError(toErrorMessage(loadError, 'Não conseguimos carregar seu plano agora.'));
      }
    };

    void loadWorkspace();
  }, [client, tenantId]);

  if (!tenantId) {
    return null;
  }

  const currentPlan = tenant?.plan ?? null;

  return (
    <section aria-label="plans-page" className="plans-page">
      <header className="plans-hero glass-card">
        <div className="plans-hero-copy">
          <span className="eyebrow">Seu plano</span>
          <h2>Planos e cobrança</h2>
          <p>
            {currentPlan ? (
              <>
                Você está no plano <strong>{currentPlan}</strong>. Veja o que está incluso, compare opções e
                descubra quando faz sentido subir.
              </>
            ) : (
              'Compare os planos disponíveis e veja qual cabe melhor no seu time.'
            )}
          </p>
        </div>

        <div className="plans-hero-stats">
          <div>
            <span>Scans por dia</span>
            <strong>{quota?.maxScansPerDay ?? '—'}</strong>
          </div>
          <div>
            <span>Em paralelo</span>
            <strong>{quota?.maxConcurrentScans ?? '—'}</strong>
          </div>
          <div>
            <span>Alvos</span>
            <strong>{quota?.maxScanTargets ?? '—'}</strong>
          </div>
          <div>
            <span>Histórico</span>
            <strong>{quota ? `${quota.reportRetentionDays}d` : '—'}</strong>
          </div>
        </div>

        <div className="plans-hero-meta">
          <span className="status-indicator">
            <span className={`status-dot ${status === 'loading' ? 'status-dot-pending' : 'status-dot-active'}`} />
            {workspaceStatusLabel(status)}
          </span>
          {tenant ? (
            <span className="plans-hero-workspace">
              {tenant.name} <em>· {tenant.slug}</em>
            </span>
          ) : null}
        </div>

        {error ? (
          <p className="alert alert-danger" role="alert">
            {error}
          </p>
        ) : null}
      </header>

      <section className="plans-catalog" aria-label="comparação de planos">
        {PLAN_CATALOG.map((plan) => {
          const isCurrentPlan = currentPlan === plan.plan;

          return (
            <article
              key={plan.plan}
              className={`plan-card${isCurrentPlan ? ' is-current' : ''}${plan.recommended ? ' is-recommended' : ''}`}
              aria-current={isCurrentPlan ? 'true' : undefined}
            >
              {isCurrentPlan ? <span className="plan-card-ribbon">Plano atual</span> : null}
              {!isCurrentPlan && plan.recommended ? (
                <span className="plan-card-ribbon plan-card-ribbon-soft">Mais escolhido</span>
              ) : null}

              <header className="plan-card-head">
                <span className="plan-card-tier">{plan.plan}</span>
                <h3>{plan.label}</h3>
                <p>{plan.tagline}</p>
              </header>

              <div className="plan-card-price">
                <strong>{plan.price}</strong>
                {plan.cadence ? <span>{plan.cadence}</span> : null}
              </div>

              <p className="plan-card-best-for">
                <span>Ideal para</span>
                {plan.bestFor}
              </p>

              <ul className="plan-card-features">
                {plan.features.map((feature) => (
                  <li key={feature}>
                    <svg viewBox="0 0 16 16" width="14" height="14" aria-hidden="true" focusable="false">
                      <path
                        fill="none"
                        stroke="currentColor"
                        strokeWidth="2"
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        d="M3 8.5l3 3L13 5"
                      />
                    </svg>
                    {feature}
                  </li>
                ))}
              </ul>

              <button
                className={isCurrentPlan ? 'button-secondary plan-card-cta' : 'button-primary plan-card-cta'}
                type="button"
                disabled={isCurrentPlan}
                onClick={() =>
                  setHint(
                    isCurrentPlan
                      ? null
                      : `A solicitação do plano ${plan.label} ainda é tratada manualmente. Nosso time entra em contato em breve.`,
                  )
                }
              >
                {isCurrentPlan ? 'Você está aqui' : plan.cta}
              </button>
            </article>
          );
        })}
      </section>

      {hint ? (
        <p className="alert alert-info plans-hint" role="status">
          {hint}
        </p>
      ) : null}

      <section className="plans-faq glass-card" aria-label="perguntas frequentes">
        <header>
          <span className="eyebrow">Dúvidas comuns</span>
          <h3>Antes de mudar de plano</h3>
        </header>
        <ul>
          {FAQ.map((item) => (
            <li key={item.question}>
              <strong>{item.question}</strong>
              <span>{item.answer}</span>
            </li>
          ))}
        </ul>

        <a className="button-ghost plans-back-link" href="#/account">
          Voltar para minha conta
        </a>
      </section>
    </section>
  );
}
