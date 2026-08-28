# The branch build inspector shares primitives with the build page, not compositions

The pipeline content view carries a build inspector — promotions on the left,
validations on the right — for the build selected in the timeline. The build page
at `/build/{id}` already renders that content as the `BuildContentPromotions` and
`BuildContentValidations` widgets. We are building the inspector's panels as new
compositions rather than embedding those widgets, because the two hosts optimise
for different things: the inspector is read while scanning a branch, the build page
is read when you have already committed to one build.

To stop the two drifting on the thing that matters — how a promotion or a
validation looks and reads — the medal-with-badge, the state pill, and the
validation chip are extracted as shared primitives used by both. Duplication is
confined to arrangement.

## Considered options

Embedding the existing widgets directly in the inspector was the cheaper option and
was rejected: it would have forced one layout to serve both hosts.

Accepting permanent, fully independent implementations was rejected because the
divergence would land on the visual language for promotions and validations, which
should be identical everywhere in the product.

## Consequences

A change to how a promotion or validation is rendered must be made in the shared
primitive, not in either composition. If the inspector's arrangement proves better
in use, the build page's widgets should be migrated onto it and the older
composition deleted, rather than the two being maintained indefinitely.
