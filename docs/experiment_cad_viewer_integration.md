# Experiment: CAD Viewer Integration

Goal: evaluate whether an open-source CAD viewer can replace or enhance the current lightweight Canvas preview.

## Candidate Tools

Primary candidate:

- `mlightcad/cad-viewer`

Fallback candidates:

- `vagran/dxf-viewer`
- `three-dxf`

## Why This Matters

The current Canvas preview is useful for MVP-level positioning, but a mature viewer can provide:

- better zoom and pan
- layer control
- richer entity rendering
- faster large drawing handling
- future DWG/DXF viewing capability
- better issue highlight overlays

## Experiment Scope

The first experiment should not rewrite the whole frontend.

Build a small isolated proof of concept that checks:

1. Can it load `samples/dxf/valid_ship_section.dxf`?
2. Can it show layers?
3. Can it zoom and pan smoothly?
4. Can it expose entity or layer metadata?
5. Can we highlight an issue by layer, entity id, or bounding box?
6. Can it be embedded in the Vue app without breaking layout?

## Decision Criteria

Adopt as main viewer if:

- loading and rendering are stable
- issue highlighting is possible
- integration complexity is acceptable
- license is compatible and recorded

Keep current Canvas viewer if:

- highlighting cannot be controlled
- API is unstable
- viewer is too heavy for MVP
- DWG/DXF support does not match our needs

## Expected Output

- A short proof-of-concept report.
- Screenshots or notes about rendering quality.
- Recommendation: adopt, keep as optional, or reject.
