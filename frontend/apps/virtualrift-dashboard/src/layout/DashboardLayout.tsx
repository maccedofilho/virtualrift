import type { ReactNode } from 'react';
import { AccountPanel } from '../features/account/AccountPanel';
import { SessionOverview } from '../features/overview/SessionOverview';
import { PlansPanel } from '../features/plans/PlansPanel';
import { ReportsPanel } from '../features/reports/ReportsPanel';
import { TenantTargetsPanel } from '../features/targets/TenantTargetsPanel';
import { ScanCreationPanel } from '../features/scans/ScanCreationPanel';
import { Sidebar, type NavSection } from './Sidebar';
import { Topbar } from './Topbar';
import { type Route, useRoute } from './useRoute';

const SECTIONS: ReadonlyArray<NavSection> = [
  { id: 'overview', label: 'Visão geral', description: 'Estado da sessão e contexto operacional do tenant.' },
  { id: 'targets', label: 'Alvos', description: 'Cadastre, valide ownership e gerencie alvos do tenant.' },
  { id: 'scans', label: 'Scans', description: 'Dispare e acompanhe scans dentro do escopo verificado.' },
  { id: 'reports', label: 'Relatórios', description: 'Abra snapshots gerados, findings e risco consolidado.' },
  { id: 'account', label: 'Minha conta', description: 'Suas informações e como você acessa.' },
  { id: 'plans', label: 'Planos', description: 'O que tem no seu plano e quando subir.' },
];

type RouteCopy = {
  kicker: string;
  title: string;
  description: string;
  intro: ReadonlyArray<{ label: string; detail: string }>;
};

const ROUTE_COPY: Record<Route, RouteCopy> = {
  overview: {
    kicker: '01 · Sessão',
    title: 'Visão geral da sessão',
    description: 'Confirme que a sessão está autenticada e dentro do contexto correto antes de tocar em alvos ou disparar scans.',
    intro: [
      {
        label: 'O que esta tela faz',
        detail: 'Mostra o estado da sessão atual, contexto do tenant, perfis vinculados e validade do token de acesso.',
      },
      {
        label: 'Quando voltar aqui',
        detail: 'Sempre que o gateway responder com 401, quando trocar de tenant, ou para encerrar a sessão de forma limpa.',
      },
      {
        label: 'Próximo passo sugerido',
        detail: 'Confirme os perfis listados e siga para Alvos para garantir que a superfície está dentro do escopo contratado.',
      },
    ],
  },
  targets: {
    kicker: '02 · Superfície',
    title: 'Alvos autorizados do tenant',
    description: 'Cadastre os ativos que você controla, valide ownership e mantenha a superfície dentro do limite do plano antes de disparar qualquer scan.',
    intro: [
      {
        label: 'Cadastrar um alvo',
        detail: 'Use URLs para fluxos web/app, especificações OpenAPI para APIs e repositórios públicos para SAST. Faixas internas (RFC1918) são bloqueadas no backend.',
      },
      {
        label: 'Validar ownership',
        detail: 'Antes de qualquer scan, o alvo precisa ter ownership confirmado. Use o token de verificação retornado pelo painel.',
      },
      {
        label: 'Antes de seguir para Scans',
        detail: 'Garanta que pelo menos um alvo está com status VERIFIED. Sem ownership confirmada, a tela de scans bloqueia a execução.',
      },
    ],
  },
  scans: {
    kicker: '03 · Execução',
    title: 'Scans e resultados',
    description: 'Selecione um alvo verificado, ajuste profundidade e timeout, acompanhe o histórico do tenant e abra o resultado agregado em uma única área.',
    intro: [
      {
        label: 'Quem aparece aqui',
        detail: 'Apenas alvos com ownership confirmada e tipo de superfície compatível com o scan solicitado. URL aceita WEB e API; repositórios aceitam SAST.',
      },
      {
        label: 'Profundidade e timeout',
        detail: 'A profundidade controla quão fundo o crawler vai. O timeout limita o tempo total da execução. Ambos respeitam os limites do plano.',
      },
      {
        label: 'Histórico e resultado',
        detail: 'A lista agora mostra o histórico real do tenant. Selecione um scan para abrir severidades, risco, findings e gerar relatório quando ele estiver concluído.',
      },
    ],
  },
  reports: {
    kicker: '04 · Relatórios',
    title: 'Relatórios do tenant',
    description: 'Abra snapshots gerados a partir de scans concluídos, compare severidade, risco e findings persistidos, e mantenha a trilha executiva pronta para compartilhar.',
    intro: [
      {
        label: 'O que aparece aqui',
        detail: 'Cada relatório é um snapshot persistido do scan, com findings e score de risco congelados no momento da geração.',
      },
      {
        label: 'Quando usar',
        detail: 'Depois de concluir um scan importante ou quando você precisar compartilhar um artefato mais estável do que o resultado operacional.',
      },
      {
        label: 'Como navegar',
        detail: 'Selecione um relatório para abrir contexto, findings e vínculo com o scan original. Se ainda não houver relatórios, gere um a partir da área de scans.',
      },
    ],
  },
  account: {
    kicker: 'Conta',
    title: 'Sua conta',
    description: 'Suas informações de acesso, o workspace que você está usando e o seu plano atual.',
    intro: [],
  },
  plans: {
    kicker: 'Planos',
    title: 'Seu plano e como crescer',
    description: 'Veja o que está incluso no seu plano e descubra quando faz sentido subir.',
    intro: [],
  },
};

const ROUTE_COMPONENT: Record<Route, () => ReactNode> = {
  overview: () => <SessionOverview />,
  targets: () => <TenantTargetsPanel />,
  scans: () => <ScanCreationPanel />,
  reports: () => <ReportsPanel />,
  account: () => <AccountPanel />,
  plans: () => <PlansPanel />,
};

function RouteIntro({ copy }: { copy: RouteCopy }) {
  if (copy.intro.length === 0) {
    return null;
  }

  return (
    <section className="glass-card dashboard-route-intro" aria-label="introdução da seção">
      <div className="dashboard-route-intro-copy">
        <span className="dashboard-route-intro-kicker">Como usar esta tela</span>
        <p>{copy.description}</p>
      </div>
      <ul className="dashboard-route-intro-list">
        {copy.intro.map((item) => (
          <li key={item.label}>
            <strong>{item.label}</strong>
            <span>{item.detail}</span>
          </li>
        ))}
      </ul>
    </section>
  );
}

export function DashboardLayout() {
  const [route] = useRoute();
  const activeSection = SECTIONS.find((section) => section.id === route) ?? SECTIONS[0];
  const copy = ROUTE_COPY[route];
  const RouteContent = ROUTE_COMPONENT[route];

  return (
    <div className="dashboard-layout">
      <Sidebar sections={SECTIONS} activeId={route} />
      <div className="dashboard-layout-main">
        <Topbar active={activeSection} copy={copy} />
        <main className="dashboard-content" key={route}>
          <RouteIntro copy={copy} />
          <div className="dashboard-route-body">{RouteContent()}</div>
        </main>
      </div>
    </div>
  );
}
