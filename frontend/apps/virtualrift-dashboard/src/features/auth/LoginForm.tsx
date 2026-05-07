import { type FormEvent, useState } from 'react';
import { useSession } from '../../session';

const HERO_FLOW_STEPS: ReadonlyArray<{ idx: string; label: string; detail: string }> = [
  { idx: '01', label: 'Sessão autenticada', detail: 'Token JWT validado e contexto do tenant aplicado.' },
  { idx: '02', label: 'Alvos verificados', detail: 'Ownership confirmada antes do primeiro scan.' },
  { idx: '03', label: 'Scans orquestrados', detail: 'Web, API, SAST e rede no mesmo workspace.' },
  { idx: '04', label: 'Findings rastreáveis', detail: 'Relatórios alinhados de ponta a ponta.' },
];

type AuthMode = 'login' | 'register';

const GitHubIcon = () => (
  <svg className="dashboard-social-icon" viewBox="0 0 24 24" width="20" height="20" aria-hidden="true" focusable="false">
    <path
      fill="currentColor"
      d="M12 .5C5.73.5.75 5.48.75 11.75c0 4.97 3.22 9.18 7.69 10.67.56.1.77-.24.77-.54 0-.27-.01-1.16-.02-2.1-3.13.68-3.79-1.34-3.79-1.34-.51-1.31-1.25-1.66-1.25-1.66-1.02-.7.08-.69.08-.69 1.13.08 1.72 1.16 1.72 1.16 1 1.72 2.63 1.22 3.27.93.1-.73.39-1.22.71-1.5-2.5-.28-5.13-1.25-5.13-5.57 0-1.23.44-2.24 1.16-3.03-.12-.28-.5-1.43.11-2.98 0 0 .94-.3 3.09 1.16.9-.25 1.86-.38 2.81-.38.95 0 1.91.13 2.81.38 2.15-1.46 3.09-1.16 3.09-1.16.61 1.55.23 2.7.11 2.98.72.79 1.16 1.8 1.16 3.03 0 4.34-2.63 5.29-5.14 5.56.4.35.76 1.04.76 2.1 0 1.51-.01 2.73-.01 3.1 0 .3.2.65.78.54 4.46-1.49 7.68-5.7 7.68-10.67C23.25 5.48 18.27.5 12 .5Z"
    />
  </svg>
);

const GoogleIcon = () => (
  <svg className="dashboard-social-icon" viewBox="0 0 48 48" width="20" height="20" aria-hidden="true" focusable="false">
    <path fill="#4285F4" d="M47.5 24.55c0-1.63-.15-3.2-.42-4.7H24v8.9h13.2c-.57 3.06-2.3 5.65-4.9 7.4v6.13h7.92c4.63-4.27 7.28-10.57 7.28-17.73Z" />
    <path fill="#34A853" d="M24 48c6.6 0 12.13-2.18 16.18-5.92l-7.92-6.13c-2.2 1.47-5.02 2.34-8.26 2.34-6.36 0-11.74-4.3-13.66-10.07H1.96v6.32C5.99 42.5 14.3 48 24 48Z" />
    <path fill="#FBBC05" d="M10.34 28.22A14.4 14.4 0 0 1 9.6 24c0-1.47.25-2.9.7-4.22v-6.32H1.96A24 24 0 0 0 0 24c0 3.87.93 7.53 2.56 10.78l7.78-6.06.01-.5Z" />
    <path fill="#EA4335" d="M24 9.5c3.6 0 6.83 1.24 9.37 3.66l7-7C36.12 2.36 30.6 0 24 0 14.3 0 5.99 5.5 1.96 13.46l8.38 6.32C12.26 13.8 17.64 9.5 24 9.5Z" />
  </svg>
);

