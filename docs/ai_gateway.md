# AI Gateway

## Current Status

The current AI Gateway is a deterministic local evidence summarizer, implemented as `AiGateway`.

It does not call an external LLM yet. This is intentional for the MVP: explanations must be traceable, testable, and based only on stored review evidence.

## Inputs

`AiGateway` consumes:

- `ReviewIssue`
- `RULE_RESULT` evidence
- `CAD_ENTITY`, `CAD_LAYER`, or `CAD_SUMMARY` evidence
- `KNOWLEDGE_CLAUSE` evidence

It must not infer facts from the drawing directly, and it must not create new review conclusions that are not supported by existing evidence.

## Output

The gateway returns `AiExplanation`:

- `summary`
- `reason`
- `basis`
- `recommendation`
- `reviewFocus`
- `evidenceRefs`

`ReviewIssue.aiExplanation` is returned by `/api/issues`, and `/api/issues/{issueId}/ai-explanation` exposes a focused explanation endpoint.

## Future Replacement Boundary

When a local LLM or OpenAI-compatible endpoint is introduced, it should replace the implementation behind `AiGateway`, not the review flow.

The prompt should include only:

- the issue fields
- the evidence chain
- explicit constraints that the model cannot invent facts
- an instruction to cite evidence references in the output

The output should still be validated and stored or returned as `AiExplanation`.
