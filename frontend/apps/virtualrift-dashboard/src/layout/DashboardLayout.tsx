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
  { id: 'overview', label: 'Início', description: 'Ações importantes para proteger seu negócio.' },
  { id: 'targets', label: 'O que proteger', description: 'Seus sites, sistemas, APIs e códigos.' },
  { id: 'scans', label: 'Verificar', description: 'Procure riscos antes que virem problemas.' },
  { id: 'reports', label: 'Resultados', description: 'Veja o que precisa de atenção.' },
  { id: 'account', label: 'Equipe e acesso', description: 'Pessoas e permissões da sua conta.' },
  { id: 'plans', label: 'Meu plano', description: 'Uso, limites e opções de crescimento.' },
];

type RouteCopy = {
  title: string;
  description: string;
};

const ROUTE_COPY: Record<Route, RouteCopy> = {
  overview: {
    title: 'Por onde você quer começar?',
    description: 'Escolha uma opção e nós guiamos você.',
  },
  targets: {
    title: 'O que você quer proteger?',
    description: 'Adicione seu site, sistema ou projeto.',
  },
  scans: {
    title: 'Verificar segurança',
    description: 'Encontre riscos antes de publicar.',
  },
  reports: {
    title: 'O que precisa de atenção',
    description: 'Veja primeiro o que é mais importante.',
  },
  account: {
    title: 'Equipe e acesso',
    description: 'Seus dados e sua equipe.',
  },
  plans: {
    title: 'Seu plano',
    description: 'Veja o que está incluído e compare opções.',
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

export function DashboardLayout() {
  const [route] = useRoute();
  const copy = ROUTE_COPY[route];
  const RouteContent = ROUTE_COMPONENT[route];

  return (
    <div className="dashboard-layout">
      <Sidebar sections={SECTIONS} activeId={route} />
      <div className="dashboard-layout-main">
        <Topbar copy={copy} />
        <main className="dashboard-content" key={route}>
          <div className="dashboard-route-body">{RouteContent()}</div>
        </main>
      </div>
    </div>
  );
}