export function LoginForm() {
  const { error, login, status } = useSession();
  const [mode, setMode] = useState<AuthMode>('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [name, setName] = useState('');
  const [hint, setHint] = useState<string | null>(null);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setHint(null);
    if (mode === 'login') {
      await login({ email, password });
    } else {
      setHint('Criação de conta está em beta fechada. Solicite provisionamento ao administrador do tenant.');
    }
  };

  const switchMode = (next: AuthMode) => {
    setMode(next);
    setHint(null);
  };

  const heading = mode === 'login' ? 'Entrar' : 'Criar conta';
  const description =
    mode === 'login'
      ? 'Conecte-se ao workspace do tenant para gerenciar alvos, validações de autorização e fluxos de execução.'
      : 'Cadastre uma conta vinculada ao tenant. O acesso é provisionado após validação do administrador.';
  const submitLabel =
    mode === 'login'
      ? status === 'refreshing'
        ? 'Entrando...'
        : 'Entrar com e-mail'
      : 'Criar conta';

  return (
    <section aria-label="login" className="dashboard-login">
      <div className="glass-card dashboard-login-hero">
        <div className="dashboard-login-hero-copy">
          <span className="eyebrow">Acesso ao workspace</span>
          <h2>Proteja cada superfície a partir de um único centro de controle.</h2>
          <p>
            Reúna autenticação, verificação de ownership, orquestração de scans e relatórios em um fluxo único antes que um finding chegue em produção.
          </p>
        </div>

        <ol className="dashboard-login-hero-flow" aria-label="fluxo do produto">
          {HERO_FLOW_STEPS.map((step) => (
            <li key={step.idx} className="dashboard-login-hero-flow-item">
              <span className="dashboard-login-hero-flow-mark">{step.idx}</span>
              <div className="dashboard-login-hero-flow-copy">
                <strong>{step.label}</strong>
                <span>{step.detail}</span>
              </div>
            </li>
          ))}
        </ol>

        <div className="dashboard-login-highlight-grid">
          <div className="dashboard-login-highlight">
            <span className="dashboard-login-highlight-label">Alvos</span>
            <strong>Defina o escopo antes do scan começar.</strong>
          </div>
          <div className="dashboard-login-highlight">
            <span className="dashboard-login-highlight-label">Execução</span>
            <strong>Dispare fluxos web, API, SAST e rede no mesmo workspace.</strong>
          </div>
          <div className="dashboard-login-highlight">
            <span className="dashboard-login-highlight-label">Relatórios</span>
            <strong>Mantenha contexto do tenant, findings e status alinhados de ponta a ponta.</strong>
          </div>
        </div>
      </div>

      <aside className="glass-card dashboard-login-pane">
        <div className="dashboard-login-pane-header">
          <span className="badge badge-accent">Workspace de segurança</span>
          <span className="badge">Acesso beta</span>
        </div>
        <div className="dashboard-side-card-copy dashboard-login-pane-copy">
          <span className="eyebrow">Autenticação</span>
          <h2>{heading}</h2>
          <p>{description}</p>
        </div>
        <div className="dashboard-auth-tabs" role="tablist" aria-label="modo de acesso">
          <button
            type="button"
            role="tab"
            aria-selected={mode === 'login'}
            className={`dashboard-auth-tab${mode === 'login' ? ' is-active' : ''}`}
            onClick={() => switchMode('login')}
          >
            Entrar
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={mode === 'register'}
            className={`dashboard-auth-tab${mode === 'register' ? ' is-active' : ''}`}
            onClick={() => switchMode('register')}
          >
            Criar conta
          </button>
        </div>
        <form onSubmit={handleSubmit} className="auth-grid">
          {mode === 'register' ? (
            <div className="field">
              <label htmlFor="name">Nome</label>
              <input
                className="input"
                id="name"
                name="name"
                type="text"
                value={name}
                onChange={(event) => setName(event.target.value)}
                placeholder="Como podemos te chamar?"
                autoComplete="name"
              />
            </div>
          ) : null}
          <div className="field">
            <label htmlFor="email">E-mail</label>
            <input
              className="input"
              id="email"
              name="email"
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              placeholder="owner@virtualrift.test"
              autoComplete="email"
            />
          </div>
          <div className="field">
            <label htmlFor="password">Senha</label>
            <input
              className="input"
              id="password"
              name="password"
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              placeholder={mode === 'login' ? 'Digite sua senha' : 'Defina uma senha forte'}
              autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
            />
          </div>
          {error ? (
            <p className="alert alert-danger" role="alert">
              {error}
            </p>
          ) : null}
          <div className="toolbar dashboard-login-actions">
            <button
              className="button-primary dashboard-login-submit"
              type="submit"
              disabled={status === 'refreshing'}
            >
              {submitLabel}
            </button>
          </div>
        </form>
        <div className="dashboard-login-divider">
          <span>ou continue com</span>
        </div>
        <div className="dashboard-social-auth">
          <button
            className="button-secondary dashboard-social-button"
            type="button"
            aria-label="Continuar com GitHub"
            onClick={() => setHint('Login com GitHub ainda não está disponível nesta beta.')}
          >
            <GitHubIcon />
            <span>GitHub</span>
          </button>
          <button
            className="button-secondary dashboard-social-button"
            type="button"
            aria-label="Continuar com Google"
            onClick={() => setHint('Login com Google ainda não está disponível nesta beta.')}
          >
            <GoogleIcon />
            <span>Google</span>
          </button>
        </div>
        {hint ? <p className="alert alert-info dashboard-social-hint">{hint}</p> : null}
        <div className="dashboard-login-footer">
          <span>Beta backend-first · Painel web</span>
          <button
            type="button"
            className="dashboard-login-footer-link"
            onClick={() => switchMode(mode === 'login' ? 'register' : 'login')}
          >
            {mode === 'login' ? 'Não tem conta? Criar conta' : 'Já tem conta? Entrar'}
          </button>
        </div>
      </aside>
    </section>
  );
}
