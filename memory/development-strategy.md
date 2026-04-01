# Estratégia de Desenvolvimento - VirtualRift

## Abordagem: Test-Driven Development (TDD)

**Ordem:**
1. Escrever testes primeiro
2. Depois implementar o código mínimo para passar
3. Refatorar

## Fluxo de Trabalho

Por cada módulo:
1. **Definir cenários de teste** (unitários + integração)
2. **Escrever os testes** (que vão falhar inicialmente)
3. **Implementar o domínio**
4. **Fazer os testes passarem**
5. **Refatorar**
6. **Revisar com security-auditor**

## Próximos Passos

- Definir testes de domínio primeiro (Tenant, User, Scan, VulnerabilityFinding)
- Definir testes de segurança (tenant isolation, JWT validation)
- Definir testes de integração (Kafka events, database)
