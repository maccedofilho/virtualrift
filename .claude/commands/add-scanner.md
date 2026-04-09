# Add Scanner Command

Invoke with `/project:add-scanner`.

Use this command to create or extend scanner capability through the workflow in `skills/new-scanner/SKILL.md`.

## What this command must do

1. collect the required inputs for scope, target type, strategy and false-positive profile
2. confirm the scanner does not duplicate an existing module or rule set
3. validate safety constraints before code generation
4. generate only the files justified by the chosen design
5. require tests, target validation and isolation before calling the scanner ready

## Safety gates

- Follow `agents/scanner-builder.md` throughout the workflow.
- Follow `.claude/rules/security.md` and `.claude/rules/testing.md`.
- Never accept raw tenant-controlled command flags.
- Never skip internal-target blocking or authorization checks.
- Never finish without a vulnerable-case test and a clean-case test strategy.

## Output expectations

The final result must state:

- which files were created or updated
- how target validation is enforced
- how execution is isolated
- what tests were added
- what still remains for production readiness, if anything