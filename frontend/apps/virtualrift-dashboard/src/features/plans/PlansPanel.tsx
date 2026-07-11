import { type BillingSummaryResponse, type Plan, type PlanChangeRequestResponse, type UUID } from '@virtualrift/types';
import { useEffect, useState } from 'react';
import { useSession } from '../../session';
import { toErrorMessage } from '../../shared/errors';
import { canRequestPlanChanges } from '../../shared/roles';

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
    label: 'Teste grátis',
    price: 'R$ 0',
    cadence: '/14 dias',
    tagline: 'Para conhecer sem compromisso.',
    bestFor: 'Quem está lançando o primeiro projeto e quer entender os riscos.',
    features: [
      '3 verificações por dia',
      '1 verificação por vez',
      '1 site ou sistema protegido',
      'Resultados guardados por 7 dias',
    ],
    cta: 'Começar grátis',
  },
  {
    plan: 'STARTER',
    label: 'Começando',
    price: 'R$ 399',
    cadence: '/mês',
    tagline: 'Para quem está colocando a ideia no ar.',
    bestFor: 'Proteger os primeiros sites, sistemas e projetos sem complicação.',
    features: [
      '20 verificações por dia',
      '3 verificações ao mesmo tempo',
      'Até 5 sites ou sistemas',
      'Resultados guardados por 30 dias',
    ],
    cta: 'Escolher Começando',
  },
  {
    plan: 'PROFESSIONAL',
    label: 'Negócio',
    price: 'R$ 1.290',
    cadence: '/mês',
    tagline: 'Para quem já tem clientes ou está em produção.',
    bestFor: 'Proteger vários projetos e verificar cada mudança com frequência.',
    features: [
      '100 verificações por dia',
      '10 verificações ao mesmo tempo',
      'Até 25 sites ou sistemas',
      'Site, integrações, código e servidores',
    ],
    cta: 'Escolher Negócio',
    recommended: true,
  },
  {
    plan: 'ENTERPRISE',
    label: 'Empresa',
    price: 'Sob consulta',
    cadence: '',
    tagline: 'Para operações maiores e várias equipes.',
    bestFor: 'Mais volume, acompanhamento próximo e necessidades personalizadas.',
    features: [
      'Verificações e itens sem limite',
      '25 verificações ao mesmo tempo',
      'Histórico e suporte personalizados',
      'Configuração acompanhada pelo nosso time',
      'Integrações sob medida',
    ],
    cta: 'Falar com vendas',
  },
] as const;

export function PlansPanel() {
  const { client, session } = useSession();
  const [billingSummary, setBillingSummary] = useState<BillingSummaryResponse | null>(null);
  const [status, setStatus] = useState<'loading' | 'ready'>('loading');
  const [error, setError] = useState<string | null>(null);
  const [hint, setHint] = useState<string | null>(null);
  const [requestingPlan, setRequestingPlan] = useState<Plan | null>(null);

  const tenantId: UUID | null = session?.tenantId ?? null;
  const roles = session?.roles ?? [];
  const canManagePlans = canRequestPlanChanges(roles);

  useEffect(() => {
    if (!tenantId) {
      return;
    }

    const loadWorkspace = async () => {
      setStatus('loading');
      setError(null);

      try {
        const nextSummary = await client.tenants.getBillingSummary(tenantId);
        setBillingSummary(nextSummary);
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

  const currentPlan = billingSummary?.currentPlan ?? null;
  const pendingRequest = billingSummary?.pendingPlanChangeRequest ?? null;

  const handlePlanRequest = async (plan: Plan, label: string) => {
    if (!tenantId || !canManagePlans) {
      return;
    }

    setHint(null);
    setError(null);
    setRequestingPlan(plan);

    try {
      const response: PlanChangeRequestResponse = await client.tenants.requestPlanChange(tenantId, {
        requestedPlan: plan,
        note: `Solicitação enviada pelo dashboard beta para o plano ${label}.`,
      });

      setBillingSummary((currentSummary) =>
        currentSummary
          ? {
              ...currentSummary,
              pendingPlanChangeRequest: response,
            }
          : currentSummary,
      );
      setHint(`Solicitação do plano ${label} registrada. Nosso time vai revisar essa mudança em breve.`);
    } catch (requestError) {
      setError(toErrorMessage(requestError, 'Não conseguimos registrar sua solicitação agora.'));
    } finally {
      setRequestingPlan(null);
    }
  };

  return (
    <section aria-label="plans-page" className="plans-page">
      {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}

      {pendingRequest ? (
        <p className="alert alert-info plans-hint" role="status">
          Sua mudança de plano está em análise.
        </p>
      ) : null}

      <section className="plans-catalog" aria-label="comparação de planos">
        {PLAN_CATALOG.map((plan) => {
          const isCurrentPlan = currentPlan === plan.plan;
          const isPendingPlan = pendingRequest?.requestedPlan === plan.plan;

          return (
            <article
              key={plan.plan}
              className={`plan-card${isCurrentPlan ? ' is-current' : ''}${plan.recommended ? ' is-recommended' : ''}`}
              aria-current={isCurrentPlan ? 'true' : undefined}
            >
              {isCurrentPlan ? <span className="plan-card-ribbon">Plano atual</span> : null}
              {isPendingPlan ? <span className="plan-card-ribbon plan-card-ribbon-soft">Solicitado</span> : null}
              {!isCurrentPlan && plan.recommended ? (
                <span className="plan-card-ribbon plan-card-ribbon-soft">Mais escolhido</span>
              ) : null}

              <header className="plan-card-head">
                <h3>{plan.label}</h3>
                <p>{plan.tagline}</p>
              </header>

              <div className="plan-card-price">
                <strong>{plan.price}</strong>
                {plan.cadence ? <span>{plan.cadence}</span> : null}
              </div>

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
                disabled={isCurrentPlan || isPendingPlan || !!pendingRequest || !canManagePlans || requestingPlan === plan.plan}
                onClick={() => void handlePlanRequest(plan.plan, plan.label)}
              >
                {isCurrentPlan
                  ? 'Você está aqui'
                  : isPendingPlan
                    ? 'Solicitação pendente'
                    : canManagePlans
                      ? requestingPlan === plan.plan
                        ? 'Enviando…'
                        : plan.cta
                      : 'Peça ao administrador da conta'}
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

      {!canManagePlans ? (
        <p className="alert alert-info plans-hint" role="status">
          Você pode consultar o plano atual. Para trocar, peça ajuda à pessoa que administra esta conta.
        </p>
      ) : null}

      <a className="button-ghost plans-back-link" href="#/account">
        Voltar para minha conta
      </a>
    </section>
  );
}
